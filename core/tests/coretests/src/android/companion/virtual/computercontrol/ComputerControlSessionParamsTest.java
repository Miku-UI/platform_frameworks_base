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

package android.companion.virtual.computercontrol;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.graphics.SurfaceTexture;
import android.os.Parcel;
import android.view.Surface;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class ComputerControlSessionParamsTest {

    private static final String COMPUTER_CONTROL_SESSION_NAME = "ComputerControlSessionName";
    private static final int DISPLAY_WIDTH = 1920;
    private static final int DISPLAY_HEIGHT = 1080;
    private static final int DISPLAY_DPI = 480;
    private static final String TARGET_PACKAGE_1 = "com.android.foo";
    private static final String TARGET_PACKAGE_2 = "com.android.bar";
    private static final List<String> TARGET_PACKAGE_NAMES =
            List.of(TARGET_PACKAGE_1, TARGET_PACKAGE_2);

    private Surface mSurface;

    @Before
    public void setUp() {
        mSurface = new Surface(new SurfaceTexture(0));
    }

    @Test
    public void parcelable_shouldRecreateSuccessfully() {
        ComputerControlSessionParams originalParams = new ComputerControlSessionParams.Builder()
                .setName(COMPUTER_CONTROL_SESSION_NAME)
                .setTargetPackageNames(TARGET_PACKAGE_NAMES)
                .setDisplayWidthPx(DISPLAY_WIDTH)
                .setDisplayHeightPx(DISPLAY_HEIGHT)
                .setDisplayDpi(DISPLAY_DPI)
                .setDisplaySurface(mSurface)
                .build();
        Parcel parcel = Parcel.obtain();
        originalParams.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        ComputerControlSessionParams params =
                ComputerControlSessionParams.CREATOR.createFromParcel(parcel);
        assertThat(params.getName()).isEqualTo(COMPUTER_CONTROL_SESSION_NAME);
        assertThat(params.getTargetPackageNames()).containsExactlyElementsIn(TARGET_PACKAGE_NAMES);
        assertThat(params.getDisplayWidthPx()).isEqualTo(DISPLAY_WIDTH);
        assertThat(params.getDisplayHeightPx()).isEqualTo(DISPLAY_HEIGHT);
        assertThat(params.getDisplayDpi()).isEqualTo(DISPLAY_DPI);
    }

    @Test
    public void build_unsetName_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ComputerControlSessionParams.Builder()
                        .setDisplayWidthPx(DISPLAY_WIDTH)
                        .setDisplayHeightPx(DISPLAY_HEIGHT)
                        .setDisplayDpi(DISPLAY_DPI)
                        .setDisplaySurface(mSurface)
                        .build());
    }

    // TODO(b/437849228): Test that targetPackageNames must be set.

    @Test
    public void build_setSurface_nonPositiveDisplayDimensions_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ComputerControlSessionParams.Builder()
                        .setName(COMPUTER_CONTROL_SESSION_NAME)
                        .setDisplayWidthPx(0)
                        .setDisplayHeightPx(DISPLAY_HEIGHT)
                        .setDisplayDpi(DISPLAY_DPI)
                        .setDisplaySurface(mSurface)
                        .build());
        assertThrows(IllegalArgumentException.class,
                () -> new ComputerControlSessionParams.Builder()
                        .setName(COMPUTER_CONTROL_SESSION_NAME)
                        .setDisplayWidthPx(DISPLAY_WIDTH)
                        .setDisplayHeightPx(0)
                        .setDisplayDpi(DISPLAY_DPI)
                        .setDisplaySurface(mSurface)
                        .build());
    }

    @Test
    public void build_setSurface_nonPositiveDisplayDpi_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ComputerControlSessionParams.Builder()
                        .setName(COMPUTER_CONTROL_SESSION_NAME)
                        .setDisplayWidthPx(DISPLAY_WIDTH)
                        .setDisplayHeightPx(DISPLAY_HEIGHT)
                        .setDisplayDpi(0)
                        .setDisplaySurface(mSurface)
                        .build());
    }
}
