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

package com.android.systemui.keyboard.shortcut.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutCategoryType
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutCustomizationRequestInfo
import com.android.systemui.keyboard.shortcut.ui.model.ShortcutsUiState

@Composable
fun ShortcutHelperDualPane(
    onSearchQueryChanged: (String) -> Unit,
    selectedCategoryType: ShortcutCategoryType?,
    onCategorySelected: (ShortcutCategoryType?) -> Unit,
    onKeyboardSettingsClicked: () -> Unit,
    onCustomizationModeToggled: (isCustomizing: Boolean) -> Unit,
    uiState: ShortcutsUiState.Active,
    modifier: Modifier = Modifier,
    onShortcutCustomizationRequested: (ShortcutCustomizationRequestInfo) -> Unit = {},
) {
    val selectedCategory =
        uiState.shortcutCategories.fastFirstOrNull { it.type == selectedCategoryType }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Keep title centered whether customize button is visible or not.
            Spacer(modifier = Modifier.weight(1f))
            Box(modifier = Modifier.width(412.dp), contentAlignment = Alignment.Center) {
                TitleBar(uiState.isCustomizationModeEnabled)
            }
            CustomizationButtonsContainer(
                modifier = Modifier.weight(1f),
                isCustomizing = uiState.isCustomizationModeEnabled,
                onToggleCustomizationMode = {
                    onCustomizationModeToggled(!uiState.isCustomizationModeEnabled)
                },
                onReset = {
                    onShortcutCustomizationRequested(ShortcutCustomizationRequestInfo.Reset)
                },
                shouldShowResetButton = uiState.shouldShowResetButton,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            StartSidePanel(
                onSearchQueryChanged = onSearchQueryChanged,
                modifier = Modifier.width(240.dp).semantics { isTraversalGroup = true },
                categories = uiState.shortcutCategories,
                onKeyboardSettingsClicked = onKeyboardSettingsClicked,
                selectedCategory = selectedCategoryType,
                onCategoryClicked = { onCategorySelected(it.type) },
            )
            Spacer(modifier = Modifier.width(24.dp))
            EndSidePanel(
                uiState,
                onCustomizationModeToggled,
                selectedCategory,
                Modifier.fillMaxSize().padding(top = 8.dp).semantics { isTraversalGroup = true },
                onShortcutCustomizationRequested,
            )
        }
    }
}

@Composable
private fun CustomizationButtonsContainer(
    isCustomizing: Boolean,
    shouldShowResetButton: Boolean,
    onToggleCustomizationMode: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.End) {
        if (isCustomizing) {
            if (shouldShowResetButton) {
                ResetButton(onClick = onReset)
                Spacer(Modifier.width(8.dp))
            }
            DoneButton(onClick = onToggleCustomizationMode)
        } else {
            CustomizeButton(onClick = onToggleCustomizationMode)
        }
    }
}
