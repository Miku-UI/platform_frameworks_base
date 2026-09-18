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

package com.android.systemui.communal.posturing.data.repository

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.os.Handler
import com.android.app.tracing.FlowTracing.tracedAwaitClose
import com.android.app.tracing.FlowTracing.tracedConflatedCallbackFlow
import com.android.systemui.communal.posturing.data.model.PositionState
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.util.sensors.AsyncSensorManager
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

/**
 * Implementation of [PosturingRepository] that uses the accelerometer to determine if the device is
 * "postured" (stationary and upright).
 */
@SysUISingleton
class PosturingRepositoryImpl
@Inject
constructor(
    private val sensorManager: AsyncSensorManager,
    private val algorithm: PostureDetectionAlgorithm,
    @Background private val bgScope: CoroutineScope,
    @Background private val bgHandler: Handler,
) : PosturingRepository {

    /**
     * A flow that emits the current [PositionState] of the device, indicating whether it is
     * stationary and upright. The flow only emits new values when the state changes.
     */
    override val positionState: Flow<PositionState> =
        tracedConflatedCallbackFlow("positionState") {
                val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                if (accelerometer == null) {
                    trySend(PositionState())
                    close()
                    return@tracedConflatedCallbackFlow
                }

                val listener =
                    object : SensorEventListener {
                        override fun onSensorChanged(event: SensorEvent?) {
                            algorithm.onSensorChanged(event)?.let { trySend(it) }
                        }

                        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                    }

                sensorManager.registerListener(
                    listener,
                    accelerometer,
                    PostureDetectionAlgorithm.SENSOR_SAMPLING_PERIOD_US.inWholeMicroseconds.toInt(),
                    bgHandler,
                )

                tracedAwaitClose("positionState") {
                    sensorManager.unregisterListener(listener)
                    algorithm.reset()
                }
            }
            .stateIn(bgScope, SharingStarted.WhileSubscribed(), initialValue = PositionState())
}
