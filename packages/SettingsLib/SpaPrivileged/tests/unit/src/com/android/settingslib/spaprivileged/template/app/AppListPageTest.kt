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

package com.android.settingslib.spaprivileged.template.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.settingslib.spaprivileged.R
import com.android.settingslib.spaprivileged.tests.testutils.TestAppListModel
import com.android.settingslib.spaprivileged.tests.testutils.TestAppRecord
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppListPageTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun title_isDisplayed() {
        setContent()

        composeTestRule.onNodeWithText(TITLE).assertIsDisplayed()
    }

    @Test
    fun appListState_hideSystemAppsInitially_hasCorrectInitialState() {
        val inputState by setContent(showSystemAppsInitially = false)

        val state = inputState!!.state
        assertThat(state.showSystem()).isFalse()
        assertThat(state.searchQuery()).isEqualTo("")
    }

    @Test
    fun appListState_showSystemAppsInitially_hasCorrectInitialState() {
        val inputState by setContent(showSystemAppsInitially = true)

        val state = inputState!!.state
        assertThat(state.showSystem()).isTrue()
        assertThat(state.searchQuery()).isEqualTo("")
    }

    @Test
    fun hideSystemAppsInitially_canShowSystem() {
        val inputState by setContent(showSystemAppsInitially = false)

        onMoreOptions().performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.menu_show_system)).performClick()

        val state = inputState!!.state
        assertThat(state.showSystem()).isTrue()
    }

    @Test
    fun showSystemAppsInitially_canHideSystem() {
        val inputState by setContent(showSystemAppsInitially = true)

        onMoreOptions().performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.menu_hide_system)).performClick()

        val state = inputState!!.state
        assertThat(state.showSystem()).isFalse()
    }

    @Test
    fun hideSystemAppsInitially_afterShowSystem_displayHideSystem() {
        setContent(showSystemAppsInitially = false)
        onMoreOptions().performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.menu_show_system)).performClick()

        onMoreOptions().performClick()

        composeTestRule.onNodeWithText(context.getString(R.string.menu_hide_system))
            .assertIsDisplayed()
    }

    @Test
    fun showSystemAppsInitially_afterHideSystem_displayShowSystem() {
        setContent(showSystemAppsInitially = true)
        onMoreOptions().performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.menu_hide_system)).performClick()

        onMoreOptions().performClick()

        composeTestRule.onNodeWithText(context.getString(R.string.menu_show_system))
            .assertIsDisplayed()
    }

    @Test
    fun noMoreOptions_notDisplayMoreOptions() {
        setContent(noMoreOptions = true)

        onMoreOptions().assertDoesNotExist()
    }

    @Test
    fun noMoreOptions_showSystemIsFalse() {
        val inputState by setContent(noMoreOptions = true)

        val state = inputState!!.state
        assertThat(state.showSystem()).isFalse()
    }

    private fun setContent(
        showSystemAppsInitially: Boolean = false,
        noMoreOptions: Boolean = false,
        header: @Composable () -> Unit = {},
    ): State<AppListInput<TestAppRecord>?> {
        val appListState = mutableStateOf<AppListInput<TestAppRecord>?>(null)
        composeTestRule.setContent {
            AppListPage(
                title = TITLE,
                listModel = TestAppListModel(),
                showSystemAppsInitially = showSystemAppsInitially,
                noMoreOptions = noMoreOptions,
                header = header,
                appList = { appListState.value = this },
            )
        }
        return appListState
    }

    private fun onMoreOptions() =
        composeTestRule.onNodeWithContentDescription(
            context.getString(androidx.appcompat.R.string.abc_action_menu_overflow_description)
        )

    private companion object {
        const val TITLE = "Title"
    }
}
