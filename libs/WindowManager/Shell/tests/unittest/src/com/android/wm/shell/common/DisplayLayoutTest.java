/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.wm.shell.common;

import static android.content.res.Configuration.UI_MODE_TYPE_NORMAL;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Insets;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.DisplayCutout;
import android.view.DisplayInfo;
import android.view.InsetsState;
import android.view.WindowInsets;

import androidx.test.filters.SmallTest;

import com.android.internal.R;
import com.android.internal.policy.SystemBarUtils;
import com.android.wm.shell.ShellTestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

/**
 * Tests for {@link DisplayLayout}.
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:DisplayLayoutTest
 */
@SmallTest
public class DisplayLayoutTest extends ShellTestCase {
    private MockitoSession mMockitoSession;
    private static final float DELTA = 0.1f; // Constant for assertion delta

    @Before
    public void setup() {
        mMockitoSession = mockitoSession()
                .initMocks(this)
                .strictness(Strictness.WARN)
                .mockStatic(SystemBarUtils.class)
                .startMocking();
    }

    @After
    public void tearDown() {
        mMockitoSession.finishMocking();
    }

    @Test
    public void testInsets() {
        Resources res = createResources(40, 50, false);
        // Test empty display, no bars or anything
        DisplayInfo info = createDisplayInfo(1000, 1500, 30, ROTATION_0);
        DisplayLayout dlWithNoBars = new DisplayLayout(info, res, false, false);
        when(SystemBarUtils.getStatusBarHeight(eq(res), any())).thenReturn(40);
        InsetsState mockInsetsState = mock(InsetsState.class);
        Rect displayFrame = new Rect(0, 0, 1000, 1500);
        Insets combinedInsets = Insets.of(0, 20, 0, 50); // left, top, right, bottom
        Insets emptyInsets = Insets.of(0, 0, 0, 0);
        when(mockInsetsState.getDisplayFrame()).thenReturn(displayFrame);
        when(mockInsetsState.calculateInsets(
                eq(displayFrame),
                eq(displayFrame),
                eq(0),
                eq(true) /* ignoreVisibility */))
                .thenReturn(emptyInsets);
        when(mockInsetsState.calculateInsets(
                eq(displayFrame),
                eq(displayFrame),
                eq(WindowInsets.Type.navigationBars()),
                eq(true) /* ignoreVisibility */))
                .thenReturn(combinedInsets);
        dlWithNoBars.mInsetsState = mockInsetsState;

        // Just cutout
        dlWithNoBars.recalcInsets(res);
        assertEquals(new Rect(0, 30, 0, 0), dlWithNoBars.stableInsets());
        assertEquals(new Rect(0, 30, 0, 0), dlWithNoBars.nonDecorInsets());

        // Test with bars
        DisplayLayout dlWithBars = new DisplayLayout(info, res, true, true);
        dlWithBars.mInsetsState = mockInsetsState;
        dlWithBars.recalcInsets(res);
        assertEquals(new Rect(0, 40, 0, 50), dlWithBars.stableInsets());
        assertEquals(new Rect(0, 30, 0, 50), dlWithBars.nonDecorInsets());
    }

    @Test
    public void testComputeNonDecorInsets_fromInsetsState_withNavigationBar() {
        // 1. Setup
        InsetsState mockInsetsState = mock(InsetsState.class);
        Rect displayFrame = new Rect(0, 0, 1000, 1500);
        Insets combinedInsets = Insets.of(10, 20, 30, 40);
        Rect cutoutInsets = new Rect(50, 0, 0, 0);
        when(mockInsetsState.getDisplayFrame()).thenReturn(displayFrame);
        when(mockInsetsState.calculateInsets(
                eq(displayFrame),
                eq(displayFrame),
                eq(WindowInsets.Type.navigationBars()),
                eq(true) /* ignoreVisibility */))
                .thenReturn(combinedInsets);

        Rect outInsets = new Rect();

        // 2. Action
        DisplayLayout.computeNonDecorInsets(mockInsetsState, outInsets,
                true /* hasNavigationBar */, cutoutInsets);

        // 3. Assert
        assertEquals(new Rect(50, 20, 30, 40), outInsets);
    }

    @Test
    public void testComputeNonDecorInsets_fromInsetsState_withoutNavigationBar() {
        InsetsState mockInsetsState = mock(InsetsState.class);
        Rect displayFrame = new Rect(0, 0, 1000, 1500);
        Insets combinedInsets = Insets.of(0, 0, 0, 0);
        Rect cutoutInsets = new Rect(50, 0, 0, 0);
        when(mockInsetsState.getDisplayFrame()).thenReturn(displayFrame);
        when(mockInsetsState.calculateInsets(
                eq(displayFrame),
                eq(displayFrame),
                eq(0),
                eq(true) /* ignoreVisibility */))
                .thenReturn(combinedInsets);

        Rect outInsets = new Rect();

        DisplayLayout.computeNonDecorInsets(mockInsetsState, outInsets,
                /* hasNavigationBar= */ false, cutoutInsets);

        // 3. Assert
        assertEquals(cutoutInsets, outInsets);
    }

    @Test
    public void testRotate() {
        // Basic rotate utility
        Resources res = createResources(40, 50, false);
        DisplayInfo info = createDisplayInfo(1000, 1500, 60, ROTATION_0);
        DisplayLayout dl = new DisplayLayout(info, res, true, true);
        when(SystemBarUtils.getStatusBarHeight(eq(res), any())).thenReturn(40);
        Insets navBarsInsetsPort = Insets.of(0, 40, 0, 50);
        Rect displayFrame = new Rect(0, 0, 1000, 1500);
        InsetsState mockInsetsState = mock(InsetsState.class);
        when(mockInsetsState.getDisplayFrame()).thenReturn(displayFrame);
        when(mockInsetsState.calculateInsets(
                eq(displayFrame),
                eq(displayFrame),
                eq(WindowInsets.Type.navigationBars()),
                eq(true) /* ignoreVisibility */))
                .thenReturn(navBarsInsetsPort);
        dl.mInsetsState = mockInsetsState;

        dl.recalcInsets(res);
        assertEquals(new Rect(0, 60, 0, 50), dl.stableInsets());
        assertEquals(new Rect(0, 60, 0, 50), dl.nonDecorInsets());

        // Rotate to 90
        Insets navBarsInestsLand = Insets.of(30, 0, 0, 40);
        when(SystemBarUtils.getStatusBarHeight(eq(res), any())).thenReturn(30);
        when(mockInsetsState.calculateInsets(
                eq(displayFrame),
                eq(displayFrame),
                eq(WindowInsets.Type.navigationBars()),
                eq(true) /* ignoreVisibility */))
                .thenReturn(navBarsInestsLand);
        dl.rotateTo(res, ROTATION_90);
        assertEquals(new Rect(60, 30, 0, 40), dl.stableInsets());
        assertEquals(new Rect(60, 0, 0, 40), dl.nonDecorInsets());

        // Rotate with moving navbar
        Insets navBarsInsetsRotatedPort = Insets.of(40, 0, 60, 0);
        res = createResources(40, 50, true);
        dl = new DisplayLayout(info, res, true, true);
        when(SystemBarUtils.getStatusBarHeight(eq(res), any())).thenReturn(30);
        when(mockInsetsState.calculateInsets(
                eq(displayFrame),
                eq(displayFrame),
                eq(WindowInsets.Type.navigationBars()),
                eq(true) /* ignoreVisibility */))
                .thenReturn(navBarsInsetsRotatedPort);
        dl.mInsetsState = mockInsetsState;
        dl.rotateTo(res, ROTATION_270);
        assertEquals(new Rect(40, 30, 60, 0), dl.stableInsets());
        assertEquals(new Rect(40, 0, 60, 0), dl.nonDecorInsets());
    }

    @Test
    public void testDpPxConversion() {
        int px = 100;
        float dp = 53.33f;
        int xPx = 100;
        int yPx = 200;
        float xDp = 164.33f;
        float yDp = 328.66f;

        Resources res = createResources(40, 50, false);
        DisplayInfo info = createDisplayInfo(1000, 1500, 0, ROTATION_0);
        DisplayLayout dl = new DisplayLayout(info, res, false, false);
        dl.setGlobalBoundsDp(new RectF(111f, 222f, 300f, 400f));

        // Test pxToDp
        float resultDp = dl.pxToDp(px);
        assertEquals(dp, resultDp, DELTA);

        // Test dpToPx
        float resultPx = dl.dpToPx(dp);
        assertEquals(px, resultPx, DELTA);

        // Test localPxToGlobalDp
        PointF resultGlobalDp = dl.localPxToGlobalDp(xPx, yPx);
        assertEquals(xDp, resultGlobalDp.x, DELTA);
        assertEquals(yDp, resultGlobalDp.y, DELTA);

        // Test globalDpToLocalPx
        PointF resultLocalPx = dl.globalDpToLocalPx(xDp, yDp);
        assertEquals(xPx, resultLocalPx.x, DELTA);
        assertEquals(yPx, resultLocalPx.y, DELTA);
    }

    private Resources createResources(int navLand, int navPort, boolean navMoves) {
        Configuration cfg = new Configuration();
        cfg.uiMode = UI_MODE_TYPE_NORMAL;
        Resources res = mock(Resources.class);
        doReturn(navLand).when(res).getDimensionPixelSize(
                R.dimen.navigation_bar_height_landscape_car_mode);
        doReturn(navPort).when(res).getDimensionPixelSize(R.dimen.navigation_bar_height_car_mode);
        doReturn(navLand).when(res).getDimensionPixelSize(R.dimen.navigation_bar_width_car_mode);
        doReturn(navLand).when(res).getDimensionPixelSize(R.dimen.navigation_bar_height_landscape);
        doReturn(navPort).when(res).getDimensionPixelSize(R.dimen.navigation_bar_height);
        doReturn(navLand).when(res).getDimensionPixelSize(R.dimen.navigation_bar_width);
        doReturn(navMoves).when(res).getBoolean(R.bool.config_navBarCanMove);
        doReturn(cfg).when(res).getConfiguration();
        return res;
    }

    private DisplayInfo createDisplayInfo(int width, int height, int cutoutHeight, int rotation) {
        DisplayInfo info = new DisplayInfo();
        info.logicalWidth = width;
        info.logicalHeight = height;
        info.rotation = rotation;
        if (cutoutHeight > 0) {
            info.displayCutout = new DisplayCutout(
                    Insets.of(0, cutoutHeight, 0, 0) /* safeInsets */, null /* boundLeft */,
                    new Rect(width / 2 - cutoutHeight, 0, width / 2 + cutoutHeight,
                            cutoutHeight) /* boundTop */, null /* boundRight */,
                    null /* boundBottom */);
        } else {
            info.displayCutout = DisplayCutout.NO_CUTOUT;
        }
        info.logicalDensityDpi = 300;
        return info;
    }
}
