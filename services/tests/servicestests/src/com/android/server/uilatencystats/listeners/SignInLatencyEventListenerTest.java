/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.uilatencystats.listeners;

import static com.android.internal.util.FrameworkStatsLog.SIGN_IN_DURATION__TYPE__LOCKSCREEN_UNLOCKING_TO_LAUNCHER_SHOWN;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.uilatencystats.Event;
import android.uilatencystats.EventType;

import androidx.test.runner.AndroidJUnit4;

import com.android.internal.util.FrameworkStatsLog;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;

@RunWith(AndroidJUnit4.class)
public class SignInLatencyEventListenerTest {

    private SignInLatencyEventListener mListener;
    @Mock private SignInLatencyEventListener.StatsLogWriter mStatsLogWriter;
    @Mock private SignInLatencyEventListener.PerfettoTriggerCallback mPerfettoTriggerCallback;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mListener = new SignInLatencyEventListener();
        mListener.setPerfettoTriggerThreshold(Duration.ofSeconds(3));
        mListener.setStatsLogWriter(mStatsLogWriter);
        mListener.setPerfettoTriggerCallback(mPerfettoTriggerCallback);
    }

    @Test
    public void testOnEvent_successfulSignIn_logsStats() {
        // 1. User Switch
        mListener.onEvent(
                new Event(new EventType.UserSwitch(10), /* userId= */ 0, /* timestamp= */ 1000L));
        // 2. Lock Screen Unlock Start
        mListener.onEvent(
                new Event(
                        new EventType.LockScreenUnlockStart(),
                        /* userId= */ 10,
                        /* timestamp= */ 2000L));
        // 3. Launcher Shown
        mListener.onEvent(
                new Event(new EventType.LauncherShown(), /* userId= */ 10, /* timestamp= */ 3500L));

        verify(mStatsLogWriter)
                .write(
                        eq(FrameworkStatsLog.SIGN_IN_DURATION),
                        eq(SIGN_IN_DURATION__TYPE__LOCKSCREEN_UNLOCKING_TO_LAUNCHER_SHOWN),
                        eq(1500L)); // 3500 - 2000 = 1500
        // 1500ms is less than the threshold of 3000ms, so no perfetto trace.
        verify(mPerfettoTriggerCallback, never()).trigger(anyString());
    }

    @Test
    public void testOnEvent_slowSignIn_triggersPerfetto() {
        // 1. User Switch
        mListener.onEvent(
                new Event(new EventType.UserSwitch(10), /* userId= */ 0, /* timestamp= */ 1000L));
        // 2. Lock Screen Unlock Start
        mListener.onEvent(
                new Event(
                        new EventType.LockScreenUnlockStart(),
                        /* userId= */ 10,
                        /* timestamp= */ 2000L));
        // 3. Launcher Shown (Duration > 3s)
        long launcherShownTime = 6000L;
        mListener.onEvent(
                new Event(
                        new EventType.LauncherShown(),
                        /* userId= */ 10,
                        /* timestamp= */ launcherShownTime));

        verify(mStatsLogWriter)
                .write(
                        eq(FrameworkStatsLog.SIGN_IN_DURATION),
                        eq(SIGN_IN_DURATION__TYPE__LOCKSCREEN_UNLOCKING_TO_LAUNCHER_SHOWN),
                        eq(4000L)); // 6000 - 2000 = 4000
        // 4000ms is greater than the threshold of 3000ms, so perfetto trace is triggered.
        verify(mPerfettoTriggerCallback)
                .trigger("com.android.server.uilatencystats-LockscreenUnlockingToLauncherShown");
    }

    @Test
    public void testOnEvent_noUserSwitch_ignored() {
        // Lock Screen Unlock Start without User Switch
        mListener.onEvent(
                new Event(
                        new EventType.LockScreenUnlockStart(),
                        /* userId= */ 0,
                        /* timestamp= */ 2000L));
        // Launcher Shown
        mListener.onEvent(
                new Event(new EventType.LauncherShown(), /* userId= */ 0, /* timestamp= */ 3500L));

        verify(mStatsLogWriter, never()).write(anyInt(), anyInt(), anyLong());
        verify(mPerfettoTriggerCallback, never()).trigger(anyString());
    }

    @Test
    public void testOnEvent_noLockScreenStart_ignored() {
        // User Switch
        mListener.onEvent(
                new Event(new EventType.UserSwitch(10), /* userId= */ 0, /* timestamp= */ 1000L));
        // Launcher Shown directly (missed lock screen start)
        mListener.onEvent(
                new Event(new EventType.LauncherShown(), /* userId= */ 10, /* timestamp= */ 3500L));

        verify(mStatsLogWriter, never()).write(anyInt(), anyInt(), anyLong());
        verify(mPerfettoTriggerCallback, never()).trigger(anyString());
    }

    @Test
    public void testOnEvent_sequenceResetByUserSwitch() {
        // 1. User Switch
        mListener.onEvent(
                new Event(new EventType.UserSwitch(10), /* userId= */ 0, /* timestamp= */ 1000L));
        // 2. Lock Screen Unlock Start
        mListener.onEvent(
                new Event(
                        new EventType.LockScreenUnlockStart(),
                        /* userId= */ 10,
                        /* timestamp= */ 2000L));

        // 3. Another User Switch (resets state)
        mListener.onEvent(
                new Event(new EventType.UserSwitch(11), /* userId= */ 10, /* timestamp= */ 3000L));
        // 4. Lock Screen Unlock Start
        mListener.onEvent(
                new Event(
                        new EventType.LockScreenUnlockStart(),
                        /* userId= */ 11,
                        /* timestamp= */ 5000L));

        // 5. Launcher Shown
        mListener.onEvent(
                new Event(new EventType.LauncherShown(), /* userId= */ 11, /* timestamp= */ 6000L));

        verify(mStatsLogWriter)
                .write(
                        eq(FrameworkStatsLog.SIGN_IN_DURATION),
                        eq(SIGN_IN_DURATION__TYPE__LOCKSCREEN_UNLOCKING_TO_LAUNCHER_SHOWN),
                        eq(1000L)); // 6000 - 5000 = 1000
        // 1000ms is less than the threshold of 3000ms, so no perfetto trace.
        verify(mPerfettoTriggerCallback, never()).trigger(anyString());
    }
}
