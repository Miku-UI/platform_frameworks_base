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

package com.android.systemui.notifications.intelligence.rules.data.repository

import android.app.NotificationRule
import com.android.systemui.notifications.intelligence.rules.data.repository.NotificationRuleConversionHelper.toExternalModel
import com.android.systemui.notifications.intelligence.rules.shared.model.PersonModel
import com.android.systemui.notifications.intelligence.rules.shared.model.RuleModel

object NotificationRuleToExternalHelpers {
    /** Converts the internal [RuleModel] to the system_server equivalent, [NotificationRule]. */
    fun RuleModel.toExternalRuleFormat(): NotificationRule {
        val internalFilter = this.filter
        val filter =
            if (internalFilter != null && internalFilter.hasContent) {
                NotificationRule.Filter.Builder()
                    .apply {
                        internalFilter.people?.let { people ->
                            val contacts = people.people.filterIsInstance<PersonModel.Contact>()
                            contacts.forEach { addContact(it.lookupUri) }

                            val conversationPartners =
                                people.people.filterIsInstance<PersonModel.ConversationPartner>()
                            conversationPartners.forEach { addShortcutId(it.id) }
                        }
                        internalFilter.includedApps?.let { apps ->
                            apps.apps.forEach { addIncludedPackageUid(it.uid) }
                        }
                        internalFilter.keywords?.let { keywords ->
                            keywords.keywords.forEach { addKeyword(it) }
                        }
                    }
                    .build()
            } else {
                null
            }

        return NotificationRule.Builder(this.id, this.action.toExternalModel())
            .apply { filter?.let { addFilter(it) } }
            .build()
    }
}
