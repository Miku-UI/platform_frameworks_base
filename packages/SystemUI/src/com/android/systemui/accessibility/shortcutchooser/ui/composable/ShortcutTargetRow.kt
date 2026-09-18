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

package com.android.systemui.accessibility.shortcutchooser.ui.composable

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.systemui.accessibility.shortcutchooser.shared.model.AccessibilityTargetModel

private object Constants {
    // Desired switch width divided by the default switch width in material3.
    // See: https://m3.material.io/components/switch/specs for track width.
    // Hardcoding because the default width is not exposed by the library.
    const val ICON_SWITCH_SCALE = 40 / 52f
}

private enum class RowType {
    CHECKBOX,
    RADIO,
    TOGGLE,
    CLICK,
}

/**
 * A shortcut target row that uses a radio button to represent a single-select option.
 *
 * Must be used within a container with the `selectableGroup` modifier.
 */
@Composable
fun ShortcutSingleSelectRow(
    target: AccessibilityTargetModel,
    modifier: Modifier = Modifier,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ShortcutTargetRow(target, RowType.RADIO, modifier, selected, onClick)
}

/** A shortcut target row that uses a checkbox to represent a multi-selectable option. */
@Composable
fun ShortcutMultiSelectRow(
    target: AccessibilityTargetModel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    ShortcutTargetRow(target, RowType.CHECKBOX, modifier, target.isAssigned, onClick)
}

/**
 * A shortcut target row that uses a toggle switch to represent a toggleable option.
 *
 * If the target is not toggleable, the row will be just clickable without a toggle switch.
 */
@Composable
fun ShortcutToggleRow(
    target: AccessibilityTargetModel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    if (target.isToggleable) {
        ShortcutTargetRow(target, RowType.TOGGLE, modifier, target.isStateOn, onClick)
    } else {
        ShortcutTargetRow(target, RowType.CLICK, modifier, false, onClick)
    }
}

/**
 * Internal composable for rendering a row representing an accessibility shortcut target.
 *
 * @param controlState Determines the on/off state of the control. For [RowType.RADIO],
 *   [RowType.CHECKBOX], and [RowType.TOGGLE], this is the selected, checked, and toggled state,
 *   respectively. Not used for [RowType.CLICK].
 */
@Composable
private fun ShortcutTargetRow(
    target: AccessibilityTargetModel,
    rowType: RowType,
    modifier: Modifier = Modifier,
    controlState: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .testTag(target.targetName)
                // Add interactable before padding so the entire row is clickable.
                .interactable(target, controlState, rowType, onClick)
                .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rowType == RowType.CHECKBOX) {
            Checkbox(checked = controlState, onCheckedChange = null)
        } else if (rowType == RowType.RADIO) {
            RadioButton(selected = controlState, onClick = null)
        }

        Image(
            painter = rememberDrawablePainter(target.icon),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
        )

        Text(
            target.featureName,
            modifier = Modifier.weight(1f),
            style =
                MaterialTheme.typography.titleMedium.copy(
                    hyphens = Hyphens.Auto,
                    lineBreak =
                        LineBreak(
                            strategy = LineBreak.Strategy.HighQuality,
                            strictness = LineBreak.Strictness.Normal,
                            wordBreak = LineBreak.WordBreak.Phrase,
                        ),
                ),
        )

        if (rowType == RowType.TOGGLE) {
            PickerSwitch(checked = controlState)
        }
    }
}

private fun Modifier.interactable(
    target: AccessibilityTargetModel,
    controlState: Boolean,
    rowType: RowType,
    onClick: () -> Unit,
): Modifier =
    when (rowType) {
        RowType.RADIO ->
            selectable(selected = controlState, role = Role.RadioButton, onClick = { onClick() })
        RowType.CHECKBOX ->
            toggleable(value = controlState, role = Role.Checkbox, onValueChange = { onClick() })
        RowType.TOGGLE ->
            toggleable(value = controlState, role = Role.Switch, onValueChange = { onClick() })
        RowType.CLICK -> clickable { onClick() }
    }

@Composable
private fun PickerSwitch(checked: Boolean) {
    Switch(
        checked = checked,
        onCheckedChange = null,
        // Scaling instead of setting width because Switch is a composite composable so setting
        // the width is ignored.
        modifier = Modifier.scale(Constants.ICON_SWITCH_SCALE),
        thumbContent = {
            if (checked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            }
        },
    )
}
