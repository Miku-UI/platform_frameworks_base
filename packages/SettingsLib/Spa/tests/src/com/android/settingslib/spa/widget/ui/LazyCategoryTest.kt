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

package com.android.settingslib.spa.widget.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LazyCategoryTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun header_displayed() {
        composeTestRule.setContent { TestLazyCategory() }

        composeTestRule.onNodeWithText("Header").assertIsDisplayed()
    }

    @Test
    fun footer_displayed() {
        composeTestRule.setContent { TestLazyCategory() }

        composeTestRule.onNodeWithText("Footer").assertIsDisplayed()
    }

    @Test
    fun item_displayed() {
        composeTestRule.setContent { TestLazyCategory() }

        composeTestRule.onNodeWithText("0").assertIsDisplayed()
        composeTestRule.onNodeWithText("1").assertIsDisplayed()
    }

    @Test
    fun groupTitle_displayed() {
        composeTestRule.setContent { TestLazyCategory() }

        composeTestRule.onAllNodesWithText("Group 0").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Group 1").assertCountEquals(1)
    }

    @Composable
    private fun TestLazyCategory() {
        LazyCategory(
            count = 4,
            key = { index -> index },
            header = { Text("Header") },
            footer = { Text("Footer") },
            groupTitle = { index -> "Group ${index / 2}" },
        ) { index ->
            Text(index.toString())
        }
    }
}
