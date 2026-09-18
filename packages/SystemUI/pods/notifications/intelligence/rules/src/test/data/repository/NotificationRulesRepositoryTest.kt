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

import android.annotation.FlaggedApi
import android.app.NotificationRule
import android.app.NotificationRule.Action.PRIMARY_ACTION_BLOCK
import android.app.NotificationRule.Action.PRIMARY_ACTION_BUNDLE
import android.app.NotificationRule.Action.PRIMARY_ACTION_HIGHLIGHT
import android.app.NotificationRule.Action.PRIMARY_ACTION_HIGHLIGHT_AND_ALERT
import android.app.NotificationRule.Action.PRIMARY_ACTION_LOW
import android.app.notificationManager
import android.graphics.drawable.Icon
import android.graphics.drawable.ShapeDrawable
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.log.assertLogsWtf
import com.android.systemui.notifications.intelligence.rules.shared.NmContextualDisplayLaunch
import com.android.systemui.notifications.intelligence.rules.shared.model.ActionModel
import com.android.systemui.notifications.intelligence.rules.shared.model.AppModel
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftFilterModel
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel
import com.android.systemui.notifications.intelligence.rules.shared.model.IncludedAppsModel
import com.android.systemui.notifications.intelligence.rules.shared.model.KeywordsModel
import com.android.systemui.notifications.intelligence.rules.shared.model.PeopleModel
import com.android.systemui.notifications.intelligence.rules.shared.model.PersonModel
import com.android.systemui.notifications.intelligence.rules.shared.model.ResponseModel
import com.android.systemui.notifications.intelligence.rules.shared.model.RuleValue
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@FlaggedApi(NmContextualDisplayLaunch.FLAG_NAME)
class NotificationRulesRepositoryTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    private val underTest = kosmos.realNotificationRulesRepository

    @Before
    fun setUp() {
        kosmos.fakeContactsRepository.contacts = listOf(FAKE_CONTACT_1, FAKE_CONTACT_2)
        kosmos.fakeInstalledAppsRepository.installedApps = listOf(FAKE_APP)
        kosmos.fakeConversationPartnersRepository.conversationPartners =
            listOf(FAKE_CONVERSATION_PARTNER_1, FAKE_CONVERSATION_PARTNER_2)

        // By default, return the rule that was passed in
        whenever(kosmos.notificationManager.addNotificationRule(any(), any())).thenAnswer {
            invocation ->
            invocation.arguments[0]
        }
        whenever(kosmos.notificationManager.updateNotificationRule(any())).thenAnswer { invocation
            ->
            invocation.arguments[0]
        }
    }

    @Test
    fun createDraftRuleFromFreeformText_isNewAndHasAction() =
        kosmos.runTest {
            val result =
                underTest.createDraftRuleFromFreeformText(
                    action = ActionModel.Block,
                    text = "sample text",
                )

            assertThat(result).isInstanceOf(ResponseModel.Success::class.java)
            assertThat((result as ResponseModel.Success<DraftRuleModel>).draftRule)
                .isInstanceOf(DraftRuleModel.New::class.java)
            assertThat((result as ResponseModel.Success<DraftRuleModel>).draftRule.action)
                .isEqualTo(ActionModel.Block)
        }

    @Test
    @DisableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun fetchInitialRules_flagOff_isEmpty() =
        kosmos.runTest {
            val rule = createRule(id = 23)
            putRulesIntoNotificationManager(listOf(rule))

            underTest.start()
            val result = underTest.rules

            assertThat(result).isEmpty()
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun fetchInitialRules_id() =
        kosmos.runTest {
            val rule = createRule(id = 23)
            putRulesIntoNotificationManager(listOf(rule))

            underTest.start()
            val result = underTest.rules

            assertThat(result).hasSize(1)
            assertThat(result[0].id).isEqualTo(23)
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun fetchInitialRules_unknownAction_filteredOut() =
        kosmos.runTest {
            val rule = createRule(action = NotificationRule.Action.Builder(100).build())
            putRulesIntoNotificationManager(listOf(rule))

            underTest.start()
            val result = underTest.rules

            assertThat(result).isEmpty()
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun fetchInitialRules_highlightAndAlertAction() =
        kosmos.runTest {
            val rule =
                createRule(
                    action =
                        NotificationRule.Action.Builder(PRIMARY_ACTION_HIGHLIGHT_AND_ALERT).build()
                )
            putRulesIntoNotificationManager(listOf(rule))

            underTest.start()
            val result = underTest.rules

            assertThat(result).hasSize(1)
            assertThat(result[0].action).isEqualTo(ActionModel.HighlightAndAlert)
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun fetchInitialRules_highlightAction() =
        kosmos.runTest {
            val rule =
                createRule(
                    action = NotificationRule.Action.Builder(PRIMARY_ACTION_HIGHLIGHT).build()
                )
            putRulesIntoNotificationManager(listOf(rule))

            underTest.start()
            val result = underTest.rules

            assertThat(result).hasSize(1)
            assertThat(result[0].action).isEqualTo(ActionModel.Highlight)
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun fetchInitialRules_silenceAction() =
        kosmos.runTest {
            val rule =
                createRule(action = NotificationRule.Action.Builder(PRIMARY_ACTION_LOW).build())
            putRulesIntoNotificationManager(listOf(rule))

            underTest.start()
            val result = underTest.rules

            assertThat(result).hasSize(1)
            assertThat(result[0].action).isEqualTo(ActionModel.Silence)
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun fetchInitialRules_bundleAction_noNameOrEmoji() =
        kosmos.runTest {
            val rule =
                createRule(action = NotificationRule.Action.Builder(PRIMARY_ACTION_BUNDLE).build())
            putRulesIntoNotificationManager(listOf(rule))

            underTest.start()
            val result = underTest.rules

            assertThat(result).hasSize(1)
            assertThat(result[0].action)
                .isEqualTo(ActionModel.Bundle(name = null, emojiIcon = null))
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun fetchInitialRules_bundleAction_withNameAndEmoji() =
        kosmos.runTest {
            val rule =
                createRule(
                    action =
                        NotificationRule.Action.Builder(PRIMARY_ACTION_BUNDLE)
                            .setDynamicBundleName("Dynamic Name")
                            .setDynamicBundleEmojiIcon("\uD83D\uDCE6")
                            .build()
                )
            putRulesIntoNotificationManager(listOf(rule))

            underTest.start()
            val result = underTest.rules

            assertThat(result).hasSize(1)
            assertThat(result[0].action)
                .isEqualTo(ActionModel.Bundle(name = "Dynamic Name", emojiIcon = "\uD83D\uDCE6"))
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun fetchInitialRules_blockAction() =
        kosmos.runTest {
            val rule =
                createRule(action = NotificationRule.Action.Builder(PRIMARY_ACTION_BLOCK).build())
            putRulesIntoNotificationManager(listOf(rule))

            underTest.start()
            val result = underTest.rules

            assertThat(result).hasSize(1)
            assertThat(result[0].action).isEqualTo(ActionModel.Block)
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun fetchInitialRules_noFilters_filterIsNull() =
        kosmos.runTest {
            val rule =
                createRule(action = NotificationRule.Action.Builder(PRIMARY_ACTION_BLOCK).build())
            putRulesIntoNotificationManager(listOf(rule))

            underTest.start()
            val result = underTest.rules

            assertThat(result).hasSize(1)
            assertThat(result[0].filter).isNull()
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun fetchInitialRules_emptyFilter_filterIsNull() =
        kosmos.runTest {
            val rule =
                NotificationRule.Builder(
                        300,
                        NotificationRule.Action.Builder(PRIMARY_ACTION_BLOCK).build(),
                    )
                    .addFilter(NotificationRule.Filter.Builder().build())
                    .build()
            putRulesIntoNotificationManager(listOf(rule))

            underTest.start()
            val result = underTest.rules

            assertThat(result).hasSize(1)
            assertThat(result[0].filter).isNull()
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun fetchInitialRules_keywords() =
        kosmos.runTest {
            val filter =
                NotificationRule.Filter.Builder()
                    .apply {
                        addKeyword("example1")
                        addKeyword("example2")
                    }
                    .build()
            val rule =
                NotificationRule.Builder(
                        300,
                        NotificationRule.Action.Builder(PRIMARY_ACTION_BLOCK).build(),
                    )
                    .addFilter(filter)
                    .build()
            putRulesIntoNotificationManager(listOf(rule))

            underTest.start()
            val result = underTest.rules

            assertThat(result).hasSize(1)
            assertThat(result[0].filter).isNotNull()
            assertThat(result[0].filter!!.keywords)
                .isEqualTo(KeywordsModel(listOf("example1", "example2")))
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun fetchInitialRules_contacts_hasOnlyFoundContacts() =
        kosmos.runTest {
            val filter =
                NotificationRule.Filter.Builder()
                    .apply {
                        addContact(CONTACT_1_URI)
                        addContact(CONTACT_2_URI)
                        addContact("unknownContact".toUri())
                    }
                    .build()
            val rule =
                NotificationRule.Builder(
                        300,
                        NotificationRule.Action.Builder(PRIMARY_ACTION_BLOCK).build(),
                    )
                    .addFilter(filter)
                    .build()
            putRulesIntoNotificationManager(listOf(rule))

            underTest.start()
            val result = underTest.rules

            assertThat(result).hasSize(1)
            assertThat(result[0].filter).isNotNull()
            assertThat(result[0].filter!!.people!!.people)
                .containsExactly(FAKE_CONTACT_1, FAKE_CONTACT_2)
                .inOrder()
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun fetchInitialRules_shortcuts_hasOnlyFoundShortcuts() =
        kosmos.runTest {
            val filter =
                NotificationRule.Filter.Builder()
                    .apply {
                        addShortcutId(FAKE_CONVERSATION_ID_1)
                        addShortcutId("unknownShortcutId")
                        addShortcutId(FAKE_CONVERSATION_ID_2)
                    }
                    .build()
            val rule =
                NotificationRule.Builder(
                        300,
                        NotificationRule.Action.Builder(PRIMARY_ACTION_BLOCK).build(),
                    )
                    .addFilter(filter)
                    .build()
            putRulesIntoNotificationManager(listOf(rule))

            underTest.start()
            val result = underTest.rules

            assertThat(result).hasSize(1)
            assertThat(result[0].filter).isNotNull()
            assertThat(result[0].filter!!.people!!.people)
                .containsExactly(FAKE_CONVERSATION_PARTNER_1, FAKE_CONVERSATION_PARTNER_2)
                .inOrder()
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun fetchInitialRules_contactsAndShortcuts() =
        kosmos.runTest {
            val filter =
                NotificationRule.Filter.Builder()
                    .apply {
                        addShortcutId(FAKE_CONVERSATION_ID_1)
                        addShortcutId(FAKE_CONVERSATION_ID_2)
                        addContact(CONTACT_1_URI)
                    }
                    .build()
            val rule =
                NotificationRule.Builder(
                        300,
                        NotificationRule.Action.Builder(PRIMARY_ACTION_BLOCK).build(),
                    )
                    .addFilter(filter)
                    .build()
            putRulesIntoNotificationManager(listOf(rule))

            underTest.start()
            val result = underTest.rules

            assertThat(result).hasSize(1)
            assertThat(result[0].filter).isNotNull()
            assertThat(result[0].filter!!.people!!.people)
                .containsExactly(
                    FAKE_CONTACT_1,
                    FAKE_CONVERSATION_PARTNER_1,
                    FAKE_CONVERSATION_PARTNER_2,
                )
                .inOrder()
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun fetchInitialRules_includedApps_hasOnlyFoundApps() =
        kosmos.runTest {
            val filter =
                NotificationRule.Filter.Builder()
                    .apply {
                        addIncludedPackageUid(FAKE_APP_UID)
                        addIncludedPackageUid(FAKE_APP_UID + 1)
                    }
                    .build()
            val rule =
                NotificationRule.Builder(
                        300,
                        NotificationRule.Action.Builder(PRIMARY_ACTION_HIGHLIGHT).build(),
                    )
                    .addFilter(filter)
                    .build()
            putRulesIntoNotificationManager(listOf(rule))

            underTest.start()
            val result = underTest.rules

            assertThat(result).hasSize(1)
            assertThat(result[0].filter).isNotNull()
            assertThat(result[0].filter!!.includedApps!!.apps).containsExactly(FAKE_APP)
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun fetchInitialRules_noKeywords_isNull() =
        kosmos.runTest {
            // WHEN there's apps & contacts but no keywords
            val filter =
                NotificationRule.Filter.Builder()
                    .apply {
                        addContact(CONTACT_1_URI)
                        addIncludedPackageUid(FAKE_APP_UID)
                    }
                    .build()
            val rule =
                NotificationRule.Builder(
                        300,
                        NotificationRule.Action.Builder(PRIMARY_ACTION_HIGHLIGHT).build(),
                    )
                    .addFilter(filter)
                    .build()
            putRulesIntoNotificationManager(listOf(rule))

            underTest.start()
            val result = underTest.rules

            assertThat(result).hasSize(1)
            // THEN keywords is null
            assertThat(result[0].filter!!.keywords).isNull()
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun fetchInitialRules_noContactsOrShortcuts_peopleIsNull() =
        kosmos.runTest {
            // WHEN there's apps but not contacts or shortcuts
            val filter =
                NotificationRule.Filter.Builder()
                    .apply { addIncludedPackageUid(FAKE_APP_UID) }
                    .build()
            val rule =
                NotificationRule.Builder(
                        300,
                        NotificationRule.Action.Builder(PRIMARY_ACTION_HIGHLIGHT).build(),
                    )
                    .addFilter(filter)
                    .build()
            putRulesIntoNotificationManager(listOf(rule))

            underTest.start()
            val result = underTest.rules

            assertThat(result).hasSize(1)
            // THEN people is null
            assertThat(result[0].filter!!.people).isNull()
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun fetchInitialRules_noIncludedApps_isNull() =
        kosmos.runTest {
            // WHEN there's contacts but not apps
            val filter =
                NotificationRule.Filter.Builder().apply { addContact(CONTACT_1_URI) }.build()
            val rule =
                NotificationRule.Builder(
                        300,
                        NotificationRule.Action.Builder(PRIMARY_ACTION_BLOCK).build(),
                    )
                    .addFilter(filter)
                    .build()
            putRulesIntoNotificationManager(listOf(rule))

            underTest.start()
            val result = underTest.rules

            assertThat(result).hasSize(1)
            // THEN included apps are null
            assertThat(result[0].filter!!.includedApps).isNull()
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun fetchInitialRules_multipleRules_inOrder() =
        kosmos.runTest {
            putRulesIntoNotificationManager(
                listOf(createRule(id = 12), createRule(id = 34), createRule(id = 56))
            )

            underTest.start()
            val result = underTest.rules

            assertThat(result).hasSize(3)
            assertThat(result[0].id).isEqualTo(12)
            assertThat(result[1].id).isEqualTo(34)
            assertThat(result[2].id).isEqualTo(56)
        }

    @Test
    @DisableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun saveRule_flagDisabled_logsWtf() =
        kosmos.runTest {
            underTest.start()

            val draftRule =
                DraftRuleModel.New(action = ActionModel.Silence, filter = DraftFilterModel())

            assertLogsWtf { runBlocking { underTest.saveRule(draftRule) } }
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun saveRule_newRule_savesAndAddsToList() =
        kosmos.runTest {
            underTest.start()

            val draftRule =
                DraftRuleModel.New(
                    action = ActionModel.Silence,
                    filter =
                        DraftFilterModel(
                            people =
                                RuleValue.Specified(
                                    PeopleModel(
                                        listOf(
                                            FAKE_CONTACT_1,
                                            FAKE_CONTACT_2,
                                            FAKE_CONVERSATION_PARTNER_1,
                                        )
                                    )
                                ),
                            includedApps = RuleValue.Specified(IncludedAppsModel(listOf(FAKE_APP))),
                            keywords = KeywordsModel(listOf("dog")),
                        ),
                )

            testScope.launch { underTest.saveRule(draftRule) }

            val expectedRule =
                NotificationRule.Builder(
                        100,
                        NotificationRule.Action.Builder(PRIMARY_ACTION_LOW).build(),
                    )
                    .addFilter(
                        NotificationRule.Filter.Builder()
                            .addContact(CONTACT_1_URI)
                            .addContact(CONTACT_2_URI)
                            .addShortcutId(FAKE_CONVERSATION_ID_1)
                            .addIncludedPackageUid(FAKE_APP_UID)
                            .addKeyword("dog")
                            .build()
                    )
                    .build()
            verify(notificationManager).addNotificationRule(expectedRule, 0)

            assertThat(underTest.rules).hasSize(1)
            val savedRule = underTest.rules[0]
            assertThat(savedRule.id).isEqualTo(100)
            assertThat(savedRule.action).isEqualTo(ActionModel.Silence)
            assertThat(savedRule.filter).isNotNull()
            assertThat(savedRule.filter!!.people)
                .isEqualTo(
                    PeopleModel(listOf(FAKE_CONTACT_1, FAKE_CONTACT_2, FAKE_CONVERSATION_PARTNER_1))
                )
            assertThat(savedRule.filter!!.includedApps)
                .isEqualTo(IncludedAppsModel(listOf(FAKE_APP)))
            assertThat(savedRule.filter!!.keywords).isEqualTo(KeywordsModel(listOf("dog")))
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun saveRule_preExistingRule_updatesAndReplaces() =
        kosmos.runTest {
            putRulesIntoNotificationManager(listOf(createRule(id = 12), createRule(id = 34)))
            underTest.start()

            val updatedRuleDraft =
                DraftRuleModel.PreExisting(
                    id = 34,
                    action = ActionModel.Block,
                    filter =
                        DraftFilterModel(
                            people =
                                RuleValue.Specified(
                                    PeopleModel(listOf(FAKE_CONTACT_1, FAKE_CONTACT_2))
                                ),
                            includedApps = RuleValue.Specified(IncludedAppsModel(listOf(FAKE_APP))),
                            keywords = KeywordsModel(listOf("dog")),
                        ),
                )

            testScope.launch { underTest.saveRule(updatedRuleDraft) }

            val expectedRule =
                NotificationRule.Builder(
                        34,
                        NotificationRule.Action.Builder(PRIMARY_ACTION_BLOCK).build(),
                    )
                    .addFilter(
                        NotificationRule.Filter.Builder()
                            .addContact(CONTACT_1_URI)
                            .addContact(CONTACT_2_URI)
                            .addIncludedPackageUid(FAKE_APP_UID)
                            .addKeyword("dog")
                            .build()
                    )
                    .build()
            verify(notificationManager).updateNotificationRule(expectedRule)

            assertThat(underTest.rules).hasSize(2)
            assertThat(underTest.rules[0].id).isEqualTo(12)
            val savedRule = underTest.rules[1]
            assertThat(savedRule.id).isEqualTo(34)
            assertThat(savedRule.action).isEqualTo(ActionModel.Block)
            assertThat(savedRule.filter).isNotNull()
            assertThat(savedRule.filter!!.people)
                .isEqualTo(PeopleModel(listOf(FAKE_CONTACT_1, FAKE_CONTACT_2)))
            assertThat(savedRule.filter!!.includedApps)
                .isEqualTo(IncludedAppsModel(listOf(FAKE_APP)))
            assertThat(savedRule.filter!!.keywords).isEqualTo(KeywordsModel(listOf("dog")))
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun saveRule_withBundleAction() =
        kosmos.runTest {
            underTest.start()

            val draftRule =
                DraftRuleModel.New(
                    action =
                        ActionModel.Bundle(name = "Dynamic Bundle", emojiIcon = "\uD83D\uDCE6"),
                    filter =
                        DraftFilterModel(
                            people = null,
                            includedApps = RuleValue.Specified(IncludedAppsModel(listOf(FAKE_APP))),
                            keywords = null,
                        ),
                )

            testScope.launch { underTest.saveRule(draftRule) }

            val expectedRule =
                NotificationRule.Builder(
                        100,
                        NotificationRule.Action.Builder(PRIMARY_ACTION_BUNDLE)
                            .setDynamicBundleName("Dynamic Bundle")
                            .setDynamicBundleEmojiIcon("\uD83D\uDCE6")
                            .build(),
                    )
                    .addFilter(
                        NotificationRule.Filter.Builder()
                            .addIncludedPackageUid(FAKE_APP_UID)
                            .build()
                    )
                    .build()
            verify(notificationManager).addNotificationRule(expectedRule, 0)

            assertThat(underTest.rules).hasSize(1)
            val savedRule = underTest.rules[0]
            assertThat(savedRule.id).isEqualTo(100)
            assertThat(savedRule.action)
                .isEqualTo(ActionModel.Bundle(name = "Dynamic Bundle", emojiIcon = "\uD83D\uDCE6"))
            assertThat(savedRule.filter).isNotNull()
            assertThat(savedRule.filter!!.includedApps)
                .isEqualTo(IncludedAppsModel(listOf(FAKE_APP)))
        }

    // This should never happen, but if NotificationManager returns a different rule than
    // what was passed in, we should treat NotificationManager as the source of truth
    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun saveRule_newRule_usesReturnedRuleValues() =
        kosmos.runTest {
            underTest.start()

            // WHEN the rule sent to NotificationManager has contacts...
            val newRuleDraft =
                DraftRuleModel.New(
                    action = ActionModel.Block,
                    filter =
                        DraftFilterModel(
                            people =
                                RuleValue.Specified(
                                    PeopleModel(listOf(FAKE_CONTACT_1, FAKE_CONTACT_2))
                                )
                        ),
                )

            // ... BUT the rule returned from NotificationManager has apps instead
            val returnedRule =
                NotificationRule.Builder(
                        34,
                        NotificationRule.Action.Builder(PRIMARY_ACTION_BLOCK).build(),
                    )
                    .addFilter(
                        NotificationRule.Filter.Builder()
                            .addIncludedPackageUid(FAKE_APP_UID)
                            .build()
                    )
                    .build()
            whenever(kosmos.notificationManager.addNotificationRule(any(), any())).thenAnswer { _ ->
                returnedRule
            }

            testScope.launch { underTest.saveRule(newRuleDraft) }

            verify(notificationManager).addNotificationRule(any(), any())

            assertThat(underTest.rules).hasSize(1)
            val savedRule = underTest.rules[0]
            assertThat(savedRule.id).isEqualTo(34)
            assertThat(savedRule.filter).isNotNull()

            // THEN the returned rule values (with just apps) are used
            assertThat(savedRule.filter!!.people).isNull()
            assertThat(savedRule.filter!!.includedApps)
                .isEqualTo(IncludedAppsModel(listOf(FAKE_APP)))
        }

    // This should never happen, but if NotificationManager returns a different rule than
    // what was passed in, we should treat NotificationManager as the source of truth
    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun saveRule_preExistingRule_usesReturnedRuleValues() =
        kosmos.runTest {
            putRulesIntoNotificationManager(listOf(createRule(id = 12), createRule(id = 34)))
            underTest.start()

            // WHEN the rule sent to NotificationManager has contacts...
            val updatedRuleDraft =
                DraftRuleModel.PreExisting(
                    id = 34,
                    action = ActionModel.Block,
                    filter =
                        DraftFilterModel(
                            people =
                                RuleValue.Specified(
                                    PeopleModel(listOf(FAKE_CONTACT_1, FAKE_CONTACT_2))
                                )
                        ),
                )

            // ... BUT the rule returned from NotificationManager has apps instead
            val returnedRule =
                NotificationRule.Builder(
                        34,
                        NotificationRule.Action.Builder(PRIMARY_ACTION_BLOCK).build(),
                    )
                    .addFilter(
                        NotificationRule.Filter.Builder()
                            .addIncludedPackageUid(FAKE_APP_UID)
                            .build()
                    )
                    .build()
            whenever(kosmos.notificationManager.updateNotificationRule(any())).thenAnswer { _ ->
                returnedRule
            }

            testScope.launch { underTest.saveRule(updatedRuleDraft) }

            verify(notificationManager).updateNotificationRule(any())

            assertThat(underTest.rules).hasSize(2)
            assertThat(underTest.rules[0].id).isEqualTo(12)
            val savedRule = underTest.rules[1]
            assertThat(savedRule.id).isEqualTo(34)
            assertThat(savedRule.action).isEqualTo(ActionModel.Block)
            assertThat(savedRule.filter).isNotNull()

            // THEN returned rule values (with just apps) are used
            assertThat(savedRule.filter!!.people).isNull()
            assertThat(savedRule.filter!!.includedApps)
                .isEqualTo(IncludedAppsModel(listOf(FAKE_APP)))
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun saveRule_newRules_addedToFront_withIncreasingIds() =
        kosmos.runTest {
            testScope.launch {
                underTest.saveRule(
                    DraftRuleModel.New(ActionModel.HighlightAndAlert, filter = DraftFilterModel())
                )
            }
            testScope.launch {
                underTest.saveRule(
                    DraftRuleModel.New(ActionModel.Highlight, filter = DraftFilterModel())
                )
            }
            testScope.launch {
                underTest.saveRule(
                    DraftRuleModel.New(ActionModel.Silence, filter = DraftFilterModel())
                )
            }

            verify(notificationManager)
                .addNotificationRule(
                    NotificationRule.Builder(
                            100,
                            NotificationRule.Action.Builder(PRIMARY_ACTION_HIGHLIGHT_AND_ALERT)
                                .build(),
                        )
                        .build(),
                    0,
                )
            verify(notificationManager)
                .addNotificationRule(
                    NotificationRule.Builder(
                            101,
                            NotificationRule.Action.Builder(PRIMARY_ACTION_HIGHLIGHT).build(),
                        )
                        .build(),
                    0,
                )
            verify(notificationManager)
                .addNotificationRule(
                    NotificationRule.Builder(
                            102,
                            NotificationRule.Action.Builder(PRIMARY_ACTION_LOW).build(),
                        )
                        .build(),
                    0,
                )

            assertThat(underTest.rules).hasSize(3)
            assertThat(underTest.rules[0].id).isEqualTo(102)
            assertThat(underTest.rules[1].id).isEqualTo(101)
            assertThat(underTest.rules[2].id).isEqualTo(100)

            assertThat(underTest.rules[0].action).isEqualTo(ActionModel.Silence)
            assertThat(underTest.rules[1].action).isEqualTo(ActionModel.Highlight)
            assertThat(underTest.rules[2].action).isEqualTo(ActionModel.HighlightAndAlert)
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun saveRule_newRule_usesFirstAvailableId() =
        kosmos.runTest {
            putRulesIntoNotificationManager(
                listOf(createRule(id = 100), createRule(id = 101), createRule(id = 103))
            )
            underTest.start()

            val draftRule =
                DraftRuleModel.New(action = ActionModel.Silence, filter = DraftFilterModel())
            testScope.launch { underTest.saveRule(draftRule) }

            // 102 is the first available ID
            val expectedRule =
                NotificationRule.Builder(
                        102,
                        NotificationRule.Action.Builder(PRIMARY_ACTION_LOW).build(),
                    )
                    .build()
            verify(notificationManager).addNotificationRule(expectedRule, 0)

            assertThat(underTest.rules).hasSize(4)
            assertThat(underTest.rules[0].id).isEqualTo(102)

            // Now 104 is the first available ID
            val secondDraftRule =
                DraftRuleModel.New(
                    action = ActionModel.HighlightAndAlert,
                    filter = DraftFilterModel(),
                )
            testScope.launch { underTest.saveRule(secondDraftRule) }

            val expectedSecondRule =
                NotificationRule.Builder(
                        104,
                        NotificationRule.Action.Builder(PRIMARY_ACTION_HIGHLIGHT_AND_ALERT).build(),
                    )
                    .build()
            verify(notificationManager).addNotificationRule(expectedSecondRule, 0)

            assertThat(underTest.rules).hasSize(5)
            assertThat(underTest.rules[0].id).isEqualTo(104)
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun saveRule_newRuleSaveFails_nextNewRuleGetsFirstAvailableId() =
        kosmos.runTest {
            putRulesIntoNotificationManager(
                listOf(createRule(id = 100), createRule(id = 101), createRule(id = 105))
            )
            underTest.start()

            val draftRule =
                DraftRuleModel.New(action = ActionModel.Silence, filter = DraftFilterModel())

            // First attempt to save a draft rule: Fails
            whenever(notificationManager.addNotificationRule(any(), any())).thenReturn(null)
            testScope.launch { underTest.saveRule(draftRule) }
            assertThat(underTest.rules).hasSize(3)

            // Second attempt to save a draft rule: Succeeds
            val newDraftRule =
                DraftRuleModel.New(action = ActionModel.Block, filter = DraftFilterModel())
            whenever(kosmos.notificationManager.addNotificationRule(any(), any())).thenAnswer {
                invocation ->
                invocation.arguments[0]
            }
            testScope.launch { underTest.saveRule(newDraftRule) }

            // The second rule should still get ID 102 since the first rule never successfully saved
            val expectedRule =
                NotificationRule.Builder(
                        102,
                        NotificationRule.Action.Builder(PRIMARY_ACTION_BLOCK).build(),
                    )
                    .build()
            verify(notificationManager).addNotificationRule(expectedRule, 0)

            assertThat(underTest.rules).hasSize(4)
            assertThat(underTest.rules[0].id).isEqualTo(102)
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun saveRule_newRule_allIdsTaken_throws() =
        kosmos.runTest {
            val rules = (100..125).map { createRule(id = it) }
            putRulesIntoNotificationManager(rules)
            underTest.start()

            val draftRule =
                DraftRuleModel.New(action = ActionModel.Silence, filter = DraftFilterModel())

            assertThrows(IllegalStateException::class.java) {
                runBlocking { underTest.saveRule(draftRule) }
            }
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun saveRule_someNullFilterValues() =
        kosmos.runTest {
            putRulesIntoNotificationManager(listOf(createRule(id = 34)))
            underTest.start()

            val updatedRuleDraft =
                DraftRuleModel.PreExisting(
                    id = 34,
                    action = ActionModel.Silence,
                    filter =
                        DraftFilterModel(
                            people = null,
                            includedApps = RuleValue.Specified(IncludedAppsModel(listOf(FAKE_APP))),
                            keywords = null,
                        ),
                )

            testScope.launch { underTest.saveRule(updatedRuleDraft) }

            val expectedRule =
                NotificationRule.Builder(
                        34,
                        NotificationRule.Action.Builder(PRIMARY_ACTION_LOW).build(),
                    )
                    .addFilter(
                        NotificationRule.Filter.Builder()
                            .addIncludedPackageUid(FAKE_APP_UID)
                            .build()
                    )
                    .build()
            verify(notificationManager).updateNotificationRule(expectedRule)
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun saveRule_noFilterValues() =
        kosmos.runTest {
            underTest.start()

            val updatedRuleDraft =
                DraftRuleModel.New(
                    action = ActionModel.Highlight,
                    filter = DraftFilterModel(people = null, includedApps = null, keywords = null),
                )

            testScope.launch { underTest.saveRule(updatedRuleDraft) }

            val expectedRule =
                NotificationRule.Builder(
                        100,
                        NotificationRule.Action.Builder(PRIMARY_ACTION_HIGHLIGHT).build(),
                    )
                    .build()
            verify(notificationManager).addNotificationRule(expectedRule, 0)
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun saveRule_newRule_notificationManagerReturnsNull_notAdded() =
        kosmos.runTest {
            whenever(notificationManager.addNotificationRule(any(), any())).thenReturn(null)

            val draftRule =
                DraftRuleModel.New(
                    action = ActionModel.Silence,
                    filter =
                        DraftFilterModel(
                            includedApps = RuleValue.Specified(IncludedAppsModel(listOf(FAKE_APP)))
                        ),
                )

            testScope.launch { underTest.saveRule(draftRule) }

            // Verify the rule was sent to the manager, but we didn't update the internal list
            verify(notificationManager).addNotificationRule(any(), eq(0))
            assertThat(underTest.rules).isEmpty()
        }

    @Test
    @EnableFlags(NmContextualDisplayLaunch.FLAG_NAME)
    fun saveRule_preExistingRule_notificationManagerReturnsNull_notUpdated() =
        kosmos.runTest {
            whenever(notificationManager.updateNotificationRule(any())).thenReturn(null)

            putRulesIntoNotificationManager(listOf(createRule(id = 12), createRule(id = 34)))
            underTest.start()

            val updatedRuleDraft =
                DraftRuleModel.PreExisting(
                    id = 34,
                    action = ActionModel.Silence,
                    filter =
                        DraftFilterModel(
                            people =
                                RuleValue.Specified(
                                    PeopleModel(listOf(FAKE_CONTACT_1, FAKE_CONTACT_2))
                                ),
                            includedApps = RuleValue.Specified(IncludedAppsModel(listOf(FAKE_APP))),
                        ),
                )

            testScope.launch { underTest.saveRule(updatedRuleDraft) }

            // Verify the rule was sent to the manager, but we didn't update the internal rule
            verify(notificationManager).updateNotificationRule(any())
            assertThat(underTest.rules).hasSize(2)
            assertThat(underTest.rules[1].filter).isNull()
        }

    private fun createRule(
        id: Int = FAKE_ID,
        action: NotificationRule.Action =
            NotificationRule.Action.Builder(PRIMARY_ACTION_HIGHLIGHT).build(),
    ): NotificationRule {
        return NotificationRule.Builder(id, action).build()
    }

    private fun Kosmos.putRulesIntoNotificationManager(rules: List<NotificationRule>) {
        whenever(notificationManager.notificationRules).thenReturn(rules)
    }

    companion object {
        private const val FAKE_ID = 101
        private val CONTACT_1_URI = "key1".toUri()
        private val CONTACT_2_URI = "key2".toUri()
        private val FAKE_CONTACT_1 =
            PersonModel.Contact(
                lookupUri = CONTACT_1_URI,
                name = "name1",
                photoUri = "uri1".toUri(),
            )
        private val FAKE_CONTACT_2 =
            PersonModel.Contact(
                lookupUri = CONTACT_2_URI,
                name = "name2",
                photoUri = "uri2".toUri(),
            )

        private const val FAKE_APP_UID = 13
        private val FAKE_APP =
            AppModel(
                uid = FAKE_APP_UID,
                label = "app label",
                icon = ShapeDrawable(),
                packageName = "app.name",
            )

        private const val FAKE_CONVERSATION_ID_1 = "5ab"
        private const val FAKE_CONVERSATION_ID_2 = "6cd"
        private val FAKE_CONVERSATION_ICON_1 = Icon.createWithBitmap(createBitmap(1, 1))

        private val FAKE_CONVERSATION_ICON_2 = Icon.createWithBitmap(createBitmap(2, 2))
        private val FAKE_CONVERSATION_PARTNER_1 =
            PersonModel.ConversationPartner(
                id = FAKE_CONVERSATION_ID_1,
                displayLabel = "Conversation #1",
                avatarIcon = FAKE_CONVERSATION_ICON_1,
                appBadgeIcon = null,
            )
        private val FAKE_CONVERSATION_PARTNER_2 =
            PersonModel.ConversationPartner(
                id = FAKE_CONVERSATION_ID_2,
                displayLabel = "Conversation #2",
                avatarIcon = FAKE_CONVERSATION_ICON_2,
                appBadgeIcon = null,
            )
    }
}
