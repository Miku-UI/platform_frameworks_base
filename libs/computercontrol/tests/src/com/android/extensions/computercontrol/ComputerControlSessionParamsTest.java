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

package com.android.extensions.computercontrol;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.content.Context;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.view.Surface;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

import java.util.List;

@RequiresFlagsEnabled(android.companion.virtualdevice.flags.Flags.FLAG_COMPUTER_CONTROL_ACCESS)
@RunWith(AndroidJUnit4.class)
public class ComputerControlSessionParamsTest {
    private static final int WIDTH = 1080;
    private static final int HEIGHT = 1920;
    private static final int DPI = 160;
    private static final String NAME = "test";
    private static final boolean DISPLAY_ALWAYS_UNLOCKED = true;
    private static final boolean DISPLAY_ALWAYS_NOT_UNLOCKED = false;
    private static final Surface SURFACE = new Surface();
    private static final String TARGET_PACKAGE_1 = "com.android.foo";
    private static final String TARGET_PACKAGE_2 = "com.android.bar";
    private static final List<String> TARGET_PACKAGE_NAMES =
            List.of(TARGET_PACKAGE_1, TARGET_PACKAGE_2);

    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    private AutoCloseable mMockitoSession;

    @Before
    public void setUp() {
        mMockitoSession = MockitoAnnotations.openMocks(this);
    }

    @After
    public void tearDown() throws Exception {
        mMockitoSession.close();
    }

    // TODO(b/437849228): Test that targetPackageNames must be set.

    @Test
    public void builder_withMissingContextArg_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new ComputerControlSession.Params.Builder(null).build());
    }

    @Test
    public void builder_withSurface_withMissingDisplayWidthArg_throwsException() {
        assertThrows(IllegalArgumentException.class,
                ()
                        -> new ComputerControlSession.Params.Builder(mContext)
                                .setName(NAME)
                                .setDisplaySurface(SURFACE)
                                .setDisplayHeightPx(HEIGHT)
                                .setDisplayDpi(DPI)
                                .build());
    }

    @Test
    public void builder_withSurface__withMissingDisplayHeightArg_throwsException() {
        assertThrows(IllegalArgumentException.class,
                ()
                        -> new ComputerControlSession.Params.Builder(mContext)
                                .setName(NAME)
                                .setDisplaySurface(SURFACE)
                                .setDisplayWidthPx(WIDTH)
                                .setDisplayDpi(DPI)
                                .build());
    }

    @Test
    public void builder_withSurface__withMissingDisplayDpiArg_throwsException() {
        assertThrows(IllegalArgumentException.class,
                ()
                        -> new ComputerControlSession.Params.Builder(mContext)
                                .setName(NAME)
                                .setDisplaySurface(SURFACE)
                                .setDisplayWidthPx(WIDTH)
                                .setDisplayHeightPx(HEIGHT)
                                .build());
    }

    @Test
    public void builder_withMissingShouldAlwaysBeUnlockedArg_setsAlwaysUnlocked() {
        ComputerControlSession.Params params =
                new ComputerControlSession.Params.Builder(mContext)
                        .setName(NAME)
                        .setDisplaySurface(SURFACE)
                        .setDisplayWidthPx(WIDTH)
                        .setDisplayHeightPx(HEIGHT)
                        .setDisplayDpi(DPI)
                        .setDisplayAlwaysUnlocked(DISPLAY_ALWAYS_UNLOCKED)
                        .build();

        assertThat(params.isDisplayAlwaysUnlocked()).isEqualTo(DISPLAY_ALWAYS_UNLOCKED);
    }

    @Test
    public void builder_buildsCorrectParams() {
        ComputerControlSession.Params params =
                new ComputerControlSession.Params.Builder(mContext)
                        .setName(NAME)
                        .setTargetPackageNames(TARGET_PACKAGE_NAMES)
                        .setDisplaySurface(SURFACE)
                        .setDisplayWidthPx(WIDTH)
                        .setDisplayHeightPx(HEIGHT)
                        .setDisplayDpi(DPI)
                        .setDisplayAlwaysUnlocked(DISPLAY_ALWAYS_NOT_UNLOCKED)
                        .build();

        assertThat(params.getName()).isEqualTo(NAME);
        assertThat(params.getTargetPackageNames()).isEqualTo(TARGET_PACKAGE_NAMES);
        assertThat(params.getDisplayDpi()).isEqualTo(DPI);
        assertThat(params.getDisplayHeightPx()).isEqualTo(HEIGHT);
        assertThat(params.getDisplayWidthPx()).isEqualTo(WIDTH);
        assertThat(params.getDisplaySurface()).isEqualTo(SURFACE);
        assertThat(params.isDisplayAlwaysUnlocked()).isEqualTo(DISPLAY_ALWAYS_NOT_UNLOCKED);
    }
}
