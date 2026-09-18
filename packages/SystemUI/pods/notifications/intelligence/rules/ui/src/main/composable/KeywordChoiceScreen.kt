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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.RulesScreenViewState
import com.android.systemui.res.R

/** Renders a fullscreen page to select keywords for a notification rule. */
@Composable
fun KeywordChoiceScreen(
    viewState: RulesScreenViewState.EditField.Keywords,
    onDismissRequest: () -> Unit,
) {
    val viewModel = viewState.viewModel
    val initialSelection: List<String> = viewModel.rule.filter.keywords?.keywords ?: emptyList()

    EditScreen(
        title = stringResource(R.string.notification_rules_field_keyword),
        initialSelection = initialSelection,
        onSelectionSaved = viewState.onKeywordsSaved,
        onDismissRequest = onDismissRequest,
        sortKey = { it },
        uniqueId = { it },
        icon = null,
        text = { it },
        inputSlot = { selectionHandler -> KeywordInputField(selectionHandler) },
        additionalContentSlot = null,
    )
}

/** Renders an input field for users to type and save new keywords. */
@Composable
private fun KeywordInputField(selectionHandler: SelectionHandler<String>) {
    val textFieldState = rememberTextFieldState()
    TextField(
        state = textFieldState,
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            AddKeywordButton(
                onClick = {
                    selectionHandler.onSelectionToggled(
                        item = textFieldState.text.toString().lowercase(),
                        isSelected = true,
                    )
                }
            )
        },
    )
}

@Composable
private fun AddKeywordButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(imageVector = Icons.Filled.Add, contentDescription = stringResource(R.string.add))
    }
}
