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

import com.android.systemui.notifications.intelligence.rules.shared.model.ActionModel
import com.android.systemui.notifications.intelligence.rules.shared.model.AppModel
import com.android.systemui.notifications.intelligence.rules.shared.model.PersonModel

/** Represents the current state of the rules screen. */
public sealed interface RulesScreenViewState {
    /** The rules screen is showing a list of the currently saved rules. */
    public data object CurrentRules : RulesScreenViewState

    /** The user can input freeform text to generate a new rule. */
    public data class FreeformTextRuleCreation(
        val viewModel: NotificationRuleFreeformTextCreationViewModel
    ) : RulesScreenViewState

    /** The rules screen is showing an edit page for the given [viewModel.rule]. */
    public data class EditRule(val viewModel: NotificationRuleEditViewModel) : RulesScreenViewState

    /** One particular field is being edited. */
    public sealed interface EditField : RulesScreenViewState {
        /**
         * The action for a particular rule is being edited.
         *
         * @param selectedAction the currently selected action.
         * @param onActionSaved invoked when the user selects an action in the menu.
         */
        public data class Action(
            val selectedAction: ActionModel,
            val onActionSaved: (ActionModel) -> Unit,
        ) : EditField

        /** The list of people for a particular rule is being edited. */
        data class People(
            val viewModel: NotificationRuleEditViewModel,
            val onPeopleSaved: (List<PersonModel>) -> Unit,
        ) : EditField

        /** The list of apps for a particular rule is being edited. */
        public data class Apps(
            val viewModel: NotificationRuleEditViewModel,
            val onAppsSaved: (List<AppModel>) -> Unit,
        ) : EditField

        /** The list of keywords for a particular rule is being edited. */
        data class Keywords(
            val viewModel: NotificationRuleEditViewModel,
            val onKeywordsSaved: (List<String>) -> Unit,
        ) : EditField
    }

    // TODO: b/478225883 - Add more edit types.
}
