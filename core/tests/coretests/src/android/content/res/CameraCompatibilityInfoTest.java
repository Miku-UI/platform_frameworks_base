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

package android.content.res;

import static android.app.WindowConfiguration.ROTATION_UNDEFINED;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.os.Parcel;
import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class CameraCompatibilityInfoTest {

    @Test
    public void testDefault() {
        final CameraCompatibilityInfo info = new CameraCompatibilityInfo.Builder()
                .build();

        assertEquals(ROTATION_UNDEFINED, info.getRotateAndCropRotation());
        assertFalse(info.shouldOverrideSensorOrientation());
        assertFalse(info.shouldLetterboxForCameraCompat());
        assertEquals(ROTATION_UNDEFINED, info.getDisplayRotationSandbox());
        assertTrue(info.shouldAllowTransformInverseDisplay());
    }

    @Test
    public void testBuilderAndGetters() {
        final CameraCompatibilityInfo info = new CameraCompatibilityInfo.Builder()
                .setRotateAndCropRotation(ROTATION_90)
                .setShouldOverrideSensorOrientation(true)
                .setShouldLetterboxForCameraCompat(true)
                .setDisplayRotationSandbox(ROTATION_270)
                .setShouldAllowTransformInverseDisplay(false)
                .build();

        assertEquals(ROTATION_90, info.getRotateAndCropRotation());
        assertTrue(info.shouldOverrideSensorOrientation());
        assertTrue(info.shouldLetterboxForCameraCompat());
        assertEquals(ROTATION_270, info.getDisplayRotationSandbox());
        assertFalse(info.shouldAllowTransformInverseDisplay());
    }

    @Test
    public void testEqualsAndHashCode() {
        final CameraCompatibilityInfo info1 = new CameraCompatibilityInfo.Builder()
                .setRotateAndCropRotation(ROTATION_90)
                .build();
        final CameraCompatibilityInfo info2 = new CameraCompatibilityInfo.Builder()
                .setRotateAndCropRotation(ROTATION_90)
                .build();
        final CameraCompatibilityInfo info3 = new CameraCompatibilityInfo.Builder()
                .setShouldOverrideSensorOrientation(true)
                .build();

        assertEquals(info1, info2);
        assertEquals(info1.hashCode(), info2.hashCode());
        assertNotEquals(info1, info3);
        assertNotEquals(info1.hashCode(), info3.hashCode());
        assertNotEquals(null, info1);
        assertNotEquals(new Object(), info1);
    }

    @Test
    public void testParcelable() {
        final CameraCompatibilityInfo originalInfo = new CameraCompatibilityInfo.Builder()
                .setRotateAndCropRotation(ROTATION_90)
                .setShouldOverrideSensorOrientation(true)
                .setShouldLetterboxForCameraCompat(true)
                .setDisplayRotationSandbox(ROTATION_90)
                .setShouldAllowTransformInverseDisplay(false)
                .build();

        final Parcel parcel = Parcel.obtain();
        originalInfo.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        final CameraCompatibilityInfo newInfo = CameraCompatibilityInfo.CREATOR.createFromParcel(
                parcel);
        parcel.recycle();

        assertEquals(originalInfo, newInfo);
    }

    @Test
    public void testIsCameraCompatModeActive() {
        // Default object should not be active.
        final CameraCompatibilityInfo defaultInfo = new CameraCompatibilityInfo.Builder().build();
        assertFalse(CameraCompatibilityInfo.isCameraCompatModeActive(defaultInfo));

        // Any non-default value should make it active.
        final CameraCompatibilityInfo info1 = new CameraCompatibilityInfo.Builder()
                .setRotateAndCropRotation(ROTATION_90)
                .build();
        assertTrue(CameraCompatibilityInfo.isCameraCompatModeActive(info1));

        final CameraCompatibilityInfo info2 = new CameraCompatibilityInfo.Builder()
                .setShouldOverrideSensorOrientation(true)
                .build();
        assertTrue(CameraCompatibilityInfo.isCameraCompatModeActive(info2));

        final CameraCompatibilityInfo info3 = new CameraCompatibilityInfo.Builder()
                .setShouldLetterboxForCameraCompat(true)
                .build();
        assertTrue(CameraCompatibilityInfo.isCameraCompatModeActive(info3));

        final CameraCompatibilityInfo info4 = new CameraCompatibilityInfo.Builder()
                .setDisplayRotationSandbox(ROTATION_90)
                .build();
        assertTrue(CameraCompatibilityInfo.isCameraCompatModeActive(info4));
    }
}
