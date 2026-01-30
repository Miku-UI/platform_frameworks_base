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

package com.android.server.power.feature;

import android.os.Build;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Slog;

import com.android.server.power.feature.flags.Flags;

import java.io.PrintWriter;
import java.util.function.Supplier;

/**
 * Utility class to read the flags used in the power manager server.
 */
public class PowerManagerFlags {
    private static final boolean DEBUG = false;
    private static final String TAG = "PowerManagerFlags";

    private final FlagState mEarlyScreenTimeoutDetectorFlagState = new FlagState(
            Flags.FLAG_ENABLE_EARLY_SCREEN_TIMEOUT_DETECTOR,
            Flags::enableEarlyScreenTimeoutDetector);

    private final FlagState mImproveWakelockLatency = new FlagState(
            Flags.FLAG_IMPROVE_WAKELOCK_LATENCY,
            Flags::improveWakelockLatency
    );

    private final FlagState mPerDisplayWakeByTouch = new FlagState(
            Flags.FLAG_PER_DISPLAY_WAKE_BY_TOUCH,
            Flags::perDisplayWakeByTouch
    );

    private final FlagState mPolicyReasonInDisplayPowerRequest = new FlagState(
            Flags.FLAG_POLICY_REASON_IN_DISPLAY_POWER_REQUEST,
            Flags::policyReasonInDisplayPowerRequest
    );

    private final FlagState mMoveWscLoggingToNotifier =
            new FlagState(Flags.FLAG_MOVE_WSC_LOGGING_TO_NOTIFIER, Flags::moveWscLoggingToNotifier);

    private final FlagState mLockOnUnplug =
            new FlagState(Flags.FLAG_LOCK_ON_UNPLUG,
                    Flags::lockOnUnplug);

    private final FlagState mWakelockAttributionViaWorkchain =
            new FlagState(Flags.FLAG_WAKELOCK_ATTRIBUTION_VIA_WORKCHAIN,
                    Flags::wakelockAttributionViaWorkchain);

    private final FlagState mDisableFrozenProcessWakelocks =
            new FlagState(Flags.FLAG_DISABLE_FROZEN_PROCESS_WAKELOCKS,
                    Flags::disableFrozenProcessWakelocks);

    private final FlagState mForceDisableWakelocks =
            new FlagState(Flags.FLAG_FORCE_DISABLE_WAKELOCKS, Flags::forceDisableWakelocks);

    private final FlagState mEnableAppWakelockDataSource =
            new FlagState(Flags.FLAG_ENABLE_APP_WAKELOCK_DATA_SOURCE,
                    Flags::enableAppWakelockDataSource);

    private final FlagState mPartialSleepWakelocks = new FlagState(
            Flags.FLAG_PARTIAL_SLEEP_WAKELOCKS,
            Flags::partialSleepWakelocks
    );

    private final FlagState mSeparateTimeoutsFlicker = new FlagState(
            Flags.FLAG_SEPARATE_TIMEOUTS_FLICKER,
            Flags::separateTimeoutsFlicker
    );

    private final FlagState mWakeAdjacentDisplaysOnWakeupCall = new FlagState(
            Flags.FLAG_WAKE_ADJACENT_DISPLAYS_ON_WAKEUP_CALL,
            Flags::wakeAdjacentDisplaysOnWakeupCall
    );

    /** Returns whether early-screen-timeout-detector is enabled on not. */
    public boolean isEarlyScreenTimeoutDetectorEnabled() {
        return mEarlyScreenTimeoutDetectorFlagState.isEnabled();
    }

    /**
     * @return Whether to improve the wakelock acquire/release latency or not
     */
    public boolean improveWakelockLatency() {
        return mImproveWakelockLatency.isEnabled();
    }

    /**
     * @return Whether per-display wake by touch is enabled or not.
     */
    public boolean isPerDisplayWakeByTouchEnabled() {
        return mPerDisplayWakeByTouch.isEnabled();
    }

    /**
     * @return Whether the wakefulness reason is populated in DisplayPowerRequest.
     */
    public boolean isPolicyReasonInDisplayPowerRequestEnabled() {
        return mPolicyReasonInDisplayPowerRequest.isEnabled();
    }

    /**
     * @return Whether we move WakelockStateChanged atom logging to Notifier (enabled) or leave it
     *     in BatteryStatsImpl (disabled).
     */
    public boolean isMoveWscLoggingToNotifierEnabled() {
        return mMoveWscLoggingToNotifier.isEnabled();
    }

    /**
     * @return {@code true} if the flag for the flicker when timing out bugfix is enabled
     */
    public boolean isSeparateTimeoutsFlickerEnabled() {
        return mSeparateTimeoutsFlicker.isEnabled();
    }

    /**
     * @return Whether the wakelock attribution via workchain is enabled
     */
    public boolean isWakelockAttributionViaWorkchainEnabled() {
        return mWakelockAttributionViaWorkchain.isEnabled();
    }

    /**
     * @return Whether to lock when all remaining adjacent displays are asleep.
     */
    public boolean isLockOnUnplugEnabled() {
        return mLockOnUnplug.isEnabled();
    }

    /**
     * @return Whether the feature to disable the frozen process wakelocks is enabled
     */
    public boolean isDisableFrozenProcessWakelocksEnabled() {
        return mDisableFrozenProcessWakelocks.isEnabled();
    }

    /**
     * @return Whether the feature to force disable wakelocks is enabled
     */
    public boolean isForceDisableWakelocksEnabled() {
        return mForceDisableWakelocks.isEnabled();
    }

    /**
     * @return Whether the new Perfetto data source for tracing app wakelocks is enabled
     */
    public boolean isAppWakelockDataSourceEnabled() {
        return mEnableAppWakelockDataSource.isEnabled();
    }

    /**
     * @return Whether new wakelock to keep device asleep - for the user, but ensures the CPU
     * remains awake - is enabled.
     */
    public boolean isPartialSleepWakelocksFeatureEnabled() {
        return mPartialSleepWakelocks.isEnabled();
    }

    /**
     * @return Whether the system should wakeup the adjacent displays too on a wakeup call
     */
    public boolean isWakeAdjacentDisplaysOnWakeupCallEnabled() {
        return mWakeAdjacentDisplaysOnWakeupCall.isEnabled();
    }

    /**
     * dumps all flagstates
     * @param pw printWriter
     */
    public void dump(PrintWriter pw) {
        pw.println("PowerManagerFlags:");
        pw.println(" " + mEarlyScreenTimeoutDetectorFlagState);
        pw.println(" " + mImproveWakelockLatency);
        pw.println(" " + mPerDisplayWakeByTouch);
        pw.println(" " + mMoveWscLoggingToNotifier);
        pw.println(" " + mLockOnUnplug);
        pw.println(" " + mWakelockAttributionViaWorkchain);
        pw.println(" " + mDisableFrozenProcessWakelocks);
        pw.println(" " + mForceDisableWakelocks);
        pw.println(" " + mEnableAppWakelockDataSource);
        pw.println(" " + mPartialSleepWakelocks);
        pw.println(" " + mSeparateTimeoutsFlicker);
        pw.println(" " + mWakeAdjacentDisplaysOnWakeupCall);
    }

    private static class FlagState {

        private final String mName;

        private final Supplier<Boolean> mFlagFunction;
        private boolean mEnabledSet;
        private boolean mEnabled;

        private FlagState(String name, Supplier<Boolean> flagFunction) {
            mName = name;
            mFlagFunction = flagFunction;
        }

        private boolean isEnabled() {
            if (mEnabledSet) {
                if (DEBUG) {
                    Slog.d(TAG, mName + ": mEnabled. Recall = " + mEnabled);
                }
                return mEnabled;
            }
            mEnabled = flagOrSystemProperty(mFlagFunction, mName);
            if (DEBUG) {
                Slog.d(TAG, mName + ": mEnabled. Flag value = " + mEnabled);
            }
            mEnabledSet = true;
            return mEnabled;
        }

        private boolean flagOrSystemProperty(Supplier<Boolean> flagFunction, String flagName) {
            boolean flagValue = flagFunction.get();
            if (Build.IS_ENG || Build.IS_USERDEBUG) {
                return SystemProperties.getBoolean("persist.sys." + flagName + "-override",
                        flagValue);
            }
            return flagValue;
        }

        @Override
        public String toString() {
            // remove com.android.server.power.feature.flags. from the beginning of the name.
            // align all isEnabled() values.
            // Adjust lengths if we end up with longer names
            final int nameLength = mName.length();
            return TextUtils.substring(mName,  39, nameLength) + ": "
                    + TextUtils.formatSimple("%" + (91 - nameLength) + "s%s", " " , isEnabled())
                    + " (def:" + mFlagFunction.get() + ")";
        }
    }
}
