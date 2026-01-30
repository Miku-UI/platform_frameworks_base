/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.app;

import static android.app.WindowConfiguration.ROTATION_UNDEFINED;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_180;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.Surface;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

// TODO(b/430274604): Remove after WindowManager -> Camera Framework communication for camera compat
// moves to CompatibilityInfo.
/**
 * Stores Camera Compat information about a particular Task.
 * @hide
 */
public class CameraCompatTaskInfo implements Parcelable {
    /**
     * Undefined camera compat mode.
     */
    public static final int CAMERA_COMPAT_UNSPECIFIED = 0;

    /**
     * The value to use when no camera compat treatment should be applied to a windowed task.
     */
    public static final int CAMERA_COMPAT_NONE = 1;

    /**
     * The value to use when camera compat treatment should be applied to an activity requesting
     * portrait orientation, while a device is in landscape.
     */
    public static final int CAMERA_COMPAT_PORTRAIT_DEVICE_IN_LANDSCAPE = 2;

    /**
     * The value to use when camera compat treatment should be applied to an activity requesting
     * landscape orientation, while a device is in landscape.
     */
    public static final int CAMERA_COMPAT_LANDSCAPE_DEVICE_IN_LANDSCAPE = 3;

    /**
     * The value to use when camera compat treatment should be applied to an activity requesting
     * portrait orientation, while a device is in portrait.
     */
    public static final int CAMERA_COMPAT_PORTRAIT_DEVICE_IN_PORTRAIT = 4;

    /**
     * The value to use when camera compat treatment should be applied to an activity requesting
     * landscape orientation, while a device is in portrait.
     */
    public static final int CAMERA_COMPAT_LANDSCAPE_DEVICE_IN_PORTRAIT = 5;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = { "CAMERA_COMPAT_" }, value = {
            CAMERA_COMPAT_UNSPECIFIED,
            CAMERA_COMPAT_NONE,
            CAMERA_COMPAT_PORTRAIT_DEVICE_IN_LANDSCAPE,
            CAMERA_COMPAT_LANDSCAPE_DEVICE_IN_LANDSCAPE,
            CAMERA_COMPAT_PORTRAIT_DEVICE_IN_PORTRAIT,
            CAMERA_COMPAT_LANDSCAPE_DEVICE_IN_PORTRAIT,
    })
    public @interface CameraCompatMode {}

    /**
     * Whether the camera activity is letterboxed to emulate expected aspect ratio for
     * fixed-orientation apps.
     *
     * <p>This field is used by the WM and the camera framework, to coordinate camera compat mode
     * setup.
     */
    // TODO(b/414347702): Revisit data structure.
    @CameraCompatMode
    public int cameraCompatMode = CAMERA_COMPAT_UNSPECIFIED;

    /**
     * Real display rotation, never affected by camera compat sandboxing.
     *
     * <p>This value is used by the Camera Framework to calculate rotate-and-crop rotation degrees.
     */
    // TODO(b/414347702): Revisit data structure.
    @Surface.Rotation
    public int displayRotation = ROTATION_UNDEFINED;

    private CameraCompatTaskInfo() {
        // Do nothing
    }

    @NonNull
    static CameraCompatTaskInfo create() {
        return new CameraCompatTaskInfo();
    }

    private CameraCompatTaskInfo(Parcel source) {
        readFromParcel(source);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<CameraCompatTaskInfo> CREATOR =
            new Creator<>() {
                @Override
                public CameraCompatTaskInfo createFromParcel(Parcel in) {
                    return new CameraCompatTaskInfo(in);
                }

                @Override
                public CameraCompatTaskInfo[] newArray(int size) {
                    return new CameraCompatTaskInfo[size];
                }
            };

    /**
     * Reads the CameraCompatTaskInfo from a parcel.
     */
    void readFromParcel(Parcel source) {
        cameraCompatMode = source.readInt();
        displayRotation = source.readInt();
    }

    /**
     * Writes the CameraCompatTaskInfo to a parcel.
     */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(cameraCompatMode);
        dest.writeInt(displayRotation);
    }

    /**
     * @return  {@code true} if the camera compat parameters that are important for task organizers
     * are equal.
     */
    public boolean equalsForTaskOrganizer(@Nullable CameraCompatTaskInfo that) {
        if (that == null) {
            return false;
        }
        return cameraCompatMode == that.cameraCompatMode
                && displayRotation == that.displayRotation;
    }

    /**
     * @return {@code true} if parameters that are important for size compat have changed.
     */
    public boolean equalsForCompatUi(@Nullable CameraCompatTaskInfo that) {
        if (that == null) {
            return false;
        }
        return cameraCompatMode == that.cameraCompatMode
                && displayRotation == that.displayRotation;
    }

    @Override
    public String toString() {
        return "CameraCompatTaskInfo { cameraCompatMode="
                + cameraCompatModeToString(cameraCompatMode)
                + displayRotationToString(displayRotation)
                + "}";
    }

    /**
     * Returns the sandboxed display rotation based on the given {@code cameraCompatMode}.
     *
     * <p>This will be what the app likely expects in its requested orientation while running on a
     * device with portrait natural orientation: `CAMERA_COMPAT_PORTRAIT_*` is 0, and
     * `CAMERA_COMPAT_LANDSCAPE_*` is 90.
     *
     * @return {@link WindowConfiguration#ROTATION_UNDEFINED} if not in camera compat mode.
     */
    @Surface.Rotation
    public static int getDisplayRotationFromCameraCompatMode(@CameraCompatMode int
            cameraCompatMode) {
        return switch (cameraCompatMode) {
            case CAMERA_COMPAT_PORTRAIT_DEVICE_IN_LANDSCAPE,
                 CAMERA_COMPAT_PORTRAIT_DEVICE_IN_PORTRAIT -> ROTATION_0;
            case CAMERA_COMPAT_LANDSCAPE_DEVICE_IN_LANDSCAPE,
                 CAMERA_COMPAT_LANDSCAPE_DEVICE_IN_PORTRAIT -> ROTATION_90;
            default -> ROTATION_UNDEFINED;
        };
    }

    /** Human readable version of the camera compat mode. */
    @NonNull
    public static String cameraCompatModeToString(@CameraCompatMode int cameraCompatMode) {
        return switch (cameraCompatMode) {
            case CAMERA_COMPAT_UNSPECIFIED -> "undefined";
            case CAMERA_COMPAT_NONE -> "inactive";
            case CAMERA_COMPAT_PORTRAIT_DEVICE_IN_LANDSCAPE ->
                    "app-portrait-device-landscape";
            case CAMERA_COMPAT_LANDSCAPE_DEVICE_IN_LANDSCAPE ->
                    "app-landscape-device-landscape";
            case CAMERA_COMPAT_PORTRAIT_DEVICE_IN_PORTRAIT ->
                    "app-portrait-device-portrait";
            case CAMERA_COMPAT_LANDSCAPE_DEVICE_IN_PORTRAIT ->
                    "app-landscape-device-portrait";
            default -> throw new AssertionError("Unexpected camera compat mode: "
                    + cameraCompatMode);
        };
    }

    /** Human readable version of the camera compat mode. */
    @NonNull
    public static String displayRotationToString(@Surface.Rotation int displayRotation) {
        return switch (displayRotation) {
            case ROTATION_UNDEFINED -> "undefined";
            case ROTATION_0 -> "0";
            case ROTATION_90 -> "90";
            case ROTATION_180 -> "180";
            case ROTATION_270 -> "270";
            default -> throw new AssertionError("Unexpected display rotation: " + displayRotation);
        };
    }
}
