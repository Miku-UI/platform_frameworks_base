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

import android.content.pm.UserInfo
import android.hardware.biometrics.BiometricFaceConstants
import android.hardware.biometrics.BiometricSourceType
import android.os.PowerManager
import android.platform.test.annotations.EnableFlags
import android.security.Flags.FLAG_SECURE_LOCK_DEVICE
import android.service.dreams.Flags.FLAG_DREAMS_V2
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.keyguard.keyguardUpdateMonitor
import com.android.keyguard.trustManager
import com.android.systemui.SysuiTestCase
import com.android.systemui.biometrics.data.repository.CameraInfo
import com.android.systemui.biometrics.data.repository.facePropertyRepository
import com.android.systemui.biometrics.shared.model.FaceSensorInfo
import com.android.systemui.biometrics.shared.model.LockoutMode
import com.android.systemui.biometrics.shared.model.SensorStrength
import com.android.systemui.bouncer.data.repository.fakeKeyguardBouncerRepository
import com.android.systemui.bouncer.domain.interactor.alternateBouncerInteractor
import com.android.systemui.bouncer.domain.interactor.primaryBouncerInteractor
import com.android.systemui.camera.domain.interactor.cameraSensorPrivacyInteractor
import com.android.systemui.deviceentry.data.repository.fakeFaceWakeUpTriggersConfig
import com.android.systemui.deviceentry.shared.FaceAuthUiEvent
import com.android.systemui.deviceentry.shared.model.ErrorFaceAuthenticationStatus
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.data.repository.fakeBiometricSettingsRepository
import com.android.systemui.keyguard.data.repository.fakeDeviceEntryFaceAuthRepository
import com.android.systemui.keyguard.data.repository.fakeDeviceEntryFingerprintAuthRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.domain.interactor.keyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.TransitionState
import com.android.systemui.keyguard.shared.model.TransitionStep
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.log.FaceAuthenticationLogger
import com.android.systemui.log.logcatLogBuffer
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAwakeForTest
import com.android.systemui.power.domain.interactor.powerInteractor
import com.android.systemui.power.shared.model.WakeSleepReason
import com.android.systemui.scene.data.repository.ShowOverlay
import com.android.systemui.scene.data.repository.Transition
import com.android.systemui.scene.data.repository.setSceneTransition
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.statusbar.pipeline.mobile.data.repository.fakeMobileConnectionsRepository
import com.android.systemui.statusbar.pipeline.mobile.data.repository.mobileConnectionsRepository
import com.android.systemui.testKosmos
import com.android.systemui.user.data.model.SelectionStatus
import com.android.systemui.user.data.repository.fakeUserRepository
import com.android.systemui.util.mockito.eq
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class DeviceEntryFaceAuthInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos()

    private lateinit var underTest: SystemUIDeviceEntryFaceAuthInteractor

    private val faceAuthRepository = kosmos.fakeDeviceEntryFaceAuthRepository

    @Before
    fun setup() {
        kosmos.fakeUserRepository.setUserInfos(listOf(primaryUser, secondaryUser))
        underTest =
            with(kosmos) {
                SystemUIDeviceEntryFaceAuthInteractor(
                    mContext,
                    testScope.backgroundScope,
                    testDispatcher,
                    faceAuthRepository,
                    { primaryBouncerInteractor },
                    alternateBouncerInteractor,
                    keyguardTransitionInteractor,
                    FaceAuthenticationLogger(logcatLogBuffer("faceAuthBuffer")),
                    keyguardUpdateMonitor,
                    deviceEntryFingerprintAuthInteractor,
                    fakeUserRepository,
                    facePropertyRepository,
                    fakeFaceWakeUpTriggersConfig,
                    powerInteractor,
                    fakeBiometricSettingsRepository,
                    trustManager,
                    { sceneInteractor },
                    deviceEntryFaceAuthStatusInteractor,
                    cameraSensorPrivacyInteractor,
                    mobileConnectionsRepository,
                )
            }
    }

    @Test
    fun faceAuthIsRequestedWhenLockscreenBecomesVisibleFromOffState() =
        kosmos.runTest {
            underTest.start()
            runCurrent()

            powerInteractor.setAwakeForTest(reason = PowerManager.WAKE_REASON_LID)
            fakeFaceWakeUpTriggersConfig.setTriggerFaceAuthOnWakeUpFrom(
                setOf(WakeSleepReason.LID.powerManagerWakeReason)
            )

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    KeyguardState.OFF,
                    KeyguardState.LOCKSCREEN,
                    transitionState = TransitionState.STARTED,
                )
            )

            runCurrent()
            assertThat(faceAuthRepository.runningAuthRequest.value)
                .isEqualTo(
                    Pair(FaceAuthUiEvent.FACE_AUTH_UPDATED_KEYGUARD_VISIBILITY_CHANGED, true)
                )
        }

    @Test
    @DisableSceneContainer
    @EnableFlags(FLAG_DREAMS_V2)
    fun faceAuthIsRequestedWhenTransitioningFromDreamToLockscreen() =
        kosmos.runTest {
            underTest.start()
            runCurrent()

            powerInteractor.setAwakeForTest(reason = PowerManager.WAKE_REASON_LID)
            fakeFaceWakeUpTriggersConfig.setTriggerFaceAuthOnWakeUpFrom(
                setOf(WakeSleepReason.LID.powerManagerWakeReason)
            )

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    KeyguardState.DREAMING,
                    KeyguardState.LOCKSCREEN,
                    transitionState = TransitionState.STARTED,
                )
            )

            runCurrent()
            assertThat(faceAuthRepository.runningAuthRequest.value)
                .isEqualTo(
                    Pair(FaceAuthUiEvent.FACE_AUTH_UPDATED_KEYGUARD_VISIBILITY_CHANGED, true)
                )
        }

    @Test
    @EnableSceneContainer
    @EnableFlags(FLAG_DREAMS_V2)
    fun faceAuthIsRequestedWhenTransitioningFromDreamToLockscreen_withSceneContainerEnabled() =
        kosmos.runTest {
            underTest.start()
            runCurrent()

            powerInteractor.setAwakeForTest(reason = PowerManager.WAKE_REASON_LID)
            fakeFaceWakeUpTriggersConfig.setTriggerFaceAuthOnWakeUpFrom(
                setOf(WakeSleepReason.LID.powerManagerWakeReason)
            )

            sceneInteractor.setTransitionState(
                MutableStateFlow(Transition(from = Scenes.Dream, to = Scenes.Lockscreen))
            )
            runCurrent()
            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    KeyguardState.UNDEFINED,
                    KeyguardState.LOCKSCREEN,
                    transitionState = TransitionState.STARTED,
                )
            )
            runCurrent()

            assertThat(faceAuthRepository.runningAuthRequest.value)
                .isEqualTo(
                    Pair(FaceAuthUiEvent.FACE_AUTH_UPDATED_KEYGUARD_VISIBILITY_CHANGED, true)
                )
        }

    @Test
    fun whenFaceIsLockedOutAndNonBypassAnyAttemptsToTriggerFaceAuthMustProvideLockoutError() =
        kosmos.runTest {
            underTest.start()
            val authenticationStatus = collectLastValue(underTest.authenticationStatus)
            faceAuthRepository.setLockedOut(true)
            kosmos.fakeDeviceEntryFaceAuthRepository.isBypassEnabled.value = false

            underTest.onDeviceLifted()

            val outputValue = authenticationStatus()!! as ErrorFaceAuthenticationStatus
            assertThat(outputValue.msgId)
                .isEqualTo(BiometricFaceConstants.FACE_ERROR_LOCKOUT_PERMANENT)
            assertThat(outputValue.msg).isEqualTo("Face Unlock unavailable")
            assertThat(faceAuthRepository.runningAuthRequest.value).isNull()
        }

    @Test
    fun whenFaceIsLockedOutAndBypass_runningAuthRequestNotNull() =
        kosmos.runTest {
            underTest.start()
            faceAuthRepository.setLockedOut(true)
            fakeDeviceEntryFaceAuthRepository.isBypassEnabled.value = true

            underTest.onDeviceLifted()

            assertThat(faceAuthRepository.runningAuthRequest.value).isNotNull()
        }

    @Test
    fun faceAuthIsRequestedWhenLockscreenBecomesVisibleFromAodState() =
        kosmos.runTest {
            underTest.start()
            runCurrent()

            powerInteractor.setAwakeForTest(reason = PowerManager.WAKE_REASON_LID)
            fakeFaceWakeUpTriggersConfig.setTriggerFaceAuthOnWakeUpFrom(
                setOf(WakeSleepReason.LID.powerManagerWakeReason)
            )

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    KeyguardState.AOD,
                    KeyguardState.LOCKSCREEN,
                    transitionState = TransitionState.STARTED,
                )
            )

            runCurrent()
            assertThat(faceAuthRepository.runningAuthRequest.value)
                .isEqualTo(
                    Pair(FaceAuthUiEvent.FACE_AUTH_UPDATED_KEYGUARD_VISIBILITY_CHANGED, true)
                )
        }

    @Test
    fun faceAuthIsNotRequestedWhenLockscreenBecomesVisibleDueToIgnoredWakeReasons() =
        kosmos.runTest {
            underTest.start()

            powerInteractor.setAwakeForTest(reason = PowerManager.WAKE_REASON_LIFT)
            fakeFaceWakeUpTriggersConfig.setTriggerFaceAuthOnWakeUpFrom(
                setOf(WakeSleepReason.LID.powerManagerWakeReason)
            )

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    KeyguardState.DOZING,
                    KeyguardState.LOCKSCREEN,
                    transitionState = TransitionState.STARTED,
                )
            )

            runCurrent()
            assertThat(faceAuthRepository.runningAuthRequest.value).isNull()
        }

    @Test
    fun faceAuthIsRequestedWhenLockscreenBecomesVisibleFromDozingState() =
        kosmos.runTest {
            underTest.start()
            runCurrent()

            powerInteractor.setAwakeForTest(reason = PowerManager.WAKE_REASON_LID)
            fakeFaceWakeUpTriggersConfig.setTriggerFaceAuthOnWakeUpFrom(
                setOf(WakeSleepReason.LID.powerManagerWakeReason)
            )

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    KeyguardState.DOZING,
                    KeyguardState.LOCKSCREEN,
                    transitionState = TransitionState.STARTED,
                )
            )

            runCurrent()
            assertThat(faceAuthRepository.runningAuthRequest.value)
                .isEqualTo(
                    Pair(FaceAuthUiEvent.FACE_AUTH_UPDATED_KEYGUARD_VISIBILITY_CHANGED, true)
                )
        }

    @Test
    @DisableSceneContainer
    fun faceAuthLockedOutStateIsUpdatedAfterUserSwitch() =
        kosmos.runTest {
            underTest.start()
            runCurrent()
            fakeBiometricSettingsRepository.setIsFaceAuthEnrolledAndEnabled(true)

            // User switching has started
            fakeUserRepository.setSelectedUserInfo(primaryUser, SelectionStatus.SELECTION_COMPLETE)
            fakeUserRepository.setSelectedUserInfo(
                primaryUser,
                SelectionStatus.SELECTION_IN_PROGRESS,
            )
            runCurrent()

            fakeKeyguardBouncerRepository.setPrimaryShow(true)
            // New user is not locked out.
            facePropertyRepository.setLockoutMode(secondaryUser.id, LockoutMode.NONE)
            fakeUserRepository.setSelectedUserInfo(
                secondaryUser,
                SelectionStatus.SELECTION_COMPLETE,
            )
            runCurrent()

            assertThat(faceAuthRepository.isLockedOut.value).isFalse()

            runCurrent()
            assertThat(faceAuthRepository.runningAuthRequest.value!!.first)
                .isEqualTo(FaceAuthUiEvent.FACE_AUTH_UPDATED_USER_SWITCHING)
            assertThat(faceAuthRepository.runningAuthRequest.value!!.second).isEqualTo(false)
        }

    @Test
    @EnableSceneContainer
    fun faceAuthLockedOutStateIsUpdatedAfterUserSwitch_withSceneContainerEnabled() =
        kosmos.runTest {
            underTest.start()
            runCurrent()
            fakeBiometricSettingsRepository.setIsFaceAuthEnrolledAndEnabled(true)
            faceAuthRepository.setLockedOut(true)

            // User switching has started
            fakeUserRepository.setSelectedUserInfo(primaryUser, SelectionStatus.SELECTION_COMPLETE)
            fakeUserRepository.setSelectedUserInfo(
                primaryUser,
                SelectionStatus.SELECTION_IN_PROGRESS,
            )
            runCurrent()

            sceneInteractor.snapToScene(Scenes.Lockscreen, "for-test")
            sceneInteractor.showOverlay(Overlays.Bouncer, "for-test")
            sceneInteractor.setTransitionState(
                flowOf(ObservableTransitionState.Idle(Scenes.Lockscreen, setOf(Overlays.Bouncer)))
            )
            runCurrent()

            // New user is not locked out.
            facePropertyRepository.setLockoutMode(secondaryUser.id, LockoutMode.NONE)
            fakeUserRepository.setSelectedUserInfo(
                secondaryUser,
                SelectionStatus.SELECTION_COMPLETE,
            )
            runCurrent()

            assertThat(faceAuthRepository.isLockedOut.value).isFalse()

            runCurrent()
            assertThat(faceAuthRepository.runningAuthRequest.value!!.first)
                .isEqualTo(FaceAuthUiEvent.FACE_AUTH_UPDATED_USER_SWITCHING)
            assertThat(faceAuthRepository.runningAuthRequest.value!!.second).isEqualTo(false)
        }

    @Test
    @EnableSceneContainer
    fun faceAuthIsRequestedWhenPrimaryBouncerIsVisible_withSceneContainerEnabled() =
        kosmos.runTest {
            underTest.start()

            sceneInteractor.snapToScene(Scenes.Lockscreen, "for-test")
            sceneInteractor.showOverlay(Overlays.Bouncer, "for-test")
            sceneInteractor.setTransitionState(
                flowOf(ObservableTransitionState.Idle(Scenes.Lockscreen, setOf(Overlays.Bouncer)))
            )
            runCurrent()

            assertThat(faceAuthRepository.runningAuthRequest.value)
                .isEqualTo(Pair(FaceAuthUiEvent.FACE_AUTH_UPDATED_PRIMARY_BOUNCER_SHOWN, false))
        }

    @Test
    @DisableSceneContainer
    fun faceAuthIsRequestedWhenPrimaryBouncerIsAboutToShow() =
        kosmos.runTest {
            underTest.start()

            fakeKeyguardBouncerRepository.setPrimaryShowingSoon(false)
            runCurrent()

            fakeKeyguardBouncerRepository.setPrimaryShowingSoon(true)
            runCurrent()

            assertThat(faceAuthRepository.runningAuthRequest.value)
                .isEqualTo(
                    Pair(
                        FaceAuthUiEvent.FACE_AUTH_UPDATED_PRIMARY_BOUNCER_SHOWN_OR_WILL_BE_SHOWN,
                        false,
                    )
                )
        }

    @Test
    @DisableSceneContainer
    fun faceAuthIsOnlyRequestedWhenPrimaryBouncerIsAboutToShow() =
        kosmos.runTest {
            underTest.start()

            fakeKeyguardBouncerRepository.setPrimaryShowingSoon(false)
            fakeKeyguardBouncerRepository.setPrimaryShow(false)
            runCurrent()

            fakeKeyguardBouncerRepository.setPrimaryShowingSoon(true)
            runCurrent()

            fakeKeyguardBouncerRepository.setPrimaryShow(true)
            runCurrent()

            assertThat(faceAuthRepository.runningAuthRequest.value)
                .isEqualTo(
                    Pair(
                        FaceAuthUiEvent.FACE_AUTH_UPDATED_PRIMARY_BOUNCER_SHOWN_OR_WILL_BE_SHOWN,
                        false,
                    )
                )
        }

    @Test
    @EnableSceneContainer
    fun withSceneContainerEnabled_faceAuthIsRequestedWhenPrimaryBouncerIsVisible() =
        kosmos.runTest {
            underTest.start()

            sceneInteractor.snapToScene(Scenes.Lockscreen, "for-test")
            sceneInteractor.showOverlay(Overlays.Bouncer, "for-test")
            sceneInteractor.setTransitionState(
                flowOf(ObservableTransitionState.Idle(Scenes.Lockscreen, setOf(Overlays.Bouncer)))
            )

            runCurrent()
            assertThat(faceAuthRepository.runningAuthRequest.value)
                .isEqualTo(Pair(FaceAuthUiEvent.FACE_AUTH_UPDATED_PRIMARY_BOUNCER_SHOWN, false))
        }

    @Test
    @EnableSceneContainer
    fun withSceneContainerEnabled_faceAuthIsRequestedWhenTransitioningToPrimaryBouncer() =
        kosmos.runTest {
            underTest.start()

            setSceneTransition(
                ShowOverlay(
                    overlay = Overlays.Bouncer,
                    fromScene = Scenes.Lockscreen,
                    progress = flowOf(.5f),
                )
            )
            assertThat(faceAuthRepository.runningAuthRequest.value)
                .isEqualTo(Pair(FaceAuthUiEvent.FACE_AUTH_UPDATED_PRIMARY_BOUNCER_SHOWN, false))
        }

    @Test
    fun faceAuthIsRequestedWhenAlternateBouncerIsVisible() =
        kosmos.runTest {
            underTest.start()

            fakeKeyguardBouncerRepository.setAlternateVisible(false)
            runCurrent()

            fakeKeyguardBouncerRepository.setAlternateVisible(true)
            runCurrent()

            assertThat(faceAuthRepository.runningAuthRequest.value)
                .isEqualTo(
                    Pair(
                        FaceAuthUiEvent.FACE_AUTH_TRIGGERED_ALTERNATE_BIOMETRIC_BOUNCER_SHOWN,
                        false,
                    )
                )
        }

    @Test
    fun faceAuthIsRequestedWhenUdfpsSensorTouched() =
        kosmos.runTest {
            underTest.start()

            underTest.onUdfpsSensorTouched()

            runCurrent()
            assertThat(faceAuthRepository.runningAuthRequest.value)
                .isEqualTo(Pair(FaceAuthUiEvent.FACE_AUTH_TRIGGERED_UDFPS_POINTER_DOWN, false))
        }

    @Test
    fun faceAuthIsRequestedWhenOnAssistantTriggeredOnLockScreen() =
        kosmos.runTest {
            underTest.start()

            underTest.onAssistantTriggeredOnLockScreen()

            runCurrent()
            assertThat(faceAuthRepository.runningAuthRequest.value)
                .isEqualTo(
                    Pair(FaceAuthUiEvent.FACE_AUTH_UPDATED_ASSISTANT_VISIBILITY_CHANGED, true)
                )
        }

    @Test
    fun faceAuthIsRequestedWhenDeviceLifted() =
        kosmos.runTest {
            underTest.start()

            underTest.onDeviceLifted()

            runCurrent()
            assertThat(faceAuthRepository.runningAuthRequest.value)
                .isEqualTo(
                    Pair(FaceAuthUiEvent.FACE_AUTH_TRIGGERED_PICK_UP_GESTURE_TRIGGERED, true)
                )
        }

    @Test
    fun faceAuthIsRequestedWhenShadeExpansionStarted() =
        kosmos.runTest {
            underTest.start()

            underTest.onShadeExpansionStarted()

            runCurrent()
            assertThat(faceAuthRepository.runningAuthRequest.value)
                .isEqualTo(Pair(FaceAuthUiEvent.FACE_AUTH_TRIGGERED_QS_EXPANDED, false))
        }

    @Test
    @EnableSceneContainer
    fun faceAuthIsRequestedWhenSingleShadeExpansionIsStarted() =
        kosmos.runTest {
            enableSingleShade()
            runCurrent()
            underTest.start()
            faceAuthRepository.canRunFaceAuth.value = true
            sceneInteractor.snapToScene(toScene = Scenes.Lockscreen, "for-test")
            runCurrent()

            sceneInteractor.changeScene(toScene = Scenes.Shade, loggingReason = "for-test")
            sceneInteractor.setTransitionState(
                flowOf(
                    ObservableTransitionState.Transition(
                        fromScene = Scenes.Lockscreen,
                        toScene = Scenes.Shade,
                        currentScene = flowOf(Scenes.Lockscreen),
                        progress = flowOf(0.2f),
                        isInitiatedByUserInput = true,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            )

            runCurrent()
            assertThat(faceAuthRepository.runningAuthRequest.value)
                .isEqualTo(Pair(FaceAuthUiEvent.FACE_AUTH_TRIGGERED_QS_EXPANDED, false))
        }

    @Test
    @EnableSceneContainer
    fun faceAuthIsRequestedOnlyOnceWhenShadeExpansionStarts() =
        kosmos.runTest {
            enableSingleShade()
            underTest.start()
            faceAuthRepository.canRunFaceAuth.value = true
            sceneInteractor.snapToScene(toScene = Scenes.Lockscreen, "for-test")
            runCurrent()

            sceneInteractor.changeScene(toScene = Scenes.Shade, loggingReason = "for-test")
            sceneInteractor.setTransitionState(
                flowOf(
                    ObservableTransitionState.Transition(
                        fromScene = Scenes.Lockscreen,
                        toScene = Scenes.Shade,
                        currentScene = flowOf(Scenes.Lockscreen),
                        progress = flowOf(0.2f),
                        isInitiatedByUserInput = true,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            )

            runCurrent()
            assertThat(faceAuthRepository.runningAuthRequest.value)
                .isEqualTo(Pair(FaceAuthUiEvent.FACE_AUTH_TRIGGERED_QS_EXPANDED, false))
            faceAuthRepository.runningAuthRequest.value = null

            // expansion progress shouldn't trigger face auth again
            sceneInteractor.setTransitionState(
                flowOf(
                    ObservableTransitionState.Transition(
                        fromScene = Scenes.Lockscreen,
                        toScene = Scenes.Shade,
                        currentScene = flowOf(Scenes.Lockscreen),
                        progress = flowOf(0.5f),
                        isInitiatedByUserInput = true,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            )

            assertThat(faceAuthRepository.runningAuthRequest.value).isNull()
        }

    @Test
    @EnableSceneContainer
    fun faceAuthIsRequestedWhenDualShadeExpansionIsStarted() =
        kosmos.runTest {
            enableDualShade()
            runCurrent()
            underTest.start()
            faceAuthRepository.canRunFaceAuth.value = true
            sceneInteractor.snapToScene(toScene = Scenes.Lockscreen, "for-test")
            runCurrent()

            sceneInteractor.showOverlay(Overlays.NotificationsShade, loggingReason = "for-test")
            sceneInteractor.setTransitionState(
                flowOf(
                    ObservableTransitionState.Transition.showOverlay(
                        overlay = Overlays.NotificationsShade,
                        fromScene = Scenes.Lockscreen,
                        currentOverlays = flowOf(emptySet()),
                        progress = flowOf(0.2f),
                        isInitiatedByUserInput = true,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            )

            runCurrent()
            assertThat(faceAuthRepository.runningAuthRequest.value)
                .isEqualTo(Pair(FaceAuthUiEvent.FACE_AUTH_TRIGGERED_QS_EXPANDED, false))
        }

    @Test
    @EnableSceneContainer
    fun faceAuthIsRequestedOnlyOnceWhenDualShadeExpansionStarts() =
        kosmos.runTest {
            enableDualShade()
            underTest.start()
            faceAuthRepository.canRunFaceAuth.value = true
            sceneInteractor.snapToScene(toScene = Scenes.Lockscreen, "for-test")
            runCurrent()

            sceneInteractor.showOverlay(Overlays.NotificationsShade, loggingReason = "for-test")
            sceneInteractor.setTransitionState(
                flowOf(
                    ObservableTransitionState.Transition.showOverlay(
                        overlay = Overlays.NotificationsShade,
                        fromScene = Scenes.Lockscreen,
                        currentOverlays = flowOf(emptySet()),
                        progress = flowOf(0.2f),
                        isInitiatedByUserInput = true,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            )

            runCurrent()
            assertThat(faceAuthRepository.runningAuthRequest.value)
                .isEqualTo(Pair(FaceAuthUiEvent.FACE_AUTH_TRIGGERED_QS_EXPANDED, false))
            faceAuthRepository.runningAuthRequest.value = null

            // expansion progress shouldn't trigger face auth again
            sceneInteractor.setTransitionState(
                flowOf(
                    ObservableTransitionState.Transition.showOverlay(
                        overlay = Overlays.NotificationsShade,
                        fromScene = Scenes.Lockscreen,
                        currentOverlays = flowOf(emptySet()),
                        progress = flowOf(0.5f),
                        isInitiatedByUserInput = true,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            )

            assertThat(faceAuthRepository.runningAuthRequest.value).isNull()
        }

    @Test
    fun faceAuthIsRequestedWhenNotificationPanelClicked() =
        kosmos.runTest {
            underTest.start()
            underTest.onNotificationPanelClicked()

            runCurrent()
            assertThat(faceAuthRepository.runningAuthRequest.value)
                .isEqualTo(
                    Pair(FaceAuthUiEvent.FACE_AUTH_TRIGGERED_NOTIFICATION_PANEL_CLICKED, true)
                )
        }

    @Test
    fun faceAuthIsCancelledWhenUserInputOnPrimaryBouncer() =
        kosmos.runTest {
            underTest.start()
            underTest.onSwipeUpOnBouncer()

            runCurrent()
            assertThat(faceAuthRepository.isAuthRunning.value).isTrue()

            underTest.onPrimaryBouncerUserInput()
            runCurrent()
            assertThat(faceAuthRepository.isAuthRunning.value).isFalse()
        }

    @Test
    fun faceAuthIsRequestedWhenSwipeUpOnBouncer() =
        kosmos.runTest {
            underTest.start()
            underTest.onSwipeUpOnBouncer()

            runCurrent()
            assertThat(faceAuthRepository.runningAuthRequest.value)
                .isEqualTo(Pair(FaceAuthUiEvent.FACE_AUTH_TRIGGERED_SWIPE_UP_ON_BOUNCER, false))
        }

    @Test
    fun faceAuthIsRequestedWhenWalletIsLaunchedAndIfFaceAuthIsStrong() =
        kosmos.runTest {
            underTest.start()
            facePropertyRepository.setSensorInfo(FaceSensorInfo(1, SensorStrength.STRONG))

            underTest.onWalletLaunched()

            runCurrent()
            assertThat(faceAuthRepository.runningAuthRequest.value)
                .isEqualTo(Pair(FaceAuthUiEvent.FACE_AUTH_TRIGGERED_OCCLUDING_APP_REQUESTED, true))
        }

    @Test
    fun faceAuthIsNotTriggeredIfFaceAuthIsWeak() =
        kosmos.runTest {
            underTest.start()
            facePropertyRepository.setSensorInfo(FaceSensorInfo(1, SensorStrength.WEAK))

            underTest.onWalletLaunched()

            runCurrent()
            assertThat(faceAuthRepository.runningAuthRequest.value).isNull()
        }

    @Test
    fun faceAuthIsNotTriggeredIfFaceAuthIsConvenience() =
        kosmos.runTest {
            underTest.start()
            facePropertyRepository.setSensorInfo(FaceSensorInfo(1, SensorStrength.CONVENIENCE))

            underTest.onWalletLaunched()

            runCurrent()
            assertThat(faceAuthRepository.runningAuthRequest.value).isNull()
        }

    @Test
    fun faceUnlockIsDisabledWhenFpIsLockedOut() =
        kosmos.runTest {
            underTest.start()
            fakeBiometricSettingsRepository.setIsFaceAuthEnrolledAndEnabled(true)

            fakeDeviceEntryFingerprintAuthRepository.setLockedOut(true)
            runCurrent()

            assertThat(faceAuthRepository.isLockedOut.value).isTrue()
        }

    @Test
    fun faceLockoutStateIsResetWheneverFingerprintIsNotLockedOut() =
        kosmos.runTest {
            underTest.start()
            fakeUserRepository.setSelectedUserInfo(primaryUser, SelectionStatus.SELECTION_COMPLETE)
            fakeBiometricSettingsRepository.setIsFaceAuthEnrolledAndEnabled(true)

            fakeDeviceEntryFingerprintAuthRepository.setLockedOut(true)
            runCurrent()

            assertThat(faceAuthRepository.isLockedOut.value).isTrue()
            facePropertyRepository.setLockoutMode(primaryUserId, LockoutMode.NONE)

            fakeDeviceEntryFingerprintAuthRepository.setLockedOut(false)
            runCurrent()

            assertThat(faceAuthRepository.isLockedOut.value).isFalse()
        }

    @Test
    fun faceLockoutStateIsSetToUsersLockoutStateWheneverFingerprintIsNotLockedOut() =
        kosmos.runTest {
            underTest.start()
            fakeUserRepository.setSelectedUserInfo(primaryUser, SelectionStatus.SELECTION_COMPLETE)
            fakeBiometricSettingsRepository.setIsFaceAuthEnrolledAndEnabled(true)

            fakeDeviceEntryFingerprintAuthRepository.setLockedOut(true)
            runCurrent()

            assertThat(faceAuthRepository.isLockedOut.value).isTrue()
            facePropertyRepository.setLockoutMode(primaryUserId, LockoutMode.TIMED)

            fakeDeviceEntryFingerprintAuthRepository.setLockedOut(false)
            runCurrent()

            assertThat(faceAuthRepository.isLockedOut.value).isTrue()
        }

    @Test
    fun whenIsAuthenticatedFalse_clearFaceBiometrics() =
        kosmos.runTest {
            underTest.start()

            faceAuthRepository.isAuthenticated.value = true
            runCurrent()
            verify(trustManager, never())
                .clearAllBiometricRecognized(eq(BiometricSourceType.FACE), anyInt())

            faceAuthRepository.isAuthenticated.value = false
            runCurrent()

            verify(trustManager).clearAllBiometricRecognized(eq(BiometricSourceType.FACE), anyInt())
        }

    @Test
    fun faceAuthIsRequestedWhenAuthIsRunningWhileCameraInfoChanged() =
        kosmos.runTest {
            facePropertyRepository.setCameraIno(null)
            underTest.start()

            faceAuthRepository.requestAuthenticate(
                FaceAuthUiEvent.FACE_AUTH_UPDATED_KEYGUARD_VISIBILITY_CHANGED,
                true,
            )
            facePropertyRepository.setCameraIno(CameraInfo("0", "1", null))

            runCurrent()
            assertThat(faceAuthRepository.runningAuthRequest.value)
                .isEqualTo(Pair(FaceAuthUiEvent.FACE_AUTH_CAMERA_AVAILABLE_CHANGED, true))
        }

    @Test
    fun faceAuthIsNotRequestedWhenNoAuthRunningWhileCameraInfoChanged() =
        kosmos.runTest {
            facePropertyRepository.setCameraIno(null)
            underTest.start()

            facePropertyRepository.setCameraIno(CameraInfo("0", "1", null))

            runCurrent()
            assertThat(faceAuthRepository.runningAuthRequest.value).isNull()
        }

    @Test
    fun faceAuthIsNotRequestedWhenAuthIsRunningWhileCameraInfoIsNull() =
        kosmos.runTest {
            facePropertyRepository.setCameraIno(null)
            underTest.start()

            facePropertyRepository.setCameraIno(null)

            runCurrent()
            assertThat(faceAuthRepository.runningAuthRequest.value).isNull()
        }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @Test
    fun faceAuthIsRequestedForSecureLockDeviceBiometricAuth_cancelledWhenHidden() =
        kosmos.runTest {
            underTest.onSecureLockDeviceBiometricAuthRequested()
            underTest.start()

            runCurrent()
            assertThat(faceAuthRepository.runningAuthRequest.value).isNotNull()

            underTest.onSecureLockDeviceBiometricAuthHidden()
            runCurrent()
            assertThat(faceAuthRepository.runningAuthRequest.value).isNull()
        }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @Test
    fun faceAuthIsNotRequestedWhenPendingConfirmation_inSecureLockDeviceMode() =
        kosmos.runTest {
            underTest.onSecureLockDeviceConfirmButtonShowingChanged(true)
            underTest.start()
            underTest.onSwipeUpOnBouncer()

            runCurrent()
            assertThat(faceAuthRepository.runningAuthRequest.value).isNull()
        }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @Test
    fun faceAuthIsNotRequestedWhenPendingRetryBiometricAuth_inSecureLockDeviceMode() =
        kosmos.runTest {
            underTest.onSecureLockDeviceTryAgainButtonShowingChanged(true)
            underTest.start()
            underTest.onSwipeUpOnBouncer()

            runCurrent()
            assertThat(faceAuthRepository.runningAuthRequest.value).isNull()
        }

    @Test
    fun lockedOut_providesSameValueFromRepository() =
        kosmos.runTest {
            assertThat(underTest.isLockedOut).isSameInstanceAs(faceAuthRepository.isLockedOut)
        }

    @Test
    fun authenticated_providesSameValueFromRepository() =
        kosmos.runTest {
            assertThat(underTest.isAuthenticated)
                .isSameInstanceAs(faceAuthRepository.isAuthenticated)
        }

    @Test
    fun faceAuthIsRequestedOnSimPinSuccess() =
        kosmos.runTest {
            underTest.start()
            runCurrent()

            // no auth request when the SIM is secure (requires pin)
            fakeMobileConnectionsRepository.isAnySimSecure.value = true
            runCurrent()
            assertThat(faceAuthRepository.runningAuthRequest.value).isNull()

            // auth request when the SIM is no longer secure (successful pin!)
            fakeMobileConnectionsRepository.isAnySimSecure.value = false
            runCurrent()

            assertThat(faceAuthRepository.runningAuthRequest.value)
                .isEqualTo(Pair(FaceAuthUiEvent.FACE_AUTH_SIM_PIN_SUCCESS, true))
        }

    companion object {
        private const val primaryUserId = 1
        private val primaryUser = UserInfo(primaryUserId, "test user", UserInfo.FLAG_PRIMARY)

        private val secondaryUser = UserInfo(2, "secondary user", 0)
    }
}
