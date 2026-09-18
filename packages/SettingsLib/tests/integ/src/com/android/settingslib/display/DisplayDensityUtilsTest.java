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

package com.android.settingslib.display;

import static com.android.settingslib.flags.Flags.FLAG_ADD_EXTRA_EXTERNAL_DISPLAY_DENSITY_STOPS;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManagerGlobal;
import android.os.RemoteException;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.view.Display;
import android.view.DisplayAdjustments;
import android.view.DisplayInfo;
import android.view.IWindowManager;
import android.view.WindowManagerGlobal;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.settingslib.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class DisplayDensityUtilsTest {

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private static final float MAX_SCALE_INTERNAL = 1.33f;
    private static final float MIN_SCALE_INTERNAL = 0.85f;
    private static final float MIN_INTERVAL = 0.09f;

    private static final float MAX_SCALE_EXTERNAL = 1.5f;
    private static final float MIN_SCALE_EXTERNAL = 0.7f;
    @Mock private Context mContext;
    @Mock private Resources mResources;
    @Mock private DisplayManager mDisplayManager;
    @Mock private DisplayManagerGlobal mDisplayManagerGlobal;
    @Mock private IWindowManager mIWindowManager;
    private IWindowManager mWindowManagerToRestore;
    private DisplayDensityUtils mDisplayDensityUtils;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        doReturn(mDisplayManager).when(mContext).getSystemService((Class<Object>) any());
        mWindowManagerToRestore = WindowManagerGlobal.getWindowManagerService();
        WindowManagerGlobal.setWindowManagerServiceForSystemProcess(mIWindowManager);
        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getFraction(R.fraction.display_density_max_scale, 1, 1))
                .thenReturn(MAX_SCALE_INTERNAL);
        when(mResources.getFraction(R.fraction.display_density_min_scale, 1, 1))
                .thenReturn(MIN_SCALE_INTERNAL);
        when(mResources.getFraction(R.fraction.display_density_min_scale_interval, 1, 1))
                .thenReturn(MIN_INTERVAL);
        when(mResources.getString(anyInt())).thenReturn("summary");
    }

    @After
    public void teardown() throws Exception {
        WindowManagerGlobal.setWindowManagerServiceForSystemProcess(mWindowManagerToRestore);
    }

    @Test
    public void createDisplayDensityUtil_onlyDefaultDisplay() throws RemoteException {
        var info =
                createDisplayInfoForDisplay(
                        Display.DEFAULT_DISPLAY,
                        Display.TYPE_INTERNAL,
                        2560,
                        1600,
                        320,
                        /* isSizeMissing= */ false);
        var display =
                new Display(mDisplayManagerGlobal, info.displayId, info, (DisplayAdjustments) null);
        doReturn(new Display[] {display}).when(mDisplayManager).getDisplays(any());
        doReturn(display).when(mDisplayManager).getDisplay(info.displayId);

        mDisplayDensityUtils = new DisplayDensityUtils(mContext, (i) -> true, (i) -> false);

        assertThat(mDisplayDensityUtils.getValues()).isEqualTo(new int[] {272, 320, 354, 390, 424});
    }

    @Test
    public void createDisplayDensityUtil_multipleInternalDisplays() throws RemoteException {
        // Default display
        var defaultDisplayInfo =
                createDisplayInfoForDisplay(
                        Display.DEFAULT_DISPLAY, Display.TYPE_INTERNAL, 2000, 2000, 390, false);
        var defaultDisplay =
                new Display(
                        mDisplayManagerGlobal,
                        defaultDisplayInfo.displayId,
                        defaultDisplayInfo,
                        (DisplayAdjustments) null);
        doReturn(defaultDisplay).when(mDisplayManager).getDisplay(defaultDisplayInfo.displayId);

        // Create another internal display
        var internalDisplayInfo =
                createDisplayInfoForDisplay(
                        1, Display.TYPE_INTERNAL, 2000, 1000, 390, /* isSizeMissing= */ false);
        var internalDisplay =
                new Display(
                        mDisplayManagerGlobal,
                        internalDisplayInfo.displayId,
                        internalDisplayInfo,
                        (DisplayAdjustments) null);
        doReturn(internalDisplay).when(mDisplayManager).getDisplay(internalDisplayInfo.displayId);

        doReturn(new Display[] {defaultDisplay, internalDisplay})
                .when(mDisplayManager)
                .getDisplays(anyString());

        mDisplayDensityUtils = new DisplayDensityUtils(mContext, (i) -> true, (i) -> false);

        assertThat(mDisplayDensityUtils.getValues()).isEqualTo(new int[] {330, 390, 426, 462, 500});
    }

    @Test
    @DisableFlags(FLAG_ADD_EXTRA_EXTERNAL_DISPLAY_DENSITY_STOPS)
    public void createDisplayDensityUtil_forExternalDisplay_flagDisabled() throws RemoteException {
        // Configure resources
        when(mResources.getFraction(R.fraction.external_display_density_max_scale, 1, 1))
                .thenReturn(MAX_SCALE_EXTERNAL);
        when(mResources.getFraction(R.fraction.external_display_density_min_scale, 1, 1))
                .thenReturn(MIN_SCALE_EXTERNAL);
        // Default display
        var defaultDisplayInfo =
                createDisplayInfoForDisplay(
                        Display.DEFAULT_DISPLAY,
                        Display.TYPE_INTERNAL,
                        2000,
                        2000,
                        390,
                        /* isSizeMissing= */ false);
        var defaultDisplay =
                new Display(
                        mDisplayManagerGlobal,
                        defaultDisplayInfo.displayId,
                        defaultDisplayInfo,
                        (DisplayAdjustments) null);
        doReturn(defaultDisplay).when(mDisplayManager).getDisplay(defaultDisplayInfo.displayId);

        // Create external display
        var externalDisplayInfo =
                createDisplayInfoForDisplay(
                        /* displayId= */ 2,
                        Display.TYPE_EXTERNAL,
                        1920,
                        1080,
                        85,
                        /* isSizeMissing= */ false);
        var externalDisplay =
                new Display(
                        mDisplayManagerGlobal,
                        externalDisplayInfo.displayId,
                        externalDisplayInfo,
                        (DisplayAdjustments) null);

        doReturn(new Display[] {externalDisplay, defaultDisplay})
                .when(mDisplayManager)
                .getDisplays(any());
        doReturn(externalDisplay).when(mDisplayManager).getDisplay(externalDisplayInfo.displayId);

        mDisplayDensityUtils =
                new DisplayDensityUtils(
                        mContext,
                        (info) -> info.displayId == externalDisplayInfo.displayId,
                        (i) -> false);

        assertThat(mDisplayDensityUtils.getValues())
                .isEqualTo(new int[] {58, 68, 76, 85, 92, 102, 110, 118, 126});
    }

    @Test
    @EnableFlags(FLAG_ADD_EXTRA_EXTERNAL_DISPLAY_DENSITY_STOPS)
    public void createDisplayDensityUtil_forExternalDisplay_flagEnabled() throws RemoteException {
        // Configure resources
        when(mResources.getFraction(R.fraction.external_display_density_max_scale, 1, 1))
                .thenReturn(MAX_SCALE_EXTERNAL); // 1.5f
        when(mResources.getFraction(R.fraction.external_display_density_min_scale, 1, 1))
                .thenReturn(MIN_SCALE_EXTERNAL); // 0.7f

        // Default display
        var defaultDisplayInfo =
                createDisplayInfoForDisplay(
                        Display.DEFAULT_DISPLAY, Display.TYPE_INTERNAL, 2000, 2000, 390, false);
        var defaultDisplay =
                new Display(
                        mDisplayManagerGlobal,
                        defaultDisplayInfo.displayId,
                        defaultDisplayInfo,
                        (DisplayAdjustments) null);
        doReturn(defaultDisplay).when(mDisplayManager).getDisplay(defaultDisplayInfo.displayId);

        // Create external display
        var externalDisplayInfo =
                createDisplayInfoForDisplay(
                        /* displayId= */ 2, Display.TYPE_EXTERNAL, 1920, 1080, 85, false);
        var externalDisplay =
                new Display(
                        mDisplayManagerGlobal,
                        externalDisplayInfo.displayId,
                        externalDisplayInfo,
                        (DisplayAdjustments) null);

        doReturn(new Display[] {externalDisplay, defaultDisplay})
                .when(mDisplayManager)
                .getDisplays(any());
        doReturn(externalDisplay).when(mDisplayManager).getDisplay(externalDisplayInfo.displayId);

        mDisplayDensityUtils =
                new DisplayDensityUtils(
                        mContext,
                        (info) -> info.displayId == externalDisplayInfo.displayId,
                        (i) -> false);

        // Add 170 (200%) and 255 (300%)
        // 255 rounded-down to 254 (multiple of 2)
        assertThat(mDisplayDensityUtils.getValues())
                .isEqualTo(new int[] {58, 68, 76, 85, 92, 102, 110, 118, 126, 170, 254});
    }

    @Test
    @EnableFlags(FLAG_ADD_EXTRA_EXTERNAL_DISPLAY_DENSITY_STOPS)
    public void createDisplayDensityUtil_forExternalDisplay_flagEnabled_clips300Percent_lowRes()
            throws RemoteException {
        // Configure resources
        when(mResources.getFraction(R.fraction.external_display_density_max_scale, 1, 1))
                .thenReturn(MAX_SCALE_EXTERNAL);
        when(mResources.getFraction(R.fraction.external_display_density_min_scale, 1, 1))
                .thenReturn(MIN_SCALE_EXTERNAL);

        // Default display
        var defaultDisplayInfo =
                createDisplayInfoForDisplay(
                        Display.DEFAULT_DISPLAY, Display.TYPE_INTERNAL, 2000, 2000, 390, false);
        var defaultDisplay =
                new Display(
                        mDisplayManagerGlobal,
                        defaultDisplayInfo.displayId,
                        defaultDisplayInfo,
                        (DisplayAdjustments) null);
        doReturn(defaultDisplay).when(mDisplayManager).getDisplay(defaultDisplayInfo.displayId);

        // External Display: 400x400, Density 85.
        // Max Density Allowed = 160 * 400 / 320 = 200 dpi.
        // 200% Scale = 85 * 2.0 = 170. (170 <= 200) -> Valid.
        // 300% Scale = 85 * 3.0 = 255. (255 > 200) -> Invalid.
        var externalDisplayInfo =
                createDisplayInfoForDisplay(
                        /* displayId= */ 2, Display.TYPE_EXTERNAL, 400, 400, 85, false);
        var externalDisplay =
                new Display(
                        mDisplayManagerGlobal,
                        externalDisplayInfo.displayId,
                        externalDisplayInfo,
                        (DisplayAdjustments) null);

        doReturn(new Display[] {externalDisplay, defaultDisplay})
                .when(mDisplayManager)
                .getDisplays(any());
        doReturn(externalDisplay).when(mDisplayManager).getDisplay(externalDisplayInfo.displayId);

        mDisplayDensityUtils =
                new DisplayDensityUtils(
                        mContext,
                        (info) -> info.displayId == externalDisplayInfo.displayId,
                        (i) -> false);

        // Should contain 170 but not 255
        assertThat(mDisplayDensityUtils.getValues())
                .isEqualTo(new int[] {58, 68, 76, 85, 92, 102, 110, 118, 126, 170});
    }

    @Test
    public void createDisplayDensityUtil_forExternalDisplay_displaySizeMissing()
            throws RemoteException {
        // Configure resources
        when(mResources.getFraction(R.fraction.external_display_density_max_scale, 1, 1))
                .thenReturn(MAX_SCALE_EXTERNAL);
        when(mResources.getFraction(R.fraction.external_display_density_min_scale, 1, 1))
                .thenReturn(MIN_SCALE_EXTERNAL);
        // Default display
        var defaultDisplayInfo =
                createDisplayInfoForDisplay(
                        Display.DEFAULT_DISPLAY,
                        Display.TYPE_INTERNAL,
                        2000,
                        2000,
                        390,
                        /* isSizeMissing= */ false);
        var defaultDisplay =
                new Display(
                        mDisplayManagerGlobal,
                        defaultDisplayInfo.displayId,
                        defaultDisplayInfo,
                        (DisplayAdjustments) null);
        doReturn(defaultDisplay).when(mDisplayManager).getDisplay(defaultDisplayInfo.displayId);

        // Create external display
        var externalDisplayInfo =
                createDisplayInfoForDisplay(
                        /* displayId= */ 2,
                        Display.TYPE_EXTERNAL,
                        1920,
                        1080,
                        85,
                        /* isSizeMissing= */ true);
        var externalDisplay =
                new Display(
                        mDisplayManagerGlobal,
                        externalDisplayInfo.displayId,
                        externalDisplayInfo,
                        (DisplayAdjustments) null);

        doReturn(new Display[] {externalDisplay, defaultDisplay})
                .when(mDisplayManager)
                .getDisplays(any());
        doReturn(externalDisplay).when(mDisplayManager).getDisplay(externalDisplayInfo.displayId);

        mDisplayDensityUtils =
                new DisplayDensityUtils(
                        mContext,
                        (info) -> info.displayId == externalDisplayInfo.displayId,
                        (i) -> false);

        assertThat(mDisplayDensityUtils.getValues())
                .isEqualTo(new int[] {58, 68, 76, 85, 92, 102, 110, 118, 126});
    }

    @Test
    public void createDisplayDensityUtil_forExternalDisplay_lowerMaxScale() throws RemoteException {
        // Configure resources
        when(mResources.getFraction(R.fraction.external_display_density_max_scale, 1, 1))
                .thenReturn(MAX_SCALE_EXTERNAL);
        when(mResources.getFraction(R.fraction.external_display_density_min_scale, 1, 1))
                .thenReturn(MIN_SCALE_EXTERNAL);
        // Default display
        var defaultDisplayInfo =
                createDisplayInfoForDisplay(
                        Display.DEFAULT_DISPLAY,
                        Display.TYPE_INTERNAL,
                        2000,
                        2000,
                        390,
                        /* isSizeMissing= */ false);
        var defaultDisplay =
                new Display(
                        mDisplayManagerGlobal,
                        defaultDisplayInfo.displayId,
                        defaultDisplayInfo,
                        (DisplayAdjustments) null);
        doReturn(defaultDisplay).when(mDisplayManager).getDisplay(defaultDisplayInfo.displayId);

        // Create external display with low resolution to test the case where max scale value is
        // calculated from the maxDensity / defaultDensity instead of the fraction constant.
        var externalDisplayInfo =
                createDisplayInfoForDisplay(
                        /* displayId= */ 2,
                        Display.TYPE_EXTERNAL,
                        240,
                        240,
                        85,
                        /* isSizeMissing= */ true);
        var externalDisplay =
                new Display(
                        mDisplayManagerGlobal,
                        externalDisplayInfo.displayId,
                        externalDisplayInfo,
                        (DisplayAdjustments) null);

        doReturn(new Display[] {externalDisplay, defaultDisplay})
                .when(mDisplayManager)
                .getDisplays(any());
        doReturn(externalDisplay).when(mDisplayManager).getDisplay(externalDisplayInfo.displayId);

        mDisplayDensityUtils =
                new DisplayDensityUtils(
                        mContext,
                        (info) -> info.displayId == externalDisplayInfo.displayId,
                        (i) -> false);

        assertThat(mDisplayDensityUtils.getValues())
                .isEqualTo(new int[] {58, 68, 76, 85, 92, 102, 110, 120});
    }

    @Test
    public void createDisplayDensityUtil_forExternalDisplay_isLargeScreen() throws RemoteException {
        // Configure resources
        when(mResources.getFraction(R.fraction.external_display_density_max_scale, 1, 1))
                .thenReturn(MAX_SCALE_EXTERNAL);
        when(mResources.getFraction(R.fraction.external_display_density_min_scale, 1, 1))
                .thenReturn(MIN_SCALE_EXTERNAL);
        // Default display
        var defaultDisplayInfo =
                createDisplayInfoForDisplay(
                        Display.DEFAULT_DISPLAY,
                        Display.TYPE_INTERNAL,
                        2000,
                        2000,
                        390,
                        /* isSizeMissing= */ false);
        var defaultDisplay =
                new Display(
                        mDisplayManagerGlobal,
                        defaultDisplayInfo.displayId,
                        defaultDisplayInfo,
                        (DisplayAdjustments) null);
        doReturn(defaultDisplay).when(mDisplayManager).getDisplay(defaultDisplayInfo.displayId);

        // Create external display with low resolution to test the case where max scale value is
        // calculated from the maxDensity / defaultDensity instead of the fraction constant.
        var externalDisplayInfo =
                createDisplayInfoForDisplay(
                        /* displayId= */ 2,
                        Display.TYPE_EXTERNAL,
                        240,
                        240,
                        85,
                        /* isSizeMissing= */ true);
        var externalDisplay =
                new Display(
                        mDisplayManagerGlobal,
                        externalDisplayInfo.displayId,
                        externalDisplayInfo,
                        (DisplayAdjustments) null);

        doReturn(new Display[] {externalDisplay, defaultDisplay})
                .when(mDisplayManager)
                .getDisplays(any());
        doReturn(externalDisplay).when(mDisplayManager).getDisplay(externalDisplayInfo.displayId);

        mDisplayDensityUtils =
                new DisplayDensityUtils(
                        mContext,
                        (info) -> info.displayId == externalDisplayInfo.displayId,
                        (i) -> true);

        assertThat(mDisplayDensityUtils.getValues()).isEqualTo(new int[] {58, 68, 76, 85});
    }

    private DisplayInfo createDisplayInfoForDisplay(
            int displayId,
            int displayType,
            int width,
            int height,
            int density,
            boolean isSizeMissing)
            throws RemoteException {
        var displayInfo = new DisplayInfo();
        displayInfo.displayId = displayId;
        displayInfo.type = displayType;
        displayInfo.logicalWidth = width;
        displayInfo.logicalHeight = height;
        displayInfo.logicalDensityDpi = density;
        if (isSizeMissing) {
            displayInfo.physicalYDpi = 0;
            displayInfo.physicalXDpi = 0;
        } else {
            displayInfo.physicalXDpi = 100;
            displayInfo.physicalYDpi = 100;
        }

        doReturn(displayInfo).when(mDisplayManagerGlobal).getDisplayInfo(displayInfo.displayId);
        doReturn(displayInfo.logicalDensityDpi)
                .when(mIWindowManager)
                .getInitialDisplayDensity(displayId);
        return displayInfo;
    }
}
