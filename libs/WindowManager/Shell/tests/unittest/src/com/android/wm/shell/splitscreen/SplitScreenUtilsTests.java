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

package com.android.wm.shell.splitscreen;

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.view.Display.DEFAULT_DISPLAY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.res.Configuration;
import android.graphics.Rect;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.window.DisplayAreaInfo;
import android.window.WindowContainerToken;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.wm.shell.RootTaskDisplayAreaOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.common.split.SplitLayout;
import com.android.wm.shell.common.split.SplitScreenUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for {@link com.android.wm.shell.common.split.SplitScreenUtils} */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class SplitScreenUtilsTests extends ShellTestCase {
    @Mock
    private RootTaskDisplayAreaOrganizer mMockRootTDAOrganizer;
    @Mock
    private SplitLayout mMockSplitLayout;

    @Mock
    private DisplayAreaInfo mMockDisplayAreaInfo;
    @Mock
    private WindowContainerToken mMockToken;

    private static final int TEST_DISPLAY_ID = 2;

    Configuration mMockConfiguration;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mMockConfiguration = mMockDisplayAreaInfo.configuration;
    }

    @Test
    @DisableFlags(com.android.window.flags.Flags.FLAG_ENABLE_NON_DEFAULT_DISPLAY_SPLIT_BUGFIX)
    public void testIsLeftRightSplit_flagDisabled() {
        Configuration portraitTablet = new Configuration();
        portraitTablet.smallestScreenWidthDp = 720;
        portraitTablet.windowConfiguration.setMaxBounds(new Rect(0, 0, 500, 1000));
        Configuration landscapeTablet = new Configuration();
        landscapeTablet.smallestScreenWidthDp = 720;
        landscapeTablet.windowConfiguration.setMaxBounds(new Rect(0, 0, 1000, 500));
        Configuration portraitPhone = new Configuration();
        portraitPhone.smallestScreenWidthDp = 420;
        portraitPhone.windowConfiguration.setMaxBounds(new Rect(0, 0, 500, 1000));
        Configuration landscapePhone = new Configuration();
        landscapePhone.smallestScreenWidthDp = 420;
        landscapePhone.windowConfiguration.setMaxBounds(new Rect(0, 0, 1000, 500));

        // Allow L/R split in portrait = false
        assertTrue(SplitScreenUtils.isLeftRightSplit(false /* allowLeftRightSplitInPortrait */,
                landscapeTablet, DEFAULT_DISPLAY));
        assertTrue(SplitScreenUtils.isLeftRightSplit(false /* allowLeftRightSplitInPortrait */,
                landscapePhone, DEFAULT_DISPLAY));
        assertFalse(SplitScreenUtils.isLeftRightSplit(false /* allowLeftRightSplitInPortrait */,
                portraitTablet, DEFAULT_DISPLAY));
        assertFalse(SplitScreenUtils.isLeftRightSplit(false /* allowLeftRightSplitInPortrait */,
                portraitPhone, DEFAULT_DISPLAY));

        // Allow L/R split in portrait = true, only affects large screens
        assertFalse(SplitScreenUtils.isLeftRightSplit(true /* allowLeftRightSplitInPortrait */,
                landscapeTablet, DEFAULT_DISPLAY));
        assertTrue(SplitScreenUtils.isLeftRightSplit(true /* allowLeftRightSplitInPortrait */,
                landscapePhone, DEFAULT_DISPLAY));
        assertTrue(SplitScreenUtils.isLeftRightSplit(true /* allowLeftRightSplitInPortrait */,
                portraitTablet, DEFAULT_DISPLAY));
        assertFalse(SplitScreenUtils.isLeftRightSplit(true /* allowLeftRightSplitInPortrait */,
                portraitPhone, DEFAULT_DISPLAY));
    }

    @Test
    @EnableFlags(com.android.window.flags.Flags.FLAG_ENABLE_NON_DEFAULT_DISPLAY_SPLIT_BUGFIX)
    public void testIsLeftRightSplit_flagEnabled() {
        assertFalse(SplitScreenUtils.isLeftRightSplit(
                true /* allowLeftRightSplitInPortrait */,
                true /* isLargeScreen */,
                false /* isLandscape */,
                TEST_DISPLAY_ID));

        assertTrue(SplitScreenUtils.isLeftRightSplit(
                true /* allowLeftRightSplitInPortrait */,
                true /* isLargeScreen */,
                true /* isLandscape */,
                TEST_DISPLAY_ID));

        assertTrue(SplitScreenUtils.isLeftRightSplit(
                true /* allowLeftRightSplitInPortrait */,
                true /* isLargeScreen */,
                false /* isLandscape */,
                DEFAULT_DISPLAY));

        assertTrue(SplitScreenUtils.isLeftRightSplit(
                true /* allowLeftRightSplitInPortrait */,
                true /* isLargeScreen */,
                false /* isLandscape */,
                DEFAULT_DISPLAY));
    }

    @Test
    @DisableFlags(com.android.window.flags.Flags.FLAG_ENABLE_NON_DEFAULT_DISPLAY_SPLIT_BUGFIX)
    public void updateSplitLayoutConfig_flagDisabled_returnsEarly() {
        SplitScreenUtils.updateSplitLayoutConfig(
                mMockRootTDAOrganizer,
                TEST_DISPLAY_ID,
                mMockSplitLayout);

        verify(mMockRootTDAOrganizer, never()).getDisplayAreaInfo(anyInt());
        verify(mMockSplitLayout, never()).updateConfiguration(any(), anyInt());
    }

    @Test
    @EnableFlags(com.android.window.flags.Flags.FLAG_ENABLE_NON_DEFAULT_DISPLAY_SPLIT_BUGFIX)
    public void updateSplitLayoutConfig_flagEnabled_validDisplayAreaInfo_updatesSplitLayout() {
        when(mMockRootTDAOrganizer.getDisplayAreaInfo(TEST_DISPLAY_ID))
                .thenReturn(mMockDisplayAreaInfo);

        SplitScreenUtils.updateSplitLayoutConfig(
                mMockRootTDAOrganizer,
                TEST_DISPLAY_ID,
                mMockSplitLayout);

        verify(mMockSplitLayout).updateConfiguration(mMockConfiguration, TEST_DISPLAY_ID);
    }

    @Test
    @EnableFlags(com.android.window.flags.Flags.FLAG_ENABLE_NON_DEFAULT_DISPLAY_SPLIT_BUGFIX)
    public void updateSplitLayoutConfig_flagEnabled_nullDisplayAreaInfo_returnsEarly() {
        when(mMockRootTDAOrganizer.getDisplayAreaInfo(TEST_DISPLAY_ID)).thenReturn(null);

        SplitScreenUtils.updateSplitLayoutConfig(
                mMockRootTDAOrganizer,
                TEST_DISPLAY_ID,
                mMockSplitLayout);

        verify(mMockRootTDAOrganizer).getDisplayAreaInfo(TEST_DISPLAY_ID);
        verify(mMockSplitLayout, never()).updateConfiguration(any(), anyInt());
    }

    @Test
    @EnableFlags(com.android.window.flags.Flags.FLAG_ENABLE_NON_DEFAULT_DISPLAY_SPLIT_BUGFIX)
    public void updateSplitLayoutConfig_flagEnabled_validDisplayAreaInfo_nullSplitLayout_noError() {
        when(mMockRootTDAOrganizer.getDisplayAreaInfo(TEST_DISPLAY_ID))
                .thenReturn(mMockDisplayAreaInfo);

        SplitScreenUtils.updateSplitLayoutConfig(
                mMockRootTDAOrganizer,
                TEST_DISPLAY_ID,
                null /* splitLayout is null */);

        verify(mMockRootTDAOrganizer).getDisplayAreaInfo(TEST_DISPLAY_ID);
    }

    @Test
    public void getTargetWindowingModeWhenExitSplit_freeformDisplay_returnsFullscreen() {
        // Create a DisplayAreaInfo with a freeform windowing mode.
        final DisplayAreaInfo displayAreaInfo = new DisplayAreaInfo(mMockToken, TEST_DISPLAY_ID, 0);
        displayAreaInfo.configuration.windowConfiguration.setWindowingMode(
                WINDOWING_MODE_FREEFORM);
        when(mMockRootTDAOrganizer.getDisplayAreaInfo(TEST_DISPLAY_ID)).thenReturn(displayAreaInfo);

        final int windowingMode = SplitScreenUtils.getTargetWindowingModeWhenExitSplit(
                mMockRootTDAOrganizer, TEST_DISPLAY_ID);

        assertEquals(WINDOWING_MODE_FULLSCREEN, windowingMode);
    }

    @Test
    public void getTargetWindowingModeWhenExitSplit_nonFreeformDisplay_returnsUndefined() {
        // Create a DisplayAreaInfo with a fullscreen windowing mode.
        final DisplayAreaInfo displayAreaInfo = new DisplayAreaInfo(mMockToken, TEST_DISPLAY_ID, 0);
        displayAreaInfo.configuration.windowConfiguration.setWindowingMode(
                WINDOWING_MODE_FULLSCREEN);
        when(mMockRootTDAOrganizer.getDisplayAreaInfo(TEST_DISPLAY_ID)).thenReturn(displayAreaInfo);

        // Call the method under test and assert that the returned windowing mode is UNDEFINED.
        assertEquals("Should return UNDEFINED for FULLSCREEN display", WINDOWING_MODE_UNDEFINED,
                SplitScreenUtils.getTargetWindowingModeWhenExitSplit(
                        mMockRootTDAOrganizer, TEST_DISPLAY_ID));

        // Update DisplayAreaInfo to have an undefined windowing mode.
        displayAreaInfo.configuration.windowConfiguration.setWindowingMode(
                WINDOWING_MODE_UNDEFINED);
        when(mMockRootTDAOrganizer.getDisplayAreaInfo(TEST_DISPLAY_ID)).thenReturn(displayAreaInfo);

        // Call the method under test again and assert that the returned windowing mode is
        // UNDEFINED.
        assertEquals("Should return UNDEFINED for UNDEFINED display", WINDOWING_MODE_UNDEFINED,
                SplitScreenUtils.getTargetWindowingModeWhenExitSplit(
                        mMockRootTDAOrganizer, TEST_DISPLAY_ID));
    }

    @Test
    public void getTargetWindowingModeWhenExitSplit_nullDisplayAreaInfo_throwsException() {
        when(mMockRootTDAOrganizer.getDisplayAreaInfo(TEST_DISPLAY_ID)).thenReturn(null);

        assertThrows(NullPointerException.class,
                () -> SplitScreenUtils.getTargetWindowingModeWhenExitSplit(
                        mMockRootTDAOrganizer, TEST_DISPLAY_ID));
    }
}
