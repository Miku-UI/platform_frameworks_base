/*
 * Copyright 2025 The Android Open Source Project
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

import static android.hardware.camera2.CameraMetadata.CONTROL_AE_MODE_ON;

import static junit.framework.Assert.assertEquals;

import android.companion.virtual.camera.VirtualCameraSessionConfig;
import android.companion.virtualdevice.flags.Flags;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.impl.CameraMetadataNative;
import android.os.Parcel;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;

@Presubmit
@RunWith(AndroidJUnit4.class)
public class VirtualCameraSessionConfigTest {

    @Test
    @EnableFlags(Flags.FLAG_VIRTUAL_CAMERA_METADATA)
    public void testSessionParamsKeysEqual() {
        CameraMetadataNative metadataNative = new CameraMetadataNative();
        metadataNative.set(CaptureRequest.CONTROL_AE_MODE, CONTROL_AE_MODE_ON);

        CaptureRequest captureRequest = new CaptureRequest.Builder(metadataNative, false,
                CameraCaptureSession.SESSION_ID_NONE, "", null).build();
        VirtualCameraSessionConfig sessionConfig = new VirtualCameraSessionConfig(captureRequest);

        // we only check for the same Keys, the CaptureRequest can be repacked in the native layer
        // and the equality is not guaranteed
        CaptureRequest originalCaptureRequest = Objects.requireNonNull(
                sessionConfig.getSessionParameters());
        CaptureRequest recreatedCaptureRequest = Objects.requireNonNull(
                reparcel(sessionConfig).getSessionParameters());

        assertEquals(originalCaptureRequest.getKeys(), recreatedCaptureRequest.getKeys());

        for (CaptureRequest.Key<?> key : originalCaptureRequest.getKeys()) {
            assertEquals(originalCaptureRequest.get(key), recreatedCaptureRequest.get(key));
        }
    }

    private static VirtualCameraSessionConfig reparcel(VirtualCameraSessionConfig config) {
        Parcel parcel = Parcel.obtain();
        try {
            config.writeToParcel(parcel, /* flags= */ 0);
            parcel.setDataPosition(0);
            return VirtualCameraSessionConfig.CREATOR.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
    }
}
