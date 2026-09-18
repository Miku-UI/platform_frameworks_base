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

package com.android.systemui.inputmethod.ui.binder

import android.hardware.display.displayManager
import android.platform.test.annotations.RequiresFlagsEnabled
import android.view.Display
import android.view.inputmethod.Flags.FLAG_IME_SWITCHER_MENU_SYSTEMUI
import android.view.inputmethod.InputMethodManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.inputmethod.data.repository.fake
import com.android.systemui.inputmethod.data.repository.imeSwitcherMenuRepository
import com.android.systemui.inputmethod.shared.model.ImeSwitcherMenuModel
import com.android.systemui.inputmethod.ui.ImeSwitcherMenuUi
import com.android.systemui.inputmethod.ui.imeSwitcherMenuUiFactory
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmos
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever

/** Tests for [ImeSwitcherMenuBinder]. */
@SmallTest
@RequiresFlagsEnabled(FLAG_IME_SWITCHER_MENU_SYSTEMUI)
@RunWith(AndroidJUnit4::class)
class ImeSwitcherMenuBinderTest : SysuiTestCase() {

    private val mockDisplay: Display = mock()
    private val mockUi: ImeSwitcherMenuUi = mock()
    private val kosmos =
        testKosmos().apply {
            whenever(displayManager.getDisplay(any())).thenReturn(mockDisplay)
            whenever(imeSwitcherMenuUiFactory.create(any(), any(), any())).thenReturn(mockUi)
        }
    private val repository
        get() = kosmos.imeSwitcherMenuRepository.fake

    private val underTest
        get() = kosmos.imeSwitcherMenuBinder

    /** Verifies that when a model changes with a non-`null` value, the UI is shown. */
    @Test
    fun onModelChange_createsAndShowsUi() =
        kosmos.runTest {
            underTest.start()
            val userId = 10
            val model = createModel()

            repository.setModel(userId, model)
            verify(imeSwitcherMenuUiFactory).create(any(), any(), any())
            verify(mockUi).show()
        }

    /** Verifies that when a model changes with a `null` value, the UI is dismissed. */
    @Test
    fun onModelChange_nullModel_dismissesUi() =
        kosmos.runTest {
            underTest.start()
            val userId = 10
            val model = createModel()

            repository.setModel(userId, model)
            verify(imeSwitcherMenuUiFactory).create(any(), any(), any())
            verify(mockUi).show()

            repository.setModel(userId, model = null)
            verify(mockUi).dismiss()
        }

    /** Verifies that model changes on different users are handled independently. */
    @Test
    fun onModelChange_multipleUsers() =
        kosmos.runTest {
            underTest.start()
            val firstUser = 0
            val firstModel = createModel(selectedImeId = "firstId")

            val secondUser = 10
            val secondModel = createModel(selectedImeId = "secondId")

            repository.setModel(firstUser, firstModel)
            verify(imeSwitcherMenuUiFactory, times(1)).create(any(), any(), any())
            verify(mockUi, times(1)).show()

            repository.setModel(secondUser, secondModel)
            verify(imeSwitcherMenuUiFactory, times(2)).create(any(), any(), any())
            verify(mockUi, times(2)).show()
            // No dismiss calls.
            verify(mockUi, never()).dismiss()

            repository.setModel(firstUser, model = null)
            // No new create calls.
            verify(imeSwitcherMenuUiFactory, times(2)).create(any(), any(), any())
            // No new show calls.
            verify(mockUi, times(2)).show()
            verify(mockUi, times(1)).dismiss()

            repository.setModel(secondUser, model = null)
            // No new create calls.
            verify(imeSwitcherMenuUiFactory, times(2)).create(any(), any(), any())
            // No new show calls.
            verify(mockUi, times(2)).show()
            verify(mockUi, times(2)).dismiss()
        }

    /**
     * Verifies that when a model changes for the same user with the same display, the existing UI
     * is re-used.
     */
    @Test
    fun onModelChange_sameUserSameDisplay_reusesUi() =
        kosmos.runTest {
            underTest.start()
            val userId = 10
            val model = createModel()
            val otherModel = createModel(selectedImeId = "otherId")

            repository.setModel(userId, model)
            verify(imeSwitcherMenuUiFactory).create(any(), any(), any())
            verify(mockUi, times(1)).show()

            repository.setModel(userId, otherModel)
            // Just one call from the first show.
            verify(imeSwitcherMenuUiFactory, times(1)).create(any(), any(), any())
            // Shown again.
            verify(mockUi, times(2)).show()
        }

    /**
     * Verifies that when a model changes for the same user but with a different display, the UI is
     * re-created.
     */
    @Test
    fun onModelChange_sameUserDifferentDisplay_recreatesUi() =
        kosmos.runTest {
            underTest.start()
            val userId = 10
            val model = createModel()
            val otherModel = createModel(displayId = 3)

            repository.setModel(userId, model)
            verify(imeSwitcherMenuUiFactory).create(any(), any(), any())
            verify(mockUi, times(1)).show()

            repository.setModel(userId, otherModel)
            // Created again for new display.
            verify(imeSwitcherMenuUiFactory, times(2)).create(any(), any(), any())
            // Shown again.
            verify(mockUi, times(2)).show()
        }

    private fun createModel(selectedImeId: String? = "id", displayId: Int = 0) =
        ImeSwitcherMenuModel(
            items = emptyList(),
            selectedImeId,
            selectedSubtypeIndex = 0,
            selectedImeSettingsIntent = null,
            isScreenLocked = false,
            entryPoint = InputMethodManager.IM_PICKER_ENTRY_POINT_DEFAULT,
            displayId,
        )
}
