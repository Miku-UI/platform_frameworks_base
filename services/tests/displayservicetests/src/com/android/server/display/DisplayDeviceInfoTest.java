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

package com.android.server.display;

import static android.view.Display.Mode.FLAG_ANISOTROPY_CORRECTION;
import static android.view.Display.Mode.FLAG_SIZE_OVERRIDE;
import static android.view.Display.Mode.INVALID_MODE_ID;

import static com.android.server.display.DisplayDeviceInfo.DIFF_COLOR_MODE;
import static com.android.server.display.DisplayDeviceInfo.DIFF_COMMITTED_STATE;
import static com.android.server.display.DisplayDeviceInfo.DIFF_HDR_SDR_RATIO;
import static com.android.server.display.DisplayDeviceInfo.DIFF_MODE_ID;
import static com.android.server.display.DisplayDeviceInfo.DIFF_RENDER_TIMINGS;
import static com.android.server.display.DisplayDeviceInfo.DIFF_ROTATION;
import static com.android.server.display.DisplayDeviceInfo.DIFF_STATE;

import static com.google.common.truth.Truth.assertThat;

import android.view.Display;
import android.view.Surface;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class DisplayDeviceInfoTest {

    @Test
    public void testDiff_noChange() {
        var oldDdi = createInfo();
        var newDdi = createInfo();

        assertThat(oldDdi.diff(newDdi)).isEqualTo(0);
    }

    @Test
    public void testDiff_state() {
        var oldDdi = createInfo();
        var newDdi = createInfo();

        newDdi.state = Display.STATE_VR;
        assertThat(oldDdi.diff(newDdi)).isEqualTo(DIFF_STATE);
    }

    @Test
    public void testDiff_committedState() {
        var oldDdi = createInfo();
        var newDdi = createInfo();

        newDdi.committedState = Display.STATE_UNKNOWN;
        assertThat(oldDdi.diff(newDdi)).isEqualTo(DIFF_COMMITTED_STATE);
    }

    @Test
    public void testDiff_colorMode() {
        var oldDdi = createInfo();
        var newDdi = createInfo();

        newDdi.colorMode = Display.COLOR_MODE_DISPLAY_P3;
        assertThat(oldDdi.diff(newDdi)).isEqualTo(DIFF_COLOR_MODE);
    }

    @Test
    public void testDiff_hdrSdrRatio() {
        var oldDdi = createInfo();
        var newDdi = createInfo();

        /* First change new ratio to non-NaN */
        newDdi.hdrSdrRatio = 2.3f;
        assertThat(oldDdi.diff(newDdi)).isEqualTo(DIFF_HDR_SDR_RATIO);

        /* Then change old to be non-NaN and also distinct */
        oldDdi.hdrSdrRatio = 1.1f;
        assertThat(oldDdi.diff(newDdi)).isEqualTo(DIFF_HDR_SDR_RATIO);

        /* Now make the new one NaN and the old one non-NaN */
        newDdi.hdrSdrRatio = Float.NaN;
        assertThat(oldDdi.diff(newDdi)).isEqualTo(DIFF_HDR_SDR_RATIO);
    }

    @Test
    public void testDiff_rotation() {
        var oldDdi = createInfo();
        var newDdi = createInfo();

        newDdi.rotation = Surface.ROTATION_270;
        assertThat(oldDdi.diff(newDdi)).isEqualTo(DIFF_ROTATION);
    }

    @Test
    public void testDiff_frameRate() {
        var oldDdi = createInfo();
        var newDdi = createInfo();

        newDdi.renderFrameRate = 123.4f;
        assertThat(oldDdi.diff(newDdi)).isEqualTo(DIFF_RENDER_TIMINGS);
        newDdi.renderFrameRate = oldDdi.renderFrameRate;

        newDdi.appVsyncOffsetNanos = 31222221;
        assertThat(oldDdi.diff(newDdi)).isEqualTo(DIFF_RENDER_TIMINGS);
        newDdi.appVsyncOffsetNanos = oldDdi.appVsyncOffsetNanos;

        newDdi.presentationDeadlineNanos = 23000000;
        assertThat(oldDdi.diff(newDdi)).isEqualTo(DIFF_RENDER_TIMINGS);
    }

    @Test
    public void testDiff_modeId() {
        var oldDdi = createInfo();
        var newDdi = createInfo();

        newDdi.modeId = 9;
        assertThat(oldDdi.diff(newDdi)).isEqualTo(DIFF_MODE_ID);
    }

    @Test
    public void testDisplayModeForSizeOverride_noUserPreferredMode() {
        var ddi = createInfo();

        assertThat(ddi.getDisplayModeForSizeOverride()).isNull();
    }

    @Test
    public void testDisplayModeForSizeOverride_sizeOverride() {
        var ddi = createInfo();
        var mode = TestUtilsKt.createDisplayMode(
                /* id= */ 1, /* parentId= */ 2, /* sfModeId= */ INVALID_MODE_ID,
                FLAG_SIZE_OVERRIDE, 100, 100);
        ddi.supportedModes = new Display.Mode[] { mode };
        ddi.userPreferredModeId = mode.getModeId();

        assertThat(ddi.getDisplayModeForSizeOverride()).isEqualTo(mode);
    }

    @Test
    public void testDisplayModeForSizeOverride_sizeOverrideNotMatching() {
        var ddi = createInfo();
        var mode1 = TestUtilsKt.createDisplayMode(
                /* id= */ 1, /* parentId= */ 2, /* sfModeId= */ INVALID_MODE_ID,
                FLAG_SIZE_OVERRIDE, 100, 100);
        ddi.supportedModes = new Display.Mode[] { mode1 };
        ddi.userPreferredModeId = 100;

        assertThat(ddi.getDisplayModeForSizeOverride()).isNull();
    }

    @Test
    public void testDisplayModeForSizeOverride_anisotropyCorrected_baseModeActive() {
        var ddi = createInfo();
        var baseMode = TestUtilsKt.createDisplayMode(
                /* id= */ 1, /* parentId= */ INVALID_MODE_ID, /* sfModeId= */ INVALID_MODE_ID,
                0, 100, 100);
        var anisotropyCorrectedMode = TestUtilsKt.createDisplayMode(
                /* id= */ 2, /* parentId= */ 1, /* sfModeId= */ INVALID_MODE_ID,
                FLAG_ANISOTROPY_CORRECTION, 200, 200);
        ddi.supportedModes = new Display.Mode[] { baseMode, anisotropyCorrectedMode };
        ddi.userPreferredModeId = anisotropyCorrectedMode.getModeId();
        ddi.modeId = baseMode.getModeId();

        assertThat(ddi.getDisplayModeForSizeOverride()).isEqualTo(anisotropyCorrectedMode);
    }

    @Test
    public void testDisplayModeForSizeOverride_anisotropyCorrected_matchingSizeModeActive() {
        var ddi = createInfo();
        var baseMode = TestUtilsKt.createDisplayMode(
                /* id= */ 1, /* parentId= */ INVALID_MODE_ID, /* sfModeId= */ INVALID_MODE_ID,
                0, 100, 100);
        var anisotropyCorrectedMode = TestUtilsKt.createDisplayMode(
                /* id= */ 2, /* parentId= */ 1, /* sfModeId= */ INVALID_MODE_ID,
                FLAG_ANISOTROPY_CORRECTION, 200, 200);
        var matchingSizeMode = TestUtilsKt.createDisplayMode(
                /* id= */ 3, /* parentId= */ INVALID_MODE_ID, /* sfModeId= */ INVALID_MODE_ID,
                0, 100, 100);
        ddi.supportedModes = new Display.Mode[] { baseMode, anisotropyCorrectedMode,
                matchingSizeMode };
        ddi.userPreferredModeId = anisotropyCorrectedMode.getModeId();
        ddi.modeId = matchingSizeMode.getModeId();

        assertThat(ddi.getDisplayModeForSizeOverride()).isEqualTo(anisotropyCorrectedMode);
    }

    @Test
    public void testDisplayModeForSizeOverride_anisotropyCorrected_baseModeNotActive() {
        var ddi = createInfo();
        var baseMode = TestUtilsKt.createDisplayMode(
                /* id= */ 1, /* parentId= */ INVALID_MODE_ID, /* sfModeId= */ INVALID_MODE_ID,
                0, 100, 100);
        var anisotropyCorrectedMode = TestUtilsKt.createDisplayMode(
                /* id= */ 2, /* parentId= */ 1, /* sfModeId= */ INVALID_MODE_ID,
                FLAG_ANISOTROPY_CORRECTION, 200, 200);
        ddi.supportedModes = new Display.Mode[] { baseMode, anisotropyCorrectedMode };
        ddi.userPreferredModeId = anisotropyCorrectedMode.getModeId();
        ddi.modeId = 100;

        assertThat(ddi.getDisplayModeForSizeOverride()).isNull();
    }

    private static DisplayDeviceInfo createInfo() {
        var ddi = new DisplayDeviceInfo();
        ddi.name = "TestDisplayDeviceInfo";
        ddi.uniqueId = "test:51651561321";
        ddi.width = 671;
        ddi.height = 483;
        ddi.modeId = 2;
        ddi.renderFrameRate = 68.9f;
        ddi.supportedModes = new Display.Mode[] {
                new Display.Mode.Builder().setRefreshRate(68.9f).setResolution(671, 483).build(),
        };
        ddi.appVsyncOffsetNanos = 6233332;
        ddi.presentationDeadlineNanos = 11500000;
        ddi.rotation = Surface.ROTATION_90;
        ddi.state = Display.STATE_ON;
        ddi.committedState = Display.STATE_DOZE_SUSPEND;
        return ddi;
    }
}
