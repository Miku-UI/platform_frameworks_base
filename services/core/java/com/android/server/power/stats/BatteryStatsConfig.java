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

import android.annotation.NonNull;
import android.os.BatteryConsumer;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Provide BatteryStats configuration choices */
public class BatteryStatsConfig {
    private static final int RESET_ON_UNPLUG_HIGH_BATTERY_LEVEL_FLAG = 1 << 0;
    private static final int RESET_ON_UNPLUG_AFTER_SIGNIFICANT_CHARGE_FLAG = 1 << 1;

    private final int mFlags;
    private final Long mDefaultPowerStatsThrottlePeriod;
    private final Map<String, Long> mPowerStatsThrottlePeriods;
    private final int mMaxHistorySizeBytes;
    private final int mHighBatteryLevelAfterCharge;

    private BatteryStatsConfig(Builder builder) {
        int flags = 0;
        if (builder.mResetOnUnplugHighBatteryLevel) {
            flags |= RESET_ON_UNPLUG_HIGH_BATTERY_LEVEL_FLAG;
        }
        if (builder.mResetOnUnplugAfterSignificantCharge) {
            flags |= RESET_ON_UNPLUG_AFTER_SIGNIFICANT_CHARGE_FLAG;
        }
        mFlags = flags;
        mDefaultPowerStatsThrottlePeriod = builder.mDefaultPowerStatsThrottlePeriod;
        mPowerStatsThrottlePeriods = builder.mPowerStatsThrottlePeriods;
        mMaxHistorySizeBytes = builder.mMaxHistorySizeBytes;
        mHighBatteryLevelAfterCharge = builder.mHighBatteryLevelAfterCharge;
    }

    /**
     * Returns whether a BatteryStats reset should occur on unplug when the battery level is
     * high.
     */
    public boolean shouldResetOnUnplugHighBatteryLevel() {
        return (mFlags & RESET_ON_UNPLUG_HIGH_BATTERY_LEVEL_FLAG)
                == RESET_ON_UNPLUG_HIGH_BATTERY_LEVEL_FLAG;
    }

    /**
     * Returns battery level (as percent of battery) to consider as "high enough" to trigger
     * a battery session reset.
     * Only has an effect if {@link #shouldResetOnUnplugHighBatteryLevel} is true.
     */
    public int getHighBatteryLevelAfterCharge() {
        return mHighBatteryLevelAfterCharge;
    }

    /**
     * Returns whether a BatteryStats reset should occur on unplug if the battery charge a
     * significant amount since it has been plugged in.
     */
    public boolean shouldResetOnUnplugAfterSignificantCharge() {
        return (mFlags & RESET_ON_UNPLUG_AFTER_SIGNIFICANT_CHARGE_FLAG)
                == RESET_ON_UNPLUG_AFTER_SIGNIFICANT_CHARGE_FLAG;
    }

    /**
     * Returns  the minimum amount of time (in millis) to wait between passes
     * of power stats collection for the specified power component.
     */
    public long getPowerStatsThrottlePeriod(@NonNull String powerComponentName) {
        return mPowerStatsThrottlePeriods.getOrDefault(powerComponentName,
                mDefaultPowerStatsThrottlePeriod);
    }

    public int getMaxHistorySizeBytes() {
        return mMaxHistorySizeBytes;
    }

    /**
     * Builder for BatteryStatsConfig
     */
    public static class Builder {
        private static final long DEFAULT_POWER_STATS_THROTTLE_PERIOD =
                TimeUnit.HOURS.toMillis(1);
        private static final long DEFAULT_POWER_STATS_THROTTLE_PERIOD_CPU =
                TimeUnit.MINUTES.toMillis(1);
        private static final int DEFAULT_MAX_HISTORY_SIZE = 4 * 1024 * 1024;
        private static final int DEFAULT_HIGH_BATTERY_LEVEL_AFTER_CHARGE = 90;

        private boolean mResetOnUnplugHighBatteryLevel;
        private boolean mResetOnUnplugAfterSignificantCharge;
        private long mDefaultPowerStatsThrottlePeriod = DEFAULT_POWER_STATS_THROTTLE_PERIOD;
        private final Map<String, Long> mPowerStatsThrottlePeriods = new HashMap<>();
        private int mMaxHistorySizeBytes = DEFAULT_MAX_HISTORY_SIZE;
        private int mHighBatteryLevelAfterCharge = DEFAULT_HIGH_BATTERY_LEVEL_AFTER_CHARGE;

        public Builder() {
            mResetOnUnplugHighBatteryLevel = true;
            mResetOnUnplugAfterSignificantCharge = true;
            setPowerStatsThrottlePeriodMillis(BatteryConsumer.powerComponentIdToString(
                            BatteryConsumer.POWER_COMPONENT_CPU),
                    DEFAULT_POWER_STATS_THROTTLE_PERIOD_CPU);
        }

        /**
         * Build the BatteryStatsConfig.
         */
        public BatteryStatsConfig build() {
            return new BatteryStatsConfig(this);
        }

        /**
         * Set whether a BatteryStats reset should occur on unplug when the battery level is
         * high.
         */
        public Builder setResetOnUnplugHighBatteryLevel(boolean reset) {
            mResetOnUnplugHighBatteryLevel = reset;
            return this;
        }

        /**
         * Set whether a BatteryStats reset should occur on unplug if the battery charge a
         * significant amount since it has been plugged in.
         */
        public Builder setResetOnUnplugAfterSignificantCharge(boolean reset) {
            mResetOnUnplugAfterSignificantCharge = reset;
            return this;
        }

        /**
         * Sets the minimum amount of time (in millis) to wait between passes
         * of power stats collection for the specified power component.
         */
        public Builder setPowerStatsThrottlePeriodMillis(@NonNull String powerComponentName,
                long periodMs) {
            if (periodMs < 0) {
                throw new IllegalArgumentException("periodMs cannot be negative: " + periodMs);
            }
            mPowerStatsThrottlePeriods.put(powerComponentName, periodMs);
            return this;
        }

        /**
         * Sets the minimum amount of time (in millis) to wait between passes
         * of power stats collection for any components not configured explicitly.
         */
        public Builder setDefaultPowerStatsThrottlePeriodMillis(long periodMs) {
            if (periodMs < 0) {
                throw new IllegalArgumentException("periodMs cannot be negative: " + periodMs);
            }
            mDefaultPowerStatsThrottlePeriod = periodMs;
            return this;
        }

        /**
         * Sets the maximum amount of disk space, in bytes, that battery history can
         * utilize. As this space fills up, the oldest history chunks must be expunged.
         */
        public Builder setMaxHistorySizeBytes(int maxHistorySizeBytes) {
            mMaxHistorySizeBytes = maxHistorySizeBytes;
            return this;
        }

        /** Sets battery level (as percent of battery) to consider as "high enough" to
         * trigger a battery session reset.*/
        public Builder setHighBatteryLevelAfterCharge(int highBatteryLevelAfterCharge) {
            if (highBatteryLevelAfterCharge < 0 || highBatteryLevelAfterCharge > 100) {
                throw new IllegalArgumentException(
                        "highBatteryLevelAfterCharge must be between 0 and 100: "
                                + highBatteryLevelAfterCharge);
            }
            mHighBatteryLevelAfterCharge = highBatteryLevelAfterCharge;
            return this;
        }
    }
}
