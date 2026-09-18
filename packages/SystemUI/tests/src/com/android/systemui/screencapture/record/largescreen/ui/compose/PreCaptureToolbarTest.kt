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

package com.android.systemui.screencapture.record.largescreen.ui.compose

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureRegion
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.record.largescreen.ui.viewmodel.PreCaptureToolbarViewModel
import com.android.systemui.screencapture.record.largescreen.ui.viewmodel.preCaptureToolbarViewModel
import com.android.systemui.testKosmosNew
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

@SmallTest
@RunWith(AndroidJUnit4::class)
class PreCaptureToolbarTest : SysuiTestCase() {

    @get:Rule val composeTestRule = createComposeRule()

    @Mock private lateinit var mockOnCaptureTypeSelectedCallback: (ScreenCaptureType) -> Unit
    @Mock private lateinit var mockOnCaptureRegionSelectedCallback: (ScreenCaptureRegion) -> Unit
    @Mock private lateinit var mockOnCloseClick: () -> Unit

    private val kosmos = testKosmosNew()
    private lateinit var viewModel: PreCaptureToolbarViewModel

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        viewModel = kosmos.preCaptureToolbarViewModel
        viewModel.activateIn(kosmos.testScope)
    }

    @Test
    @EnableFlags(Flags.FLAG_LARGE_SCREEN_SCREENSHOT_APP_WINDOW)
    fun appWindowButton_whenFlagIsEnabled_isDisplayed_andHasCorrectContentDescription() =
        kosmos.runTest {
            composeTestRule.setContent {
                PreCaptureToolbar(
                    viewModel = viewModel,
                    selectedCaptureType = ScreenCaptureType.SCREENSHOT,
                    selectedCaptureRegion = ScreenCaptureRegion.FULLSCREEN,
                    onCaptureTypeSelected = {},
                    onCaptureRegionSelected = {},
                    onCloseClick = {},
                )
            }

            val buttonDescription =
                context.getString(R.string.screen_capture_toolbar_app_window_button_screenshot_a11y)
            composeTestRule
                .onNodeWithContentDescription(buttonDescription)
                .assertExists()
                .assertIsDisplayed()
                .assertContentDescriptionEquals(buttonDescription)
        }

    @Test
    @DisableFlags(Flags.FLAG_LARGE_SCREEN_SCREENSHOT_APP_WINDOW)
    fun appWindowButton_whenFlagIsDisabled_isNotDisplayed() =
        kosmos.runTest {
            composeTestRule.setContent {
                PreCaptureToolbar(
                    viewModel = viewModel,
                    selectedCaptureType = ScreenCaptureType.SCREENSHOT,
                    selectedCaptureRegion = ScreenCaptureRegion.FULLSCREEN,
                    onCaptureTypeSelected = {},
                    onCaptureRegionSelected = {},
                    onCloseClick = {},
                )
            }

            val buttonDescription =
                context.getString(R.string.screen_capture_toolbar_app_window_button_screenshot_a11y)
            composeTestRule.onNodeWithContentDescription(buttonDescription).assertDoesNotExist()
        }

    @Test
    @EnableFlags(Flags.FLAG_LARGE_SCREEN_RECORDING)
    fun recordButton_whenFlagIsEnabled_isDisplayed_andHasCorrectContentDescription() =
        kosmos.runTest {
            composeTestRule.setContent {
                PreCaptureToolbar(
                    viewModel = viewModel,
                    selectedCaptureType = ScreenCaptureType.SCREENSHOT,
                    selectedCaptureRegion = ScreenCaptureRegion.FULLSCREEN,
                    onCaptureTypeSelected = {},
                    onCaptureRegionSelected = {},
                    onCloseClick = {},
                )
            }

            val buttonText = context.getString(R.string.screen_capture_toolbar_record_button)
            composeTestRule
                .onNodeWithText(buttonText)
                .assertExists()
                .assertIsDisplayed()
                .assertTextEquals(buttonText)
        }

    @Test
    @DisableFlags(Flags.FLAG_LARGE_SCREEN_RECORDING)
    fun recordButton_whenFlagIsDisabled_isNotDisplayed() =
        kosmos.runTest {
            composeTestRule.setContent {
                PreCaptureToolbar(
                    viewModel = viewModel,
                    selectedCaptureType = ScreenCaptureType.SCREENSHOT,
                    selectedCaptureRegion = ScreenCaptureRegion.FULLSCREEN,
                    onCaptureTypeSelected = {},
                    onCaptureRegionSelected = {},
                    onCloseClick = {},
                )
            }

            val buttonDescription = context.getString(R.string.screen_capture_toolbar_record_button)
            composeTestRule.onNodeWithContentDescription(buttonDescription).assertIsNotDisplayed()
        }

    @Test
    @EnableFlags(Flags.FLAG_LARGE_SCREEN_RECORDING)
    fun settingsButton_whenFlagIsEnabled_isDisplayed_andHasCorrectContentDescription() =
        kosmos.runTest {
            composeTestRule.setContent {
                PreCaptureToolbar(
                    viewModel = viewModel,
                    selectedCaptureType = ScreenCaptureType.SCREENSHOT,
                    selectedCaptureRegion = ScreenCaptureRegion.FULLSCREEN,
                    onCaptureTypeSelected = {},
                    onCaptureRegionSelected = {},
                    onCloseClick = {},
                )
            }

            val buttonDescription =
                context.getString(R.string.screen_capture_toolbar_settings_button_a11y)
            composeTestRule
                .onNodeWithContentDescription(buttonDescription)
                .assertExists()
                .assertIsDisplayed()
                .assertContentDescriptionEquals(buttonDescription)
        }

    @Test
    @DisableFlags(Flags.FLAG_LARGE_SCREEN_RECORDING)
    fun settingsButton_whenFlagIsDisabled_isNotDisplayed() =
        kosmos.runTest {
            composeTestRule.setContent {
                PreCaptureToolbar(
                    viewModel = viewModel,
                    selectedCaptureType = ScreenCaptureType.SCREENSHOT,
                    selectedCaptureRegion = ScreenCaptureRegion.FULLSCREEN,
                    onCaptureTypeSelected = {},
                    onCaptureRegionSelected = {},
                    onCloseClick = {},
                )
            }

            val buttonDescription =
                context.getString(R.string.screen_capture_toolbar_settings_button_a11y)
            composeTestRule.onNodeWithContentDescription(buttonDescription).assertDoesNotExist()
        }

    @Test
    @EnableFlags(Flags.FLAG_LARGE_SCREEN_RECORDING)
    fun regionScreenShotButton_whenScreenCaptureTypeIsScreenshot_andHasCorrectContentDescription() =
        kosmos.runTest {
            composeTestRule.setContent {
                PreCaptureToolbar(
                    viewModel = viewModel,
                    selectedCaptureType = ScreenCaptureType.SCREENSHOT,
                    selectedCaptureRegion = ScreenCaptureRegion.PARTIAL,
                    onCaptureTypeSelected = {},
                    onCaptureRegionSelected = {},
                    onCloseClick = {},
                )
            }

            val buttonDescription =
                context.getString(R.string.screen_capture_toolbar_region_button_screenshot_a11y)
            composeTestRule.onNodeWithContentDescription(buttonDescription).assertExists()
        }

    @Test
    @EnableFlags(Flags.FLAG_LARGE_SCREEN_RECORDING, Flags.FLAG_LARGE_SCREEN_REGION_RECORDING)
    fun regionRecordButton_whenScreenCaptureTypeIsRecord_andHasCorrectContentDescription() =
        kosmos.runTest {
            composeTestRule.setContent {
                PreCaptureToolbar(
                    viewModel = viewModel,
                    selectedCaptureType = ScreenCaptureType.RECORDING,
                    selectedCaptureRegion = ScreenCaptureRegion.PARTIAL,
                    onCaptureTypeSelected = {},
                    onCaptureRegionSelected = {},
                    onCloseClick = {},
                )
            }

            val buttonDescription =
                context.getString(R.string.screen_capture_toolbar_region_button_record_a11y)
            composeTestRule.onNodeWithContentDescription(buttonDescription).assertExists()
        }

    @Test
    @EnableFlags(Flags.FLAG_LARGE_SCREEN_RECORDING)
    fun regionRecordButton_whenScreenCaptureTypeIsRecord_isNotDisplayed() =
        kosmos.runTest {
            composeTestRule.setContent {
                PreCaptureToolbar(
                    viewModel = viewModel,
                    selectedCaptureType = ScreenCaptureType.RECORDING,
                    selectedCaptureRegion = ScreenCaptureRegion.FULLSCREEN,
                    onCaptureTypeSelected = {},
                    onCaptureRegionSelected = {},
                    onCloseClick = {},
                )
            }

            composeTestRule.waitForIdle()

            val buttonDescription =
                context.getString(R.string.screen_capture_toolbar_region_button_record_a11y)
            composeTestRule.onNodeWithContentDescription(buttonDescription).assertIsNotDisplayed()
        }

    @Test
    @EnableFlags(Flags.FLAG_LARGE_SCREEN_RECORDING)
    fun captureTypeButtons_whenScreenshotButtonClicked_firesCallback() =
        kosmos.runTest {
            composeTestRule.setContent {
                PreCaptureToolbar(
                    viewModel = viewModel,
                    selectedCaptureType = ScreenCaptureType.RECORDING,
                    selectedCaptureRegion = ScreenCaptureRegion.FULLSCREEN,
                    onCaptureTypeSelected = mockOnCaptureTypeSelectedCallback,
                    onCaptureRegionSelected = {},
                    onCloseClick = {},
                )
            }

            val buttonText = context.getString(R.string.screen_capture_toolbar_screenshot_button)
            composeTestRule.onNodeWithText(buttonText).performClick()

            verify(mockOnCaptureTypeSelectedCallback).invoke(ScreenCaptureType.SCREENSHOT)
        }

    @Test
    @EnableFlags(Flags.FLAG_LARGE_SCREEN_RECORDING)
    fun captureTypeButtons_whenRecordButtonClicked_firesCallback() =
        kosmos.runTest {
            composeTestRule.setContent {
                PreCaptureToolbar(
                    viewModel = viewModel,
                    selectedCaptureType = ScreenCaptureType.SCREENSHOT,
                    selectedCaptureRegion = ScreenCaptureRegion.FULLSCREEN,
                    onCaptureTypeSelected = mockOnCaptureTypeSelectedCallback,
                    onCaptureRegionSelected = {},
                    onCloseClick = {},
                )
            }

            val buttonText = context.getString(R.string.screen_capture_toolbar_record_button)
            composeTestRule.onNodeWithText(buttonText).performClick()

            verify(mockOnCaptureTypeSelectedCallback).invoke(ScreenCaptureType.RECORDING)
        }

    @Test
    @EnableFlags(Flags.FLAG_LARGE_SCREEN_SCREENSHOT_APP_WINDOW)
    fun captureRegionButtons_whenAppWindowButtonClicked_firesCallback() =
        kosmos.runTest {
            composeTestRule.setContent {
                PreCaptureToolbar(
                    viewModel = viewModel,
                    selectedCaptureType = ScreenCaptureType.SCREENSHOT,
                    selectedCaptureRegion = ScreenCaptureRegion.FULLSCREEN,
                    onCaptureTypeSelected = {},
                    onCaptureRegionSelected = mockOnCaptureRegionSelectedCallback,
                    onCloseClick = {},
                )
            }

            val buttonDescription =
                context.getString(R.string.screen_capture_toolbar_app_window_button_screenshot_a11y)
            composeTestRule.onNodeWithContentDescription(buttonDescription).performClick()

            verify(mockOnCaptureRegionSelectedCallback).invoke(ScreenCaptureRegion.APP_WINDOW)
        }

    @Test
    fun captureRegionButtons_whenPartialButtonClicked_firesCallback() =
        kosmos.runTest {
            composeTestRule.setContent {
                PreCaptureToolbar(
                    viewModel = viewModel,
                    selectedCaptureType = ScreenCaptureType.SCREENSHOT,
                    selectedCaptureRegion = ScreenCaptureRegion.FULLSCREEN,
                    onCaptureTypeSelected = {},
                    onCaptureRegionSelected = mockOnCaptureRegionSelectedCallback,
                    onCloseClick = {},
                )
            }

            val buttonDescription =
                context.getString(R.string.screen_capture_toolbar_region_button_screenshot_a11y)
            composeTestRule.onNodeWithContentDescription(buttonDescription).performClick()

            verify(mockOnCaptureRegionSelectedCallback).invoke(ScreenCaptureRegion.PARTIAL)
        }

    @Test
    fun captureRegionButtons_whenFullscreenButtonClicked_firesCallback() =
        kosmos.runTest {
            composeTestRule.setContent {
                PreCaptureToolbar(
                    viewModel = viewModel,
                    selectedCaptureType = ScreenCaptureType.SCREENSHOT,
                    selectedCaptureRegion = ScreenCaptureRegion.PARTIAL,
                    onCaptureTypeSelected = {},
                    onCaptureRegionSelected = mockOnCaptureRegionSelectedCallback,
                    onCloseClick = {},
                )
            }

            val buttonDescription =
                context.getString(R.string.screen_capture_toolbar_fullscreen_button_screenshot_a11y)
            composeTestRule.onNodeWithContentDescription(buttonDescription).performClick()

            verify(mockOnCaptureRegionSelectedCallback).invoke(ScreenCaptureRegion.FULLSCREEN)
        }

    @Test
    fun closeButton_whenClicked_firesCallback() =
        kosmos.runTest {
            composeTestRule.setContent {
                PreCaptureToolbar(
                    viewModel = viewModel,
                    selectedCaptureType = ScreenCaptureType.SCREENSHOT,
                    selectedCaptureRegion = ScreenCaptureRegion.FULLSCREEN,
                    onCaptureTypeSelected = {},
                    onCaptureRegionSelected = {},
                    onCloseClick = mockOnCloseClick,
                )
            }

            val buttonDescription =
                context.getString(R.string.underlay_close_button_content_description)
            composeTestRule.onNodeWithContentDescription(buttonDescription).performClick()

            verify(mockOnCloseClick).invoke()
        }
}
