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

package com.android.server.am;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.mockito.Mockito.times;

import android.content.Context;
import android.view.Display;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.util.FrameworkStatsLog;
import com.android.modules.utils.testing.ExtendedMockitoRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BatteryStatsServiceAtomTest {

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).mockStatic(FrameworkStatsLog.class).build();

    private static final int DISPLAY_ID_0 = 0;
    private static final int DISPLAY_ID_1 = 1;

    private BatteryStatsService mBatteryStatsService;

    @Before
    public void setUp() {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mBatteryStatsService = new BatteryStatsService(context, context.getCacheDir());
    }

    @Test
    public void noteScreenState_singleOn_logsStateChangedOn() {
        mBatteryStatsService.setDisplayCount(2);

        mBatteryStatsService.noteScreenState(
                DISPLAY_ID_0, Display.STATE_ON, Display.STATE_REASON_UNKNOWN);

        verify(() -> FrameworkStatsLog.write(
                FrameworkStatsLog.SCREEN_STATE_CHANGED, Display.STATE_ON));
    }

    @Test
    public void noteScreenState_specialOn_logsStateChangedOn() {
        mBatteryStatsService.setDisplayCount(2);

        mBatteryStatsService.noteScreenState(
                DISPLAY_ID_0, Display.STATE_VR, Display.STATE_REASON_UNKNOWN);

        verify(() -> FrameworkStatsLog.write(
                FrameworkStatsLog.SCREEN_STATE_CHANGED, Display.STATE_ON));
    }

    @Test
    public void noteScreenState_offAfterOn_logsCorrectStates() {
        mBatteryStatsService.setDisplayCount(2);

        mBatteryStatsService.noteScreenState(
                DISPLAY_ID_1, Display.STATE_ON, Display.STATE_REASON_UNKNOWN);
        mBatteryStatsService.noteScreenState(
                DISPLAY_ID_1, Display.STATE_OFF, Display.STATE_REASON_UNKNOWN);

        verify(() -> FrameworkStatsLog.write(
                FrameworkStatsLog.SCREEN_STATE_CHANGED, Display.STATE_ON));
        verify(() -> FrameworkStatsLog.write(
                FrameworkStatsLog.SCREEN_STATE_CHANGED, Display.STATE_OFF));
    }

    @Test
    public void noteScreenState_dozeAfterOn_logsCorrectStates() {
        mBatteryStatsService.setDisplayCount(2);

        mBatteryStatsService.noteScreenState(
                DISPLAY_ID_1, Display.STATE_ON, Display.STATE_REASON_UNKNOWN);
        mBatteryStatsService.noteScreenState(
                DISPLAY_ID_1, Display.STATE_DOZE, Display.STATE_REASON_UNKNOWN);

        verify(() -> FrameworkStatsLog.write(
                FrameworkStatsLog.SCREEN_STATE_CHANGED, Display.STATE_ON));
        verify(() -> FrameworkStatsLog.write(
                FrameworkStatsLog.SCREEN_STATE_CHANGED, Display.STATE_DOZE));
    }

    @Test
    public void noteScreenState_successiveOns_logsCorrectStates() {
        mBatteryStatsService.setDisplayCount(2);

        mBatteryStatsService.noteScreenState(
                DISPLAY_ID_0, Display.STATE_ON, Display.STATE_REASON_UNKNOWN);
        mBatteryStatsService.noteScreenState(
                DISPLAY_ID_1, Display.STATE_ON, Display.STATE_REASON_UNKNOWN);

        verify(() -> FrameworkStatsLog.write(
                FrameworkStatsLog.SCREEN_STATE_CHANGED, Display.STATE_ON), times(2));
    }

    @Test
    public void noteScreenState_onAndOff_logsCorrectStates() {
        mBatteryStatsService.setDisplayCount(2);

        mBatteryStatsService.noteScreenState(
                DISPLAY_ID_0, Display.STATE_ON, Display.STATE_REASON_UNKNOWN);
        mBatteryStatsService.noteScreenState(
                DISPLAY_ID_1, Display.STATE_OFF, Display.STATE_REASON_UNKNOWN);

        verify(() -> FrameworkStatsLog.write(
                FrameworkStatsLog.SCREEN_STATE_CHANGED, Display.STATE_ON), times(2));
    }

    @Test
    public void noteScreenState_offAfterOns_logsCorrectStates() {
        mBatteryStatsService.setDisplayCount(2);

        mBatteryStatsService.noteScreenState(
                DISPLAY_ID_0, Display.STATE_ON, Display.STATE_REASON_UNKNOWN);
        mBatteryStatsService.noteScreenState(
                DISPLAY_ID_1, Display.STATE_ON, Display.STATE_REASON_UNKNOWN);
        mBatteryStatsService.noteScreenState(
                DISPLAY_ID_1, Display.STATE_OFF, Display.STATE_REASON_UNKNOWN);

        verify(() -> FrameworkStatsLog.write(
                FrameworkStatsLog.SCREEN_STATE_CHANGED, Display.STATE_ON), times(3));
    }

    @Test
    public void noteScreenState_offsAfterOns_logsCorrectStates() {
        mBatteryStatsService.setDisplayCount(2);

        mBatteryStatsService.noteScreenState(
                DISPLAY_ID_0, Display.STATE_ON, Display.STATE_REASON_UNKNOWN);
        mBatteryStatsService.noteScreenState(
                DISPLAY_ID_1, Display.STATE_ON, Display.STATE_REASON_UNKNOWN);
        mBatteryStatsService.noteScreenState(
                DISPLAY_ID_1, Display.STATE_OFF, Display.STATE_REASON_UNKNOWN);
        mBatteryStatsService.noteScreenState(
                DISPLAY_ID_0, Display.STATE_OFF, Display.STATE_REASON_UNKNOWN);

        verify(() -> FrameworkStatsLog.write(
                FrameworkStatsLog.SCREEN_STATE_CHANGED, Display.STATE_ON), times(3));
        verify(() -> FrameworkStatsLog.write(
                FrameworkStatsLog.SCREEN_STATE_CHANGED, Display.STATE_OFF));
    }

    @Test
    public void noteScreenState_noDisplaysSet_logStateChangedNotCalled() {
        mBatteryStatsService.setDisplayCount(0);

        mBatteryStatsService.noteScreenState(
                DISPLAY_ID_0, Display.STATE_ON, Display.STATE_REASON_UNKNOWN);

        verify(() -> FrameworkStatsLog.write(
                FrameworkStatsLog.SCREEN_STATE_CHANGED, Display.STATE_ON), never());
    }
}
