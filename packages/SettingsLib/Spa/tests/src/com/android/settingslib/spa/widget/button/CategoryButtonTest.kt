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

package com.android.settingslib.spa.widget.button

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryButtonTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun categoryButton_textDisplayed() {
        composeTestRule.setContent {
            CategoryButton(icon = Icons.Outlined.Add, text = TEXT) {}
        }

        composeTestRule.onNodeWithText(TEXT).assertIsDisplayed()
    }

    @Test
    fun categoryButton_onClick() {
        var clicked = false

        composeTestRule.setContent {
            CategoryButton(icon = Icons.Outlined.Add, text = TEXT) { clicked = true }
        }
        composeTestRule.onNodeWithText(TEXT).performClick()

        assertThat(clicked).isTrue()
    }

    private companion object {
        const val TEXT = "Text"
    }
}
