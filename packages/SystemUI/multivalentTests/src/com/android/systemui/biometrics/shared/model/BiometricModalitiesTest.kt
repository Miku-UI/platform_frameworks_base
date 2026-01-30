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

package com.android.systemui.biometrics.shared.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class BiometricModalitiesTest : SysuiTestCase() {

    @Test
    fun isEmpty() {
        assertThat(BiometricModalities().isEmpty).isTrue()
    }

    @Test
    fun hasUdfps() {
        with(
            BiometricModalities(
                fingerprintSensorInfo =
                    FingerprintSensorInfo(
                        type = FingerprintSensorType.UDFPS_OPTICAL,
                        strength = SensorStrength.STRONG,
                    )
            )
        ) {
            assertThat(isEmpty).isFalse()
            assertThat(hasUdfps).isTrue()
            assertThat(hasSfps).isFalse()
            assertThat(hasFace).isFalse()
            assertThat(hasFaceOnly).isFalse()
            assertThat(hasFingerprint).isTrue()
            assertThat(hasFingerprintOnly).isTrue()
            assertThat(hasFaceAndFingerprint).isFalse()
        }
    }

    @Test
    fun hasSfps() {
        with(
            BiometricModalities(
                fingerprintSensorInfo =
                    FingerprintSensorInfo(
                        type = FingerprintSensorType.POWER_BUTTON,
                        strength = SensorStrength.STRONG,
                    )
            )
        ) {
            assertThat(isEmpty).isFalse()
            assertThat(hasUdfps).isFalse()
            assertThat(hasSfps).isTrue()
            assertThat(hasFace).isFalse()
            assertThat(hasFaceOnly).isFalse()
            assertThat(hasFingerprint).isTrue()
            assertThat(hasFingerprintOnly).isTrue()
            assertThat(hasFaceAndFingerprint).isFalse()
        }
    }

    @Test
    fun isSfpsStrong() {
        with(
            BiometricModalities(
                fingerprintSensorInfo =
                    FingerprintSensorInfo(
                        type = FingerprintSensorType.POWER_BUTTON,
                        strength = SensorStrength.STRONG,
                    ),
                faceSensorInfo = null,
            )
        ) {
            assertThat(isSfpsStrong).isTrue()
        }

        with(
            BiometricModalities(
                fingerprintSensorInfo =
                    FingerprintSensorInfo(
                        type = FingerprintSensorType.POWER_BUTTON,
                        strength = SensorStrength.WEAK,
                    ),
                faceSensorInfo = null,
            )
        ) {
            assertThat(isSfpsStrong).isFalse()
        }
    }

    @Test
    fun isUdfpsStrong() {
        with(
            BiometricModalities(
                fingerprintSensorInfo =
                    FingerprintSensorInfo(
                        type = FingerprintSensorType.UDFPS_ULTRASONIC,
                        strength = SensorStrength.STRONG,
                    ),
                faceSensorInfo = null,
            )
        ) {
            assertThat(isUdfpsStrong).isTrue()
        }

        with(
            BiometricModalities(
                fingerprintSensorInfo =
                    FingerprintSensorInfo(
                        type = FingerprintSensorType.UDFPS_ULTRASONIC,
                        strength = SensorStrength.WEAK,
                    ),
                faceSensorInfo = null,
            )
        ) {
            assertThat(isUdfpsStrong).isFalse()
        }
    }

    @Test
    fun faceOnly() {
        with(
            BiometricModalities(
                faceSensorInfo = FaceSensorInfo(id = 0, strength = SensorStrength.STRONG)
            )
        ) {
            assertThat(isEmpty).isFalse()
            assertThat(hasFace).isTrue()
            assertThat(hasFaceOnly).isTrue()
            assertThat(hasFingerprint).isFalse()
            assertThat(hasFingerprintOnly).isFalse()
            assertThat(hasFaceAndFingerprint).isFalse()
        }
    }

    @Test
    fun faceStrength() {
        with(
            BiometricModalities(
                fingerprintSensorInfo = null,
                faceSensorInfo = FaceSensorInfo(id = 0, strength = SensorStrength.STRONG),
            )
        ) {
            assertThat(isFaceStrong).isTrue()
        }

        with(
            BiometricModalities(
                fingerprintSensorInfo = null,
                faceSensorInfo = FaceSensorInfo(id = 0, strength = SensorStrength.WEAK),
            )
        ) {
            assertThat(isFaceStrong).isFalse()
        }
    }

    @Test
    fun faceAndFingerprint() {
        with(
            BiometricModalities(
                fingerprintSensorInfo =
                    FingerprintSensorInfo(
                        type = FingerprintSensorType.POWER_BUTTON,
                        strength = SensorStrength.STRONG,
                    ),
                faceSensorInfo = FaceSensorInfo(id = 0, strength = SensorStrength.STRONG),
            )
        ) {
            assertThat(isEmpty).isFalse()
            assertThat(hasFace).isTrue()
            assertThat(hasFingerprint).isTrue()
            assertThat(hasFaceOnly).isFalse()
            assertThat(hasFingerprintOnly).isFalse()
            assertThat(hasFaceAndFingerprint).isTrue()
        }
    }
}
