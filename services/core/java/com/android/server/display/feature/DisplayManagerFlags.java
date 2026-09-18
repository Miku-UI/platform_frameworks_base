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

package com.android.server.display.feature;

import android.content.Context;
import android.os.Build;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Slog;
import android.window.DesktopExperienceFlags;

import com.android.server.display.feature.flags.Flags;
import com.android.server.display.utils.DebugUtils;

import java.io.PrintWriter;
import java.util.function.Supplier;

/**
 * Utility class to read the flags used in the display manager server.
 * @deprecated use {@link Flags} directly, see b/440342129
 */
@Deprecated
public class DisplayManagerFlags {
    private static final String TAG = "DisplayManagerFlags";

    // To enable these logs, run:
    // 'adb shell setprop persist.log.tag.DisplayManagerFlags DEBUG && adb reboot'
    private static final boolean DEBUG = DebugUtils.isDebuggable(TAG);

    private final FlagState mDisplayTopologyApi = new FlagState(
            Flags.FLAG_DISPLAY_TOPOLOGY_API,
            Flags::displayTopologyApi);

    private final FlagState mPowerThrottlingClamperFlagState = new FlagState(
            Flags.FLAG_ENABLE_POWER_THROTTLING_CLAMPER,
            Flags::enablePowerThrottlingClamper);

    private final FlagState mSyncedResolutionSwitch = new FlagState(
            com.android.graphics.surfaceflinger.flags.Flags.FLAG_SYNCED_RESOLUTION_SWITCH,
            com.android.graphics.surfaceflinger.flags.Flags::syncedResolutionSwitch
    );

    private final FlagState mFastHdrTransitions = new FlagState(
            Flags.FLAG_FAST_HDR_TRANSITIONS,
            Flags::fastHdrTransitions);

    private final FlagState mSensorBasedBrightnessThrottling = new FlagState(
            Flags.FLAG_SENSOR_BASED_BRIGHTNESS_THROTTLING,
            Flags::sensorBasedBrightnessThrottling
    );

    private final FlagState mUseFusionProxSensor = new FlagState(
            Flags.FLAG_USE_FUSION_PROX_SENSOR,
            Flags::useFusionProxSensor
    );
    private final FlagState mNormalBrightnessForDozeParameter = new FlagState(
            Flags.FLAG_NORMAL_BRIGHTNESS_FOR_DOZE_PARAMETER,
            Flags::normalBrightnessForDozeParameter
    );
    private final FlagState mBlockAutobrightnessChangesOnStylusUsage = new FlagState(
            Flags.FLAG_BLOCK_AUTOBRIGHTNESS_CHANGES_ON_STYLUS_USAGE,
            Flags::blockAutobrightnessChangesOnStylusUsage
    );

    private final FlagState mHasArrSupport = new FlagState(
            Flags.FLAG_ENABLE_HAS_ARR_SUPPORT,
            Flags::enableHasArrSupport
    );

    private final FlagState mGetSupportedRefreshRatesFlagState = new FlagState(
            Flags.FLAG_ENABLE_GET_SUPPORTED_REFRESH_RATES,
            Flags::enableGetSupportedRefreshRates
    );

    private final FlagState mEnableHdrOverridePluginTypeFlagState = new FlagState(
            Flags.FLAG_ENABLE_HDR_OVERRIDE_PLUGIN_TYPE,
            Flags::enableHdrOverridePluginType
    );

    private final FlagState mDisplayListenerPerformanceImprovementsFlagState = new FlagState(
            Flags.FLAG_DISPLAY_LISTENER_PERFORMANCE_IMPROVEMENTS,
            Flags::displayListenerPerformanceImprovements
    );
    private final FlagState mEnableDisplayContentModeManagementFlagState = new FlagState(
            Flags.FLAG_ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT,
            DesktopExperienceFlags.ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT::isTrue
    );

    private final FlagState mSubscribeGranularDisplayEvents = new FlagState(
            Flags.FLAG_SUBSCRIBE_GRANULAR_DISPLAY_EVENTS,
            Flags::subscribeGranularDisplayEvents
    );

    private final FlagState mFramerateOverrideTriggersRrCallbacks = new FlagState(
            Flags.FLAG_FRAMERATE_OVERRIDE_TRIGGERS_RR_CALLBACKS,
            Flags::framerateOverrideTriggersRrCallbacks
    );

    private final FlagState mRefreshRateEventForForegroundApps = new FlagState(
            Flags.FLAG_REFRESH_RATE_EVENT_FOR_FOREGROUND_APPS,
            Flags::refreshRateEventForForegroundApps
    );

    private final FlagState mCommittedStateSeparateEvent = new FlagState(
            Flags.FLAG_COMMITTED_STATE_SEPARATE_EVENT,
            Flags::committedStateSeparateEvent
    );

    private final FlagState mSeparateTimeouts = new FlagState(
            Flags.FLAG_SEPARATE_TIMEOUTS,
            Flags::separateTimeouts
    );

    private final FlagState mDelayImplicitRrRegistrationUntilRrAccessed = new FlagState(
            Flags.FLAG_DELAY_IMPLICIT_RR_REGISTRATION_UNTIL_RR_ACCESSED,
            Flags::delayImplicitRrRegistrationUntilRrAccessed
    );
    private final FlagState mEnsureColorFadeWhenTurningOn = new FlagState(
            Flags.FLAG_ENSURE_COLOR_FADE_WHEN_TURNING_ON,
            Flags::ensureColorFadeWhenTurningOn
    );

    private final FlagState mIsLoggingForDisplayEventsEnabled = new FlagState(
            Flags.FLAG_ENABLE_LOGGING_FOR_DISPLAY_EVENTS,
            Flags::enableLoggingForDisplayEvents
    );

    private final FlagState mIsMinmodeCapBrightnessEnabled = new FlagState(
            Flags.FLAG_MINMODE_CAP_BRIGHTNESS_ENABLED,
            Flags::minmodeCapBrightnessEnabled
    );

    /** Returns whether power throttling clamper is enabled on not. */
    public boolean isPowerThrottlingClamperEnabled() {
        return mPowerThrottlingClamperFlagState.isEnabled();
    }

    public boolean isSyncedResolutionSwitchEnabled() {
        return mSyncedResolutionSwitch.isEnabled();
    }

    public boolean isFastHdrTransitionsEnabled() {
        return mFastHdrTransitions.isEnabled();
    }

    public boolean isSensorBasedBrightnessThrottlingEnabled() {
        return mSensorBasedBrightnessThrottling.isEnabled();
    }

    public boolean isUseFusionProxSensorEnabled() {
        return mUseFusionProxSensor.isEnabled();
    }

    public String getUseFusionProxSensorFlagName() {
        return mUseFusionProxSensor.getName();
    }

    /**
     * @return Whether the useDozeBrightness parameter should be used
     */
    public boolean isNormalBrightnessForDozeParameterEnabled(Context context) {
        return mNormalBrightnessForDozeParameter.isEnabled() && context.getResources().getBoolean(
                com.android.internal.R.bool.config_allowNormalBrightnessForDozePolicy);
    }

    /**
     * @return {@code true} if autobrightness is to be blocked when stylus is being used
     */
    public boolean isBlockAutobrightnessChangesOnStylusUsage() {
        return mBlockAutobrightnessChangesOnStylusUsage.isEnabled();
    }

    /**
     * @return {@code true} if hasArrSupport API is enabled.
     */
    public boolean hasArrSupportFlag() {
        return mHasArrSupport.isEnabled();
    }

    /**
     * @return {@code true} if supported refresh rate api is enabled.
     */
    public boolean enableGetSupportedRefreshRates() {
        return mGetSupportedRefreshRatesFlagState.isEnabled();
    }

    public boolean isHdrOverrideEnabled() {
        return mEnableHdrOverridePluginTypeFlagState.isEnabled();
    }

    /**
     * @return {@code true} if the flag for display listener performance improvements is enabled
     */
    public boolean isDisplayListenerPerformanceImprovementsEnabled() {
        return mDisplayListenerPerformanceImprovementsFlagState.isEnabled();
    }

    public boolean isDisplayContentModeManagementEnabled() {
        return mEnableDisplayContentModeManagementFlagState.isEnabled();
    }

    /**
     * @return {@code true} if the flag for subscribing to granular display events is enabled
     */
    public boolean isSubscribeGranularDisplayEventsEnabled() {
        return mSubscribeGranularDisplayEvents.isEnabled();
    }

    /**
     * @return {@code true} if the flag triggering refresh rate callbacks when framerate is
     * overridden is enabled
     */
    public boolean isFramerateOverrideTriggersRrCallbacksEnabled() {
        return mFramerateOverrideTriggersRrCallbacks.isEnabled();
    }

    /**
     * @return {@code true} if the flag for sending refresh rate events only for the apps in
     * foreground is enabled
     */
    public boolean isRefreshRateEventForForegroundAppsEnabled() {
        return mRefreshRateEventForForegroundApps.isEnabled();
    }

    /**
     * @return {@code true} if the flag for having a separate event for display's committed state
     * is enabled
     */
    public boolean isCommittedStateSeparateEventEnabled() {
        return mCommittedStateSeparateEvent.isEnabled();
    }

    /**
     * @return {@code true} if the flag for having a separate timeouts for power groups
     * is enabled
     */
    public boolean isSeparateTimeoutsEnabled() {
        return mSeparateTimeouts.isEnabled();
    }

    /**
     * @return {@code true} if the flag for only explicit subscription for RR changes is enabled
     */
    public boolean isDelayImplicitRrRegistrationUntilRrAccessedEnabled() {
        return mDelayImplicitRrRegistrationUntilRrAccessed.isEnabled();
    }

    /**
     * @return {@code true} if the flag for ensure color fad when turning screen on is enabled
     */
    public boolean isEnsureColorFadeWhenTurningOnEnabled() {
        return mEnsureColorFadeWhenTurningOn.isEnabled();
    }

    public boolean isDisplayEventsLoggingEnabled() {
        return mIsLoggingForDisplayEventsEnabled.isEnabled();
    }

    public boolean isMinmodeCapBrightnessEnabled() {
        return mIsMinmodeCapBrightnessEnabled.isEnabled();
    }

    /**
     * dumps all flagstates
     * @param pw printWriter
     */
    public void dump(PrintWriter pw) {
        pw.println("DisplayManagerFlags:");
        pw.println("--------------------");
        pw.println(" " + mDisplayTopologyApi);
        pw.println(" " + mPowerThrottlingClamperFlagState);
        pw.println(" " + mSyncedResolutionSwitch);
        pw.println(" " + mFastHdrTransitions);
        pw.println(" " + mSensorBasedBrightnessThrottling);
        pw.println(" " + mUseFusionProxSensor);
        pw.println(" " + mNormalBrightnessForDozeParameter);
        pw.println(" " + mBlockAutobrightnessChangesOnStylusUsage);
        pw.println(" " + mHasArrSupport);
        pw.println(" " + mGetSupportedRefreshRatesFlagState);
        pw.println(" " + mDisplayListenerPerformanceImprovementsFlagState);
        pw.println(" " + mSubscribeGranularDisplayEvents);
        pw.println(" " + mEnableDisplayContentModeManagementFlagState);
        pw.println(" " + mFramerateOverrideTriggersRrCallbacks);
        pw.println(" " + mRefreshRateEventForForegroundApps);
        pw.println(" " + mCommittedStateSeparateEvent);
        pw.println(" " + mSeparateTimeouts);
        pw.println(" " + mDelayImplicitRrRegistrationUntilRrAccessed);
        pw.println(" " + mEnsureColorFadeWhenTurningOn);
        pw.println(" " + mIsLoggingForDisplayEventsEnabled);
        pw.println(" " + mIsMinmodeCapBrightnessEnabled);
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

        private String getName() {
            return mName;
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
            // TODO(b/299462337) Remove when the infrastructure is ready.
            if (Build.IS_ENG || Build.IS_USERDEBUG) {
                return SystemProperties.getBoolean("persist.sys." + flagName + "-override",
                        flagValue);
            }
            return flagValue;
        }

        @Override
        public String toString() {
            // remove the flag package from the beginning of the name.
            String shortName = TextUtils.substring(mName, mName.lastIndexOf('.') + 1,
                    mName.length());

            // align all isEnabled() values.
            return String.format("%-53s %b (def:%b)",
                    shortName + ":",
                    isEnabled(),
                    mFlagFunction.get());
        }
    }
}
