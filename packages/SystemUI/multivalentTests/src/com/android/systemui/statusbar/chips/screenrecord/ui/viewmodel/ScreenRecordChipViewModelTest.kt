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

package com.android.systemui.statusbar.chips.screenrecord.ui.viewmodel

import android.content.Context
import android.content.DialogInterface
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.view.View
import android.view.ViewRootImpl
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.animation.DialogTransitionAnimator
import com.android.systemui.animation.Expandable
import com.android.systemui.animation.mockDialogTransitionAnimator
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.mediaprojection.data.model.MediaProjectionState
import com.android.systemui.mediaprojection.data.repository.fakeMediaProjectionRepository
import com.android.systemui.mediaprojection.taskswitcher.FakeActivityTaskManager
import com.android.systemui.plugins.activityStarter
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiState
import com.android.systemui.screencapture.domain.interactor.screenCaptureUiInteractor
import com.android.systemui.screenrecord.data.model.ScreenRecordModel
import com.android.systemui.screenrecord.data.repository.screenRecordRepository
import com.android.systemui.statusbar.chips.mediaprojection.domain.interactor.MediaProjectionChipInteractorTest.Companion.setUpPackageManagerForMediaProjection
import com.android.systemui.statusbar.chips.screenrecord.ui.view.EndScreenRecordingDialogDelegate
import com.android.systemui.statusbar.chips.sharetoapp.ui.viewmodel.shareToAppChipViewModel
import com.android.systemui.statusbar.chips.ui.model.Chronometer
import com.android.systemui.statusbar.chips.ui.model.ColorsModel
import com.android.systemui.statusbar.chips.ui.model.EventTime
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import com.android.systemui.statusbar.chips.ui.view.ChipBackgroundContainer
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipsWithNotifsViewModelTest.Companion.getStopActionFromDialog
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.statusbar.phone.mockSystemUIDialogFactory
import com.android.systemui.testKosmos
import com.android.systemui.util.time.fakeSystemClock
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenRecordChipViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val underTest by lazy { kosmos.screenRecordChipViewModel }
    private val testScope = kosmos.testScope
    private val systemClock = kosmos.fakeSystemClock

    private val screenRecordRepo
        get() = kosmos.screenRecordRepository

    private val mediaProjectionRepo
        get() = kosmos.fakeMediaProjectionRepository

    private val mockSystemUIDialog = mock<SystemUIDialog>()
    private val chipBackgroundView =
        mock<ChipBackgroundContainer> { on { context } doReturn context }
    private val chipView =
        mock<View> {
            on {
                requireViewById<ChipBackgroundContainer>(R.id.ongoing_activity_chip_background)
            } doReturn chipBackgroundView
            on { context } doReturn context
        }
    private val viewRootImpl = mock<ViewRootImpl> { on { view } doReturn chipView }
    private val dialogTransitionController =
        mock<DialogTransitionAnimator.Controller> { on { viewRoot } doReturn viewRootImpl }
    private val mockExpandable: Expandable =
        mock<Expandable> {
            on { dialogTransitionController(any()) } doReturn dialogTransitionController
        }

    @Before
    fun setUp() {
        setUpPackageManagerForMediaProjection(kosmos)
        whenever(
                kosmos.mockSystemUIDialogFactory.create(
                    any<EndScreenRecordingDialogDelegate>(),
                    any<Context>(),
                )
            )
            .thenReturn(mockSystemUIDialog)
    }

    @Test
    fun chip_doingNothingState_isHidden() =
        testScope.runTest {
            val latest by collectLastValue(underTest.chip)

            screenRecordRepo.screenRecordState.value = ScreenRecordModel.DoingNothing

            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Inactive::class.java)
        }

    @Test
    fun chip_startingState_isShownAsCountdownWithoutIconOrClickListener() =
        testScope.runTest {
            val latest by collectLastValue(underTest.chip)

            screenRecordRepo.screenRecordState.value = ScreenRecordModel.Starting(400)

            assertThat((latest as OngoingActivityChipModel.Active).content)
                .isInstanceOf(OngoingActivityChipModel.Content.Countdown::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isImportantForPrivacy).isTrue()
            assertThat((latest as OngoingActivityChipModel.Active).instanceId).isNotNull()
            assertThat((latest as OngoingActivityChipModel.Active).icon).isNull()
        }

    // The millis we typically get from [ScreenRecordRepository] are around 2995, 1995, and 995.
    @Test
    fun chip_startingState_millis2995_is3() =
        testScope.runTest {
            val latest by collectLastValue(underTest.chip)

            screenRecordRepo.screenRecordState.value = ScreenRecordModel.Starting(2995)

            assertThat(
                    ((latest as OngoingActivityChipModel.Active).content
                            as OngoingActivityChipModel.Content.Countdown)
                        .secondsUntilStarted
                )
                .isEqualTo(3)
        }

    @Test
    fun chip_startingState_millis1995_is2() =
        testScope.runTest {
            val latest by collectLastValue(underTest.chip)

            screenRecordRepo.screenRecordState.value = ScreenRecordModel.Starting(1995)

            assertThat(
                    ((latest as OngoingActivityChipModel.Active).content
                            as OngoingActivityChipModel.Content.Countdown)
                        .secondsUntilStarted
                )
                .isEqualTo(2)
        }

    @Test
    fun chip_startingState_millis995_is1() =
        testScope.runTest {
            val latest by collectLastValue(underTest.chip)

            screenRecordRepo.screenRecordState.value = ScreenRecordModel.Starting(995)

            assertThat(
                    ((latest as OngoingActivityChipModel.Active).content
                            as OngoingActivityChipModel.Content.Countdown)
                        .secondsUntilStarted
                )
                .isEqualTo(1)
        }

    @Test
    fun chip_recordingState_isShownAsTimerWithIcon() =
        testScope.runTest {
            val latest by collectLastValue(underTest.chip)

            screenRecordRepo.screenRecordState.value = ScreenRecordModel.Recording

            assertThat((latest as OngoingActivityChipModel.Active).content)
                .isInstanceOf(OngoingActivityChipModel.Content.Timer::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isImportantForPrivacy).isTrue()
            assertThat((latest as OngoingActivityChipModel.Active).instanceId).isNotNull()
            val icon =
                (((latest as OngoingActivityChipModel.Active).icon)
                        as OngoingActivityChipModel.ChipIcon.SingleColorIcon)
                    .impl as Icon.Resource
            assertThat(icon.resId).isEqualTo(R.drawable.ic_screenrecord)
            assertThat(icon.contentDescription).isNotNull()
        }

    @Test
    @DisableFlags(
        Flags.FLAG_NEW_SCREEN_RECORD_TOOLBAR,
        Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE,
        Flags.FLAG_LARGE_SCREEN_RECORDING,
    )
    fun chip_recordingStoppedFromDialog_screenRecordAndShareToAppChipImmediatelyHidden() =
        testScope.runTest {
            val latest by collectLastValue(underTest.chip)
            val latestShareToApp by collectLastValue(kosmos.shareToAppChipViewModel.chip)

            // On real devices, when screen recording is active then share-to-app is also active
            // because screen record is just a special case of share-to-app where the app receiving
            // the share is SysUI
            screenRecordRepo.screenRecordState.value = ScreenRecordModel.Recording
            mediaProjectionRepo.mediaProjectionState.value =
                MediaProjectionState.Projecting.EntireScreen("fake.package")

            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            assertThat(latestShareToApp).isInstanceOf(OngoingActivityChipModel.Active::class.java)

            // WHEN the stop action on the dialog is clicked
            val dialogStopAction =
                getStopActionFromDialog(
                    latest,
                    chipView,
                    mockExpandable,
                    mockSystemUIDialog,
                    kosmos,
                )
            dialogStopAction.onClick(mock<DialogInterface>(), 0)

            // THEN both the screen record chip and the share-to-app chip are immediately hidden...
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Inactive::class.java)
            assertThat(latestShareToApp).isInstanceOf(OngoingActivityChipModel.Inactive::class.java)
            // ...even though the repos still say it's recording
            assertThat(screenRecordRepo.screenRecordState.value)
                .isEqualTo(ScreenRecordModel.Recording)
            assertThat(mediaProjectionRepo.mediaProjectionState.value)
                .isInstanceOf(MediaProjectionState.Projecting::class.java)

            // AND we specify no animation
            assertThat((latest as OngoingActivityChipModel.Inactive).shouldAnimate).isFalse()
        }

    @Test
    fun chip_startingState_colorsAreRed() =
        testScope.runTest {
            val latest by collectLastValue(underTest.chip)

            screenRecordRepo.screenRecordState.value = ScreenRecordModel.Starting(2000L)

            assertThat((latest as OngoingActivityChipModel.Active).colors)
                .isEqualTo(ColorsModel.Red)
        }

    @Test
    fun chip_recordingState_colorsAreRed() =
        testScope.runTest {
            val latest by collectLastValue(underTest.chip)

            screenRecordRepo.screenRecordState.value = ScreenRecordModel.Recording

            assertThat((latest as OngoingActivityChipModel.Active).colors)
                .isEqualTo(ColorsModel.Red)
        }

    @Test
    fun chip_startingState_nullNotificationKey() =
        testScope.runTest {
            val latest by collectLastValue(underTest.chip)

            screenRecordRepo.screenRecordState.value = ScreenRecordModel.Starting(2000L)

            assertThat((latest as OngoingActivityChipModel.Active).notificationKey).isNull()
        }

    @Test
    fun chip_recordingState_nullNotificationKey() =
        testScope.runTest {
            val latest by collectLastValue(underTest.chip)

            screenRecordRepo.screenRecordState.value = ScreenRecordModel.Recording

            assertThat((latest as OngoingActivityChipModel.Active).notificationKey).isNull()
        }

    @Test
    fun chip_timeResetsOnEachNewRecording() =
        testScope.runTest {
            val latest by collectLastValue(underTest.chip)

            systemClock.setElapsedRealtime(1234)
            screenRecordRepo.screenRecordState.value = ScreenRecordModel.Recording

            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            assertThat(
                    ((latest as OngoingActivityChipModel.Active).content
                            as OngoingActivityChipModel.Content.Timer)
                        .value
                )
                .isEqualTo(Chronometer.Running(EventTime.ElapsedRealtime(1234)))

            screenRecordRepo.screenRecordState.value = ScreenRecordModel.DoingNothing
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Inactive::class.java)

            systemClock.setElapsedRealtime(5678)
            screenRecordRepo.screenRecordState.value = ScreenRecordModel.Recording

            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            assertThat(
                    ((latest as OngoingActivityChipModel.Active).content
                            as OngoingActivityChipModel.Content.Timer)
                        .value
                )
                .isEqualTo(Chronometer.Running(EventTime.ElapsedRealtime(5678)))
        }

    /** Regression test for b/349620526. */
    @Test
    fun chip_recordingState_thenGetsTaskInfo_startTimeDoesNotChange() =
        testScope.runTest {
            val latest by collectLastValue(underTest.chip)

            // Start recording, but without any task info
            systemClock.setElapsedRealtime(1234)
            screenRecordRepo.screenRecordState.value = ScreenRecordModel.Recording
            mediaProjectionRepo.mediaProjectionState.value = MediaProjectionState.NotProjecting

            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isImportantForPrivacy).isTrue()
            assertThat(
                    ((latest as OngoingActivityChipModel.Active).content
                            as OngoingActivityChipModel.Content.Timer)
                        .value
                )
                .isEqualTo(Chronometer.Running(EventTime.ElapsedRealtime(1234)))

            // WHEN we receive the recording task info a few milliseconds later
            systemClock.setElapsedRealtime(1240)
            mediaProjectionRepo.mediaProjectionState.value =
                MediaProjectionState.Projecting.SingleTask(
                    "host.package",
                    hostDeviceName = null,
                    FakeActivityTaskManager.createTask(taskId = 1),
                )

            // THEN the start time is still the old start time
            assertThat(latest).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).isImportantForPrivacy).isTrue()
            assertThat(
                    ((latest as OngoingActivityChipModel.Active).content
                            as OngoingActivityChipModel.Content.Timer)
                        .value
                )
                .isEqualTo(Chronometer.Running(EventTime.ElapsedRealtime(1234)))
        }

    @Test
    fun chip_recordingState_hasClickBehavior() =
        testScope.runTest {
            val latest by collectLastValue(underTest.chip)
            screenRecordRepo.screenRecordState.value = ScreenRecordModel.Recording
            assertThat((latest as OngoingActivityChipModel.Active).content)
                .isInstanceOf(OngoingActivityChipModel.Content.Timer::class.java)
            assertThat((latest as OngoingActivityChipModel.Active).clickBehavior)
                .isInstanceOf(OngoingActivityChipModel.ClickBehavior.ExpandAction::class.java)
        }

    @Test
    @DisableFlags(
        Flags.FLAG_NEW_SCREEN_RECORD_TOOLBAR,
        Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE,
        Flags.FLAG_LARGE_SCREEN_RECORDING,
        Flags.FLAG_ANIMATION_LIBRARY_DYNAMIC_TARGET_RESOLUTION,
    )
    fun chip_notProjecting_expandActionBehaviorShowsDialog() =
        testScope.runTest {
            val latest by collectLastValue(underTest.chip)
            screenRecordRepo.screenRecordState.value = ScreenRecordModel.Recording
            mediaProjectionRepo.mediaProjectionState.value = MediaProjectionState.NotProjecting

            val expandAction =
                ((latest as OngoingActivityChipModel.Active).clickBehavior
                    as OngoingActivityChipModel.ClickBehavior.ExpandAction)

            expandAction.onClick(mockExpandable)
            verify(kosmos.mockDialogTransitionAnimator).show(any(), any(), anyBoolean())
        }

    @Test
    @DisableFlags(
        Flags.FLAG_NEW_SCREEN_RECORD_TOOLBAR,
        Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE,
        Flags.FLAG_LARGE_SCREEN_RECORDING,
    )
    @EnableFlags(Flags.FLAG_ANIMATION_LIBRARY_DYNAMIC_TARGET_RESOLUTION)
    fun chip_notProjecting_expandActionBehaviorShowsDialog_withDynamicTargetResolution() =
        testScope.runTest {
            val latest by collectLastValue(underTest.chip)
            screenRecordRepo.screenRecordState.value = ScreenRecordModel.Recording
            mediaProjectionRepo.mediaProjectionState.value = MediaProjectionState.NotProjecting

            val expandAction =
                ((latest as OngoingActivityChipModel.Active).clickBehavior
                    as OngoingActivityChipModel.ClickBehavior.ExpandAction)

            expandAction.onClick(mockExpandable)
            verify(kosmos.mockDialogTransitionAnimator)
                .show(any(), anyOrNull(), anyOrNull(), anyBoolean())
        }

    @Test
    @DisableFlags(
        Flags.FLAG_NEW_SCREEN_RECORD_TOOLBAR,
        Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE,
        Flags.FLAG_LARGE_SCREEN_RECORDING,
        Flags.FLAG_ANIMATION_LIBRARY_DYNAMIC_TARGET_RESOLUTION,
    )
    fun chip_projectingEntireScreen_expandActionBehaviorShowsDialog() =
        testScope.runTest {
            val latest by collectLastValue(underTest.chip)
            screenRecordRepo.screenRecordState.value = ScreenRecordModel.Recording

            val expandAction =
                ((latest as OngoingActivityChipModel.Active).clickBehavior
                    as OngoingActivityChipModel.ClickBehavior.ExpandAction)

            expandAction.onClick(mockExpandable)
            verify(kosmos.mockDialogTransitionAnimator).show(any(), any(), anyBoolean())
        }

    @Test
    @DisableFlags(
        Flags.FLAG_NEW_SCREEN_RECORD_TOOLBAR,
        Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE,
        Flags.FLAG_LARGE_SCREEN_RECORDING,
    )
    @EnableFlags(Flags.FLAG_ANIMATION_LIBRARY_DYNAMIC_TARGET_RESOLUTION)
    fun chip_projectingEntireScreen_expandActionBehaviorShowsDialog_withDynamicTargetResolution() =
        testScope.runTest {
            val latest by collectLastValue(underTest.chip)
            screenRecordRepo.screenRecordState.value = ScreenRecordModel.Recording

            val expandAction =
                ((latest as OngoingActivityChipModel.Active).clickBehavior
                    as OngoingActivityChipModel.ClickBehavior.ExpandAction)

            expandAction.onClick(mockExpandable)
            verify(kosmos.mockDialogTransitionAnimator)
                .show(any(), anyOrNull(), anyOrNull(), anyBoolean())
        }

    @Test
    @DisableFlags(
        Flags.FLAG_NEW_SCREEN_RECORD_TOOLBAR,
        Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE,
        Flags.FLAG_LARGE_SCREEN_RECORDING,
        Flags.FLAG_ANIMATION_LIBRARY_DYNAMIC_TARGET_RESOLUTION,
    )
    fun chip_projectingSingleTask_expandActionBehaviorShowsDialog() =
        testScope.runTest {
            val latest by collectLastValue(underTest.chip)
            screenRecordRepo.screenRecordState.value = ScreenRecordModel.Recording
            mediaProjectionRepo.mediaProjectionState.value =
                MediaProjectionState.Projecting.SingleTask(
                    "host.package",
                    hostDeviceName = null,
                    FakeActivityTaskManager.createTask(taskId = 1),
                )

            val expandAction =
                ((latest as OngoingActivityChipModel.Active).clickBehavior
                    as OngoingActivityChipModel.ClickBehavior.ExpandAction)

            expandAction.onClick(mockExpandable)
            verify(kosmos.mockDialogTransitionAnimator).show(any(), any(), anyBoolean())
        }

    @Test
    @DisableFlags(
        Flags.FLAG_NEW_SCREEN_RECORD_TOOLBAR,
        Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE,
        Flags.FLAG_LARGE_SCREEN_RECORDING,
    )
    @EnableFlags(Flags.FLAG_ANIMATION_LIBRARY_DYNAMIC_TARGET_RESOLUTION)
    fun chip_projectingSingleTask_expandActionBehaviorShowsDialog_withDynamicTargetResolution() =
        testScope.runTest {
            val latest by collectLastValue(underTest.chip)
            screenRecordRepo.screenRecordState.value = ScreenRecordModel.Recording
            mediaProjectionRepo.mediaProjectionState.value =
                MediaProjectionState.Projecting.SingleTask(
                    "host.package",
                    hostDeviceName = null,
                    FakeActivityTaskManager.createTask(taskId = 1),
                )

            val expandAction =
                ((latest as OngoingActivityChipModel.Active).clickBehavior
                    as OngoingActivityChipModel.ClickBehavior.ExpandAction)

            expandAction.onClick(mockExpandable)
            verify(kosmos.mockDialogTransitionAnimator)
                .show(any(), anyOrNull(), anyOrNull(), anyBoolean())
        }

    @Test
    @EnableFlags(
        Flags.FLAG_NEW_SCREEN_RECORD_TOOLBAR,
        Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE,
        Flags.FLAG_LARGE_SCREEN_RECORDING,
    )
    fun chip_recording_tapShowsScreenCaptureUi() =
        kosmos.runTest {
            whenever(
                    activityStarter.executeRunnableDismissingKeyguard(
                        any(),
                        anyOrNull(),
                        anyBoolean(),
                        anyBoolean(),
                        anyBoolean(),
                    )
                )
                .then { (it.arguments[0] as Runnable).run() }
            val latest by collectLastValue(underTest.chip)
            val screenCaptureUi by
                collectLastValue(screenCaptureUiInteractor.uiState(ScreenCaptureType.RECORD))
            screenRecordRepo.screenRecordState.value = ScreenRecordModel.Recording

            val expandAction =
                (latest as OngoingActivityChipModel.Active).clickBehavior
                    as OngoingActivityChipModel.ClickBehavior.ExpandAction
            expandAction.onClick(mockExpandable)

            assertThat(screenCaptureUi).isInstanceOf(ScreenCaptureUiState.Visible::class.java)
        }
}
