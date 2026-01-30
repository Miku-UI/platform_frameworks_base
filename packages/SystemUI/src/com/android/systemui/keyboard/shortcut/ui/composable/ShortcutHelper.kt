/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.keyboard.shortcut.ui.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutCustomizationRequestInfo
import com.android.systemui.keyboard.shortcut.ui.model.ShortcutsUiState

@Composable
fun ShortcutHelper(
    onSearchQueryChanged: (String) -> Unit,
    onKeyboardSettingsClicked: () -> Unit,
    modifier: Modifier = Modifier,
    shortcutsUiState: ShortcutsUiState,
    useSinglePane: @Composable () -> Boolean = { shouldUseSinglePane() },
    onShortcutCustomizationRequested: (ShortcutCustomizationRequestInfo) -> Unit = {},
    onCustomizationModeToggled: (Boolean) -> Unit = {},
) {
    when (shortcutsUiState) {
        is ShortcutsUiState.Active -> {
            ActiveShortcutHelper(
                shortcutsUiState,
                useSinglePane,
                onSearchQueryChanged,
                onCustomizationModeToggled,
                modifier,
                onKeyboardSettingsClicked,
                onShortcutCustomizationRequested,
            )
        }

        else -> {
            // No-op for now.
        }
    }
}

@Composable
private fun ActiveShortcutHelper(
    shortcutsUiState: ShortcutsUiState.Active,
    useSinglePane: @Composable () -> Boolean,
    onSearchQueryChanged: (String) -> Unit,
    onCustomizationModeToggled: (Boolean) -> Unit,
    modifier: Modifier,
    onKeyboardSettingsClicked: () -> Unit,
    onShortcutCustomizationRequested: (ShortcutCustomizationRequestInfo) -> Unit = {},
) {
    var selectedCategoryType by
        remember(shortcutsUiState.defaultSelectedCategory) {
            mutableStateOf(shortcutsUiState.defaultSelectedCategory)
        }
    if (useSinglePane()) {
        ShortcutHelperSinglePane(
            shortcutsUiState,
            onSearchQueryChanged,
            selectedCategoryType,
            onCategorySelected = { selectedCategoryType = it },
            onKeyboardSettingsClicked,
            modifier,
        )
    } else {
        ShortcutHelperDualPane(
            onSearchQueryChanged,
            selectedCategoryType,
            onCategorySelected = { selectedCategoryType = it },
            onKeyboardSettingsClicked,
            onCustomizationModeToggled,
            shortcutsUiState,
            modifier,
            onShortcutCustomizationRequested,
        )
    }
}

@Composable private fun shouldUseSinglePane() = hasCompactWindowSize()
