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

import com.android.systemui.notifications.intelligence.rules.shared.model.ActionModel
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel
import com.android.systemui.notifications.intelligence.rules.shared.model.ResponseModel
import com.android.systemui.notifications.intelligence.rules.shared.model.RuleModel

/**
 * A repository storing a user's current notification rules and methods for updating those rules.
 */
public interface NotificationRulesRepository {
    /** A list of the user's current rules. */
    public val rules: List<RuleModel>

    /** Creates a draft rule based on the freeform text inputted by the user. */
    public suspend fun createDraftRuleFromFreeformText(
        action: ActionModel,
        text: String,
    ): ResponseModel<DraftRuleModel>

    /**
     * Saves the given [rule] to system_server. Returns true if the rule was saved successfully and
     * false if there was an error when saving.
     */
    suspend fun saveRule(rule: DraftRuleModel): Boolean
}
