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

package com.android.server.display.config;

import android.view.SurfaceControl.WorkDuration;

import com.android.server.display.feature.flags.Flags;

public class WorkDurationsConfigLoader {
    /**
     * Loads the default work durations from the given refresh rate configuration.
     *
     * @param refreshRateConfigs The {@link RefreshRateConfigs} to load the work durations from.
     * @return The loaded default {@link WorkDurationsData}, or null if not available or disabled
     */
    public static WorkDuration loadDefaultWorkDurations(
            RefreshRateConfigs refreshRateConfigs) {
        if (Flags.enableWorkDurations() && refreshRateConfigs != null) {
            WorkDurations defaultWorkDurations = refreshRateConfigs.getDefaultWorkDurations();
            return load(defaultWorkDurations);
        }
        return null;
    }

    /**
     * Loads the low-power work durations from the given refresh rate configuration.
     *
     * @param refreshRateConfigs The {@link RefreshRateConfigs} to load the work durations from.
     * @return The loaded low-power {@link WorkDurationsData}, or null if not available or disabled
     */
    public static WorkDuration loadLowPowerWorkDurations(
            RefreshRateConfigs refreshRateConfigs) {
        if (Flags.enableWorkDurations() && refreshRateConfigs != null) {
            WorkDurations lowPowerWorkDurations = refreshRateConfigs.getLowPowerWorkDurations();
            return load(lowPowerWorkDurations);
        }
        return null;
    }

    /**
     * Loads the thermal-throttling work durations from the given thermal throttling configuration.
     *
     * @param thermalThrottling The {@link WorkDurationsThrottlingPair} to load the work durations
     * from.
     * @return The loaded thermal-throttling {@link WorkDurations}, or null if not available or has
     * flag disabled
     */
    public static WorkDuration loadThermalThrottlingWorkDurations(
            WorkDurationsThrottlingPair thermalThrottling) {
        if (Flags.enableWorkDurations() && thermalThrottling != null) {
            WorkDurations thermalThrottlingWorkDurations =
                    thermalThrottling.getThermalThrottlingWorkDurations();
            return load(thermalThrottlingWorkDurations);
        }
        return null;
    }

    private static WorkDuration load(WorkDurations workDurations) {
        if (workDurations != null) {
            return new WorkDuration(
                    workDurations.getLateWorkDuration().longValue(),
                    workDurations.getEarlyWorkDuration().longValue(),
                    workDurations.getAppWorkDuration().longValue());
        }
        return null;
    }
}
