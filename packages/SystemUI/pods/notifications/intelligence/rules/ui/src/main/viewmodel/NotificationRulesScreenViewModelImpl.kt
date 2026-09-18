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

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.net.Uri
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.notifications.intelligence.rules.domain.interactor.ContactsInteractor
import com.android.systemui.notifications.intelligence.rules.domain.interactor.NotificationRulesInteractor
import com.android.systemui.notifications.intelligence.rules.shared.NotificationRulesLog
import com.android.systemui.notifications.intelligence.rules.shared.model.RuleModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

class NotificationRulesScreenViewModelImpl
@AssistedInject
constructor(
    @Assisted override val backStack: List<RulesScreenViewState>,
    private val interactor: NotificationRulesInteractor,
    private val contactsInteractor: ContactsInteractor,
    @NotificationRulesLog logBuffer: LogBuffer,
) : NotificationRulesScreenViewModel, HydratedActivatable() {
    private val logger = Logger(logBuffer, "ScreenViewModel")

    override val rules: List<RuleModel>
        get() = interactor.rules

    override val currentScreen: RulesScreenViewState
        get() = backStack[backStack.size - 1]

    override fun buildRuleText(rule: RuleModel, resources: Resources): RuleDisplayModel {
        return buildReadOnlyRuleText(rule, resources, logger)
    }

    override suspend fun loadContactBitmapFromUri(
        uri: Uri,
        userContext: Context,
        sizePx: Int,
    ): Bitmap? {
        return contactsInteractor.loadBitmapFromUri(uri, userContext, sizePx)
    }

    @AssistedFactory
    interface Factory : NotificationRulesScreenViewModel.Factory {
        override fun create(
            backStack: List<RulesScreenViewState>
        ): NotificationRulesScreenViewModelImpl
    }
}
