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

import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.notifications.intelligence.rules.domain.interactor.conversationPartnersInteractor
import com.android.systemui.notifications.intelligence.rules.domain.interactor.fakeContactsInteractor
import com.android.systemui.notifications.intelligence.rules.domain.interactor.fakeInstalledAppsInteractor
import com.android.systemui.notifications.intelligence.rules.domain.interactor.notificationRulesInteractor
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel
import com.android.systemui.notifications.intelligence.rules.shared.notificationRulesLogBuffer

val Kosmos.notificationRuleEditViewModelFactory by
    Kosmos.Fixture {
        object : NotificationRuleEditViewModel.Factory {
            override fun create(
                rule: DraftRuleModel,
                onNavigateToCurrentRulesScreen: () -> Unit,
            ): NotificationRuleEditViewModel {
                return NotificationRuleEditViewModelImpl(
                    rule,
                    onNavigateToCurrentRulesScreen,
                    notificationRulesInteractor,
                    fakeContactsInteractor,
                    conversationPartnersInteractor,
                    fakeInstalledAppsInteractor,
                    backgroundDispatcher = testDispatcher,
                    applicationCoroutineScope,
                    notificationRulesLogBuffer,
                )
            }
        }
    }
