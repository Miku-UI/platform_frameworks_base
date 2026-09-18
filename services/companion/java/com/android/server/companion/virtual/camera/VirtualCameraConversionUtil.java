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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.companion.virtual.camera.IVirtualCameraCallback;
import android.companion.virtual.camera.VirtualCameraConfig;
import android.companion.virtual.camera.VirtualCameraStreamConfig;
import android.companion.virtualcamera.Format;
import android.companion.virtualcamera.ICaptureResultConsumer;
import android.companion.virtualcamera.IVirtualCameraService;
import android.companion.virtualcamera.SupportedStreamConfiguration;
import android.companion.virtualcamera.VirtualCameraConfiguration;
import android.companion.virtualcamera.VirtualCameraMetadata;
import android.companion.virtualdevice.flags.Flags;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.impl.CameraMetadataNative;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Slog;
import android.view.Surface;

import java.util.ArrayList;
import java.util.List;

/** Utilities to convert the client side classes to the virtual camera service ones. */
final class VirtualCameraConversionUtil {

    private static final String TAG = "VirtualCameraConversionUtil";

    private VirtualCameraConversionUtil() {
    }

    /**
     * Fetches the configuration of the provided virtual cameraConfig that was provided by its owner
     * and convert it into the {@link IVirtualCameraService} types: {@link
     * VirtualCameraConfiguration}.
     *
     * @param cameraConfig The cameraConfig sent by the client.
     * @return The converted configuration to be sent to the {@link IVirtualCameraService}.
     * @throws RemoteException If there was an issue fetching the configuration from the client.
     */
    @SuppressWarnings("NullAway") // code generated for aidl files is not properly annotated
    @NonNull
    public static VirtualCameraConfiguration
            getServiceCameraConfiguration(@NonNull VirtualCameraConfig cameraConfig) {
        VirtualCameraConfiguration serviceConfiguration = new VirtualCameraConfiguration();
        List<SupportedStreamConfiguration> list = new ArrayList<>();
        for (VirtualCameraStreamConfig virtualCameraStreamConfig :
                cameraConfig.getStreamConfigs()) {
            SupportedStreamConfiguration supportedStreamConfiguration =
                    convertSupportedStreamConfiguration(virtualCameraStreamConfig);
            list.add(supportedStreamConfiguration);
        }
        serviceConfiguration.supportedStreamConfigs =
                list.toArray(new SupportedStreamConfiguration[0]);
        serviceConfiguration.sensorOrientation = cameraConfig.getSensorOrientation();
        serviceConfiguration.lensFacing = cameraConfig.getLensFacing();
        serviceConfiguration.virtualCameraCallback = convertCallback(cameraConfig.getCallback());
        serviceConfiguration.perFrameCameraMetadataEnabled =
                cameraConfig.isPerFrameCameraMetadataEnabled();
        serviceConfiguration.cameraCharacteristics = convertToVirtualCameraMetadata(
                cameraConfig.getCameraCharacteristics());
        if (Flags.cameraMultipleInputStreams()) {
            serviceConfiguration.isMultiInputStreamEnabled =
                    cameraConfig.isConcurrentStreamConfigSupported();
        }
        return serviceConfiguration;
    }

    @NonNull
    private static android.companion.virtualcamera.IVirtualCameraCallback convertCallback(
            @NonNull IVirtualCameraCallback camera) {
        return new android.companion.virtualcamera.IVirtualCameraCallback.Stub() {
            @Override
            public void onOpenCamera() throws RemoteException {
                if (Flags.virtualCameraOnOpen()) {
                    camera.onOpenCamera();
                }
            }

            @Override
            public void onConfigureSession(VirtualCameraMetadata sessionParameters,
                    ICaptureResultConsumer captureResultConsumer) throws RemoteException {
                CaptureRequest captureRequest = null;
                if (sessionParameters != null) {
                    captureRequest = convertToCaptureRequest(sessionParameters);
                }

                camera.onConfigureSession(captureRequest,
                        convertToVdmCaptureResultConsumer(captureResultConsumer));
            }
            @Override
            public void onStreamConfigured(int streamId, Surface surface, int width, int height,
                    int format) throws RemoteException {
                camera.onStreamConfigured(streamId, surface, width, height,
                        convertToJavaFormat(format));
            }

            @Override
            public void onProcessCaptureRequest(int streamId, int frameId,
                    VirtualCameraMetadata captureRequestSettings) throws RemoteException {
                CaptureRequest captureRequest = null;

                if (captureRequestSettings != null) {
                    captureRequest = convertToCaptureRequest(captureRequestSettings);
                }

                camera.onProcessCaptureRequest(streamId, frameId, captureRequest);
            }

            @Override
            public void onStreamClosed(int streamId) throws RemoteException {
                camera.onStreamClosed(streamId);
            }
        };
    }

    @NonNull
    private static SupportedStreamConfiguration convertSupportedStreamConfiguration(
            VirtualCameraStreamConfig stream) {
        SupportedStreamConfiguration supportedConfig = new SupportedStreamConfiguration();
        supportedConfig.height = stream.getHeight();
        supportedConfig.width = stream.getWidth();
        supportedConfig.imageFormat = convertToHalFormat(stream.getFormat());
        supportedConfig.maxFps = stream.getMaximumFramesPerSecond();
        supportedConfig.index = stream.getStreamIndex();
        return supportedConfig;
    }

    private static int convertToHalFormat(int javaFormat) {
        return switch (javaFormat) {
            case ImageFormat.YUV_420_888 -> Format.YUV_420_888;
            case PixelFormat.RGBA_8888 -> Format.RGBA_8888;
            case ImageFormat.JPEG -> Format.JPEG;
            case ImageFormat.HEIC -> Format.HEIC;
            default -> Format.UNKNOWN;
        };
    }

    private static int convertToJavaFormat(int halFormat) {
        return switch (halFormat) {
            case Format.YUV_420_888 -> ImageFormat.YUV_420_888;
            case Format.RGBA_8888 -> PixelFormat.RGBA_8888;
            case Format.JPEG -> ImageFormat.JPEG;
            case Format.HEIC -> ImageFormat.HEIC;
            default -> ImageFormat.UNKNOWN;
        };
    }

    @Nullable
    private static VirtualCameraMetadata convertToVirtualCameraMetadata(
            @Nullable CameraCharacteristics cameraCharacteristics) {
        if (cameraCharacteristics == null) {
            return null;
        }

        return convertToVirtualCameraMetadata(cameraCharacteristics.getNativeMetadata());
    }

    @Nullable
    private static VirtualCameraMetadata convertToVirtualCameraMetadata(
            @Nullable CameraMetadataNative metadataNative) {
        if (metadataNative == null) {
            return null;
        }

        VirtualCameraMetadata virtualCameraMetadata = new VirtualCameraMetadata();
        Parcel parcel = Parcel.obtain();
        try {
            metadataNative.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            virtualCameraMetadata.metadata = parcel.marshall();
        } catch (Exception e) {
            Slog.w(TAG, "Failed to convert CameraMetadataNative to VirtualCameraMetadata.");
            return null;
        } finally {
            parcel.recycle();
        }
        return virtualCameraMetadata;
    }

    @Nullable
    private static CameraMetadataNative convertToCameraMetadataNative(
            @NonNull VirtualCameraMetadata virtualCameraMetadata) {
        CameraMetadataNative cameraMetadataNative = null;
        Parcel parcel = Parcel.obtain();
        try {
            parcel.unmarshall(virtualCameraMetadata.metadata, 0,
                    virtualCameraMetadata.metadata.length);
            parcel.setDataPosition(0);
            cameraMetadataNative = CameraMetadataNative.CREATOR.createFromParcel(parcel);
        } catch (Exception e) {
            Slog.w(TAG, "Failed to convert VirtualCameraMetadata to CameraMetadataNative.");
        } finally {
            parcel.recycle();
        }
        return cameraMetadataNative;
    }

    @Nullable
    private static CaptureRequest convertToCaptureRequest(
            @NonNull VirtualCameraMetadata virtualCameraMetadata) {
        CameraMetadataNative metadataNative = convertToCameraMetadataNative(virtualCameraMetadata);
        if (metadataNative != null) {
            // Only the settings of the CaptureRequest are useful to the VD owner app
            return new CaptureRequest.Builder(metadataNative, false /* reprocess */,
                    CameraCaptureSession.SESSION_ID_NONE /* reprocessableSessionId */,
                    "" /* logicalCameraId */, null /* physicalCameraIdSet */).build();
        }

        return null;
    }

    @Nullable
    private static android.companion.virtual.camera.ICaptureResultConsumer
            convertToVdmCaptureResultConsumer(
                @Nullable ICaptureResultConsumer serviceCaptureResultConsumer) {
        if (serviceCaptureResultConsumer != null) {
            return new android.companion.virtual.camera.ICaptureResultConsumer.Stub() {
                @Override
                public void acceptCaptureResult(long timestamp, CameraMetadataNative captureResult)
                        throws RemoteException {
                    serviceCaptureResultConsumer.acceptCaptureResult(timestamp,
                            convertToVirtualCameraMetadata(captureResult));
                }
            };
        }
        return null;
    }
}
