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
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import com.android.systemui.common.shared.model.Icon as IconModel
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.res.R

private val ICON_SIZE = 20.dp

/**
 * Data class to represent a single radio button item. The item must have an [icon] or a [label] (or
 * both).
 */
data class RadioButtonGroupItem(
    val isSelected: Boolean,
    val icon: IconModel? = null,
    val label: String? = null,
    val contentDescription: String? = null,
    val stateDescription: String? = null,
    val hasTooltip: Boolean = false,
    val onClick: () -> Unit,
    val modifier: Modifier = Modifier,
) {
    /** Secondary constructor for cases where the icon is different when selected vs unselected. */
    constructor(
        label: String? = null,
        selectedIcon: IconModel? = null,
        unselectedIcon: IconModel? = null,
        isSelected: Boolean,
        onClick: () -> Unit,
        contentDescription: String? = null,
        stateDescription: String? = null,
        hasTooltip: Boolean = false,
        modifier: Modifier = Modifier,
    ) : this(
        label = label,
        icon = if (isSelected) selectedIcon else unselectedIcon,
        isSelected = isSelected,
        onClick = onClick,
        contentDescription = contentDescription,
        stateDescription = stateDescription,
        hasTooltip = hasTooltip,
        modifier = modifier,
    )
}

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
            val shapes =
                when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    items.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                }

            key(item.contentDescription) {
                val radioButton: @Composable () -> Unit = {
                    SelectableRadioButton(
                        item = item,
                        colors = colors,
                        shapes = shapes,
                        modifier = item.modifier,
                    )
                }

                if (item.hasTooltip && item.contentDescription != null) {
                    StyledTooltip(tooltipText = item.contentDescription) { radioButton() }
                } else {
                    radioButton()
                }
            }
        }
    }
}

@Composable
private fun SelectableRadioButton(
    item: RadioButtonGroupItem,
    colors: ToggleButtonColors,
    shapes: ToggleButtonShapes,
    modifier: Modifier = Modifier,
) {
    val actionLabel = stringResource(R.string.screen_capture_a11y_toolbar_radio_button_action)

    ToggleButton(
        colors = colors,
        shapes = shapes,
        checked = item.isSelected,
        onCheckedChange = { item.onClick() },
        modifier =
            modifier.semantics(mergeDescendants = true) {
                this.role = Role.RadioButton
                this.onClick(label = actionLabel, action = null)
                item.contentDescription
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { this.contentDescription = it }
                item.stateDescription
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { this.stateDescription = it }
            },
    ) {
        if (item.icon != null && item.label != null) {
            Icon(icon = item.icon, modifier = Modifier.size(ICON_SIZE))
            Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
            Text(item.label)
        } else if (item.icon != null) {
            Icon(icon = item.icon, modifier = Modifier.size(ICON_SIZE))
        } else if (item.label != null) {
            Text(item.label)
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
