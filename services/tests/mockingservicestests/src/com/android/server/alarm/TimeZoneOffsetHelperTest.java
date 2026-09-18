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

package com.android.server.alarm;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AlarmManager;
import android.app.AlarmManager.AlarmClockInfo;
import android.app.IAlarmCompleteListener;
import android.app.IAlarmListener;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.WorkSource;
import android.util.SparseArray;
import android.os.UserHandle;
import android.platform.test.annotations.Presubmit;

import androidx.test.runner.AndroidJUnit4;

import com.android.modules.utils.testing.ExtendedMockitoRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.ZoneId;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.TimeZone;
import java.util.concurrent.Executor;

@Presubmit
@RunWith(AndroidJUnit4.class)
public class TimeZoneOffsetHelperTest {

    private static final String TZ_ID_LA = "America/Los_Angeles";
    private static final String TZ_ID_UTC = "Etc/UTC";
    // 2025-01-01 00:00:00 UTC
    private static final long TEST_TIME_JAN_1_2025 = 1735732800000L;

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).build();

    @Mock private AlarmManagerService mAlarmManagerService;
    @Mock private AlarmManagerService.Injector mInjector;
    @Mock private Context mContext;
    @Mock private AlarmManagerService.AlarmHandler mHandler;

    private final Object mLock = new Object();
    private TimeZoneOffsetHelper mTimeZoneOffsetHelper;
    private TimeZone mOriginalTimeZone;

    @Before
    public void setUp() throws Exception {
        mOriginalTimeZone = TimeZone.getDefault();

        when(mAlarmManagerService.getContext()).thenReturn(mContext);
        when(mAlarmManagerService.getHandler()).thenReturn(mHandler);
        when(mInjector.getCurrentTimeMillis()).thenReturn(TEST_TIME_JAN_1_2025);

        // Make the handler run the runnable immediately
        doAnswer(
                        invocation -> {
                            Runnable r = invocation.getArgument(0);
                            r.run();
                            return null;
                        })
                .when(mHandler)
                .post(any(Runnable.class));

        mTimeZoneOffsetHelper = new TimeZoneOffsetHelper(mAlarmManagerService, mLock, mInjector);
    }

    @After
    public void tearDown() {
        TimeZone.setDefault(mOriginalTimeZone);
    }

    @Test
    public void scheduleNextTzOffsetTransition_schedulesAlarmCorrectly() {
        TimeZone.setDefault(TimeZone.getTimeZone(TZ_ID_LA));

        mTimeZoneOffsetHelper.scheduleNextTzOffsetTransition(null);

        verify(mAlarmManagerService).removeImpl(isNull(), any(IAlarmListener.class));

        ArgumentCaptor<Long> triggerAtTimeCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<IAlarmListener> listenerCaptor =
                ArgumentCaptor.forClass(IAlarmListener.class);

        verify(mAlarmManagerService)
                .setImpl(
                        eq(AlarmManager.RTC),
                        triggerAtTimeCaptor.capture(),
                        anyLong(),
                        anyLong(),
                        isNull(),
                        listenerCaptor.capture(),
                        anyString(),
                        anyInt(),
                        isNull(),
                        isNull(),
                        anyInt(),
                        anyString(),
                        isNull(),
                        anyInt());

        ZoneRules rules = ZoneId.of(TZ_ID_LA).getRules();
        ZoneOffsetTransition transition =
                rules.nextTransition(Instant.ofEpochMilli(TEST_TIME_JAN_1_2025));
        long expectedTriggerTime = transition.toEpochSecond() * 1000;

        assertEquals(expectedTriggerTime, triggerAtTimeCaptor.getValue().longValue());
    }

    @Test
    public void scheduleNextTzOffsetTransition_noTransition() {
        TimeZone.setDefault(TimeZone.getTimeZone(TZ_ID_UTC));

        mTimeZoneOffsetHelper.scheduleNextTzOffsetTransition(null);

        verify(mAlarmManagerService).removeImpl(isNull(), any(IAlarmListener.class));
        verify(mAlarmManagerService, never())
                .setImpl(
                        anyInt(),
                        anyLong(),
                        anyLong(),
                        anyLong(),
                        isNull(),
                        any(IAlarmListener.class),
                        anyString(),
                        anyInt(),
                        isNull(),
                        isNull(),
                        anyInt(),
                        anyString(),
                        isNull(),
                        anyInt());
    }

    @Test
    public void alarmTrigger_broadcastsIntentAndReschedules() throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone(TZ_ID_LA));

        mTimeZoneOffsetHelper.scheduleNextTzOffsetTransition(null);

        verify(mAlarmManagerService).removeImpl(isNull(), any(IAlarmListener.class));
        ArgumentCaptor<IAlarmListener> listenerCaptor =
                ArgumentCaptor.forClass(IAlarmListener.class);
        verify(mAlarmManagerService)
                .setImpl(
                        eq(AlarmManager.RTC),
                        anyLong(),
                        anyLong(),
                        anyLong(),
                        isNull(),
                        listenerCaptor.capture(),
                        anyString(),
                        anyInt(),
                        isNull(),
                        isNull(),
                        anyInt(),
                        anyString(),
                        isNull(),
                        anyInt());

        IAlarmListener listener = listenerCaptor.getValue();
        IAlarmCompleteListener completeListener = mock(IAlarmCompleteListener.class);
        listener.doAlarm(completeListener);

        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendBroadcastAsUser(intentCaptor.capture(), eq(UserHandle.ALL), any());

        Intent capturedIntent = intentCaptor.getValue();
        assertEquals(Intent.ACTION_TIMEZONE_OFFSET_CHANGED, capturedIntent.getAction());

        ZoneRules rules = ZoneId.of(TZ_ID_LA).getRules();
        ZoneOffsetTransition transition =
                rules.nextTransition(Instant.ofEpochMilli(TEST_TIME_JAN_1_2025));

        assertEquals(
                transition.getOffsetAfter().getTotalSeconds(),
                capturedIntent.getIntExtra(Intent.EXTRA_NEW_TIMEZONE_OFFSET, -1));
        assertEquals(
                transition.getOffsetBefore().getTotalSeconds(),
                capturedIntent.getIntExtra(Intent.EXTRA_OLD_TIMEZONE_OFFSET, -1));

        // Verify that it reschedules
        verify(mAlarmManagerService, Mockito.times(2)).removeImpl(any(), any());
        verify(mAlarmManagerService, Mockito.times(2))
                .setImpl(
                        eq(AlarmManager.RTC),
                        anyLong(),
                        anyLong(),
                        anyLong(),
                        isNull(),
                        any(IAlarmListener.class),
                        anyString(),
                        anyInt(),
                        isNull(),
                        isNull(),
                        anyInt(),
                        anyString(),
                        isNull(),
                        anyInt());

        verify(completeListener).alarmComplete(listener.asBinder());
    }
}
