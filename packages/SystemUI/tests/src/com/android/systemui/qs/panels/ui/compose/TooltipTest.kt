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

package com.android.systemui.qs.panels.ui.compose

import androidx.compose.foundation.focusable
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class TooltipTest : SysuiTestCase() {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun hover_enabled_showsTooltip_after2Seconds() {
        composeRule.setContent {
            Tooltip(text = "My Tip", enabled = true) { modifier ->
                Text(text = "Hover Me", modifier = modifier)
            }
        }
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithText("Hover Me").performMouseInput { enter(center) }
        composeRule.mainClock.advanceTimeBy(2500L)

        composeRule.onNodeWithText("My Tip").assertIsDisplayed()
    }

    @Test
    fun hover_enabled_doesNotShowTooltip_before2Seconds() {
        composeRule.setContent {
            Tooltip(text = "My Tip", enabled = true) { modifier ->
                Text(text = "Hover Me", modifier = modifier)
            }
        }
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithText("Hover Me").performMouseInput { enter(center) }
        composeRule.mainClock.advanceTimeBy(1500L)

        composeRule.onNodeWithText("My Tip").assertDoesNotExist()
    }

    @Test
    fun mouseExit_hidesTooltip() {
        composeRule.setContent {
            Tooltip(text = "My Tip", enabled = true) { modifier ->
                Text(text = "Hover Me", modifier = modifier)
            }
        }
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithText("Hover Me").performMouseInput { enter(center) }
        composeRule.mainClock.advanceTimeBy(2500L)

        composeRule.onNodeWithText("Hover Me").performMouseInput { exit(Offset(-1f, -1f)) }
        composeRule.mainClock.advanceTimeBy(1000L)

        composeRule.onNodeWithText("My Tip").assertDoesNotExist()
    }

    @Test
    fun hover_disabled_doesNotShowTooltip() {
        composeRule.setContent {
            Tooltip(text = "My Tip", enabled = false) { modifier ->
                Text(text = "Hover Me", modifier = modifier)
            }
        }
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithText("Hover Me").performMouseInput { enter(center) }
        composeRule.mainClock.advanceTimeBy(2500L)

        composeRule.onNodeWithText("My Tip").assertDoesNotExist()
    }

    @Test
    fun hover_blankText_doesNotShowTooltip() {
        composeRule.setContent {
            Tooltip(text = "   ", enabled = true) { modifier ->
                Text(text = "Hover Me", modifier = modifier)
            }
        }
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithText("Hover Me").performMouseInput { enter(center) }
        composeRule.mainClock.advanceTimeBy(2500L)

        composeRule.onNodeWithText("   ").assertDoesNotExist()
    }

    @Test
    fun longClick_doesNotShowTooltip() {
        composeRule.setContent {
            Tooltip(text = "My Tip", enabled = true) { modifier ->
                Text(text = "Long Click Me", modifier = modifier)
            }
        }
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithText("Long Click Me").performTouchInput { longClick() }
        composeRule.mainClock.advanceTimeBy(2500L)

        composeRule.onNodeWithText("My Tip").assertDoesNotExist()
    }

    @Test
    fun keyboardFocus_doesNotShowTooltip() {
        composeRule.setContent {
            Tooltip(text = "My Tip", enabled = true) { modifier ->
                Text(text = "Focus Me", modifier = modifier.focusable())
            }
        }
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithText("Focus Me").requestFocus()
        composeRule.mainClock.advanceTimeBy(2500L)

        composeRule.onNodeWithText("My Tip").assertDoesNotExist()
    }

    @Test
    fun disabled_passesOriginalModifierToContent() {
        composeRule.setContent {
            Tooltip(
                text = "My Tip",
                enabled = false,
                modifier = Modifier.testTag("original_modifier"),
            ) { modifier ->
                Text(text = "Anchor", modifier = modifier)
            }
        }

        composeRule
            .onNode(hasText("Anchor") and hasTestTag("original_modifier"), useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun enabled_consumesModifierAndPassesEmptyToContent() {
        composeRule.setContent {
            Tooltip(
                text = "My Tip",
                enabled = true,
                modifier = Modifier.testTag("original_modifier"),
            ) { modifier ->
                Text(text = "Anchor", modifier = modifier)
            }
        }

        composeRule
            .onNode(hasText("Anchor") and hasTestTag("original_modifier"), useUnmergedTree = true)
            .assertDoesNotExist()
    }
}
