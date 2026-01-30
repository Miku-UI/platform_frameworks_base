/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.server.companion.virtual.computercontrol;

import static android.app.PendingIntent.FLAG_CANCEL_CURRENT;
import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.app.PendingIntent.FLAG_ONE_SHOT;

import android.annotation.NonNull;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AppOpsManager;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.companion.virtual.IVirtualDevice;
import android.companion.virtual.IVirtualDeviceActivityListener;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.computercontrol.ComputerControlSession;
import android.companion.virtual.computercontrol.ComputerControlSessionParams;
import android.companion.virtual.computercontrol.IComputerControlSessionCallback;
import android.companion.virtualdevice.flags.Flags;
import android.content.AttributionSource;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.UserHandle;
import android.util.ArraySet;
import android.util.Slog;
import android.view.Display;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.ServiceThread;

import java.util.Objects;

/**
 * Handles creation and lifecycle of {@link ComputerControlSession}s.
 *
 * <p>This class enforces session creation policies, such as limiting the number of concurrent
 * sessions and preventing creation when the device is locked.
 */
public class ComputerControlSessionProcessor {

    private static final String TAG = ComputerControlSessionProcessor.class.getSimpleName();

    // TODO(b/419548594): Make this configurable.
    @VisibleForTesting
    static final int MAXIMUM_CONCURRENT_SESSIONS = 5;

    private final Context mContext;
    private final KeyguardManager mKeyguardManager;
    private final AppOpsManager mAppOpsManager;
    private final PackageManager mPackageManager;
    private final VirtualDeviceFactory mVirtualDeviceFactory;
    private final PendingIntentFactory mPendingIntentFactory;

    /** The binders of all currently active sessions. */
    private final ArraySet<ComputerControlSessionImpl> mSessions = new ArraySet<>();

    private final Object mHandlerThreadLock = new Object();
    @GuardedBy("mHandlerThreadLock")
    private ServiceThread mHandlerThread;
    private Handler mHandler;

    public ComputerControlSessionProcessor(
            Context context, VirtualDeviceFactory virtualDeviceFactory) {
        this(context, virtualDeviceFactory, ComputerControlSessionProcessor::createPendingIntent);
    }

    @VisibleForTesting
    ComputerControlSessionProcessor(
            Context context, VirtualDeviceFactory virtualDeviceFactory,
            PendingIntentFactory pendingIntentFactory) {
        mContext = context;
        mVirtualDeviceFactory = virtualDeviceFactory;
        mPendingIntentFactory = pendingIntentFactory;
        mKeyguardManager = context.getSystemService(KeyguardManager.class);
        mAppOpsManager = context.getSystemService(AppOpsManager.class);
        mPackageManager = context.getPackageManager();
    }

    /**
     * Process a new session creation request.
     *
     * <p>A new session will be created. In case of failure, the
     * {@link IComputerControlSessionCallback#onSessionCreationFailed} method on the provided
     * {@code callback} will be invoked.
     */
    public void processNewSessionRequest(
            @NonNull AttributionSource attributionSource,
            @NonNull ComputerControlSessionParams params,
            @NonNull IComputerControlSessionCallback callback) {
        validateParams(attributionSource, params);
        startHandlerThreadIfNeeded();

        final boolean canCreateWithoutConsent;
        if (Flags.computerControlConsent()) {
            final int isOpAllowed = mAppOpsManager.noteOpNoThrow(
                    AppOpsManager.OP_COMPUTER_CONTROL, attributionSource, "create session");
            canCreateWithoutConsent = isOpAllowed == AppOpsManager.MODE_ALLOWED;
        } else {
            canCreateWithoutConsent = true;
        }

        if (canCreateWithoutConsent) {
            mHandler.post(() -> createSession(attributionSource, params, callback));
            return;
        }

        synchronized (mSessions) {
            if (!checkSessionCreationPreconditionsLocked(params, callback)) {
                return;
            }
        }

        final ResultReceiver resultReceiver =
                new ConsentResultReceiver(attributionSource, params, callback).prepareForIpc();
        final Intent intent = new Intent(ComputerControlSession.ACTION_REQUEST_ACCESS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(Intent.EXTRA_PACKAGE_NAME, attributionSource.getPackageName())
                .putExtra(Intent.EXTRA_RESULT_RECEIVER, resultReceiver);
        final PendingIntent pendingIntent =
                mPendingIntentFactory.create(mContext, Binder.getCallingUid(), intent);
        try {
            callback.onSessionPending(pendingIntent);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to notify ComputerControlSession " + params.getName()
                    + " about pending session");
        }
    }

    private void validateParams(AttributionSource attributionSource,
            ComputerControlSessionParams params) {
        synchronized (mSessions) {
            for (int i = 0; i < mSessions.size(); i++) {
                ComputerControlSessionImpl session = mSessions.valueAt(i);
                if (Objects.equals(attributionSource.getPackageName(),
                        session.getOwnerPackageName())
                        && Objects.equals(params.getName(), session.getName())) {
                    throw new IllegalArgumentException("Session name must be unique");
                }
            }
        }

        if (!Flags.computerControlActivityPolicyStrict()) {
            return;
        }

        // TODO(b/437849228): Should be non-null
        if (params.getTargetPackageNames() == null || params.getTargetPackageNames().isEmpty()) {
            return;
        }

        // Ensure all packages the ComputerControl session should be able to launch are:
        // 1) Applications with a valid launcher Intent
        // 2) NOT PermissionController
        for (int i = 0; i < params.getTargetPackageNames().size(); i++) {
            String packageName = params.getTargetPackageNames().get(i);

            if (packageName == null
                    || packageName.isEmpty()
                    || mPackageManager.getPermissionControllerPackageName().equals(packageName)
                    || mPackageManager.getLaunchIntentForPackage(packageName) == null) {
                throw new IllegalArgumentException(
                        "Invalid target package for ComputerControl: " + packageName);
            }
        }
    }

    /**
     * Returns whether the virtual device with the given ID represents a computer control session.
     */
    public boolean isComputerControlSession(int deviceId) {
        synchronized (mSessions) {
            for (int i = 0; i < mSessions.size(); i++) {
                ComputerControlSessionImpl session = mSessions.valueAt(i);
                if (session.getDeviceId() == deviceId) {
                    return true;
                }
            }
        }
        return false;
    }

    private void startHandlerThreadIfNeeded() {
        synchronized (mHandlerThreadLock) {
            if (mHandlerThread != null) {
                return;
            }
            mHandlerThread =
                    new ServiceThread(TAG, Process.THREAD_PRIORITY_FOREGROUND, /* allowTo= */false);
            mHandlerThread.start();
            mHandler = new Handler(mHandlerThread.getLooper());
        }
    }

    private void createSession(@NonNull AttributionSource attributionSource,
            @NonNull ComputerControlSessionParams params,
            @NonNull IComputerControlSessionCallback callback) {
        if (!callback.asBinder().pingBinder()) {
            Slog.w(TAG, "Binder is dead for ComputerControlSession " + params.getName()
                    + ", aborting session creation");
            // Don't bother creating the session if the requester is not around anymore.
            return;
        }
        ComputerControlSessionImpl session;
        synchronized (mSessions) {
            if (!checkSessionCreationPreconditionsLocked(params, callback)) {
                return;
            }
            Slog.d(TAG, "Creating ComputerControlSession " + params.getName());
            session = new ComputerControlSessionImpl(
                    callback.asBinder(), params, attributionSource, mVirtualDeviceFactory,
                    new OnSessionClosedListener(params.getName(), callback),
                    new ComputerControlSessionImpl.Injector(mContext));
            mSessions.add(session);
        }

        // If the client provided a surface, disable the screenshot API.
        // TODO(b/439774796): Do not allow client-provided surface and dimensions.
        final int displayId = params.getDisplaySurface() == null
                ? session.getVirtualDisplayId()
                : Display.INVALID_DISPLAY;
        try {
            callback.onSessionCreated(displayId, session.getVirtualDisplayToken(), session);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to notify ComputerControlSession " + params.getName()
                    + " about session creation success");
        }
    }

    /** Closes the session with the given ID. */
    public void closeSession(int deviceId) {
        synchronized (mSessions) {
            for (int i = 0; i < mSessions.size(); i++) {
                ComputerControlSessionImpl session = mSessions.valueAt(i);
                if (session.getDeviceId() != deviceId) {
                    continue;
                }
                try {
                    session.close();
                } catch (RemoteException e) {
                    Slog.w(TAG, "Failed to close ComputerControlSession for deviceId "
                            + deviceId, e);
                }
                return;
            }
        }
        Slog.w(TAG, "Failed to close ComputerControlSession for unknown deviceId " + deviceId);
    }

    @GuardedBy("mSessions")
    private boolean checkSessionCreationPreconditionsLocked(
            @NonNull ComputerControlSessionParams params,
            @NonNull IComputerControlSessionCallback callback) {
        if (mKeyguardManager.isDeviceLocked()) {
            dispatchSessionCreationFailed(callback, params,
                    ComputerControlSession.ERROR_DEVICE_LOCKED);
            return false;
        }
        if (mSessions.size() >= MAXIMUM_CONCURRENT_SESSIONS) {
            dispatchSessionCreationFailed(callback, params,
                    ComputerControlSession.ERROR_SESSION_LIMIT_REACHED);
            return false;
        }
        return true;
    }

    /** Notifies the client that session creation failed. */
    private void dispatchSessionCreationFailed(@NonNull IComputerControlSessionCallback callback,
            @NonNull ComputerControlSessionParams params, int reason) {
        try {
            callback.onSessionCreationFailed(reason);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to notify ComputerControlSession " + params.getName()
                    + " about session creation failure");
        }
    }

    private static PendingIntent createPendingIntent(Context context, int uid, Intent intent) {
        return Binder.withCleanCallingIdentity(() ->
                PendingIntent.getActivityAsUser(
                        context, /*requestCode= */ uid, intent,
                        FLAG_ONE_SHOT | FLAG_CANCEL_CURRENT | FLAG_IMMUTABLE,
                        ActivityOptions.makeBasic()
                                .setPendingIntentCreatorBackgroundActivityStartMode(
                                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                                .toBundle(),
                        UserHandle.CURRENT));
    }

    private final class ConsentResultReceiver extends ResultReceiver {

        private final AttributionSource mAttributionSource;
        private final ComputerControlSessionParams mParams;
        private final IComputerControlSessionCallback mCallback;

        ConsentResultReceiver(@NonNull AttributionSource attributionSource,
                @NonNull ComputerControlSessionParams params,
                @NonNull IComputerControlSessionCallback callback) {
            super(mHandler);
            mAttributionSource = attributionSource;
            mParams = params;
            mCallback = callback;
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle data) {
            if (resultCode == Activity.RESULT_OK) {
                mHandler.post(() -> createSession(mAttributionSource, mParams, mCallback));
            } else {
                dispatchSessionCreationFailed(mCallback, mParams,
                        ComputerControlSession.ERROR_PERMISSION_DENIED);
            }
        }

        /**
         * Convert this instance of a "locally-defined" ResultReceiver to an instance of
         * {@link android.os.ResultReceiver} itself, which the receiving process will be able to
         * unmarshall.
         */
        private ResultReceiver prepareForIpc() {
            final Parcel parcel = Parcel.obtain();
            writeToParcel(parcel, 0);
            parcel.setDataPosition(0);

            final ResultReceiver ipcFriendly = ResultReceiver.CREATOR.createFromParcel(parcel);
            parcel.recycle();

            return ipcFriendly;
        }
    }

    /**
     * Listener for when a {@link ComputerControlSessionImpl} is closed.
     *
     * <p>Removes the session from the set of active sessions and notifies the client.
     */
    private class OnSessionClosedListener implements ComputerControlSessionImpl.OnClosedListener {
        @NonNull
        private final String mSessionName;
        @NonNull
        private final IComputerControlSessionCallback mAppCallback;

        OnSessionClosedListener(@NonNull String sessionName,
                @NonNull IComputerControlSessionCallback appCallback) {
            mSessionName = sessionName;
            mAppCallback = appCallback;
        }

        @Override
        public void onClosed(@NonNull ComputerControlSessionImpl session) {
            synchronized (mSessions) {
                if (!mSessions.remove(session)) {
                    // The session was already removed, which can happen if close() is called
                    // multiple times.
                    return;
                }
            }
            try {
                mAppCallback.onSessionClosed();
            } catch (RemoteException e) {
                Slog.w(TAG, "Failed to notify ComputerControlSession " + mSessionName
                        + " about session closure");
            }
        }
    }

    /**
     * Returns {@code true}, if any of the ongoing computer control sessions are running on the
     * provided virtual display id, {@code false} otherwise.
     */
    public boolean isComputerControlDisplay(int displayId) {
        synchronized (mSessions) {
            for (int i = 0; i < mSessions.size(); i++) {
                if (mSessions.valueAt(i).getVirtualDisplayId() == displayId) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Interface for creating a virtual device for a computer control session.
     */
    public interface VirtualDeviceFactory {
        /**
         * Creates a new virtual device.
         */
        IVirtualDevice createVirtualDevice(
                IBinder token,
                AttributionSource attributionSource,
                VirtualDeviceParams params,
                IVirtualDeviceActivityListener activityListener);
    }

    /**
     * Interface for creating a pending intent for a computer control session.
     */
    @VisibleForTesting
    public interface PendingIntentFactory {
        /**
         * Creates a new pending intent.
         */
        PendingIntent create(Context context, int uid, Intent intent);
    }
}
