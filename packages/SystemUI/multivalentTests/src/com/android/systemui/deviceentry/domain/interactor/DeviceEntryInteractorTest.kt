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

import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import android.service.dreams.Flags.FLAG_DRIVE_DREAM_STATE_FROM_OCCLUSION
import android.testing.TestableLooper
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.OverlayKey
import com.android.compose.animation.scene.SceneKey
import com.android.internal.logging.uiEventLoggerFake
import com.android.systemui.Flags.FLAG_DUAL_SHADE
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
import com.android.systemui.bouncer.data.repository.fakeKeyguardBouncerRepository
import com.android.systemui.bouncer.data.repository.keyguardBouncerRepository
import com.android.systemui.bouncer.domain.interactor.alternateBouncerInteractor
import com.android.systemui.bouncer.domain.interactor.simBouncerInteractor
import com.android.systemui.bouncer.shared.logging.BouncerUiEvent
import com.android.systemui.dreams.dreamStartable
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.data.repository.biometricSettingsRepository
import com.android.systemui.keyguard.data.repository.deviceEntryFingerprintAuthRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.data.repository.fakeTrustRepository
import com.android.systemui.keyguard.data.repository.keyguardOcclusionRepository
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.domain.interactor.biometricUnlockInteractor
import com.android.systemui.keyguard.domain.interactor.keyguardDismissActionInteractor
import com.android.systemui.keyguard.domain.interactor.keyguardInteractor
import com.android.systemui.keyguard.shared.DriveDreamStateFromOcclusion
import com.android.systemui.keyguard.shared.model.BiometricUnlockSource
import com.android.systemui.keyguard.shared.model.DismissAction
import com.android.systemui.keyguard.shared.model.DozeStateModel
import com.android.systemui.keyguard.shared.model.DozeTransitionModel
import com.android.systemui.keyguard.shared.model.KeyguardDone
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.KeyguardTransitionKeys.WithAnimationOverLockscreen
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.advanceTimeBy
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.collectValues
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useStandardTestDispatcher
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.plugins.statusbar.statusBarStateController
import com.android.systemui.scene.data.model.SceneStack
import com.android.systemui.scene.data.model.asIterable
import com.android.systemui.scene.domain.interactor.sceneBackInteractor
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.domain.startable.sceneContainerStartable
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.shared.model.fakeSceneDataSource
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.domain.interactor.shadeInteractor
import com.android.systemui.statusbar.phone.BiometricUnlockController
import com.android.systemui.statusbar.pipeline.mobile.data.repository.fakeMobileConnectionsRepository
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
@EnableSceneContainer
@TestableLooper.RunWithLooper
class DeviceEntryInteractorTest(flags: FlagsParameterization) : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.trustRepository by Kosmos.Fixture { fakeTrustRepository }
    private val Kosmos.underTest: DeviceEntryInteractor by Kosmos.Fixture { deviceEntryInteractor }

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

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
            fakeKeyguardRepository.setKeyguardEnabled(false)

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
            biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
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
            fakeKeyguardRepository.setKeyguardEnabled(true)
            switchToScene(Scenes.Lockscreen)
            showBouncer()

            val isDeviceEntered by collectLastValue(underTest.isDeviceEntered)
            assertThat(isDeviceEntered).isFalse()
        }

    @Test
    fun isDeviceEntered_onDreamAfterDeviceLock_isFalse() =
        kosmos.runTest {
            dreamStartable.start()

            // Unlock device and go to home screen.
            fakeAuthenticationRepository.setAuthenticationMethod(Pattern)
            biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )

            // Verify device is entered.
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            val isDeviceEntered by collectLastValue(underTest.isDeviceEntered)
            assertThat(currentScene).isEqualTo(Scenes.Gone)
            assertThat(currentOverlays).isEmpty()
            assertThat(isDeviceEntered).isTrue()

            // Dream starts, and device is unlocked.
            val dreamTaskInfo =
                RunningTaskInfo().apply {
                    topActivityType = WindowConfiguration.ACTIVITY_TYPE_DREAM
                }
            keyguardOcclusionRepository.setOccludedFromRemoteAnimation(true, dreamTaskInfo)
            if (!DriveDreamStateFromOcclusion.isEnabled) {
                keyguardInteractor.setDreaming(true)
                advanceTimeBy(DREAMING_DELAY_MS)
            }

            // Verify device remains entered.
            assertThat(currentScene).isEqualTo(Scenes.Dream)
            assertThat(currentOverlays).isEmpty()
            assertThat(isDeviceEntered).isTrue()

            // Device locks due to timeout.
            underTest.lockNow("timeout")

            // Verify device is no longer entered.
            assertThat(currentScene).isEqualTo(Scenes.Dream)
            assertThat(currentOverlays).isEmpty()
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
            fakeKeyguardRepository.setKeyguardEnabled(true)
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

            assertThat(canSwipeToEnter).isTrue()
        }

    @Test
    fun canSwipeToEnter_whenAuthenticatedByFace_isTrue() =
        kosmos.runTest {
            val canSwipeToEnter by collectLastValue(underTest.canSwipeToEnter)
            fakeAuthenticationRepository.setAuthenticationMethod(Password)
            switchToScene(Scenes.Lockscreen)
            assertThat(canSwipeToEnter).isFalse()

            biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_NONE_UNLOCKED,
                biometricUnlockSource = BiometricUnlockSource.FACE_SENSOR,
            )
            trustRepository.setCurrentUserTrusted(false)

            assertThat(canSwipeToEnter).isTrue()
        }

    @Test
    fun canSwipeToEnter_whenNotAuthenticatedByFace_isFalse() =
        kosmos.runTest {
            val canSwipeToEnter by collectLastValue(underTest.canSwipeToEnter)
            fakeAuthenticationRepository.setAuthenticationMethod(Password)
            switchToScene(Scenes.Lockscreen)
            assertThat(canSwipeToEnter).isFalse()

            // MODE_ONLY_WAKE can occur if unlocking isn't allowed:
            biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_ONLY_WAKE,
                biometricUnlockSource = BiometricUnlockSource.FACE_SENSOR,
            )
            trustRepository.setCurrentUserTrusted(false)

            assertThat(canSwipeToEnter).isFalse()
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
            biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            fakeAuthenticationRepository.setAuthenticationMethod(Password)

            assertThat(underTest.isAuthenticationRequired()).isFalse()
        }

    @Test
    fun isAuthenticationRequired_unlockedAndNotSecured_false() =
        kosmos.runTest {
            biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            fakeAuthenticationRepository.setAuthenticationMethod(None)

            assertThat(underTest.isAuthenticationRequired()).isFalse()
        }

    @Test
    fun attemptDeviceEntry_notLocked_switchesToGoneScene() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            switchToScene(Scenes.Lockscreen)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )

            underTest.attemptDeviceEntry("test")

            assertThat(currentScene).isEqualTo(Scenes.Gone)
        }

    @Test
    fun attemptDeviceEntry_notLocked_replacesLockscreenWithGoneInTheBackStack() =
        kosmos.runTest {
            enableSingleShade()
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val backStack by collectLastValue(sceneBackInteractor.backStack)
            switchToScene(Scenes.Lockscreen)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            switchToScene(Scenes.Shade)
            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(backStack!!.asIterable()).containsExactly(Scenes.Lockscreen)

            fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FACE_SENSOR,
            )

            underTest.attemptDeviceEntry("test")

            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(backStack!!.asIterable()).containsExactly(Scenes.Gone)
        }

    @Test
    fun attemptDeviceEntry_authMethodNotSecure_switchesToGoneScene() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            switchToScene(Scenes.Lockscreen)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            fakeAuthenticationRepository.setAuthenticationMethod(None)

            underTest.attemptDeviceEntry("test")

            assertThat(currentScene).isEqualTo(Scenes.Gone)
        }

    @Test
    fun attemptDeviceEntry_authMethodNotSecure_replacesLockscreenWithGoneInTheBackStack() =
        kosmos.runTest {
            enableSingleShade()
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val backStack by collectLastValue(sceneBackInteractor.backStack)
            switchToScene(Scenes.Lockscreen)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            switchToScene(Scenes.Shade)
            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(backStack!!.asIterable()).containsExactly(Scenes.Lockscreen)

            fakeAuthenticationRepository.setAuthenticationMethod(None)

            underTest.attemptDeviceEntry("test")

            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(backStack!!.asIterable()).containsExactly(Scenes.Gone)
        }

    @Test
    fun attemptDeviceEntry_authMethodNotSecure_switchesToGoneSceneWhenOnCommunal() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val backStack by collectLastValue(sceneBackInteractor.backStack)
            switchToScene(Scenes.Lockscreen)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            switchToScene(Scenes.Communal)
            assertThat(currentScene).isEqualTo(Scenes.Communal)
            assertThat(backStack!!.asIterable()).containsExactly(Scenes.Lockscreen)

            fakeAuthenticationRepository.setAuthenticationMethod(None)

            underTest.attemptDeviceEntry("test")

            assertThat(currentScene).isEqualTo(Scenes.Gone)
            assertThat(backStack!!.asIterable().toList()).isEmpty()
        }

    @Test
    fun attemptDeviceEntry_dismissActionAnimates_runsTransitionToGone() =
        kosmos.runTest {
            enableSingleShade()
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val backStack by collectLastValue(sceneBackInteractor.backStack)
            switchToScene(Scenes.Lockscreen)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            switchToScene(Scenes.Shade)
            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(backStack!!.asIterable()).containsExactly(Scenes.Lockscreen)

            // Since the dismiss action wants to animate over the lockscreen, attempting device
            // entry should animate Lockscreen -> Gone instead of replacing Lockscreen in the back
            // stack with no animation.
            kosmos.keyguardDismissActionInteractor.setDismissAction(
                DismissAction.RunAfterKeyguardGone(
                    dismissAction = { KeyguardDone.LATER },
                    onCancelAction = {},
                    message = "",
                    willAnimateOnLockscreen = true,
                )
            )
            runCurrent()
            fakeAuthenticationRepository.setAuthenticationMethod(None)

            underTest.attemptDeviceEntry("test")
            runCurrent()

            assertThat(currentScene).isEqualTo(Scenes.Gone)
            assertThat(backStack!!.asIterable().toList()).isEqualTo(emptyList<SceneStack>())
        }

    @Test
    fun attemptDeviceEntrySingleShade_leaveOpenOnKeyguardHide_expandsNotificationShade() =
        kosmos.runTest {
            // GIVEN single shade
            enableSingleShade()
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val backStack by collectLastValue(sceneBackInteractor.backStack)

            // GIVEN authentication is not required
            fakeAuthenticationRepository.setAuthenticationMethod(None)

            // GIVEN keyguard showing
            switchToScene(Scenes.Lockscreen)
            runCurrent()

            // GIVEN setLeaveOpenOnKeyguardHide=true
            statusBarStateController.setLeaveOpenOnKeyguardHide(true)
            runCurrent()

            // WHEN attemptDeviceEntry
            underTest.attemptDeviceEntry("test")
            runCurrent()

            // THEN shade shows and the backstack is Gone (not Lockscreen)
            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(backStack!!.asIterable()).contains(Scenes.Gone)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun attemptDeviceEntryDualShade_leaveOpenOnKeyguardHide_expandsNotificationShade() =
        kosmos.runTest {
            // GIVEN dual shade
            enableDualShade()
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val overlays by collectLastValue(sceneInteractor.currentOverlays)

            // GIVEN authentication is not required
            fakeAuthenticationRepository.setAuthenticationMethod(None)

            // GIVEN keyguard showing
            switchToScene(Scenes.Lockscreen)
            runCurrent()

            // GIVEN setLeaveOpenOnKeyguardHide=true
            statusBarStateController.setLeaveOpenOnKeyguardHide(true)
            runCurrent()

            // WHEN attemptDeviceEntry
            underTest.attemptDeviceEntry("test")
            runCurrent()

            // THEN shade shows on the Gone Scene
            assertThat(currentScene).isEqualTo(Scenes.Gone)
            assertThat(overlays).contains(Overlays.NotificationsShade)
        }

    @Test
    fun attemptDeviceEntrySingleShade_leaveOpenOnKeyguardHide_replacesLockscreenWithGone() =
        kosmos.runTest {
            enableSingleShade()
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val backStack by collectLastValue(sceneBackInteractor.backStack)

            // GIVEN authentication is not required
            fakeAuthenticationRepository.setAuthenticationMethod(None)

            // GIVEN the shade showing over keyguard
            switchToScene(Scenes.Lockscreen)
            switchToScene(Scenes.Shade)
            assertThat(backStack!!.asIterable()).containsExactly(Scenes.Lockscreen)

            // GIVEN setLeaveOpenOnKeyguarddHide=true
            statusBarStateController.setLeaveOpenOnKeyguardHide(true)
            runCurrent()

            // WHEN attempt device entry
            underTest.attemptDeviceEntry("test")
            runCurrent()

            // THEN shade continues to show and the backstack is Gone (not Lockscreen)
            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(backStack!!.asIterable()).containsExactly(Scenes.Gone)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun attemptDeviceEntryDualShade_leaveOpenOnKeyguardHide() =
        kosmos.runTest {
            enableDualShade()
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val overlays by collectLastValue(sceneInteractor.currentOverlays)

            // GIVEN authentication is not required
            fakeAuthenticationRepository.setAuthenticationMethod(None)

            // GIVEN the shade showing over keyguard
            switchToScene(Scenes.Lockscreen)
            showOverlay(Overlays.NotificationsShade)

            // GIVEN setLeaveOpenOnKeyguarddHide=true
            statusBarStateController.setLeaveOpenOnKeyguardHide(true)
            runCurrent()

            // WHEN attempt device entry
            underTest.attemptDeviceEntry("test")
            runCurrent()

            // THEN shade continues to show on Scenes.Gone
            assertThat(currentScene).isEqualTo(Scenes.Gone)
            assertThat(overlays).contains(Overlays.NotificationsShade)
        }

    @Test
    fun attemptDeviceEntry_authMethodSwipe_switchesToGoneScene() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            switchToScene(Scenes.Lockscreen)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            fakeKeyguardRepository.setKeyguardEnabled(true)
            fakeAuthenticationRepository.setAuthenticationMethod(None)

            underTest.attemptDeviceEntry("test")

            assertThat(currentScene).isEqualTo(Scenes.Gone)
        }

    @Test
    fun attemptDeviceEntry_authMethodSwipe_replacesLockscreenWithGoneInTheBackStack() =
        kosmos.runTest {
            enableSingleShade()
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val backStack by collectLastValue(sceneBackInteractor.backStack)
            switchToScene(Scenes.Lockscreen)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            switchToScene(Scenes.Shade)
            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(backStack!!.asIterable()).containsExactly(Scenes.Lockscreen)

            fakeKeyguardRepository.setKeyguardEnabled(true)
            fakeAuthenticationRepository.setAuthenticationMethod(None)

            underTest.attemptDeviceEntry("test")

            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(backStack!!.asIterable()).containsExactly(Scenes.Gone)
        }

    @Test
    fun attemptDeviceEntry_noAlternateBouncer_showsBouncerOverlay() =
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
    fun attemptDeviceEntry_showsAlternateBouncer_staysOnLockscreenScene_noBouncerOverlay() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            switchToScene(Scenes.Lockscreen)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            givenCanShowAlternateBouncer()

            underTest.attemptDeviceEntry(
                loggingReason = "test",
                skipShowingAlternateBouncer = false,
            )

            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).isEmpty()
        }

    @Test
    fun attemptDeviceEntry_skipShowingAlternateBouncer_showsBouncerOverlay() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            switchToScene(Scenes.Lockscreen)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            givenCanShowAlternateBouncer()

            underTest.attemptDeviceEntry(loggingReason = "test", skipShowingAlternateBouncer = true)

            assertThat(currentOverlays).contains(Overlays.Bouncer)
        }

    @Test
    fun successfulAuthenticationChallengeAttempt_updatesIsUnlockedState() =
        kosmos.runTest {
            val isUnlocked by collectLastValue(underTest.isUnlocked)
            fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            fakeKeyguardRepository.setKeyguardEnabled(true)
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
            statusBarStateController.setLeaveOpenOnKeyguardHide(true)
            showBouncer()
            assertThat(currentOverlays).contains(Overlays.Bouncer)

            assertThat(isDeviceEntered).isFalse()
            assertThat(isDeviceEnteredDirectly).isFalse()
            // Authenticate with PIN to unlock and dismiss the lockscreen:
            authenticationInteractor.authenticate(FakeAuthenticationRepository.DEFAULT_PIN)

            assertThat(isDeviceEntered).isTrue()
            assertThat(isDeviceEnteredDirectly).isFalse()
            assertThat(currentScene).isEqualTo(Scenes.Shade)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun isDeviceEntered_unlockedWhileOnDualShade_emitsTrue() =
        kosmos.runTest {
            enableDualShade()
            val isDeviceEntered by collectLastValue(underTest.isDeviceEntered)
            val isDeviceEnteredDirectly by collectLastValue(underTest.isDeviceEnteredDirectly)
            assertThat(isDeviceEntered).isFalse()
            assertThat(isDeviceEnteredDirectly).isFalse()
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            // Navigate to shade and bouncer:
            showOverlay(Overlays.NotificationsShade)
            assertThat(currentOverlays).contains(Overlays.NotificationsShade)
            // Simulating a "leave it open when the keyguard is hidden" which means the bouncer will
            // be shown and successful authentication should take the user back to where they are,
            // the shade overlay.
            statusBarStateController.setLeaveOpenOnKeyguardHide(true)
            showOverlay(Overlays.Bouncer)
            assertThat(currentOverlays).contains(Overlays.Bouncer)

            assertThat(isDeviceEntered).isFalse()
            assertThat(isDeviceEnteredDirectly).isFalse()
            // Authenticate with PIN to unlock and dismiss the lockscreen:
            authenticationInteractor.authenticate(FakeAuthenticationRepository.DEFAULT_PIN)

            assertThat(isDeviceEntered).isTrue()
            assertThat(currentScene).isEqualTo(Scenes.Gone)
            assertThat(currentOverlays).contains(Overlays.NotificationsShade)
        }

    @Test
    fun isDeviceEntered_goneToCommunalAndLockDevice_emitsFalse() =
        kosmos.runTest {
            val isDeviceEntered by collectLastValue(underTest.isDeviceEntered)
            val isDeviceEnteredDirectly by collectLastValue(underTest.isDeviceEnteredDirectly)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            assertThat(isDeviceEntered).isFalse()
            assertThat(isDeviceEnteredDirectly).isFalse()
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            // Navigate to communal
            switchToScene(Scenes.Communal)
            assertThat(currentScene).isEqualTo(Scenes.Communal)

            // Unlock and verify device entered
            authenticationInteractor.authenticate(FakeAuthenticationRepository.DEFAULT_PIN)
            switchToScene(Scenes.Gone)
            assertThat(currentScene).isEqualTo(Scenes.Gone)
            assertThat(isDeviceEntered).isTrue()
            assertThat(isDeviceEnteredDirectly).isTrue()

            // Return to communal, lock device, and verify device is not entered
            switchToScene(Scenes.Communal)
            underTest.lockNow("test")
            assertThat(isDeviceEntered).isFalse()
            assertThat(isDeviceEnteredDirectly).isFalse()
        }

    @Test
    fun lockNow_authMethodSecure_locksAndSwitchesToLockscreen() =
        kosmos.runTest {
            val isUnlocked by collectLastValue(underTest.isUnlocked)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            switchToScene(Scenes.Gone)
            assertThat(isUnlocked).isTrue()
            assertThat(currentScene).isEqualTo(Scenes.Gone)

            underTest.lockNow("test")

            assertThat(isUnlocked).isFalse()
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
        }

    @Test
    fun lockNow_swipeAuthMethod_switchesToLockscreen() =
        kosmos.runTest {
            val isUnlocked by collectLastValue(underTest.isUnlocked)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            setupSwipeDeviceEntryMethod() // sets auth to None and lockscreen enabled
            switchToScene(Scenes.Gone)
            assertThat(isUnlocked).isTrue()
            assertThat(currentScene).isEqualTo(Scenes.Gone)

            underTest.lockNow("test")

            assertThat(isUnlocked).isTrue()
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun lockNow_swipeAuthMethod_whenDreamingRemainsDreamingAndAddsLockscreenToBackstack() =
        kosmos.runTest {
            val isUnlocked by collectLastValue(underTest.isUnlocked)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val backStack by collectLastValue(sceneBackInteractor.backStack)
            setupSwipeDeviceEntryMethod() // sets auth to None and lockscreen enabled
            switchToScene(Scenes.Gone)

            // Simulate dreaming
            val dreamTaskInfo =
                RunningTaskInfo().apply {
                    topActivityType = WindowConfiguration.ACTIVITY_TYPE_DREAM
                }
            keyguardOcclusionRepository.setOccludedFromRemoteAnimation(true, dreamTaskInfo)

            if (!DriveDreamStateFromOcclusion.isEnabled) {
                fakeKeyguardRepository.setDreaming(true)
                fakeKeyguardRepository.setDreamingWithOverlay(true)
                fakeKeyguardRepository.setDozeTransitionModel(
                    DozeTransitionModel(from = DozeStateModel.DOZE, to = DozeStateModel.FINISH)
                )
                testScope.advanceTimeBy(KeyguardInteractor.IS_DREAMING_NOT_DOZING_DELAY_MS + 100L)
            }
            switchToScene(Scenes.Dream)
            assertThat(isUnlocked).isTrue()
            assertThat(currentScene).isEqualTo(Scenes.Dream)

            underTest.lockNow("test")

            assertThat(isUnlocked).isTrue()
            assertThat(currentScene).isEqualTo(Scenes.Dream)
            assertThat(backStack!!.asIterable()).containsExactly(Scenes.Lockscreen)
        }

    @Test
    fun lockNow_lockscreenDisabled_doesNothing() =
        kosmos.runTest {
            val isUnlocked by collectLastValue(underTest.isUnlocked)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            fakeKeyguardRepository.setKeyguardEnabled(false)
            fakeAuthenticationRepository.setAuthenticationMethod(None)
            switchToScene(Scenes.Gone)
            assertThat(isUnlocked).isTrue()
            assertThat(currentScene).isEqualTo(Scenes.Gone)

            underTest.lockNow("test")

            assertThat(isUnlocked).isTrue()
            assertThat(currentScene).isEqualTo(Scenes.Gone)
        }

    @Test
    fun deviceEntered_extendUnlockMetricLogged() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val isDeviceEntered by collectLastValue(underTest.isDeviceEntered)

            // GIVEN secure lockscreen
            fakeAuthenticationRepository.setAuthenticationMethod(Pattern)
            switchToScene(Scenes.Lockscreen)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(isDeviceEntered).isFalse()

            underTest.handleDeviceEntryMetricsLogging()

            // WHEN the user is trusted (ie: extended unlock)
            //   AND there's an attempt to enter the device
            fakeTrustRepository.setCurrentUserTrusted(true)
            underTest.attemptDeviceEntry("test")

            // THEN device is entered
            assertThat(currentScene).isEqualTo(Scenes.Gone)
            assertThat(isDeviceEntered).isTrue()

            // THEN metric logged
            assertThat(uiEventLoggerFake.numLogs()).isAtLeast(1)
            assertThat(uiEventLoggerFake[uiEventLoggerFake.numLogs() - 1].eventId)
                .isEqualTo(BouncerUiEvent.BOUNCER_DISMISS_EXTENDED_ACCESS.id)
        }

    @Test
    fun handleDeviceUnlockStatusChange_transitionsToGoneWithAnimation_fromShadeBouncer() =
        kosmos.runTest {
            enableSingleShade()

            val isUnlocked by
                collectLastValue(deviceUnlockedInteractor.deviceUnlockStatus.map { it.isUnlocked })
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)

            fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            switchToScene(Scenes.Lockscreen)
            assertThat(isUnlocked).isFalse()
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            switchToScene(Scenes.Shade)
            assertThat(currentScene).isEqualTo(Scenes.Shade)
            showBouncer()
            assertThat(currentOverlays).contains(Overlays.Bouncer)

            // Unlock device with a dismiss action that wants to animate over LS.
            kosmos.keyguardDismissActionInteractor.setDismissAction(
                DismissAction.RunAfterKeyguardGone(
                    dismissAction = { KeyguardDone.LATER },
                    onCancelAction = {},
                    message = "",
                    willAnimateOnLockscreen = true,
                )
            )

            // Authenticate with PIN to unlock and dismiss the lockscreen:
            authenticationInteractor.authenticate(FakeAuthenticationRepository.DEFAULT_PIN)

            assertThat(isUnlocked).isTrue()
            assertThat(currentScene).isEqualTo(Scenes.Gone)
        }

    @Test
    fun handleDeviceUnlockStatusChange_transitionsToGoneWithAnimation_fromBouncer() =
        kosmos.runTest {
            val isUnlocked by
                collectLastValue(deviceUnlockedInteractor.deviceUnlockStatus.map { it.isUnlocked })
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)

            fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            switchToScene(Scenes.Lockscreen)
            assertThat(isUnlocked).isFalse()
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            showBouncer()
            assertThat(currentOverlays).contains(Overlays.Bouncer)

            // Unlock device with a dismiss action that wants to animate over LS.
            kosmos.keyguardDismissActionInteractor.setDismissAction(
                DismissAction.RunAfterKeyguardGone(
                    dismissAction = { KeyguardDone.LATER },
                    onCancelAction = {},
                    message = "",
                    willAnimateOnLockscreen = true,
                )
            )

            // Authenticate with PIN to unlock and dismiss the lockscreen:
            authenticationInteractor.authenticate(FakeAuthenticationRepository.DEFAULT_PIN)

            assertThat(isUnlocked).isTrue()
            assertThat(fakeSceneDataSource.currentSceneTransitionKey)
                .isEqualTo(WithAnimationOverLockscreen)
            assertThat(currentScene).isEqualTo(Scenes.Gone)
        }

    @Test
    fun handleDeviceUnlockStatusChange_enterDevice_whenUnlockedOnAlternateBouncerOnShadeChange() =
        kosmos.runTest {
            enableSingleShade()

            val isUnlocked by
                collectLastValue(deviceUnlockedInteractor.deviceUnlockStatus.map { it.isUnlocked })
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val backStack by collectLastValue(sceneBackInteractor.backStack)
            val isAlternateBouncerVisible by collectLastValue(alternateBouncerInteractor.isVisible)
            val isAnyShadeFullyExpanded by collectLastValue(shadeInteractor.isAnyFullyExpanded)

            switchToScene(Scenes.Lockscreen)
            fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            assertThat(isUnlocked).isFalse()
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(isAlternateBouncerVisible).isFalse()

            // Change to shade.
            switchToScene(Scenes.Shade)
            assertThat(isUnlocked).isFalse()
            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(isAnyShadeFullyExpanded).isTrue()
            assertThat(backStack?.asIterable()?.first()).isEqualTo(Scenes.Lockscreen)

            // Show the alternate bouncer.
            alternateBouncerInteractor.forceShow()
            statusBarStateController.setLeaveOpenOnKeyguardHide(true) // leave shade open
            assertThat(isUnlocked).isFalse()
            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(isAnyShadeFullyExpanded).isTrue()
            assertThat(backStack?.asIterable()?.first()).isEqualTo(Scenes.Lockscreen)
            assertThat(isAlternateBouncerVisible).isTrue()

            // Trigger a fingerprint unlock.
            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )

            assertThat(isUnlocked).isTrue()
            assertThat(isAlternateBouncerVisible).isFalse()
            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(backStack?.asIterable()?.first()).isEqualTo(Scenes.Gone)
        }

    @Test
    fun handleDeviceUnlockStatusChange_switchFromCommunalToGone_whenDeviceUnlockedOnPrimaryBouncerChange() =
        kosmos.runTest {
            val currentSceneKey by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            switchToScene(Scenes.Communal)
            fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            showBouncer()
            assertThat(currentSceneKey).isEqualTo(Scenes.Communal)
            assertThat(currentOverlays).contains(Overlays.Bouncer)

            biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )

            assertThat(currentSceneKey).isEqualTo(Scenes.Gone)
            assertThat(currentOverlays).doesNotContain(Overlays.Bouncer)
        }

    @Test
    fun handleDeviceUnlockStatusChange_switchFromCommunalToGone_whenUnlockedOnAlternateBouncer() =
        kosmos.runTest {
            val alternateBouncerVisible by
                collectLastValue(keyguardBouncerRepository.alternateBouncerVisible)
            val currentSceneKey by collectLastValue(sceneInteractor.currentScene)

            fakeKeyguardBouncerRepository.setAlternateVisible(true)
            assertThat(alternateBouncerVisible).isTrue()
            switchToScene(Scenes.Communal)
            fakeAuthenticationRepository.setAuthenticationMethod(Pin)

            biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            assertThat(currentSceneKey).isEqualTo(Scenes.Gone)
            assertThat(alternateBouncerVisible).isFalse()
        }

    @Test
    fun handleDeviceUnlockStatusChange_switchFromCommunalToGone_whenUnlocked() =
        kosmos.runTest {
            val currentSceneKey by collectLastValue(sceneInteractor.currentScene)
            switchToScene(Scenes.Communal)
            assertThat(currentSceneKey).isEqualTo(Scenes.Communal)

            biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            assertThat(currentSceneKey).isEqualTo(Scenes.Gone)
        }

    @Test
    fun handleDeviceUnlockStatusChange_switchToGoneWhenDeviceIsUnlockedAndUserIsOnBouncerWithBypassDisabled() =
        kosmos.runTest {
            val currentSceneKey by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)

            switchToScene(Scenes.Lockscreen)
            fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            showBouncer()
            assertThat(currentSceneKey).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).contains(Overlays.Bouncer)

            // Authenticate using a passive auth method like face auth while bypass is disabled.
            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_NONE_UNLOCKED,
                biometricUnlockSource = BiometricUnlockSource.FACE_SENSOR,
            )
            assertThat(currentSceneKey).isEqualTo(Scenes.Gone)
            assertThat(currentOverlays).doesNotContain(Overlays.Bouncer)
        }

    @Test
    fun handleDeviceUnlockStatusChangeFromBiometric() =
        kosmos.runTest {
            val currentSceneKey by collectLastValue(sceneInteractor.currentScene)
            switchToScene(Scenes.Lockscreen)
            fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            assertThat(currentSceneKey).isEqualTo(Scenes.Lockscreen)

            // Authenticate using a biometric that should dismiss the keyguard.
            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_WAKE_AND_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            assertThat(currentSceneKey).isEqualTo(Scenes.Gone)
        }

    @Test
    fun handleDeviceEntryFromBiometricWhenAlreadyUnlocked() =
        kosmos.runTest {
            val currentSceneKey by collectLastValue(sceneInteractor.currentScene)
            fakeAuthenticationRepository.setAuthenticationMethod(Pin)

            // Authenticate using a biometric that should dismiss the keyguard.
            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_WAKE_AND_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            // WHEN on the lockscreen in an unlocked state
            switchToScene(Scenes.Lockscreen)
            assertThat(currentSceneKey).isEqualTo(Scenes.Lockscreen)
            // and biometric unlock state is reset
            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_NONE,
                biometricUnlockSource = null,
            )

            // WHEN authenticate using the same biometric that should dismiss the keyguard again
            kosmos.biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_WAKE_AND_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )

            // THEN device will transition to Gone
            assertThat(currentSceneKey).isEqualTo(Scenes.Gone)
        }

    @Test
    fun attemptDeviceEntry_whenOccludedAndSimSecure_doesNotShowBouncer() =
        kosmos.runTest {
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)

            // GIVEN no security setup
            fakeAuthenticationRepository.setAuthenticationMethod(None)

            // GIVEN a SIM is secure
            kosmos.fakeMobileConnectionsRepository.isAnySimSecure.value = true

            // GIVEN the device is occluded
            switchToScene(Scenes.Occluded)

            assertThat(sceneInteractor.currentScene.value).isEqualTo(Scenes.Occluded)
            assertThat(kosmos.simBouncerInteractor.isAnySimSecure.value).isTrue()

            // WHEN attempting device entry
            underTest.attemptDeviceEntry("test")

            // THEN bouncer overlay is NOT shown
            assertThat(currentOverlays).doesNotContain(Overlays.Bouncer)
        }

    @Test
    fun attemptDeviceEntry_whenOccludedAndSimUnlocked_showsBouncer() =
        kosmos.runTest {
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)

            // GIVEN authentication is required
            fakeAuthenticationRepository.setAuthenticationMethod(Pin)

            // GIVEN the device is occluded
            switchToScene(Scenes.Occluded)

            // GIVEN SIM is unlocked
            kosmos.fakeMobileConnectionsRepository.isAnySimSecure.value = false

            // WHEN attempting device entry
            underTest.attemptDeviceEntry("test")

            // THEN bouncer overlay IS shown
            assertThat(currentOverlays).contains(Overlays.Bouncer)
        }

    @Test
    fun attemptDeviceEntry_whenNotOccludedAndSimSecure_showsBouncer() =
        kosmos.runTest {
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)

            // GIVEN authentication is required
            fakeAuthenticationRepository.setAuthenticationMethod(Pin)

            // GIVEN the device is NOT occluded
            switchToScene(Scenes.Lockscreen)

            // GIVEN a SIM is secure
            kosmos.fakeMobileConnectionsRepository.isAnySimSecure.value = true

            // WHEN attempting device entry
            underTest.attemptDeviceEntry("test")

            // THEN bouncer overlay IS shown
            assertThat(currentOverlays).contains(Overlays.Bouncer)
        }

    private fun Kosmos.setupSwipeDeviceEntryMethod() {
        fakeAuthenticationRepository.setAuthenticationMethod(None)
        fakeKeyguardRepository.setKeyguardEnabled(true)
    }

    private fun Kosmos.switchToScene(sceneKey: SceneKey) {
        sceneInteractor.changeScene(sceneKey, "reason")
        sceneInteractor.setTransitionState(flowOf(ObservableTransitionState.Idle(sceneKey)))
    }

    private fun Kosmos.showBouncer() {
        showOverlay(Overlays.Bouncer)
    }

    private fun Kosmos.showOverlay(overlay: OverlayKey) {
        sceneInteractor.showOverlay(overlay, "reason")
        sceneInteractor.setTransitionState(
            flowOf(
                ObservableTransitionState.Idle(sceneInteractor.currentScene.value, setOf(overlay))
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

    companion object {
        // A delay to move past the initial dreaming delay.
        private const val DREAMING_DELAY_MS =
            KeyguardInteractor.IS_DREAMING_NOT_DOZING_DELAY_MS + 100L

        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf(FLAG_DRIVE_DREAM_STATE_FROM_OCCLUSION)
        }
    }
}
