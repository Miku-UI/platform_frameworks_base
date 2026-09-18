/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.settingslib.spa.widget.button

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActionButtonsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun button_displayed() {
        composeTestRule.setContent {
            ActionButtons(
                listOf(
                    ActionButton(
                        text = "Open",
                        imageVector = Icons.AutoMirrored.Outlined.Launch
                    ) {},
                )
            )
        }

        composeTestRule.onNodeWithText("Open").assertIsDisplayed()
    }

    @Test
    fun button_clickable() {
        var clicked by mutableStateOf(false)
        composeTestRule.setContent {
            ActionButtons(
                listOf(
                    ActionButton(text = "Open", imageVector = Icons.AutoMirrored.Outlined.Launch) {
                        clicked = true
                    },
                )
            )
        }

        composeTestRule.onNodeWithText("Open").performClick()

        assertThat(clicked).isTrue()
    }

    @Test
    fun button_disabledIsNotClickable() {
        var clicked by mutableStateOf(false)
        composeTestRule.setContent {
            ActionButtons(
                listOf(
                    ActionButton(
                        text = "Open",
                        imageVector = Icons.AutoMirrored.Outlined.Launch,
                        enabled = false
                    ) {
                        clicked = true
                    },
                )
            )
        }

        val actionButtonNode = composeTestRule.onNodeWithText("Open")

        // Perform touches from top to bottom of the action button
        actionButtonNode.performTouchInput { click(topCenter) }
        actionButtonNode.performTouchInput { click(center) }
        actionButtonNode.performTouchInput { click(bottomCenter) } // where the text is

        assertThat(clicked).isFalse()
    }

    @Test
    fun button_enabled_hasEnabledSemantics() {
        composeTestRule.setContent {
            ActionButtons(
                listOf(
                    ActionButton(
                        text = "Open",
                        imageVector = Icons.AutoMirrored.Outlined.Launch,
                        enabled = true
                    ) {},
                )
            )
        }

        // Verifies that the button node has the correct enabled semantics for accessibility
        composeTestRule.onNode(
            hasAnyDescendant(hasText("Open")) and hasClickAction(),
            useUnmergedTree = true
        ).assertIsEnabled()
    }

    @Test
    fun button_disabled_hasDisabledSemantics() {
        composeTestRule.setContent {
            ActionButtons(
                listOf(
                    ActionButton(
                        text = "Open",
                        imageVector = Icons.AutoMirrored.Outlined.Launch,
                        enabled = false
                    ) {},
                )
            )
        }

        // Verifies that the button node has the correct disabled semantics for accessibility (e.g., TalkBack)
        composeTestRule.onNode(
            hasAnyDescendant(hasText("Open")) and hasClickAction(),
            useUnmergedTree = true
        ).assertIsNotEnabled()
    }

    @Test
    fun twoButtons_positionIsAligned() {
        composeTestRule.setContent {
            ActionButtons(
                listOf(
                    ActionButton(
                        text = "Open",
                        imageVector = Icons.AutoMirrored.Outlined.Launch
                    ) {},
                    ActionButton(text = "Close", imageVector = Icons.Outlined.Close) {},
                )
            )
        }

        assertThat(composeTestRule.onNodeWithText("Open").getBoundsInRoot().top)
            .isEqualTo(composeTestRule.onNodeWithText("Close").getBoundsInRoot().top)
    }
}
