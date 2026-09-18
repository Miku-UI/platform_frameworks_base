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

import android.app.NotificationManager
import android.app.NotificationRule
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.annotation.MainThread
import androidx.compose.runtime.mutableStateListOf
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.notifications.intelligence.rules.data.repository.NotificationRuleConversionHelper.toInternalModel
import com.android.systemui.notifications.intelligence.rules.data.repository.NotificationRuleConversionHelper.validPrimaryActionValues
import com.android.systemui.notifications.intelligence.rules.data.repository.NotificationRuleToExternalHelpers.toExternalRuleFormat
import com.android.systemui.notifications.intelligence.rules.shared.NmContextualDisplayLaunch
import com.android.systemui.notifications.intelligence.rules.shared.NotificationRulesLog
import com.android.systemui.notifications.intelligence.rules.shared.model.ActionModel
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel.Companion.toFullRule
import com.android.systemui.notifications.intelligence.rules.shared.model.FilterModel
import com.android.systemui.notifications.intelligence.rules.shared.model.IncludedAppsModel
import com.android.systemui.notifications.intelligence.rules.shared.model.KeywordsModel
import com.android.systemui.notifications.intelligence.rules.shared.model.PeopleModel
import com.android.systemui.notifications.intelligence.rules.shared.model.ResponseModel
import com.android.systemui.notifications.intelligence.rules.shared.model.RuleModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SysUISingleton
class NotificationRulesRepositoryImpl
@Inject
constructor(
    private val notificationManager: NotificationManager,
    private val freeformRuleRepository: FreeformRuleRepository,
    private val installedAppsRepository: InstalledAppsRepository,
    private val contactsRepository: ContactsRepository,
    private val conversationPartnersRepository: ConversationPartnersRepository,
    private val contentResolver: ContentResolver,
    @Application private val applicationContext: Context,
    @Application private val applicationScope: CoroutineScope,
    @Main private val mainDispatcher: CoroutineDispatcher,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
    @NotificationRulesLog logBuffer: LogBuffer,
) : NotificationRulesRepository, CoreStartable {
    private val logger = Logger(logBuffer, "RulesRepository")

    override var rules = mutableStateListOf<RuleModel>()

    private val availableRuleIds = mutableStateListOf<Int>().apply { addAll(RULE_ID_RANGE) }

    override fun start() {
        if (!NmContextualDisplayLaunch.isEnabled) {
            return
        }

        applicationScope.launch {
            // TODO: b/478225883 & b/493539998 - Don't fetch the rules until private storage is
            // unlocked, since some information (like contact information) is only available after
            // first unlock.
            val initialRules = withContext(backgroundDispatcher) { fetchInitialRules() }
            val initialRuleIds = initialRules.map { it.id }

            // Modify `rules` on the main thread so order is guaranteed.
            rules.clear()
            rules.addAll(initialRules)

            availableRuleIds.removeAll(initialRuleIds)
        }
    }

    private suspend fun fetchInitialRules(): List<RuleModel> {
        return notificationManager.notificationRules
            .filter {
                val isValidAction = validPrimaryActionValues.contains(it.action.primaryAction)
                if (!isValidAction) {
                    logger.w({ "Filtering out invalid action $int1" }) {
                        int1 = it.action.primaryAction
                    }
                }
                isValidAction
            }
            .map { it.toInternalModel() }
    }

    override suspend fun createDraftRuleFromFreeformText(
        action: ActionModel,
        text: String,
    ): ResponseModel<DraftRuleModel> {
        return freeformRuleRepository.createDraftRuleFromFreeformText(action, text)
    }

    override suspend fun saveRule(rule: DraftRuleModel): Boolean {
        NmContextualDisplayLaunch.expectInNewMode()

        return when (rule) {
            is DraftRuleModel.New -> createNewRule(rule)
            is DraftRuleModel.PreExisting -> updateExistingRule(rule)
        }
    }

    private suspend fun createNewRule(newRule: DraftRuleModel.New): Boolean {
        val position = 0
        val formedRule = newRule.toFullRule(id = generateIdForNewRule())
        val externalRule = formedRule.toExternalRuleFormat()
        val savedRule =
            withContext(backgroundDispatcher) {
                val createdRule = notificationManager.addNotificationRule(externalRule, position)
                createdRule?.toInternalModel()
            }

        if (savedRule != null) {
            withContext(mainDispatcher) {
                // Always modify rules list & IDs list on main thread
                rules.add(position, savedRule)
                availableRuleIds.remove(savedRule.id)
            }
        }
        return savedRule != null
    }

    private suspend fun updateExistingRule(updatedRule: DraftRuleModel.PreExisting): Boolean {
        val formedRule = updatedRule.toFullRule()
        val externalRule = formedRule.toExternalRuleFormat()
        val savedRule =
            withContext(backgroundDispatcher) {
                val updatedRule = notificationManager.updateNotificationRule(externalRule)
                updatedRule?.toInternalModel()
            }

        if (savedRule != null) {
            withContext(mainDispatcher) {
                // Always modify rules list on main thread
                val existingRuleIndex = rules.indexOfFirst { it.id == savedRule.id }
                rules[existingRuleIndex] = savedRule
            }
        }
        return savedRule != null
    }

    @MainThread // Keep on the main thread so that two rules can't take the same ID
    private fun generateIdForNewRule(): Int {
        if (availableRuleIds.isEmpty()) {
            // TODO: b/478225883 - In the UI, don't allow a user to start creating a rule if they're
            //  already maxed out.
            throw IllegalStateException("All rule slots are already taken")
        }
        return availableRuleIds[0]
    }

    private suspend fun NotificationRule.toInternalModel(): RuleModel {
        return RuleModel(
            id = this.id,
            action = this.action.toInternalModel(),
            filter =
                if (this.filters.isNotEmpty()) {
                    // TODO: b/478225883 - Parse all the filters, not just the first one.
                    this.filters[0].toInternalModel()
                } else {
                    null
                },
        )
    }

    private suspend fun NotificationRule.Filter.toInternalModel(): FilterModel? {
        val filterModel =
            FilterModel(
                people =
                    createPeopleModel(
                        contacts = this.contacts,
                        conversationPartnerIds = this.shortcutIds,
                    ),
                includedApps = this.includedPackageUids.toIncludedAppsModel(),
                keywords = this.keywords.toKeywordsModel(),
            )
        return if (filterModel.hasContent) {
            filterModel
        } else {
            null
        }
    }

    private suspend fun createPeopleModel(
        contacts: List<Uri>,
        conversationPartnerIds: List<String>,
    ): PeopleModel? {
        val contacts = contacts.mapNotNull { contactsRepository.lookupContact(it, contentResolver) }
        val conversationPartners =
            conversationPartnerIds.mapNotNull {
                conversationPartnersRepository.lookupConversationPartner(it)
            }
        if (contacts.isEmpty() && conversationPartners.isEmpty()) {
            return null
        }
        return PeopleModel(contacts + conversationPartners)
    }

    private suspend fun List<Int>.toIncludedAppsModel(): IncludedAppsModel? {
        val includedApps =
            this.mapNotNull { installedAppsRepository.lookupApp(it, applicationContext) }
        if (includedApps.isEmpty()) {
            return null
        }
        return IncludedAppsModel(includedApps)
    }

    private fun List<String>.toKeywordsModel(): KeywordsModel? {
        return if (this.isNotEmpty()) {
            KeywordsModel(this)
        } else {
            null
        }
    }

    companion object {
        private val RULE_ID_RANGE = 100..125
    }
}
