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

package com.android.systemui.screencapture.record.largescreen.ui.viewmodel

import android.app.ActivityManager
import android.app.WindowConfiguration
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.hardware.display.displayManager
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.uiEventLoggerFake
import com.android.internal.util.ScreenshotRequest
import com.android.internal.util.mockScreenshotHelper
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.advanceTimeBy
import com.android.systemui.kosmos.advanceUntilIdle
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.screencapture.ScreenCaptureEvent
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiParameters.Record.LargeScreenCaptureUiParameters
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiState
import com.android.systemui.screencapture.common.shared.model.largeScreenCaptureUiParameters
import com.android.systemui.screencapture.data.repository.screenCaptureUiRepository
import com.android.systemui.screencapture.record.largescreen.domain.interactor.appWindowInteractor
import com.android.systemui.screencapture.record.largescreen.domain.interactor.largeScreenCaptureParametersInteractor
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureRegion
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureType
import com.android.systemui.screenrecord.ScreenRecordingAudioSource
import com.android.systemui.screenrecord.data.repository.screenRecordingServiceRepository
import com.android.systemui.screenrecord.shared.model.ScreenRecordingStatus
import com.android.systemui.screenshot.mockImageCapture
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class PreCaptureViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    @Mock private lateinit var mockBitmap: Bitmap
    @Mock private lateinit var mockBackgroundBitmap: Bitmap
    @Mock private lateinit var mockDisplay: Display

    private val screenBounds = Rect(0, 0, 100, 100)
    private val displayId = 1234
    private lateinit var viewModel: PreCaptureViewModel

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(kosmos.displayManager.getDisplay(displayId)).thenReturn(mockDisplay)
        doAnswer {
                val metrics = it.arguments[0] as DisplayMetrics
                metrics.widthPixels = screenBounds.width()
                metrics.heightPixels = screenBounds.height()
                null
            }
            .whenever(mockDisplay)
            .getRealMetrics(any())
    }

    @Test
    fun isShowingUi_initialStateIsTrue() =
        kosmos.runTest {
            setupViewModel()

            assertThat(viewModel.isShowingUi).isTrue()
        }

    @Test
    fun onActivated_initializesRegionBox() =
        kosmos.runTest {
            setupViewModel()

            val bounds = Rect(0, 0, 100, 100)
            val expectedRegionBox = Rect(bounds)
            expectedRegionBox.inset(bounds.width() / 4, bounds.height() / 4)
            // For a 100x100 screen, the expected inset rect is (25, 25, 75, 75).
            assertThat(viewModel.regionBox).isEqualTo(expectedRegionBox)
        }

    @Test
    fun onActivated_initializesRegionBoxWithSavedBounds() =
        kosmos.runTest {
            setupViewModel()

            viewModel.updateRegionBoxBounds(Rect(10, 10, 70, 70))

            setupViewModel()
            assertThat(viewModel.regionBox).isEqualTo(Rect(10, 10, 70, 70))
        }

    @Test
    fun onActivated_initializesRegionBoxWithInvalidSavedBounds() =
        kosmos.runTest {
            setupViewModel()

            viewModel.updateRegionBoxBounds(Rect(0, 0, 200, 200))

            setupViewModel()
            val bounds = Rect(0, 0, 100, 100)
            val expectedRegionBox = Rect(bounds)
            expectedRegionBox.inset(bounds.width() / 4, bounds.height() / 4)
            assertThat(viewModel.regionBox).isEqualTo(expectedRegionBox)
        }

    @Test
    fun captureType_defaultsToScreenshot() =
        kosmos.runTest {
            setupViewModel()

            assertThat(viewModel.captureType).isEqualTo(ScreenCaptureType.SCREENSHOT)
        }

    @Test
    fun captureType_initializesByScreenCaptureParams() =
        kosmos.runTest {
            setupViewModel(
                LargeScreenCaptureUiParameters(defaultCaptureType = ScreenCaptureType.RECORDING)
            )

            assertThat(viewModel.captureType).isEqualTo(ScreenCaptureType.RECORDING)
        }

    @Test
    fun captureType_initializeBySelectedType() =
        kosmos.runTest {
            setupViewModel()
            assertThat(viewModel.captureType).isEqualTo(ScreenCaptureType.SCREENSHOT)
            viewModel.updateCaptureType(ScreenCaptureType.RECORDING)

            setupViewModel()
            assertThat(viewModel.captureType).isEqualTo(ScreenCaptureType.RECORDING)
        }

    @Test
    fun captureType_selectedTypeOverriddenByDefaultType() =
        kosmos.runTest {
            setupViewModel(
                LargeScreenCaptureUiParameters(defaultCaptureType = ScreenCaptureType.RECORDING)
            )
            assertThat(viewModel.captureType).isEqualTo(ScreenCaptureType.RECORDING)

            setupViewModel()
            assertThat(largeScreenCaptureParametersInteractor.getSelectedCaptureType())
                .isEqualTo(ScreenCaptureType.RECORDING)
            assertThat(viewModel.captureType).isEqualTo(ScreenCaptureType.RECORDING)
        }

    @Test
    fun disclaimer_shownWhenRecordingSelected() =
        kosmos.runTest {
            setupViewModel()
            assertThat(viewModel.captureType).isEqualTo(ScreenCaptureType.SCREENSHOT)
            assertThat(viewModel.snackbarHostState.currentSnackbarData).isNull()

            // Select APP_WINDOW recording (treated as single app message)
            viewModel.updateCaptureRegion(ScreenCaptureRegion.APP_WINDOW)
            viewModel.updateCaptureType(ScreenCaptureType.RECORDING)
            runCurrent()

            assertThat(viewModel.snackbarHostState.currentSnackbarData?.visuals?.message)
                .isEqualTo(
                    context.getString(R.string.screenrecord_permission_dialog_warning_single_app)
                )
        }

    @Test
    fun disclaimer_updatesWhenRecordingTargetChanges() =
        kosmos.runTest {
            setupViewModel()
            viewModel.updateCaptureType(ScreenCaptureType.RECORDING)
            viewModel.updateCaptureRegion(ScreenCaptureRegion.FULLSCREEN)
            runCurrent()

            assertThat(viewModel.snackbarHostState.currentSnackbarData?.visuals?.message)
                .isEqualTo(
                    context.getString(R.string.screenrecord_permission_dialog_warning_entire_screen)
                )

            // Switch to APP_WINDOW while recording mode is still active
            viewModel.updateCaptureRegion(ScreenCaptureRegion.APP_WINDOW)
            runCurrent()

            assertThat(viewModel.snackbarHostState.currentSnackbarData?.visuals?.message)
                .isEqualTo(
                    context.getString(R.string.screenrecord_permission_dialog_warning_single_app)
                )
        }

    @Test
    fun disclaimer_shownOnStartWhenRecordingDefault() =
        kosmos.runTest {
            setupViewModel(
                LargeScreenCaptureUiParameters(defaultCaptureType = ScreenCaptureType.RECORDING)
            )
            runCurrent()

            assertThat(viewModel.captureType).isEqualTo(ScreenCaptureType.RECORDING)
            assertThat(viewModel.snackbarHostState.currentSnackbarData).isNotNull()
        }

    @Test
    fun captureRegion_defaultsToPartial() =
        kosmos.runTest {
            setupViewModel()

            assertThat(viewModel.captureRegion).isEqualTo(ScreenCaptureRegion.PARTIAL)
        }

    @Test
    fun captureRegion_initializesByScreenCaptureParams() =
        kosmos.runTest {
            setupViewModel(
                LargeScreenCaptureUiParameters(defaultCaptureRegion = ScreenCaptureRegion.PARTIAL)
            )

            assertThat(viewModel.captureRegion).isEqualTo(ScreenCaptureRegion.PARTIAL)
        }

    @Test
    fun captureRegion_initializeBySelectedRegion() =
        kosmos.runTest {
            setupViewModel()

            // Default selection is Partial + Screenshot
            assertThat(viewModel.captureRegion).isEqualTo(ScreenCaptureRegion.PARTIAL)

            // Change the capture region and reinit the view model
            viewModel.updateCaptureRegion(ScreenCaptureRegion.FULLSCREEN)
            setupViewModel()

            assertThat(largeScreenCaptureParametersInteractor.getSelectedCaptureRegion())
                .isEqualTo(ScreenCaptureRegion.FULLSCREEN)
            assertThat(viewModel.captureRegion).isEqualTo(ScreenCaptureRegion.FULLSCREEN)
        }

    @Test
    @EnableFlags(Flags.FLAG_LARGE_SCREEN_SCREENSHOT_APP_WINDOW)
    fun captureRegion_selectedRegionOverriddenByDefaultRegion() =
        kosmos.runTest {
            setupViewModel(
                LargeScreenCaptureUiParameters(
                    defaultCaptureRegion = ScreenCaptureRegion.APP_WINDOW
                )
            )
            assertThat(viewModel.captureRegion).isEqualTo(ScreenCaptureRegion.APP_WINDOW)

            setupViewModel()
            assertThat(largeScreenCaptureParametersInteractor.getSelectedCaptureRegion())
                .isEqualTo(ScreenCaptureRegion.APP_WINDOW)
            assertThat(viewModel.captureRegion).isEqualTo(ScreenCaptureRegion.APP_WINDOW)
        }

    @Test
    @EnableFlags(Flags.FLAG_LARGE_SCREEN_SCREENSHOT_APP_WINDOW)
    fun initialize_withAppWindowRegion_loadsTasks() =
        kosmos.runTest {
            val task = createRunningTaskInfo(taskId = 1, bounds = Rect(0, 0, 50, 50))
            whenever(appWindowInteractor.getAppWindowTasks(any<Int>())).thenReturn(listOf(task))

            setupViewModel(
                LargeScreenCaptureUiParameters(
                    defaultCaptureRegion = ScreenCaptureRegion.APP_WINDOW
                )
            )
            runCurrent()

            // Verify that we can select the task immediately, implying tasks were loaded
            viewModel.updateTaskSelectionFromHover(Point(25, 25))
            assertThat(viewModel.topTask).isEqualTo(task)
        }

    @Test
    @DisableFlags(Flags.FLAG_LARGE_SCREEN_REGION_RECORDING)
    fun captureRegion_resetInvalidSelectedRegion() =
        kosmos.runTest {
            largeScreenCaptureParametersInteractor.setSelectedCaptureType(
                ScreenCaptureType.RECORDING
            )
            largeScreenCaptureParametersInteractor.setSelectedCaptureRegion(
                ScreenCaptureRegion.PARTIAL
            )

            setupViewModel()
            assertThat(viewModel.captureType).isEqualTo(ScreenCaptureType.RECORDING)
            assertThat(viewModel.captureRegion).isEqualTo(ScreenCaptureRegion.FULLSCREEN)
            assertThat(largeScreenCaptureParametersInteractor.getSelectedCaptureType())
                .isEqualTo(ScreenCaptureType.RECORDING)
            assertThat(largeScreenCaptureParametersInteractor.getSelectedCaptureRegion())
                .isEqualTo(ScreenCaptureRegion.FULLSCREEN)
        }

    @Test
    fun updateCaptureType_updatesState() =
        kosmos.runTest {
            setupViewModel()

            viewModel.updateCaptureType(ScreenCaptureType.RECORDING)
            assertThat(viewModel.captureType).isEqualTo(ScreenCaptureType.RECORDING)
        }

    @Test
    fun updateCaptureRegion_updatesState() =
        kosmos.runTest {
            setupViewModel()

            viewModel.updateCaptureRegion(ScreenCaptureRegion.PARTIAL)
            assertThat(viewModel.captureRegion).isEqualTo(ScreenCaptureRegion.PARTIAL)
        }

    @Test
    fun updateCaptureType_toRecording_logsSelectedCaptureType() =
        kosmos.runTest {
            setupViewModel(
                LargeScreenCaptureUiParameters(
                    defaultCaptureType = ScreenCaptureType.SCREENSHOT,
                    defaultCaptureRegion = ScreenCaptureRegion.FULLSCREEN,
                )
            )

            viewModel.updateCaptureType(ScreenCaptureType.RECORDING)

            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
            assertThat(uiEventLoggerFake.eventId(0))
                .isEqualTo(
                    ScreenCaptureEvent.SCREEN_CAPTURE_LARGE_SCREEN_SELECTED_FULLSCREEN_RECORDING.id
                )
        }

    @Test
    fun updateCaptureType_toScreenshot_logsSelectedCaptureType() =
        kosmos.runTest {
            setupViewModel(
                LargeScreenCaptureUiParameters(
                    defaultCaptureType = ScreenCaptureType.RECORDING,
                    defaultCaptureRegion = ScreenCaptureRegion.FULLSCREEN,
                )
            )

            viewModel.updateCaptureType(ScreenCaptureType.SCREENSHOT)

            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
            assertThat(uiEventLoggerFake.eventId(0))
                .isEqualTo(
                    ScreenCaptureEvent.SCREEN_CAPTURE_LARGE_SCREEN_SELECTED_FULLSCREEN_SCREENSHOT.id
                )
        }

    @Test
    fun updateCaptureType_whenPartialRecordingIsSelected_updateCaptureRegionToFullScreen() =
        kosmos.runTest {
            setupViewModel(
                LargeScreenCaptureUiParameters(defaultCaptureRegion = ScreenCaptureRegion.PARTIAL)
            )

            viewModel.updateCaptureType(ScreenCaptureType.RECORDING)
            assertThat(viewModel.captureRegion).isEqualTo(ScreenCaptureRegion.FULLSCREEN)
        }

    @Test
    fun updateCaptureType_whenPartialRecordingIsSelected_toolBarOpacityIsUpdated() =
        kosmos.runTest {
            setupViewModel(
                LargeScreenCaptureUiParameters(defaultCaptureRegion = ScreenCaptureRegion.PARTIAL)
            )

            viewModel.updateCaptureType(ScreenCaptureType.RECORDING)
            val toolbarViewModel = viewModel.toolbarViewModel

            assertThat(toolbarViewModel.toolbarOpacity).isEqualTo(1f)
        }

    @Test
    fun updateCaptureRegion_toPartial_logsPartialScreenshot() =
        kosmos.runTest {
            setupViewModel()

            viewModel.updateCaptureRegion(ScreenCaptureRegion.PARTIAL)

            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
            assertThat(uiEventLoggerFake.eventId(0))
                .isEqualTo(
                    ScreenCaptureEvent.SCREEN_CAPTURE_LARGE_SCREEN_SELECTED_PARTIAL_SCREENSHOT.id
                )
        }

    @Test
    fun updateCaptureRegion_toAppWindow_logsAppWindowScreenshot() =
        kosmos.runTest {
            setupViewModel()

            viewModel.updateCaptureRegion(ScreenCaptureRegion.APP_WINDOW)

            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
            assertThat(uiEventLoggerFake.eventId(0))
                .isEqualTo(
                    ScreenCaptureEvent.SCREEN_CAPTURE_LARGE_SCREEN_SELECTED_APP_WINDOW_SCREENSHOT.id
                )
        }

    @Test
    fun updateCaptureRegion_toPartial_withRecording_logsPartialRecording() =
        kosmos.runTest {
            setupViewModel(
                LargeScreenCaptureUiParameters(defaultCaptureType = ScreenCaptureType.RECORDING)
            )

            viewModel.updateCaptureRegion(ScreenCaptureRegion.PARTIAL)

            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
            assertThat(uiEventLoggerFake.eventId(0))
                .isEqualTo(
                    ScreenCaptureEvent.SCREEN_CAPTURE_LARGE_SCREEN_SELECTED_PARTIAL_RECORDING.id
                )
        }

    @Test
    fun updateCaptureRegion_toAppWindow_withRecording_logsAppWindowRecording() =
        kosmos.runTest {
            setupViewModel(
                LargeScreenCaptureUiParameters(defaultCaptureType = ScreenCaptureType.RECORDING)
            )

            viewModel.updateCaptureRegion(ScreenCaptureRegion.APP_WINDOW)

            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
            assertThat(uiEventLoggerFake.eventId(0))
                .isEqualTo(
                    ScreenCaptureEvent.SCREEN_CAPTURE_LARGE_SCREEN_SELECTED_APP_WINDOW_RECORDING.id
                )
        }

    @Test
    fun updateCaptureRegion_toAppWindow_updatesRunningTasks() =
        kosmos.runTest {
            val task1 = createRunningTaskInfo(taskId = 1, bounds = Rect(0, 0, 50, 50))
            val runningTasks = listOf(task1)
            whenever(appWindowInteractor.getAppWindowTasks(any<Int>())).thenReturn(runningTasks)
            setupViewModel()

            assertThat(viewModel.runningTasks).isEmpty()

            viewModel.updateCaptureRegion(ScreenCaptureRegion.APP_WINDOW)

            assertThat(viewModel.runningTasks).containsExactly(task1)
        }

    @Test
    fun updateRegionBoxBounds_updatesState() =
        kosmos.runTest {
            setupViewModel()

            val regionBox = Rect(0, 0, 100, 100)
            viewModel.updateRegionBoxBounds(regionBox)

            assertThat(viewModel.regionBox).isEqualTo(regionBox)
        }

    @Test
    fun updateRegionBoxBounds_updateSavedBounds() =
        kosmos.runTest {
            setupViewModel()

            val regionBox = Rect(0, 0, 100, 100)
            viewModel.updateRegionBoxBounds(regionBox)

            assertThat(largeScreenCaptureParametersInteractor.getSelectedCaptureRegionBox())
                .isEqualTo(regionBox)
        }

    @Test
    fun beginCapture_forFullscreenScreenshot_makesCorrectRequest() =
        kosmos.runTest {
            setupViewModel()

            viewModel.updateCaptureType(ScreenCaptureType.SCREENSHOT)
            viewModel.updateCaptureRegion(ScreenCaptureRegion.FULLSCREEN)

            viewModel.beginCapture()

            // Account for the delay (temporary fix b/435225255)
            advanceTimeBy(200)
            runCurrent()

            val screenshotRequestCaptor = argumentCaptor<ScreenshotRequest>()
            verify(kosmos.mockScreenshotHelper, times(1))
                .takeScreenshot(screenshotRequestCaptor.capture(), any(), isNull())
            val capturedRequest = screenshotRequestCaptor.lastValue
            assertThat(capturedRequest.type).isEqualTo(WindowManager.TAKE_SCREENSHOT_FULLSCREEN)
            assertThat(capturedRequest.source)
                .isEqualTo(WindowManager.ScreenshotSource.SCREENSHOT_SCREEN_CAPTURE_UI)
            assertThat(capturedRequest.displayId).isEqualTo(displayId)
        }

    @Test
    fun beginCapture_forFullscreenScreenshot_closesUi() =
        kosmos.runTest {
            setupViewModel()

            viewModel.updateCaptureType(ScreenCaptureType.SCREENSHOT)
            viewModel.updateCaptureRegion(ScreenCaptureRegion.FULLSCREEN)

            viewModel.beginCapture()

            // Account for the delay (temporary fix b/435225255)
            advanceTimeBy(200)
            runCurrent()

            assertUiClosed()
        }

    @Test
    fun beginCapture_forPartialScreenshot_makesCorrectRequest() =
        kosmos.runTest {
            setupViewModel()

            viewModel.updateCaptureType(ScreenCaptureType.SCREENSHOT)
            viewModel.updateCaptureRegion(ScreenCaptureRegion.PARTIAL)

            val regionBox = Rect(0, 0, 100, 100)
            viewModel.updateRegionBoxBounds(regionBox)

            whenever(kosmos.mockImageCapture.captureDisplay(any(), eq(regionBox)))
                .thenReturn(mockBitmap)

            viewModel.beginCapture()

            // Account for the delay (temporary fix b/435225255)
            advanceTimeBy(200)
            runCurrent()

            val screenshotRequestCaptor = argumentCaptor<ScreenshotRequest>()
            verify(kosmos.mockScreenshotHelper, times(1))
                .takeScreenshot(screenshotRequestCaptor.capture(), any(), isNull())
            val capturedRequest = screenshotRequestCaptor.lastValue
            assertThat(capturedRequest.type).isEqualTo(WindowManager.TAKE_SCREENSHOT_PROVIDED_IMAGE)
            assertThat(capturedRequest.source)
                .isEqualTo(WindowManager.ScreenshotSource.SCREENSHOT_SCREEN_CAPTURE_UI)
            assertThat(capturedRequest.bitmap).isEqualTo(mockBitmap)
            assertThat(capturedRequest.boundsInScreen).isEqualTo(regionBox)
            assertThat(capturedRequest.displayId).isEqualTo(displayId)
        }

    @Test
    fun beginCapture_forPartialScreenshot_closesUi() =
        kosmos.runTest {
            setupViewModel()

            viewModel.updateCaptureType(ScreenCaptureType.SCREENSHOT)
            viewModel.updateCaptureRegion(ScreenCaptureRegion.PARTIAL)

            val regionBox = Rect(0, 0, 100, 100)
            viewModel.updateRegionBoxBounds(regionBox)

            whenever(kosmos.mockImageCapture.captureDisplay(any(), eq(regionBox)))
                .thenReturn(mockBitmap)

            viewModel.beginCapture()

            // Account for the delay (temporary fix b/435225255)
            advanceTimeBy(200)
            runCurrent()

            assertUiClosed()
        }

    @Test
    fun beginCapture_forFullScreenRecording_startsRecordingWithCorrectParameters() =
        kosmos.runTest {
            val status by collectLastValue(screenRecordingServiceRepository.status)
            setupViewModel()

            viewModel.updateCaptureType(ScreenCaptureType.RECORDING)
            viewModel.updateCaptureRegion(ScreenCaptureRegion.FULLSCREEN)

            viewModel.beginCapture()
            with((status as ScreenRecordingStatus.Starting).parameters) {
                assertThat(captureTarget).isNull()
                assertThat(audioSource).isEqualTo(ScreenRecordingAudioSource.NONE)
                assertThat(this.displayId).isEqualTo(displayId)
                assertThat(shouldShowTaps).isFalse()
            }
        }

    @Test
    fun hideUi_updatesState() =
        kosmos.runTest {
            setupViewModel()

            viewModel.hideUi()

            assertThat(viewModel.isShowingUi).isFalse()
        }

    @Test
    fun closeUi_dismissesWindow() =
        kosmos.runTest {
            setupViewModel()

            viewModel.closeUi()

            assertUiClosed()
        }

    @Test
    fun beginCapture_forFullScreenRecording_logFullscreenRecordingIsTaken() =
        kosmos.runTest {
            setupViewModel(
                LargeScreenCaptureUiParameters(
                    defaultCaptureType = ScreenCaptureType.RECORDING,
                    defaultCaptureRegion = ScreenCaptureRegion.FULLSCREEN,
                )
            )

            viewModel.beginCapture()
            advanceUntilIdle()

            val logs = uiEventLoggerFake.logs
            assertThat(logs).isNotEmpty()
            assertThat(logs).hasSize(1)
        }

    @Test
    fun beginCapture_forAppWindowRecording_logAppWindowRecordingIsTaken() =
        kosmos.runTest {
            val topTask = createRunningTaskInfo(taskId = 1, bounds = Rect(0, 0, 50, 50))
            whenever(appWindowInteractor.getAppWindowTasks(any<Int>())).thenReturn(listOf(topTask))
            setupViewModel(
                LargeScreenCaptureUiParameters(
                    defaultCaptureType = ScreenCaptureType.RECORDING,
                    defaultCaptureRegion = ScreenCaptureRegion.APP_WINDOW,
                )
            )

            viewModel.captureTaskAtPosition(Point(25, 25))
            runCurrent()

            val logs = uiEventLoggerFake.logs
            assertThat(logs).isNotEmpty()
            assertThat(logs).hasSize(1)
        }

    @Test
    fun updateCaptureRegion_toFullscreen_resetsToolbarOpacity() =
        kosmos.runTest {
            setupViewModel(
                LargeScreenCaptureUiParameters(defaultCaptureRegion = ScreenCaptureRegion.PARTIAL)
            )
            val toolbarViewModel = viewModel.toolbarViewModel
            val toolbarBounds = Rect(0, 0, 100, 20)
            toolbarViewModel.setToolbarBounds(toolbarBounds)

            // When the region box intersects with the toolbar, the opacity is dimmed
            val regionBox = Rect(10, 10, 50, 50)
            toolbarViewModel.updateOpacityForRegionBox(
                isInteracting = false,
                regionBoxBounds = regionBox,
            )
            assertThat(toolbarViewModel.toolbarOpacity).isLessThan(1f)

            viewModel.updateCaptureRegion(ScreenCaptureRegion.FULLSCREEN)
            assertThat(toolbarViewModel.toolbarOpacity).isEqualTo(1f)
        }

    @Test
    fun updateTaskSelectionFromHover_selectsCorrectTask() =
        kosmos.runTest {
            val task1 = createRunningTaskInfo(taskId = 1, bounds = Rect(0, 0, 50, 50))
            val task2 = createRunningTaskInfo(taskId = 2, bounds = Rect(60, 60, 100, 100))
            val runningTasks = listOf(task1, task2)
            whenever(appWindowInteractor.getAppWindowTasks(any<Int>())).thenReturn(runningTasks)
            setupViewModel()
            viewModel.updateCaptureRegion(ScreenCaptureRegion.APP_WINDOW)

            // Hover over task 1
            viewModel.updateTaskSelectionFromHover(Point(25, 25))
            assertThat(viewModel.topTask).isEqualTo(task1)

            // Hover over task 2
            viewModel.updateTaskSelectionFromHover(Point(75, 75))
            assertThat(viewModel.topTask).isEqualTo(task2)

            // Hover outside any task
            viewModel.updateTaskSelectionFromHover(Point(55, 55))
            assertThat(viewModel.topTask).isNull()

            // Hover on the edge of task 1
            viewModel.updateTaskSelectionFromHover(Point(0, 0))
            assertThat(viewModel.topTask).isEqualTo(task1)

            // Hover on the edge of task 2
            viewModel.updateTaskSelectionFromHover(Point(99, 99))
            assertThat(viewModel.topTask).isEqualTo(task2)
        }

    @Test
    fun focusTask_selectsCorrectTask() =
        kosmos.runTest {
            val task1 = createRunningTaskInfo(taskId = 1, bounds = Rect(0, 0, 50, 50))
            val runningTasks = listOf(task1)
            whenever(appWindowInteractor.getAppWindowTasks(any<Int>())).thenReturn(runningTasks)
            setupViewModel()
            viewModel.updateCaptureRegion(ScreenCaptureRegion.APP_WINDOW)

            viewModel.focusTask(task1)

            assertThat(viewModel.topTask).isEqualTo(task1)
            assertThat(viewModel.appWindowSelection?.taskBounds)
                .isEqualTo(task1.configuration.windowConfiguration.bounds)
        }

    @Test
    fun unfocusTask_clearsTask() =
        kosmos.runTest {
            val task1 = createRunningTaskInfo(taskId = 1, bounds = Rect(0, 0, 50, 50))
            val runningTasks = listOf(task1)
            whenever(appWindowInteractor.getAppWindowTasks(any<Int>())).thenReturn(runningTasks)
            setupViewModel()
            viewModel.updateCaptureRegion(ScreenCaptureRegion.APP_WINDOW)

            viewModel.focusTask(task1)
            assertThat(viewModel.topTask).isEqualTo(task1)

            viewModel.unfocusTask(task1)
            assertThat(viewModel.topTask).isNull()
            assertThat(viewModel.appWindowSelection).isNull()
        }

    @Test
    fun captureTaskAtPosition_requestsScreenshotForSingleTask() =
        kosmos.runTest {
            val topTask = createRunningTaskInfo(taskId = 1, bounds = Rect(0, 0, 50, 50))
            whenever(appWindowInteractor.getAppWindowTasks(any<Int>())).thenReturn(listOf(topTask))
            setupViewModel()
            viewModel.updateCaptureRegion(ScreenCaptureRegion.APP_WINDOW)
            whenever(kosmos.mockImageCapture.captureTask(topTask.taskId)).thenReturn(mockBitmap)

            viewModel.captureTaskAtPosition(Point(25, 25))
            runCurrent()

            val screenshotRequestCaptor = argumentCaptor<ScreenshotRequest>()
            verify(kosmos.mockScreenshotHelper, times(1))
                .takeScreenshot(screenshotRequestCaptor.capture(), any(), isNull())

            val capturedRequest = screenshotRequestCaptor.lastValue
            assertThat(capturedRequest.type).isEqualTo(WindowManager.TAKE_SCREENSHOT_PROVIDED_IMAGE)
            assertThat(capturedRequest.source)
                .isEqualTo(WindowManager.ScreenshotSource.SCREENSHOT_SCREEN_CAPTURE_UI)
            assertThat(capturedRequest.bitmap).isEqualTo(mockBitmap)
            assertThat(capturedRequest.displayId).isEqualTo(displayId)

            assertUiClosed()
        }

    @Test
    fun captureTaskAtPosition_requestsScreenshotForMultipleTasks() =
        kosmos.runTest {
            setupViewModel()
            val task1 = createRunningTaskInfo(taskId = 1, bounds = Rect(0, 0, 50, 50))
            val task2 = createRunningTaskInfo(taskId = 2, bounds = Rect(60, 60, 100, 100))
            val runningTasks = listOf(task1, task2)

            whenever(appWindowInteractor.getAppWindowTasks(any<Int>())).thenReturn(runningTasks)
            whenever(kosmos.mockImageCapture.captureTask(task1.taskId)).thenReturn(mockBitmap)
            whenever(kosmos.mockImageCapture.captureTask(task2.taskId))
                .thenReturn(mockBackgroundBitmap)

            viewModel.captureTaskAtPosition(Point(25, 25))
            runCurrent()

            val screenshotRequestCaptor = argumentCaptor<ScreenshotRequest>()
            verify(kosmos.mockScreenshotHelper, times(1))
                .takeScreenshot(screenshotRequestCaptor.capture(), any(), isNull())

            val capturedRequest = screenshotRequestCaptor.lastValue
            assertThat(capturedRequest.type).isEqualTo(WindowManager.TAKE_SCREENSHOT_PROVIDED_IMAGE)
            assertThat(capturedRequest.source)
                .isEqualTo(WindowManager.ScreenshotSource.SCREENSHOT_SCREEN_CAPTURE_UI)
            assertThat(capturedRequest.bitmap).isEqualTo(mockBitmap)
            assertThat(capturedRequest.displayId).isEqualTo(displayId)

            assertUiClosed()
        }

    @Test
    fun captureTaskAtPosition_requestsScreenshotForMultipleTasksWithOverlap() =
        kosmos.runTest {
            setupViewModel()
            val task1 = createRunningTaskInfo(taskId = 1, bounds = Rect(0, 0, 50, 50))
            val task2 = createRunningTaskInfo(taskId = 2, bounds = Rect(10, 10, 60, 60))
            // The list of tasks is ordered from top to bottom. task1 is on top of task2.
            val runningTasks = listOf(task1, task2)

            whenever(appWindowInteractor.getAppWindowTasks(any<Int>())).thenReturn(runningTasks)
            whenever(kosmos.mockImageCapture.captureTask(task1.taskId)).thenReturn(mockBitmap)
            whenever(kosmos.mockImageCapture.captureTask(task2.taskId))
                .thenReturn(mockBackgroundBitmap)

            // Click on a point that is within both tasks.
            viewModel.captureTaskAtPosition(Point(25, 25))
            runCurrent()

            val screenshotRequestCaptor = argumentCaptor<ScreenshotRequest>()
            verify(kosmos.mockScreenshotHelper, times(1))
                .takeScreenshot(screenshotRequestCaptor.capture(), any(), isNull())

            val capturedRequest = screenshotRequestCaptor.lastValue
            assertThat(capturedRequest.type).isEqualTo(WindowManager.TAKE_SCREENSHOT_PROVIDED_IMAGE)
            assertThat(capturedRequest.source)
                .isEqualTo(WindowManager.ScreenshotSource.SCREENSHOT_SCREEN_CAPTURE_UI)
            assertThat(capturedRequest.bitmap).isEqualTo(mockBitmap)
            assertThat(capturedRequest.displayId).isEqualTo(displayId)

            assertUiClosed()
        }

    @Test
    fun captureTaskAtPosition_withInvalidPosition_doesNothing() =
        kosmos.runTest {
            setupViewModel()
            val task1 = createRunningTaskInfo(taskId = 1, bounds = Rect(0, 0, 50, 50))
            val runningTasks = listOf(task1)

            whenever(appWindowInteractor.getAppWindowTasks(any<Int>())).thenReturn(runningTasks)

            viewModel.captureTaskAtPosition(Point(75, 75))
            runCurrent()

            val screenshotRequestCaptor = argumentCaptor<ScreenshotRequest>()
            verify(kosmos.mockScreenshotHelper, times(0))
                .takeScreenshot(screenshotRequestCaptor.capture(), any(), isNull())

            assertThat(viewModel.isShowingUi).isTrue()
        }

    @Test
    fun currentSaveLocationUri_whenCustomLocationNotSet_isNull() =
        kosmos.runTest {
            setupViewModel()
            val toolbarViewModel = viewModel.toolbarViewModel

            assertThat(toolbarViewModel.currentSaveLocationUri).isNull()
        }

    @Test
    fun currentSaveLocationUri_whenCustomLocationNotSetAndMadeActive_remainsNull() =
        kosmos.runTest {
            setupViewModel()
            val toolbarViewModel = viewModel.toolbarViewModel
            toolbarViewModel.setCustomSaveLocationActiveStatus(true)

            assertThat(toolbarViewModel.currentSaveLocationUri).isNull()
        }

    @Test
    fun currentSaveLocationUri_whenCustomLocationSetAndActive_isNotNull() =
        kosmos.runTest {
            setupViewModel()
            val toolbarViewModel = viewModel.toolbarViewModel
            val customUri =
                "content://com.android.externalstorage.documents/tree/primary%3ATest".toUri()

            largeScreenCaptureParametersInteractor.setCustomSaveLocation(customUri)
            toolbarViewModel.setCustomSaveLocationActiveStatus(true)

            assertThat(toolbarViewModel.currentSaveLocationUri).isEqualTo(customUri)
        }

    @Test
    fun currentSaveLocationUri_whenCustomLocationSetButInactive_isNull() =
        kosmos.runTest {
            setupViewModel()
            val toolbarViewModel = viewModel.toolbarViewModel
            val customUri =
                "content://com.android.externalstorage.documents/tree/primary%3ATest".toUri()

            largeScreenCaptureParametersInteractor.setCustomSaveLocation(customUri)
            toolbarViewModel.setCustomSaveLocationActiveStatus(false)

            assertThat(toolbarViewModel.currentSaveLocationUri).isNull()
        }

    @Test
    fun calculateVisibleArea_noOverlappingTasks() =
        kosmos.runTest {
            val task1 = createRunningTaskInfo(taskId = 1, bounds = Rect(0, 0, 50, 50))
            val task2 = createRunningTaskInfo(taskId = 2, bounds = Rect(60, 60, 100, 100))
            val runningTasks = listOf(task1, task2)
            whenever(appWindowInteractor.getAppWindowTasks(any<Int>())).thenReturn(runningTasks)
            setupViewModel()
            viewModel.updateCaptureRegion(ScreenCaptureRegion.APP_WINDOW)

            viewModel.updateTaskSelectionFromHover(Point(75, 75))
            runCurrent()

            val appWindowSelection = viewModel.appWindowSelection
            assertThat(appWindowSelection?.taskBounds)
                .isEqualTo(task2.configuration.windowConfiguration.bounds)
            assertThat(appWindowSelection?.overlappingBounds).isEmpty()
        }

    @Test
    fun calculateVisibleArea_oneFullyOverlappingTask() =
        kosmos.runTest {
            val topTask = createRunningTaskInfo(taskId = 1, bounds = Rect(10, 10, 40, 40))
            val bottomTask = createRunningTaskInfo(taskId = 2, bounds = Rect(0, 0, 50, 50))
            val runningTasks = listOf(topTask, bottomTask)
            whenever(appWindowInteractor.getAppWindowTasks(any<Int>())).thenReturn(runningTasks)
            setupViewModel()
            viewModel.updateCaptureRegion(ScreenCaptureRegion.APP_WINDOW)

            viewModel.updateTaskSelectionFromHover(Point(5, 5))
            runCurrent()

            val appWindowSelection = viewModel.appWindowSelection
            assertThat(appWindowSelection?.taskBounds)
                .isEqualTo(bottomTask.configuration.windowConfiguration.bounds)
            assertThat(appWindowSelection?.overlappingBounds)
                .containsExactly(topTask.configuration.windowConfiguration.bounds)
        }

    @Test
    fun calculateVisibleArea_onePartiallyOverlappingTask() =
        kosmos.runTest {
            val topTask = createRunningTaskInfo(taskId = 1, bounds = Rect(30, 30, 70, 70))
            val bottomTask = createRunningTaskInfo(taskId = 2, bounds = Rect(0, 0, 50, 50))
            val runningTasks = listOf(topTask, bottomTask)
            whenever(appWindowInteractor.getAppWindowTasks(any<Int>())).thenReturn(runningTasks)
            setupViewModel()
            viewModel.updateCaptureRegion(ScreenCaptureRegion.APP_WINDOW)

            viewModel.updateTaskSelectionFromHover(Point(25, 25))
            runCurrent()

            val appWindowSelection = viewModel.appWindowSelection
            assertThat(appWindowSelection?.taskBounds)
                .isEqualTo(bottomTask.configuration.windowConfiguration.bounds)
            assertThat(appWindowSelection?.overlappingBounds)
                .containsExactly(topTask.configuration.windowConfiguration.bounds)
        }

    @Test
    fun calculateVisibleArea_multipleOverlappingTasks() =
        kosmos.runTest {
            val topTask1 = createRunningTaskInfo(taskId = 1, bounds = Rect(10, 10, 20, 20))
            val topTask2 = createRunningTaskInfo(taskId = 2, bounds = Rect(30, 30, 40, 40))
            val nonOverlappingTask =
                createRunningTaskInfo(taskId = 3, bounds = Rect(80, 80, 90, 90))
            val bottomTask = createRunningTaskInfo(taskId = 4, bounds = Rect(0, 0, 50, 50))
            val runningTasks = listOf(topTask1, topTask2, nonOverlappingTask, bottomTask)
            whenever(appWindowInteractor.getAppWindowTasks(any<Int>())).thenReturn(runningTasks)
            setupViewModel()
            viewModel.updateCaptureRegion(ScreenCaptureRegion.APP_WINDOW)

            viewModel.updateTaskSelectionFromHover(Point(25, 25))
            runCurrent()

            val appWindowSelection = viewModel.appWindowSelection
            assertThat(appWindowSelection?.taskBounds)
                .isEqualTo(bottomTask.configuration.windowConfiguration.bounds)
            assertThat(appWindowSelection?.overlappingBounds)
                .containsExactly(
                    topTask1.configuration.windowConfiguration.bounds,
                    topTask2.configuration.windowConfiguration.bounds,
                )
        }

    @Test
    fun calculateVisibleArea_taskBelowIsIgnored() =
        kosmos.runTest {
            val topTask = createRunningTaskInfo(taskId = 1, bounds = Rect(0, 0, 50, 50))
            val bottomTask = createRunningTaskInfo(taskId = 2, bounds = Rect(30, 30, 70, 70))
            val runningTasks = listOf(topTask, bottomTask)
            whenever(appWindowInteractor.getAppWindowTasks(any<Int>())).thenReturn(runningTasks)
            setupViewModel()
            viewModel.updateCaptureRegion(ScreenCaptureRegion.APP_WINDOW)

            viewModel.updateTaskSelectionFromHover(Point(25, 25))
            runCurrent()

            val appWindowSelection = viewModel.appWindowSelection
            assertThat(appWindowSelection?.taskBounds)
                .isEqualTo(topTask.configuration.windowConfiguration.bounds)
            assertThat(appWindowSelection?.overlappingBounds).isEmpty()
        }

    @Test
    fun calculateVisibleArea_nonIntersectingTaskIsIgnored() =
        kosmos.runTest {
            val topTask = createRunningTaskInfo(taskId = 1, bounds = Rect(60, 60, 100, 100))
            val bottomTask = createRunningTaskInfo(taskId = 2, bounds = Rect(0, 0, 50, 50))
            val runningTasks = listOf(topTask, bottomTask)
            whenever(appWindowInteractor.getAppWindowTasks(any<Int>())).thenReturn(runningTasks)
            setupViewModel()
            viewModel.updateCaptureRegion(ScreenCaptureRegion.APP_WINDOW)

            viewModel.updateTaskSelectionFromHover(Point(25, 25))
            runCurrent()

            val appWindowSelection = viewModel.appWindowSelection
            assertThat(appWindowSelection?.taskBounds)
                .isEqualTo(bottomTask.configuration.windowConfiguration.bounds)
            assertThat(appWindowSelection?.overlappingBounds).isEmpty()
        }

    private fun setupViewModel(uiParams: LargeScreenCaptureUiParameters? = null) {
        if (uiParams != null) {
            kosmos.largeScreenCaptureUiParameters = uiParams
        }
        viewModel = kosmos.preCaptureViewModelFactory.create(displayId)
        viewModel.activateIn(kosmos.testScope)
    }

    private fun assertUiClosed() {
        with(kosmos) {
            val uiState by
                collectLastValue(
                    kosmos.screenCaptureUiRepository.uiState(
                        com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
                            .RECORD
                    )
                )
            assertThat(uiState).isInstanceOf(ScreenCaptureUiState.Invisible::class.java)
        }
    }

    private fun createRunningTaskInfo(
        taskId: Int,
        bounds: Rect,
        displayId: Int = this.displayId,
        isVisible: Boolean = true,
        numActivities: Int = 1,
    ): ActivityManager.RunningTaskInfo {
        return ActivityManager.RunningTaskInfo().apply {
            this.taskId = taskId
            this.isVisible = isVisible
            this.displayId = displayId
            this.topActivity = ComponentName("test.pkg", "test.class")
            this.configuration.windowConfiguration.apply {
                setBounds(bounds)
                activityType = WindowConfiguration.ACTIVITY_TYPE_STANDARD
            }
            this.numActivities = numActivities
        }
    }
}
