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

package com.android.systemui.inputmethod.ui.composable

import android.platform.test.annotations.EnableFlags
import android.view.inputmethod.Flags
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.inputmethod.ui.viewmodel.imeSwitcherMenuViewModel
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmosNew
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for [ImeSwitcherMenuContent]. */
@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(Flags.FLAG_IME_SWITCHER_MENU_SYSTEMUI)
class ImeSwitcherMenuContentTest : SysuiTestCase() {

    @get:Rule val composeTestRule = createComposeRule()

    private val kosmos = testKosmosNew()

    /** Verifies that the settings footer is shown when a settings action is provided. */
    @Test
    fun withSettingsAction_hasSettingsFooter() =
        kosmos.runTest {
            val viewModel = kosmos.imeSwitcherMenuViewModel
            viewModel.settingsButtonAction.value = {}

            composeTestRule.setContent {
                ImeSwitcherMenuContent(viewModelFactory = { viewModel }, dismissAction = {})
            }

            composeTestRule.onNodeWithTag("settings_footer").assertExists()
        }

    /** Verifies that the settings footer is not shown when no settings action is provided. */
    @Test
    fun withoutSettingsAction_noSettingsFooter() =
        kosmos.runTest {
            val viewModel = kosmos.imeSwitcherMenuViewModel
            viewModel.settingsButtonAction.value = null

            composeTestRule.setContent {
                ImeSwitcherMenuContent(viewModelFactory = { viewModel }, dismissAction = {})
            }

            composeTestRule.onNodeWithTag("settings_footer").assertDoesNotExist()
        }
}
