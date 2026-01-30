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

package com.android.server.wm;

import static android.app.WindowConfiguration.ROTATION_UNDEFINED;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_180;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.window.DesktopExperienceFlags;

import com.android.internal.annotations.VisibleForTesting;

/**
 * Provider for the camera rotation and sensor orientation info used to setup camera compat mode.
 *
 * <p>{@link AppCompatCameraRotationState} monitors whether the app currently using the camera is
 * on a built-in display (rotates with the built-in camera) or an external display, and returns the
 * display rotation apps should use to keep the preview upright:
 * <ul>
 *     <li>If running on a built-in display, relevant rotation is {@link Display#getRotation()}.
 *     <li>If running on an external display, only the sensor rotation matters, received from
 *     {@link OrientationEventListener}.
 * </ul>
 */
class AppCompatCameraRotationState {
    @Nullable
    @VisibleForTesting
    OrientationEventListener mOrientationEventListener;

    @NonNull
    private final DisplayContent mDisplayContent;

    private int mDisplayRotationIfExternal = ROTATION_UNDEFINED;

    AppCompatCameraRotationState(@NonNull DisplayContent displayContent) {
        mDisplayContent = displayContent;
    }

    /** Sets up listening to the orientation of the primary device if on an external display. */
    void start() {
        if (isExternalDisplay()) {
            // Listen to orientation changes of the host device.
            setupSensorOrientationListener();
        }
    }

    /** Disables {@link OrientationEventListener} if set up. */
    void dispose() {
        if (mOrientationEventListener != null) {
            mOrientationEventListener.disable();
            mOrientationEventListener = null;
        }
    }


    /** Creates and enables {@link OrientationEventListener}.  */
    void setupSensorOrientationListener() {
        mOrientationEventListener = new OrientationEventListener(
                mDisplayContent.mWmService.mContext) {
            @Override
            public void onOrientationChanged(int orientation) {
                synchronized (mDisplayContent.mWmService.mGlobalLock) {
                    mDisplayRotationIfExternal = transformSensorOrientationToDisplayRotation(
                            orientation);
                }
            }
        };

        mOrientationEventListener.enable();
    }

    /**
     * Whether the natural orientation (not the current rotation) of the camera is portrait.
     *
     * <p>This orientation is equal to the natural orientation of the display it is tied to
     * (built-in display).
     */
    boolean isCameraDeviceNaturalOrientationPortrait() {
        // Per CDD (7.5.5 C-1-1), camera sensor orientation and display natural orientation have to
        // be the same (portrait or landscape).
        // TODO(b/444213250): this is not always correct, for example for some landscape foldables,
        //  natural orientation of some displays and camera sensors may differ. Instead, query the
        //  camera sensors for their natural orientation. Also make sure no camera sensor sandboxing
        //  affects that.
        return getDisplayContentTiedToCamera().getNaturalOrientation() == ORIENTATION_PORTRAIT;
    }

    /**
     * Returns relevant rotation of the relevant "device", whether it is a camera or display.
     *
     *<p>This is the offset that apps should use to rotate the camera preview. Difference in this
     * value and what the app expects given their requested orientation informs camera compat setup.
     */
    @Surface.Rotation
    int getCameraDeviceRotation() {
        return isExternalDisplay() ? mDisplayRotationIfExternal : mDisplayContent.getRotation();
    }

    // TODO(b/425599049): support external cameras.
    /**
     * Whether the device relevant for camera is in portrait orientation.
     *
     * <p>This is either the display rotation when running on an internal display, or the camera
     * rotation when running on an external display.
     */
    boolean isCameraDeviceOrientationPortrait() {
        final int cameraDisplayRotation = getCameraDeviceRotation();
        final boolean isDisplayInItsNaturalOrientation = (cameraDisplayRotation == ROTATION_0
                || cameraDisplayRotation == ROTATION_180);
        // Display is in portrait if and only if: portrait device is in its natural orientation,
        // or landscape device is not in its natural orientation.
        // `isPortraitCamera <=> isDisplayInItsNaturalOrientation` is equivalent to
        // `isPortraitCamera XOR !isDisplayInItsNaturalOrientation`.
        return isCameraDeviceNaturalOrientationPortrait() ^ !isDisplayInItsNaturalOrientation;
    }

    @Surface.Rotation
    private int transformSensorOrientationToDisplayRotation(int orientation) {
        // Sensor rotation is continuous, and counted in the opposite direction from display
        // rotation.
        final int displayRotationInt = ((360 - orientation) + 360) % 360;
        // Choose the closest display rotation. When using the `OrientationEventListener`, this is
        // the recommended way in developer documentation for apps to orient the preview or a
        // captured image.
        if (displayRotationInt > 45 && displayRotationInt <= 135) {
            return ROTATION_90;
        } else if (displayRotationInt > 135 && displayRotationInt <= 225) {
            return ROTATION_180;
        } else if (displayRotationInt > 225 && displayRotationInt <= 315) {
            return ROTATION_270;
        } else {
            return ROTATION_0;
        }
    }

    @NonNull
    private DisplayContent getDisplayContentTiedToCamera() {
        return isExternalDisplay()
                // If camera app is on the external display, the display rotation should be
                // overridden to use the primary device rotation which the camera sensor is tied to.
                ? mDisplayContent.mWmService.getDefaultDisplayContentLocked()
                : mDisplayContent;
    }

    private boolean isExternalDisplay() {
        return DesktopExperienceFlags.ENABLE_CAMERA_COMPAT_EXTERNAL_DISPLAY_ROTATION_BUGFIX.isTrue()
                && mDisplayContent.getDisplay().getType() == Display.TYPE_EXTERNAL;
    }
}
