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
import androidx.annotation.DrawableRes
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.asIcon
import com.android.systemui.inputmethod.ui.viewmodel.ImeSwitcherMenuViewModel
import com.android.systemui.inputmethod.ui.viewmodel.imeSwitcherMenuViewModel
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmos
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for [ImeSwitcherMenuList]. */
@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(Flags.FLAG_IME_SWITCHER_MENU_SYSTEMUI)
class ImeSwitcherMenuListTest : SysuiTestCase() {

    @get:Rule val composeTestRule = createComposeRule()

    private val kosmos = testKosmos()

    /** Verifies that IMEs with no subtypes are correctly shown. */
    @Test
    fun menuItems_imeWithNoSubtypes() =
        kosmos.runTest {
            val viewModel = kosmos.imeSwitcherMenuViewModel
            viewModel.menuItems.addAll(
                listOf(menuItem("ImeNameA", "ImeIdA"), menuItem("ImeNameB", "ImeIdB"))
            )
            viewModel.selectedIndex.intValue = 1

            composeTestRule.setContent {
                ImeSwitcherMenuList(
                    items = viewModel.menuItems,
                    viewModel = viewModel,
                    dismissAction = {},
                    useLargeScreenLayout = false,
                )
            }

            composeTestRule.onNodeWithText("ImeNameA").assertExists().assertIsNotSelected()
            composeTestRule.onNodeWithText("ImeNameB").assertExists().assertIsSelected()
        }

    /** Verifies that IMEs with subtypes are correctly shown. */
    @Test
    fun menuItems_imeWithSubtypes() =
        kosmos.runTest {
            val viewModel = kosmos.imeSwitcherMenuViewModel
            viewModel.menuItems.addAll(
                listOf(
                    menuItem("ImeNameA", "ImeIdA"),
                    menuItem("ImeNameB", "ImeIdB", subtypeName = "English", subtypeIndex = 0),
                    menuItem("ImeNameB", "ImeIdB", subtypeName = "French", subtypeIndex = 1),
                )
            )
            viewModel.selectedIndex.intValue = 2

            composeTestRule.setContent {
                ImeSwitcherMenuList(
                    items = viewModel.menuItems,
                    viewModel = viewModel,
                    dismissAction = {},
                    useLargeScreenLayout = false,
                )
            }

            composeTestRule.onNodeWithText("ImeNameA").assertExists().assertIsNotSelected()
            composeTestRule.onNodeWithText("English").assertExists().assertIsNotSelected()
            composeTestRule.onNodeWithText("French").assertExists().assertIsSelected()
        }

    /** Verifies that the subtype icon is shown on the large screen layout. */
    @Test
    fun largeScreenLayout_showsIcon() =
        kosmos.runTest {
            val viewModel = kosmos.imeSwitcherMenuViewModel
            viewModel.menuItems.addAll(
                listOf(
                    menuItem(
                        "ImeName",
                        "ImeId",
                        imePackageName = context.packageName,
                        subtypeName = "English",
                        subtypeShortLabel = "EN",
                        subtypeIconResId = android.R.drawable.ic_menu_add,
                        subtypeIndex = 0,
                    )
                )
            )
            viewModel.selectedIndex.intValue = 0

            composeTestRule.setContent {
                ImeSwitcherMenuList(
                    items = viewModel.menuItems,
                    viewModel = viewModel,
                    dismissAction = {},
                    useLargeScreenLayout = true,
                )
            }

            composeTestRule.onNodeWithText("English").assertExists().assertIsSelected()
            composeTestRule.onNodeWithTag("SubtypeIcon", useUnmergedTree = true).assertExists()
            composeTestRule.onNodeWithText("EN").assertDoesNotExist()
        }

    /**
     * Verifies that the subtype short label is shown on the large screen layout if there is no
     * icon.
     */
    @Test
    fun largeScreenLayout_showsShortLabel() =
        kosmos.runTest {
            val viewModel = kosmos.imeSwitcherMenuViewModel
            viewModel.menuItems.addAll(
                listOf(
                    menuItem(
                        "ImeName",
                        "ImeId",
                        imePackageName = context.packageName,
                        subtypeName = "English",
                        subtypeShortLabel = "EN",
                        subtypeIconResId = 0,
                        subtypeIndex = 0,
                    )
                )
            )
            viewModel.selectedIndex.intValue = 0

            composeTestRule.setContent {
                ImeSwitcherMenuList(
                    items = viewModel.menuItems,
                    viewModel = viewModel,
                    dismissAction = {},
                    useLargeScreenLayout = true,
                )
            }

            composeTestRule.onNodeWithText("English").assertExists().assertIsSelected()
            composeTestRule
                .onNodeWithTag("SubtypeIcon", useUnmergedTree = true)
                .assertDoesNotExist()
            composeTestRule.onNodeWithText("EN").assertExists()
        }

    /** Verifies that the subtype icon and short label are not shown on the standard layout. */
    @Test
    fun standardLayout_doesNotShowIcon() =
        kosmos.runTest {
            val viewModel = kosmos.imeSwitcherMenuViewModel
            viewModel.menuItems.addAll(
                listOf(
                    menuItem(
                        "ImeName",
                        "ImeId",
                        imePackageName = context.packageName,
                        subtypeName = "English",
                        subtypeShortLabel = "EN",
                        subtypeIconResId = android.R.drawable.ic_menu_add,
                        subtypeIndex = 0,
                    )
                )
            )
            viewModel.selectedIndex.intValue = 0

            composeTestRule.setContent {
                ImeSwitcherMenuList(
                    items = viewModel.menuItems,
                    viewModel = viewModel,
                    dismissAction = {},
                    useLargeScreenLayout = false,
                )
            }

            composeTestRule.onNodeWithText("English").assertExists().assertIsSelected()
            composeTestRule
                .onNodeWithTag("SubtypeIcon", useUnmergedTree = true)
                .assertDoesNotExist()
            composeTestRule.onNodeWithText("EN").assertDoesNotExist()
        }

    private fun menuItem(
        name: String,
        id: String,
        imePackageName: String? = null,
        subtypeName: String? = null,
        subtypeShortLabel: String? = null,
        @DrawableRes subtypeIconResId: Int = 0,
        subtypeIndex: Int = -1,
    ): ImeSwitcherMenuViewModel.MenuItem {
        val icon =
            if (subtypeIconResId != 0 && imePackageName != null) {
                android.graphics.drawable.Icon.createWithResource(imePackageName, subtypeIconResId)
                    .loadDrawable(context)
                    ?.asIcon()
            } else {
                null
            }
        return ImeSwitcherMenuViewModel.MenuItem(
            imeName = name,
            subtypeName = subtypeName,
            subtypeShortLabel = subtypeShortLabel,
            subtypeIcon = icon,
            layoutName = null,
            imeId = id,
            subtypeIndex = subtypeIndex,
            hasDivider = false,
            hasHeader = false,
        )
    }
}
