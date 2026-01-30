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

package com.android.systemui.lowlight.shared.model

import android.hardware.Sensor
import com.android.systemui.lowlight.AmbientLightModeMonitor

/**
 * {@link LightSensor} encapsulates a hardware sensor and a debounce algorithm. This tuple can be
 * used by a monitor to access the sensor based on its lifecycle and then apply the appropriate
 * debounce algorithm to interpret the results.
 */
data class LightSensor(
    val sensor: Sensor,
    val algorithm: AmbientLightModeMonitor.DebounceAlgorithm,
) {
    companion object {
        /**
         * Helper method for assembling a {@link LightSensor}. {@code null} should be returned in
         * the case the sensor is not present.
         */
        @JvmStatic
        fun from(
            sensor: Sensor?,
            algorithm: AmbientLightModeMonitor.DebounceAlgorithm,
        ): LightSensor? {
            return if (sensor == null) {
                null
            } else {
                LightSensor(sensor, algorithm)
            }
        }
    }
}
