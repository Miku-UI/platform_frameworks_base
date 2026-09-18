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

package com.android.systemui.accessibility.shortcutchooser.ui.composable

import android.graphics.drawable.ShapeDrawable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType
import com.android.systemui.SysuiTestCase
import com.android.systemui.accessibility.shortcutchooser.shared.model.AccessibilityTargetModel
import com.android.systemui.res.R
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class NavBarMoreOptionsDialogContentTest : SysuiTestCase() {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun dialog_showsTitleAndDoneButton() {
        composeRule.setContent {
            NavBarMoreOptionsDialogContent(
                targets = emptyList(),
                selectedTarget = null,
                onDoneClick = {},
                onTargetSelected = {},
            )
        }

        composeRule
            .onNodeWithText(context.getString(R.string.accessibility_nav_bar_more_options_title))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.accessibility_nav_bar_more_options_subtitle))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.accessibility_shortcutchooser_done_button))
            .assertIsDisplayed()
    }

    @Test
    fun dialog_showsTargets_andHighlightsSelected() {
        val target1 = createTarget("Target 1")
        val target2 = createTarget("Target 2")
        val targets = listOf(target1, target2)
        val selectedTarget = mutableStateOf(target1.targetName)

        composeRule.setContent {
            NavBarMoreOptionsDialogContent(
                targets = targets,
                selectedTarget = selectedTarget.value,
                onDoneClick = {},
                onTargetSelected = { selectedTarget.value = it.targetName },
            )
        }

        composeRule.onNodeWithText(target1.featureName).assertIsDisplayed()
        composeRule.onNodeWithText(target2.featureName).assertIsDisplayed()

        // Check radio button selection state (using testTag or similar if set in implementation)
        // Since ShortcutTargetRow sets testTag to targetName on the row
        composeRule.onNodeWithTag(target1.targetName).assertIsSelected()
        composeRule.onNodeWithTag(target2.targetName).assertIsNotSelected()
    }

    @Test
    fun dialog_clickingTarget_updatesSelection() {
        val target1 = createTarget("Target 1")
        val target2 = createTarget("Target 2")
        val targets = listOf(target1, target2)
        var selectedTargetName = mutableStateOf(target1.targetName)

        composeRule.setContent {
            NavBarMoreOptionsDialogContent(
                targets = targets,
                selectedTarget = selectedTargetName.value,
                onDoneClick = {},
                onTargetSelected = { selectedTargetName.value = it.targetName },
            )
        }

        composeRule.onNodeWithTag(target2.targetName).performClick()
        composeRule.onNodeWithTag(target1.targetName).assertIsNotSelected()
        composeRule.onNodeWithTag(target2.targetName).assertIsSelected()
        assertThat(selectedTargetName.value).isEqualTo(target2.targetName)
    }

    @Test
    fun dialog_clickingDone_invokesCallback() {
        var doneClicked = false
        composeRule.setContent {
            NavBarMoreOptionsDialogContent(
                targets = emptyList(),
                selectedTarget = null,
                onDoneClick = { doneClicked = true },
                onTargetSelected = {},
            )
        }

        composeRule
            .onNodeWithText(context.getString(R.string.accessibility_shortcutchooser_done_button))
            .performClick()

        assertThat(doneClicked).isTrue()
    }

    private fun createTarget(label: String): AccessibilityTargetModel {
        return AccessibilityTargetModel(
            shortcutType = UserShortcutType.SOFTWARE,
            targetName = "com.example/.$label",
            featureName = label,
            icon =
                ShapeDrawable().apply {
                    intrinsicWidth = 10
                    intrinsicHeight = 10
                },
            isAssigned = false,
            isToggleable = false,
            isStateOn = false,
        )
    }
}
