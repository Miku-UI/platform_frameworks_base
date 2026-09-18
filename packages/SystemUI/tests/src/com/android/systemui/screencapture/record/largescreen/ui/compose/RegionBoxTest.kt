/*
 * Copyright (C) 2026 The Android Open Source Project
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

import android.graphics.Rect
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.NativeKeyEvent
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyPress
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock

@SmallTest
@RunWith(AndroidJUnit4::class)
class RegionBoxTest : SysuiTestCase() {

    @get:Rule val composeTestRule = createComposeRule()

    private val onCaptureClick: () -> Unit = mock()
    private val onRegionSelected: (Rect) -> Unit = mock()
    private val onInteractionStateChanged: (Boolean) -> Unit = mock()

    @Test
    fun pressingEnter_triggersCapture_whenRegionExists() {
        // Start with an existing region.
        val initialRect = Rect(100, 100, 500, 500)

        composeTestRule.setContent {
            RegionBox(
                initialRect = initialRect,
                buttonText = "Capture",
                buttonIcon = null,
                onRegionSelected = onRegionSelected,
                onCaptureClick = onCaptureClick,
                onInteractionStateChanged = onInteractionStateChanged,
            )
        }

        // Simulate pressing the Enter key.
        composeTestRule
            .onRoot()
            .performKeyPress(
                KeyEvent(NativeKeyEvent(NativeKeyEvent.ACTION_DOWN, NativeKeyEvent.KEYCODE_ENTER))
            )

        verify(onCaptureClick).invoke()
    }

    @Test
    fun pressingSpacebar_triggersCapture_whenRegionExists() {
        val initialRect = Rect(100, 100, 500, 500)

        composeTestRule.setContent {
            RegionBox(
                initialRect = initialRect,
                buttonText = "Capture",
                buttonIcon = null,
                onRegionSelected = onRegionSelected,
                onCaptureClick = onCaptureClick,
                onInteractionStateChanged = onInteractionStateChanged,
            )
        }

        // Simulate pressing the Spacebar
        composeTestRule
            .onRoot()
            .performKeyPress(
                KeyEvent(NativeKeyEvent(NativeKeyEvent.ACTION_DOWN, NativeKeyEvent.KEYCODE_SPACE))
            )

        verify(onCaptureClick).invoke()
    }
}
