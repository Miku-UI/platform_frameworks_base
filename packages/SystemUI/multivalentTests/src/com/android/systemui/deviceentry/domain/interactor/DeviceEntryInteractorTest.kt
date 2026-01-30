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

import android.testing.TestableLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.FakeAuthenticationRepository
import com.android.systemui.authentication.data.repository.fakeAuthenticationRepository
import com.android.systemui.authentication.domain.interactor.authenticationInteractor
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.None
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.Password
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.Pattern
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.Pin
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.Sim
import com.android.systemui.biometrics.data.repository.fakeFingerprintPropertyRepository
import com.android.systemui.bouncer.data.repository.keyguardBouncerRepository
import com.android.systemui.bouncer.domain.interactor.alternateBouncerInteractor
import com.android.systemui.deviceentry.data.repository.fakeDeviceEntryRepository
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.data.repository.biometricSettingsRepository
import com.android.systemui.keyguard.data.repository.deviceEntryFingerprintAuthRepository
import com.android.systemui.keyguard.data.repository.fakeDeviceEntryFaceAuthRepository
import com.android.systemui.keyguard.data.repository.fakeDeviceEntryFingerprintAuthRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.data.repository.fakeTrustRepository
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.SuccessFingerprintAuthenticationStatus
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.collectValues
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useStandardTestDispatcher
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.scene.data.model.asIterable
import com.android.systemui.scene.domain.interactor.sceneBackInteractor
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.domain.startable.sceneContainerStartable
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.statusbar.sysuiStatusBarStateController
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
@TestableLooper.RunWithLooper
class DeviceEntryInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val trustRepository by lazy { kosmos.fakeTrustRepository }
    private val underTest: DeviceEntryInteractor by lazy { kosmos.deviceEntryInteractor }

    @Before
    fun setUp() {
        kosmos.sceneContainerStartable.start()
    }

    @Test
    fun canSwipeToEnter_startsNull() =
        testKosmos().useStandardTestDispatcher().runTest {
            val underTest = deviceEntryInteractor
            val values by collectValues(underTest.canSwipeToEnter)
            assertThat(values[0]).isNull()
        }

    @Test
    fun isUnlocked_whenAuthMethodIsNoneAndLockscreenDisabled_isTrue() =
        kosmos.runTest {
            fakeAuthenticationRepository.setAuthenticationMethod(None)
            fakeDeviceEntryRepository.setLockscreenEnabled(false)

            val isUnlocked by collectLastValue(underTest.isUnlocked)
            assertThat(isUnlocked).isTrue()
        }

    @Test
    fun isUnlocked_whenAuthMethodIsNoneAndLockscreenEnabled_isTrue() =
        kosmos.runTest {
            setupSwipeDeviceEntryMethod()

            val isUnlocked by collectLastValue(underTest.isUnlocked)
            assertThat(isUnlocked).isTrue()
        }

    @Test
    fun isUnlocked_whenAuthMethodIsSimAndUnlocked_isFalse() =
        kosmos.runTest {
            fakeAuthenticationRepository.setAuthenticationMethod(Sim)
            fakeDeviceEntryFingerprintAuthRepository.setAuthenticationStatus(
                SuccessFingerprintAuthenticationStatus(0, true)
            )

            val isUnlocked by collectLastValue(underTest.isUnlocked)
            assertThat(isUnlocked).isFalse()
        }

    @Test
    fun isDeviceEntered_onLockscreenWithSwipe_isFalse() =
        kosmos.runTest {
            val isDeviceEntered by collectLastValue(underTest.isDeviceEntered)
            setupSwipeDeviceEntryMethod()
            switchToScene(Scenes.Lockscreen)

            assertThat(isDeviceEntered).isFalse()
        }

    @Test
    fun isDeviceEntered_onShadeBeforeDismissingLockscreenWithSwipe_isFalse() =
        kosmos.runTest {
            enableSingleShade()
            val isDeviceEntered by collectLastValue(underTest.isDeviceEntered)
            setupSwipeDeviceEntryMethod()
            switchToScene(Scenes.Lockscreen)
            switchToScene(Scenes.Shade)

            assertThat(isDeviceEntered).isFalse()
        }

    @Test
    fun isDeviceEntered_afterDismissingLockscreenWithSwipe_isTrue() =
        kosmos.runTest {
            val isDeviceEntered by collectLastValue(underTest.isDeviceEntered)
            setupSwipeDeviceEntryMethod()
            switchToScene(Scenes.Lockscreen)
            switchToScene(Scenes.Gone)

            assertThat(isDeviceEntered).isTrue()
        }

    @Test
    fun isDeviceEntered_onShadeAfterDismissingLockscreenWithSwipe_isTrue() =
        kosmos.runTest {
            enableSingleShade()
            val isDeviceEntered by collectLastValue(underTest.isDeviceEntered)
            setupSwipeDeviceEntryMethod()
            switchToScene(Scenes.Lockscreen)
            switchToScene(Scenes.Gone)
            switchToScene(Scenes.Shade)

            assertThat(isDeviceEntered).isTrue()
        }

    @Test
    fun isDeviceEntered_onBouncer_isFalse() =
        kosmos.runTest {
            fakeAuthenticationRepository.setAuthenticationMethod(Pattern)
            fakeDeviceEntryRepository.setLockscreenEnabled(true)
            switchToScene(Scenes.Lockscreen)
            showBouncer()

            val isDeviceEntered by collectLastValue(underTest.isDeviceEntered)
            assertThat(isDeviceEntered).isFalse()
        }

    @Test
    fun canSwipeToEnter_onLockscreenWithSwipe_isTrue() =
        kosmos.runTest {
            setupSwipeDeviceEntryMethod()
            switchToScene(Scenes.Lockscreen)

            val canSwipeToEnter by collectLastValue(underTest.canSwipeToEnter)
            assertThat(canSwipeToEnter).isTrue()
        }

    @Test
    fun canSwipeToEnter_onLockscreenWithPin_isFalse() =
        kosmos.runTest {
            fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            fakeDeviceEntryRepository.setLockscreenEnabled(true)
            switchToScene(Scenes.Lockscreen)

            val canSwipeToEnter by collectLastValue(underTest.canSwipeToEnter)
            assertThat(canSwipeToEnter).isFalse()
        }

    @Test
    fun canSwipeToEnter_afterLockscreenDismissedInSwipeMode_isFalse() =
        kosmos.runTest {
            setupSwipeDeviceEntryMethod()
            switchToScene(Scenes.Lockscreen)
            switchToScene(Scenes.Gone)

            val canSwipeToEnter by collectLastValue(underTest.canSwipeToEnter)
            assertThat(canSwipeToEnter).isFalse()
        }

    @Test
    fun canSwipeToEnter_whenTrustedByTrustManager_isTrue() =
        kosmos.runTest {
            val canSwipeToEnter by collectLastValue(underTest.canSwipeToEnter)
            fakeAuthenticationRepository.setAuthenticationMethod(Password)
            switchToScene(Scenes.Lockscreen)
            assertThat(canSwipeToEnter).isFalse()

            trustRepository.setCurrentUserTrusted(true)
            fakeDeviceEntryFaceAuthRepository.isAuthenticated.value = false

            assertThat(canSwipeToEnter).isTrue()
        }

    @Test
    fun canSwipeToEnter_whenAuthenticatedByFace_isTrue() =
        kosmos.runTest {
            val canSwipeToEnter by collectLastValue(underTest.canSwipeToEnter)
            fakeAuthenticationRepository.setAuthenticationMethod(Password)
            switchToScene(Scenes.Lockscreen)
            assertThat(canSwipeToEnter).isFalse()

            fakeDeviceEntryFaceAuthRepository.isAuthenticated.value = true
            trustRepository.setCurrentUserTrusted(false)

            assertThat(canSwipeToEnter).isTrue()
        }

    @Test
    fun isAuthenticationRequired_lockedAndSecured_true() =
        kosmos.runTest {
            fakeAuthenticationRepository.setAuthenticationMethod(Password)

            assertThat(underTest.isAuthenticationRequired()).isTrue()
        }

    @Test
    fun isAuthenticationRequired_lockedAndNotSecured_false() =
        kosmos.runTest {
            fakeAuthenticationRepository.setAuthenticationMethod(None)

            assertThat(underTest.isAuthenticationRequired()).isFalse()
        }

    @Test
    fun isAuthenticationRequired_unlockedAndSecured_false() =
        kosmos.runTest {
            fakeDeviceEntryFingerprintAuthRepository.setAuthenticationStatus(
                SuccessFingerprintAuthenticationStatus(0, true)
            )
            fakeAuthenticationRepository.setAuthenticationMethod(Password)

            assertThat(underTest.isAuthenticationRequired()).isFalse()
        }

    @Test
    fun isAuthenticationRequired_unlockedAndNotSecured_false() =
        kosmos.runTest {
            fakeDeviceEntryFingerprintAuthRepository.setAuthenticationStatus(
                SuccessFingerprintAuthenticationStatus(0, true)
            )
            fakeAuthenticationRepository.setAuthenticationMethod(None)

            assertThat(underTest.isAuthenticationRequired()).isFalse()
        }

    @Test
    fun showOrUnlockDevice_notLocked_switchesToGoneScene() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            switchToScene(Scenes.Lockscreen)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            fakeDeviceEntryFingerprintAuthRepository.setAuthenticationStatus(
                SuccessFingerprintAuthenticationStatus(0, true)
            )

            underTest.attemptDeviceEntry("test")

            assertThat(currentScene).isEqualTo(Scenes.Gone)
        }

    @Test
    fun showOrUnlockDevice_notLocked_replacesLockscreenWithGoneInTheBackStack() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val backStack by collectLastValue(sceneBackInteractor.backStack)
            switchToScene(Scenes.Lockscreen)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            switchToScene(Scenes.QuickSettings)
            assertThat(currentScene).isEqualTo(Scenes.QuickSettings)
            assertThat(backStack!!.asIterable().toList()).isEqualTo(listOf(Scenes.Lockscreen))

            fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            fakeDeviceEntryFingerprintAuthRepository.setAuthenticationStatus(
                SuccessFingerprintAuthenticationStatus(0, true)
            )

            underTest.attemptDeviceEntry("test")

            assertThat(currentScene).isEqualTo(Scenes.QuickSettings)
            assertThat(backStack!!.asIterable().toList()).isEqualTo(listOf(Scenes.Gone))
        }

    @Test
    fun showOrUnlockDevice_authMethodNotSecure_switchesToGoneScene() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            switchToScene(Scenes.Lockscreen)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            fakeAuthenticationRepository.setAuthenticationMethod(None)

            underTest.attemptDeviceEntry("test")

            assertThat(currentScene).isEqualTo(Scenes.Gone)
        }

    @Test
    fun showOrUnlockDevice_authMethodNotSecure_replacesLockscreenWithGoneInTheBackStack() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val backStack by collectLastValue(sceneBackInteractor.backStack)
            switchToScene(Scenes.Lockscreen)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            switchToScene(Scenes.QuickSettings)
            assertThat(currentScene).isEqualTo(Scenes.QuickSettings)
            assertThat(backStack!!.asIterable().toList()).isEqualTo(listOf(Scenes.Lockscreen))

            fakeAuthenticationRepository.setAuthenticationMethod(None)

            underTest.attemptDeviceEntry("test")

            assertThat(currentScene).isEqualTo(Scenes.QuickSettings)
            assertThat(backStack!!.asIterable().toList()).isEqualTo(listOf(Scenes.Gone))
        }

    @Test
    fun showOrUnlockDevice_authMethodSwipe_switchesToGoneScene() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            switchToScene(Scenes.Lockscreen)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            fakeDeviceEntryRepository.setLockscreenEnabled(true)
            fakeAuthenticationRepository.setAuthenticationMethod(None)

            underTest.attemptDeviceEntry("test")

            assertThat(currentScene).isEqualTo(Scenes.Gone)
        }

    @Test
    fun showOrUnlockDevice_authMethodSwipe_replacesLockscreenWithGoneInTheBackStack() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val backStack by collectLastValue(sceneBackInteractor.backStack)
            switchToScene(Scenes.Lockscreen)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            switchToScene(Scenes.QuickSettings)
            assertThat(currentScene).isEqualTo(Scenes.QuickSettings)
            assertThat(backStack!!.asIterable().toList()).isEqualTo(listOf(Scenes.Lockscreen))

            fakeDeviceEntryRepository.setLockscreenEnabled(true)
            fakeAuthenticationRepository.setAuthenticationMethod(None)

            underTest.attemptDeviceEntry("test")

            assertThat(currentScene).isEqualTo(Scenes.QuickSettings)
            assertThat(backStack!!.asIterable().toList()).isEqualTo(listOf(Scenes.Gone))
        }

    @Test
    fun showOrUnlockDevice_noAlternateBouncer_switchesToBouncerScene() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            switchToScene(Scenes.Lockscreen)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            fakeFingerprintPropertyRepository.supportsRearFps() // altBouncer unsupported
            fakeAuthenticationRepository.setAuthenticationMethod(Pin)

            underTest.attemptDeviceEntry("test")

            assertThat(currentOverlays).contains(Overlays.Bouncer)
        }

    @Test
    fun showOrUnlockDevice_showsAlternateBouncer_staysOnLockscreenScene() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            switchToScene(Scenes.Lockscreen)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            givenCanShowAlternateBouncer()

            underTest.attemptDeviceEntry("test")

            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
        }

    @Test
    fun successfulAuthenticationChallengeAttempt_updatesIsUnlockedState() =
        kosmos.runTest {
            val isUnlocked by collectLastValue(underTest.isUnlocked)
            fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            fakeDeviceEntryRepository.setLockscreenEnabled(true)
            assertThat(isUnlocked).isFalse()

            authenticationInteractor.authenticate(FakeAuthenticationRepository.DEFAULT_PIN)

            assertThat(isUnlocked).isTrue()
        }

    @Test
    fun isDeviceEntered_unlockedWhileOnShade_emitsTrue() =
        kosmos.runTest {
            enableSingleShade()
            val isDeviceEntered by collectLastValue(underTest.isDeviceEntered)
            val isDeviceEnteredDirectly by collectLastValue(underTest.isDeviceEnteredDirectly)
            assertThat(isDeviceEntered).isFalse()
            assertThat(isDeviceEnteredDirectly).isFalse()
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            // Navigate to shade and bouncer:
            switchToScene(Scenes.Shade)
            assertThat(currentScene).isEqualTo(Scenes.Shade)
            // Simulating a "leave it open when the keyguard is hidden" which means the bouncer will
            // be shown and successful authentication should take the user back to where they are,
            // the shade scene.
            sysuiStatusBarStateController.setLeaveOpenOnKeyguardHide(true)
            showBouncer()
            assertThat(currentOverlays).contains(Overlays.Bouncer)

            assertThat(isDeviceEntered).isFalse()
            assertThat(isDeviceEnteredDirectly).isFalse()
            // Authenticate with PIN to unlock and dismiss the lockscreen:
            authenticationInteractor.authenticate(FakeAuthenticationRepository.DEFAULT_PIN)

            assertThat(isDeviceEntered).isTrue()
            assertThat(isDeviceEnteredDirectly).isFalse()
        }

    private fun Kosmos.setupSwipeDeviceEntryMethod() {
        fakeAuthenticationRepository.setAuthenticationMethod(None)
        fakeDeviceEntryRepository.setLockscreenEnabled(true)
    }

    private fun Kosmos.switchToScene(sceneKey: SceneKey) {
        sceneInteractor.changeScene(sceneKey, "reason")
        sceneInteractor.setTransitionState(flowOf(ObservableTransitionState.Idle(sceneKey)))
    }

    private fun Kosmos.showBouncer() {
        sceneInteractor.showOverlay(Overlays.Bouncer, "reason")
        sceneInteractor.setTransitionState(
            flowOf(
                ObservableTransitionState.Idle(
                    sceneInteractor.currentScene.value,
                    setOf(Overlays.Bouncer),
                )
            )
        )
    }

    private suspend fun Kosmos.givenCanShowAlternateBouncer() {
        val canShowAlternateBouncer by
            collectLastValue(alternateBouncerInteractor.canShowAlternateBouncer)
        fakeFingerprintPropertyRepository.supportsUdfps()
        fakeKeyguardTransitionRepository.sendTransitionSteps(
            from = KeyguardState.GONE,
            to = KeyguardState.LOCKSCREEN,
            testScheduler = testScope.testScheduler,
        )
        deviceEntryFingerprintAuthRepository.setLockedOut(false)
        biometricSettingsRepository.setIsFingerprintAuthCurrentlyAllowed(true)
        fakeKeyguardRepository.setKeyguardDismissible(false)
        keyguardBouncerRepository.setPrimaryShow(false)
        assertThat(canShowAlternateBouncer).isTrue()
    }
}
