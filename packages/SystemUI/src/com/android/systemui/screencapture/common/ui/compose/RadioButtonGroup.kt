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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.android.systemui.screencapture.common.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import com.android.systemui.common.shared.model.Icon as IconModel
import com.android.systemui.common.ui.compose.Icon

private val ICON_SIZE = 20.dp

/**
 * Data class to represent a single radio button item. The item must have an [icon] or a [label] (or
 * both).
 */
data class RadioButtonGroupItem(
    val isSelected: Boolean,
    val onClick: () -> Unit,
    val icon: IconModel? = null,
    val label: String? = null,
    val contentDescription: String? = null,
)

/** A group of N icon buttons where any single icon button is selected at a time. */
@Composable
fun RadioButtonGroup(
    items: List<RadioButtonGroupItem>,
    modifier: Modifier = Modifier,
    colors: ToggleButtonColors = defaultColors(),
) {
    require(items.count { it.isSelected } == 1) { "Only one button item must be selected." }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        items.fastForEachIndexed { index, item ->
            ToggleButton(
                colors = colors,
                shapes =
                    when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        items.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                checked = item.isSelected,
                onCheckedChange = { item.onClick() },
                modifier =
                    Modifier.semantics {
                        this.contentDescription = item.contentDescription ?: item.label ?: ""
                    },
            ) {
                if (item.icon != null && item.label != null) {
                    Icon(icon = item.icon, modifier = Modifier.size(ICON_SIZE))
                    Spacer(Modifier.size(8.dp))
                    Text(item.label)
                } else if (item.icon != null) {
                    Icon(icon = item.icon, modifier = Modifier.size(ICON_SIZE))
                } else if (item.label != null) {
                    Text(item.label)
                }
            }
        }
    }
}

@Composable
private fun defaultColors(): ToggleButtonColors {
    return ToggleButtonDefaults.toggleButtonColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        checkedContainerColor = MaterialTheme.colorScheme.secondary,
        checkedContentColor = MaterialTheme.colorScheme.onSecondary,
    )
}
