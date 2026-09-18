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

package com.android.systemui.inputmethod.ui.viewmodel

import android.content.Intent
import android.platform.test.annotations.RequiresFlagsEnabled
import android.view.inputmethod.Flags.FLAG_IME_SWITCHER_MENU_SYSTEMUI
import android.view.inputmethod.InputMethodManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.inputmethod.IImeSwitcherMenu
import com.android.systemui.SysuiTestCase
import com.android.systemui.inputmethod.data.repository.FakeImeSwitcherMenuRepository
import com.android.systemui.inputmethod.data.repository.fake
import com.android.systemui.inputmethod.data.repository.imeSwitcherMenuRepository
import com.android.systemui.inputmethod.shared.model.ImeSwitcherMenuModel
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for [ImeSwitcherMenuViewModel]. */
@SmallTest
@RequiresFlagsEnabled(FLAG_IME_SWITCHER_MENU_SYSTEMUI)
@RunWith(AndroidJUnit4::class)
class ImeSwitcherMenuViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos()

    private val repository: FakeImeSwitcherMenuRepository
        get() = kosmos.imeSwitcherMenuRepository.fake

    private val underTest
        get() = kosmos.imeSwitcherMenuViewModel

    /** Verifies the divider and header logic for a given list of items. */
    @Test
    fun updateState_dividersAndHeaders() =
        kosmos.runTest {
            val items =
                listOf(
                    createItem("imeA", "ImeA", subtypeName = "English", subtypeIndex = 0),
                    createItem("imeA", "ImeA", subtypeName = "French", subtypeIndex = 1),
                    createItem("imeB", "ImeB", subtypeIndex = 0),
                    createItem("imeC", "ImeC", subtypeName = "Italian", subtypeIndex = 0),
                )
            val model = createModel(items)

            underTest.updateState(model)

            val menuItems = underTest.menuItems
            assertThat(menuItems).hasSize(4)

            // Header (first in group), no Divider (first group)
            assertThat(menuItems[0].hasHeader).isTrue()
            assertThat(menuItems[0].hasDivider).isFalse()

            // No Header (not first in group), no Divider
            assertThat(menuItems[1].hasHeader).isFalse()
            assertThat(menuItems[1].hasDivider).isFalse()

            // No header (first in group, but single item with no subtype name),
            // Divider (not first group)
            assertThat(menuItems[2].hasHeader).isFalse()
            assertThat(menuItems[2].hasDivider).isTrue()

            // Header (first in group, single item with subtype name, Divider (not first group)
            assertThat(menuItems[3].hasHeader).isTrue()
            assertThat(menuItems[3].hasDivider).isTrue()
        }

    /** Verifies that a list of items that only has one group has no header. */
    @Test
    fun updateState_singleGroup() =
        kosmos.runTest {
            val items =
                listOf(
                    createItem("imeA", "ImeA", subtypeName = "English", subtypeIndex = 0),
                    createItem("imeA", "ImeA", subtypeName = "French", subtypeIndex = 1),
                )
            val model = createModel(items)

            underTest.updateState(model)

            val menuItems = underTest.menuItems
            assertThat(menuItems).hasSize(2)

            // No Header (first in group, but single group), no Divider (first group)
            assertThat(menuItems[0].hasHeader).isFalse()
            assertThat(menuItems[0].hasDivider).isFalse()

            // No Header (not first in group), no Divider
            assertThat(menuItems[1].hasHeader).isFalse()
            assertThat(menuItems[1].hasDivider).isFalse()
        }

    /** Verifies the selected index when a specific subtype index is selected. */
    @Test
    fun updateState_explicitSelection() =
        kosmos.runTest {
            val items =
                listOf(
                    createItem("imeA", "ImeA", subtypeName = "English", subtypeIndex = 0),
                    createItem("imeA", "ImeA", subtypeName = "French", subtypeIndex = 1),
                )
            val model = createModel(items, selectedImeId = "imeA", selectedSubtypeIndex = 1)

            underTest.updateState(model)

            assertThat(underTest.selectedIndex.intValue).isEqualTo(1)
        }

    /**
     * Verifies that the first item of an IME is selected when the selected subtype index is `-1`.
     */
    @Test
    fun updateState_implicitSelection() =
        kosmos.runTest {
            val items =
                listOf(
                    createItem("imeA", imeName = "ImeA", subtypeName = "English", subtypeIndex = 0),
                    createItem("imeB", imeName = "ImeB", subtypeName = "Emoji", subtypeIndex = 0),
                )
            // IME A is selected, but index -1 (NOT_A_SUBTYPE_INDEX)
            val model = createModel(items, selectedImeId = "imeA", selectedSubtypeIndex = -1)

            underTest.updateState(model)

            // Fall back to the first item of IME A
            assertThat(underTest.selectedIndex.intValue).isEqualTo(0)
        }

    /**
     * Verifies that onImeAndSubtypeSelected calls into the interactor (and in turn the repository).
     */
    @Test
    fun onSelection_callsInteractor() =
        kosmos.runTest {
            underTest.updateState(createModel(listOf()))

            underTest.onImeAndSubtypeSelected("newIme", 2)

            val calls = repository.imeAndSubtypeSelectedCalls
            assertThat(calls).hasSize(1)
            assertThat(calls[0].first).isEqualTo("newIme")
            assertThat(calls[0].second).isEqualTo(2)
        }

    /** Verifies that onVisibilityChanged calls into the interactor (and in turn the repository). */
    @Test
    fun onVisibilityChanged_callsInteractor() =
        kosmos.runTest {
            underTest.onVisibilityChanged(true)

            val calls = repository.visibilityChangedCalls
            assertThat(calls).hasSize(1)
            assertThat(calls[0].first).isEqualTo(true)
        }

    private fun createItem(
        imeId: String,
        imeName: String,
        subtypeName: String? = null,
        subtypeIndex: Int,
    ): IImeSwitcherMenu.Item {
        val item = IImeSwitcherMenu.Item()
        item.imeName = imeName
        item.subtypeName = subtypeName
        item.layoutName = null
        item.imeId = imeId
        item.subtypeIndex = subtypeIndex
        return item
    }

    private fun createModel(
        items: List<IImeSwitcherMenu.Item>,
        selectedImeId: String? = null,
        selectedSubtypeIndex: Int = 0,
        selectedSettingsIntent: Intent? = null,
        isScreenLocked: Boolean = false,
    ) =
        ImeSwitcherMenuModel(
            items,
            selectedImeId,
            selectedSubtypeIndex,
            selectedSettingsIntent,
            isScreenLocked,
            entryPoint = InputMethodManager.IM_PICKER_ENTRY_POINT_DEFAULT,
            displayId = 0,
        )
}
