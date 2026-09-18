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

package com.android.systemui.statusbar.pipeline.shared.ui.viewmodel

// Force re-run

import android.app.StatusBarManager.DISABLE2_NONE
import android.app.StatusBarManager.DISABLE_CLOCK
import android.app.StatusBarManager.DISABLE_NONE
import android.app.StatusBarManager.DISABLE_NOTIFICATION_ICONS
import android.app.StatusBarManager.DISABLE_SYSTEM_INFO
import android.content.testableContext
import android.graphics.Rect
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import android.view.Display.DEFAULT_DISPLAY
import android.view.Display.TYPE_EXTERNAL
import android.view.View
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.OverlayKey
import com.android.media.projection.flags.Flags.FLAG_SHOW_STOP_DIALOG_POST_CALL_END
import com.android.systemui.Flags
import com.android.systemui.Flags.FLAG_DUAL_SHADE
import com.android.systemui.Flags.FLAG_SCENE_CONTAINER
import com.android.systemui.SysuiTestCase
import com.android.systemui.display.data.repository.display
import com.android.systemui.display.data.repository.displayRepository
import com.android.systemui.display.data.repository.fake
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.flags.andSceneContainer
import com.android.systemui.inputmethod.data.repository.fakeInputMethodRepository
import com.android.systemui.keyguard.data.repository.fakeDeviceEntryFaceAuthRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.data.repository.keyguardOcclusionRepository
import com.android.systemui.keyguard.domain.interactor.keyguardInteractor
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.TransitionState
import com.android.systemui.keyguard.shared.model.TransitionStep
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.collectValues
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.mediaprojection.data.model.MediaProjectionState
import com.android.systemui.mediaprojection.data.repository.fakeMediaProjectionRepository
import com.android.systemui.plugins.DarkIconDispatcher
import com.android.systemui.scene.data.repository.sceneContainerRepository
import com.android.systemui.scene.data.repository.setSceneTransition
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.screenrecord.data.model.ScreenRecordModel
import com.android.systemui.screenrecord.data.repository.screenRecordRepository
import com.android.systemui.shade.data.repository.fakeShadeDisplaysRepository
import com.android.systemui.shade.data.repository.statusBarTouchShadeDisplayPolicy
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.domain.interactor.notificationElement
import com.android.systemui.shade.domain.interactor.qsElement
import com.android.systemui.shade.shadeTestUtil
import com.android.systemui.statusbar.chips.mediaprojection.domain.interactor.MediaProjectionChipInteractorTest.Companion.NORMAL_PACKAGE
import com.android.systemui.statusbar.chips.mediaprojection.domain.interactor.MediaProjectionChipInteractorTest.Companion.setUpPackageManagerForMediaProjection
import com.android.systemui.statusbar.chips.mediaprojection.domain.model.MediaProjectionStopDialogModel
import com.android.systemui.statusbar.chips.sharetoapp.ui.viewmodel.shareToAppChipViewModel
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipsWithNotifsViewModelTest.Companion.assertIsCallChip
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipsWithNotifsViewModelTest.Companion.assertIsScreenRecordChip
import com.android.systemui.statusbar.core.StatusBarForDesktop
import com.android.systemui.statusbar.data.model.StatusBarMode
import com.android.systemui.statusbar.data.repository.fakeStatusBarModePerDisplayRepository
import com.android.systemui.statusbar.disableflags.data.repository.fakeDisableFlagsRepository
import com.android.systemui.statusbar.disableflags.shared.model.DisableFlagsModel
import com.android.systemui.statusbar.events.data.repository.systemStatusEventAnimationRepository
import com.android.systemui.statusbar.events.shared.model.SystemEventAnimationState.AnimatingIn
import com.android.systemui.statusbar.events.shared.model.SystemEventAnimationState.AnimatingOut
import com.android.systemui.statusbar.events.shared.model.SystemEventAnimationState.Idle
import com.android.systemui.statusbar.notification.data.model.activeNotificationModel
import com.android.systemui.statusbar.notification.data.repository.ActiveNotificationsStore
import com.android.systemui.statusbar.notification.data.repository.UnconfinedFakeHeadsUpRowRepository
import com.android.systemui.statusbar.notification.data.repository.activeNotificationListRepository
import com.android.systemui.statusbar.notification.data.repository.getPopulatedActiveNotificationsStore
import com.android.systemui.statusbar.notification.headsup.PinnedStatus
import com.android.systemui.statusbar.notification.shared.ActiveNotificationModel
import com.android.systemui.statusbar.notification.stack.data.repository.headsUpNotificationRepository
import com.android.systemui.statusbar.phone.SysuiDarkIconDispatcher
import com.android.systemui.statusbar.phone.data.repository.fakeDarkIconRepository
import com.android.systemui.statusbar.phone.ongoingcall.shared.model.OngoingCallTestHelper.addOngoingCallState
import com.android.systemui.statusbar.pipeline.shared.StatusBarShowIconsInSecureCamera
import com.android.systemui.statusbar.pipeline.shared.domain.HomeStatusBarHelper.launchSecureCamera
import com.android.systemui.statusbar.pipeline.shared.domain.HomeStatusBarHelper.setStatusBarWindowState
import com.android.systemui.statusbar.pipeline.shared.domain.HomeStatusBarHelper.transitionKeyguardToGone
import com.android.systemui.statusbar.pipeline.shared.domain.interactor.setHomeStatusBarIconBlockList
import com.android.systemui.statusbar.pipeline.shared.domain.interactor.setHomeStatusBarInteractorShowOperatorName
import com.android.systemui.statusbar.pipeline.shared.ui.model.VisibilityModel
import com.android.systemui.statusbar.policy.data.repository.fakeDeviceProvisioningRepository
import com.android.systemui.statusbar.quickactions.ime.domain.interactor.imeIndicatorChipInteractor
import com.android.systemui.statusbar.window.shared.model.StatusBarWindowState
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.fakeUserRepository
import com.android.wm.shell.scrolltotop.fakeScrollToTop
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@RunWith(ParameterizedAndroidJunit4::class)
@SmallTest
class HomeStatusBarViewModelImplTest(flags: FlagsParameterization) : SysuiTestCase() {
    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.underTest by
        Kosmos.Fixture { homeStatusBarViewModel.also { it.activateIn(testScope) } }

    @Before
    fun setUp() {
        setUpPackageManagerForMediaProjection(kosmos)
    }

    @Before
    fun addDisplays() = runBlocking { kosmos.displayRepository.fake.addDisplay(DEFAULT_DISPLAY) }

    @Test
    @EnableFlags(FLAG_SHOW_STOP_DIALOG_POST_CALL_END)
    fun mediaProjectionStopDialogDueToCallEndedState_initiallyHidden() =
        kosmos.runTest {
            shareToAppChipViewModel.start()
            val latest by collectLastValue(underTest.mediaProjectionStopDialogDueToCallEndedState)

            // Verify that the stop dialog is initially hidden
            assertThat(latest).isInstanceOf(MediaProjectionStopDialogModel.Hidden::class.java)
        }

    @Test
    @EnableFlags(FLAG_SHOW_STOP_DIALOG_POST_CALL_END)
    fun mediaProjectionStopDialogDueToCallEndedState_flagEnabled_mediaIsProjecting_projectionStartedDuringCallAndActivePostCallEventEmitted_isShown() =
        kosmos.runTest {
            shareToAppChipViewModel.start()

            val latest by
                collectLastValue(
                    homeStatusBarViewModel.mediaProjectionStopDialogDueToCallEndedState
                )

            fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(NORMAL_PACKAGE)

            fakeMediaProjectionRepository.emitProjectionStartedDuringCallAndActivePostCallEvent()

            assertThat(latest).isInstanceOf(MediaProjectionStopDialogModel.Shown::class.java)
        }

    @Test
    @DisableFlags(FLAG_SHOW_STOP_DIALOG_POST_CALL_END)
    fun mediaProjectionStopDialogDueToCallEndedState_flagDisabled_mediaIsProjecting_projectionStartedDuringCallAndActivePostCallEventEmitted_isHidden() =
        kosmos.runTest {
            shareToAppChipViewModel.start()

            val latest by
                collectLastValue(
                    homeStatusBarViewModel.mediaProjectionStopDialogDueToCallEndedState
                )

            fakeMediaProjectionRepository.mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen(NORMAL_PACKAGE)

            fakeMediaProjectionRepository.emitProjectionStartedDuringCallAndActivePostCallEvent()

            assertThat(latest).isInstanceOf(MediaProjectionStopDialogModel.Hidden::class.java)
        }

    @Test
    @DisableSceneContainer
    fun isTransitioningFromLockscreenToOccluded_started_isTrue() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isTransitioningFromLockscreenToOccluded)

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    KeyguardState.LOCKSCREEN,
                    KeyguardState.OCCLUDED,
                    value = 0f,
                    TransitionState.STARTED,
                )
            )

            assertThat(latest).isTrue()
        }

    @Test
    @DisableSceneContainer
    fun isTransitioningFromLockscreenToOccluded_running_isTrue() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isTransitioningFromLockscreenToOccluded)

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    KeyguardState.LOCKSCREEN,
                    KeyguardState.OCCLUDED,
                    value = 0f,
                    TransitionState.RUNNING,
                )
            )

            assertThat(latest).isTrue()
        }

    @Test
    @DisableSceneContainer
    fun isTransitioningFromLockscreenToOccluded_finished_isFalse() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isTransitioningFromLockscreenToOccluded)

            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.OCCLUDED,
                testScope.testScheduler,
            )

            assertThat(latest).isFalse()
        }

    @Test
    fun isTransitioningFromLockscreenToOccluded_canceled_isFalse() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isTransitioningFromLockscreenToOccluded)

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    KeyguardState.LOCKSCREEN,
                    KeyguardState.OCCLUDED,
                    value = 0f,
                    TransitionState.CANCELED,
                )
            )

            assertThat(latest).isFalse()
        }

    @Test
    fun isTransitioningFromLockscreenToOccluded_irrelevantTransition_isFalse() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isTransitioningFromLockscreenToOccluded)

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    KeyguardState.AOD,
                    KeyguardState.LOCKSCREEN,
                    value = 0f,
                    TransitionState.RUNNING,
                )
            )

            assertThat(latest).isFalse()
        }

    @Test
    @DisableSceneContainer
    fun isTransitioningFromLockscreenToOccluded_followsRepoUpdates() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isTransitioningFromLockscreenToOccluded)

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    KeyguardState.LOCKSCREEN,
                    KeyguardState.OCCLUDED,
                    value = 0f,
                    TransitionState.RUNNING,
                )
            )

            assertThat(latest).isTrue()

            // WHEN the repo updates the transition to finished
            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    KeyguardState.LOCKSCREEN,
                    KeyguardState.OCCLUDED,
                    value = 0f,
                    TransitionState.FINISHED,
                )
            )

            // THEN our manager also updates
            assertThat(latest).isFalse()
        }

    @Test
    @DisableSceneContainer
    fun transitionFromLockscreenToDreamStartedEvent_started_emitted() =
        kosmos.runTest {
            val emissions by collectValues(underTest.transitionFromLockscreenToDreamStartedEvent)

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    KeyguardState.LOCKSCREEN,
                    KeyguardState.DREAMING,
                    value = 0f,
                    TransitionState.STARTED,
                )
            )

            assertThat(emissions.size).isEqualTo(1)
        }

    @Test
    @DisableSceneContainer
    fun transitionFromLockscreenToDreamStartedEvent_startedMultiple_emittedMultiple() =
        kosmos.runTest {
            val emissions by collectValues(underTest.transitionFromLockscreenToDreamStartedEvent)

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    KeyguardState.LOCKSCREEN,
                    KeyguardState.DREAMING,
                    value = 0f,
                    TransitionState.STARTED,
                )
            )

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    KeyguardState.LOCKSCREEN,
                    KeyguardState.DREAMING,
                    value = 0f,
                    TransitionState.STARTED,
                )
            )

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    KeyguardState.LOCKSCREEN,
                    KeyguardState.DREAMING,
                    value = 0f,
                    TransitionState.STARTED,
                )
            )

            assertThat(emissions.size).isEqualTo(3)
        }

    @Test
    @DisableSceneContainer
    fun transitionFromLockscreenToDreamStartedEvent_startedThenRunning_emittedOnlyOne() =
        kosmos.runTest {
            val emissions by collectValues(underTest.transitionFromLockscreenToDreamStartedEvent)

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    KeyguardState.LOCKSCREEN,
                    KeyguardState.DREAMING,
                    value = 0f,
                    TransitionState.STARTED,
                )
            )
            assertThat(emissions.size).isEqualTo(1)

            // WHEN the transition progresses through its animation by going through the RUNNING
            // step with increasing fractions
            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    KeyguardState.LOCKSCREEN,
                    KeyguardState.DREAMING,
                    value = .1f,
                    TransitionState.RUNNING,
                )
            )

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    KeyguardState.LOCKSCREEN,
                    KeyguardState.DREAMING,
                    value = .2f,
                    TransitionState.RUNNING,
                )
            )

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    KeyguardState.LOCKSCREEN,
                    KeyguardState.DREAMING,
                    value = .3f,
                    TransitionState.RUNNING,
                )
            )

            // THEN the flow does not emit since the flow should only emit when the transition
            // starts
            assertThat(emissions.size).isEqualTo(1)
        }

    @Test
    fun transitionFromLockscreenToDreamStartedEvent_irrelevantTransition_notEmitted() =
        kosmos.runTest {
            val emissions by collectValues(underTest.transitionFromLockscreenToDreamStartedEvent)

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    KeyguardState.LOCKSCREEN,
                    KeyguardState.OCCLUDED,
                    value = 0f,
                    TransitionState.STARTED,
                )
            )

            assertThat(emissions).isEmpty()
        }

    @Test
    @DisableSceneContainer
    fun transitionFromLockscreenToDreamStartedEvent_irrelevantTransitionState_notEmitted() =
        kosmos.runTest {
            val emissions by collectValues(underTest.transitionFromLockscreenToDreamStartedEvent)

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    KeyguardState.LOCKSCREEN,
                    KeyguardState.DREAMING,
                    value = 1.0f,
                    TransitionState.FINISHED,
                ),
                // We're intentionally not sending STARTED to validate that FINISHED steps are
                // ignored.
                validateStep = false,
            )

            assertThat(emissions).isEmpty()
        }

    @Test
    fun areNotificationsLightsOut_lowProfileWithNotifications_true() =
        kosmos.runTest {
            fakeStatusBarModePerDisplayRepository.statusBarMode.value =
                StatusBarMode.LIGHTS_OUT_TRANSPARENT
            activeNotificationListRepository.activeNotifications.value =
                activeNotificationsStore(testNotifications)

            val actual by collectLastValue(underTest.areNotificationsLightsOut)

            assertThat(actual).isTrue()
        }

    @Test
    fun areNotificationsLightsOut_lowProfileWithoutNotifications_false() =
        kosmos.runTest {
            fakeStatusBarModePerDisplayRepository.statusBarMode.value =
                StatusBarMode.LIGHTS_OUT_TRANSPARENT
            activeNotificationListRepository.activeNotifications.value =
                activeNotificationsStore(emptyList())

            val actual by collectLastValue(underTest.areNotificationsLightsOut)

            assertThat(actual).isFalse()
        }

    @Test
    fun areNotificationsLightsOut_defaultStatusBarModeWithoutNotifications_false() =
        kosmos.runTest {
            fakeStatusBarModePerDisplayRepository.statusBarMode.value = StatusBarMode.TRANSPARENT
            activeNotificationListRepository.activeNotifications.value =
                activeNotificationsStore(emptyList())

            val actual by collectLastValue(underTest.areNotificationsLightsOut)

            assertThat(actual).isFalse()
        }

    @Test
    fun areNotificationsLightsOut_defaultStatusBarModeWithNotifications_false() =
        kosmos.runTest {
            fakeStatusBarModePerDisplayRepository.statusBarMode.value = StatusBarMode.TRANSPARENT
            activeNotificationListRepository.activeNotifications.value =
                activeNotificationsStore(testNotifications)

            val actual by collectLastValue(underTest.areNotificationsLightsOut)

            assertThat(actual).isFalse()
        }

    @Test
    fun shouldShowOperatorNameView_allowedByInteractor_allowedByDisableFlags_visible() =
        kosmos.runTest {
            kosmos.setHomeStatusBarInteractorShowOperatorName(true)

            val latest by collectLastValue(underTest.shouldShowOperatorNameView)
            transitionKeyguardToGone()

            fakeDisableFlagsRepository.disableFlags.value =
                DisableFlagsModel(DISABLE_NONE, DISABLE2_NONE)

            assertThat(latest).isTrue()
        }

    @Test
    fun shouldShowOperatorNameView_disAllowedByInteractor_allowedByDisableFlags_notVisible() =
        kosmos.runTest {
            kosmos.setHomeStatusBarInteractorShowOperatorName(false)

            transitionKeyguardToGone()

            fakeDisableFlagsRepository.disableFlags.value =
                DisableFlagsModel(DISABLE_NONE, DISABLE2_NONE)

            val latest by collectLastValue(underTest.shouldShowOperatorNameView)

            assertThat(latest).isFalse()
        }

    @Test
    fun shouldShowOperatorNameView_allowedByInteractor_disallowedByDisableFlags_notVisible() =
        kosmos.runTest {
            kosmos.setHomeStatusBarInteractorShowOperatorName(true)

            val latest by collectLastValue(underTest.shouldShowOperatorNameView)
            transitionKeyguardToGone()

            fakeDisableFlagsRepository.disableFlags.value =
                DisableFlagsModel(DISABLE_SYSTEM_INFO, DISABLE2_NONE)

            assertThat(latest).isFalse()
        }

    @Test
    fun shouldShowOperatorNameView_allowedByInteractor_hunPinned_true() =
        kosmos.runTest {
            kosmos.setHomeStatusBarInteractorShowOperatorName(true)

            transitionKeyguardToGone()

            fakeDisableFlagsRepository.disableFlags.value =
                DisableFlagsModel(DISABLE_NONE, DISABLE2_NONE)

            // WHEN there is an active HUN
            headsUpNotificationRepository.setNotifications(
                UnconfinedFakeHeadsUpRowRepository(
                    key = "key",
                    pinnedStatus = MutableStateFlow(PinnedStatus.PinnedBySystem),
                )
            )

            val latest by collectLastValue(underTest.shouldShowOperatorNameView)

            // THEN we still show the operator name view
            assertThat(latest).isTrue()
        }

    @Test
    fun ongoingActivityChips_statusBarHidden_noSecureCamera_noHun_notAllowed() =
        kosmos.runTest {
            // home status bar not allowed
            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Lockscreen)
            kosmos.keyguardOcclusionRepository.setOccludedFromRemoteAnimation(
                false,
                taskInfo = null,
            )

            assertThat(underTest.ongoingActivityChips.areChipsAllowed).isFalse()
        }

    @Test
    fun ongoingActivityChips_statusBarNotHidden_noSecureCamera_noHun_isAllowed() =
        kosmos.runTest {
            transitionKeyguardToGone()

            assertThat(underTest.ongoingActivityChips.areChipsAllowed).isTrue()
        }

    @Test
    fun ongoingActivityChips_statusBarNotHidden_secureCamera_noHun_notAllowed() =
        kosmos.runTest {
            launchSecureCamera()

            assertThat(underTest.ongoingActivityChips.areChipsAllowed).isFalse()
        }

    @Test
    fun ongoingActivityChips_statusBarNotHidden_noSecureCamera_hunBySystem_isAllowed() =
        kosmos.runTest {
            transitionKeyguardToGone()

            headsUpNotificationRepository.setNotifications(
                UnconfinedFakeHeadsUpRowRepository(
                    key = "key",
                    pinnedStatus = MutableStateFlow(PinnedStatus.PinnedBySystem),
                )
            )

            assertThat(underTest.ongoingActivityChips.areChipsAllowed).isTrue()
        }

    @Test
    fun ongoingActivityChips_statusBarNotHidden_noSecureCamera_hunByUser_isAllowed() =
        kosmos.runTest {
            transitionKeyguardToGone()

            headsUpNotificationRepository.setNotifications(
                UnconfinedFakeHeadsUpRowRepository(
                    key = "key",
                    pinnedStatus = MutableStateFlow(PinnedStatus.PinnedByUser),
                )
            )

            assertThat(underTest.ongoingActivityChips.areChipsAllowed).isTrue()
        }

    @Test
    fun ongoingActivityChips_followsChipsViewModel() =
        kosmos.runTest {
            transitionKeyguardToGone()

            screenRecordRepository.screenRecordState.value = ScreenRecordModel.Recording

            assertIsScreenRecordChip(underTest.ongoingActivityChips.chips.active[0])

            addOngoingCallState(key = "call")

            assertIsScreenRecordChip(underTest.ongoingActivityChips.chips.active[0])
            assertIsCallChip(underTest.ongoingActivityChips.chips.active[1], "call", context)
        }

    @Test
    fun hunOnLockscreenWithBypass_everythingHidden() =
        kosmos.runTest {
            val latestNotifs by collectLastValue(underTest.isNotificationIconContainerVisible)
            val latestSystemInfo by collectLastValue(underTest.systemInfoCombinedVis)

            // WHEN on lockscreen with bypass enabled
            if (SceneContainerFlag.isEnabled) {
                sceneContainerRepository.instantlyTransitionTo(Scenes.Lockscreen)
            } else {
                fakeKeyguardTransitionRepository.transitionTo(
                    KeyguardState.GONE,
                    KeyguardState.LOCKSCREEN,
                )
            }
            fakeDeviceEntryFaceAuthRepository.isBypassEnabled.value = true
            // WHEN there's a HUN
            headsUpNotificationRepository.setNotifications(
                UnconfinedFakeHeadsUpRowRepository(
                    key = "key",
                    pinnedStatus = MutableStateFlow(PinnedStatus.PinnedBySystem),
                )
            )

            // THEN status bar content still hides
            assertThat(latestNotifs!!.visibility).isEqualTo(View.GONE)
            assertThat(latestSystemInfo!!.baseVisibility.visibility).isEqualTo(View.GONE)
        }

    @Test
    fun isClockVisible_allowedByDisableFlags_visible() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isClockVisible)
            transitionKeyguardToGone()

            fakeDisableFlagsRepository.disableFlags.value =
                DisableFlagsModel(DISABLE_NONE, DISABLE2_NONE)

            assertThat(latest!!.visibility).isEqualTo(View.VISIBLE)
        }

    @Test
    fun isClockVisible_notAllowedByDisableFlags_invisible() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isClockVisible)
            transitionKeyguardToGone()

            fakeDisableFlagsRepository.disableFlags.value =
                DisableFlagsModel(DISABLE_CLOCK, DISABLE2_NONE)

            assertThat(latest!!.visibility).isEqualTo(View.INVISIBLE)
        }

    @Test
    fun isClockVisible_allowedByDisableFlags_hunPinnedByUser_visible() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isClockVisible)
            transitionKeyguardToGone()

            fakeDisableFlagsRepository.disableFlags.value =
                DisableFlagsModel(DISABLE_NONE, DISABLE2_NONE)
            // there is an active HUN
            headsUpNotificationRepository.setNotifications(
                UnconfinedFakeHeadsUpRowRepository(
                    key = "key",
                    pinnedStatus = MutableStateFlow(PinnedStatus.PinnedByUser),
                )
            )

            assertThat(latest!!.visibility).isEqualTo(View.VISIBLE)
        }

    @Test
    fun isClockVisible_allowedByDisableFlags_hunPinnedBySystem_visible() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isClockVisible)
            transitionKeyguardToGone()

            fakeDisableFlagsRepository.disableFlags.value =
                DisableFlagsModel(DISABLE_NONE, DISABLE2_NONE)
            // WHEN there is an active HUN
            headsUpNotificationRepository.setNotifications(
                UnconfinedFakeHeadsUpRowRepository(
                    key = "key",
                    pinnedStatus = MutableStateFlow(PinnedStatus.PinnedBySystem),
                )
            )

            // THEN we still show the clock view
            assertThat(latest!!.visibility).isEqualTo(View.VISIBLE)
        }

    @Test
    fun isNotificationIconContainerVisible_allowedByDisableFlags_visible() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isNotificationIconContainerVisible)
            transitionKeyguardToGone()

            fakeDisableFlagsRepository.disableFlags.value =
                DisableFlagsModel(DISABLE_NONE, DISABLE2_NONE)

            assertThat(latest!!.visibility).isEqualTo(View.VISIBLE)
        }

    @Test
    fun isNotificationIconContainerVisible_notAllowedByDisableFlags_gone() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isNotificationIconContainerVisible)
            transitionKeyguardToGone()

            fakeDisableFlagsRepository.disableFlags.value =
                DisableFlagsModel(DISABLE_NOTIFICATION_ICONS, DISABLE2_NONE)

            assertThat(latest!!.visibility).isEqualTo(View.GONE)
        }

    @Test
    fun isNotificationIconContainerVisible_anyChipShowing_chipsModernizationOn() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isNotificationIconContainerVisible)
            transitionKeyguardToGone()

            kosmos.screenRecordRepository.screenRecordState.value = ScreenRecordModel.Recording

            assertThat(latest!!.visibility).isEqualTo(View.GONE)

            kosmos.screenRecordRepository.screenRecordState.value = ScreenRecordModel.DoingNothing

            assertThat(latest!!.visibility).isEqualTo(View.VISIBLE)
        }

    @Test
    fun isNotificationIconContainerVisible_hasChipButAlsoHun_hunBySystem_gone() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isNotificationIconContainerVisible)
            transitionKeyguardToGone()

            // Chip
            kosmos.screenRecordRepository.screenRecordState.value = ScreenRecordModel.Recording

            // HUN, PinnedBySystem
            headsUpNotificationRepository.setNotifications(
                UnconfinedFakeHeadsUpRowRepository(
                    key = "key",
                    pinnedStatus = MutableStateFlow(PinnedStatus.PinnedBySystem),
                )
            )

            assertThat(latest!!.visibility).isEqualTo(View.GONE)
        }

    @Test
    fun isNotificationIconContainerVisible_hasChipButAlsoHun_hunByUser_gone() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isNotificationIconContainerVisible)
            transitionKeyguardToGone()

            // Chip
            kosmos.screenRecordRepository.screenRecordState.value = ScreenRecordModel.Recording

            // HUN, PinnedByUser
            headsUpNotificationRepository.setNotifications(
                UnconfinedFakeHeadsUpRowRepository(
                    key = "key",
                    pinnedStatus = MutableStateFlow(PinnedStatus.PinnedByUser),
                )
            )

            assertThat(latest!!.visibility).isEqualTo(View.GONE)
        }

    @Test
    fun isSystemInfoVisible_allowedByDisableFlags_visible() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.systemInfoCombinedVis)
            transitionKeyguardToGone()

            fakeDisableFlagsRepository.disableFlags.value =
                DisableFlagsModel(DISABLE_NONE, DISABLE2_NONE)

            assertThat(latest!!.baseVisibility.visibility).isEqualTo(View.VISIBLE)
        }

    @Test
    fun isSystemInfoVisible_notAllowedByDisableFlags_gone() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.systemInfoCombinedVis)
            transitionKeyguardToGone()

            fakeDisableFlagsRepository.disableFlags.value =
                DisableFlagsModel(DISABLE_SYSTEM_INFO, DISABLE2_NONE)

            assertThat(latest!!.baseVisibility.visibility).isEqualTo(View.GONE)
        }

    @Test
    fun systemInfoCombineVis_animationsPassThrough() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.systemInfoCombinedVis)
            transitionKeyguardToGone()

            assertThat(latest!!.baseVisibility)
                .isEqualTo(VisibilityModel(visibility = View.VISIBLE, shouldAnimateChange = false))
            assertThat(latest!!.animationState).isEqualTo(Idle)

            // WHEN the animation state changes, but the visibility state doesn't change
            systemStatusEventAnimationRepository.animationState.value = AnimatingIn

            // THEN the visibility is the same
            assertThat(latest!!.baseVisibility)
                .isEqualTo(VisibilityModel(visibility = View.VISIBLE, shouldAnimateChange = false))
            // THEN the animation state updates
            assertThat(latest!!.animationState).isEqualTo(AnimatingIn)

            systemStatusEventAnimationRepository.animationState.value = AnimatingOut
            assertThat(latest!!.baseVisibility)
                .isEqualTo(VisibilityModel(visibility = View.VISIBLE, shouldAnimateChange = false))
            assertThat(latest!!.animationState).isEqualTo(AnimatingOut)
        }

    @Test
    fun lockscreenVisible_noStatusBarViewsShown() =
        kosmos.runTest {
            val clockVisible by collectLastValue(underTest.isClockVisible)
            val notifIconsVisible by collectLastValue(underTest.isNotificationIconContainerVisible)
            val systemInfoVisible by collectLastValue(underTest.systemInfoCombinedVis)

            if (SceneContainerFlag.isEnabled) {
                kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Lockscreen)
            } else {
                fakeKeyguardTransitionRepository.sendTransitionSteps(
                    from = KeyguardState.GONE,
                    to = KeyguardState.LOCKSCREEN,
                    testScope = testScope,
                )
            }
            assertThat(clockVisible!!.visibility).isEqualTo(View.INVISIBLE)
            assertThat(notifIconsVisible!!.visibility).isEqualTo(View.GONE)
            assertThat(systemInfoVisible!!.baseVisibility.visibility).isEqualTo(View.GONE)
        }

    @Test
    fun bouncerVisible_noStatusBarViewsShown() =
        kosmos.runTest {
            val clockVisible by collectLastValue(underTest.isClockVisible)
            val notifIconsVisible by collectLastValue(underTest.isNotificationIconContainerVisible)
            val systemInfoVisible by collectLastValue(underTest.systemInfoCombinedVis)

            if (SceneContainerFlag.isEnabled) {
                kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Lockscreen)
                kosmos.sceneContainerRepository.showOverlay(Overlays.Bouncer)
            } else {
                fakeKeyguardTransitionRepository.sendTransitionSteps(
                    from = KeyguardState.LOCKSCREEN,
                    to = KeyguardState.PRIMARY_BOUNCER,
                    testScope = testScope,
                )
            }

            assertThat(clockVisible!!.visibility).isEqualTo(View.INVISIBLE)
            assertThat(notifIconsVisible!!.visibility).isEqualTo(View.GONE)
            assertThat(systemInfoVisible!!.baseVisibility.visibility).isEqualTo(View.GONE)
        }

    @Test
    fun keyguardIsOccluded_statusBarViewsShown() =
        kosmos.runTest {
            val clockVisible by collectLastValue(underTest.isClockVisible)
            val notifIconsVisible by collectLastValue(underTest.isNotificationIconContainerVisible)
            val systemInfoVisible by collectLastValue(underTest.systemInfoCombinedVis)

            if (SceneContainerFlag.isEnabled) {
                kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Lockscreen)
                kosmos.keyguardOcclusionRepository.setOccludedFromRemoteAnimation(
                    true,
                    taskInfo = null,
                )
            } else {
                fakeKeyguardTransitionRepository.sendTransitionSteps(
                    from = KeyguardState.LOCKSCREEN,
                    to = KeyguardState.OCCLUDED,
                    testScope = testScope,
                )
            }

            assertThat(clockVisible!!.visibility).isEqualTo(View.VISIBLE)
            assertThat(notifIconsVisible!!.visibility).isEqualTo(View.VISIBLE)
            assertThat(systemInfoVisible!!.baseVisibility.visibility).isEqualTo(View.VISIBLE)
        }

    @Test
    fun statusBarViewsShown_whenKeyguardAndShadeAreNotActive() =
        kosmos.runTest {
            val clockVisible by collectLastValue(underTest.isClockVisible)
            val notifIconsVisible by collectLastValue(underTest.isNotificationIconContainerVisible)
            val systemInfoVisible by collectLastValue(underTest.systemInfoCombinedVis)

            if (SceneContainerFlag.isEnabled) {
                kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Gone)
            } else {
                transitionKeyguardToGone()
                kosmos.shadeTestUtil.setShadeExpansion(0f)
            }

            assertThat(clockVisible!!.visibility).isEqualTo(View.VISIBLE)
            assertThat(notifIconsVisible!!.visibility).isEqualTo(View.VISIBLE)
            assertThat(systemInfoVisible!!.baseVisibility.visibility).isEqualTo(View.VISIBLE)
        }

    @Test
    @DisableSceneContainer
    fun shadeSlightlyShown_sceneFlagOff_statusBarViewsShown() =
        kosmos.runTest {
            val clockVisible by collectLastValue(underTest.isClockVisible)
            val notifIconsVisible by collectLastValue(underTest.isNotificationIconContainerVisible)
            val systemInfoVisible by collectLastValue(underTest.systemInfoCombinedVis)
            transitionKeyguardToGone()

            kosmos.shadeTestUtil.setShadeExpansion(0.1f)

            assertThat(clockVisible!!.visibility).isEqualTo(View.VISIBLE)
            assertThat(notifIconsVisible!!.visibility).isEqualTo(View.VISIBLE)
            assertThat(systemInfoVisible!!.baseVisibility.visibility).isEqualTo(View.VISIBLE)
        }

    @Test
    @DisableSceneContainer
    fun shadeHalfShown_sceneFlagOff_noStatusBarViewsShown() =
        kosmos.runTest {
            val clockVisible by collectLastValue(underTest.isClockVisible)
            val notifIconsVisible by collectLastValue(underTest.isNotificationIconContainerVisible)
            val systemInfoVisible by collectLastValue(underTest.systemInfoCombinedVis)
            transitionKeyguardToGone()

            kosmos.shadeTestUtil.setShadeExpansion(0.5f)

            assertThat(clockVisible!!.visibility).isEqualTo(View.INVISIBLE)
            assertThat(notifIconsVisible!!.visibility).isEqualTo(View.GONE)
            assertThat(systemInfoVisible!!.baseVisibility.visibility).isEqualTo(View.GONE)
        }

    @Test
    fun shadeFullyShown_noStatusBarViewsShown() =
        kosmos.runTest {
            val clockVisible by collectLastValue(underTest.isClockVisible)
            val notifIconsVisible by collectLastValue(underTest.isNotificationIconContainerVisible)
            val systemInfoVisible by collectLastValue(underTest.systemInfoCombinedVis)
            transitionKeyguardToGone()

            if (SceneContainerFlag.isEnabled) {
                kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Shade)
            } else {
                kosmos.shadeTestUtil.setShadeExpansion(1f)
            }

            assertThat(clockVisible!!.visibility).isEqualTo(View.INVISIBLE)
            assertThat(notifIconsVisible!!.visibility).isEqualTo(View.GONE)
            assertThat(systemInfoVisible!!.baseVisibility.visibility).isEqualTo(View.GONE)
        }

    /** Regression test for b/394257529#comment24. */
    @Test
    @DisableSceneContainer
    fun qqsToQsTransition_sceneFlagOff_statusBarViewsNeverShown() =
        kosmos.runTest {
            val clockVisible by collectLastValue(underTest.isClockVisible)
            val notifIconsVisible by collectLastValue(underTest.isNotificationIconContainerVisible)
            val systemInfoVisible by collectLastValue(underTest.systemInfoCombinedVis)
            transitionKeyguardToGone()

            kosmos.shadeTestUtil.setShadeAndQsExpansion(shadeExpansion = 1f, qsExpansion = 0f)
            assertThat(clockVisible!!.visibility).isEqualTo(View.INVISIBLE)
            assertThat(notifIconsVisible!!.visibility).isEqualTo(View.GONE)
            assertThat(systemInfoVisible!!.baseVisibility.visibility).isEqualTo(View.GONE)

            kosmos.shadeTestUtil.setShadeAndQsExpansion(shadeExpansion = 0.9f, qsExpansion = 0.1f)
            assertThat(clockVisible!!.visibility).isEqualTo(View.INVISIBLE)
            assertThat(notifIconsVisible!!.visibility).isEqualTo(View.GONE)
            assertThat(systemInfoVisible!!.baseVisibility.visibility).isEqualTo(View.GONE)

            kosmos.shadeTestUtil.setShadeAndQsExpansion(shadeExpansion = 0.6f, qsExpansion = 0.4f)
            assertThat(clockVisible!!.visibility).isEqualTo(View.INVISIBLE)
            assertThat(notifIconsVisible!!.visibility).isEqualTo(View.GONE)
            assertThat(systemInfoVisible!!.baseVisibility.visibility).isEqualTo(View.GONE)

            kosmos.shadeTestUtil.setShadeAndQsExpansion(shadeExpansion = 0.5f, qsExpansion = 0.5f)
            assertThat(clockVisible!!.visibility).isEqualTo(View.INVISIBLE)
            assertThat(notifIconsVisible!!.visibility).isEqualTo(View.GONE)
            assertThat(systemInfoVisible!!.baseVisibility.visibility).isEqualTo(View.GONE)

            kosmos.shadeTestUtil.setShadeAndQsExpansion(shadeExpansion = 0.2f, qsExpansion = 0.8f)
            assertThat(clockVisible!!.visibility).isEqualTo(View.INVISIBLE)
            assertThat(notifIconsVisible!!.visibility).isEqualTo(View.GONE)
            assertThat(systemInfoVisible!!.baseVisibility.visibility).isEqualTo(View.GONE)

            kosmos.shadeTestUtil.setShadeAndQsExpansion(shadeExpansion = 0f, qsExpansion = 1f)
            assertThat(clockVisible!!.visibility).isEqualTo(View.INVISIBLE)
            assertThat(notifIconsVisible!!.visibility).isEqualTo(View.GONE)
            assertThat(systemInfoVisible!!.baseVisibility.visibility).isEqualTo(View.GONE)
        }

    /** Regression test for b/394257529#comment24. */
    @Test
    @DisableSceneContainer
    fun qsToQqsTransition_sceneFlagOff_statusBarViewsNeverShown() =
        kosmos.runTest {
            val clockVisible by collectLastValue(underTest.isClockVisible)
            val notifIconsVisible by collectLastValue(underTest.isNotificationIconContainerVisible)
            val systemInfoVisible by collectLastValue(underTest.systemInfoCombinedVis)
            transitionKeyguardToGone()

            kosmos.shadeTestUtil.setShadeAndQsExpansion(shadeExpansion = 0f, qsExpansion = 1f)
            assertThat(clockVisible!!.visibility).isEqualTo(View.INVISIBLE)
            assertThat(notifIconsVisible!!.visibility).isEqualTo(View.GONE)
            assertThat(systemInfoVisible!!.baseVisibility.visibility).isEqualTo(View.GONE)

            kosmos.shadeTestUtil.setShadeAndQsExpansion(shadeExpansion = 0.3f, qsExpansion = 0.7f)
            assertThat(clockVisible!!.visibility).isEqualTo(View.INVISIBLE)
            assertThat(notifIconsVisible!!.visibility).isEqualTo(View.GONE)
            assertThat(systemInfoVisible!!.baseVisibility.visibility).isEqualTo(View.GONE)

            kosmos.shadeTestUtil.setShadeAndQsExpansion(shadeExpansion = 0.5f, qsExpansion = 0.5f)
            assertThat(clockVisible!!.visibility).isEqualTo(View.INVISIBLE)
            assertThat(notifIconsVisible!!.visibility).isEqualTo(View.GONE)
            assertThat(systemInfoVisible!!.baseVisibility.visibility).isEqualTo(View.GONE)

            kosmos.shadeTestUtil.setShadeAndQsExpansion(shadeExpansion = 0.7f, qsExpansion = 0.3f)
            assertThat(clockVisible!!.visibility).isEqualTo(View.INVISIBLE)
            assertThat(notifIconsVisible!!.visibility).isEqualTo(View.GONE)
            assertThat(systemInfoVisible!!.baseVisibility.visibility).isEqualTo(View.GONE)

            kosmos.shadeTestUtil.setShadeAndQsExpansion(shadeExpansion = 1f, qsExpansion = 0f)
            assertThat(clockVisible!!.visibility).isEqualTo(View.INVISIBLE)
            assertThat(notifIconsVisible!!.visibility).isEqualTo(View.GONE)
            assertThat(systemInfoVisible!!.baseVisibility.visibility).isEqualTo(View.GONE)
        }

    @EnableSceneContainer
    @Test
    fun shadeShown_noStatusBarViewsShown() =
        kosmos.runTest {
            val clockVisible by collectLastValue(underTest.isClockVisible)
            val notifIconsVisible by collectLastValue(underTest.isNotificationIconContainerVisible)
            val systemInfoVisible by collectLastValue(underTest.systemInfoCombinedVis)
            transitionKeyguardToGone()

            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Shade)

            assertThat(clockVisible!!.visibility).isEqualTo(View.INVISIBLE)
            assertThat(notifIconsVisible!!.visibility).isEqualTo(View.GONE)
            assertThat(systemInfoVisible!!.baseVisibility.visibility).isEqualTo(View.GONE)
        }

    @Test
    @DisableFlags(StatusBarShowIconsInSecureCamera.FLAG_NAME)
    fun secureCameraActive_noStatusBarViewsShown() =
        kosmos.runTest {
            val clockVisible by collectLastValue(underTest.isClockVisible)
            val notifIconsVisible by collectLastValue(underTest.isNotificationIconContainerVisible)
            val systemInfoVisible by collectLastValue(underTest.systemInfoCombinedVis)

            launchSecureCamera()

            assertThat(clockVisible!!.visibility).isEqualTo(View.INVISIBLE)
            assertThat(notifIconsVisible!!.visibility).isEqualTo(View.GONE)
            assertThat(systemInfoVisible!!.baseVisibility.visibility).isEqualTo(View.GONE)
        }

    @Test
    @EnableFlags(StatusBarShowIconsInSecureCamera.FLAG_NAME)
    fun secureCamera_noStatusBarViewsShown_duringAnyPartOfLaunch() =
        kosmos.runTest {
            setStatusBarWindowState(StatusBarWindowState.Showing)

            val clockVisible by collectLastValue(underTest.isClockVisible)
            val notifIconsVisible by collectLastValue(underTest.isNotificationIconContainerVisible)
            val systemInfoVisible by collectLastValue(underTest.systemInfoCombinedVis)

            launchSecureCamera()

            assertThat(clockVisible!!.visibility).isEqualTo(View.INVISIBLE)
            assertThat(notifIconsVisible!!.visibility).isEqualTo(View.GONE)
            assertThat(systemInfoVisible!!.baseVisibility.visibility).isEqualTo(View.GONE)

            setStatusBarWindowState(StatusBarWindowState.Hidden)

            assertThat(clockVisible!!.visibility).isEqualTo(View.INVISIBLE)
            assertThat(notifIconsVisible!!.visibility).isEqualTo(View.GONE)
            assertThat(systemInfoVisible!!.baseVisibility.visibility).isEqualTo(View.GONE)
        }

    @Test
    @EnableFlags(StatusBarShowIconsInSecureCamera.FLAG_NAME)
    fun secureCamera_statusBarViewsShown_ifWindowShowing() =
        kosmos.runTest {
            setStatusBarWindowState(StatusBarWindowState.Showing)

            val clockVisible by collectLastValue(underTest.isClockVisible)
            val notifIconsVisible by collectLastValue(underTest.isNotificationIconContainerVisible)
            val systemInfoVisible by collectLastValue(underTest.systemInfoCombinedVis)

            launchSecureCamera()
            setStatusBarWindowState(StatusBarWindowState.Hidden)

            assertThat(clockVisible!!.visibility).isEqualTo(View.INVISIBLE)
            assertThat(notifIconsVisible!!.visibility).isEqualTo(View.GONE)
            assertThat(systemInfoVisible!!.baseVisibility.visibility).isEqualTo(View.GONE)

            // WHEN user swipes down to show status bar
            setStatusBarWindowState(StatusBarWindowState.Showing)

            // THEN the icons can show
            assertThat(clockVisible!!.visibility).isEqualTo(View.VISIBLE)
            assertThat(notifIconsVisible!!.visibility).isEqualTo(View.VISIBLE)
            assertThat(systemInfoVisible!!.baseVisibility.visibility).isEqualTo(View.VISIBLE)

            // WHEN the status bar disappears after a few seconds
            setStatusBarWindowState(StatusBarWindowState.Hidden)

            // THEN we hide the icons again
            assertThat(clockVisible!!.visibility).isEqualTo(View.INVISIBLE)
            assertThat(notifIconsVisible!!.visibility).isEqualTo(View.GONE)
            assertThat(systemInfoVisible!!.baseVisibility.visibility).isEqualTo(View.GONE)
        }

    @Test
    fun areaTint_viewIsInDarkBounds_getsDarkTint() =
        kosmos.runTest {
            val displayId = testableContext.displayId
            fakeDarkIconRepository.darkState(displayId).value =
                SysuiDarkIconDispatcher.DarkChange(listOf(Rect(0, 0, 5, 5)), 0f, 0xAABBCC)

            val areaTint by collectLastValue(underTest.areaTint)

            val tint = areaTint?.tint(Rect(1, 1, 3, 3))

            assertThat(tint).isEqualTo(0xAABBCC)
        }

    @Test
    fun areaTint_viewIsNotInDarkBounds_getsDefaultTint() =
        kosmos.runTest {
            val displayId = testableContext.displayId
            fakeDarkIconRepository.darkState(displayId).value =
                SysuiDarkIconDispatcher.DarkChange(listOf(Rect(0, 0, 5, 5)), 0f, 0xAABBCC)

            val areaTint by collectLastValue(underTest.areaTint)

            val tint = areaTint?.tint(Rect(6, 6, 7, 7))

            assertThat(tint).isEqualTo(DarkIconDispatcher.DEFAULT_ICON_TINT)
        }

    @Test
    fun areaTint_viewIsInDarkBounds_darkBoundsChange_viewUpdates() =
        kosmos.runTest {
            val displayId = testableContext.displayId
            fakeDarkIconRepository.darkState(displayId).value =
                SysuiDarkIconDispatcher.DarkChange(listOf(Rect(0, 0, 5, 5)), 0f, 0xAABBCC)

            val areaTint by collectLastValue(underTest.areaTint)

            var tint = areaTint?.tint(Rect(1, 1, 3, 3))

            assertThat(tint).isEqualTo(0xAABBCC)

            // Dark region moves 5px to the right
            fakeDarkIconRepository.darkState(displayId).value =
                SysuiDarkIconDispatcher.DarkChange(listOf(Rect(5, 0, 10, 5)), 0f, 0xAABBCC)

            tint = areaTint?.tint(Rect(1, 1, 3, 3))

            assertThat(tint).isEqualTo(DarkIconDispatcher.DEFAULT_ICON_TINT)
        }

    @Test
    fun iconBlockList_followsInteractor() =
        kosmos.runTest {
            setHomeStatusBarIconBlockList(listOf("icon1", "icon2"))

            val latest by collectLastValue(underTest.iconBlockList)

            assertThat(latest).containsExactly("icon1", "icon2")
        }

    @Test
    @DisableSceneContainer
    fun launcherToDream_sceneFlagOff_noStatusBarViewsShown() =
        kosmos.runTest {
            val clockVisible by collectLastValue(underTest.isClockVisible)
            val notifIconsVisible by collectLastValue(underTest.isNotificationIconContainerVisible)
            val systemInfoVisible by collectLastValue(underTest.systemInfoCombinedVis)

            // Gone to dreaming transition starts
            keyguardInteractor.setDreaming(true)
            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.GONE,
                to = KeyguardState.DREAMING,
                throughTransitionState = TransitionState.STARTED,
                testScope = testScope,
            )

            assertThat(clockVisible!!.visibility).isEqualTo(View.INVISIBLE)
            assertThat(notifIconsVisible!!.visibility).isEqualTo(View.GONE)
            assertThat(systemInfoVisible!!.baseVisibility.visibility).isEqualTo(View.GONE)
        }

    @Test
    @EnableSceneContainer
    fun launcherToDream_sceneFlagOn_noStatusBarViewsShown() =
        kosmos.runTest {
            val clockVisible by collectLastValue(underTest.isClockVisible)
            val notifIconsVisible by collectLastValue(underTest.isNotificationIconContainerVisible)
            val systemInfoVisible by collectLastValue(underTest.systemInfoCombinedVis)

            sceneContainerRepository.instantlyTransitionTo(Scenes.Gone)

            assertThat(clockVisible!!.visibility).isEqualTo(View.VISIBLE)
            assertThat(notifIconsVisible!!.visibility).isEqualTo(View.VISIBLE)
            assertThat(systemInfoVisible!!.baseVisibility.visibility).isEqualTo(View.VISIBLE)

            sceneContainerRepository.instantlyTransitionTo(Scenes.Dream)

            assertThat(clockVisible!!.visibility).isEqualTo(View.INVISIBLE)
            assertThat(notifIconsVisible!!.visibility).isEqualTo(View.GONE)
            assertThat(systemInfoVisible!!.baseVisibility.visibility).isEqualTo(View.GONE)
        }

    @Test
    @DisableSceneContainer
    fun dreamingToLauncher_sceneFlagOff_statusBarViewsShown() =
        kosmos.runTest {
            val clockVisible by collectLastValue(underTest.isClockVisible)
            val notifIconsVisible by collectLastValue(underTest.isNotificationIconContainerVisible)
            val systemInfoVisible by collectLastValue(underTest.systemInfoCombinedVis)

            // Dream stops and returns to launcher
            keyguardInteractor.setDreaming(false)
            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.DREAMING,
                to = KeyguardState.GONE,
                testScope = testScope,
            )

            assertThat(clockVisible!!.visibility).isEqualTo(View.VISIBLE)
            assertThat(notifIconsVisible!!.visibility).isEqualTo(View.VISIBLE)
            assertThat(systemInfoVisible!!.baseVisibility.visibility).isEqualTo(View.VISIBLE)
        }

    @Test
    @DisableSceneContainer
    fun finishedInDreaming_noStatusBarViewsShown() =
        kosmos.runTest {
            val clockVisible by collectLastValue(underTest.isClockVisible)
            val notifIconsVisible by collectLastValue(underTest.isNotificationIconContainerVisible)
            val systemInfoVisible by collectLastValue(underTest.systemInfoCombinedVis)

            // Transition from gone to dreaming finishes.
            keyguardInteractor.setDreaming(true)
            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.GONE,
                to = KeyguardState.DREAMING,
                testScope = testScope,
            )

            assertThat(clockVisible!!.visibility).isEqualTo(View.INVISIBLE)
            assertThat(notifIconsVisible!!.visibility).isEqualTo(View.GONE)
            assertThat(systemInfoVisible!!.baseVisibility.visibility).isEqualTo(View.GONE)
        }

    @Test
    @EnableSceneContainer
    fun dreamingToLauncher_sceneFlagOn_statusBarViewsShown() =
        kosmos.runTest {
            val clockVisible by collectLastValue(underTest.isClockVisible)
            val notifIconsVisible by collectLastValue(underTest.isNotificationIconContainerVisible)
            val systemInfoVisible by collectLastValue(underTest.systemInfoCombinedVis)

            sceneContainerRepository.instantlyTransitionTo(Scenes.Dream)

            assertThat(clockVisible!!.visibility).isEqualTo(View.INVISIBLE)
            assertThat(notifIconsVisible!!.visibility).isEqualTo(View.GONE)
            assertThat(systemInfoVisible!!.baseVisibility.visibility).isEqualTo(View.GONE)

            sceneContainerRepository.instantlyTransitionTo(Scenes.Gone)

            assertThat(clockVisible!!.visibility).isEqualTo(View.VISIBLE)
            assertThat(notifIconsVisible!!.visibility).isEqualTo(View.VISIBLE)
            assertThat(systemInfoVisible!!.baseVisibility.visibility).isEqualTo(View.VISIBLE)
        }

    @Test
    @DisableSceneContainer
    fun startsDreaming_fromLockscreen_noStatusBarViewsShown() =
        kosmos.runTest {
            val clockVisible by collectLastValue(underTest.isClockVisible)
            val notifIconsVisible by collectLastValue(underTest.isNotificationIconContainerVisible)
            val systemInfoVisible by collectLastValue(underTest.systemInfoCombinedVis)

            // Starts transitioning to dream from any state other than launcher (lock screen)
            keyguardInteractor.setDreaming(true)
            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.DREAMING,
                throughTransitionState = TransitionState.STARTED,
                testScope = testScope,
            )
            // home status bar should remain hidden
            assertThat(clockVisible!!.visibility).isEqualTo(View.INVISIBLE)
            assertThat(notifIconsVisible!!.visibility).isEqualTo(View.GONE)
            assertThat(systemInfoVisible!!.baseVisibility.visibility).isEqualTo(View.GONE)
        }

    @Test
    fun onClockClicked_hidesInputMethodPicker() =
        kosmos.runTest {
            imeIndicatorChipInteractor.hideInputMethodPicker(testableContext.displayId)
            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId).isNull()

            underTest.onClockClicked()
            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId).isNull()

            imeIndicatorChipInteractor.toggleInputMethodPicker(testableContext.displayId)
            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId).isNotNull()

            underTest.onClockClicked()
            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId).isNull()
        }

    @Test
    fun onSpacerClicked_hidesInputMethodPicker() =
        kosmos.runTest {
            imeIndicatorChipInteractor.hideInputMethodPicker(testableContext.displayId)
            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId).isNull()

            underTest.onSpacerClicked()
            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId).isNull()

            imeIndicatorChipInteractor.toggleInputMethodPicker(testableContext.displayId)
            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId).isNotNull()

            underTest.onSpacerClicked()
            assertThat(fakeInputMethodRepository.inputMethodPickerShownDisplayId).isNull()
        }

    @Test
    @EnableFlags(FLAG_SCENE_CONTAINER, StatusBarForDesktop.FLAG_NAME, FLAG_DUAL_SHADE)
    fun onQuickSettingsChipClicked_qsShadeIsOpen_collapsesShade() =
        kosmos.runTest {
            enableDualShade()
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)

            sceneContainerRepository.showOverlay(Overlays.QuickSettingsShade)
            setSceneTransition(
                ObservableTransitionState.Idle(
                    sceneInteractor.currentScene.value,
                    checkNotNull(currentOverlays),
                ),
                skipChangeScene = true,
            )
            assertThat(currentOverlays).containsExactly(Overlays.QuickSettingsShade)

            underTest.onQuickSettingsChipClicked()
            assertThat(currentOverlays).doesNotContain(Overlays.QuickSettingsShade)
        }

    @Test
    @EnableFlags(FLAG_SCENE_CONTAINER, StatusBarForDesktop.FLAG_NAME, FLAG_DUAL_SHADE)
    fun onQuickSettingsChipClicked_qsShadeIsClosed_expandsShade() =
        kosmos.runTest {
            enableDualShade()
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)

            assertThat(currentOverlays).doesNotContain(Overlays.QuickSettingsShade)

            underTest.onQuickSettingsChipClicked()

            assertThat(currentOverlays).contains(Overlays.QuickSettingsShade)
        }

    @Test
    @EnableFlags(FLAG_SCENE_CONTAINER, StatusBarForDesktop.FLAG_NAME, FLAG_DUAL_SHADE)
    fun onQuickSettingsChipClicked_displayDiffers_setsExpansionIntent() =
        kosmos.runTest {
            enableDualShade()
            val fakeShadeDisplaysRepository = kosmos.fakeShadeDisplaysRepository
            val statusBarTouchShadeDisplayPolicy = kosmos.statusBarTouchShadeDisplayPolicy

            // ViewModel is for display 0 (default). Set active display to 2.
            fakeShadeDisplaysRepository.setDisplayId(2)
            kosmos.displayRepository.addDisplays(
                display(id = 0, type = android.view.Display.TYPE_INTERNAL)
            )

            underTest.onQuickSettingsChipClicked()

            assertThat(statusBarTouchShadeDisplayPolicy.consumeExpansionIntent())
                .isEqualTo(kosmos.qsElement)
        }

    @Test
    @EnableFlags(FLAG_SCENE_CONTAINER, StatusBarForDesktop.FLAG_NAME, FLAG_DUAL_SHADE)
    fun onNotificationIconChipClicked_notificationsShadeIsOpen_collapsesShade() =
        kosmos.runTest {
            enableDualShade()
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)

            sceneContainerRepository.showOverlay(Overlays.NotificationsShade)
            setSceneTransition(
                ObservableTransitionState.Idle(
                    checkNotNull(currentScene),
                    checkNotNull(currentOverlays),
                ),
                skipChangeScene = true,
            )
            assertThat(currentOverlays).contains(Overlays.NotificationsShade)

            underTest.onNotificationIconChipClicked()

            assertThat(currentOverlays).doesNotContain(Overlays.NotificationsShade)
        }

    @Test
    @EnableFlags(FLAG_SCENE_CONTAINER, StatusBarForDesktop.FLAG_NAME, FLAG_DUAL_SHADE)
    fun onNotificationIconChipClicked_displayDiffers_setsExpansionIntent() =
        kosmos.runTest {
            enableDualShade()
            val fakeShadeDisplaysRepository = kosmos.fakeShadeDisplaysRepository
            val statusBarTouchShadeDisplayPolicy = kosmos.statusBarTouchShadeDisplayPolicy

            // ViewModel is for display 0 (default). Set active display to 2.
            fakeShadeDisplaysRepository.setDisplayId(2)
            kosmos.displayRepository.addDisplays(
                display(id = 0, type = android.view.Display.TYPE_INTERNAL)
            )

            underTest.onNotificationIconChipClicked()

            assertThat(statusBarTouchShadeDisplayPolicy.consumeExpansionIntent())
                .isEqualTo(kosmos.notificationElement)
        }

    @EnableSceneContainer
    @EnableFlags(Flags.FLAG_STATUS_BAR_EVENT_FORWARDING_MODERNIZATION, FLAG_DUAL_SHADE)
    @Test
    fun onStatusBarLongPressed_expandsShade() =
        kosmos.runTest {
            enableDualShade()
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)

            assertThat(currentOverlays).doesNotContain(Overlays.NotificationsShade)

            underTest.onStatusBarLongPressed()

            assertThat(currentOverlays).contains(Overlays.NotificationsShade)
        }

    @Test
    @EnableFlags(FLAG_SCENE_CONTAINER, StatusBarForDesktop.FLAG_NAME, FLAG_DUAL_SHADE)
    fun onNotificationIconChipClicked_notificationsShadeIsClosed_expandsShade() =
        kosmos.runTest {
            enableDualShade()
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            assertThat(currentOverlays).doesNotContain(Overlays.NotificationsShade)

            underTest.onNotificationIconChipClicked()

            assertThat(currentOverlays).contains(Overlays.NotificationsShade)
        }

    @Test
    fun hasStatusBarNotifications_ifNotificationsExist_isTrue() =
        kosmos.runTest {
            assertThat(underTest.hasStatusBarNotifications).isFalse()

            activeNotificationListRepository.activeNotifications.value =
                getPopulatedActiveNotificationsStore()

            assertThat(underTest.hasStatusBarNotifications).isTrue()
        }

    @Test
    fun onShadeExpansionIntent_notConsumed_setsTargetDisplayAndIntent() =
        kosmos.runTest {
            displayRepository.addDisplays(display(id = EXTERNAL_DISPLAY, type = TYPE_EXTERNAL))

            val underTest = homeStatusBarViewModelFactory(EXTERNAL_DISPLAY)
            val displayId by collectLastValue(statusBarTouchShadeDisplayPolicy.displayId)

            val eventX = 123f
            val statusBarWidth = 1080

            underTest.onShadeExpansionIntent(eventX, statusBarWidth, isConsumed = false)

            assertThat(displayId).isEqualTo(EXTERNAL_DISPLAY)
            assertThat(statusBarTouchShadeDisplayPolicy.consumeExpansionIntent()).isNotNull()
        }

    @Test
    fun onShadeExpansionIntent_isConsumed_setsTargetDisplayOnly() =
        kosmos.runTest {
            displayRepository.addDisplays(display(id = EXTERNAL_DISPLAY, type = TYPE_EXTERNAL))

            val underTest = homeStatusBarViewModelFactory(EXTERNAL_DISPLAY)
            val displayId by collectLastValue(statusBarTouchShadeDisplayPolicy.displayId)

            val eventX = 123f
            val statusBarWidth = 1080

            underTest.onShadeExpansionIntent(eventX, statusBarWidth, isConsumed = true)

            assertThat(displayId).isEqualTo(EXTERNAL_DISPLAY)
            assertThat(statusBarTouchShadeDisplayPolicy.consumeExpansionIntent()).isNull()
        }

    @Test
    fun onStatusBarTap_callsScrollToTopInteractor() =
        kosmos.runTest {
            val eventX = 150f
            underTest.onStatusBarTap(eventX)

            assertThat(fakeScrollToTop.lastScrollToTopDisplayId).isEqualTo(DEFAULT_DISPLAY)
            assertThat(fakeScrollToTop.lastScrollToTopX).isEqualTo(150)
        }

    @Test
    fun deviceNotProvisioned_quickSettingsChipNotClickable() =
        kosmos.runTest {
            fakeDeviceProvisioningRepository.setDeviceProvisioned(false)

            assertThat(underTest.isQuickSettingsChipClickable).isFalse()
        }

    @Test
    fun deviceProvisioned_quickSettingsChipClickable() =
        kosmos.runTest {
            fakeDeviceProvisioningRepository.setDeviceProvisioned(true)

            assertThat(underTest.isQuickSettingsChipClickable).isTrue()
        }

    @Test
    fun deviceNotProvisioned_notificationsChipNotClickable() =
        kosmos.runTest {
            fakeDeviceProvisioningRepository.setDeviceProvisioned(false)

            assertThat(underTest.isNotificationsChipClickable).isFalse()
        }

    @Test
    fun deviceProvisioned_notificationsChipClickable() =
        kosmos.runTest {
            fakeDeviceProvisioningRepository.setDeviceProvisioned(true)

            assertThat(underTest.isNotificationsChipClickable).isTrue()
        }

    @Test
    @EnableSceneContainer
    @EnableFlags(Flags.FLAG_SIGN_OUT_BUTTON_ON_KEYGUARD_STATUS_BAR)
    fun signOutButton_isVisible_whenUserManagerLogoutIsEnabled_andDeviceIsProvisioned_andSceneIsLockscreen() {
        kosmos.runTest {
            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Lockscreen)
            fakeKeyguardRepository.setIsSignOutButtonOnStatusBarEnabledInConfig(true)
            fakeUserRepository.setUserManagerLogoutEnabled(true)
            fakeUserRepository.setPolicyManagerLogoutEnabled(false)
            fakeDeviceProvisioningRepository.setDeviceProvisioned(true)

            assertThat(underTest.isSignOutButtonVisible).isTrue()
        }
    }

    @Test
    @EnableSceneContainer
    @EnableFlags(Flags.FLAG_SIGN_OUT_BUTTON_ON_KEYGUARD_STATUS_BAR)
    fun signOutButton_isNotVisible_whenSceneIsNotLockscreen() {
        kosmos.runTest {
            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Gone)
            fakeKeyguardRepository.setIsSignOutButtonOnStatusBarEnabledInConfig(true)
            fakeUserRepository.setUserManagerLogoutEnabled(true)
            fakeUserRepository.setPolicyManagerLogoutEnabled(false)
            fakeDeviceProvisioningRepository.setDeviceProvisioned(true)

            assertThat(underTest.isSignOutButtonVisible).isFalse()
        }
    }

    @Test
    @EnableSceneContainer
    @EnableFlags(Flags.FLAG_SIGN_OUT_BUTTON_ON_KEYGUARD_STATUS_BAR)
    fun signOutButton_isNotVisible_whenUserManagerLogoutIsDisabled() {
        kosmos.runTest {
            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Lockscreen)
            fakeKeyguardRepository.setIsSignOutButtonOnStatusBarEnabledInConfig(true)
            fakeUserRepository.setUserManagerLogoutEnabled(false)
            fakeUserRepository.setPolicyManagerLogoutEnabled(true)
            fakeDeviceProvisioningRepository.setDeviceProvisioned(true)

            assertThat(underTest.isSignOutButtonVisible).isFalse()
        }
    }

    @Test
    @EnableSceneContainer
    @EnableFlags(Flags.FLAG_SIGN_OUT_BUTTON_ON_KEYGUARD_STATUS_BAR)
    fun signOutButton_isNotVisible_whenDeviceIsNotProvisioned() {
        kosmos.runTest {
            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Lockscreen)
            fakeKeyguardRepository.setIsSignOutButtonOnStatusBarEnabledInConfig(true)
            fakeUserRepository.setUserManagerLogoutEnabled(true)
            fakeUserRepository.setPolicyManagerLogoutEnabled(true)
            fakeDeviceProvisioningRepository.setDeviceProvisioned(false)

            assertThat(underTest.isSignOutButtonVisible).isFalse()
        }
    }

    @Test
    @EnableSceneContainer
    @EnableFlags(Flags.FLAG_SIGN_OUT_BUTTON_ON_KEYGUARD_STATUS_BAR)
    fun signOutButton_isNotVisible_whenDisabledInConfig() {
        kosmos.runTest {
            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Lockscreen)
            fakeKeyguardRepository.setIsSignOutButtonOnStatusBarEnabledInConfig(false)
            fakeUserRepository.setUserManagerLogoutEnabled(true)
            fakeUserRepository.setPolicyManagerLogoutEnabled(false)
            fakeDeviceProvisioningRepository.setDeviceProvisioned(true)

            assertThat(underTest.isSignOutButtonVisible).isFalse()
        }
    }

    @Test
    @EnableSceneContainer
    @EnableFlags(Flags.FLAG_SIGN_OUT_BUTTON_ON_KEYGUARD_STATUS_BAR)
    fun onSignOut_logsOutWithUserManager_whenUserManagerLogoutIsEnabled() {
        kosmos.runTest {
            fakeKeyguardRepository.setIsSignOutButtonOnStatusBarEnabledInConfig(true)
            val logoutToSystemUserCount = fakeUserRepository.logOutWithUserManagerCallCount
            fakeUserRepository.setUserManagerLogoutEnabled(true)
            fakeUserRepository.setPolicyManagerLogoutEnabled(false)
            underTest.onSignOut()

            assertThat(fakeUserRepository.logOutWithUserManagerCallCount)
                .isEqualTo(logoutToSystemUserCount + 1)
        }
    }

    @Test
    @EnableSceneContainer
    fun isHomeStatusBarAllowed_shadeOnExternalDisplay_statusBarOnDefaultDisplay_isVisible() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isHomeStatusBarAllowed)

            // GIVEN shade is moving to external display
            kosmos.fakeShadeDisplaysRepository.setPendingDisplayId(EXTERNAL_DISPLAY)
            // AND shade window is still on default display (simulating delay)
            kosmos.fakeShadeDisplaysRepository.setDisplayId(DEFAULT_DISPLAY)

            // WHEN scene changes to Shade
            kosmos.shadeTestUtil.setShadeExpansion(1f)
            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Shade)
            runCurrent()

            // THEN status bar should be visible on default display
            assertThat(latest).isTrue()
        }

    @Test
    @EnableFlags(FLAG_SCENE_CONTAINER, StatusBarForDesktop.FLAG_NAME, FLAG_DUAL_SHADE)
    fun isQuickSettingsChipHighlighted_qsExpandedOnThisDisplay_true() =
        kosmos.runTest {
            enableDualShade()
            fakeShadeDisplaysRepository.setPendingDisplayId(DEFAULT_DISPLAY)

            showOverlay(Overlays.QuickSettingsShade)

            assertThat(underTest.isQuickSettingsChipHighlighted).isTrue()
        }

    @Test
    @EnableFlags(FLAG_SCENE_CONTAINER, StatusBarForDesktop.FLAG_NAME, FLAG_DUAL_SHADE)
    fun isQuickSettingsChipHighlighted_qsExpandedOnOtherDisplay_false() =
        kosmos.runTest {
            enableDualShade()
            fakeShadeDisplaysRepository.setPendingDisplayId(EXTERNAL_DISPLAY)

            showOverlay(Overlays.QuickSettingsShade)

            assertThat(underTest.isQuickSettingsChipHighlighted).isFalse()
        }

    @Test
    @EnableFlags(FLAG_SCENE_CONTAINER, StatusBarForDesktop.FLAG_NAME, FLAG_DUAL_SHADE)
    fun isQuickSettingsChipHighlighted_qsNotExpanded_false() =
        kosmos.runTest {
            enableDualShade()
            fakeShadeDisplaysRepository.setPendingDisplayId(DEFAULT_DISPLAY)

            // Default state is not expanded
            assertThat(underTest.isQuickSettingsChipHighlighted).isFalse()
        }

    @Test
    @EnableFlags(FLAG_SCENE_CONTAINER, StatusBarForDesktop.FLAG_NAME, FLAG_DUAL_SHADE)
    fun isNotificationsChipHighlighted_notificationsExpandedOnThisDisplay_true() =
        kosmos.runTest {
            enableDualShade()
            fakeShadeDisplaysRepository.setPendingDisplayId(DEFAULT_DISPLAY)

            showOverlay(Overlays.NotificationsShade)

            assertThat(underTest.isNotificationsChipHighlighted).isTrue()
        }

    @Test
    @EnableFlags(FLAG_SCENE_CONTAINER, StatusBarForDesktop.FLAG_NAME, FLAG_DUAL_SHADE)
    fun isNotificationsChipHighlighted_notificationsExpandedOnOtherDisplay_false() =
        kosmos.runTest {
            enableDualShade()
            fakeShadeDisplaysRepository.setPendingDisplayId(EXTERNAL_DISPLAY)

            showOverlay(Overlays.NotificationsShade)

            assertThat(underTest.isNotificationsChipHighlighted).isFalse()
        }

    @Test
    @EnableFlags(FLAG_SCENE_CONTAINER, StatusBarForDesktop.FLAG_NAME, FLAG_DUAL_SHADE)
    fun isNotificationsChipHighlighted_notificationsNotExpanded_false() =
        kosmos.runTest {
            enableDualShade()
            fakeShadeDisplaysRepository.setPendingDisplayId(DEFAULT_DISPLAY)

            // Default state is not expanded
            assertThat(underTest.isNotificationsChipHighlighted).isFalse()
        }

    private fun activeNotificationsStore(notifications: List<ActiveNotificationModel>) =
        ActiveNotificationsStore.Builder()
            .apply { notifications.forEach(::addIndividualNotif) }
            .build()

    private fun Kosmos.showOverlay(overlay: OverlayKey) {
        val currentScene by collectLastValue(sceneInteractor.currentScene)
        val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)

        sceneInteractor.showOverlay(overlay, "reason")
        setSceneTransition(
            ObservableTransitionState.Idle(
                checkNotNull(currentScene),
                checkNotNull(currentOverlays),
            ),
            skipChangeScene = true,
        )
    }

    private val testNotifications by lazy {
        listOf(activeNotificationModel(key = "notif1"), activeNotificationModel(key = "notif2"))
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf().andSceneContainer()
        }

        const val EXTERNAL_DISPLAY = 1
    }
}
