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

import android.content.res.Resources
import com.android.systemui.log.core.Logger
import com.android.systemui.notifications.intelligence.rules.shared.model.AppModel
import com.android.systemui.notifications.intelligence.rules.shared.model.IncludedAppsModel
import com.android.systemui.notifications.intelligence.rules.shared.model.KeywordsModel
import com.android.systemui.notifications.intelligence.rules.shared.model.PeopleModel
import com.android.systemui.notifications.intelligence.rules.shared.model.PersonModel
import com.android.systemui.notifications.intelligence.rules.shared.model.RuleModel

/**
 * Transforms [rule] into a readable string. Because this is a read-only view, individual fields are
 * more visually prominent but not editable.
 */
internal fun buildReadOnlyRuleText(
    rule: RuleModel,
    resources: Resources,
    logger: Logger,
): RuleDisplayModel {
    val appsText: TextModel.SingleFieldTextModel<AppModel>? =
        rule.filter?.includedApps?.let {
            createReadOnlyIncludedAppsText(
                selectedIncludedApps = it,
                resources = resources,
                logger = logger,
            )
        }

    val peopleText: TextModel.SingleFieldTextModel<PersonModel>? =
        rule.filter?.people?.let {
            createReadOnlyPeopleText(selectedPeople = it, resources = resources, logger = logger)
        }

    val keywordsText: TextModel.SingleFieldTextModel<String>? =
        rule.filter?.keywords?.let {
            createReadOnlyKeywordsText(
                selectedKeywords = it,
                resources = resources,
                logger = logger,
            )
        }

    val bundleText = createBundleText(rule.action, resources = resources, logger = logger)

    return buildRuleText(
        appsText = appsText,
        peopleText = peopleText,
        keywordsText = keywordsText,
        bundleText = bundleText,
        resources = resources,
    )
}

/** Creates text representation for the included apps filter field. */
private fun createReadOnlyIncludedAppsText(
    selectedIncludedApps: IncludedAppsModel,
    resources: Resources,
    logger: Logger,
): TextModel.SingleFieldTextModel<AppModel> {
    return createMultiItemText(
        items = selectedIncludedApps.apps,
        iconId = { it.uniqueId },
        label = { it.label },
        onClick = null,
        itemTextString = ItemTextStringConstants.includedAppString,
        resources = resources,
        logger = logger,
    )
}

/** Creates text representation for the people filter field. */
private fun createReadOnlyPeopleText(
    selectedPeople: PeopleModel,
    resources: Resources,
    logger: Logger,
): TextModel.SingleFieldTextModel<PersonModel> {
    return createMultiItemText(
        items = selectedPeople.people,
        iconId = { it.id },
        label = { it.displayLabel },
        onClick = null,
        itemTextString = ItemTextStringConstants.personString,
        resources = resources,
        logger = logger,
    )
}

private fun createReadOnlyKeywordsText(
    selectedKeywords: KeywordsModel,
    resources: Resources,
    logger: Logger,
): TextModel.SingleFieldTextModel<String> {
    return createMultiItemText(
        items = selectedKeywords.keywords,
        iconId = null, // No icon for keywords
        label = { it },
        onClick = null,
        itemTextString = ItemTextStringConstants.keywordString,
        resources = resources,
        logger = logger,
    )
}
