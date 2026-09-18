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

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.notifications.intelligence.rules.shared.model.ActionModel
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftFilterModel
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel
import com.android.systemui.notifications.intelligence.rules.shared.model.KeywordsModel
import com.android.systemui.notifications.intelligence.rules.shared.model.ResponseModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@SysUISingleton
class FreeformRuleRepositoryImpl
@Inject
constructor(@Background private val backgroundDispatcher: CoroutineDispatcher) :
    FreeformRuleRepository {
    override suspend fun createDraftRuleFromFreeformText(
        action: ActionModel,
        text: String,
    ): ResponseModel<DraftRuleModel> {
        return withContext(backgroundDispatcher) {
            // TODO: b/478225883 - Send freeform text for processing.
            val keywords =
                if (text.isNotBlank()) {
                    KeywordsModel(listOf(text))
                } else {
                    null
                }
            val newDraftRule =
                DraftRuleModel.New(action = action, filter = DraftFilterModel(keywords = keywords))
            ResponseModel.Success(newDraftRule)
        }
    }
}
