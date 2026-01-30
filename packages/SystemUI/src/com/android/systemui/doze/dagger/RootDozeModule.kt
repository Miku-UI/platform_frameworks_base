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

package com.android.systemui.doze.dagger

import android.content.Context
import android.hardware.Sensor
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.doze.DozeSensors
import com.android.systemui.res.R
import com.android.systemui.statusbar.phone.DozeParameters
import com.android.systemui.statusbar.policy.DevicePostureController
import com.android.systemui.util.sensors.AsyncSensorManager
import dagger.Module
import dagger.Provides
import java.util.HashMap
import java.util.Optional

/**
 * A module for allowing components outside the doze package to depend on doze-related dependencies
 * such as the doze brightness sensors.
 */
@Module
abstract class RootDozeModule {
    companion object {
        @SysUISingleton
        @Provides
        @BrightnessSensor
        fun providesBrightnessSensors(
            sensorManager: AsyncSensorManager?,
            context: Context,
            dozeParameters: DozeParameters,
        ): Array<Optional<Sensor>> {
            val sensorNames: Array<String> = dozeParameters.brightnessNames() ?: emptyArray()
            if (sensorNames.isEmpty()) {
                // if no brightness names are specified, just use the brightness sensor type
                return arrayOf(
                    Optional.ofNullable<Sensor>(
                        DozeSensors.findSensor(
                            sensorManager,
                            context.getString(R.string.doze_brightness_sensor_type),
                            null,
                        )
                    )
                )
            }

            // length and index of brightnessMap correspond to
            // DevicePostureController.DevicePostureInt:
            val brightnessSensorMap: Array<Optional<Sensor>> =
                Array<Optional<Sensor>>(DevicePostureController.SUPPORTED_POSTURES_SIZE) {
                    Optional.empty<Sensor>()
                }

            // Map of sensorName => Sensor, so we reuse the same sensor if it's the same between
            // postures
            val nameToSensorMap: MutableMap<String, Optional<Sensor>> =
                HashMap<String, Optional<Sensor>>()
            for (i in sensorNames.indices) {
                val sensorName = sensorNames[i]
                if (!nameToSensorMap.containsKey(sensorName)) {
                    nameToSensorMap[sensorName] =
                        Optional.ofNullable<android.hardware.Sensor>(
                            DozeSensors.findSensor(
                                sensorManager,
                                context.getString(R.string.doze_brightness_sensor_type),
                                sensorNames[i],
                            )
                        )
                }
                nameToSensorMap[sensorName]?.let { brightnessSensorMap[i] = it }
            }
            return brightnessSensorMap
        }
    }
}
