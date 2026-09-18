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

package com.android.systemui.lowlight.data.repository

import android.hardware.Sensor
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.display.domain.interactor.DisplayStateInteractor
import com.android.systemui.doze.dagger.BrightnessSensor
import com.android.systemui.keyguard.domain.interactor.DevicePostureInteractor
import com.android.systemui.lowlight.AmbientLightModeMonitor
import com.android.systemui.lowlight.dagger.AmbientLightModeComponent
import com.android.systemui.lowlight.dagger.LowLightModule
import com.android.systemui.lowlight.shared.model.LightSensor
import java.util.Optional
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest

/**
 * An {@link AmbientLightModeMonitorRepository} implementation that tracks the screen state to
 * switch between the {@link AmbientLightModeMonitor} with the most appropriate {@link LightSensor}.
 * When the screen is off, determines the correct binned sensor to use.
 */
@SysUISingleton
class ScreenAwareAmbientLightModeMonitorRepository
@Inject
constructor(
    @BrightnessSensor lightSensorOptional: Array<Optional<Sensor>>,
    devicePostureInteractor: DevicePostureInteractor,
    @Named(LowLightModule.BINNED_SENSOR_ALGORITHM)
    algorithm: AmbientLightModeMonitor.DebounceAlgorithm,
    private val displayStateInteractor: DisplayStateInteractor,
    private val monitorFactory: AmbientLightModeComponent.Factory,
    @Named(LowLightModule.SCREEN_ON_LOW_LIGHT_MONITOR)
    private val screenOnMonitor: AmbientLightModeMonitor?,
) : AmbientLightModeMonitorRepository {
    private val binnedSensor: Flow<LightSensor?> =
        devicePostureInteractor.posture.mapLatest { posture ->
            val postureInt = posture.toDevicePostureInt()
            return@mapLatest if (postureInt >= lightSensorOptional.size) {
                null
            } else {
                val lightSensorOptional = lightSensorOptional[postureInt]

                LightSensor.from(
                    if (lightSensorOptional.isPresent()) {
                        lightSensorOptional.get()
                    } else {
                        null
                    },
                    algorithm,
                )
            }
        }

    override val currentMonitor: Flow<AmbientLightModeMonitor?>
        get() =
            displayStateInteractor.isDefaultDisplayOff.flatMapLatest { screenOff ->
                if (screenOff) {
                    binnedSensor.mapLatest { sensor ->
                        sensor?.let { monitorFactory.create(it).getAmbientLightModeMonitor() }
                    }
                } else {
                    flowOf(screenOnMonitor)
                }
            }
}
