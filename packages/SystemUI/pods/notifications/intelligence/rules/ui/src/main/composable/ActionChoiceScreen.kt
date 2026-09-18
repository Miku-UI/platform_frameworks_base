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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.android.systemui.notifications.intelligence.rules.shared.model.ActionModel
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.RulesScreenViewState
import com.android.systemui.res.R

/** Renders a fullscreen page to choose one rule action. */
@Composable
fun ActionChoiceScreen(
    viewState: RulesScreenViewState.EditField.Action,
    onDismissRequest: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        Header(
            stringResource(R.string.notification_rules_choose_action_title),
            onDismissRequest = onDismissRequest,
        )

        val actionOnClick: (ActionModel) -> Unit = {
            viewState.onActionSaved(it)
            onDismissRequest.invoke()
        }

        HeaderText(stringResource(R.string.notification_rules_action_more_alerting))
        MoreAlertingActions.fastForEach { action ->
            ActionItem(
                action = action,
                onClick = { actionOnClick(action) },
                isSelected = action == viewState.selectedAction,
            )
        }

        HeaderText(stringResource(R.string.notification_rules_action_less_alerting))
        buildLessAlertingActions(selectedAction = viewState.selectedAction).fastForEach { action ->
            ActionItem(
                action = action,
                onClick = { actionOnClick(action) },
                isSelected = action == viewState.selectedAction,
            )
        }
    }
}

@Composable
private fun HeaderText(string: String, modifier: Modifier = Modifier) {
    Text(
        text = string,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleMediumEmphasized,
        modifier = modifier.padding(16.dp),
    )
}

@Composable
private fun ActionItem(
    action: ActionModel,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = MaterialTheme.shapes.medium
    val textColor =
        if (isSelected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        }
    Column(
        modifier =
            modifier
                .then(
                    if (isSelected) {
                        Modifier.border(width = 2.dp, color = action.toColor(), shape = cardShape)
                    } else {
                        Modifier.border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                            shape = cardShape,
                        )
                    }
                )
                .background(color = MaterialTheme.colorScheme.surface, shape = cardShape)
                .clickable(enabled = true, onClick = onClick)
                .padding(16.dp)
    ) {
        ReadOnlyAction(action)
        Text(
            text = action.toDescription(),
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

private fun buildLessAlertingActions(selectedAction: ActionModel): List<ActionModel> {
    return buildList {
        add(ActionModel.Silence)

        if (selectedAction is ActionModel.Bundle) {
            // If the user already picked bundle & then picked it again, use their
            // already-defined bundle name & emoji.
            add(selectedAction)
        } else {
            // Seed the new bundle action with a name & emoji so users can edit them.
            // TODO: b/478225883 - Define single default bundle name and emoji in strings.xml.
            add(
                ActionModel.Bundle(
                    name = defaultBundleNames.random(),
                    emojiIcon = defaultBundleEmojis.random(),
                )
            )
        }

        add(ActionModel.Block)
    }
}

private val defaultBundleNames =
    listOf("My Custom Bundle [TK]", "Demo Bundle [TK]", "Example Bundle [TK]")
private val defaultBundleEmojis = listOf("\uD83D\uDCE6", "\uD83C\uDF81", "\uD83D\uDC9D")

private val MoreAlertingActions = listOf(ActionModel.HighlightAndAlert, ActionModel.Highlight)
