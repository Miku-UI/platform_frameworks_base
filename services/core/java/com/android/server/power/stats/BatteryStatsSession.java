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

package com.android.server.power.stats;

import com.android.internal.os.BatteryStatsHistory;

/**
 * A snapshot of battery stats at a certain point.
 */
public class BatteryStatsSession {
    private final BatteryStatsHistory mHistory;
    private final long mMonotonicStartTime;
    private final long mStartClockTime;
    private final long mEstimatedBatteryCapacityMah;
    private final long mBatteryTimeRemainingMs;
    private final long mChargeTimeRemainingMs;
    private final String[] mCustomEnergyConsumerNames;

    BatteryStatsSession(BatteryStatsHistory history, long monotonicStartTime,
            long startClockTime, long batteryTimeRemainingMs, long chargeTimeRemainingMs,
            long estimatedBatteryCapacityMah, String[] customEnergyConsumerNames) {
        mHistory = history;
        mMonotonicStartTime = monotonicStartTime;
        mStartClockTime = startClockTime;
        mEstimatedBatteryCapacityMah = estimatedBatteryCapacityMah;
        mBatteryTimeRemainingMs = batteryTimeRemainingMs;
        mChargeTimeRemainingMs = chargeTimeRemainingMs;
        mCustomEnergyConsumerNames = customEnergyConsumerNames;
    }

    public BatteryStatsHistory getHistory() {
        return mHistory;
    }

    public long getMonotonicStartTime() {
        return mMonotonicStartTime;
    }

    public long getStartClockTime() {
        return mStartClockTime;
    }

    public long getBatteryTimeRemainingMs() {
        return mBatteryTimeRemainingMs;
    }

    public long getChargeTimeRemainingMs() {
        return mChargeTimeRemainingMs;
    }

    public long getEstimatedBatteryCapacity() {
        return mEstimatedBatteryCapacityMah;
    }

    public String[] getCustomEnergyConsumerNames() {
        return mCustomEnergyConsumerNames;
    }
}

