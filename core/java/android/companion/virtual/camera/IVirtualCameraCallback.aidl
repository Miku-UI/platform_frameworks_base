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

package android.companion.virtual.camera;

import android.companion.virtual.camera.ICaptureResultConsumer;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.SessionConfiguration;
import android.view.Surface;

/**
 * Interface for the virtual camera service and system server to talk back to the virtual camera
 * owner.
 *
 * @hide
 */
oneway interface IVirtualCameraCallback {

    /**
     * Called when the client application calls
     * {@link android.hardware.camera2.CameraManager#openCamera}. This is the earliest signal that
     * this camera will be used. At this point, no stream is opened yet, nor any configuration took
     * place. The owner of the virtual camera can use this as signal to prepare the camera and
     * reduce latency for when
     * {@link android.hardware.camera2.CameraDevice#createCaptureSession(SessionConfiguration)} is
     * called and before
     * {@link
     * android.hardware.camera2.CameraCaptureSession.StateCallback#onConfigured(CameraCaptureSession)}
     * is called.
     */
    void onOpenCamera();

    /**
     * Called when there's a new camera session. This callback is sent when clients open and
     * configure the video session for the virtual camera.
     *
     * @param sessionParameters The {@link CaptureRequest} session parameters from the
     *      {@link SessionConfiguration} requested by the app using the virtual camera.
     *      The available Keys need to be set in the ANDROID_REQUEST_AVAILABLE_SESSION_KEYS tag of
     *      the {@link CameraCharacteristics}.
     * @param captureResultConsumer The consumer interface through which the virtual camera server
     *      consumes the CameraMetadataNative part of the CaptureResult. It is null if per frame
     *      camera metadata is not enabled.
     */
    void onConfigureSession(in CaptureRequest sessionParameters,
        in @nullable ICaptureResultConsumer captureResultConsumer);

    /**
     * Called when one of the requested stream has been configured by the virtual camera service and
     * is ready to receive data onto its {@link Surface}
     *
     * @param streamId The id of the configured stream
     * @param surface The surface to write data into for this stream
     * @param width The width of the surface
     * @param height The height of the surface
     * @param format The pixel format of the surface
     */
    void onStreamConfigured(int streamId, in Surface surface, int width, int height,
        int format);

    /**
     * The client application is requesting a camera frame for the given streamId and frameId.
     *
     * <p>The virtual camera needs to write the frame data in the {@link Surface} corresponding to
     * this stream that was provided during the
     * {@link #onStreamConfigured(int, Surface, int, int, int)} call.
     *
     * @param streamId The streamId for which the frame is requested. This corresponds to the
     *     streamId that was given in {@link #onStreamConfigured(int, Surface, int, int, int)}
     * @param frameId The frameId that is being requested. Each request will have a different
     *     frameId, that will be increasing for each call with a particular streamId.
     * @param captureRequest The capture request metadata provided by the app in association with
     *     the requested {@code frameId}. This is {@code null} id per frame camera metadata is not
     *     enabled or if unchanged from the previous frame.
     */
    void onProcessCaptureRequest(int streamId, long frameId,
        in @nullable CaptureRequest captureRequest);

    /**
     * The stream previously configured when
     * {@link #onStreamConfigured(int, Surface, int, int, int)} was called is now being closed and
     * associated resources can be freed. The Surface was disposed on the client side and should not
     * be used anymore by the virtual camera owner.
     *
     * @param streamId The id of the stream that was closed.
     */
    void onStreamClosed(int streamId);
}