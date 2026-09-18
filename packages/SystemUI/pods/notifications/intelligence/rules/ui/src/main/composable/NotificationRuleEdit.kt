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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.NotificationRuleEditViewModel
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.RuleDisplayModel
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.RulesScreenViewState
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.TextStyles
import com.android.systemui.res.R

/**
 * A composable rendering a page to edit a specific notification rule.
 *
 * This is still a work-in-progress.
 *
 * @param onDismissRuleEditScreen invoked when the user dismisses this current screen.
 * @param onEnterEditField invoked when the user starts editing a particular field of the rule.
 * @param onExitEditField invoked when the user finishes editing a particular field of the rule.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NotificationRuleEdit(
    viewModel: NotificationRuleEditViewModel,
    onDismissRuleEditScreen: () -> Unit,
    onEnterEditField: (RulesScreenViewState.EditField) -> Unit,
    onExitEditField: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(viewModel) { onDispose { viewModel.cleanUp() } }
    val resources = LocalResources.current

    val addFieldOptions: List<RulesScreenViewState.EditField> =
        buildAddFieldOptions(viewModel, onExitEditField = onExitEditField)
    var isAddFieldDialogShowing by remember { mutableStateOf(false) }

    val textStyles = rememberTextStyles()
    val textSize = textStyles.defaultStyle.fontSize
    val ruleDisplay: RuleDisplayModel =
        remember(
            viewModel,
            viewModel.rule,
            onEnterEditField,
            onExitEditField,
            textStyles,
            resources,
        ) {
            viewModel.buildRuleText(
                onEnterEditField = onEnterEditField,
                onExitEditField = onExitEditField,
                resources = resources,
            )
        }
    val text =
        remember(ruleDisplay.textChunks, textStyles) {
            buildAnnotatedString(ruleDisplay.textChunks, textStyles)
        }
    val inlineTextContent =
        remember(ruleDisplay.textChunks, textStyles) {
            buildInlineContentMap(
                ruleDisplay.textChunks,
                appIcon = { AppIcon(it) },
                personIcon = {
                    val iconSizeDp = with(LocalDensity.current) { textSize.toDp() }
                    PersonIcon(it, iconSizeDp, viewModel::loadContactBitmapFromUri)
                },
                textSize = textSize,
            )
        }

    BackHandler(enabled = true, onBack = onDismissRuleEditScreen)
    Column(modifier = modifier) {
        Header(
            title =
                if (viewModel.rule is DraftRuleModel.New) {
                    stringResource(R.string.notification_rules_create_new_title)
                } else {
                    stringResource(R.string.notification_rules_edit)
                },
            onDismissRequest = onDismissRuleEditScreen,
        )
        EditableAction(
            action = viewModel.rule.action,
            onEnterEditField = onEnterEditField,
            onActionSaved = { newAction ->
                viewModel.rule = viewModel.rule.copyDraft(action = newAction)
            },
        )
        Text(
            text = text,
            inlineContent = inlineTextContent,
            color = MaterialTheme.colorScheme.onSurface,
            style = textStyles.defaultStyle,
        )

        AddButton(
            addFieldOptions = addFieldOptions,
            toggleAddFieldDialogShowing = { isAddFieldDialogShowing = !isAddFieldDialogShowing },
        )
        if (isAddFieldDialogShowing) {
            AddFieldDialog(
                options = addFieldOptions,
                onDismissRequest = { isAddFieldDialogShowing = false },
                onOptionSelected = { editField -> onEnterEditField(editField) },
            )
        }

        if (viewModel.isErrorVisible) {
            ErrorMessage(modifier = Modifier.fillMaxWidth(0.8f).align(Alignment.CenterHorizontally))
        }

        SaveRuleButton(
            // Only let the user save the rule once all ambiguous values have been fixed.
            isEnabled = !viewModel.rule.hasAmbiguousValues,
            isRuleNew = viewModel.rule is DraftRuleModel.New,
            onClick = { viewModel.saveRule() },
            modifier = Modifier.fillMaxWidth(0.8f).align(Alignment.CenterHorizontally),
        )
    }
}

/** Renders a '+' button letting users add additional fields to the rule. */
@Composable
private fun AddButton(
    addFieldOptions: List<RulesScreenViewState.EditField>,
    toggleAddFieldDialogShowing: () -> Unit,
) {
    if (addFieldOptions.isEmpty()) {
        return
    }

    Button(onClick = { toggleAddFieldDialogShowing() }) { Text(stringResource(R.string.add)) }
}

/**
 * Builds a list of filter and condition fields that can be added to the rule. Only includes types
 * that *aren't* present in the rule yet. (Types that *are* present can be edited by clicking their
 * text.)
 */
private fun buildAddFieldOptions(
    viewModel: NotificationRuleEditViewModel,
    onExitEditField: () -> Unit,
): List<RulesScreenViewState.EditField> {
    return mutableListOf<RulesScreenViewState.EditField>().apply {
        if (viewModel.rule.filter.people == null) {
            add(
                RulesScreenViewState.EditField.People(
                    onPeopleSaved = { newPeople ->
                        viewModel.onPeopleSaved(newPeople, onExitEditField)
                    },
                    viewModel = viewModel,
                )
            )
        }
        if (viewModel.rule.filter.includedApps == null) {
            add(
                RulesScreenViewState.EditField.Apps(
                    viewModel = viewModel,
                    onAppsSaved = { newApps -> viewModel.onAppsSaved(newApps, onExitEditField) },
                )
            )
        }
        if (viewModel.rule.filter.keywords == null) {
            add(
                RulesScreenViewState.EditField.Keywords(
                    viewModel = viewModel,
                    onKeywordsSaved = { newKeywords ->
                        viewModel.onKeywordsSaved(newKeywords, onExitEditField)
                    },
                )
            )
        }
    }
}

@Composable
private fun SaveRuleButton(
    isEnabled: Boolean,
    isRuleNew: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(onClick = onClick, enabled = isEnabled, modifier = modifier) {
        Text(
            if (isRuleNew) {
                stringResource(R.string.notification_rules_create_new_rule)
            } else {
                stringResource(R.string.notification_rules_confirm_changes)
            }
        )
    }
}

@Composable
private fun ErrorMessage(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.notification_rules_save_error),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onErrorContainer,
        modifier =
            modifier
                .background(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                )
                .padding(8.dp),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun rememberTextStyles(): TextStyles {
    val defaultStyle = MaterialTheme.typography.titleLargeEmphasized
    val baseValueSpanStyle =
        SpanStyle(textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Bold)
    val specified = baseValueSpanStyle.copy(color = MaterialTheme.colorScheme.primary)
    val ambiguous = baseValueSpanStyle.copy(color = MaterialTheme.colorScheme.error)

    return remember(defaultStyle, specified, ambiguous) {
        TextStyles(
            defaultStyle = defaultStyle,
            specifiedValueSpanStyle = specified,
            ambiguousValueSpanStyle = ambiguous,
        )
    }
}
