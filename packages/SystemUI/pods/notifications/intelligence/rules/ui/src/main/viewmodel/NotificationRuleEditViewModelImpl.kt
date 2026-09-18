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

import android.annotation.Px
import android.content.ContentResolver
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.notifications.intelligence.rules.domain.interactor.ContactsInteractor
import com.android.systemui.notifications.intelligence.rules.domain.interactor.ConversationPartnersInteractor
import com.android.systemui.notifications.intelligence.rules.domain.interactor.InstalledAppsInteractor
import com.android.systemui.notifications.intelligence.rules.domain.interactor.NotificationRulesInteractor
import com.android.systemui.notifications.intelligence.rules.shared.NmContextualDisplayLaunch
import com.android.systemui.notifications.intelligence.rules.shared.NotificationRulesLog
import com.android.systemui.notifications.intelligence.rules.shared.model.AppModel
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel
import com.android.systemui.notifications.intelligence.rules.shared.model.IncludedAppsModel
import com.android.systemui.notifications.intelligence.rules.shared.model.KeywordsModel
import com.android.systemui.notifications.intelligence.rules.shared.model.PeopleModel
import com.android.systemui.notifications.intelligence.rules.shared.model.PersonModel
import com.android.systemui.notifications.intelligence.rules.shared.model.RuleValue
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationRuleEditViewModelImpl
@AssistedInject
constructor(
    @Assisted startingRule: DraftRuleModel,
    @Assisted private val onNavigateToCurrentRulesScreen: () -> Unit,
    private val rulesInteractor: NotificationRulesInteractor,
    private val contactsInteractor: ContactsInteractor,
    private val conversationPartnersInteractor: ConversationPartnersInteractor,
    private val installedAppsInteractor: InstalledAppsInteractor,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
    @Application private val applicationScope: CoroutineScope,
    @NotificationRulesLog logBuffer: LogBuffer,
) : NotificationRuleEditViewModel {
    private val logger = Logger(logBuffer, "EditViewModel")

    override var rule: DraftRuleModel by mutableStateOf(startingRule)

    override var isErrorVisible: Boolean by mutableStateOf(false)
        private set

    override fun buildRuleText(
        onEnterEditField: (RulesScreenViewState.EditField) -> Unit,
        onExitEditField: () -> Unit,
        resources: Resources,
    ): RuleDisplayModel {
        return buildEditableRuleText(
            this,
            onEnterEditField,
            onAppsSaved = { onAppsSaved(it, onExitEditField) },
            onPeopleSaved = { onPeopleSaved(it, onExitEditField) },
            onKeywordsSaved = { onKeywordsSaved(it, onExitEditField) },
            resources = resources,
            logger = logger,
        )
    }

    override fun onAppsSaved(newApps: List<AppModel>, onExitEditField: () -> Unit) {
        val newFilter =
            rule.filter.copy(
                includedApps =
                    if (newApps.isNotEmpty()) {
                        RuleValue.Specified(IncludedAppsModel(newApps))
                    } else {
                        // Saving with no selected apps is effectively removing apps from the
                        // filter.
                        null
                    }
            )
        rule = rule.copyDraft(filter = newFilter)
        onExitEditField()
    }

    override fun onPeopleSaved(newPeople: List<PersonModel>, onExitEditField: () -> Unit) {
        val newFilter =
            rule.filter.copy(
                people =
                    if (newPeople.isNotEmpty()) {
                        RuleValue.Specified(PeopleModel(newPeople))
                    } else {
                        // Saving with no selected contacts is effectively removing contacts from
                        // the filter.
                        null
                    }
            )
        rule = rule.copyDraft(filter = newFilter)
        onExitEditField()
    }

    override fun onKeywordsSaved(newKeywords: List<String>, onExitEditField: () -> Unit) {
        val newFilter =
            rule.filter.copy(
                keywords =
                    if (newKeywords.isNotEmpty()) {
                        KeywordsModel(newKeywords)
                    } else {
                        null
                    }
            )
        rule = rule.copyDraft(filter = newFilter)
        onExitEditField()
    }

    override suspend fun fetchPeople(
        searchQuery: String,
        contentResolver: ContentResolver,
    ): List<PersonModel> {
        if (!NmContextualDisplayLaunch.isEnabled) {
            return emptyList()
        }
        val contacts: Deferred<List<PersonModel.Contact>> =
            withContext(backgroundDispatcher) {
                async { contactsInteractor.fetchContacts(searchQuery, contentResolver) }
            }
        val conversationPartners: Deferred<List<PersonModel.ConversationPartner>> =
            withContext(backgroundDispatcher) {
                async {
                    conversationPartnersInteractor.fetchRecentConversationPartners(searchQuery)
                }
            }
        return buildList {
            addAll(contacts.await())
            addAll(conversationPartners.await())
        }
    }

    override suspend fun loadContactBitmapFromUri(
        uri: Uri,
        userContext: Context,
        @Px sizePx: Int,
    ): Bitmap? {
        return contactsInteractor.loadBitmapFromUri(uri, userContext, sizePx)
    }

    override suspend fun fetchInstalledApps(context: Context): List<AppModel> {
        return installedAppsInteractor.fetchInstalledApps(context)
    }

    override fun saveRule() {
        // Use application scope so it's never cancelled
        applicationScope.launch {
            val wasSavedSuccessfully = rulesInteractor.saveRule(rule)
            if (wasSavedSuccessfully) {
                onNavigateToCurrentRulesScreen()
            }
            isErrorVisible = !wasSavedSuccessfully
        }
    }

    override fun cleanUp() {
        // Stop showing the error whenever the user leaves the page.
        isErrorVisible = false
    }

    @AssistedFactory
    interface Factory : NotificationRuleEditViewModel.Factory {
        override fun create(
            rule: DraftRuleModel,
            onNavigateToCurrentRulesScreen: () -> Unit,
        ): NotificationRuleEditViewModelImpl
    }
}
