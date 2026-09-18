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

/** Responsible for transforming freeform text into a draft rule. */
interface FreeformRuleRepository {
    /**
     * Creates a draft rule based on the freeform text inputted by the user.
     *
     * If created successfully, the new draft rule will be sent via [onChangeResponseStatus].
     */
    public suspend fun createDraftRuleFromFreeformText(
        action: ActionModel,
        text: String,
    ): ResponseModel<DraftRuleModel>
}
