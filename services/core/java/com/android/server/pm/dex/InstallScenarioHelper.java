/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.pm.dex;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.util.Slog;

import com.android.server.art.ReasonMapping;

/**
 * Helper class for install scenarios.
 */
public class InstallScenarioHelper {
    private static final String TAG = "InstallScenarioHelper";

    private final Context mContext;

    private BatteryManager mBatteryManager = null;
    private PowerManager mPowerManager = null;

    // An integer percentage value used to determine when the device is considered to be on low
    // power for compilation purposes.
    private final int mCriticalBatteryLevel;

    public InstallScenarioHelper(Context context) {
        mContext = context;

        mPowerManager = mContext.getSystemService(PowerManager.class);

        if (mPowerManager == null) {
            Slog.wtf(TAG, "Power Manager is unavailable at time of Dex Manager start");
        }

        mCriticalBatteryLevel = mContext.getResources().getInteger(
            com.android.internal.R.integer.config_criticalBatteryWarningLevel);
    }

    /**
     * Translates install scenarios into compilation reasons. This process can be influenced by the
     * state of the device.
     */
    public String getCompilationReasonForInstallScenario(int installScenario) {
        boolean resourcesAreCritical = areBatteryThermalOrMemoryCritical();
        switch (installScenario) {
            case PackageManager.INSTALL_SCENARIO_DEFAULT: {
                return ReasonMapping.REASON_INSTALL;
            }
            case PackageManager.INSTALL_SCENARIO_FAST: {
                return ReasonMapping.REASON_INSTALL_FAST;
            }
            case PackageManager.INSTALL_SCENARIO_BULK: {
                if (resourcesAreCritical) {
                    return ReasonMapping.REASON_INSTALL_BULK_DOWNGRADED;
                } else {
                    return ReasonMapping.REASON_INSTALL_BULK;
                }
            }
            case PackageManager.INSTALL_SCENARIO_BULK_SECONDARY: {
                if (resourcesAreCritical) {
                    return ReasonMapping.REASON_INSTALL_BULK_SECONDARY_DOWNGRADED;
                } else {
                    return ReasonMapping.REASON_INSTALL_BULK_SECONDARY;
                }
            }
            default: {
                throw new IllegalArgumentException("Invalid installation scenario");
            }
        }
    }

    /**
     * Fetches the battery manager object and caches it if it hasn't been fetched already.
     */
    private BatteryManager getBatteryManager() {
        if (mBatteryManager == null) {
            mBatteryManager = mContext.getSystemService(BatteryManager.class);
        }

        return mBatteryManager;
    }

    /**
     * Returns true if the battery level, device temperature, or memory usage are considered to be
     * in a critical state.
     */
    private boolean areBatteryThermalOrMemoryCritical() {
        BatteryManager batteryManager = getBatteryManager();
        boolean isBtmCritical = (batteryManager != null
                && batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
                    == BatteryManager.BATTERY_STATUS_DISCHARGING
                && batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    <= mCriticalBatteryLevel)
                || (mPowerManager != null
                    && mPowerManager.getCurrentThermalStatus()
                        >= PowerManager.THERMAL_STATUS_SEVERE);

        return isBtmCritical;
    }
}
