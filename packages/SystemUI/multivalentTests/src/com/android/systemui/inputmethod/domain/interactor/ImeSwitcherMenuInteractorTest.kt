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

package com.android.systemui.inputmethod.domain.interactor

import android.platform.test.annotations.RequiresFlagsEnabled
import android.view.inputmethod.Flags.FLAG_IME_SWITCHER_MENU_SYSTEMUI
import android.view.inputmethod.InputMethodManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.inputmethod.data.repository.fake
import com.android.systemui.inputmethod.data.repository.imeSwitcherMenuRepository
import com.android.systemui.inputmethod.shared.model.ImeSwitcherMenuModel
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for [ImeSwitcherMenuInteractor]. */
@SmallTest
@RequiresFlagsEnabled(FLAG_IME_SWITCHER_MENU_SYSTEMUI)
@RunWith(AndroidJUnit4::class)
class ImeSwitcherMenuInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val repository
        get() = kosmos.imeSwitcherMenuRepository.fake

    private val underTest
        get() = kosmos.imeSwitcherMenuInteractor

    /** Verifies that onVisibilityChange calls are forwarded to the repository. */
    @Test
    fun onVisibilityChanged_forwardsToRepo() =
        kosmos.runTest {
            val visible = true
            val displayId = 1
            val userId = 10

            underTest.onVisibilityChanged(visible, displayId, userId)

            assertThat(repository.visibilityChangedCalls)
                .containsExactly(Triple(visible, displayId, userId))
        }

    /** Verifies that onImeAndSubtypeSelected calls are forwarded to the repository. */
    @Test
    fun onImeAndSubtypeSelected_forwardsToRepo() =
        kosmos.runTest {
            val imeId = "testIme"
            val subtypeIndex = 1
            val userId = 2

            underTest.onImeAndSubtypeSelected(imeId, subtypeIndex, userId)

            assertThat(repository.imeAndSubtypeSelectedCalls)
                .containsExactly(Triple(imeId, subtypeIndex, userId))
        }

    /** Verifies that getModel retrieves the current model from the repository. */
    @Test
    fun getModel_getsFromRepo() =
        kosmos.runTest {
            val userId = 10
            val expectedModel =
                ImeSwitcherMenuModel(
                    items = emptyList(),
                    selectedImeId = "id",
                    selectedSubtypeIndex = 0,
                    selectedImeSettingsIntent = null,
                    isScreenLocked = false,
                    entryPoint = InputMethodManager.IM_PICKER_ENTRY_POINT_DEFAULT,
                    displayId = 5,
                )

            val initialModel = underTest.getModel(userId)
            assertNull(initialModel)

            repository.setModel(userId, expectedModel)
            val model = underTest.getModel(userId)
            assertThat(model).isEqualTo(expectedModel)

            val otherUserModel = underTest.getModel(3)
            assertNull(otherUserModel)
        }
}
