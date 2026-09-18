/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.systemui.qs.tiles

import android.app.Dialog
import android.content.Intent
import android.content.ServiceConnection
import android.content.applicationContext
import android.content.testableContext
import android.media.projection.StopReason.STOP_QS_TILE
import android.os.Handler
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import android.service.quicksettings.Tile
import android.testing.TestableLooper
import android.testing.TestableLooper.RunWithLooper
import android.view.Display
import androidx.test.filters.SmallTest
import com.android.internal.logging.metricsLogger
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.animation.mockDialogTransitionAnimator
import com.android.systemui.classifier.FalsingManagerFake
import com.android.systemui.flags.FeatureFlags
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.mediaprojection.MediaProjectionMetricsLogger
import com.android.systemui.plugins.ActivityStarter.OnDismissAction
import com.android.systemui.plugins.activityStarter
import com.android.systemui.plugins.qs.QSTile
import com.android.systemui.plugins.qs.QSTile.BooleanState
import com.android.systemui.plugins.statusbar.statusBarStateController
import com.android.systemui.qs.QSHost
import com.android.systemui.qs.flags.QsDetailedView
import com.android.systemui.qs.logging.QSLogger
import com.android.systemui.qs.pipeline.domain.interactor.panelInteractor
import com.android.systemui.qs.qsEventLogger
import com.android.systemui.qs.tileimpl.QSTileImpl.DrawableIconWithRes
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiState
import com.android.systemui.screencapture.data.repository.fakeScreenCaptureDeviceStateRepository
import com.android.systemui.screencapture.domain.interactor.screenCaptureUiInteractor
import com.android.systemui.screencapture.record.domain.interactor.screenCaptureRecordFeaturesInteractor
import com.android.systemui.screenrecord.ScreenRecordingAudioSource
import com.android.systemui.screenrecord.data.repository.screenRecordingServiceRepository
import com.android.systemui.screenrecord.domain.interactor.screenRecordingServiceInteractor
import com.android.systemui.screenrecord.screenRecordUxController
import com.android.systemui.screenrecord.service.fakeScreenRecordingService
import com.android.systemui.screenrecord.shared.model.ScreenRecordingParameters
import com.android.systemui.screenrecord.shared.model.ScreenRecordingStatus
import com.android.systemui.settings.fakeUserTracker
import com.android.systemui.settings.userTracker
import com.android.systemui.statusbar.phone.KeyguardDismissUtil
import com.android.systemui.statusbar.policy.keyguardStateController
import com.android.systemui.testKosmosNew
import com.android.systemui.user.data.repository.userRepository
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.stubbing.Answer
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@RunWith(ParameterizedAndroidJunit4::class)
@RunWithLooper(setAsMainLooper = true)
@SmallTest
class ScreenRecordTileTest(flags: FlagsParameterization) : SysuiTestCase() {

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf(QsDetailedView.FLAG_NAME)
        }
    }

    private val kosmos = testKosmosNew().apply { panelInteractor = mock {} }

    private val qsLogger: QSLogger = mock {}
    private val qsHost: QSHost = mock {
        whenever(it.context).then { kosmos.applicationContext }
        whenever(it.userContext).then { kosmos.applicationContext }
    }
    private val featureFlags: FeatureFlags = mock {}
    private val keyguardDismissUtil: KeyguardDismissUtil = mock {}
    private val mediaProjectionMetricsLogger: MediaProjectionMetricsLogger = mock {}
    private var serviceConnection: ServiceConnection? = null

    private val testableLooper: TestableLooper by lazy { TestableLooper.get(this) }
    private val underTest: ScreenRecordTile by lazy {
        with(kosmos) {
            ScreenRecordTile(
                qsHost,
                qsEventLogger,
                testableLooper.looper,
                Handler(testableLooper.looper),
                FalsingManagerFake(),
                metricsLogger,
                featureFlags,
                statusBarStateController,
                activityStarter,
                qsLogger,
                screenRecordUxController,
                keyguardDismissUtil,
                keyguardStateController,
                mockDialogTransitionAnimator,
                panelInteractor,
                mediaProjectionMetricsLogger,
                screenCaptureUiInteractor,
                userTracker,
                screenRecordingServiceInteractor,
                screenCaptureRecordFeaturesInteractor,
            )
        }
    }

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @Before
    fun setUp(): Unit =
        with(kosmos) {
            testableContext.prepareCreateContextAsUser(
                userRepository.selectedUser.value.userInfo.userHandle,
                mock { mockedCtx ->
                    whenever(
                            mockedCtx.bindService(any<Intent>(), any<ServiceConnection>(), anyInt())
                        )
                        .then(bindFakeService())
                },
            )

            fakeUserTracker.userContext = mContext
            whenever(qsHost.context).thenReturn(mContext)
            Mockito.doAnswer { invocation: InvocationOnMock ->
                    (invocation.getArgument<Any>(0) as Runnable).run()
                    null
                }
                .whenever(activityStarter)
                .executeRunnableDismissingKeyguard(
                    ArgumentMatchers.any(),
                    ArgumentMatchers.any(),
                    ArgumentMatchers.anyBoolean(),
                    ArgumentMatchers.anyBoolean(),
                    ArgumentMatchers.anyBoolean(),
                )

            fakeScreenCaptureDeviceStateRepository.setLargeScreen(false)

            testableLooper.processAllMessages()
        }

    @After
    fun tearDown() {
        underTest.destroy()
        testableLooper.processAllMessages()
    }

    // Test that the tile is inactive and labeled correctly when the controller is neither starting
    // or recording, and that clicking on the tile in this state brings up the record prompt
    @Test
    @DisableFlags(
        Flags.FLAG_NEW_SCREEN_RECORD_TOOLBAR,
        Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE,
        Flags.FLAG_LARGE_SCREEN_RECORDING,
    )
    fun testNotActive() =
        kosmos.runTest {
            whenever(screenRecordUxController.isStarting).thenReturn(false)
            whenever(screenRecordUxController.isRecording).thenReturn(false)

            underTest.refreshState()
            testableLooper.processAllMessages()

            assertThat(underTest.state.state).isEqualTo(Tile.STATE_INACTIVE)
            assertThat(underTest.state.secondaryLabel.toString())
                .isEqualTo(mContext.getString(R.string.quick_settings_screen_record_start))

            underTest.handleClick(null /* view */)
            testableLooper.processAllMessages()

            val onStartRecordingClicked = ArgumentCaptor.forClass(Runnable::class.java)
            Mockito.verify(screenRecordUxController)
                .createScreenRecordDialog(onStartRecordingClicked.capture())

            // When starting the recording, we collapse the shade and disable the dialog animation.
            assertThat(onStartRecordingClicked.value).isNotNull()
            onStartRecordingClicked.value.run()
            Mockito.verify(mockDialogTransitionAnimator).disableAllCurrentDialogsExitAnimations()
            Mockito.verify(panelInteractor).collapsePanels()
        }

    // Test that the tile is active and labeled correctly when the controller is starting
    @Test
    fun testIsStarting() =
        kosmos.runTest {
            whenever(screenRecordUxController.isStarting).thenReturn(true)
            whenever(screenRecordUxController.isRecording).thenReturn(false)

            underTest.refreshState()
            testableLooper.processAllMessages()

            assertThat(underTest.state.state).isEqualTo(Tile.STATE_ACTIVE)
            assertThat(underTest.state.secondaryLabel.toString()).endsWith("...")
        }

    // Test that the tile cancels countdown if it is clicked when the controller is starting
    @Test
    @DisableFlags(
        Flags.FLAG_NEW_SCREEN_RECORD_TOOLBAR,
        Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE,
        Flags.FLAG_LARGE_SCREEN_RECORDING,
    )
    fun testCancelRecording() =
        kosmos.runTest {
            whenever(screenRecordUxController.isStarting).thenReturn(true)
            whenever(screenRecordUxController.isRecording).thenReturn(false)

            underTest.handleClick(null /* view */)

            Mockito.verify(screenRecordUxController, Mockito.times(1)).cancelCountdown()
        }

    @Test
    @EnableFlags(
        Flags.FLAG_NEW_SCREEN_RECORD_TOOLBAR,
        Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE,
        Flags.FLAG_LARGE_SCREEN_RECORDING,
    )
    fun testClickOpensNewUI() =
        kosmos.runTest {
            whenever(screenRecordUxController.isStarting).thenReturn(false)
            whenever(screenRecordUxController.isRecording).thenReturn(false)

            underTest.refreshState()
            testableLooper.processAllMessages()

            underTest.handleClick(null /* view */)
            testableLooper.processAllMessages()
            assertThat(screenCaptureUiInteractor.uiState(ScreenCaptureType.RECORD).value)
                .isInstanceOf(ScreenCaptureUiState.Visible::class.java)
        }

    // Test that clicking the tile is NOP if opened from large screen.
    @Test
    @EnableFlags(Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE, Flags.FLAG_NEW_SCREEN_RECORD_TOOLBAR)
    fun testClickFromLargeScreen() =
        kosmos.runTest {
            fakeScreenCaptureDeviceStateRepository.setLargeScreen(true)

            whenever(screenRecordUxController.isStarting).thenReturn(false)
            whenever(screenRecordUxController.isRecording).thenReturn(false)

            underTest.refreshState()
            testableLooper.processAllMessages()

            underTest.handleClick(null /* view */)
            testableLooper.processAllMessages()
            Mockito.verify(screenRecordUxController, Mockito.never()).createScreenRecordDialog(null)
        }

    // Test that clicking the tile opens the recording dialog if flag is disabled.
    @Test
    @DisableFlags(
        Flags.FLAG_NEW_SCREEN_RECORD_TOOLBAR,
        Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE,
        Flags.FLAG_LARGE_SCREEN_RECORDING,
    )
    fun testClickNewToolbarFlagDisabled() =
        kosmos.runTest {
            whenever(screenRecordUxController.isStarting).thenReturn(false)
            whenever(screenRecordUxController.isRecording).thenReturn(false)

            underTest.refreshState()
            testableLooper.processAllMessages()

            underTest.handleClick(null /* view */)
            testableLooper.processAllMessages()

            Mockito.verify(screenRecordUxController)
                .createScreenRecordDialog(ArgumentMatchers.any())
        }

    // Test that the tile is active and labeled correctly when the controller is recording
    @Test
    fun testIsRecording() =
        kosmos.runTest {
            whenever(screenRecordUxController.isStarting).thenReturn(false)
            whenever(screenRecordUxController.isRecording).thenReturn(true)

            underTest.refreshState()
            testableLooper.processAllMessages()

            assertThat(underTest.state.state).isEqualTo(Tile.STATE_ACTIVE)
            assertThat(underTest.state.secondaryLabel.toString())
                .isEqualTo(context.getString(R.string.quick_settings_screen_record_stop))
        }

    // Test that the tile stops the recording if it is clicked when the controller is recording
    @Test
    @DisableFlags(
        Flags.FLAG_NEW_SCREEN_RECORD_TOOLBAR,
        Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE,
        Flags.FLAG_LARGE_SCREEN_RECORDING,
    )
    fun testStopRecording() =
        kosmos.runTest {
            whenever(screenRecordUxController.isStarting).thenReturn(false)
            whenever(screenRecordUxController.isRecording).thenReturn(true)

            underTest.handleClick(null /* view */)

            Mockito.verify(screenRecordUxController, Mockito.times(1))
                .stopRecording(ArgumentMatchers.eq(STOP_QS_TILE))
        }

    @Test
    fun testContentDescriptionHasTileName() =
        kosmos.runTest {
            underTest.refreshState()
            testableLooper.processAllMessages()

            assertThat(underTest.state.contentDescription.toString())
                .contains(underTest.state.label)
        }

    @Test
    fun testForceExpandIcon_notRecordingNotStarting() =
        kosmos.runTest {
            whenever(screenRecordUxController.isStarting).thenReturn(false)
            whenever(screenRecordUxController.isRecording).thenReturn(false)

            underTest.refreshState()
            testableLooper.processAllMessages()

            assertThat(underTest.state.forceExpandIcon).isTrue()
        }

    @Test
    fun testForceExpandIcon_recordingNotStarting() =
        kosmos.runTest {
            whenever(screenRecordUxController.isStarting).thenReturn(false)
            whenever(screenRecordUxController.isRecording).thenReturn(true)

            underTest.refreshState()
            testableLooper.processAllMessages()

            org.junit.Assert.assertFalse(underTest.state.forceExpandIcon)
        }

    @Test
    fun testForceExpandIcon_startingNotRecording() =
        kosmos.runTest {
            whenever(screenRecordUxController.isStarting).thenReturn(true)
            whenever(screenRecordUxController.isRecording).thenReturn(false)

            underTest.refreshState()
            testableLooper.processAllMessages()

            org.junit.Assert.assertFalse(underTest.state.forceExpandIcon)
        }

    @Test
    fun testIcon_whenRecording_isOnState() =
        kosmos.runTest {
            whenever(screenRecordUxController.isStarting).thenReturn(false)
            whenever(screenRecordUxController.isRecording).thenReturn(true)
            val state = BooleanState()

            underTest.handleUpdateState(state, /* arg= */ null)

            assertThat(state.icon)
                .isEqualTo(createExpectedIcon(R.drawable.qs_screen_record_icon_on))
        }

    @Test
    fun testIcon_whenStarting_isOnState() =
        kosmos.runTest {
            whenever(screenRecordUxController.isStarting).thenReturn(true)
            whenever(screenRecordUxController.isRecording).thenReturn(false)
            val state = BooleanState()

            underTest.handleUpdateState(state, /* arg= */ null)

            assertThat(state.icon)
                .isEqualTo(createExpectedIcon(R.drawable.qs_screen_record_icon_on))
        }

    @Test
    fun testIcon_whenRecordingOff_isOffState() =
        kosmos.runTest {
            whenever(screenRecordUxController.isStarting).thenReturn(false)
            whenever(screenRecordUxController.isRecording).thenReturn(false)
            val state = BooleanState()

            underTest.handleUpdateState(state, /* arg= */ null)

            assertThat(state.icon)
                .isEqualTo(createExpectedIcon(R.drawable.qs_screen_record_icon_off))
        }

    @Test
    @DisableFlags(
        Flags.FLAG_NEW_SCREEN_RECORD_TOOLBAR,
        Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE,
        Flags.FLAG_LARGE_SCREEN_RECORDING,
    )
    fun showingDialogPrompt_logsMediaProjectionPermissionRequested() =
        kosmos.runTest {
            val permissionDialogPrompt = mock<Dialog> {}
            whenever(screenRecordUxController.isStarting).thenReturn(false)
            whenever(screenRecordUxController.isRecording).thenReturn(false)
            whenever(screenRecordUxController.createScreenRecordDialog(ArgumentMatchers.any()))
                .thenReturn(permissionDialogPrompt)

            underTest.handleClick(null /* view */)
            testableLooper.processAllMessages()

            Mockito.verify(screenRecordUxController)
                .createScreenRecordDialog(ArgumentMatchers.any())
            val onDismissAction = ArgumentCaptor.forClass(OnDismissAction::class.java)
            Mockito.verify(keyguardDismissUtil)
                .executeWhenUnlocked(
                    onDismissAction.capture(),
                    ArgumentMatchers.anyBoolean(),
                    ArgumentMatchers.anyBoolean(),
                )
            assertThat(onDismissAction.value).isNotNull()

            onDismissAction.value.onDismiss()

            Mockito.verify(permissionDialogPrompt).show()
            Mockito.verify(mediaProjectionMetricsLogger)
                .notifyPermissionRequestDisplayed(mContext.userId)
        }

    @Test
    @EnableFlags(
        Flags.FLAG_NEW_SCREEN_RECORD_TOOLBAR,
        Flags.FLAG_LARGE_SCREEN_SCREENCAPTURE,
        Flags.FLAG_LARGE_SCREEN_RECORDING,
    )
    fun testClickWhenRecording_stopRecording() =
        kosmos.runTest {
            val status by collectLastValue(screenRecordingServiceRepository.status)
            screenRecordingServiceRepository.startRecording(
                ScreenRecordingParameters(
                    captureTarget = null,
                    audioSource = ScreenRecordingAudioSource.NONE,
                    displayId = Display.DEFAULT_DISPLAY,
                    shouldShowTaps = false,
                )
            )

            underTest.handleClick(null)

            assertThat(status).isEqualTo(ScreenRecordingStatus.Stopped(STOP_QS_TILE))
        }

    private fun createExpectedIcon(resId: Int): QSTile.Icon =
        DrawableIconWithRes(mContext.getDrawable(resId), resId)

    private fun bindFakeService(): Answer<*> =
        Answer<Any> {
            serviceConnection =
                (it.arguments[1] as ServiceConnection).apply {
                    onServiceConnected(
                        _root_ide_package_.android.content.ComponentName(
                            "com.android.systemui",
                            "test",
                        ),
                        kosmos.fakeScreenRecordingService,
                    )
                }
            true
        }
}
