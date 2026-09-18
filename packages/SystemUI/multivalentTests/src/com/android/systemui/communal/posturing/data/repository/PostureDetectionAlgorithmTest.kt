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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.communal.posturing.data.model.PositionState
import com.android.systemui.communal.posturing.shared.model.ConfidenceLevel
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.log.logcatLogBuffer
import com.android.systemui.testKosmos
import com.android.systemui.util.time.FakeSystemClock
import com.android.systemui.util.time.fakeSystemClock
import com.google.common.truth.Truth.assertThat
import java.io.PrintWriter
import java.io.StringWriter
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when` as whenever
import org.mockito.kotlin.mock

@SmallTest
@RunWith(AndroidJUnit4::class)
class PostureDetectionAlgorithmTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val clock: FakeSystemClock
        get() = kosmos.fakeSystemClock

    // Device is considered flat if angle to vertical is < 30.
    private val flatAngleThreshold = 30

    // Device is considered upright if angle to vertical is > 60.
    private val uprightAngleThreshold = 60

    private val sensor = mock<Sensor> { on { type }.thenReturn(Sensor.TYPE_ACCELEROMETER) }

    private val Kosmos.logBuffer by
        Kosmos.Fixture { logcatLogBuffer("PostureDetectionAlgorithmTest") }

    private val Kosmos.logOutputWriter: StringWriter by Kosmos.Fixture { StringWriter() }

    private val Kosmos.underTest by
        Kosmos.Fixture {
            PostureDetectionAlgorithm(flatAngleThreshold, uprightAngleThreshold, logBuffer)
        }

    @Test
    fun onSensorChanged_wrongSensorType_returnsNull() =
        kosmos.runTest {
            whenever(sensor.type).thenReturn(Sensor.TYPE_GYROSCOPE)
            val event = createSensorEvent(floatArrayOf(0f, 0f, 9.8f))
            assertThat(underTest.onSensorChanged(event)).isNull()
        }

    @Test
    fun onSensorChanged_notEnoughSamples_returnsNull() =
        kosmos.runTest {
            var result: PositionState? = null
            for (i in 1 until MIN_SAMPLES) {
                val event = createSensorEvent(floatArrayOf(0f, 0f, 9.8f))
                result = underTest.onSensorChanged(event)
                clock.advanceTime(20)
            }
            assertThat(result).isNull()
        }

    @Test
    fun onSensorChanged_enoughSamples_returnsState() =
        kosmos.runTest {
            for (i in 1 until MIN_SAMPLES) {
                val event = createSensorEvent(floatArrayOf(0f, 0f, 9.8f))
                underTest.onSensorChanged(event)
                clock.advanceTime(20)
            }

            val event = createSensorEvent(floatArrayOf(0f, 0f, 9.8f))
            val result = underTest.onSensorChanged(event)
            assertThat(result).isNotNull()
        }

    @Test
    fun onSensorChanged_stationaryAndFaceUpAt0Degrees_isFlat() =
        kosmos.runTest {
            var result: PositionState? = null
            for (i in 0 until MIN_SAMPLES) {
                val event = createSensorEvent(floatArrayOf(0f, 0f, 9.8f))
                result = underTest.onSensorChanged(event)
                clock.advanceTime(20)
            }
            val log = dumpBuffer()

            assertThat(result).isNotNull()
            assertThat(result!!.stationary is ConfidenceLevel.Positive).isTrue()
            assertThat(result.stationary.confidence).isEqualTo(1.0f)
            assertThat(result.postured is ConfidenceLevel.Positive).isFalse()
            assertThat(result.postured.confidence).isEqualTo(1.0f)
            assertThat(log).contains("orientation=FACE_UP")
        }

    @Test
    fun onSensorChanged_stationaryAndFaceUpAt25Degrees_isFlat() =
        kosmos.runTest {
            var result: PositionState? = null
            val z = 9.8f * kotlin.math.cos(Math.toRadians(25.0)).toFloat()
            val y = 9.8f * kotlin.math.sin(Math.toRadians(25.0)).toFloat()

            for (i in 0 until MIN_SAMPLES) {
                val event = createSensorEvent(floatArrayOf(0f, y, z))
                result = underTest.onSensorChanged(event)
                clock.advanceTime(20)
            }
            val log = dumpBuffer()

            assertThat(result).isNotNull()
            assertThat(result!!.stationary is ConfidenceLevel.Positive).isTrue()
            assertThat(result.stationary.confidence).isEqualTo(1.0f)
            assertThat(result.postured is ConfidenceLevel.Positive).isFalse()
            assertThat(result.postured.confidence).isEqualTo(1.0f)
            assertThat(log).contains("orientation=FACE_UP")
        }

    @Test
    fun onSensorChanged_stationaryAndFaceDownAt0Degrees_isFlat() =
        kosmos.runTest {
            var result: PositionState? = null
            for (i in 0 until MIN_SAMPLES) {
                val event = createSensorEvent(floatArrayOf(0f, 0f, -9.8f))
                result = underTest.onSensorChanged(event)
                clock.advanceTime(20)
            }
            val log = dumpBuffer()

            assertThat(result).isNotNull()
            assertThat(result!!.stationary is ConfidenceLevel.Positive).isTrue()
            assertThat(result.stationary.confidence).isEqualTo(1.0f)
            assertThat(result.postured is ConfidenceLevel.Positive).isFalse()
            assertThat(result.postured.confidence).isEqualTo(1.0f)
            assertThat(log).contains("orientation=FACE_DOWN")
        }

    @Test
    fun onSensorChanged_stationaryAndTiltedAt35Degrees_isUpright() =
        kosmos.runTest {
            var result: PositionState? = null
            val z = 9.8f * kotlin.math.cos(Math.toRadians(35.0)).toFloat()
            val y = 9.8f * kotlin.math.sin(Math.toRadians(35.0)).toFloat()

            for (i in 0 until MIN_SAMPLES) {
                val event = createSensorEvent(floatArrayOf(0f, y, z))
                result = underTest.onSensorChanged(event)
                clock.advanceTime(20)
            }
            val log = dumpBuffer()

            assertThat(result).isNotNull()
            assertThat(result!!.stationary is ConfidenceLevel.Positive).isTrue()
            assertThat(result.stationary.confidence).isEqualTo(1.0f)
            assertThat(result.postured is ConfidenceLevel.Positive).isTrue()
            assertThat(result.postured.confidence).isEqualTo(1.0f)
            assertThat(log).contains("orientation=POSTURED_BOTTOM_EDGE")
        }

    @Test
    fun onSensorChanged_stationaryAndTiltedAt55Degrees_isUpright() =
        kosmos.runTest {
            var result: PositionState? = null
            val z = 9.8f * kotlin.math.cos(Math.toRadians(55.0)).toFloat()
            val y = 9.8f * kotlin.math.sin(Math.toRadians(55.0)).toFloat()

            for (i in 0 until MIN_SAMPLES) {
                val event = createSensorEvent(floatArrayOf(0f, y, z))
                result = underTest.onSensorChanged(event)
                clock.advanceTime(20)
            }
            val log = dumpBuffer()

            assertThat(result).isNotNull()
            assertThat(result!!.stationary is ConfidenceLevel.Positive).isTrue()
            assertThat(result.stationary.confidence).isEqualTo(1.0f)
            assertThat(result.postured is ConfidenceLevel.Positive).isTrue()
            assertThat(result.postured.confidence).isEqualTo(1.0f)
            assertThat(log).contains("orientation=POSTURED_BOTTOM_EDGE")
        }

    @Test
    fun onSensorChanged_posturedOnBottomEdgeAt90Degrees_isUpright() =
        kosmos.runTest {
            var result: PositionState? = null
            for (i in 0 until MIN_SAMPLES) {
                val event = createSensorEvent(floatArrayOf(0f, 9.8f, 0f))
                result = underTest.onSensorChanged(event)
                clock.advanceTime(20)
            }
            val log = dumpBuffer()

            assertThat(result).isNotNull()
            assertThat(result!!.stationary is ConfidenceLevel.Positive).isTrue()
            assertThat(result.stationary.confidence).isEqualTo(1.0f)
            assertThat(result.postured is ConfidenceLevel.Positive).isTrue()
            assertThat(result.postured.confidence).isEqualTo(1.0f)
            assertThat(log).contains("orientation=POSTURED_BOTTOM_EDGE")
        }

    @Test
    fun onSensorChanged_tiled80DegreesFaceDown_isNotUpright() =
        kosmos.runTest {
            var result: PositionState? = null
            val z = -9.8f * kotlin.math.cos(Math.toRadians(80.0)).toFloat()
            val y = 9.8f * kotlin.math.sin(Math.toRadians(80.0)).toFloat()

            for (i in 0 until MIN_SAMPLES) {
                val event = createSensorEvent(floatArrayOf(0f, y, z))
                result = underTest.onSensorChanged(event)
                clock.advanceTime(20)
            }
            val log = dumpBuffer()

            assertThat(result).isNotNull()
            assertThat(result!!.stationary is ConfidenceLevel.Positive).isTrue()
            assertThat(result.stationary.confidence).isEqualTo(1.0f)
            assertThat(result.postured is ConfidenceLevel.Positive).isFalse()
            assertThat(result.postured.confidence).isEqualTo(1.0f)
            assertThat(log).contains("orientation=NONE")
        }

    @Test
    fun onSensorChanged_posturedOnBottomEdgeAt65Degrees_isUpright() =
        kosmos.runTest {
            var result: PositionState? = null
            val z = 9.8f * kotlin.math.cos(Math.toRadians(65.0)).toFloat()
            val y = 9.8f * kotlin.math.sin(Math.toRadians(65.0)).toFloat()

            for (i in 0 until MIN_SAMPLES) {
                val event = createSensorEvent(floatArrayOf(0f, y, z))
                result = underTest.onSensorChanged(event)
                clock.advanceTime(20)
            }
            val log = dumpBuffer()

            assertThat(result).isNotNull()
            assertThat(result!!.stationary is ConfidenceLevel.Positive).isTrue()
            assertThat(result.stationary.confidence).isEqualTo(1.0f)
            assertThat(result.postured is ConfidenceLevel.Positive).isTrue()
            assertThat(result.postured.confidence).isEqualTo(1.0f)
            assertThat(log).contains("orientation=POSTURED_BOTTOM_EDGE")
        }

    @Test
    fun onSensorChanged_posturedOnTopEdgeAt75Degrees_isNotUpright() =
        kosmos.runTest {
            var result: PositionState? = null
            val z = 9.8f * kotlin.math.cos(Math.toRadians(75.0)).toFloat()
            val y = -9.8f * kotlin.math.sin(Math.toRadians(75.0)).toFloat()

            for (i in 0 until MIN_SAMPLES) {
                val event = createSensorEvent(floatArrayOf(0f, y, z))
                result = underTest.onSensorChanged(event)
                clock.advanceTime(20)
            }
            val log = dumpBuffer()

            assertThat(result).isNotNull()
            assertThat(result!!.stationary is ConfidenceLevel.Positive).isTrue()
            assertThat(result.stationary.confidence).isEqualTo(1.0f)
            assertThat(result.postured is ConfidenceLevel.Positive).isFalse()
            assertThat(result.postured.confidence).isEqualTo(1.0f)
            assertThat(log).contains("orientation=POSTURED_TOP_EDGE")
        }

    @Test
    fun onSensorChanged_stationaryAndPosturedOnLeftEdgeAt70Degrees_isUpright() =
        kosmos.runTest {
            var result: PositionState? = null
            val z = 9.8f * kotlin.math.cos(Math.toRadians(70.0)).toFloat()
            val x = 9.8f * kotlin.math.sin(Math.toRadians(70.0)).toFloat()

            for (i in 0 until MIN_SAMPLES) {
                val event = createSensorEvent(floatArrayOf(x, 0f, z))
                result = underTest.onSensorChanged(event)
                clock.advanceTime(20)
            }
            val log = dumpBuffer()

            assertThat(result).isNotNull()
            assertThat(result!!.stationary is ConfidenceLevel.Positive).isTrue()
            assertThat(result.stationary.confidence).isEqualTo(1.0f)
            assertThat(result.postured is ConfidenceLevel.Positive).isTrue()
            assertThat(result.postured.confidence).isEqualTo(1.0f)
            assertThat(log).contains("orientation=POSTURED_LEFT_EDGE")
        }

    @Test
    fun onSensorChanged_stationaryAndPosturedOnRightEdgeAt80Degrees_isUpright() =
        kosmos.runTest {
            var result: PositionState? = null
            val z = 9.8f * kotlin.math.cos(Math.toRadians(80.0)).toFloat()
            val x = -9.8f * kotlin.math.sin(Math.toRadians(80.0)).toFloat()

            for (i in 0 until MIN_SAMPLES) {
                val event = createSensorEvent(floatArrayOf(x, 0f, z))
                result = underTest.onSensorChanged(event)
                clock.advanceTime(20)
            }
            val log = dumpBuffer()

            assertThat(result).isNotNull()
            assertThat(result!!.stationary is ConfidenceLevel.Positive).isTrue()
            assertThat(result.stationary.confidence).isEqualTo(1.0f)
            assertThat(result.postured is ConfidenceLevel.Positive).isTrue()
            assertThat(result.postured.confidence).isEqualTo(1.0f)
            assertThat(log).contains("orientation=POSTURED_RIGHT_EDGE")
        }

    @Test
    fun onSensorChanged_deviceMoving_isNotStationary() =
        kosmos.runTest {
            var result: PositionState? = null
            for (i in 0 until MIN_SAMPLES) {
                val event = createSensorEvent(floatArrayOf(i.toFloat(), 9.8f, 0f))
                result = underTest.onSensorChanged(event)
                clock.advanceTime(20)
            }
            val log = dumpBuffer()

            assertThat(result).isNotNull()
            assertThat(result!!.stationary is ConfidenceLevel.Positive).isFalse()
            assertThat(result.stationary.confidence).isEqualTo(1.0f)
            assertThat(result.postured is ConfidenceLevel.Positive).isFalse()
            assertThat(result.postured.confidence).isEqualTo(1.0f)
            assertThat(log).contains("orientation=NONE")
        }

    private fun createSensorEvent(values: FloatArray): SensorEvent {
        val constructor =
            SensorEvent::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType)
        constructor.isAccessible = true
        val event = constructor.newInstance(values.size)
        event.sensor = sensor
        event.timestamp = clock.elapsedRealtimeNanos()
        System.arraycopy(values, 0, event.values, 0, values.size)
        return event
    }

    private fun Kosmos.dumpBuffer(): String {
        logBuffer.dump(PrintWriter(logOutputWriter), tailLength = 100)
        return logOutputWriter.toString()
    }

    private companion object {
        private const val MIN_SAMPLES = 10
    }
}
