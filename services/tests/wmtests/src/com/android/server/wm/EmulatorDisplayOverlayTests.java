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

package com.android.server.wm;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.graphics.BLASTBufferQueue;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.hardware.display.BrightnessInfo;
import android.hardware.display.DisplayManagerGlobal;
import android.platform.test.annotations.Presubmit;
import android.view.Surface;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.verification.VerificationMode;

/**
 * Tests for the {@link EmulatorDisplayOverlay} class.
 *
 * Build/Install/Run:
 * atest WmTests:EmulatorDisplayOverlayTests
 */
@Presubmit
@RunWith(WindowTestRunner.class)
public class EmulatorDisplayOverlayTests extends WindowTestsBase {

    private static final int COLOR_ALPHA_255_BLACK = 0xFF000000;
    private static final int COLOR_ALPHA_128_BLACK = 0x80000000;
    private static final int COLOR_ALPHA_0_BLACK = 0x00000000;

    @Mock
    BLASTBufferQueue mBLASTBufferQueue;
    @Mock
    Surface mSurface;
    @Mock
    Canvas mCanvas;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        doReturn(mSurface).when(mBLASTBufferQueue).createSurface();
        doReturn(mCanvas).when(mSurface).lockCanvas(any());
    }

    @Test
    public void testShowEmulatorDisplayOverlay() {
        clearInvocations(mWm);

        testShowEmulatorDisplayOverlay(
                "enableCircularOverlay and enableScreenBrightnessDim -> show "
                        + "EmulatorDisplayOverlay",
                true /* enableCircularOverlay */,
                true /* enableScreenBrightnessDim */,
                times(1));
        testShowEmulatorDisplayOverlay(
                "disableCircularOverlay and enableScreenBrightnessDim -> show "
                        + "EmulatorDisplayOverlay",
                false /* enableCircularOverlay */,
                true /* enableScreenBrightnessDim */,
                times(1));
        testShowEmulatorDisplayOverlay(
                "enableCircularOverlay and disableScreenBrightnessDim -> show "
                        + "EmulatorDisplayOverlay",
                true /* enableCircularOverlay */,
                false /* enableScreenBrightnessDim */,
                times(1));
        testShowEmulatorDisplayOverlay(
                "disableCircularOverlay and disableScreenBrightnessDim -> don't show "
                        + "EmulatorDisplayOverlay",
                false /* enableCircularOverlay */,
                false /* enableScreenBrightnessDim */,
                never());
    }

    private void testShowEmulatorDisplayOverlay(String desc,
            boolean enableCircularOverlay, boolean enableScreenBrightnessDim,
            VerificationMode expected) {
        doReturn(enableCircularOverlay).when(mWm).enableCircularEmulatorDisplayOverlay();
        doReturn(enableScreenBrightnessDim).when(
                mWm).enableScreenBrightnessEmulatorDisplayOverlay();

        mWm.showEmulatorDisplayOverlayIfNeeded();
        waitUntilHandlersIdle();

        verify(mWm, expected.description(desc)).showEmulatorDisplayOverlay();
        clearInvocations(mWm);
    }

    @Test
    public void testBackgroundColor() {
        testBackgroundColor("enabledScreenBrightnessDim: Screen brightness 100%",
                true /* enableScreenBrightnessDim */, 1.0f, COLOR_ALPHA_0_BLACK);
        testBackgroundColor("enabledScreenBrightnessDim: Screen brightness 50%",
                true /* enableScreenBrightnessDim */, 0.5f, COLOR_ALPHA_128_BLACK);
        testBackgroundColor("enabledScreenBrightnessDim: Screen brightness 0%",
                true /* enableScreenBrightnessDim */, 0.0f, COLOR_ALPHA_255_BLACK);
        testBackgroundColor("disabledScreenBrightnessDim: Screen brightness 100%",
                false /* enableScreenBrightnessDim */, 1.0f, Color.TRANSPARENT);
        testBackgroundColor("disabledScreenBrightnessDim: Screen brightness 50%",
                false /* enableScreenBrightnessDim */, 0.5f, Color.TRANSPARENT);
        testBackgroundColor("disabledScreenBrightnessDim: Screen brightness 0%",
                false /* enableScreenBrightnessDim */, 0.0f, Color.TRANSPARENT);
    }

    private void testBackgroundColor(String desc,
            boolean enableScreenBrightnessDim, float screenBrightness, int expectedColor) {
        doReturn(getBrightnessInfo(screenBrightness)).when(
                DisplayManagerGlobal.getInstance()).getBrightnessInfo(0);

        EmulatorDisplayOverlay overlay = new EmulatorDisplayOverlay(mContext, mWm,
                mDefaultDisplay, 100, mTransaction, false /* enableCircularOverlay */,
                enableScreenBrightnessDim, mBLASTBufferQueue);
        overlay.setVisibility(true, mTransaction);

        verify(mCanvas, times(1).description(desc)).drawColor(expectedColor,
                PorterDuff.Mode.SRC);

        clearInvocations(mCanvas);
    }

    @Test
    public void testChangingBackgroundColor() {
        float initScreenBrightness = 1.0f;
        float screenBrightness = 0.5f;

        doReturn(getBrightnessInfo(initScreenBrightness)).when(
                DisplayManagerGlobal.getInstance()).getBrightnessInfo(0);
        EmulatorDisplayOverlay overlay = new EmulatorDisplayOverlay(mContext, mWm, mDefaultDisplay,
                100, mTransaction, true /* enableCircularOverlay */,
                true /* enableScreenBrightnessDim */, mBLASTBufferQueue);
        overlay.setVisibility(true, mTransaction);
        clearInvocations(mCanvas);

        doReturn(getBrightnessInfo(screenBrightness)).when(
                DisplayManagerGlobal.getInstance()).getBrightnessInfo(0);
        overlay.onDisplayChanged(0);

        verify(mCanvas, times(1)).drawColor(COLOR_ALPHA_128_BLACK, PorterDuff.Mode.SRC);
    }

    private BrightnessInfo getBrightnessInfo(float brightness) {
        return new BrightnessInfo(brightness, 0.0f, 1.0f, BrightnessInfo.HIGH_BRIGHTNESS_MODE_HDR,
                0.0f, BrightnessInfo.BRIGHTNESS_MAX_REASON_NONE);
    }
}
