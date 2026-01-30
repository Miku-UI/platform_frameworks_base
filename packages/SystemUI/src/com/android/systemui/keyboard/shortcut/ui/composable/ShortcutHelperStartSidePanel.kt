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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItemColors
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutCategoryType
import com.android.systemui.keyboard.shortcut.ui.model.IconSource
import com.android.systemui.keyboard.shortcut.ui.model.ShortcutCategoryUi

@Composable
fun StartSidePanel(
    onSearchQueryChanged: (String) -> Unit,
    modifier: Modifier,
    categories: List<ShortcutCategoryUi>,
    onKeyboardSettingsClicked: () -> Unit,
    selectedCategory: ShortcutCategoryType?,
    onCategoryClicked: (ShortcutCategoryUi) -> Unit,
) {
    CompositionLocalProvider(
        // Restrict system font scale increases up to a max so categories display correctly.
        LocalDensity provides
            Density(
                density = LocalDensity.current.density,
                fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.5f),
            )
    ) {
        Column(modifier) {
            ShortcutsSearchBar(onSearchQueryChanged)
            Spacer(modifier = Modifier.heightIn(8.dp))
            CategoriesPanelTwoPane(categories, selectedCategory, onCategoryClicked)
            Spacer(modifier = Modifier.weight(1f))
            KeyboardSettings(onKeyboardSettingsClicked)
        }
    }
}

@Composable
private fun CategoriesPanelTwoPane(
    categories: List<ShortcutCategoryUi>,
    selectedCategory: ShortcutCategoryType?,
    onCategoryClicked: (ShortcutCategoryUi) -> Unit,
) {
    Column {
        categories.fastForEach {
            CategoryItemTwoPane(
                label = it.label,
                iconSource = it.iconSource,
                selected = selectedCategory == it.type,
                onClick = { onCategoryClicked(it) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CategoryItemTwoPane(
    label: String,
    iconSource: IconSource,
    selected: Boolean,
    onClick: () -> Unit,
    colors: NavigationDrawerItemColors =
        NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
) {
    SelectableShortcutSurface(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.semantics { role = Role.Tab }.heightIn(min = 64.dp).fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = colors.containerColor(selected).value,
        interactionsConfig =
            InteractionsConfig(
                hoverOverlayColor = MaterialTheme.colorScheme.onSurface,
                hoverOverlayAlpha = 0.11f,
                pressedOverlayColor = MaterialTheme.colorScheme.onSurface,
                pressedOverlayAlpha = 0.15f,
                focusOutlineColor = MaterialTheme.colorScheme.secondary,
                focusOutlineStrokeWidth = 3.dp,
                focusOutlinePadding = 2.dp,
                surfaceCornerRadius = 28.dp,
                focusOutlineCornerRadius = 33.dp,
            ),
    ) {
        Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            ShortcutCategoryIcon(
                modifier = Modifier.size(24.dp),
                source = iconSource,
                contentDescription = null,
                tint = colors.iconColor(selected).value,
            )
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                Text(
                    fontSize = 18.sp,
                    color = colors.textColor(selected).value,
                    style =
                        if (selected) {
                            MaterialTheme.typography.titleMediumEmphasized.copy(
                                hyphens = Hyphens.Auto
                            )
                        } else {
                            MaterialTheme.typography.titleMedium.copy(hyphens = Hyphens.Auto)
                        },
                    text = label,
                )
            }
        }
    }
}
