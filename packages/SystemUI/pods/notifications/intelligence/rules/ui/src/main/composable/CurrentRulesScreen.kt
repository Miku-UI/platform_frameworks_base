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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel.Companion.toDraft
import com.android.systemui.notifications.intelligence.rules.shared.model.RuleModel
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.NotificationRulesScreenViewModel
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.TextStyles
import com.android.systemui.res.R

@Composable
fun CurrentRulesScreen(
    viewModel: NotificationRulesScreenViewModel,
    onDismissCurrentRulesScreen: () -> Unit,
    onNavigateToEditScreen: (DraftRuleModel) -> Unit,
    onNavigateToFreeformRuleCreationScreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textStyles = rememberTextStyles()
    BackHandler(enabled = true, onBack = onDismissCurrentRulesScreen)

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.Top),
        modifier = modifier,
    ) {
        item("Title") {
            Header(
                title = stringResource(R.string.notification_rules_activity_title),
                onDismissRequest = onDismissCurrentRulesScreen,
                actions = { CreateNewRuleAction(onNavigateToFreeformRuleCreationScreen) },
            )
        }

        viewModel.rules.forEach { rule ->
            item(rule.id) {
                CurrentRule(
                    rule = rule,
                    screenViewModel = viewModel,
                    textStyles = textStyles,
                    onNavigateToEditScreen = onNavigateToEditScreen,
                )
            }
        }
    }
}

@Composable
private fun CurrentRule(
    rule: RuleModel,
    screenViewModel: NotificationRulesScreenViewModel,
    textStyles: TextStyles,
    onNavigateToEditScreen: (DraftRuleModel) -> Unit,
) {
    val resources = LocalResources.current
    var isExpanded by remember { mutableStateOf(false) }

    val textSize = textStyles.defaultStyle.fontSize
    val ruleDisplay = remember(rule, resources) { screenViewModel.buildRuleText(rule, resources) }
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
                    PersonIcon(it, iconSizeDp, screenViewModel::loadContactBitmapFromUri)
                },
                textSize = textSize,
            )
        }

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.large,
                )
                .clickable(onClick = { isExpanded = !isExpanded })
                .padding(8.dp)
    ) {
        ReadOnlyAction(rule.action)
        Text(
            text = text,
            inlineContent = inlineTextContent,
            color = MaterialTheme.colorScheme.onSurface,
            style = textStyles.defaultStyle,
            modifier = Modifier.padding(top = 8.dp),
        )

        if (isExpanded) {
            Button(onClick = { onNavigateToEditScreen(rule.toDraft()) }) {
                Text(stringResource(R.string.notification_rules_edit))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun rememberTextStyles(): TextStyles {
    val defaultStyle = MaterialTheme.typography.titleLargeEmphasized
    val valueSpanStyle = SpanStyle(fontWeight = FontWeight.Bold)
    return remember(defaultStyle, valueSpanStyle) {
        TextStyles(
            defaultStyle = defaultStyle,
            specifiedValueSpanStyle = valueSpanStyle,
            ambiguousValueSpanStyle = valueSpanStyle,
        )
    }
}

/** Renders a '+' button that lets users create a new notification rule using freeform text. */
@Composable
private fun CreateNewRuleAction(onNavigateToFreeformRuleCreationScreen: () -> Unit) {
    IconButton(onClick = onNavigateToFreeformRuleCreationScreen) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = stringResource(R.string.notification_rules_create_new_title),
        )
    }
}
