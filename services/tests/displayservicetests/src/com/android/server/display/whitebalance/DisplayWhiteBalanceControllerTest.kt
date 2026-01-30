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

package com.android.server.display.whitebalance

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.util.test.LocalServiceKeeperRule
import com.android.server.display.color.ColorDisplayService.ColorDisplayServiceInternal
import com.android.server.display.utils.AmbientFilter
import com.android.server.testutils.TestHandler
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

@SmallTest
@RunWith(AndroidJUnit4::class)
class DisplayWhiteBalanceControllerTest {

    @get:Rule
    val localServiceKeeperRule = LocalServiceKeeperRule()

    private val mockBrightnessSensor = mock<AmbientSensor.AmbientBrightnessSensor>()
    private val mockBrightnessFilter = mock<AmbientFilter>()
    private val mockColorTemperatureSensor = mock<AmbientSensor.AmbientColorTemperatureSensor>()
    private val mockColorTemperatureFilter = mock<AmbientFilter>()
    private val mockThrottler = mock<DisplayWhiteBalanceThrottler>()
    private val testHandler = TestHandler(null)

    @Before
    fun setUp() {
        localServiceKeeperRule.overrideLocalService(
            ColorDisplayServiceInternal::class.java,
            mock<ColorDisplayServiceInternal>()
        )
    }

    @Test
    fun testSensorsEnabledInSuppliedHandler() {
        val controller = createDisplayWhiteBalanceController()

        controller.setEnabled(true)
        verifyNoInteractions(mockBrightnessSensor, mockColorTemperatureSensor)

        testHandler.flush()
        verify(mockBrightnessSensor).setEnabled(true)
        verify(mockColorTemperatureSensor).setEnabled(true)
    }

    @Test
    fun testSensorsDisabledInSuppliedHandler() {
        val controller = createDisplayWhiteBalanceController()
        controller.setEnabled(true)
        testHandler.flush()
        clearInvocations(mockBrightnessSensor, mockColorTemperatureSensor)

        controller.setEnabled(false)
        verifyNoInteractions(mockBrightnessSensor, mockColorTemperatureSensor)

        testHandler.flush()
        verify(mockBrightnessSensor).setEnabled(false)
        verify(mockColorTemperatureSensor).setEnabled(false)
    }

    @Test
    fun testAmbientBrightnessChange() {
        val brightness = 0.4f
        val controller = createDisplayWhiteBalanceController()
        controller.setEnabled(true)

        controller.onAmbientBrightnessChanged(brightness)

        verify(mockBrightnessFilter).addValue(any<Long>(), eq(brightness))
    }

    @Test
    fun testAmbientBrightnessChange_disabled() {
        val brightness = 0.4f
        val controller = createDisplayWhiteBalanceController()
        controller.setEnabled(true)

        controller.setEnabled(false)
        clearInvocations(mockBrightnessFilter)

        controller.onAmbientBrightnessChanged(brightness)

        verifyNoInteractions(mockBrightnessFilter)
    }

    @Test
    fun testColorTemperatureChanged() {
        val colorTemperature = 0.45f
        val controller = createDisplayWhiteBalanceController()
        controller.setEnabled(true)

        controller.onAmbientColorTemperatureChanged(colorTemperature)

        verify(mockColorTemperatureFilter).addValue(any<Long>(), eq(colorTemperature))
    }

    @Test
    fun testColorTemperatureChanged_disabled() {
        val colorTemperature = 0.45f
        val controller = createDisplayWhiteBalanceController()
        controller.setEnabled(true)

        controller.setEnabled(false)
        clearInvocations(mockColorTemperatureFilter)

        controller.onAmbientColorTemperatureChanged(colorTemperature)

        verifyNoInteractions(mockColorTemperatureFilter)
    }

    fun createDisplayWhiteBalanceController(): DisplayWhiteBalanceController {
        return DisplayWhiteBalanceController(
            mockBrightnessSensor, mockBrightnessFilter,
            mockColorTemperatureSensor, mockColorTemperatureFilter,
            mockThrottler, testHandler,
            floatArrayOf(), floatArrayOf(), floatArrayOf(), floatArrayOf(), 0.1f, 0.1f,
            floatArrayOf(), floatArrayOf(), floatArrayOf(), floatArrayOf(), 0.1f, 0.1f,
            floatArrayOf(), floatArrayOf(), floatArrayOf(), floatArrayOf(),
            true
        )
    }
}