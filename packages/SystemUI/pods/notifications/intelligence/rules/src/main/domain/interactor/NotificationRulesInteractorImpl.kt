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

package com.android.systemui.notifications.intelligence.rules.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.notifications.intelligence.rules.data.repository.NotificationRulesRepository
import com.android.systemui.notifications.intelligence.rules.shared.model.ActionModel
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel
import com.android.systemui.notifications.intelligence.rules.shared.model.ResponseModel
import com.android.systemui.notifications.intelligence.rules.shared.model.RuleModel
import javax.inject.Inject

@SysUISingleton
class NotificationRulesInteractorImpl
@Inject
constructor(private val repository: NotificationRulesRepository) : NotificationRulesInteractor {
    override val rules: List<RuleModel>
        get() = repository.rules

    override suspend fun createDraftRuleFromFreeformText(
        action: ActionModel,
        text: String,
    ): ResponseModel<DraftRuleModel> {
        return repository.createDraftRuleFromFreeformText(action, text)
    }

    override suspend fun saveRule(rule: DraftRuleModel): Boolean {
        return repository.saveRule(rule)
    }
}
