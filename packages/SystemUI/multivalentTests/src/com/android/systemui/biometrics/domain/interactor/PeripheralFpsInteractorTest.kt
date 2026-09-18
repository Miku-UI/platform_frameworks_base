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

package com.android.systemui.biometrics.domain.interactor

import android.content.res.Configuration
import android.graphics.PointF
import android.graphics.Rect
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.view.Display
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags.FLAG_STANDALONE_FINGERPRINT_LOCK_SCREEN_UX_FIX
import com.android.systemui.SysuiTestCase
import com.android.systemui.biometrics.data.repository.fingerprintPropertyRepository
import com.android.systemui.biometrics.shared.model.FingerprintSensorType
import com.android.systemui.biometrics.shared.model.PeripheralFingerprintSensorLocation
import com.android.systemui.biometrics.shared.model.SensorStrength
import com.android.systemui.common.ui.data.repository.fakeConfigurationRepository
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.display.data.repository.setDisplayType
import com.android.systemui.kosmos.testScope
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class PeripheralFpsInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val fingerprintRepository = kosmos.fingerprintPropertyRepository
    private val configurationRepository = kosmos.fakeConfigurationRepository

    private val underTest = kosmos.peripheralFpsInteractor

    @Before
    fun setup() {
        setScreenSize(WIDTH, HEIGHT)
        configurationRepository.setScaleForResolution(1f)
        kosmos.setDisplayType(Display.DEFAULT_DISPLAY, Display.TYPE_INTERNAL)
    }

    @Test
    @DisableFlags(FLAG_STANDALONE_FINGERPRINT_LOCK_SCREEN_UX_FIX)
    fun isSupported_whenFlagDisabled_false() =
        testScope.runTest {
            val isSupported by collectLastValue(underTest.isSupported)

            fingerprintRepository.setProperties(
                sensorId = 0,
                strength = SensorStrength.STRONG,
                sensorType = FingerprintSensorType.STANDALONE,
                sensorLocations = emptyMap(),
            )

            assertThat(isSupported).isFalse()
        }

    @Test
    @EnableFlags(FLAG_STANDALONE_FINGERPRINT_LOCK_SCREEN_UX_FIX)
    fun isSupported_standaloneType_true() =
        testScope.runTest {
            val isSupported by collectLastValue(underTest.isSupported)

            fingerprintRepository.setProperties(
                sensorId = 0,
                strength = SensorStrength.STRONG,
                sensorType = FingerprintSensorType.STANDALONE,
                sensorLocations = emptyMap(),
            )

            assertThat(isSupported).isTrue()
        }

    @Test
    @EnableFlags(FLAG_STANDALONE_FINGERPRINT_LOCK_SCREEN_UX_FIX)
    fun isSupported_externalDisplay_unknownType_true() =
        testScope.runTest {
            val isSupported by collectLastValue(underTest.isSupported)

            fingerprintRepository.setProperties(
                sensorId = 0,
                strength = SensorStrength.STRONG,
                sensorType = FingerprintSensorType.UNKNOWN,
                sensorLocations = emptyMap(),
            )
            kosmos.setDisplayType(Display.DEFAULT_DISPLAY, Display.TYPE_EXTERNAL)

            assertThat(isSupported).isTrue()
        }

    @Test
    @EnableFlags(FLAG_STANDALONE_FINGERPRINT_LOCK_SCREEN_UX_FIX)
    fun isSupported_externalDisplay_sideFpsType_true() =
        testScope.runTest {
            val isSupported by collectLastValue(underTest.isSupported)

            fingerprintRepository.setProperties(
                sensorId = 0,
                strength = SensorStrength.STRONG,
                sensorType = FingerprintSensorType.POWER_BUTTON,
                sensorLocations = emptyMap(),
            )
            kosmos.setDisplayType(Display.DEFAULT_DISPLAY, Display.TYPE_EXTERNAL)

            assertThat(isSupported).isTrue()
        }

    @Test
    @EnableFlags(FLAG_STANDALONE_FINGERPRINT_LOCK_SCREEN_UX_FIX)
    fun isSupported_powerButtonWithPeripheralLocation_true() =
        testScope.runTest {
            val isSupported by collectLastValue(underTest.isSupported)

            fingerprintRepository.setProperties(
                sensorId = 0,
                strength = SensorStrength.STRONG,
                sensorType = FingerprintSensorType.POWER_BUTTON,
                sensorLocations = emptyMap(),
                peripheralSensorLocation =
                    PeripheralFingerprintSensorLocation.POWER_BUTTON_TOP_RIGHT_KEY,
            )

            assertThat(isSupported).isTrue()
        }

    @Test
    @EnableFlags(FLAG_STANDALONE_FINGERPRINT_LOCK_SCREEN_UX_FIX)
    fun isSupported_powerButtonWithUnknownPeripheralLocation_false() =
        testScope.runTest {
            val isSupported by collectLastValue(underTest.isSupported)

            fingerprintRepository.setProperties(
                sensorId = 0,
                strength = SensorStrength.STRONG,
                sensorType = FingerprintSensorType.POWER_BUTTON,
                sensorLocations = emptyMap(),
                peripheralSensorLocation = PeripheralFingerprintSensorLocation.UNKNOWN,
            )

            assertThat(isSupported).isFalse()
        }

    @Test
    @EnableFlags(FLAG_STANDALONE_FINGERPRINT_LOCK_SCREEN_UX_FIX)
    fun isSupported_otherTypes_false() =
        testScope.runTest {
            val isSupported by collectLastValue(underTest.isSupported)

            val otherTypes =
                listOf(
                    FingerprintSensorType.REAR,
                    FingerprintSensorType.UDFPS_OPTICAL,
                    FingerprintSensorType.UDFPS_ULTRASONIC,
                    FingerprintSensorType.HOME_BUTTON,
                    FingerprintSensorType.UNKNOWN,
                )

            for (type in otherTypes) {
                fingerprintRepository.setProperties(
                    sensorId = 0,
                    strength = SensorStrength.STRONG,
                    sensorType = type,
                    sensorLocations = emptyMap(),
                    peripheralSensorLocation =
                        PeripheralFingerprintSensorLocation.KEYBOARD_BOTTOM_LEFT,
                )
                assertThat(isSupported).isFalse()
            }
        }

    @Test
    @EnableFlags(FLAG_STANDALONE_FINGERPRINT_LOCK_SCREEN_UX_FIX)
    fun locationForRippleEffect_isScreenCenter() =
        testScope.runTest {
            val location by collectLastValue(underTest.locationForRippleEffect)

            fingerprintRepository.setProperties(
                sensorId = 0,
                strength = SensorStrength.STRONG,
                sensorType = FingerprintSensorType.STANDALONE,
                sensorLocations = emptyMap(),
                peripheralSensorLocation = PeripheralFingerprintSensorLocation.UNKNOWN,
            )

            for (scale in SCALES) {
                configurationRepository.setScaleForResolution(scale)
                assertPointF(location)
                    .isWithin(0.1f)
                    .of(PointF(WIDTH_CENTER * scale, HEIGHT_CENTER * scale))
            }
        }

    @Test
    @EnableFlags(FLAG_STANDALONE_FINGERPRINT_LOCK_SCREEN_UX_FIX)
    fun locationForRippleEffect_isNearPeripheralSensor_whenInternalDisplayAndDefaultRotation() =
        testScope.runTest {
            val location by collectLastValue(underTest.locationForRippleEffect)

            val testData =
                listOf(
                    PeripheralFingerprintSensorLocation.KEYBOARD_BOTTOM_RIGHT to 1360,
                    PeripheralFingerprintSensorLocation.KEYBOARD_TOP_RIGHT to 1360,
                    PeripheralFingerprintSensorLocation.RIGHT_SIDE to 1360,
                    PeripheralFingerprintSensorLocation.POWER_BUTTON_TOP_RIGHT_KEY to 1360,
                    PeripheralFingerprintSensorLocation.KEYBOARD_BOTTOM_LEFT to 240,
                    PeripheralFingerprintSensorLocation.LEFT_SIDE to 240,
                    PeripheralFingerprintSensorLocation.LEFT_OF_POWER_BUTTON_TOP_RIGHT to 240,
                )

            for ((sensorLocation, expectedX) in testData) {
                fingerprintRepository.setProperties(
                    sensorId = 0,
                    strength = SensorStrength.STRONG,
                    sensorType = FingerprintSensorType.STANDALONE,
                    sensorLocations = emptyMap(),
                    peripheralSensorLocation = sensorLocation,
                )

                for (scale in SCALES) {
                    configurationRepository.setScaleForResolution(scale)
                    assertPointF(location)
                        .isWithin(0.1f)
                        .of(PointF(expectedX * scale, HEIGHT * scale))
                }
            }
        }

    @Test
    @EnableFlags(FLAG_STANDALONE_FINGERPRINT_LOCK_SCREEN_UX_FIX)
    fun locationForRippleEffect_isScreenCenter_whenExternalDisplay() =
        testScope.runTest {
            val location by collectLastValue(underTest.locationForRippleEffect)

            kosmos.setDisplayType(Display.DEFAULT_DISPLAY, Display.TYPE_EXTERNAL)
            fingerprintRepository.setProperties(
                sensorId = 0,
                strength = SensorStrength.STRONG,
                sensorType = FingerprintSensorType.STANDALONE,
                sensorLocations = emptyMap(),
                peripheralSensorLocation = PeripheralFingerprintSensorLocation.RIGHT_SIDE,
            )

            for (scale in SCALES) {
                configurationRepository.setScaleForResolution(scale)
                assertPointF(location)
                    .isWithin(0.1f)
                    .of(PointF(WIDTH_CENTER * scale, HEIGHT_CENTER * scale))
            }
        }

    @Test
    @EnableFlags(FLAG_STANDALONE_FINGERPRINT_LOCK_SCREEN_UX_FIX)
    fun locationForRippleEffect_isScreenCenter_whenRotated() =
        testScope.runTest {
            val location by collectLastValue(underTest.locationForRippleEffect)

            setScreenSize(WIDTH, HEIGHT, Surface.ROTATION_90)
            fingerprintRepository.setProperties(
                sensorId = 0,
                strength = SensorStrength.STRONG,
                sensorType = FingerprintSensorType.STANDALONE,
                sensorLocations = emptyMap(),
                peripheralSensorLocation = PeripheralFingerprintSensorLocation.RIGHT_SIDE,
            )

            for (scale in SCALES) {
                configurationRepository.setScaleForResolution(scale)
                assertPointF(location)
                    .isWithin(0.1f)
                    .of(PointF(WIDTH_CENTER * scale, HEIGHT_CENTER * scale))
            }
        }

    @Test
    @DisableFlags(FLAG_STANDALONE_FINGERPRINT_LOCK_SCREEN_UX_FIX)
    fun locationForRippleEffect_whenFlagDisabled_isDefault() =
        testScope.runTest {
            val location by collectLastValue(underTest.locationForRippleEffect)

            fingerprintRepository.setProperties(
                sensorId = 0,
                strength = SensorStrength.STRONG,
                sensorType = FingerprintSensorType.STANDALONE,
                sensorLocations = emptyMap(),
                peripheralSensorLocation = PeripheralFingerprintSensorLocation.UNKNOWN,
            )

            assertPointF(location).isEqualTo(PointF(0f, 0f))
        }

    private fun assertPointF(point: PointF?): PointFSubject = PointFSubject(point)

    private class PointFSubject(private val actual: PointF?) {
        fun isWithin(tolerance: Float): TolerantPointFComparison =
            TolerantPointFComparison(actual, tolerance)

        fun isEqualTo(expected: PointF) {
            assertThat(actual).isEqualTo(expected)
        }
    }

    private class TolerantPointFComparison(
        private val actual: PointF?,
        private val tolerance: Float,
    ) {
        fun of(expected: PointF) {
            assertThat(actual).isNotNull()
            assertThat(actual!!.x).isWithin(tolerance).of(expected.x)
            assertThat(actual.y).isWithin(tolerance).of(expected.y)
        }
    }

    private fun setScreenSize(width: Int, height: Int, rotation: Int = Surface.ROTATION_0) {
        val config = Configuration()
        config.windowConfiguration.setMaxBounds(Rect(0, 0, width, height))
        config.windowConfiguration.displayRotation = rotation
        configurationRepository.onConfigurationChange(config)
    }

    companion object {
        private const val WIDTH = 1600
        private const val HEIGHT = 1000
        private const val WIDTH_CENTER = 800
        private const val HEIGHT_CENTER = 500

        private val SCALES = listOf(0.8f, 1f, 1.5f)
    }
}
