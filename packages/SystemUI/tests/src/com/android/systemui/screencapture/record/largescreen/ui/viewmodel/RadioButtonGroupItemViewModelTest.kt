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

package com.android.systemui.screencapture.record.largescreen.ui.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.Icon
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class RadioButtonGroupItemViewModelTest : SysuiTestCase() {
    private val iconA = Icon.Resource(1, null)
    private val iconB = Icon.Resource(2, null)

    @Test
    fun primaryConstructor_nullIcons_iconGettersReturnNull() {
        val viewModel =
            RadioButtonGroupItemViewModel(
                label = null,
                unselectedIcon = null,
                selectedIcon = null,
                isSelected = true,
                onClick = {},
            )
        assertThat(viewModel.icon).isEqualTo(null)
        assertThat(viewModel.unselectedIcon).isEqualTo(null)
        assertThat(viewModel.selectedIcon).isEqualTo(null)
    }

    @Test
    fun primaryConstructor_isSelected_iconGetterReturnsSelectedIcon() {
        val unselectedIcon: Icon = iconA
        val selectedIcon: Icon = iconB
        val viewModel =
            RadioButtonGroupItemViewModel(
                label = null,
                unselectedIcon = unselectedIcon,
                selectedIcon = selectedIcon,
                isSelected = true,
                onClick = {},
            )
        assertThat(viewModel.icon).isEqualTo(selectedIcon)
        assertThat(viewModel.icon).isNotEqualTo(unselectedIcon)
    }

    @Test
    fun primaryConstructor_isNotSelected_iconGetterReturnsUnselectedIcon() {
        val unselectedIcon: Icon = iconA
        val selectedIcon: Icon = iconB
        val viewModel =
            RadioButtonGroupItemViewModel(
                label = null,
                unselectedIcon = unselectedIcon,
                selectedIcon = selectedIcon,
                isSelected = false,
                onClick = {},
            )
        assertThat(viewModel.icon).isEqualTo(unselectedIcon)
        assertThat(viewModel.icon).isNotEqualTo(selectedIcon)
    }

    @Test
    fun secondaryConstructor_isSelected_iconGetterReturnsSameIcon() {
        val viewModel =
            RadioButtonGroupItemViewModel(
                label = null,
                icon = iconA,
                isSelected = true,
                onClick = {},
            )
        assertThat(viewModel.icon).isEqualTo(iconA)
        assertThat(viewModel.unselectedIcon).isEqualTo(iconA)
        assertThat(viewModel.selectedIcon).isEqualTo(iconA)
    }

    @Test
    fun secondaryConstructor_isNotSelected_iconGetterReturnsSameIcon() {
        val viewModel =
            RadioButtonGroupItemViewModel(
                label = null,
                icon = iconA,
                isSelected = false,
                onClick = {},
            )
        assertThat(viewModel.icon).isEqualTo(iconA)
        assertThat(viewModel.unselectedIcon).isEqualTo(iconA)
        assertThat(viewModel.selectedIcon).isEqualTo(iconA)
    }

    @Test
    fun primaryConstructor_onlySelectedIconProvided_throwsIllegalArgumentException() {
        // Both selectedIcon and unselectedIcon should be provided.
        val exception =
            assertFailsWith<IllegalArgumentException> {
                RadioButtonGroupItemViewModel(
                    selectedIcon = iconA,
                    unselectedIcon = null,
                    isSelected = true,
                    onClick = {},
                )
            }
        assertThat(exception)
            .hasMessageThat()
            .contains("selectedIcon and unselectedIcon must both be provided or both be null.")
    }

    @Test
    fun primaryConstructor_onlyUnselectedIconProvided_throwsIllegalArgumentException() {
        // Both selectedIcon and unselectedIcon should be provided.
        val exception =
            assertFailsWith<IllegalArgumentException> {
                RadioButtonGroupItemViewModel(
                    selectedIcon = null,
                    unselectedIcon = iconA,
                    isSelected = true,
                    onClick = {},
                )
            }
        assertThat(exception)
            .hasMessageThat()
            .contains("selectedIcon and unselectedIcon must both be provided or both be null.")
    }
}
