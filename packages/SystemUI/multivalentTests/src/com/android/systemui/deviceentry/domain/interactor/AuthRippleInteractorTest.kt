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

package com.android.systemui.deviceentry.domain.interactor

import android.content.res.Configuration
import android.graphics.PointF
import android.graphics.Rect
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags.FLAG_STANDALONE_FINGERPRINT_LOCK_SCREEN_UX_FIX
import com.android.systemui.SysuiTestCase
import com.android.systemui.biometrics.data.repository.fingerprintPropertyRepository
import com.android.systemui.biometrics.shared.model.FingerprintSensorType
import com.android.systemui.biometrics.shared.model.SensorStrength
import com.android.systemui.common.ui.data.repository.fakeConfigurationRepository
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.deviceentry.domain.interactor.AuthRippleInteractor.DwellRipplePhase
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.shared.model.BiometricUnlockMode
import com.android.systemui.keyguard.shared.model.BiometricUnlockSource
import com.android.systemui.kosmos.testScope
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class AuthRippleInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private lateinit var deviceEntrySourceInteractor: DeviceEntrySourceInteractor
    private val fingerprintPropertyRepository = kosmos.fingerprintPropertyRepository
    private val keyguardRepository = kosmos.fakeKeyguardRepository

    private lateinit var underTest: AuthRippleInteractor

    @Before
    fun setup() {
        deviceEntrySourceInteractor = kosmos.deviceEntrySourceInteractor
        underTest = kosmos.authRippleInteractor
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    @DisableSceneContainer
    fun enteringDeviceFromDeviceEntryIcon_udfpsNotSupported_doesNotShowAuthRipple() =
        testScope.runTest {
            val showUnlockRipple by collectLastValue(underTest.showUnlockRipple)
            fingerprintPropertyRepository.supportsRearFps()
            keyguardRepository.setKeyguardDismissible(true)
            runCurrent()
            deviceEntrySourceInteractor.attemptEnterDeviceFromDeviceEntryIcon()
            assertThat(showUnlockRipple).isNull()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    @DisableSceneContainer
    fun enteringDeviceFromDeviceEntryIcon_udfpsSupported_showsAuthRipple() =
        testScope.runTest {
            val showUnlockRipple by collectLastValue(underTest.showUnlockRipple)
            fingerprintPropertyRepository.supportsUdfps()
            keyguardRepository.setKeyguardDismissible(true)
            runCurrent()
            deviceEntrySourceInteractor.attemptEnterDeviceFromDeviceEntryIcon()
            assertThat(showUnlockRipple).isEqualTo(BiometricUnlockSource.FINGERPRINT_SENSOR)
        }

    @Test
    @DisableSceneContainer
    fun faceUnlocked_showsAuthRipple() =
        testScope.runTest {
            val showUnlockRipple by collectLastValue(underTest.showUnlockRipple)
            keyguardRepository.setBiometricUnlockState(
                BiometricUnlockMode.WAKE_AND_DISMISS,
                BiometricUnlockSource.FACE_SENSOR,
            )
            assertThat(showUnlockRipple).isEqualTo(BiometricUnlockSource.FACE_SENSOR)
        }

    @Test
    @DisableSceneContainer
    fun fingerprintUnlocked_showsAuthRipple() =
        testScope.runTest {
            val showUnlockRippleFromBiometricUnlock by collectLastValue(underTest.showUnlockRipple)
            keyguardRepository.setBiometricUnlockState(
                BiometricUnlockMode.WAKE_AND_DISMISS,
                BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            assertThat(showUnlockRippleFromBiometricUnlock)
                .isEqualTo(BiometricUnlockSource.FINGERPRINT_SENSOR)
        }

    @Test
    @EnableFlags(FLAG_STANDALONE_FINGERPRINT_LOCK_SCREEN_UX_FIX)
    @EnableSceneContainer
    fun fingerprintUnlocked_standaloneFpsSupported_sensorOriginIsScreenCenter() =
        testScope.runTest {
            val sensorOrigin by collectLastValue(underTest.sensorOrigin)

            fingerprintPropertyRepository.setProperties(
                sensorId = 0,
                strength = SensorStrength.STRONG,
                sensorType = FingerprintSensorType.STANDALONE,
                sensorLocations = emptyMap(),
            )
            keyguardRepository.setBiometricUnlockState(
                BiometricUnlockMode.WAKE_AND_DISMISS,
                BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            val config = Configuration()
            config.windowConfiguration.setMaxBounds(Rect(0, 0, WIDTH, HEIGHT))
            kosmos.fakeConfigurationRepository.onConfigurationChange(config)
            kosmos.fakeConfigurationRepository.setScaleForResolution(1f)

            runCurrent()

            assertThat(sensorOrigin).isEqualTo(PointF(WIDTH_CENTER, HEIGHT_CENTER))
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    @EnableSceneContainer
    fun pulsing_retractThenFade_updatesToRetractThenFade() =
        testScope.runTest {
            keyguardRepository.setKeyguardShowing(true)
            runCurrent()

            val phase by collectLastValue(underTest.dwellRipplePhase)
            underTest.sendAuthRippleEvent(AuthRippleInteractor.AuthRippleEvent.PulseOut)
            assertThat(phase).isEqualTo(DwellRipplePhase.PULSE_OUT)

            underTest.sendAuthRippleEvent(AuthRippleInteractor.AuthRippleEvent.Retract)
            assertThat(phase).isEqualTo(DwellRipplePhase.RETRACT)

            underTest.sendAuthRippleEvent(AuthRippleInteractor.AuthRippleEvent.FadeOut)
            assertThat(phase).isEqualTo(DwellRipplePhase.FADE_OUT)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    @EnableSceneContainer
    fun dwellRipplePulsesWhenUnlockingRippleEmits_maintainsPulse() =
        testScope.runTest {
            keyguardRepository.setKeyguardShowing(true)
            runCurrent()

            val phase by collectLastValue(underTest.dwellRipplePhase)
            underTest.sendAuthRippleEvent(AuthRippleInteractor.AuthRippleEvent.PulseOut)
            runCurrent()

            keyguardRepository.setBiometricUnlockState(
                BiometricUnlockMode.ONLY_WAKE_UNLOCKED,
                BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            runCurrent()
            assertThat(phase).isEqualTo(DwellRipplePhase.PULSE_OUT)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    @EnableSceneContainer
    fun dwellRippleRetractsWhenUnlockingRippleEmits_maintainsRetracts() =
        testScope.runTest {
            keyguardRepository.setKeyguardShowing(true)
            runCurrent()

            val phase by collectLastValue(underTest.dwellRipplePhase)
            underTest.sendAuthRippleEvent(AuthRippleInteractor.AuthRippleEvent.PulseOut)
            runCurrent()
            assertThat(phase).isEqualTo(DwellRipplePhase.PULSE_OUT)

            underTest.sendAuthRippleEvent(AuthRippleInteractor.AuthRippleEvent.Retract)
            keyguardRepository.setBiometricUnlockState(
                BiometricUnlockMode.ONLY_WAKE_UNLOCKED,
                BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            runCurrent()
            assertThat(phase).isEqualTo(DwellRipplePhase.RETRACT)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    @EnableSceneContainer
    fun dwellRippleFadesOutAndRepulsesWhenUnlockingRippleEmits_fadesOut() =
        testScope.runTest {
            keyguardRepository.setKeyguardShowing(true)
            runCurrent()

            val phase by collectLastValue(underTest.dwellRipplePhase)
            underTest.sendAuthRippleEvent(AuthRippleInteractor.AuthRippleEvent.PulseOut)
            runCurrent()
            assertThat(phase).isEqualTo(DwellRipplePhase.PULSE_OUT)

            underTest.sendAuthRippleEvent(AuthRippleInteractor.AuthRippleEvent.FadeOut)
            runCurrent()
            assertThat(phase).isEqualTo(DwellRipplePhase.FADE_OUT)

            underTest.sendAuthRippleEvent(AuthRippleInteractor.AuthRippleEvent.PulseOut)
            keyguardRepository.setBiometricUnlockState(
                BiometricUnlockMode.ONLY_WAKE_UNLOCKED,
                BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            runCurrent()
            assertThat(phase).isEqualTo(DwellRipplePhase.FADE_OUT)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    @EnableSceneContainer
    fun idle_receivesRetract_remainsIdle() =
        testScope.runTest {
            keyguardRepository.setKeyguardShowing(true)
            runCurrent()
            val phase by collectLastValue(underTest.dwellRipplePhase)

            underTest.sendAuthRippleEvent(AuthRippleInteractor.AuthRippleEvent.Retract)
            runCurrent()
            assertThat(phase).isEqualTo(DwellRipplePhase.IDLE)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    @EnableSceneContainer
    fun idle_receivesFadeOut_remainsIdle() =
        testScope.runTest {
            keyguardRepository.setKeyguardShowing(true)
            runCurrent()

            val phase by collectLastValue(underTest.dwellRipplePhase)
            underTest.sendAuthRippleEvent(AuthRippleInteractor.AuthRippleEvent.FadeOut)
            runCurrent()
            assertThat(phase).isEqualTo(DwellRipplePhase.IDLE)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    @EnableSceneContainer
    fun unlockedBeforeFingerprintRetracts_ignoreRetractsAndFadeOut() =
        testScope.runTest {
            keyguardRepository.setKeyguardShowing(true)
            runCurrent()

            val phase by collectLastValue(underTest.dwellRipplePhase)
            underTest.sendAuthRippleEvent(AuthRippleInteractor.AuthRippleEvent.PulseOut)
            runCurrent()

            unlockFromSource(BiometricUnlockSource.FACE_SENSOR)
            runCurrent()

            underTest.sendAuthRippleEvent(AuthRippleInteractor.AuthRippleEvent.Retract)
            runCurrent()
            assertThat(phase).isEqualTo(DwellRipplePhase.FADE_OUT)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    @EnableSceneContainer
    fun unlockedWithFingerprint_keyguardReshow_resetsToIdle() =
        testScope.runTest {
            keyguardRepository.setKeyguardShowing(true)
            runCurrent()

            val phase by collectLastValue(underTest.dwellRipplePhase)
            underTest.sendAuthRippleEvent(AuthRippleInteractor.AuthRippleEvent.PulseOut)
            runCurrent()
            unlockFromSource(BiometricUnlockSource.FINGERPRINT_SENSOR)
            runCurrent()
            assertThat(phase).isEqualTo(DwellRipplePhase.FADE_OUT)

            keyguardRepository.setKeyguardShowing(true)
            runCurrent()
            assertThat(phase).isEqualTo(DwellRipplePhase.IDLE)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    @EnableSceneContainer
    fun unlockedWithFingerprint_keyguardReshow_resetsToIdleAndAllowsNewPulse() =
        testScope.runTest {
            keyguardRepository.setKeyguardShowing(true)
            runCurrent()

            val phase by collectLastValue(underTest.dwellRipplePhase)
            underTest.sendAuthRippleEvent(AuthRippleInteractor.AuthRippleEvent.PulseOut)
            assertThat(phase).isEqualTo(DwellRipplePhase.PULSE_OUT)
            unlockFromSource(BiometricUnlockSource.FINGERPRINT_SENSOR)
            assertThat(phase).isEqualTo(DwellRipplePhase.FADE_OUT)
            runCurrent()

            keyguardRepository.setKeyguardShowing(true)
            runCurrent()
            assertThat(phase).isEqualTo(DwellRipplePhase.IDLE)

            underTest.sendAuthRippleEvent(AuthRippleInteractor.AuthRippleEvent.PulseOut)
            runCurrent()
            assertThat(phase).isEqualTo(DwellRipplePhase.PULSE_OUT)
        }

    private fun unlockFromSource(source: BiometricUnlockSource) {
        keyguardRepository.setBiometricUnlockState(BiometricUnlockMode.ONLY_WAKE_UNLOCKED, source)
        underTest.sendAuthRippleEvent(AuthRippleInteractor.AuthRippleEvent.FadeOut)
        keyguardRepository.setKeyguardShowing(false)
    }

    companion object {
        private const val WIDTH = 1600
        private const val HEIGHT = 1000
        private const val WIDTH_CENTER = 800f
        private const val HEIGHT_CENTER = 500f
    }
}
