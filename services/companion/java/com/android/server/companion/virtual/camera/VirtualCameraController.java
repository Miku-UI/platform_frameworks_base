/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.companion.virtual.camera;

import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_DEFAULT;

import static com.android.server.companion.virtual.camera.VirtualCameraConversionUtil.getServiceCameraConfiguration;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.companion.virtual.VirtualDeviceParams.DevicePolicy;
import android.companion.virtual.camera.IVirtualCameraCallback;
import android.companion.virtual.camera.VirtualCameraConfig;
import android.companion.virtual.camera.VirtualCameraStreamConfig;
import android.companion.virtualcamera.IVirtualCameraService;
import android.companion.virtualcamera.VirtualCameraConfiguration;
import android.companion.virtualdevice.flags.Flags;
import android.content.AttributionSource;
import android.content.Context;
import android.hardware.camera2.CameraMetadata;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.expresslog.Counter;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Manages the registration and removal of virtual camera from the server side.
 *
 * <p>This classes delegate calls to the virtual camera service, so it is dependent on the service
 * to be up and running.
 */
public final class VirtualCameraController implements IBinder.DeathRecipient {

    private static final String VIRTUAL_CAMERA_SERVICE_NAME = "virtual_camera";
    private static final String TAG = "VirtualCameraController";

    private final Object mServiceLock = new Object();

    @GuardedBy("mServiceLock")
    @Nullable
    private IVirtualCameraService mVirtualCameraService;
    @DevicePolicy
    private final int mCameraPolicy;
    private final int mDeviceId;

    @GuardedBy("mRegisteredCameras")
    private final Map<IBinder, CameraDescriptor> mRegisteredCameras = new ArrayMap<>();

    public VirtualCameraController(@DevicePolicy int cameraPolicy, int deviceId) {
        this(/* virtualCameraService= */ null, cameraPolicy, deviceId);
    }

    @VisibleForTesting
    VirtualCameraController(@Nullable IVirtualCameraService virtualCameraService,
            @DevicePolicy int cameraPolicy, int deviceId) {
        mVirtualCameraService = virtualCameraService;
        mCameraPolicy = cameraPolicy;
        mDeviceId = deviceId;
    }

    /**
     * Register a new virtual camera with the given config.
     *
     * @param cameraConfig The {@link VirtualCameraConfig} sent by the client.
     */
    public void registerCamera(@NonNull VirtualCameraConfig cameraConfig,
            AttributionSource attributionSource) {
        checkConfigByPolicy(cameraConfig);

        connectVirtualCameraServiceIfNeeded();

        try {
            if (registerCameraWithService(cameraConfig)) {
                CameraDescriptor cameraDescriptor = new CameraDescriptor(cameraConfig);
                IBinder cameraClientBinder = cameraConfig.getCallback().asBinder();
                cameraClientBinder.linkToDeath(cameraDescriptor, 0 /* flags */);
                synchronized (mRegisteredCameras) {
                    mRegisteredCameras.put(cameraClientBinder, cameraDescriptor);
                }
            } else {
                throw new RuntimeException("Failed to register virtual camera.");
            }
        } catch (RemoteException e) {
            e.rethrowFromSystemServer();
        }
        Counter.logIncrementWithUid(
                "virtual_devices.value_virtual_camera_created_count",
                attributionSource.getUid());
    }

    /**
     * Unregister the virtual camera with the given config.
     *
     * @param cameraConfig The {@link VirtualCameraConfig} sent by the client.
     */
    public void unregisterCamera(@NonNull VirtualCameraConfig cameraConfig) {
        final IBinder cameraClientBinder = cameraConfig.getCallback().asBinder();
        synchronized (mRegisteredCameras) {
            CameraDescriptor descriptor = mRegisteredCameras.remove(cameraClientBinder);
            if (descriptor == null) {
                Slog.w(TAG, "Virtual camera was not registered.");
                return;
            }
            cameraClientBinder.unlinkToDeath(descriptor, 0 /* flags */);
        }

        connectVirtualCameraServiceIfNeeded();

        final IVirtualCameraService service;
        synchronized (mServiceLock) {
            service = mVirtualCameraService;
        }

        if (service != null) {
            try {
                service.unregisterCamera(cameraClientBinder);
            } catch (RemoteException e) {
                e.rethrowFromSystemServer();
            }
        }
    }

    /** Return the id of the virtual camera with the given config. */
    public String getCameraId(@NonNull VirtualCameraConfig cameraConfig) {
        connectVirtualCameraServiceIfNeeded();

        final IVirtualCameraService service;
        synchronized (mServiceLock) {
            service = mVirtualCameraService;
        }

        Objects.requireNonNull(service);
        try {
            return service.getCameraId(cameraConfig.getCallback().asBinder());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Closes the session of the virtual camera with the given config.
     *
     * @param cameraConfig The {@link VirtualCameraConfig} sent by the client.
     */
    public void closeVirtualCameraSession(@NonNull VirtualCameraConfig cameraConfig) {
        connectVirtualCameraServiceIfNeeded();

        final IVirtualCameraService service;
        synchronized (mServiceLock) {
            service = mVirtualCameraService;
        }

        if (service != null) {
            try {
                service.closeSession(cameraConfig.getCallback().asBinder());
            } catch (RemoteException e) {
                e.rethrowFromSystemServer();
            }
        }
    }

    @Override
    public void binderDied() {
        Slog.w(TAG, "virtual_camera_service died.");
        synchronized (mServiceLock) {
            mVirtualCameraService = null;
        }
        notifyCameraClosureFromServer();
    }

    /**
     * This directly notifies all the currently connected virtual camera of closure.
     * <p>
     * Usually this is done by the virtual_camera_service (the HAL side) by calling
     * {@link IVirtualCameraService#unregisterCamera(IBinder)}, but if for some reason
     * (e.g. service crash), be can't call the virtual camera service to close the camera, we still
     * need to notify the virtual camera owner using this method.
     */
    private void notifyCameraClosureFromServer() {
        List<CameraDescriptor> camerasToNotify;
        synchronized (mRegisteredCameras) {
            camerasToNotify = new ArrayList<>(mRegisteredCameras.values());
            mRegisteredCameras.clear();
        }

        for (CameraDescriptor descriptor : camerasToNotify) {
            IVirtualCameraCallback cameraCallback = descriptor.mConfig.getCallback();
            for (VirtualCameraStreamConfig streamConfig :
                    descriptor.mConfig.getStreamConfigs()) {
                try {
                    cameraCallback.onStreamClosed(streamConfig.getStreamIndex());
                } catch (RemoteException e) {
                    Slog.w(TAG,
                            "binderDied(): Camera failed to notify stream closed (id=%d)"
                                    .formatted(streamConfig.getStreamIndex()), e);
                }
            }
            cameraCallback.asBinder().unlinkToDeath(descriptor, 0);
        }
    }

    /** Release resources associated with this controller. */
    public void close() {
        Slog.i(TAG, "Closing VirtualCameraController for deviceId:" + mDeviceId);
        Set<IBinder> camerasToClose = null;
        synchronized (mRegisteredCameras) {
            if (!mRegisteredCameras.isEmpty()) {
                camerasToClose = new ArraySet<>(mRegisteredCameras.keySet());
                mRegisteredCameras.clear();
            }
        }

        if (camerasToClose != null) {
            final IVirtualCameraService service;
            connectVirtualCameraServiceIfNeeded();

            synchronized (mServiceLock) {
                service = mVirtualCameraService;
            }

            if (service == null) {
                Slog.w(TAG, "VirtualCameraService is null. Failed to unregister cameras.");
                return;
            }

            for (IBinder binder : camerasToClose) {
                try {
                    service.unregisterCamera(binder);
                } catch (RemoteException e) {
                    Slog.w(TAG, "close(): Camera failed to be removed on camera "
                            + "service.", e);
                }
            }
        }

        synchronized (mServiceLock) {
            mVirtualCameraService = null;
        }
    }

    /** Dumps information about this {@link VirtualCameraController} for debugging purposes. */
    public void dump(PrintWriter fout, String indent) {
        synchronized (mRegisteredCameras) {
            fout.println(indent + "VirtualCameraController: " + mRegisteredCameras.size()
                    + " registered cameras");
            for (CameraDescriptor descriptor : mRegisteredCameras.values()) {
                fout.println(indent + indent + descriptor.mConfig);
            }
        }
    }

    private void checkConfigByPolicy(VirtualCameraConfig config) {
        // Don't allow any cameras on the default policy if the flag is disabled.
        if (!Flags.externalCameraDefaultPolicy() && mCameraPolicy == DEVICE_POLICY_DEFAULT) {
            throw new IllegalArgumentException(
                    "Cannot create virtual camera with DEVICE_POLICY_DEFAULT for "
                            + "POLICY_TYPE_CAMERA.");
        }

        // Multiple external cameras are allowed on any policy
        if (CameraMetadata.LENS_FACING_EXTERNAL == config.getLensFacing()) {
            return;
        }

        if (mCameraPolicy == DEVICE_POLICY_DEFAULT) {
            throw new IllegalArgumentException(
                    "Cannot create virtual camera with DEVICE_POLICY_DEFAULT for "
                            + "POLICY_TYPE_CAMERA and lens facing " + config.getLensFacing());
        }

        if (isLensFacingAlreadyPresent(config.getLensFacing())) {
            throw new IllegalArgumentException(
                    "Only a single virtual camera can be created with lens facing "
                            + config.getLensFacing());
        }
    }

    private boolean isLensFacingAlreadyPresent(int lensFacing) {
        synchronized (mRegisteredCameras) {
            for (CameraDescriptor cameraDescriptor : mRegisteredCameras.values()) {
                if (cameraDescriptor.mConfig.getLensFacing() == lensFacing) {
                    return true;
                }
            }
        }
        return false;
    }

    private void connectVirtualCameraServiceIfNeeded() {
        synchronized (mServiceLock) {
            // Try to connect to service if not connected already.
            if (mVirtualCameraService == null) {
                connectVirtualCameraServiceLocked();
            }
            // Throw exception if we are unable to connect to service.
            if (mVirtualCameraService == null) {
                notifyCameraClosureFromServer();
                throw new IllegalStateException("Virtual camera service is not connected.");
            }
        }
    }

    @GuardedBy("mServiceLock")
    private void connectVirtualCameraServiceLocked() {
        final long callingId = Binder.clearCallingIdentity();
        try {
            IBinder virtualCameraServiceBinder =
                    ServiceManager.waitForService(VIRTUAL_CAMERA_SERVICE_NAME);
            if (virtualCameraServiceBinder == null) {
                Slog.e(TAG, "connectVirtualCameraServiceLocked: Failed to connect to the virtual "
                        + "camera service");
                return;
            }
            virtualCameraServiceBinder.linkToDeath(this, 0);
            mVirtualCameraService = IVirtualCameraService.Stub.asInterface(
                    virtualCameraServiceBinder);
        } catch (RemoteException e) {
            e.rethrowFromSystemServer();
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    private boolean registerCameraWithService(VirtualCameraConfig config) throws RemoteException {
        VirtualCameraConfiguration serviceConfiguration = getServiceCameraConfiguration(config);
        final IVirtualCameraService service;

        synchronized (mServiceLock) {
            // This case should already be prevented by connectVirtualCameraServiceIfNeeded()
            // called in registerCamera()
            if (mVirtualCameraService == null) {
                throw new IllegalStateException("Virtual camera service is not connected.");
            }
            service = mVirtualCameraService;
        }

        int ownerDeviceId = mDeviceId;
        if (Flags.externalCameraDefaultPolicy() && mCameraPolicy == DEVICE_POLICY_DEFAULT) {
            ownerDeviceId = Context.DEVICE_ID_DEFAULT;
        }

        return service.registerCamera(config.getCallback().asBinder(),
                serviceConfiguration, ownerDeviceId);
    }

    private final class CameraDescriptor implements IBinder.DeathRecipient {

        private final VirtualCameraConfig mConfig;

        CameraDescriptor(VirtualCameraConfig config) {
            mConfig = config;
        }

        @Override
        public void binderDied() {
            Slog.d(TAG, "Virtual camera binder died");
            unregisterCamera(mConfig);
        }
    }
}
