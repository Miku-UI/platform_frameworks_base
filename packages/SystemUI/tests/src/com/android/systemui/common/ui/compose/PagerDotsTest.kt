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

package com.android.systemui.common.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class PagerDotsTest : SysuiTestCase() {
    @get:Rule val composeRule = createComposeRule()

    @Composable
    private fun TestPagerDots(showArrows: Boolean, clickToCyclePages: Boolean, pageCount: Int) {
        Column(Modifier.fillMaxSize()) {
            val pagerState = rememberPagerState { pageCount }
            HorizontalPager(pagerState) { Text("$it") }
            PagerDots(
                pagerState,
                activeColor = Color.Red,
                nonActiveColor = Color.White,
                showArrows = showArrows,
                clickToCyclePages = clickToCyclePages,
            )
        }
    }

    @Test
    fun click_onPagerDots_cyclesThroughPages() {
        composeRule.setContent {
            TestPagerDots(clickToCyclePages = true, showArrows = false, pageCount = 3)
        }
        composeRule.waitForIdle()

        // Assert state description and click from initial page
        composeRule.onNode(hasStateDescription("Page 1 of 3")).performClick()

        // Click from second page
        composeRule.onNode(hasStateDescription("Page 2 of 3")).performClick()

        // Click from third page and assert we're back on page 1
        composeRule.onNode(hasStateDescription("Page 3 of 3")).performClick()
        composeRule.onNode(hasStateDescription("Page 1 of 3")).assertExists()
    }

    @Test
    fun click_onNavigationArrows_changesPage() {
        composeRule.setContent {
            TestPagerDots(clickToCyclePages = false, showArrows = true, pageCount = 2)
        }
        composeRule.waitForIdle()

        // Click on "next" arrow and assert it navigates to the new page
        composeRule.onNodeWithContentDescription("Next").performClick()
        composeRule.onNode(hasStateDescription("Page 2 of 2")).assertExists()

        // Click on "previous" arrow and assert it navigates to the previous page
        composeRule.onNodeWithContentDescription("Previous").performClick()
        composeRule.onNode(hasStateDescription("Page 1 of 2")).assertExists()
    }
}
