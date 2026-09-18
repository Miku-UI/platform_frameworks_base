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

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.RulesScreenViewState
import com.android.systemui.res.R

/** Renders an inline menu to choose a new field to add to the rule. */
@Composable
fun AddFieldDialog(
    options: List<RulesScreenViewState.EditField>,
    onDismissRequest: () -> Unit,
    onOptionSelected: (RulesScreenViewState.EditField) -> Unit,
) {
    DropdownMenu(expanded = true, onDismissRequest = onDismissRequest) {
        for (option in options) {
            DropdownMenuItem(
                text = { Text(option.toText()) },
                onClick = {
                    onDismissRequest.invoke()
                    onOptionSelected.invoke(option)
                },
            )
        }
    }
}

@Composable
private fun RulesScreenViewState.EditField.toText(): String {
    val resourceId =
        when (this) {
            is RulesScreenViewState.EditField.People -> R.string.notification_rules_field_people
            is RulesScreenViewState.EditField.Apps -> R.string.notification_rules_field_app
            is RulesScreenViewState.EditField.Keywords -> R.string.notification_rules_field_keyword
            is RulesScreenViewState.EditField.Action ->
                throw IllegalStateException(
                    "Actions should always be edited inline, not via the `Add` button"
                )
        }
    return stringResource(resourceId)
}
