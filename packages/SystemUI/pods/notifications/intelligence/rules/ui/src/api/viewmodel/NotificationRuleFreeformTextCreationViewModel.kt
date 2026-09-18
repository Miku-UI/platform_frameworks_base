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

import androidx.compose.foundation.text.input.TextFieldState
import com.android.systemui.notifications.intelligence.rules.shared.model.ActionModel
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel

/** View model for the screen allowing a user to input freeform text in order to create a rule. */
public interface NotificationRuleFreeformTextCreationViewModel {
    /** The currently-selected action on the screen. */
    public var selectedAction: ActionModel

    /** The currently-inputted text on the screen. */
    public val enteredText: TextFieldState

    /**
     * True if the user's freeform text has been sent for processing and SysUI awaits a response.
     */
    val isLoadingIndicatorVisible: Boolean
    /** True if there was an error in processing the freeform text. */
    val isErrorVisible: Boolean

    /** Creates a draft rule based on the freeform text inputted by the user. */
    public suspend fun createDraftRuleFromFreeformText()

    /** Cancels any pending requests. */
    public fun cancelRequest()

    public interface Factory {
        public fun create(
            onNavigateToEditScreen: (DraftRuleModel) -> Unit
        ): NotificationRuleFreeformTextCreationViewModel
    }
}
