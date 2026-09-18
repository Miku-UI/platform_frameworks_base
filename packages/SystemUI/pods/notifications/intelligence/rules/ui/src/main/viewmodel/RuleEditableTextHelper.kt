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

package com.android.systemui.notifications.intelligence.rules.ui.viewmodel

import android.annotation.PluralsRes
import android.content.res.Resources
import com.android.systemui.log.core.Logger
import com.android.systemui.notifications.intelligence.rules.shared.model.AppModel
import com.android.systemui.notifications.intelligence.rules.shared.model.IncludedAppsModel
import com.android.systemui.notifications.intelligence.rules.shared.model.KeywordsModel
import com.android.systemui.notifications.intelligence.rules.shared.model.PeopleModel
import com.android.systemui.notifications.intelligence.rules.shared.model.PersonModel
import com.android.systemui.notifications.intelligence.rules.shared.model.RuleValue

/**
 * Transforms [viewModel.rule] into a readable string. Also includes clickable spans where the user
 * can modify particular fields of the rule.
 */
internal fun buildEditableRuleText(
    viewModel: NotificationRuleEditViewModel,
    onEnterEditField: (RulesScreenViewState.EditField) -> Unit,
    onAppsSaved: (List<AppModel>) -> Unit,
    onPeopleSaved: (List<PersonModel>) -> Unit,
    onKeywordsSaved: (List<String>) -> Unit,
    resources: Resources,
    logger: Logger,
): RuleDisplayModel {
    val appsText: TextModel.SingleFieldTextModel<AppModel>? =
        viewModel.rule.filter.includedApps?.let {
            createEditableIncludedAppsText(
                selectedIncludedApps = it,
                viewModel = viewModel,
                onEnterEditField = onEnterEditField,
                onAppsSaved = onAppsSaved,
                resources = resources,
                logger = logger,
            )
        }

    val peopleText: TextModel.SingleFieldTextModel<PersonModel>? =
        viewModel.rule.filter.people?.let {
            createEditablePeopleText(
                selectedPeople = it,
                viewModel = viewModel,
                onEnterEditField = onEnterEditField,
                onPeopleSaved = onPeopleSaved,
                resources = resources,
                logger = logger,
            )
        }

    val keywordsText: TextModel.SingleFieldTextModel<String>? =
        viewModel.rule.filter.keywords?.let {
            createEditableKeywordsText(
                selectedKeywords = it,
                viewModel = viewModel,
                onEnterEditField = onEnterEditField,
                onKeywordsSaved = onKeywordsSaved,
                resources = resources,
                logger = logger,
            )
        }

    val bundleText = createBundleText(viewModel.rule.action, resources = resources, logger = logger)

    return buildRuleText(
        appsText = appsText,
        peopleText = peopleText,
        keywordsText = keywordsText,
        bundleText = bundleText,
        resources = resources,
    )
}

/** Creates text representation for the included apps filter field. */
private fun createEditableIncludedAppsText(
    selectedIncludedApps: RuleValue<IncludedAppsModel>,
    viewModel: NotificationRuleEditViewModel,
    onEnterEditField: (RulesScreenViewState.EditField) -> Unit,
    onAppsSaved: (List<AppModel>) -> Unit,
    resources: Resources,
    logger: Logger,
): TextModel.SingleFieldTextModel<AppModel> {
    val onClick = {
        onEnterEditField(
            RulesScreenViewState.EditField.Apps(viewModel = viewModel, onAppsSaved = onAppsSaved)
        )
    }

    val items =
        if (selectedIncludedApps is RuleValue.Specified<IncludedAppsModel>) {
            selectedIncludedApps.value.apps
        } else {
            emptyList()
        }

    return createFieldText(
        FieldDataModel(
            currentValue = selectedIncludedApps,
            items = items,
            iconId = { it.uniqueId },
            label = { it.label },
            itemTextString = ItemTextStringConstants.includedAppString,
            onClick = onClick,
        ),
        resources = resources,
        logger = logger,
    )
}

/** Creates text representation for the people filter field. */
private fun createEditablePeopleText(
    selectedPeople: RuleValue<PeopleModel>,
    viewModel: NotificationRuleEditViewModel,
    onEnterEditField: (RulesScreenViewState.EditField) -> Unit,
    onPeopleSaved: (List<PersonModel>) -> Unit,
    resources: Resources,
    logger: Logger,
): TextModel.SingleFieldTextModel<PersonModel> {
    val onClick: () -> Unit = {
        onEnterEditField(
            RulesScreenViewState.EditField.People(
                onPeopleSaved = onPeopleSaved,
                viewModel = viewModel,
            )
        )
    }

    val items =
        if (selectedPeople is RuleValue.Specified<PeopleModel>) {
            selectedPeople.value.people
        } else {
            emptyList()
        }

    return createFieldText(
        fieldData =
            FieldDataModel(
                currentValue = selectedPeople,
                items = items,
                iconId = { it.id },
                label = { it.displayLabel },
                itemTextString = ItemTextStringConstants.personString,
                onClick = onClick,
            ),
        resources = resources,
        logger = logger,
    )
}

/** Creates text representation for the keywords filter field. */
private fun createEditableKeywordsText(
    selectedKeywords: KeywordsModel,
    viewModel: NotificationRuleEditViewModel,
    onEnterEditField: (RulesScreenViewState.EditField) -> Unit,
    onKeywordsSaved: (List<String>) -> Unit,
    resources: Resources,
    logger: Logger,
): TextModel.SingleFieldTextModel<String> {
    val onClick: () -> Unit = {
        onEnterEditField(
            RulesScreenViewState.EditField.Keywords(
                viewModel = viewModel,
                onKeywordsSaved = onKeywordsSaved,
            )
        )
    }

    return createMultiItemText(
        items = selectedKeywords.keywords,
        iconId = null, // No icon for keywords
        label = { it },
        onClick = onClick,
        itemTextString = ItemTextStringConstants.keywordString,
        resources = resources,
        logger = logger,
    )
}

/** Creates text representation for a single field. */
private fun <T, R> createFieldText(
    fieldData: FieldDataModel<T, R>,
    resources: Resources,
    logger: Logger,
): TextModel.SingleFieldTextModel<T> {
    return when (fieldData.currentValue) {
        is RuleValue.Specified -> {
            createMultiItemText(
                items = fieldData.items,
                iconId = fieldData.iconId,
                label = fieldData.label,
                onClick = fieldData.onClick,
                itemTextString = fieldData.itemTextString,
                resources = resources,
                logger = logger,
            )
        }
        is RuleValue.Ambiguous -> {
            createAmbiguousText(
                placeholderText = fieldData.currentValue.placeholderText,
                onClick = fieldData.onClick,
                itemTextString = fieldData.itemTextString,
                resources = resources,
                logger = logger,
            )
        }
    }
}

/**
 * Represents a field in a rule, like [RuleModel.filter.includedApps] or [RuleModel.filter.people].
 *
 * Type T: The type for an individual item in the filter, like [PersonModel].
 *
 * Type R: The type of the field as a whole, like [PeopleModel].
 */
private data class FieldDataModel<T, R>(
    val currentValue: RuleValue<R>,
    val items: List<T>,
    val iconId: (T) -> String,
    val label: (T) -> String,
    val onClick: () -> Unit,
    @PluralsRes val itemTextString: Int,
)
