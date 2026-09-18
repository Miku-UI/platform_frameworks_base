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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.systemui.notifications.intelligence.rules.shared.model.ActionModel
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.NotificationRuleFreeformTextCreationViewModel
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.RulesScreenViewState
import com.android.systemui.res.R
import kotlinx.coroutines.launch

/** Screen allowing the user to input freeform text in order to generate a new notification rule. */
@Composable
fun FreeformTextRuleCreationScreen(
    viewModel: NotificationRuleFreeformTextCreationViewModel,
    onDismissRequest: () -> Unit,
    onEnterEditField: (RulesScreenViewState.EditField) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    DisposableEffect(viewModel) { onDispose { viewModel.cancelRequest() } }

    Column(modifier = modifier) {
        Header(
            stringResource(R.string.notification_rules_create_new_title),
            onDismissRequest = onDismissRequest,
        )

        FreeformTextArea(
            action = viewModel.selectedAction,
            textFieldState = viewModel.enteredText,
            isLoadingIndicatorVisible = viewModel.isLoadingIndicatorVisible,
            isErrorVisible = viewModel.isErrorVisible,
            onEnterEditField = onEnterEditField,
            onActionSaved = { viewModel.selectedAction = it },
            modifier =
                Modifier.background(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.large,
                    )
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f),
        )

        NextButton(
            isLoadingIndicatorVisible = viewModel.isLoadingIndicatorVisible,
            onClick = {
                // Use scope so that we cancel the rule generation if the user ever leaves this page
                scope.launch { viewModel.createDraftRuleFromFreeformText() }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FreeformTextArea(
    action: ActionModel,
    textFieldState: TextFieldState,
    isLoadingIndicatorVisible: Boolean,
    isErrorVisible: Boolean,
    onEnterEditField: (RulesScreenViewState.EditField) -> Unit,
    onActionSaved: (ActionModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        if (isLoadingIndicatorVisible) {
            // Don't let the user modify the action while the rule is being generated
            ReadOnlyAction(action)
        } else {
            EditableAction(
                action = action,
                onEnterEditField = onEnterEditField,
                onActionSaved = onActionSaved,
            )
        }

        TextField(
            textFieldState,
            enabled = !isLoadingIndicatorVisible,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )

        if (isErrorVisible) {
            ErrorMessage(
                Modifier.fillMaxWidth(0.9f)
                    .background(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                    )
                    .padding(8.dp)
            )
        }

        // TODO: b/478225883 - Add the inline prompt suggestions.
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ErrorMessage(modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(
            stringResource(R.string.notification_rules_generation_error_title),
            style = MaterialTheme.typography.titleLargeEmphasized,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(
            stringResource(R.string.notification_rules_generation_error_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun NextButton(
    isLoadingIndicatorVisible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textStyle = MaterialTheme.typography.labelLarge

    if (isLoadingIndicatorVisible) {
        Button(enabled = false, onClick = onClick, modifier = modifier) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.padding(end = 8.dp).size(24.dp),
            )
            Text(
                stringResource(R.string.notification_rules_generating),
                color = MaterialTheme.colorScheme.inverseOnSurface,
                style = textStyle,
            )
        }
    } else {
        Button(enabled = true, onClick = onClick, modifier = modifier) {
            Text(stringResource(R.string.notification_rules_next), style = textStyle)
        }
    }
}
