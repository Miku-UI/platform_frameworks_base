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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.systemui.notifications.intelligence.rules.domain.interactor.NotificationRulesInteractor
import com.android.systemui.notifications.intelligence.rules.shared.model.ActionModel
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel
import com.android.systemui.notifications.intelligence.rules.shared.model.ResponseModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

class NotificationRuleFreeformTextCreationViewModelImpl
@AssistedInject
constructor(
    private val interactor: NotificationRulesInteractor,
    @Assisted private val onNavigateToEditScreen: (DraftRuleModel) -> Unit,
) : NotificationRuleFreeformTextCreationViewModel {
    // These items are stored in the view model so that they're retained even when going between
    // different pages (e.g. editing the action then coming back, editing the rule then coming back)
    override var selectedAction: ActionModel by mutableStateOf(ActionModel.Highlight)
    override val enteredText = TextFieldState()

    private var processedFreeformTextResponse: ResponseModel<DraftRuleModel>? by
        mutableStateOf(null)

    override val isLoadingIndicatorVisible: Boolean
        get() = processedFreeformTextResponse is ResponseModel.Loading

    override val isErrorVisible: Boolean
        get() = processedFreeformTextResponse is ResponseModel.Error

    override suspend fun createDraftRuleFromFreeformText() {
        processedFreeformTextResponse = ResponseModel.Loading
        val result =
            interactor.createDraftRuleFromFreeformText(selectedAction, enteredText.text.toString())
        processedFreeformTextResponse = result
        if (result is ResponseModel.Success) {
            onNavigateToEditScreen(result.draftRule)
        }
    }

    override fun cancelRequest() {
        processedFreeformTextResponse = null
    }

    @AssistedFactory
    interface Factory : NotificationRuleFreeformTextCreationViewModel.Factory {
        override fun create(
            onNavigateToEditScreen: (DraftRuleModel) -> Unit
        ): NotificationRuleFreeformTextCreationViewModelImpl
    }
}
