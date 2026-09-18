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

package com.android.systemui.qs.tiles.impl.screenrecord.domain.interactor

import android.app.Dialog
import android.media.projection.StopReason
import android.os.UserHandle
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.animation.DialogTransitionAnimator
import com.android.systemui.animation.Expandable
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.domain.interactor.keyguardInteractor
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.mediaprojection.MediaProjectionMetricsLogger
import com.android.systemui.plugins.ActivityStarter.OnDismissAction
import com.android.systemui.plugins.activityStarter
import com.android.systemui.qs.pipeline.domain.interactor.PanelInteractor
import com.android.systemui.qs.tiles.base.domain.model.QSTileInputTestKtx
import com.android.systemui.qs.tiles.impl.screenrecord.domain.model.ScreenRecordTileModel
import com.android.systemui.screencapture.data.repository.fakeScreenCaptureDeviceStateRepository
import com.android.systemui.screencapture.domain.interactor.screenCaptureUiInteractor
import com.android.systemui.screencapture.record.domain.interactor.screenCaptureRecordFeaturesInteractor
import com.android.systemui.screenrecord.ScreenRecordUxController
import com.android.systemui.screenrecord.ScreenRecordingAudioSource
import com.android.systemui.screenrecord.data.model.ScreenRecordModel
import com.android.systemui.screenrecord.data.repository.ScreenRecordRepositoryImpl
import com.android.systemui.screenrecord.data.repository.screenRecordingServiceRepository
import com.android.systemui.screenrecord.domain.interactor.screenRecordingServiceInteractor
import com.android.systemui.screenrecord.shared.model.ScreenRecordingParameters
import com.android.systemui.screenrecord.shared.model.ScreenRecordingStatus
import com.android.systemui.statusbar.phone.KeyguardDismissUtil
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock

@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenRecordTileUserActionInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val testScope = kosmos.testScope
    private val keyguardInteractor = kosmos.keyguardInteractor
    private val dialogTransitionAnimator = mock<DialogTransitionAnimator>()
    private val keyguardDismissUtil = mock<KeyguardDismissUtil>()
    private val panelInteractor = mock<PanelInteractor>()
    private val dialog = mock<Dialog>()
    private val screenRecordUxController =
        mock<ScreenRecordUxController> { on { createScreenRecordDialog(any()) } doReturn dialog }

    private val screenRecordRepository =
        ScreenRecordRepositoryImpl(
            bgCoroutineContext = testScope.testScheduler,
            screenRecordUxController = screenRecordUxController,
        )

    private val underTest by lazy {
        ScreenRecordTileUserActionInteractor(
            testScope.testScheduler,
            kosmos.testDispatcher,
            testScope.testScheduler,
            screenRecordRepository,
            screenRecordUxController,
            keyguardInteractor,
            kosmos.activityStarter,
            keyguardDismissUtil,
            dialogTransitionAnimator,
            panelInteractor,
            kosmos.screenCaptureUiInteractor,
            mock<MediaProjectionMetricsLogger>(),
            kosmos.screenRecordingServiceInteractor,
            kosmos.screenCaptureRecordFeaturesInteractor,
        )
    }

    @Before
    fun setUp() {
        // Default to large screen.
        kosmos.fakeScreenCaptureDeviceStateRepository.setLargeScreen(true)
    }

    @Test
    @DisableFlags(Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE, Flags.FLAG_NEW_SCREEN_RECORD_TOOLBAR)
    fun handleClick_whenStarting_cancelCountdown() = runTest {
        val startingModel = ScreenRecordTileModel(ScreenRecordModel.Starting(0))

        underTest.handleInput(QSTileInputTestKtx.click(startingModel))

        verify(screenRecordUxController).cancelCountdown()
    }

    @Test
    @EnableFlags(Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE, Flags.FLAG_LARGE_SCREEN_RECORDING)
    @DisableFlags(Flags.FLAG_NEW_SCREEN_RECORD_TOOLBAR)
    fun handleClick_withLargeScreenCaptureFlagEnabled_doesNotOpenDialog() = runTest {
        val recordingModel =
            ScreenRecordTileModel(
                ScreenRecordModel.DoingNothing,
                isLargeScreenRecordingEnabled = true,
            )

        underTest.handleInput(QSTileInputTestKtx.click(recordingModel))
        verify(screenRecordUxController, never()).createScreenRecordDialog(any())
    }

    @Test
    @EnableFlags(
        Flags.FLAG_NEW_SCREEN_RECORD_TOOLBAR,
        Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE,
        Flags.FLAG_LARGE_SCREEN_RECORDING,
    )
    fun handleClick_withNewScreenRecordFlagEnabled_doesNotOpenDialog() = runTest {
        kosmos.fakeScreenCaptureDeviceStateRepository.setLargeScreen(false)
        val recordingModel =
            ScreenRecordTileModel(
                ScreenRecordModel.DoingNothing,
                isLargeScreenRecordingEnabled = true,
            )

        underTest.handleInput(QSTileInputTestKtx.click(recordingModel))
        verify(screenRecordUxController, never()).createScreenRecordDialog(any())
    }

    @Test
    @DisableFlags(Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE, Flags.FLAG_NEW_SCREEN_RECORD_TOOLBAR)
    fun handleClick_newScreenRecordingFlagsDisabled_opensRecordingDialog() = runTest {
        val recordingModel = ScreenRecordTileModel(ScreenRecordModel.DoingNothing)

        underTest.handleInput(QSTileInputTestKtx.click(recordingModel))
        verify(screenRecordUxController).createScreenRecordDialog(any())
    }

    @Test
    @DisableFlags(Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE, Flags.FLAG_NEW_SCREEN_RECORD_TOOLBAR)
    fun handleClick_whenRecording_stopRecordingLegacy() = runTest {
        val recordingModel = ScreenRecordTileModel(ScreenRecordModel.Recording)

        underTest.handleInput(QSTileInputTestKtx.click(recordingModel))

        verify(screenRecordUxController).stopRecording(eq(StopReason.STOP_QS_TILE))
    }

    @Test
    @EnableFlags(Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE, Flags.FLAG_NEW_SCREEN_RECORD_TOOLBAR)
    fun handleClick_whenRecording_stopRecording() = runTest {
        kosmos.screenRecordingServiceRepository.startRecording(
            ScreenRecordingParameters(
                captureTarget = null,
                audioSource = ScreenRecordingAudioSource.NONE,
                displayId = Display.DEFAULT_DISPLAY,
                shouldShowTaps = false,
            )
        )
        val recordingStatus by collectLastValue(kosmos.screenRecordingServiceRepository.status)

        underTest.handleInput(
            QSTileInputTestKtx.click(
                ScreenRecordTileModel(
                    ScreenRecordModel.Recording,
                    isLargeScreenRecordingEnabled = true,
                )
            )
        )

        assertThat(recordingStatus)
            .isEqualTo(ScreenRecordingStatus.Stopped(StopReason.STOP_QS_TILE))
    }

    @Test
    @DisableFlags(Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE, Flags.FLAG_NEW_SCREEN_RECORD_TOOLBAR)
    fun handleClick_whenDoingNothing_createDialogDismissPanelShowDialog() = runTest {
        val recordingModel = ScreenRecordTileModel(ScreenRecordModel.DoingNothing)

        underTest.handleInput(QSTileInputTestKtx.click(recordingModel))
        val onStartRecordingClickedCaptor = argumentCaptor<Runnable>()
        verify(screenRecordUxController)
            .createScreenRecordDialog(onStartRecordingClickedCaptor.capture())

        val onDismissActionCaptor = argumentCaptor<OnDismissAction>()
        verify(keyguardDismissUtil)
            .executeWhenUnlocked(onDismissActionCaptor.capture(), eq(false), eq(true))
        onDismissActionCaptor.lastValue.onDismiss()
        verify(dialog).show() // because the view was null

        // When starting the recording, we collapse the shade and disable the dialog animation.
        onStartRecordingClickedCaptor.lastValue.run()
        verify(dialogTransitionAnimator).disableAllCurrentDialogsExitAnimations()
        verify(panelInteractor).collapsePanels()
    }

    /**
     * When the input view is not null and keyguard is not showing, dialog should animate and show
     */
    @Test
    @DisableFlags(
        Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE,
        Flags.FLAG_NEW_SCREEN_RECORD_TOOLBAR,
        Flags.FLAG_ANIMATION_LIBRARY_DYNAMIC_TARGET_RESOLUTION,
    )
    fun handleClickFromView_whenDoingNothing_whenKeyguardNotShowing_showDialogFromView() = runTest {
        val controller = mock<DialogTransitionAnimator.Controller>()
        val expandable =
            mock<Expandable> { on { dialogTransitionController(any()) } doReturn controller }

        kosmos.fakeKeyguardRepository.setKeyguardShowing(false)

        val recordingModel = ScreenRecordTileModel(ScreenRecordModel.DoingNothing)

        underTest.handleInput(
            QSTileInputTestKtx.click(recordingModel, UserHandle.CURRENT, expandable)
        )
        verify(screenRecordUxController).createScreenRecordDialog(any())

        val onDismissActionCaptor = argumentCaptor<OnDismissAction>()
        verify(keyguardDismissUtil)
            .executeWhenUnlocked(onDismissActionCaptor.capture(), eq(false), eq(true))
        onDismissActionCaptor.lastValue.onDismiss()
        verify(dialogTransitionAnimator).show(eq(dialog), eq(controller), eq(true))
    }

    /**
     * When the input view is not null and keyguard is not showing, dialog should animate and show
     */
    @Test
    @DisableFlags(Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE, Flags.FLAG_NEW_SCREEN_RECORD_TOOLBAR)
    @EnableFlags(Flags.FLAG_ANIMATION_LIBRARY_DYNAMIC_TARGET_RESOLUTION)
    fun handleClickFromView_whenDoingNothing_whenKeyguardNotShowing_showDialogFromView_withDynamicTargetResolution() =
        runTest {
            val controller = mock<DialogTransitionAnimator.Controller>()
            val expandable =
                mock<Expandable> { on { dialogTransitionController(any()) } doReturn controller }

            kosmos.fakeKeyguardRepository.setKeyguardShowing(false)

            val recordingModel = ScreenRecordTileModel(ScreenRecordModel.DoingNothing)

            underTest.handleInput(
                QSTileInputTestKtx.click(recordingModel, UserHandle.CURRENT, expandable)
            )

            verify(screenRecordUxController).createScreenRecordDialog(any())

            val onDismissActionCaptor = argumentCaptor<OnDismissAction>()
            verify(keyguardDismissUtil)
                .executeWhenUnlocked(onDismissActionCaptor.capture(), eq(false), eq(true))
            onDismissActionCaptor.lastValue.onDismiss()
            verify(dialogTransitionAnimator).show(eq(dialog), anyOrNull(), anyOrNull(), eq(true))
        }
}
