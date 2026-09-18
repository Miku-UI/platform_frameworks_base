/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.server.display.persistence.PersistentDataStoreTestUtilsKt.createInMemoryPersistentDataStore;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.PropertyInvalidatedCache;
import android.content.Context;
import android.graphics.Point;
import android.os.IBinder;
import android.util.CopyOnWriteSparseArray;
import android.util.SparseArray;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.Surface;
import android.view.SurfaceControl;

import androidx.test.filters.SmallTest;

import com.android.server.display.layout.Layout;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.concurrent.Executor;

@SmallTest
public class LogicalDisplayTest {
    private static final int DISPLAY_ID = 0;
    private static final int LAYER_STACK = 0;
    private static final int DISPLAY_WIDTH = 100;
    private static final int DISPLAY_HEIGHT = 200;
    private static final int MODE_ID = 1;
    private static final int OTHER_MODE_ID = 2;

    private LogicalDisplay mLogicalDisplay;
    private DisplayDevice mDisplayDevice;
    private DisplayAdapter mDisplayAdapter;
    private Context mContext;
    private IBinder mDisplayToken;
    private DisplayDeviceRepository mDeviceRepo;
    private final DisplayDeviceInfo mDisplayDeviceInfo = new DisplayDeviceInfo();
    private Executor mExecutor = mock(Executor.class);
    private CopyOnWriteSparseArray<LogicalDisplay.CachedDisplayInfo> mDisplayInfoCacheMocked =
            mock(CopyOnWriteSparseArray.class);

    @Before
    public void setUp() {
        // Share classloader to allow package private access.
        System.setProperty("dexmaker.share_classloader", "true");
        mDisplayDevice = mock(DisplayDevice.class);
        mDisplayAdapter = mock(DisplayAdapter.class);
        mContext = mock(Context.class);
        mDisplayToken = mock(IBinder.class);
        mLogicalDisplay = new LogicalDisplay(DISPLAY_ID, LAYER_STACK, mDisplayDevice,
                mDisplayInfoCacheMocked);

        mDisplayDeviceInfo.copyFrom(new DisplayDeviceInfo());
        mDisplayDeviceInfo.width = DISPLAY_WIDTH;
        mDisplayDeviceInfo.height = DISPLAY_HEIGHT;
        mDisplayDeviceInfo.touch = DisplayDeviceInfo.TOUCH_INTERNAL;
        mDisplayDeviceInfo.modeId = MODE_ID;
        mDisplayDeviceInfo.supportedModes = new Display.Mode[] {new Display.Mode(MODE_ID,
                DISPLAY_WIDTH, DISPLAY_HEIGHT, /* refreshRate= */ 60)};
        when(mDisplayDevice.getDisplayDeviceInfoLocked()).thenReturn(mDisplayDeviceInfo);

        DisplayDeviceConfig mockDisplayDeviceConfig = mock(DisplayDeviceConfig.class);
        when(mDisplayDevice.getDisplayDeviceConfig()).thenReturn(mockDisplayDeviceConfig);
        when(mockDisplayDeviceConfig.getFrameRateVelocityMapping()).thenReturn(new ArrayList<>());

        // Disable binder caches in this process.
        PropertyInvalidatedCache.disableForTestMode();

        mDeviceRepo = new DisplayDeviceRepository(new DisplayManagerService.SyncRoot(),
                createInMemoryPersistentDataStore(), /* stableEdidsFlag= */ true);
        mDeviceRepo.onDisplayDeviceEvent(mDisplayDevice, DisplayAdapter.DISPLAY_DEVICE_EVENT_ADDED);
        mLogicalDisplay.updateLocked(mDeviceRepo);
    }

    @Test
    public void testLetterbox() {
        mLogicalDisplay = new LogicalDisplay(DISPLAY_ID, LAYER_STACK, mDisplayDevice,
                mDisplayInfoCacheMocked);
        mDisplayDeviceInfo.xDpi = 0.5f;
        mDisplayDeviceInfo.yDpi = 1.0f;

        mLogicalDisplay.updateLocked(mDeviceRepo);
        var originalDisplayInfo = mLogicalDisplay.getDisplayInfoLocked();
        assertEquals(DISPLAY_WIDTH, originalDisplayInfo.logicalWidth);
        assertEquals(DISPLAY_HEIGHT, originalDisplayInfo.logicalHeight);

        SurfaceControl.Transaction t = mock(SurfaceControl.Transaction.class);
        mLogicalDisplay.configureDisplayLocked(t, mDisplayDevice, false, mExecutor);
        assertEquals(new Point(0, 0), mLogicalDisplay.getDisplayPosition());

        /*
         * Content is too wide, should become letterboxed
         *  ______DISPLAY_WIDTH________
         * |                        |
         * |________________________|
         * |                        |
         * |       CONTENT          |
         * |                        |
         * |________________________|
         * |                        |
         * |________________________|
         */
        // Make a wide application content, by reducing its height.
        DisplayInfo displayInfo = new DisplayInfo();
        displayInfo.logicalWidth = DISPLAY_WIDTH;
        displayInfo.logicalHeight = DISPLAY_HEIGHT / 2;
        mLogicalDisplay.setDisplayInfoOverrideFromWindowManagerLocked(displayInfo);

        mLogicalDisplay.configureDisplayLocked(t, mDisplayDevice, false, mExecutor);
        assertEquals(new Point(0, DISPLAY_HEIGHT / 4), mLogicalDisplay.getDisplayPosition());
    }

    @Test
    public void testNoLetterbox_noAnisotropyCorrectionIfAnisotropicModesEnabled() {
        mDisplayDeviceInfo.type = Display.TYPE_EXTERNAL;
        mLogicalDisplay = new LogicalDisplay(DISPLAY_ID, LAYER_STACK, mDisplayDevice,
                /*isSyncedResolutionSwitchEnabled=*/ true, mDisplayInfoCacheMocked);

        // In case of Anisotropy of pixels, then the content should be rescaled so it would adjust
        // to using the whole screen. This is because display will rescale it back to fill the
        // screen (in case the display menu setting is set to stretch the pixels across the display)
        mDisplayDeviceInfo.xDpi = 0.5f;
        mDisplayDeviceInfo.yDpi = 1.0f;

        mLogicalDisplay.updateLocked(mDeviceRepo);
        var originalDisplayInfo = mLogicalDisplay.getDisplayInfoLocked();
        // Content width not scaled
        assertEquals(DISPLAY_WIDTH, originalDisplayInfo.logicalWidth);
        assertEquals(DISPLAY_HEIGHT, originalDisplayInfo.logicalHeight);

        SurfaceControl.Transaction t = mock(SurfaceControl.Transaction.class);
        mLogicalDisplay.configureDisplayLocked(t, mDisplayDevice, false, mExecutor);

        assertEquals(new Point(0, 0), mLogicalDisplay.getDisplayPosition());
    }

    @Test
    public void testPillarbox() {
        mLogicalDisplay = new LogicalDisplay(DISPLAY_ID, LAYER_STACK, mDisplayDevice,
                mDisplayInfoCacheMocked);
        mDisplayDeviceInfo.xDpi = 0.5f;
        mDisplayDeviceInfo.yDpi = 1.0f;

        DisplayInfo displayInfo = new DisplayInfo();
        displayInfo.rotation = Surface.ROTATION_90;
        displayInfo.logicalWidth = DISPLAY_WIDTH;
        displayInfo.logicalHeight = DISPLAY_HEIGHT;
        mDisplayDeviceInfo.flags = DisplayDeviceInfo.FLAG_ROTATES_WITH_CONTENT;
        mLogicalDisplay.setDisplayInfoOverrideFromWindowManagerLocked(displayInfo);
        mLogicalDisplay.updateLocked(mDeviceRepo);

        var updatedDisplayInfo = mLogicalDisplay.getDisplayInfoLocked();
        assertEquals(Surface.ROTATION_90, updatedDisplayInfo.rotation);
        assertEquals(DISPLAY_WIDTH, updatedDisplayInfo.logicalWidth);
        assertEquals(DISPLAY_HEIGHT, updatedDisplayInfo.logicalHeight);

        /*
         * Content is too tall, should become pillarboxed
         *  ______DISPLAY_WIDTH________
         * |    |                |    |
         * |    |                |    |
         * |    |                |    |
         * |    |   CONTENT      |    |
         * |    |                |    |
         * |    |                |    |
         * |____|________________|____|
         */

        SurfaceControl.Transaction t = mock(SurfaceControl.Transaction.class);
        mLogicalDisplay.configureDisplayLocked(t, mDisplayDevice, false, mExecutor);

        assertEquals(new Point(75, 0), mLogicalDisplay.getDisplayPosition());
    }

    @Test
    public void testBrightnessConfigurationFromDisplayDevice() {
        mDisplayDeviceInfo.brightnessMinimum = 0.12f;
        mDisplayDeviceInfo.brightnessDim = 0.34f;
        mDisplayDeviceInfo.brightnessDefault = 0.56f;
        mDisplayDeviceInfo.brightnessMaximum = 0.78f;

        mLogicalDisplay = new LogicalDisplay(DISPLAY_ID, LAYER_STACK, mDisplayDevice,
                mDisplayInfoCacheMocked);
        mLogicalDisplay.updateLocked(mDeviceRepo);

        DisplayInfo info = mLogicalDisplay.getDisplayInfoLocked();
        assertThat(info.brightnessMinimum).isEqualTo(0.12f);
        assertThat(info.brightnessDim).isEqualTo(0.34f);
        assertThat(info.brightnessDefault).isEqualTo(0.56f);
        assertThat(info.brightnessMaximum).isEqualTo(0.78f);
    }

    @Test
    public void testGetDisplayPosition() {
        mDisplayDeviceInfo.type = Display.TYPE_EXTERNAL;
        mLogicalDisplay.updateLocked(mDeviceRepo);
        Point expectedPosition = new Point();

        SurfaceControl.Transaction t = mock(SurfaceControl.Transaction.class);
        mLogicalDisplay.configureDisplayLocked(t, mDisplayDevice, false, mExecutor);
        assertEquals(expectedPosition, mLogicalDisplay.getDisplayPosition());

        expectedPosition.set(20, 40);
        mLogicalDisplay.setDisplayOffsetsLocked(20, 40);
        mLogicalDisplay.configureDisplayLocked(t, mDisplayDevice, false, mExecutor);
        assertEquals(expectedPosition, mLogicalDisplay.getDisplayPosition());

        DisplayInfo displayInfo = new DisplayInfo();
        displayInfo.logicalWidth = DISPLAY_WIDTH;
        displayInfo.logicalHeight = DISPLAY_HEIGHT;
        // Rotation sent from WindowManager is always taken into account by LogicalDisplay
        // not matter whether FLAG_ROTATES_WITH_CONTENT is set or not.
        // This is because WindowManager takes care of rotation and expects that LogicalDisplay
        // will follow the rotation supplied by WindowManager
        expectedPosition.set(115, -20);
        displayInfo.rotation = Surface.ROTATION_90;
        mLogicalDisplay.setDisplayInfoOverrideFromWindowManagerLocked(displayInfo);
        mLogicalDisplay.configureDisplayLocked(t, mDisplayDevice, false, mExecutor);
        assertEquals(expectedPosition, mLogicalDisplay.getDisplayPosition());

        expectedPosition.set(40, -20);
        mDisplayDeviceInfo.flags = DisplayDeviceInfo.FLAG_ROTATES_WITH_CONTENT;
        mLogicalDisplay.updateLocked(mDeviceRepo);
        displayInfo.logicalWidth = DISPLAY_HEIGHT;
        displayInfo.logicalHeight = DISPLAY_WIDTH;
        displayInfo.rotation = Surface.ROTATION_90;
        mLogicalDisplay.setDisplayInfoOverrideFromWindowManagerLocked(displayInfo);
        mLogicalDisplay.configureDisplayLocked(t, mDisplayDevice, false, mExecutor);
        assertEquals(expectedPosition, mLogicalDisplay.getDisplayPosition());
    }

    @Test
    public void testSetDisplaySizeIsCalledDuringConfigureDisplayLocked() {
        mLogicalDisplay = new LogicalDisplay(DISPLAY_ID, LAYER_STACK, mDisplayDevice,
                /*isSyncedResolutionSwitchEnabled=*/ true, mDisplayInfoCacheMocked);
        mLogicalDisplay.updateLocked(mDeviceRepo);
        SurfaceControl.Transaction t = mock(SurfaceControl.Transaction.class);
        mLogicalDisplay.configureDisplayLocked(t, mDisplayDevice, false, mExecutor);
        verify(mDisplayDevice).configureDisplaySizeLocked(eq(t), any());
    }

    @Test
    public void testDisplayInputFlags() {
        DisplayDevice displayDevice = new DisplayDevice(mDisplayAdapter, mDisplayToken,
                "unique_display_id", mContext) {
            @Override
            public boolean hasStableUniqueId() {
                return false;
            }

            @Override
            public DisplayDeviceInfo getDisplayDeviceInfoLocked() {
                return mDisplayDeviceInfo;
            }
        };
        SurfaceControl.Transaction t = mock(SurfaceControl.Transaction.class);
        mLogicalDisplay.configureDisplayLocked(t, displayDevice, false, mExecutor);
        verify(t).setDisplayFlags(any(), eq(SurfaceControl.DISPLAY_RECEIVES_INPUT));
        reset(t);

        mDisplayDeviceInfo.touch = DisplayDeviceInfo.TOUCH_NONE;
        mLogicalDisplay.configureDisplayLocked(t, displayDevice, false, mExecutor);
        verify(t).setDisplayFlags(any(), eq(0));
        reset(t);

        mDisplayDeviceInfo.touch = DisplayDeviceInfo.TOUCH_VIRTUAL;
        mLogicalDisplay.configureDisplayLocked(t, displayDevice, false, mExecutor);
        verify(t).setDisplayFlags(any(), eq(SurfaceControl.DISPLAY_RECEIVES_INPUT));
        reset(t);

        mLogicalDisplay.setEnabledLocked(false);
        mLogicalDisplay.configureDisplayLocked(t, displayDevice, false, mExecutor);
        verify(t).setDisplayFlags(any(), eq(0));
        reset(t);

        mLogicalDisplay.setEnabledLocked(true);
        mDisplayDeviceInfo.touch = DisplayDeviceInfo.TOUCH_EXTERNAL;
        mLogicalDisplay.configureDisplayLocked(t, displayDevice, false, mExecutor);
        verify(t).setDisplayFlags(any(), eq(SurfaceControl.DISPLAY_RECEIVES_INPUT));
        reset(t);
    }

    @Test
    public void testRearDisplaysArePresentationDisplaysThatDestroyContentOnRemoval() {
        // Assert that the display isn't a presentation display by default, with a default remove
        // mode
        assertEquals(0, mLogicalDisplay.getDisplayInfoLocked().flags);
        assertEquals(Display.REMOVE_MODE_MOVE_CONTENT_TO_PRIMARY,
                mLogicalDisplay.getDisplayInfoLocked().removeMode);

        // Update position and test to see that it's been updated to a rear, presentation display
        // that destroys content on removal
        mLogicalDisplay.setDevicePositionLocked(Layout.Display.POSITION_REAR);
        mLogicalDisplay.updateLocked(mDeviceRepo);
        assertEquals(Display.FLAG_REAR | Display.FLAG_PRESENTATION,
                mLogicalDisplay.getDisplayInfoLocked().flags);
        assertEquals(Display.REMOVE_MODE_DESTROY_CONTENT,
                mLogicalDisplay.getDisplayInfoLocked().removeMode);

        // And then check the unsetting the position resets both
        mLogicalDisplay.setDevicePositionLocked(Layout.Display.POSITION_UNKNOWN);
        mLogicalDisplay.updateLocked(mDeviceRepo);
        assertEquals(0, mLogicalDisplay.getDisplayInfoLocked().flags);
        assertEquals(Display.REMOVE_MODE_MOVE_CONTENT_TO_PRIMARY,
                mLogicalDisplay.getDisplayInfoLocked().removeMode);
    }

    @Test
    public void testUpdateLayoutLimitedRefreshRate() {
        SurfaceControl.RefreshRateRange layoutLimitedRefreshRate =
                new SurfaceControl.RefreshRateRange(0, 120);
        DisplayInfo info1 = mLogicalDisplay.getDisplayInfoLocked();
        mLogicalDisplay.updateLayoutLimitedRefreshRateLocked(layoutLimitedRefreshRate);
        DisplayInfo info2 = mLogicalDisplay.getDisplayInfoLocked();
        // Display info should only be updated when updateLocked is called
        assertEquals(info2, info1);

        mLogicalDisplay.updateLocked(mDeviceRepo);
        DisplayInfo info3 = mLogicalDisplay.getDisplayInfoLocked();
        assertNotEquals(info3, info2);
        assertEquals(layoutLimitedRefreshRate, info3.layoutLimitedRefreshRate);
    }

    @Test
    public void testUpdateLayoutLimitedRefreshRate_setsDirtyFlag() {
        SurfaceControl.RefreshRateRange layoutLimitedRefreshRate =
                new SurfaceControl.RefreshRateRange(0, 120);
        assertFalse(mLogicalDisplay.isDirtyLocked());

        mLogicalDisplay.updateLayoutLimitedRefreshRateLocked(layoutLimitedRefreshRate);
        assertTrue(mLogicalDisplay.isDirtyLocked());

        mLogicalDisplay.updateLocked(mDeviceRepo);
        assertFalse(mLogicalDisplay.isDirtyLocked());
    }

    @Test
    public void testUpdateRefreshRateThermalThrottling() {
        SparseArray<SurfaceControl.RefreshRateRange> refreshRanges = new SparseArray<>();
        refreshRanges.put(0, new SurfaceControl.RefreshRateRange(0, 120));
        DisplayInfo info1 = mLogicalDisplay.getDisplayInfoLocked();
        mLogicalDisplay.updateThermalRefreshRateThrottling(refreshRanges);
        DisplayInfo info2 = mLogicalDisplay.getDisplayInfoLocked();
        // Display info should only be updated when updateLocked is called
        assertEquals(info2, info1);

        mLogicalDisplay.updateLocked(mDeviceRepo);
        DisplayInfo info3 = mLogicalDisplay.getDisplayInfoLocked();
        assertNotEquals(info3, info2);
        assertTrue(refreshRanges.contentEquals(info3.thermalRefreshRateThrottling));
    }

    @Test
    public void testUpdateRefreshRateThermalThrottling_setsDirtyFlag() {
        SparseArray<SurfaceControl.RefreshRateRange> refreshRanges = new SparseArray<>();
        refreshRanges.put(0, new SurfaceControl.RefreshRateRange(0, 120));
        assertFalse(mLogicalDisplay.isDirtyLocked());

        mLogicalDisplay.updateThermalRefreshRateThrottling(refreshRanges);
        assertTrue(mLogicalDisplay.isDirtyLocked());

        mLogicalDisplay.updateLocked(mDeviceRepo);
        assertFalse(mLogicalDisplay.isDirtyLocked());
    }

    @Test
    public void testUpdateDisplayGroupIdLocked() {
        int newId = 999;
        DisplayInfo info1 = mLogicalDisplay.getDisplayInfoLocked();
        mLogicalDisplay.updateDisplayGroupIdLocked(newId);
        DisplayInfo info2 = mLogicalDisplay.getDisplayInfoLocked();
        // Display info should only be updated when updateLocked is called
        assertEquals(info2, info1);

        mLogicalDisplay.updateLocked(mDeviceRepo);
        DisplayInfo info3 = mLogicalDisplay.getDisplayInfoLocked();
        assertNotEquals(info3, info2);
        assertEquals(newId, info3.displayGroupId);
    }

    @Test
    public void testUpdateDisplayGroupIdLocked_setsDirtyFlag() {
        assertFalse(mLogicalDisplay.isDirtyLocked());

        mLogicalDisplay.updateDisplayGroupIdLocked(99);
        assertTrue(mLogicalDisplay.isDirtyLocked());

        mLogicalDisplay.updateLocked(mDeviceRepo);
        assertFalse(mLogicalDisplay.isDirtyLocked());
    }

    @Test
    public void testSetThermalBrightnessThrottlingDataId() {
        String brightnessThrottlingDataId = "throttling_data_id";
        DisplayInfo info1 = mLogicalDisplay.getDisplayInfoLocked();
        mLogicalDisplay.setThermalBrightnessThrottlingDataIdLocked(brightnessThrottlingDataId);
        DisplayInfo info2 = mLogicalDisplay.getDisplayInfoLocked();
        // Display info should only be updated when updateLocked is called
        assertEquals(info2, info1);

        mLogicalDisplay.updateLocked(mDeviceRepo);
        DisplayInfo info3 = mLogicalDisplay.getDisplayInfoLocked();
        assertNotEquals(info3, info2);
        assertEquals(brightnessThrottlingDataId, info3.thermalBrightnessThrottlingDataId);
    }

    @Test
    public void testSetThermalBrightnessThrottlingDataId_setsDirtyFlag() {
        assertFalse(mLogicalDisplay.isDirtyLocked());

        mLogicalDisplay.setThermalBrightnessThrottlingDataIdLocked("99");
        assertTrue(mLogicalDisplay.isDirtyLocked());

        mLogicalDisplay.updateLocked(mDeviceRepo);
        assertFalse(mLogicalDisplay.isDirtyLocked());
    }

    @Test
    public void testGetsSupportedModesFromSupportedModes() {
        mLogicalDisplay = new LogicalDisplay(DISPLAY_ID, LAYER_STACK, mDisplayDevice,
                mDisplayInfoCacheMocked);

        mLogicalDisplay.updateLocked(mDeviceRepo);
        DisplayInfo info = mLogicalDisplay.getDisplayInfoLocked();
        assertArrayEquals(mDisplayDeviceInfo.supportedModes, info.supportedModes);
    }

    @Test
    public void testSetCanHostTasks_defaultDisplay() {
        mLogicalDisplay = new LogicalDisplay(Display.DEFAULT_DISPLAY, LAYER_STACK, mDisplayDevice,
                mDisplayInfoCacheMocked);

        mLogicalDisplay.setCanHostTasksLocked(true);
        assertTrue(mLogicalDisplay.canHostTasksLocked());

        mLogicalDisplay.setCanHostTasksLocked(false);
        assertTrue(mLogicalDisplay.canHostTasksLocked());
    }

    @Test
    public void testSetCanHostTasks_nonDefaultNormalDisplay() {
        // create a non-default display that allows content mode switch
        mDisplayDeviceInfo.flags = DisplayDeviceInfo.FLAG_ALLOWS_CONTENT_MODE_SWITCH;
        mLogicalDisplay =
                new LogicalDisplay(Display.DEFAULT_DISPLAY + 1, LAYER_STACK, mDisplayDevice,
                        mDisplayInfoCacheMocked);

        mLogicalDisplay.setCanHostTasksLocked(true);
        assertTrue(mLogicalDisplay.canHostTasksLocked());

        mLogicalDisplay.setCanHostTasksLocked(false);
        assertFalse(mLogicalDisplay.canHostTasksLocked());
    }

    @Test
    public void testSetCanHostTasks_nonDefaultVirtualMirrorDisplay() {
        mDisplayDeviceInfo.type = Display.TYPE_VIRTUAL;
        when(mDisplayDevice.shouldOnlyMirror()).thenReturn(true);
        mLogicalDisplay =
                new LogicalDisplay(Display.DEFAULT_DISPLAY + 1, LAYER_STACK, mDisplayDevice,
                        mDisplayInfoCacheMocked);
        mLogicalDisplay.updateLocked(mDeviceRepo);

        mLogicalDisplay.setCanHostTasksLocked(true);
        assertFalse(mLogicalDisplay.canHostTasksLocked());

        mLogicalDisplay.setCanHostTasksLocked(false);
        assertFalse(mLogicalDisplay.canHostTasksLocked());
    }

    @Test
    public void testSetCanHostTasks_nonDefaultRearDisplay() {
        mLogicalDisplay =
                new LogicalDisplay(Display.DEFAULT_DISPLAY + 1, LAYER_STACK, mDisplayDevice,
                        mDisplayInfoCacheMocked);
        mLogicalDisplay.setDevicePositionLocked(Layout.Display.POSITION_REAR);

        mLogicalDisplay.setCanHostTasksLocked(true);
        assertTrue(mLogicalDisplay.canHostTasksLocked());

        mLogicalDisplay.setCanHostTasksLocked(false);
        assertTrue(mLogicalDisplay.canHostTasksLocked());
    }

    @Test
    public void testSetCanHostTasks_nonDefaultOwnContentOnly() {
        mDisplayDeviceInfo.flags = DisplayDeviceInfo.FLAG_OWN_CONTENT_ONLY;
        mLogicalDisplay =
                new LogicalDisplay(Display.DEFAULT_DISPLAY + 1, LAYER_STACK, mDisplayDevice,
                        mDisplayInfoCacheMocked);

        mLogicalDisplay.setCanHostTasksLocked(true);
        assertTrue(mLogicalDisplay.canHostTasksLocked());

        mLogicalDisplay.setCanHostTasksLocked(false);
        assertTrue(mLogicalDisplay.canHostTasksLocked());
    }

    @Test
    public void testSetCanHostTasks_nonDefaultShouldAlwaysShowSysDecors() {
        mDisplayDeviceInfo.flags = DisplayDeviceInfo.FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS;
        mLogicalDisplay =
                new LogicalDisplay(Display.DEFAULT_DISPLAY + 1, LAYER_STACK, mDisplayDevice,
                        mDisplayInfoCacheMocked);

        mLogicalDisplay.setCanHostTasksLocked(true);
        assertTrue(mLogicalDisplay.canHostTasksLocked());

        mLogicalDisplay.setCanHostTasksLocked(false);
        assertTrue(mLogicalDisplay.canHostTasksLocked());
    }

    @Test
    public void testSetCanHostTasks_nonDefaultNeverBlank() {
        mDisplayDeviceInfo.type = Display.TYPE_VIRTUAL;
        when(mDisplayDevice.shouldAutoMirror()).thenReturn(true);
        mLogicalDisplay =
                new LogicalDisplay(Display.DEFAULT_DISPLAY + 1, LAYER_STACK, mDisplayDevice,
                        mDisplayInfoCacheMocked);
        mLogicalDisplay.updateLocked(mDeviceRepo);

        mLogicalDisplay.setCanHostTasksLocked(true);
        assertTrue(mLogicalDisplay.canHostTasksLocked());

        mLogicalDisplay.setCanHostTasksLocked(false);
        assertTrue(mLogicalDisplay.canHostTasksLocked());
    }

    @Test
    public void testSetCanHostTasks_doesNotAllowContentModeSwitch() {
        // Disable FLAG_ALLOWS_CONTENT_MODE_SWITCH
        mDisplayDeviceInfo.flags = 0;
        mLogicalDisplay =
                new LogicalDisplay(Display.DEFAULT_DISPLAY + 1, LAYER_STACK, mDisplayDevice,
                        mDisplayInfoCacheMocked);

        mLogicalDisplay.setCanHostTasksLocked(true);
        assertTrue(mLogicalDisplay.canHostTasksLocked());

        mLogicalDisplay.setCanHostTasksLocked(false);
        assertTrue(mLogicalDisplay.canHostTasksLocked());
    }

    @Test
    public void testCalculateBaseDensity_withValidDpi() {
        mLogicalDisplay =
                new LogicalDisplay(Display.DEFAULT_DISPLAY + 1, LAYER_STACK, mDisplayDevice,
                        mDisplayInfoCacheMocked);
        mDisplayDeviceInfo.type = Display.TYPE_EXTERNAL;
        mDisplayDeviceInfo.densityDpi = 0; // To trigger calculateBaseDensity
        mDisplayDeviceInfo.xDpi = 100f;
        mDisplayDeviceInfo.yDpi = 100f;
        mDisplayDeviceInfo.width = 1920;
        mDisplayDeviceInfo.height = 1080;

        mLogicalDisplay.updateLocked(mDeviceRepo);
        DisplayInfo info = mLogicalDisplay.getDisplayInfoLocked();

        assertEquals(136, info.logicalDensityDpi);
    }

    @Test
    public void testCalculateBaseDensity_withValidDpi_usesMinimumDensityDpi() {
        mLogicalDisplay =
                new LogicalDisplay(Display.DEFAULT_DISPLAY + 1, LAYER_STACK, mDisplayDevice,
                        mDisplayInfoCacheMocked);
        mDisplayDeviceInfo.densityDpi = 0; // To trigger calculateBaseDensity
        mDisplayDeviceInfo.xDpi = 50f;
        mDisplayDeviceInfo.yDpi = 50f;
        mDisplayDeviceInfo.width = 1920;
        mDisplayDeviceInfo.height = 1080;

        mLogicalDisplay.updateLocked(mDeviceRepo);
        DisplayInfo info = mLogicalDisplay.getDisplayInfoLocked();

        assertEquals(100, info.logicalDensityDpi);
    }

    @Test
    public void testCalculateBaseDensity_notCalledWhenDensityDpiIsSet() {
        mLogicalDisplay =
                new LogicalDisplay(Display.DEFAULT_DISPLAY + 1, LAYER_STACK, mDisplayDevice,
                        mDisplayInfoCacheMocked);
        mDisplayDeviceInfo.densityDpi = 320;
        mDisplayDeviceInfo.xDpi = 100f;
        mDisplayDeviceInfo.yDpi = 100f;

        mLogicalDisplay.updateLocked(mDeviceRepo);
        DisplayInfo info = mLogicalDisplay.getDisplayInfoLocked();

        assertEquals(320, info.logicalDensityDpi);
    }

    @Test
    public void testCalculateBaseDensity_withMissingDpi() {
        mLogicalDisplay =
                new LogicalDisplay(Display.DEFAULT_DISPLAY + 1, LAYER_STACK, mDisplayDevice,
                        mDisplayInfoCacheMocked);
        mDisplayDeviceInfo.densityDpi = 0;
        mDisplayDeviceInfo.xDpi = 0f;
        mDisplayDeviceInfo.yDpi = 0f;
        mDisplayDeviceInfo.width = 1920;
        mDisplayDeviceInfo.height = 1080;

        mLogicalDisplay.updateLocked(mDeviceRepo);
        DisplayInfo info = mLogicalDisplay.getDisplayInfoLocked();

        assertEquals(125, info.logicalDensityDpi);
    }

    @Test
    public void testUserPreferredModeWithSizeOverride_updatesResolution() {
        mLogicalDisplay = new LogicalDisplay(DISPLAY_ID, LAYER_STACK, mDisplayDevice, false,
            mDisplayInfoCacheMocked);
        Display.Mode mode = new Display.Mode(OTHER_MODE_ID, -1, -1,
                Display.Mode.FLAG_SIZE_OVERRIDE,  1000, 1000, 60f, 60f, new float[]{},
                new int[]{});
        mDisplayDeviceInfo.supportedModes = new Display.Mode[] {mode};
        mDisplayDeviceInfo.width = 500;
        mDisplayDeviceInfo.height = 500;
        mDisplayDeviceInfo.xDpi = 100;
        mDisplayDeviceInfo.yDpi = 100;
        mDisplayDeviceInfo.userPreferredModeId = OTHER_MODE_ID;

        mLogicalDisplay.updateLocked(mDeviceRepo);

        assertThat(mLogicalDisplay.getDisplayInfoLocked().logicalWidth).isEqualTo(1000);
        assertThat(mLogicalDisplay.getDisplayInfoLocked().logicalHeight).isEqualTo(1000);
        assertThat(mLogicalDisplay.getDisplayInfoLocked().physicalXDpi).isEqualTo(200);
        assertThat(mLogicalDisplay.getDisplayInfoLocked().physicalYDpi).isEqualTo(200);
    }

    @Test
    public void testUserPreferredModeWithoutSizeOverride_doesNotUpdateResolution() {
        mLogicalDisplay = new LogicalDisplay(DISPLAY_ID, LAYER_STACK, mDisplayDevice, false,
                mDisplayInfoCacheMocked);
        Display.Mode mode = new Display.Mode(OTHER_MODE_ID, -1, -1, 0,
                1000, 1000, 60f, 60f, new float[]{}, new int[]{});
        mDisplayDeviceInfo.supportedModes = new Display.Mode[] {mode};
        mDisplayDeviceInfo.width = 500;
        mDisplayDeviceInfo.height = 500;
        mDisplayDeviceInfo.xDpi = 100;
        mDisplayDeviceInfo.yDpi = 100;
        mDisplayDeviceInfo.userPreferredModeId = OTHER_MODE_ID;

        mLogicalDisplay.updateLocked(mDeviceRepo);

        assertThat(mLogicalDisplay.getDisplayInfoLocked().logicalWidth).isEqualTo(500);
        assertThat(mLogicalDisplay.getDisplayInfoLocked().logicalHeight).isEqualTo(500);
        assertThat(mLogicalDisplay.getDisplayInfoLocked().physicalXDpi).isEqualTo(100);
        assertThat(mLogicalDisplay.getDisplayInfoLocked().physicalYDpi).isEqualTo(100);
    }

    @Test
    public void testUserPreferredModeWithSizeOverride_withRotation_updatesProjection() {
        // Enable size override for this test
        mLogicalDisplay = new LogicalDisplay(DISPLAY_ID, LAYER_STACK, mDisplayDevice, false,
                mDisplayInfoCacheMocked);

        // Setup a physical display that is portrait (1000x2000)
        mDisplayDeviceInfo.type = Display.TYPE_EXTERNAL;
        mDisplayDeviceInfo.width = 1000;
        mDisplayDeviceInfo.height = 2000;
        mDisplayDeviceInfo.xDpi = 100;
        mDisplayDeviceInfo.yDpi = 100;

        // Setup a user-preferred mode that is landscape (2000x1000) with size override
        Display.Mode overrideMode = new Display.Mode(OTHER_MODE_ID, -1, -1,
                Display.Mode.FLAG_SIZE_OVERRIDE, 2000, 1000, 60f, 60f, new float[]{}, new int[]{});
        mDisplayDeviceInfo.supportedModes = new Display.Mode[] {overrideMode};
        mDisplayDeviceInfo.userPreferredModeId = OTHER_MODE_ID;

        // Update the logical display. This will adopt the override mode's resolution.
        mLogicalDisplay.updateLocked(mDeviceRepo);
        DisplayInfo baseInfo = mLogicalDisplay.getDisplayInfoLocked();
        assertThat(baseInfo.logicalWidth).isEqualTo(2000);
        assertThat(baseInfo.logicalHeight).isEqualTo(1000);

        // Now, simulate a rotation from WindowManager. The logical display is now landscape,
        // and we're rotating it by 90 degrees to be displayed on the portrait physical display.
        DisplayInfo overrideInfo = new DisplayInfo(baseInfo);
        overrideInfo.rotation = Surface.ROTATION_90;
        mLogicalDisplay.setDisplayInfoOverrideFromWindowManagerLocked(overrideInfo);

        // Configure the display projection
        SurfaceControl.Transaction t = mock(SurfaceControl.Transaction.class);
        mLogicalDisplay.configureDisplayLocked(t, mDisplayDevice, false, mExecutor);

        // With the fix, the user mode dimensions are swapped for rotation, leading to
        // correct scaling and a letterboxed projection.
        // Without the fix, the dimensions are not swapped, leading to incorrect scaling
        // and a projection that fills the screen (position 0,0).
        //
        // Calculation with fix:
        // Physical display (rotated for projection): 2000x1000
        // Logical display (from override mode): 2000x1000
        // User override mode (rotated for scaling): 1000x2000
        //
        // Recalculated logical size for projection:
        // displayLogicalWidth = 2000 * 2000 / 1000 = 4000
        // displayLogicalHeight = 1000 * 1000 / 2000 = 500
        //
        // Fit 4000x500 content into 2000x1000 physical space (letterboxed):
        // displayRectWidth = 2000
        // displayRectHeight = 500 * 2000 / 4000 = 250
        // displayRectTop = (1000 - 250) / 2 = 375
        // displayRectLeft = 0
        assertEquals(new Point(0, 375), mLogicalDisplay.getDisplayPosition());
    }

    @Test
    public void testAnisotropyCorrectedMode_parentModeSelected_appliesCorrectedMode() {
        mLogicalDisplay = new LogicalDisplay(DISPLAY_ID, LAYER_STACK, mDisplayDevice, false,
                mDisplayInfoCacheMocked);
        Display.Mode anisotropicMode = new Display.Mode(MODE_ID, -1, -1, 0,
                1000, 1000, 60f, 60f, new float[]{}, new int[]{});
        Display.Mode anisotropyCorrectedMode = new Display.Mode(OTHER_MODE_ID, MODE_ID, -1,
                Display.Mode.FLAG_ANISOTROPY_CORRECTION, 2000, 2000, 60f, 60f,
                new float[]{}, new int[]{});

        mDisplayDeviceInfo.type = Display.TYPE_EXTERNAL;
        mDisplayDeviceInfo.supportedModes = new Display.Mode[] {
                anisotropicMode, anisotropyCorrectedMode};
        mDisplayDeviceInfo.modeId = MODE_ID;
        mDisplayDeviceInfo.width = 500;
        mDisplayDeviceInfo.height = 500;
        mDisplayDeviceInfo.xDpi = 100;
        mDisplayDeviceInfo.yDpi = 100;

        mLogicalDisplay.updateLocked(mDeviceRepo);

        assertWithMessage("logicalWidth is not matching")
                .that(mLogicalDisplay.getDisplayInfoLocked().logicalWidth).isEqualTo(2000);
        assertWithMessage("logicalHeight is not matching")
                .that(mLogicalDisplay.getDisplayInfoLocked().logicalHeight).isEqualTo(2000);
        assertWithMessage("physicalXDpi is not matching")
                .that(mLogicalDisplay.getDisplayInfoLocked().physicalXDpi).isEqualTo(400);
        assertWithMessage("physicalYDpi is not matching")
                .that(mLogicalDisplay.getDisplayInfoLocked().physicalYDpi).isEqualTo(400);
    }

    @Test
    public void testAnisotropyCorrectedMode_parentModeNotSelected_doesNotApplyCorrectedMode() {
        int mismatchedModeId = 1000;
        mLogicalDisplay = new LogicalDisplay(DISPLAY_ID, LAYER_STACK, mDisplayDevice, false,
                mDisplayInfoCacheMocked);
        Display.Mode anisotropicMode = new Display.Mode(MODE_ID, -1, -1, 0,
                1000, 1000, 60f, 60f, new float[]{}, new int[]{});
        Display.Mode anisotropyCorrectedMode = new Display.Mode(OTHER_MODE_ID, mismatchedModeId,
                -1, Display.Mode.FLAG_ANISOTROPY_CORRECTION, 2000, 2000, 60f, 60f,
                new float[]{}, new int[]{});

        mDisplayDeviceInfo.type = Display.TYPE_EXTERNAL;
        mDisplayDeviceInfo.supportedModes = new Display.Mode[] {
                anisotropicMode, anisotropyCorrectedMode};
        mDisplayDeviceInfo.modeId = MODE_ID;
        mDisplayDeviceInfo.width = 500;
        mDisplayDeviceInfo.height = 500;
        mDisplayDeviceInfo.xDpi = 100;
        mDisplayDeviceInfo.yDpi = 100;

        mLogicalDisplay.updateLocked(mDeviceRepo);

        assertWithMessage("logicalWidth is not matching")
                .that(mLogicalDisplay.getDisplayInfoLocked().logicalWidth).isEqualTo(500);
        assertWithMessage("logicalHeight is not matching")
                .that(mLogicalDisplay.getDisplayInfoLocked().logicalHeight).isEqualTo(500);
        assertWithMessage("physicalXDpi is not matching")
                .that(mLogicalDisplay.getDisplayInfoLocked().physicalXDpi).isEqualTo(100);
        assertWithMessage("physicalYDpi is not matching")
                .that(mLogicalDisplay.getDisplayInfoLocked().physicalYDpi).isEqualTo(100);
    }

    @Test
    public void testAnisotropyCorrectedMode_internalDisplay_doesNotApplyCorrectedMode() {
        mLogicalDisplay = new LogicalDisplay(DISPLAY_ID, LAYER_STACK, mDisplayDevice, false,
                mDisplayInfoCacheMocked);
        Display.Mode anisotropicMode = new Display.Mode(MODE_ID, -1, -1, 0,
                1000, 1000, 60f, 60f, new float[]{}, new int[]{});
        Display.Mode anisotropyCorrectedMode = new Display.Mode(OTHER_MODE_ID, MODE_ID, -1,
                Display.Mode.FLAG_ANISOTROPY_CORRECTION, 2000, 2000, 60f, 60f,
                new float[]{}, new int[]{});

        mDisplayDeviceInfo.type = Display.TYPE_INTERNAL;
        mDisplayDeviceInfo.supportedModes = new Display.Mode[] {
                anisotropicMode, anisotropyCorrectedMode};
        mDisplayDeviceInfo.modeId = MODE_ID;
        mDisplayDeviceInfo.width = 500;
        mDisplayDeviceInfo.height = 500;
        mDisplayDeviceInfo.xDpi = 100;
        mDisplayDeviceInfo.yDpi = 100;

        mLogicalDisplay.updateLocked(mDeviceRepo);

        assertWithMessage("logicalWidth is not matching")
                .that(mLogicalDisplay.getDisplayInfoLocked().logicalWidth).isEqualTo(500);
        assertWithMessage("logicalHeight is not matching")
                .that(mLogicalDisplay.getDisplayInfoLocked().logicalHeight).isEqualTo(500);
        assertWithMessage("physicalXDpi is not matching")
                .that(mLogicalDisplay.getDisplayInfoLocked().physicalXDpi).isEqualTo(100);
        assertWithMessage("physicalYDpi is not matching")
                .that(mLogicalDisplay.getDisplayInfoLocked().physicalYDpi).isEqualTo(100);
    }

    @Test
    public void testAnisotropyCorrectedMode_userPreferredModeSelected_doesNotApplyCorrectedMode() {
        mLogicalDisplay = new LogicalDisplay(DISPLAY_ID, LAYER_STACK, mDisplayDevice, false,
                mDisplayInfoCacheMocked);
        Display.Mode anisotropicMode = new Display.Mode(MODE_ID, -1, -1, 0,
                1000, 1000, 60f, 60f, new float[]{}, new int[]{});
        Display.Mode anisotropyCorrectedMode = new Display.Mode(OTHER_MODE_ID, MODE_ID, -1,
                Display.Mode.FLAG_ANISOTROPY_CORRECTION, 2000, 2000, 60f, 60f,
                new float[]{}, new int[]{});

        mDisplayDeviceInfo.type = Display.TYPE_EXTERNAL;
        mDisplayDeviceInfo.supportedModes = new Display.Mode[] {
                anisotropicMode, anisotropyCorrectedMode};
        mDisplayDeviceInfo.modeId = MODE_ID;
        mDisplayDeviceInfo.userPreferredModeId = MODE_ID;
        mDisplayDeviceInfo.width = 500;
        mDisplayDeviceInfo.height = 500;
        mDisplayDeviceInfo.xDpi = 100;
        mDisplayDeviceInfo.yDpi = 100;

        mLogicalDisplay.updateLocked(mDeviceRepo);

        assertWithMessage("logicalWidth is not matching")
                .that(mLogicalDisplay.getDisplayInfoLocked().logicalWidth).isEqualTo(500);
        assertWithMessage("logicalHeight is not matching")
                .that(mLogicalDisplay.getDisplayInfoLocked().logicalHeight).isEqualTo(500);
        assertWithMessage("physicalXDpi is not matching")
                .that(mLogicalDisplay.getDisplayInfoLocked().physicalXDpi).isEqualTo(100);
        assertWithMessage("physicalYDpi is not matching")
                .that(mLogicalDisplay.getDisplayInfoLocked().physicalYDpi).isEqualTo(100);
    }
}
