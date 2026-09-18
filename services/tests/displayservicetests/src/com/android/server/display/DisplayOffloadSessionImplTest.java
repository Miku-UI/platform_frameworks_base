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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.hardware.display.DisplayManagerInternal;
import android.util.StatsEvent;
import android.util.StatsLog;
import android.view.Display;

import com.android.internal.util.FrameworkStatsLog;
import com.android.modules.utils.testing.ExtendedMockitoRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Method;

public class DisplayOffloadSessionImplTest {

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).mockStatic(StatsLog.class).build();

    @Mock
    private DisplayManagerInternal.DisplayOffloader mDisplayOffloader;

    @Mock
    private DisplayPowerController mDisplayPowerController;

    @Captor
    private ArgumentCaptor<StatsEvent> mStatsEventCaptor;

    private DisplayOffloadSessionImpl mSession;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mDisplayOffloader.startOffload(anyInt())).thenReturn(true);
        mSession = new DisplayOffloadSessionImpl(mDisplayOffloader, mDisplayPowerController);
    }

    @Test
    public void testStartOffload() {
        mSession.startOffload(Display.STATE_DOZE_SUSPEND);
        assertTrue(mSession.isActive());

        // An active session shouldn't be started again
        mSession.startOffload(Display.STATE_DOZE_SUSPEND);
        verify(mDisplayOffloader, times(1)).startOffload(Display.STATE_DOZE_SUSPEND);
    }

    @Test
    public void testStopOffload() throws Exception {
        mSession.startOffload(Display.STATE_DOZE_SUSPEND);
        mSession.stopOffload(Display.STATE_ON);

        assertFalse(mSession.isActive());

        verify(() -> StatsLog.write(mStatsEventCaptor.capture()), atLeast(1));
        StatsEvent statsEvent = mStatsEventCaptor.getValue();

        // Use reflection to access hidden API on StatsEvent.
        // Normally we would use StatsEventTestUtils however this doesn't
        // work for empty atoms b/287773614.
        Class<?> statsEventClass = Class.forName("android.util.StatsEvent");
        Method getAtomIdMethod = statsEventClass.getMethod("getAtomId");
        int atomId = (int) getAtomIdMethod.invoke(statsEvent);
        assertThat(atomId).isEqualTo(FrameworkStatsLog.DISPLAY_SWITCH_TO_AP_ISSUED);

        // An inactive session shouldn't be stopped again
        mSession.stopOffload(Display.STATE_ON);
        verify(mDisplayOffloader, times(1)).stopOffload(Display.STATE_ON);
    }

    @Test
    public void testUpdateBrightness_sessionInactive() {
        mSession.updateBrightness(0.3f);
        verify(mDisplayPowerController, never()).setBrightnessFromOffload(anyFloat());
    }

    @Test
    public void testUpdateBrightness_sessionActive() {
        float brightness = 0.3f;

        mSession.startOffload(Display.STATE_DOZE_SUSPEND);
        mSession.updateBrightness(brightness);

        verify(mDisplayPowerController).setBrightnessFromOffload(brightness);
    }

    @Test
    public void testBlockScreenOn() {
        Runnable unblocker = () -> {};
        mSession.blockScreenOn(unblocker);

        verify(mDisplayOffloader).onBlockingScreenOn(eq(unblocker));
    }

    @Test
    public void testUnblockScreenOn() {
        mSession.cancelBlockScreenOn();

        verify(mDisplayOffloader).cancelBlockScreenOn();
    }
}
