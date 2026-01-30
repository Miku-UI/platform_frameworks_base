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

package com.android.server.display.brightness.clamper

import android.content.res.Resources
import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import androidx.test.filters.SmallTest
import com.android.server.display.brightness.clamper.LightSensorController.Injector
import com.android.server.display.brightness.clamper.LightSensorController.LightSensorListener
import com.android.server.display.config.SensorData
import com.android.server.display.config.createSensorData
import com.android.server.display.createSensor
import com.android.server.display.createSensorEvent
import com.android.server.display.utils.AmbientFilter
import com.android.server.testutils.TestHandler
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

private const val LIGHT_SENSOR_RATE: Int = 10
private const val DISPLAY_ID: Int = 3
private const val NOW: Long = 3_000

@SmallTest
class LightSensorControllerTest {

    private val mockSensorManager: SensorManager = mock()
    private val mockResources: Resources = mock()
    private val mockLightSensorListener: LightSensorListener = mock()
    private val mockHandler: Handler = mock()
    private val mockAmbientFilter: AmbientFilter = mock()

    private val testHandler = TestHandler(null)
    private val testInjector = TestInjector()
    private val dummySensorData = createSensorData()

    private lateinit var controller: LightSensorController

    @Before
    fun setUp() {
        controller = LightSensorController(mockSensorManager, mockResources,
            mockLightSensorListener, mockHandler, testHandler, testInjector)
    }

    @Test
    fun doesNotRegisterLightSensorIfNotConfigured() {
        controller.restart()
        testHandler.flush()

        verifyNoMoreInteractions(mockSensorManager, mockAmbientFilter, mockLightSensorListener)
    }

    @Test
    fun doesNotRegisterLightSensorIfMissing() {
        controller.configure(dummySensorData, DISPLAY_ID)
        controller.restart()
        testHandler.flush()

        verifyNoMoreInteractions(mockSensorManager, mockAmbientFilter, mockLightSensorListener)
    }

    @Test
    fun registerLightSensorIfConfiguredAndPresent() {
        testInjector.lightSensor = createSensor(Sensor.TYPE_LIGHT, Sensor.STRING_TYPE_LIGHT)
        controller.configure(dummySensorData, DISPLAY_ID)
        controller.restart()
        testHandler.flush()

        verify(mockSensorManager).registerListener(any(),
            eq(testInjector.lightSensor), eq(LIGHT_SENSOR_RATE * 1000), eq(mockHandler))
        verifyNoMoreInteractions(mockSensorManager, mockAmbientFilter, mockLightSensorListener)
    }

    @Test
    fun registerLightSensorOnceIfNotChanged() {
        testInjector.lightSensor = createSensor(Sensor.TYPE_LIGHT, Sensor.STRING_TYPE_LIGHT)
        controller.configure(dummySensorData, DISPLAY_ID)

        controller.restart()
        testHandler.flush()
        controller.restart()
        testHandler.flush()

        verify(mockSensorManager).registerListener(any(),
            eq(testInjector.lightSensor), eq(LIGHT_SENSOR_RATE * 1000), eq(mockHandler))
        verifyNoMoreInteractions(mockSensorManager, mockAmbientFilter, mockLightSensorListener)
    }

    @Test
    fun registerNewAndUnregisterOldLightSensorIfChanged() {
        val lightSensor1 = createSensor(Sensor.TYPE_LIGHT, Sensor.STRING_TYPE_LIGHT)
        testInjector.lightSensor = lightSensor1
        controller.configure(dummySensorData, DISPLAY_ID)
        controller.restart()
        testHandler.flush()

        val lightSensor2 = createSensor(Sensor.TYPE_LIGHT, Sensor.STRING_TYPE_LIGHT)
        testInjector.lightSensor = lightSensor2
        controller.configure(dummySensorData, DISPLAY_ID)
        controller.restart()
        testHandler.flush()

        inOrder(mockSensorManager, mockAmbientFilter, mockLightSensorListener) {
            // first restart
            verify(mockSensorManager).registerListener(any(),
                eq(lightSensor1), eq(LIGHT_SENSOR_RATE * 1000), eq(mockHandler))
            // second restart
            verify(mockAmbientFilter).clear()
            verify(mockLightSensorListener).onAmbientLuxChange(LightSensorController.INVALID_LUX)
            // delayed resubscribe
            verify(mockSensorManager).registerListener(any(),
                eq(lightSensor2), eq(LIGHT_SENSOR_RATE * 1000), eq(mockHandler))
            verify(mockSensorManager).unregisterListener(any<SensorEventListener>(),
                eq(lightSensor1))
            }
        verifyNoMoreInteractions(mockSensorManager, mockAmbientFilter, mockLightSensorListener)
    }

    @Test
    fun notifiesListenerOnAmbientLuxChange() {
        val expectedLux = 40f
        val eventLux = 50f
        val eventTime = 60L
        whenever(mockAmbientFilter.getEstimate(NOW)).thenReturn(expectedLux)
        val listenerCaptor = argumentCaptor<SensorEventListener>()
        testInjector.lightSensor = createSensor(Sensor.TYPE_LIGHT, Sensor.STRING_TYPE_LIGHT)
        controller.configure(dummySensorData, DISPLAY_ID)
        controller.restart()
        testHandler.flush()
        verify(mockSensorManager).registerListener(listenerCaptor.capture(),
            eq(testInjector.lightSensor), eq(LIGHT_SENSOR_RATE * 1000), eq(mockHandler))

        val listener = listenerCaptor.lastValue
        listener.onSensorChanged(createSensorEvent(testInjector.lightSensor!!,
            eventLux, eventTime * 1_000_000))

        inOrder(mockAmbientFilter, mockLightSensorListener) {
            verify(mockAmbientFilter).addValue(eventTime, eventLux)
            verify(mockLightSensorListener).onAmbientLuxChange(expectedLux)
        }
    }

    private inner class TestInjector : Injector() {
        var lightSensor: Sensor? = null
        override fun getLightSensor(sensorManager: SensorManager?,
            sensorData: SensorData?, fallbackType: Int): Sensor? {
            return lightSensor
        }

        override fun getLightSensorRate(resources: Resources?): Int {
            return LIGHT_SENSOR_RATE
        }

        override fun getAmbientFilter(resources: Resources?): AmbientFilter {
            return mockAmbientFilter
        }

        override fun getTime(): Long {
            return NOW
        }
    }
}