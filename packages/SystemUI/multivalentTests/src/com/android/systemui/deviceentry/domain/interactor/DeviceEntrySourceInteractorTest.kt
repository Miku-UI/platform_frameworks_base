/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.hardware.face.FaceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.SceneKey
import com.android.keyguard.keyguardUpdateMonitor
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.fakeAuthenticationRepository
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.biometrics.authController
import com.android.systemui.bouncer.data.repository.keyguardBouncerRepository
import com.android.systemui.deviceentry.shared.model.SuccessFaceAuthenticationStatus
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.data.repository.configureKeyguardBypass
import com.android.systemui.keyguard.data.repository.fakeDeviceEntryFaceAuthRepository
import com.android.systemui.keyguard.data.repository.fakeDeviceEntryFingerprintAuthRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.data.repository.keyguardBypassRepository
import com.android.systemui.keyguard.data.repository.keyguardOcclusionRepository
import com.android.systemui.keyguard.data.repository.verifyCallback
import com.android.systemui.keyguard.shared.model.BiometricUnlockMode
import com.android.systemui.keyguard.shared.model.BiometricUnlockSource
import com.android.systemui.keyguard.shared.model.SuccessFingerprintAuthenticationStatus
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.domain.interactor.shadeModeInteractor
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.statusbar.phone.dozeScrimController
import com.android.systemui.statusbar.phone.screenOffAnimationController
import com.android.systemui.statusbar.policy.DevicePostureController.DEVICE_POSTURE_OPENED
import com.android.systemui.statusbar.policy.devicePostureController
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class DeviceEntrySourceInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val underTest: DeviceEntrySourceInteractor by lazy {
        kosmos.deviceEntrySourceInteractor
    }

    @Before
    fun setup() {
        with(kosmos) {
            if (SceneContainerFlag.isEnabled) {
                whenever(authController.isUdfpsFingerDown).thenReturn(false)
                whenever(dozeScrimController.isPulsing).thenReturn(false)
                whenever(keyguardUpdateMonitor.isUnlockingWithBiometricAllowed(anyBoolean()))
                    .thenReturn(true)
                whenever(screenOffAnimationController.isKeyguardShowDelayed()).thenReturn(false)
                fakeAuthenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.Pin)
            }
        }
    }

    @DisableSceneContainer
    @Test
    fun deviceEntryFromFaceUnlock() =
        kosmos.runTest {
            val deviceEntryFromBiometricAuthentication by
                collectLastValue(underTest.deviceEntryFromBiometricSource)

            fakeKeyguardRepository.setBiometricUnlockState(
                BiometricUnlockMode.WAKE_AND_UNLOCK,
                BiometricUnlockSource.FACE_SENSOR,
            )
            runCurrent()

            assertThat(deviceEntryFromBiometricAuthentication)
                .isEqualTo(BiometricUnlockSource.FACE_SENSOR)
        }

    @DisableSceneContainer
    @Test
    fun deviceEntryFromFingerprintUnlock() =
        kosmos.runTest {
            val deviceEntryFromBiometricAuthentication by
                collectLastValue(underTest.deviceEntryFromBiometricSource)

            fakeKeyguardRepository.setBiometricUnlockState(
                BiometricUnlockMode.WAKE_AND_UNLOCK,
                BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            runCurrent()

            assertThat(deviceEntryFromBiometricAuthentication)
                .isEqualTo(BiometricUnlockSource.FINGERPRINT_SENSOR)
        }

    @DisableSceneContainer
    @Test
    fun noDeviceEntry() =
        kosmos.runTest {
            val deviceEntryFromBiometricAuthentication by
                collectLastValue(underTest.deviceEntryFromBiometricSource)

            fakeKeyguardRepository.setBiometricUnlockState(
                BiometricUnlockMode.ONLY_WAKE, // doesn't dismiss keyguard:
                BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            runCurrent()

            assertThat(deviceEntryFromBiometricAuthentication).isNull()
        }

    @EnableSceneContainer
    @Test
    fun deviceEntryFromFingerprintUnlockOnLockScreen_sceneContainerEnabled() =
        kosmos.runTest {
            val deviceEntryFromBiometricSource by
                collectLastValue(underTest.deviceEntryFromBiometricSource)

            whenever(keyguardUpdateMonitor.isDeviceInteractive).thenReturn(true)
            configureDeviceEntryBiometricAuthSuccessState(isFingerprintAuth = true)
            configureBiometricUnlockState(
                primaryBouncerVisible = false,
                alternateBouncerVisible = false,
                sceneKey = Scenes.Lockscreen,
            )

            configureKeyguardBypass(isBypassAvailable = true)

            assertThat(deviceEntryFromBiometricSource)
                .isEqualTo(BiometricUnlockSource.FINGERPRINT_SENSOR)
        }

    @EnableSceneContainer
    @Test
    fun deviceEntryFromFingerprintUnlockOnAod_sceneContainerEnabled() =
        kosmos.runTest {
            val deviceEntryFromBiometricSource by
                collectLastValue(underTest.deviceEntryFromBiometricSource)

            whenever(keyguardUpdateMonitor.isDeviceInteractive).thenReturn(false)
            configureDeviceEntryBiometricAuthSuccessState(isFingerprintAuth = true)
            configureBiometricUnlockState(
                primaryBouncerVisible = false,
                alternateBouncerVisible = false,
                sceneKey = Scenes.Dream,
            )

            assertThat(deviceEntryFromBiometricSource)
                .isEqualTo(BiometricUnlockSource.FINGERPRINT_SENSOR)
        }

    @EnableSceneContainer
    @Test
    fun deviceEntryFromFingerprintUnlockOnBouncer_sceneContainerEnabled() =
        kosmos.runTest {
            val deviceEntryFromBiometricSource by
                collectLastValue(underTest.deviceEntryFromBiometricSource)

            whenever(keyguardUpdateMonitor.isDeviceInteractive).thenReturn(true)
            configureDeviceEntryBiometricAuthSuccessState(isFingerprintAuth = true)
            configureBiometricUnlockState(
                primaryBouncerVisible = true,
                alternateBouncerVisible = false,
                sceneKey = Scenes.Lockscreen,
            )

            assertThat(deviceEntryFromBiometricSource)
                .isEqualTo(BiometricUnlockSource.FINGERPRINT_SENSOR)
        }

    @EnableSceneContainer
    @Test
    fun deviceEntryFromFingerprintUnlockOnShade_sceneContainerEnabled() =
        kosmos.runTest {
            val deviceEntryFromBiometricSource by
                collectLastValue(underTest.deviceEntryFromBiometricSource)

            whenever(keyguardUpdateMonitor.isDeviceInteractive).thenReturn(true)
            configureDeviceEntryBiometricAuthSuccessState(isFingerprintAuth = true)
            configureBiometricUnlockState(
                primaryBouncerVisible = false,
                alternateBouncerVisible = false,
                sceneKey = Scenes.Lockscreen,
            )

            assertThat(deviceEntryFromBiometricSource)
                .isEqualTo(BiometricUnlockSource.FINGERPRINT_SENSOR)
        }

    @EnableSceneContainer
    @Test
    fun deviceEntryFromFingerprintUnlockOnAlternateBouncer_sceneContainerEnabled() =
        kosmos.runTest {
            val deviceEntryFromBiometricSource by
                collectLastValue(underTest.deviceEntryFromBiometricSource)

            whenever(keyguardUpdateMonitor.isDeviceInteractive).thenReturn(true)
            configureDeviceEntryBiometricAuthSuccessState(isFingerprintAuth = true)
            configureBiometricUnlockState(
                primaryBouncerVisible = false,
                alternateBouncerVisible = true,
                sceneKey = Scenes.Lockscreen,
            )

            assertThat(deviceEntryFromBiometricSource)
                .isEqualTo(BiometricUnlockSource.FINGERPRINT_SENSOR)
        }

    @EnableSceneContainer
    @Test
    fun deviceEntryFromFaceUnlockOnLockScreen_bypassAvailable_sceneContainerEnabled() =
        kosmos.runTest {
            configureKeyguardBypass(isBypassAvailable = true)

            val deviceEntryFromBiometricSource by
                collectLastValue(underTest.deviceEntryFromBiometricSource)

            whenever(keyguardUpdateMonitor.isDeviceInteractive).thenReturn(true)
            configureDeviceEntryBiometricAuthSuccessState(isFaceAuth = true)
            configureBiometricUnlockState(
                primaryBouncerVisible = false,
                alternateBouncerVisible = false,
                sceneKey = Scenes.Lockscreen,
            )

            assertThat(deviceEntryFromBiometricSource).isEqualTo(BiometricUnlockSource.FACE_SENSOR)
        }

    @EnableSceneContainer
    @Test
    fun deviceEntryFromFaceUnlockOnLockScreen_bypassDisabled_sceneContainerEnabled() =
        kosmos.runTest {
            configureKeyguardBypass(isBypassAvailable = false)

            collectLastValue(keyguardBypassRepository.isBypassAvailable)
            runCurrent()

            val postureControllerCallback = devicePostureController.verifyCallback()
            postureControllerCallback.onPostureChanged(DEVICE_POSTURE_OPENED)

            val deviceEntryFromBiometricSource by
                collectLastValue(underTest.deviceEntryFromBiometricSource)

            whenever(keyguardUpdateMonitor.isDeviceInteractive).thenReturn(true)
            configureDeviceEntryBiometricAuthSuccessState(isFaceAuth = true)
            configureBiometricUnlockState(
                primaryBouncerVisible = false,
                alternateBouncerVisible = false,
                sceneKey = Scenes.Lockscreen,
            )

            // MODE_NONE does not dismiss keyguard
            assertThat(deviceEntryFromBiometricSource).isNull()
        }

    @EnableSceneContainer
    @Test
    fun deviceEntryFromFaceUnlockOnBouncer_sceneContainerEnabled() =
        kosmos.runTest {
            configureKeyguardBypass(isBypassAvailable = true)
            val deviceEntryFromBiometricSource by
                collectLastValue(underTest.deviceEntryFromBiometricSource)

            whenever(keyguardUpdateMonitor.isDeviceInteractive).thenReturn(true)
            configureDeviceEntryBiometricAuthSuccessState(isFaceAuth = true)
            configureBiometricUnlockState(
                primaryBouncerVisible = true,
                alternateBouncerVisible = false,
                sceneKey = Scenes.Lockscreen,
            )

            assertThat(deviceEntryFromBiometricSource).isEqualTo(BiometricUnlockSource.FACE_SENSOR)
        }

    @EnableSceneContainer
    @Test
    fun deviceEntryFromFaceUnlockOnShade_bypassAvailable_sceneContainerEnabled() =
        kosmos.runTest {
            val shadeMode by collectLastValue(shadeModeInteractor.shadeMode)
            enableSingleShade()
            assertThat(shadeMode).isEqualTo(ShadeMode.Single)
            configureKeyguardBypass(isBypassAvailable = true)
            val deviceEntryFromBiometricSource by
                collectLastValue(underTest.deviceEntryFromBiometricSource)

            whenever(keyguardUpdateMonitor.isDeviceInteractive).thenReturn(true)
            configureDeviceEntryBiometricAuthSuccessState(isFaceAuth = true)
            configureBiometricUnlockState(
                primaryBouncerVisible = false,
                alternateBouncerVisible = false,
                sceneKey = Scenes.Shade,
            )

            // MODE_NONE does not dismiss keyguard
            assertThat(deviceEntryFromBiometricSource).isNull()
        }

    @EnableSceneContainer
    @Test
    fun deviceEntryFromFaceUnlockOnShade_bypassDisabled_sceneContainerEnabled() =
        kosmos.runTest {
            configureKeyguardBypass(isBypassAvailable = false)
            val deviceEntryFromBiometricSource by
                collectLastValue(underTest.deviceEntryFromBiometricSource)

            whenever(keyguardUpdateMonitor.isDeviceInteractive).thenReturn(true)
            configureDeviceEntryBiometricAuthSuccessState(isFaceAuth = true)
            configureBiometricUnlockState(
                primaryBouncerVisible = false,
                alternateBouncerVisible = false,
                sceneKey = Scenes.Lockscreen,
            )

            assertThat(deviceEntryFromBiometricSource).isNull()
        }

    @EnableSceneContainer
    @Test
    fun deviceEntryFromFaceUnlockOnAlternateBouncer_sceneContainerEnabled() =
        kosmos.runTest {
            configureKeyguardBypass(isBypassAvailable = true)
            val deviceEntryFromBiometricSource by
                collectLastValue(underTest.deviceEntryFromBiometricSource)

            whenever(keyguardUpdateMonitor.isDeviceInteractive).thenReturn(true)
            keyguardOcclusionRepository.setShowWhenLockedActivityInfo(onTop = true)
            configureDeviceEntryBiometricAuthSuccessState(isFaceAuth = true)
            configureBiometricUnlockState(
                primaryBouncerVisible = false,
                alternateBouncerVisible = true,
                sceneKey = Scenes.Lockscreen,
            )

            assertThat(deviceEntryFromBiometricSource).isEqualTo(BiometricUnlockSource.FACE_SENSOR)
        }

    private fun Kosmos.configureDeviceEntryBiometricAuthSuccessState(
        isFingerprintAuth: Boolean = false,
        isFaceAuth: Boolean = false,
    ) {
        if (isFingerprintAuth) {
            fakeDeviceEntryFingerprintAuthRepository.setAuthenticationStatus(
                SuccessFingerprintAuthenticationStatus(0, true)
            )
        }

        if (isFaceAuth) {
            fakeDeviceEntryFaceAuthRepository.setAuthenticationStatus(
                SuccessFaceAuthenticationStatus(
                    FaceManager.AuthenticationResult(null, null, 0, true)
                )
            )
        }
    }

    private fun Kosmos.configureBiometricUnlockState(
        primaryBouncerVisible: Boolean,
        alternateBouncerVisible: Boolean,
        sceneKey: SceneKey,
    ) {
        keyguardBouncerRepository.setAlternateVisible(alternateBouncerVisible)
        sceneInteractor.changeScene(sceneKey, "reason")
        if (primaryBouncerVisible) {
            sceneInteractor.showOverlay(Overlays.Bouncer, "reason")
        }
        sceneInteractor.setTransitionState(
            flowOf(
                ObservableTransitionState.Idle(
                    sceneKey,
                    if (primaryBouncerVisible) setOf(Overlays.Bouncer) else emptySet(),
                )
            )
        )
        runCurrent()
    }
}
