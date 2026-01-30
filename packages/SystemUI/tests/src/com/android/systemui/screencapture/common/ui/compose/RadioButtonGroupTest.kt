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

package com.android.systemui.screencapture.common.ui.compose

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class RadioButtonGroupTest : SysuiTestCase() {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun radiobuttonGroup_withContentDescription_setsSemantics() {
        val testContentDescription = "Test Description"
        val testItems =
            listOf(
                RadioButtonGroupItem(
                    isSelected = true,
                    onClick = {},
                    label = "Ignored Label",
                    contentDescription = testContentDescription,
                )
            )

        composeTestRule.setContent { RadioButtonGroup(items = testItems) }

        composeTestRule
            .onNodeWithContentDescription(testContentDescription)
            .assertIsDisplayed()
            .assertContentDescriptionEquals(testContentDescription)
    }

    @Test
    fun radioButtonGroup_nullContentDescription_fallsBackToLabel() {
        val testLabel = "Fallback Label"
        val testItems =
            listOf(
                RadioButtonGroupItem(
                    isSelected = true,
                    onClick = {},
                    label = testLabel,
                    contentDescription = null, // Explicitly null
                )
            )

        composeTestRule.setContent { RadioButtonGroup(items = testItems) }

        composeTestRule.onNodeWithContentDescription(testLabel).assertIsDisplayed()
    }
}
