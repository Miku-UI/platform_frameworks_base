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

import static com.android.server.alarm.Alarm.EXACT_ALLOW_REASON_ALLOW_LIST;

import android.app.AlarmManager;
import android.app.IAlarmCompleteListener;
import android.app.IAlarmListener;
import android.content.Intent;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Helper class to schedule and handle internal alarms for time zone offset changes (e.g. Daylight
 * Saving Time).
 */
final class TimeZoneOffsetHelper {
    private static final String TAG = "AlarmManager-TimeZoneOffsetHelper";
    private static final boolean DEBUG = false;

    /**
     * Tag for the internal alarm we set to trigger at the next time zone offset change (e.g. DST).
     */
    private static final String TIME_ZONE_OFFSET_CHANGE_ALARM_TAG = "*tz_offset_change*";

    private final AlarmManagerService mService;
    private final AlarmManagerService.Injector mInjector;

    /** The main lock from AlarmManagerService, used to synchronize access to alarm state. */
    private final Object mLock;

    @GuardedBy("mLock")
    private final Intent mTimeZoneOffsetChangedIntent;

    private final IAlarmListener mTimeZoneOffsetChangedTrigger;

    TimeZoneOffsetHelper(
            AlarmManagerService service, Object lock, AlarmManagerService.Injector injector) {
        mService = service;
        mLock = lock;
        mInjector = injector;
        mTimeZoneOffsetChangedIntent =
                new Intent(Intent.ACTION_TIMEZONE_OFFSET_CHANGED)
                        .addFlags(
                                Intent.FLAG_RECEIVER_REPLACE_PENDING
                                        | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND
                                        | Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
                                        | Intent.FLAG_RECEIVER_VISIBLE_TO_INSTANT_APPS);
        mTimeZoneOffsetChangedTrigger =
                new IAlarmListener.Stub() {
                    @Override
                    public void doAlarm(IAlarmCompleteListener callback) {
                        if (DEBUG) {
                            Slog.v(TAG, "Time zone offset changed, broadcasting");
                        }

                        final Intent intentToSend;
                        synchronized (mLock) {
                            intentToSend = new Intent(mTimeZoneOffsetChangedIntent);
                        }

                        // Via handler dispatch to avoid holding the alarm manager service lock.
                        // OnAlarmListener takes care of this automatically, but we're using the
                        // direct internal interface here rather than that client-side wrapper
                        // infrastructure.
                        mService.getHandler()
                                .post(
                                        () -> {
                                            mService.getContext()
                                                    .sendBroadcastAsUser(
                                                            intentToSend,
                                                            UserHandle.ALL,
                                                            /* receiverPermission= */ null);

                                            scheduleNextTzOffsetTransition(/* newTimeZone= */ null);
                                            try {
                                                callback.alarmComplete(this);
                                            } catch (RemoteException e) {
                                                /* local method call */
                                            }
                                        });
                    }
                };
    }

    /**
     * Calculates the next time zone offset change for the given time zone and schedules an exact
     * internal alarm to fire at that time.
     *
     * @param newTimeZone the new time zone to schedule the alarm for, or {@code null} to use the
     *     system default time zone.
     */
    void scheduleNextTzOffsetTransition(TimeZone newTimeZone) {
        synchronized (mLock) {
            mService.removeImpl(null, mTimeZoneOffsetChangedTrigger);

            final String timeZoneId =
                    newTimeZone != null ? newTimeZone.getID() : TimeZone.getDefault().getID();

            // Find the next transition *after* the current time
            final ZoneRules rules;
            try {
                rules = ZoneId.of(timeZoneId).getRules();
            } catch (DateTimeException e) {
                Slog.w(TAG, "Unknown time zone " + timeZoneId, e);
                return;
            }
            final ZoneOffsetTransition transition =
                    rules.nextTransition(Instant.ofEpochMilli(mInjector.getCurrentTimeMillis()));

            if (transition == null) {
                Slog.d(TAG, "No upcoming time zone offset change for " + timeZoneId);
                return;
            }

            mTimeZoneOffsetChangedIntent
                    .putExtra(
                            Intent.EXTRA_NEW_TIMEZONE_OFFSET,
                            transition.getOffsetAfter().getTotalSeconds())
                    .putExtra(
                            Intent.EXTRA_OLD_TIMEZONE_OFFSET,
                            transition.getOffsetBefore().getTotalSeconds());

            final long transitionTimeMillis = TimeUnit.SECONDS.toMillis(transition.toEpochSecond());
            Slog.i(TAG, "Scheduling next time zone offset change alarm for: " + transition);

            mService.setImpl(
                    /* type= */ AlarmManager.RTC,
                    /* triggerAtTime= */ transitionTimeMillis,
                    /* windowLength= */ 0,
                    /* interval= */ 0,
                    /* operation= */ null,
                    /* directReceiver= */ mTimeZoneOffsetChangedTrigger,
                    /* listenerTag= */ TIME_ZONE_OFFSET_CHANGE_ALARM_TAG,
                    /* flags= */ AlarmManager.FLAG_STANDALONE,
                    /* workSource= */ null,
                    /* alarmClock= */ null,
                    /* callingUid= */ Process.myUid(),
                    /* callingPackage= */ "android",
                    /* idleOptions= */ null,
                    /* exactAllowReason= */ EXACT_ALLOW_REASON_ALLOW_LIST);
        }
    }
}
