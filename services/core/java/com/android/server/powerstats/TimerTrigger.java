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

package com.android.server.powerstats;

import android.app.AlarmManager;
import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Slog;

import com.android.server.power.optimization.Flags;

/**
 * TimerTrigger sets a 60 second opportunistic timer using postDelayed.
 * When the timer expires a message is sent to the PowerStatsLogger to
 * read the rail energy data and log it to on-device storage.
 */
public final class TimerTrigger extends PowerStatsLogTrigger {
    private static final String TAG = TimerTrigger.class.getSimpleName();
    private static final boolean DEBUG = false;
    private static final long LOG_PERIOD_MS_LOW_FREQUENCY = 60 * 60 * 1000; // 1 hour
    private static final long LOG_PERIOD_MS_HIGH_FREQUENCY = 2 * 60 * 1000; // 2 minutes

    private final Handler mHandler;
    private final AlarmManager mAlarmManager;

    class PeriodicTimer implements Runnable, AlarmManager.OnAlarmListener {
        private final String mName;
        private final long mPeriodMs;
        private final int mMsgType;

        PeriodicTimer(String name, long periodMs, int msgType) {
            mName = name;
            mPeriodMs = periodMs;
            mMsgType = msgType;
        }

        @Override
        public void onAlarm() {
            run();
        }

        @Override
        public void run() {
            final long nextAlarmMs = SystemClock.elapsedRealtime() + mPeriodMs;
            mAlarmManager.set(AlarmManager.ELAPSED_REALTIME, nextAlarmMs,
                    AlarmManager.WINDOW_EXACT, 0, mName, this, mHandler, null);
            if (DEBUG) Slog.d(TAG, "Received delayed message (" + mName + ").  Logging rail data");
            logPowerStatsData(mMsgType);
        }
    }

    public TimerTrigger(Context context, PowerStatsLogger powerStatsLogger,
            boolean triggerEnabled) {
        super(context, powerStatsLogger);
        long logPeriodMsLowFrequency;
        long logPeriodMsHighFrequency;
        if (Flags.configurePowerStatsLogFrequency()) {
            logPeriodMsLowFrequency = mContext.getResources().getInteger(
                        com.android.internal.R.integer
                            .config_powerStatsLogPeriodLowFrequencyMs);
            logPeriodMsHighFrequency = mContext.getResources().getInteger(
                        com.android.internal.R.integer
                            .config_powerStatsLogPeriodHighFrequencyMs);
        } else {
            logPeriodMsLowFrequency = LOG_PERIOD_MS_LOW_FREQUENCY;
            logPeriodMsHighFrequency = LOG_PERIOD_MS_HIGH_FREQUENCY;
        }

        mHandler = mContext.getMainThreadHandler();
        mAlarmManager = mContext.getSystemService(AlarmManager.class);

        if (triggerEnabled) {
            final PeriodicTimer logDataLowFrequency = new PeriodicTimer("PowerStatsLowFreqLog",
                    logPeriodMsLowFrequency,
                    PowerStatsLogger.MSG_LOG_TO_DATA_STORAGE_LOW_FREQUENCY);
            final PeriodicTimer logDataHighFrequency = new PeriodicTimer("PowerStatsHighFreqLog",
                    logPeriodMsHighFrequency,
                    PowerStatsLogger.MSG_LOG_TO_DATA_STORAGE_HIGH_FREQUENCY);
            logDataLowFrequency.run();
            logDataHighFrequency.run();
        }
    }
}
