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

import android.graphics.Bitmap
import android.graphics.Rect
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.view.WindowManager
import android.view.WindowMetrics
import android.view.windowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.uiEventLoggerFake
import com.android.internal.util.ScreenshotRequest
import com.android.internal.util.mockScreenshotHelper
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.advanceTimeBy
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.screencapture.ScreenCaptureEvent
import com.android.systemui.screencapture.common.shared.model.LargeScreenCaptureUiParameters
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiState
import com.android.systemui.screencapture.common.shared.model.largeScreenCaptureUiParameters
import com.android.systemui.screencapture.data.repository.screenCaptureUiRepository
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureRegion
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureType
import com.android.systemui.screenrecord.ScreenRecordingAudioSource
import com.android.systemui.screenrecord.domain.ScreenRecordingParameters
import com.android.systemui.screenrecord.domain.interactor.ScreenRecordingServiceInteractor
import com.android.systemui.screenrecord.domain.interactor.screenRecordingServiceInteractor
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class PreCaptureViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    @Mock
    private lateinit var mockScreenRecordingServiceInteractor: ScreenRecordingServiceInteractor
    @Mock private lateinit var mockBitmap: Bitmap
    @Mock private lateinit var mockWindowMetrics: WindowMetrics
    private val screenBounds = Rect(0, 0, 100, 100)
    private val displayId = 1234
    private lateinit var viewModel: PreCaptureViewModel

    private fun setupViewModel(uiParams: LargeScreenCaptureUiParameters? = null) {
        if (uiParams != null) {
            kosmos.largeScreenCaptureUiParameters = uiParams
        }
        kosmos.screenRecordingServiceInteractor = mockScreenRecordingServiceInteractor
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
            assertThat(uiState).isEqualTo(ScreenCaptureUiState.Invisible)
        }
    }

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(kosmos.windowManager.currentWindowMetrics).thenReturn(mockWindowMetrics)
        whenever(mockWindowMetrics.bounds).thenReturn(screenBounds)
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
    fun captureRegion_defaultsToFullscreen() =
        kosmos.runTest {
            setupViewModel()

            assertThat(viewModel.captureRegion).isEqualTo(ScreenCaptureRegion.FULLSCREEN)
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
    fun updateCaptureType_updatesSelectedCaptureTypeButtonViewModel() =
        kosmos.runTest {
            setupViewModel()

            // Screenshot type is default selected
            val (screenRecordButton, screenshotButton) = viewModel.captureTypeButtonViewModels
            assertThat(screenRecordButton.isSelected).isFalse()
            assertThat(screenshotButton.isSelected).isTrue()

            viewModel.updateCaptureType(ScreenCaptureType.RECORDING)

            val (screenRecordButton2, screenshotButton2) = viewModel.captureTypeButtonViewModels
            assertThat(screenRecordButton2.isSelected).isTrue()
            assertThat(screenshotButton2.isSelected).isFalse()
        }

    @Test
    fun updateCaptureType_usesCorrectIconWhenSelected() =
        kosmos.runTest {
            setupViewModel()

            val (screenRecordButton, screenshotButton) = viewModel.captureTypeButtonViewModels
            assertThat(screenRecordButton.icon).isEqualTo(viewModel.icons?.screenRecord)
            // Screenshot is selected by default.
            assertThat(screenshotButton.icon).isEqualTo(viewModel.icons?.screenshotToolbar)

            viewModel.updateCaptureType(ScreenCaptureType.RECORDING)

            val (screenRecordButton2, screenshotButton2) = viewModel.captureTypeButtonViewModels
            assertThat(screenRecordButton2.icon).isEqualTo(viewModel.icons?.screenRecord)
            assertThat(screenshotButton2.icon)
                .isEqualTo(viewModel.icons?.screenshotToolbarUnselected)
        }

    @Test
    @EnableFlags(Flags.FLAG_LARGE_SCREEN_SCREENSHOT_APP_WINDOW)
    fun updateCaptureRegion_updatesSelectedCaptureRegionButton() =
        kosmos.runTest {
            setupViewModel()

            // Default region is fullscreen
            val (appWindowButton, partialButton, fullscreenButton) =
                viewModel.captureRegionButtonViewModels
            assertThat(appWindowButton.isSelected).isFalse()
            assertThat(partialButton.isSelected).isFalse()
            assertThat(fullscreenButton.isSelected).isTrue()

            viewModel.updateCaptureRegion(ScreenCaptureRegion.PARTIAL)

            val (appWindowButton2, partialButton2, fullscreenButton2) =
                viewModel.captureRegionButtonViewModels
            assertThat(appWindowButton2.isSelected).isFalse()
            assertThat(partialButton2.isSelected).isTrue()
            assertThat(fullscreenButton2.isSelected).isFalse()

            viewModel.updateCaptureRegion(ScreenCaptureRegion.APP_WINDOW)

            val (appWindowButton3, partialButton3, fullscreenButton3) =
                viewModel.captureRegionButtonViewModels
            assertThat(appWindowButton3.isSelected).isTrue()
            assertThat(partialButton3.isSelected).isFalse()
            assertThat(fullscreenButton3.isSelected).isFalse()
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
    fun beginCapture_forFullscreenScreenshot_makesCorrectRequest() =
        kosmos.runTest {
            setupViewModel()

            viewModel.updateCaptureType(ScreenCaptureType.SCREENSHOT)
            viewModel.updateCaptureRegion(ScreenCaptureRegion.FULLSCREEN)

            viewModel.beginCapture()

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
            advanceTimeBy(100)
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
            advanceTimeBy(100)
            runCurrent()

            assertUiClosed()
        }

    @Test
    fun beginCapture_forFullScreenRecording_startsRecordingWithCorrectParameters() =
        kosmos.runTest {
            setupViewModel()

            viewModel.updateCaptureType(ScreenCaptureType.RECORDING)
            viewModel.updateCaptureRegion(ScreenCaptureRegion.FULLSCREEN)

            viewModel.beginCapture()

            val paramsCaptor = argumentCaptor<ScreenRecordingParameters>()
            verify(mockScreenRecordingServiceInteractor, times(1))
                .startRecording(paramsCaptor.capture())
            val capturedParams = paramsCaptor.lastValue
            with(capturedParams) {
                assertThat(captureTarget).isNull()
                assertThat(audioSource).isEqualTo(ScreenRecordingAudioSource.INTERNAL)
                assertThat(this.displayId).isEqualTo(displayId)
                assertThat(shouldShowTaps).isFalse()
            }
        }

    @Test
    @DisableFlags(Flags.FLAG_LARGE_SCREEN_SCREENSHOT_APP_WINDOW)
    fun captureRegionButtonViewModels_excludesAppWindowWithFeatureDisabled() =
        kosmos.runTest {
            setupViewModel()

            // TODO(b/430364500) Once a11y label is available, use it for a more robust assertion.
            viewModel.updateCaptureRegion(ScreenCaptureRegion.APP_WINDOW)
            assertThat(viewModel.captureRegionButtonViewModels.none { it.isSelected }).isTrue()
        }

    @Test
    @EnableFlags(Flags.FLAG_LARGE_SCREEN_SCREENSHOT_APP_WINDOW)
    fun captureRegionButtonViewModels_includesAppWindowWithFeatureEnabled() =
        kosmos.runTest {
            setupViewModel()

            // TODO(b/430364500) Once a11y label is available, use it for a more robust assertion.
            viewModel.updateCaptureRegion(ScreenCaptureRegion.APP_WINDOW)
            assertThat(viewModel.captureRegionButtonViewModels.count { it.isSelected }).isEqualTo(1)
        }

    @Test
    @EnableFlags(Flags.FLAG_LARGE_SCREEN_SCREENSHOT_APP_WINDOW)
    fun captureRegionButtonViewModels_hasScreenshotContentDescriptions_byDefault() =
        kosmos.runTest {
            setupViewModel()

            val (appWindowButton, partialButton, fullscreenButton) =
                viewModel.captureRegionButtonViewModels

            // Default capture type is SCREENSHOT.
            assertThat(viewModel.captureType).isEqualTo(ScreenCaptureType.SCREENSHOT)
            assertThat(appWindowButton.contentDescription)
                .isEqualTo(
                    context.getString(
                        R.string.screen_capture_toolbar_app_window_button_screenshot_a11y
                    )
                )
            assertThat(partialButton.contentDescription)
                .isEqualTo(
                    context.getString(R.string.screen_capture_toolbar_region_button_screenshot_a11y)
                )
            assertThat(fullscreenButton.contentDescription)
                .isEqualTo(
                    context.getString(
                        R.string.screen_capture_toolbar_fullscreen_button_screenshot_a11y
                    )
                )
        }

    @Test
    @EnableFlags(Flags.FLAG_LARGE_SCREEN_SCREENSHOT_APP_WINDOW)
    fun captureRegionButtonViewModels_hasRecordContentDescriptions_whenCaptureTypeIsRecord() =
        kosmos.runTest {
            setupViewModel()

            viewModel.updateCaptureType(ScreenCaptureType.RECORDING)

            val (appWindowButton, partialButton, fullscreenButton) =
                viewModel.captureRegionButtonViewModels

            assertThat(appWindowButton.contentDescription)
                .isEqualTo(
                    context.getString(R.string.screen_capture_toolbar_app_window_button_record_a11y)
                )
            assertThat(partialButton.contentDescription)
                .isEqualTo(
                    context.getString(R.string.screen_capture_toolbar_region_button_record_a11y)
                )
            assertThat(fullscreenButton.contentDescription)
                .isEqualTo(
                    context.getString(R.string.screen_capture_toolbar_fullscreen_button_record_a11y)
                )
        }

    @Test
    fun hideUi_updatesState() =
        kosmos.runTest {
            setupViewModel()

            viewModel.hideUi()

            assertThat(viewModel.isShowingUi).isFalse()
        }

    @Test
    fun closeFromToolbar_dismissesWindow() =
        kosmos.runTest {
            setupViewModel()

            viewModel.closeFromToolbar()

            assertUiClosed()
        }

    @Test
    fun closeFromToolbar_logsEvent() =
        kosmos.runTest {
            setupViewModel()

            viewModel.closeFromToolbar()

            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
            assertThat(uiEventLoggerFake.eventId(0))
                .isEqualTo(
                    ScreenCaptureEvent.SCREEN_CAPTURE_LARGE_SCREEN_CLOSE_UI_WITHOUT_CAPTURE.id
                )
        }

    @Test
    fun updateToolbarOpacityForRegionBox_isInteracting_opacityIsZero() =
        kosmos.runTest {
            setupViewModel()
            viewModel.updateToolbarOpacityForRegionBox(isInteracting = true)

            assertThat(viewModel.toolbarOpacity).isEqualTo(0f)
        }

    @Test
    fun updateToolbarOpacityForRegionBox_notInteracting_noOverlap_opacityIsOne() =
        kosmos.runTest {
            setupViewModel()
            viewModel.updateToolbarBounds(Rect(0, 0, 100, 100))
            viewModel.updateRegionBoxBounds(Rect(200, 200, 300, 300))

            viewModel.updateToolbarOpacityForRegionBox(isInteracting = false)

            assertThat(viewModel.toolbarOpacity).isEqualTo(1f)
        }

    @Test
    fun updateToolbarOpacityForRegionBox_notInteracting_overlap_opacityIsPoint15() =
        kosmos.runTest {
            setupViewModel()
            viewModel.updateToolbarBounds(Rect(0, 0, 100, 100))
            viewModel.updateRegionBoxBounds(Rect(50, 50, 150, 150))

            viewModel.updateToolbarOpacityForRegionBox(isInteracting = false)

            assertThat(viewModel.toolbarOpacity).isEqualTo(0.15f)
        }
}
