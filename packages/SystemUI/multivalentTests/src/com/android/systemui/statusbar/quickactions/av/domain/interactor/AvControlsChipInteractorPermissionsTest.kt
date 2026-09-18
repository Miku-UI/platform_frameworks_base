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

package com.android.systemui.statusbar.quickactions.av.domain.interactor

import android.hardware.SensorPrivacyManager
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags.FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class AvControlsChipInteractorPermissionsTest : AvControlsChipInteractorTestBase() {

    @Test
    @EnableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun setCameraPermission() =
        kosmos.runTest {
            assertThat(collectLastValue(underTest.cameraBlocked).invoke()).isFalse()
            assertThat(sensorPrivacyController.isSensorBlocked(SensorPrivacyManager.Sensors.CAMERA))
                .isFalse()

            underTest.setCameraBlocked(true)
            assertThat(collectLastValue(underTest.cameraBlocked).invoke()).isTrue()
            assertThat(sensorPrivacyController.isSensorBlocked(SensorPrivacyManager.Sensors.CAMERA))
                .isTrue()

            underTest.setCameraBlocked(false)
            assertThat(collectLastValue(underTest.cameraBlocked).invoke()).isFalse()
            assertThat(sensorPrivacyController.isSensorBlocked(SensorPrivacyManager.Sensors.CAMERA))
                .isFalse()
        }

    @Test
    @EnableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun setMicrophonePermission() =
        kosmos.runTest {
            assertThat(collectLastValue(underTest.microphoneBlocked).invoke()).isFalse()
            assertThat(
                    sensorPrivacyController.isSensorBlocked(SensorPrivacyManager.Sensors.MICROPHONE)
                )
                .isFalse()

            underTest.setMicrophoneBlocked(true)
            assertThat(collectLastValue(underTest.microphoneBlocked).invoke()).isTrue()
            assertThat(
                    sensorPrivacyController.isSensorBlocked(SensorPrivacyManager.Sensors.MICROPHONE)
                )
                .isTrue()

            underTest.setMicrophoneBlocked(false)
            assertThat(collectLastValue(underTest.microphoneBlocked).invoke()).isFalse()
            assertThat(
                    sensorPrivacyController.isSensorBlocked(SensorPrivacyManager.Sensors.MICROPHONE)
                )
                .isFalse()
        }

    @Test
    @EnableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun receiveCameraPermissionUpdate() =
        kosmos.runTest {
            assertThat(collectLastValue(underTest.cameraBlocked).invoke()).isFalse()
            assertThat(sensorPrivacyController.isSensorBlocked(SensorPrivacyManager.Sensors.CAMERA))
                .isFalse()

            sensorPrivacyController.setSensorBlocked(
                source = SensorPrivacyManager.Sources.OTHER,
                sensor = SensorPrivacyManager.Sensors.CAMERA,
                blocked = true,
            )
            assertThat(collectLastValue(underTest.cameraBlocked).invoke()).isTrue()
            assertThat(sensorPrivacyController.isSensorBlocked(SensorPrivacyManager.Sensors.CAMERA))
                .isTrue()

            sensorPrivacyController.setSensorBlocked(
                source = SensorPrivacyManager.Sources.OTHER,
                sensor = SensorPrivacyManager.Sensors.CAMERA,
                blocked = false,
            )
            assertThat(collectLastValue(underTest.cameraBlocked).invoke()).isFalse()
            assertThat(sensorPrivacyController.isSensorBlocked(SensorPrivacyManager.Sensors.CAMERA))
                .isFalse()
        }

    @Test
    @EnableFlags(FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN)
    fun receiveMicrophonePermissionUpdate() =
        kosmos.runTest {
            assertThat(collectLastValue(underTest.microphoneBlocked).invoke()).isFalse()
            assertThat(
                    sensorPrivacyController.isSensorBlocked(SensorPrivacyManager.Sensors.MICROPHONE)
                )
                .isFalse()

            sensorPrivacyController.setSensorBlocked(
                source = SensorPrivacyManager.Sources.OTHER,
                sensor = SensorPrivacyManager.Sensors.MICROPHONE,
                blocked = true,
            )
            assertThat(
                    sensorPrivacyController.isSensorBlocked(SensorPrivacyManager.Sensors.MICROPHONE)
                )
                .isTrue()
            assertThat(collectLastValue(underTest.microphoneBlocked).invoke()).isTrue()

            sensorPrivacyController.setSensorBlocked(
                source = SensorPrivacyManager.Sources.OTHER,
                sensor = SensorPrivacyManager.Sensors.MICROPHONE,
                blocked = false,
            )
            assertThat(collectLastValue(underTest.microphoneBlocked).invoke()).isFalse()
            assertThat(
                    sensorPrivacyController.isSensorBlocked(SensorPrivacyManager.Sensors.MICROPHONE)
                )
                .isFalse()
        }
}
