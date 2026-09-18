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

package com.android.systemui.notifications.intelligence.rules.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.systemui.notifications.intelligence.rules.shared.model.ActionModel
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.RulesScreenViewState
import com.android.systemui.res.R

/** Renders the action on [viewModel.rule] as a clickable item. */
@Composable
fun EditableAction(
    action: ActionModel,
    onEnterEditField: (RulesScreenViewState.EditField) -> Unit,
    onActionSaved: (ActionModel) -> Unit,
) {
    Action(
        action,
        onClick = {
            onEnterEditField(
                RulesScreenViewState.EditField.Action(
                    selectedAction = action,
                    onActionSaved = onActionSaved,
                )
            )
        },
    )
}

/** Renders the given action as a non-clickable item. */
@Composable
fun ReadOnlyAction(action: ActionModel) {
    Action(action, onClick = null)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Action(action: ActionModel, onClick: (() -> Unit)?) {
    val actionColor = action.toColor()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            painter = action.toIconPainter(),
            contentDescription = null,
            tint = Color.Black.copy(alpha = 0.8f),
            modifier =
                Modifier.size(32.dp)
                    .background(actionColor, MaterialTheme.shapes.medium)
                    .padding(4.dp),
        )
        Text(
            text = action.toText(),
            color = actionColor,
            style = MaterialTheme.typography.titleMediumEmphasized,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        onClick?.let {
            Box(
                modifier =
                    Modifier.minimumInteractiveComponentSize()
                        .clickable(
                            enabled = true,
                            onClickLabel =
                                stringResource(R.string.notification_rules_modify_action),
                            onClick = it,
                        )
            ) {
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = actionColor,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
fun ActionModel.toText(): String {
    val resourceId =
        when (this) {
            ActionModel.HighlightAndAlert -> R.string.notification_rules_action_highlight_and_alert
            ActionModel.Highlight -> R.string.notification_rules_action_highlight
            ActionModel.Silence -> R.string.notification_rules_action_silence
            is ActionModel.Bundle -> R.string.notification_rules_action_bundle
            ActionModel.Block -> R.string.notification_rules_action_block
        }
    return stringResource(resourceId)
}

@Composable
fun ActionModel.toDescription(): String {
    val resourceId =
        when (this) {
            ActionModel.HighlightAndAlert ->
                R.string.notification_rules_action_highlight_and_alert_description
            ActionModel.Highlight -> R.string.notification_rules_action_highlight_description
            ActionModel.Silence -> R.string.notification_rules_action_silence_description
            is ActionModel.Bundle -> R.string.notification_rules_action_bundle_description
            ActionModel.Block -> R.string.notification_rules_action_block_description
        }
    return stringResource(resourceId)
}

@Composable
fun ActionModel.toIconPainter(): Painter {
    val resourceId =
        when (this) {
            ActionModel.HighlightAndAlert -> R.drawable.ic_star_shine
            ActionModel.Highlight -> R.drawable.ic_star
            ActionModel.Silence -> R.drawable.ic_notifications_off
            is ActionModel.Bundle -> com.android.settingslib.R.drawable.ic_dynamic_bundle
            ActionModel.Block -> R.drawable.ic_block
        }
    return painterResource(resourceId)
}

@Composable
fun ActionModel.toColor(): Color {
    return when (this) {
        // TODO: b/478225883 - Also include light theme variants.
        ActionModel.HighlightAndAlert -> Color(0xFF81E1D9)
        ActionModel.Highlight -> Color(0xFF86E181)
        ActionModel.Silence -> Color(0xFFE1D381)
        is ActionModel.Bundle -> Color(0xFFE1AE81)
        ActionModel.Block -> Color(0xFFE18981)
    }
}
