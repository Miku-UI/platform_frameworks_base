/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.display;

import static android.view.Display.Mode.FLAG_SIZE_OVERRIDE;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_180;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.graphics.Point;
import android.graphics.Rect;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceControl;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.display.feature.flags.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * Tests for the {@link DisplayDevice} class.
 *
 * Build/Install/Run:
 * atest DisplayServicesTests:DisplayDeviceTest
 */
@SmallTest
@Presubmit
@RunWith(AndroidJUnit4.class)
public class DisplayDeviceTest {
    private final DisplayDeviceInfo mDisplayDeviceInfo = new DisplayDeviceInfo();
    private static final int WIDTH = 500;
    private static final int HEIGHT = 900;
    private static final int OTHER_WIDTH = 550;
    private static final int OTHER_HEIGHT = 950;
    private static final Point PORTRAIT_SIZE = new Point(WIDTH, HEIGHT);
    private static final Point PORTRAIT_OTHER_SIZE = new Point(OTHER_WIDTH, OTHER_HEIGHT);
    private static final Point PORTRAIT_DOUBLE_WIDTH = new Point(2 * WIDTH, HEIGHT);
    private static final Point LANDSCAPE_SIZE = new Point(HEIGHT, WIDTH);
    private static final Point LANDSCAPE_OTHER_SIZE = new Point(OTHER_HEIGHT, OTHER_WIDTH);
    private static final Point LANDSCAPE_DOUBLE_HEIGHT = new Point(HEIGHT, 2 * WIDTH);

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock
    private SurfaceControl.Transaction mMockTransaction;

    @Mock
    private DisplayAdapter mMockDisplayAdapter;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mDisplayDeviceInfo.width = WIDTH;
        mDisplayDeviceInfo.height = HEIGHT;
        mDisplayDeviceInfo.rotation = ROTATION_0;
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_ANISOTROPY_CORRECTED_MODES)
    public void testGetDisplaySurfaceDefaultSizeLocked_notRotated_anisotropyCorrection() {
        mDisplayDeviceInfo.type = Display.TYPE_EXTERNAL;
        mDisplayDeviceInfo.xDpi = 0.5f;
        mDisplayDeviceInfo.yDpi = 1.0f;
        DisplayDevice displayDevice = new FakeDisplayDevice(mDisplayDeviceInfo,
                mMockDisplayAdapter);
        assertThat(displayDevice.getDisplaySurfaceDefaultSizeLocked()).isEqualTo(
                PORTRAIT_DOUBLE_WIDTH);
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_ANISOTROPY_CORRECTED_MODES)
    public void testGetDisplaySurfaceDefaultSizeLocked_notRotated_noAnisotropyCorrection() {
        mDisplayDeviceInfo.type = Display.TYPE_INTERNAL;
        mDisplayDeviceInfo.xDpi = 0.5f;
        mDisplayDeviceInfo.yDpi = 1.0f;
        DisplayDevice displayDevice = new FakeDisplayDevice(mDisplayDeviceInfo,
                mMockDisplayAdapter);
        assertThat(displayDevice.getDisplaySurfaceDefaultSizeLocked()).isEqualTo(PORTRAIT_SIZE);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_ANISOTROPY_CORRECTED_MODES)
    public void testGetDisplaySurfaceDefaultSizeLocked_notRotated_userModeNotSet() {
        mDisplayDeviceInfo.type = Display.TYPE_EXTERNAL;
        mDisplayDeviceInfo.xDpi = 0.5f;
        mDisplayDeviceInfo.yDpi = 1.0f;
        DisplayDevice displayDevice = new FakeDisplayDevice(mDisplayDeviceInfo,
                mMockDisplayAdapter);
        assertThat(displayDevice.getDisplaySurfaceDefaultSizeLocked()).isEqualTo(
                PORTRAIT_SIZE);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_ANISOTROPY_CORRECTED_MODES)
    public void testGetDisplaySurfaceDefaultSizeLocked_notRotated_userModeNormal() {
        mDisplayDeviceInfo.type = Display.TYPE_EXTERNAL;
        mDisplayDeviceInfo.xDpi = 0.5f;
        mDisplayDeviceInfo.yDpi = 1.0f;
        FakeDisplayDevice displayDevice = new FakeDisplayDevice(mDisplayDeviceInfo,
                mMockDisplayAdapter);
        displayDevice.mUserPreferredMode = TestUtilsKt.createDisplayMode(
                /* id= */ 1, /* parentId= */ 2, /* flags= */ 0, OTHER_WIDTH, OTHER_HEIGHT);

        assertThat(displayDevice.getDisplaySurfaceDefaultSizeLocked()).isEqualTo(PORTRAIT_SIZE);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_ANISOTROPY_CORRECTED_MODES)
    public void testGetDisplaySurfaceDefaultSizeLocked_notRotated_userModeSizeOverride() {
        mDisplayDeviceInfo.type = Display.TYPE_EXTERNAL;
        mDisplayDeviceInfo.xDpi = 0.5f;
        mDisplayDeviceInfo.yDpi = 1.0f;
        FakeDisplayDevice displayDevice = new FakeDisplayDevice(mDisplayDeviceInfo,
                mMockDisplayAdapter);
        displayDevice.mUserPreferredMode = TestUtilsKt.createDisplayMode(
                /* id= */ 1, /* parentId= */ 2, FLAG_SIZE_OVERRIDE, OTHER_WIDTH, OTHER_HEIGHT);

        assertThat(displayDevice.getDisplaySurfaceDefaultSizeLocked())
                .isEqualTo(PORTRAIT_OTHER_SIZE);
    }

    @Test
    public void testGetDisplaySurfaceDefaultSizeLocked_notRotated() {
        DisplayDevice displayDevice = new FakeDisplayDevice(mDisplayDeviceInfo,
                mMockDisplayAdapter);
        assertThat(displayDevice.getDisplaySurfaceDefaultSizeLocked()).isEqualTo(PORTRAIT_SIZE);
    }

    @Test
    public void testGetDisplaySurfaceDefaultSizeLocked_rotation0() {
        DisplayDevice displayDevice = new FakeDisplayDevice(mDisplayDeviceInfo,
                mMockDisplayAdapter);
        displayDevice.setProjectionLocked(mMockTransaction, ROTATION_0, new Rect(), new Rect());
        assertThat(displayDevice.getDisplaySurfaceDefaultSizeLocked()).isEqualTo(PORTRAIT_SIZE);
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_ANISOTROPY_CORRECTED_MODES)
    public void testGetDisplaySurfaceDefaultSizeLocked_rotation90_anisotropyCorrection() {
        mDisplayDeviceInfo.type = Display.TYPE_EXTERNAL;
        mDisplayDeviceInfo.xDpi = 0.5f;
        mDisplayDeviceInfo.yDpi = 1.0f;
        DisplayDevice displayDevice = new FakeDisplayDevice(mDisplayDeviceInfo,
                mMockDisplayAdapter);
        displayDevice.setProjectionLocked(mMockTransaction, ROTATION_90, new Rect(), new Rect());
        assertThat(displayDevice.getDisplaySurfaceDefaultSizeLocked()).isEqualTo(
                LANDSCAPE_DOUBLE_HEIGHT);
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_ANISOTROPY_CORRECTED_MODES)
    public void testGetDisplaySurfaceDefaultSizeLocked_rotation90_noAnisotropyCorrection() {
        mDisplayDeviceInfo.type = Display.TYPE_INTERNAL;
        mDisplayDeviceInfo.xDpi = 0.5f;
        mDisplayDeviceInfo.yDpi = 1.0f;
        DisplayDevice displayDevice = new FakeDisplayDevice(mDisplayDeviceInfo,
                mMockDisplayAdapter);
        displayDevice.setProjectionLocked(mMockTransaction, ROTATION_90, new Rect(), new Rect());
        assertThat(displayDevice.getDisplaySurfaceDefaultSizeLocked()).isEqualTo(LANDSCAPE_SIZE);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_ANISOTROPY_CORRECTED_MODES)
    public void testGetDisplaySurfaceDefaultSizeLocked_rotation90_userModeNotSet() {
        mDisplayDeviceInfo.type = Display.TYPE_EXTERNAL;
        mDisplayDeviceInfo.xDpi = 0.5f;
        mDisplayDeviceInfo.yDpi = 1.0f;
        DisplayDevice displayDevice = new FakeDisplayDevice(mDisplayDeviceInfo,
                mMockDisplayAdapter);
        displayDevice.setProjectionLocked(mMockTransaction, ROTATION_90, new Rect(), new Rect());
        assertThat(displayDevice.getDisplaySurfaceDefaultSizeLocked()).isEqualTo(
                LANDSCAPE_SIZE);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_ANISOTROPY_CORRECTED_MODES)
    public void testGetDisplaySurfaceDefaultSizeLocked_rotation90_userModeNormal() {
        mDisplayDeviceInfo.type = Display.TYPE_EXTERNAL;
        mDisplayDeviceInfo.xDpi = 0.5f;
        mDisplayDeviceInfo.yDpi = 1.0f;
        FakeDisplayDevice displayDevice = new FakeDisplayDevice(mDisplayDeviceInfo,
                mMockDisplayAdapter);
        displayDevice.setProjectionLocked(mMockTransaction, ROTATION_90, new Rect(), new Rect());
        displayDevice.mUserPreferredMode = TestUtilsKt.createDisplayMode(
                /* id= */ 1, /* parentId= */ 2, /* flags= */ 0, OTHER_WIDTH, OTHER_HEIGHT);

        assertThat(displayDevice.getDisplaySurfaceDefaultSizeLocked()).isEqualTo(LANDSCAPE_SIZE);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_ANISOTROPY_CORRECTED_MODES)
    public void testGetDisplaySurfaceDefaultSizeLocked_rotation90_userModeSizeOverride() {
        mDisplayDeviceInfo.type = Display.TYPE_EXTERNAL;
        mDisplayDeviceInfo.xDpi = 0.5f;
        mDisplayDeviceInfo.yDpi = 1.0f;
        FakeDisplayDevice displayDevice = new FakeDisplayDevice(mDisplayDeviceInfo,
                mMockDisplayAdapter);
        displayDevice.setProjectionLocked(mMockTransaction, ROTATION_90, new Rect(), new Rect());
        displayDevice.mUserPreferredMode = TestUtilsKt.createDisplayMode(
                /* id= */ 1, /* parentId= */ 2, FLAG_SIZE_OVERRIDE, OTHER_WIDTH, OTHER_HEIGHT);

        assertThat(displayDevice.getDisplaySurfaceDefaultSizeLocked())
                .isEqualTo(LANDSCAPE_OTHER_SIZE);
    }

    @Test
    public void testGetDisplaySurfaceDefaultSizeLocked_rotation90() {
        DisplayDevice displayDevice = new FakeDisplayDevice(mDisplayDeviceInfo,
                mMockDisplayAdapter);
        displayDevice.setProjectionLocked(mMockTransaction, ROTATION_90, new Rect(), new Rect());
        assertThat(displayDevice.getDisplaySurfaceDefaultSizeLocked()).isEqualTo(LANDSCAPE_SIZE);
    }

    @Test
    public void testGetDisplaySurfaceDefaultSizeLocked_rotation180() {
        DisplayDevice displayDevice = new FakeDisplayDevice(mDisplayDeviceInfo,
                mMockDisplayAdapter);
        displayDevice.setProjectionLocked(mMockTransaction, ROTATION_180, new Rect(), new Rect());
        assertThat(displayDevice.getDisplaySurfaceDefaultSizeLocked()).isEqualTo(PORTRAIT_SIZE);
    }

    @Test
    public void testGetDisplaySurfaceDefaultSizeLocked_rotation270() {
        DisplayDevice displayDevice = new FakeDisplayDevice(mDisplayDeviceInfo,
                mMockDisplayAdapter);
        displayDevice.setProjectionLocked(mMockTransaction, ROTATION_270, new Rect(), new Rect());
        assertThat(displayDevice.getDisplaySurfaceDefaultSizeLocked()).isEqualTo(LANDSCAPE_SIZE);
    }

    @Test
    public void testSetDisplaySize_landscapeInstallRotation() {
        DisplayDevice displayDevice = new FakeDisplayDevice(mDisplayDeviceInfo,
                mMockDisplayAdapter);
        mDisplayDeviceInfo.installOrientation = Surface.ROTATION_0;
        mDisplayDeviceInfo.width = WIDTH;
        mDisplayDeviceInfo.height = 200;
        displayDevice.configureDisplaySizeLocked(mMockTransaction);
        verify(mMockTransaction).setDisplaySize(isNull(), eq(WIDTH), eq(200));

        Mockito.clearInvocations(mMockTransaction);

        mDisplayDeviceInfo.installOrientation = Surface.ROTATION_180;
        mDisplayDeviceInfo.width = 300;
        mDisplayDeviceInfo.height = 200;
        displayDevice.configureDisplaySizeLocked(mMockTransaction);
        verify(mMockTransaction).setDisplaySize(isNull(), eq(300), eq(200));
    }

    @Test
    public void testSetDisplaySize_portraitInstallRotation() {
        DisplayDevice displayDevice = new FakeDisplayDevice(mDisplayDeviceInfo,
                mMockDisplayAdapter);
        mDisplayDeviceInfo.installOrientation = Surface.ROTATION_90;
        mDisplayDeviceInfo.width = WIDTH;
        mDisplayDeviceInfo.height = 200;
        displayDevice.configureDisplaySizeLocked(mMockTransaction);
        verify(mMockTransaction).setDisplaySize(isNull(), eq(200), eq(WIDTH));

        Mockito.clearInvocations(mMockTransaction);

        mDisplayDeviceInfo.installOrientation = Surface.ROTATION_270;
        mDisplayDeviceInfo.width = 300;
        mDisplayDeviceInfo.height = 200;
        displayDevice.configureDisplaySizeLocked(mMockTransaction);
        verify(mMockTransaction).setDisplaySize(isNull(), eq(200), eq(300));
    }

    @Test
    public void testSetDisplaySize_invokedOnlyAfterResize() {
        DisplayDevice displayDevice = new FakeDisplayDevice(mDisplayDeviceInfo,
                mMockDisplayAdapter);
        mDisplayDeviceInfo.installOrientation = Surface.ROTATION_90;
        mDisplayDeviceInfo.width = 100;
        mDisplayDeviceInfo.height = 200;
        displayDevice.configureDisplaySizeLocked(mMockTransaction);
        verify(mMockTransaction).setDisplaySize(isNull(), eq(200), eq(100));

        Mockito.clearInvocations(mMockTransaction);

        displayDevice.configureDisplaySizeLocked(mMockTransaction);
        verify(mMockTransaction, never()).setDisplaySize(isNull(), anyInt(), anyInt());

        mDisplayDeviceInfo.width = 300;
        mDisplayDeviceInfo.height = 400;
        displayDevice.configureDisplaySizeLocked(mMockTransaction);
        verify(mMockTransaction).setDisplaySize(isNull(), eq(400), eq(300));
    }

    private static class FakeDisplayDevice extends DisplayDevice {
        private final DisplayDeviceInfo mDisplayDeviceInfo;
        private Display.Mode mUserPreferredMode = new Display.Mode.Builder().build();


        FakeDisplayDevice(DisplayDeviceInfo displayDeviceInfo, DisplayAdapter displayAdapter) {
            super(displayAdapter, /* displayToken= */ null, /* uniqueId= */ "",
                    InstrumentationRegistry.getInstrumentation().getContext());
            mDisplayDeviceInfo = displayDeviceInfo;
        }

        @Override
        public boolean hasStableUniqueId() {
            return false;
        }

        @Override
        public DisplayDeviceInfo getDisplayDeviceInfoLocked() {
            return mDisplayDeviceInfo;
        }

        @Override
        public Display.Mode getUserPreferredDisplayModeLocked() {
            return mUserPreferredMode;
        }
    }
}
