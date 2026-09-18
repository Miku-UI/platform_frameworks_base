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

import android.content.applicationContext
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.notifications.intelligence.rules.shared.model.ActionModel
import com.android.systemui.notifications.intelligence.rules.shared.model.AppModel
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftFilterModel
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel
import com.android.systemui.notifications.intelligence.rules.shared.model.IncludedAppsModel
import com.android.systemui.notifications.intelligence.rules.shared.model.KeywordsModel
import com.android.systemui.notifications.intelligence.rules.shared.model.PeopleModel
import com.android.systemui.notifications.intelligence.rules.shared.model.PersonModel
import com.android.systemui.notifications.intelligence.rules.shared.model.RuleValue
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@SmallTest
@RunWith(AndroidJUnit4::class)
class NotificationRuleEditViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    val Kosmos.underTest by Kosmos.Fixture { notificationRuleEditViewModelFactory }

    @Test
    fun buildRuleText_onlyAction() =
        kosmos.runTest {
            val underTest =
                notificationRuleEditViewModelFactory.create(
                    DraftRuleModel.PreExisting(
                        id = 10,
                        action = ActionModel.Highlight,
                        filter = DraftFilterModel(people = null, includedApps = null),
                    ),
                    onNavigateToCurrentRulesScreen = {},
                )

            val ruleDisplay =
                underTest.buildRuleText(
                    onEnterEditField = {},
                    onExitEditField = {},
                    resources = applicationContext.resources,
                )

            assertThat(ruleDisplay.textChunks).hasSize(1)
            assertThat(ruleDisplay.textChunks[0]).isEqualTo(TextChunk.BasicText("Notifications"))
        }

    @Test
    fun buildRuleText_singleContact() =
        kosmos.runTest {
            var lastEnteredEditField: RulesScreenViewState.EditField? = null

            val contact =
                PersonModel.Contact(
                    lookupUri = "cat-uri".toUri(),
                    name = "Meowth",
                    photoUri = "cat-photo".toUri(),
                )
            val underTest =
                notificationRuleEditViewModelFactory.create(
                    DraftRuleModel.New(
                        action = ActionModel.Highlight,
                        filter =
                            DraftFilterModel(
                                people = RuleValue.Specified(PeopleModel(listOf(contact))),
                                includedApps = null,
                            ),
                    ),
                    onNavigateToCurrentRulesScreen = {},
                )

            val ruleDisplay =
                underTest.buildRuleText(
                    onEnterEditField = { lastEnteredEditField = it },
                    onExitEditField = {},
                    resources = applicationContext.resources,
                )

            assertThat(ruleDisplay.textChunks).hasSize(4)
            assertThat(ruleDisplay.textChunks[0]).isEqualTo(TextChunk.BasicText("Notifications"))
            assertThat(ruleDisplay.textChunks[1]).isEqualTo(TextChunk.BasicText(" from "))
            assertThat(ruleDisplay.textChunks[2]).isEqualTo(TextChunk.Icon(contact, "cat-uri"))

            assertThat(ruleDisplay.textChunks[3]).isInstanceOf(TextChunk.ClickableText::class.java)
            val clickableChunk = ruleDisplay.textChunks[3] as TextChunk.ClickableText
            assertThat(clickableChunk.text).isEqualTo("Meowth")
            assertThat(clickableChunk.isAmbiguous).isFalse()

            clickableChunk.onClick()
            assertThat(lastEnteredEditField)
                .isInstanceOf(RulesScreenViewState.EditField.People::class.java)
        }

    @Test
    fun buildRuleText_singleConversationPartner() =
        kosmos.runTest {
            var lastEnteredEditField: RulesScreenViewState.EditField? = null

            val conversationPartner =
                PersonModel.ConversationPartner(
                    id = "skitty",
                    displayLabel = "Conversation with Skitty",
                    avatarIcon = Icon.createWithBitmap(createBitmap(10, 10)),
                    appBadgeIcon = null,
                )
            val underTest =
                notificationRuleEditViewModelFactory.create(
                    DraftRuleModel.New(
                        action = ActionModel.Highlight,
                        filter =
                            DraftFilterModel(
                                people =
                                    RuleValue.Specified(PeopleModel(listOf(conversationPartner))),
                                includedApps = null,
                            ),
                    ),
                    onNavigateToCurrentRulesScreen = {},
                )

            val ruleDisplay =
                underTest.buildRuleText(
                    onEnterEditField = { lastEnteredEditField = it },
                    onExitEditField = {},
                    resources = applicationContext.resources,
                )

            assertThat(ruleDisplay.textChunks).hasSize(4)
            assertThat(ruleDisplay.textChunks[0]).isEqualTo(TextChunk.BasicText("Notifications"))
            assertThat(ruleDisplay.textChunks[1]).isEqualTo(TextChunk.BasicText(" from "))
            assertThat(ruleDisplay.textChunks[2])
                .isEqualTo(TextChunk.Icon(conversationPartner, "skitty"))

            assertThat(ruleDisplay.textChunks[3]).isInstanceOf(TextChunk.ClickableText::class.java)
            val clickableChunk = ruleDisplay.textChunks[3] as TextChunk.ClickableText
            assertThat(clickableChunk.text).isEqualTo("Conversation with Skitty")
            assertThat(clickableChunk.isAmbiguous).isFalse()

            clickableChunk.onClick()
            assertThat(lastEnteredEditField)
                .isInstanceOf(RulesScreenViewState.EditField.People::class.java)
        }

    @Test
    fun buildRuleText_ambiguousContact() =
        kosmos.runTest {
            var lastEnteredEditField: RulesScreenViewState.EditField? = null

            val underTest =
                notificationRuleEditViewModelFactory.create(
                    DraftRuleModel.New(
                        action = ActionModel.Highlight,
                        filter =
                            DraftFilterModel(
                                people = RuleValue.Ambiguous("who is it?"),
                                includedApps = null,
                            ),
                    ),
                    onNavigateToCurrentRulesScreen = {},
                )

            val ruleDisplay =
                underTest.buildRuleText(
                    onEnterEditField = { lastEnteredEditField = it },
                    onExitEditField = {},
                    resources = applicationContext.resources,
                )

            assertThat(ruleDisplay.textChunks).hasSize(3)
            assertThat(ruleDisplay.textChunks[0]).isEqualTo(TextChunk.BasicText("Notifications"))
            assertThat(ruleDisplay.textChunks[1]).isEqualTo(TextChunk.BasicText(" from "))

            assertThat(ruleDisplay.textChunks[2]).isInstanceOf(TextChunk.ClickableText::class.java)
            val clickableChunk = ruleDisplay.textChunks[2] as TextChunk.ClickableText
            assertThat(clickableChunk.text).isEqualTo("who is it?")
            assertThat(clickableChunk.isAmbiguous).isTrue()

            clickableChunk.onClick()
            assertThat(lastEnteredEditField)
                .isInstanceOf(RulesScreenViewState.EditField.People::class.java)
        }

    @Test
    fun buildRuleText_singleApp() =
        kosmos.runTest {
            var lastEnteredEditField: RulesScreenViewState.EditField? = null

            val app =
                AppModel(
                    packageName = "fake.app.messaging.cat",
                    label = "Chat the Cat",
                    icon = mock<Drawable>(),
                    uid = 1000,
                )
            val underTest =
                notificationRuleEditViewModelFactory.create(
                    DraftRuleModel.PreExisting(
                        id = 10,
                        action = ActionModel.Highlight,
                        filter =
                            DraftFilterModel(
                                people = null,
                                includedApps = RuleValue.Specified(IncludedAppsModel(listOf(app))),
                            ),
                    ),
                    onNavigateToCurrentRulesScreen = {},
                )

            val ruleDisplay =
                underTest.buildRuleText(
                    onEnterEditField = { lastEnteredEditField = it },
                    onExitEditField = {},
                    resources = applicationContext.resources,
                )

            assertThat(ruleDisplay.textChunks).hasSize(4)
            assertThat(ruleDisplay.textChunks[0]).isEqualTo(TextChunk.BasicText("Notifications"))
            assertThat(ruleDisplay.textChunks[1]).isEqualTo(TextChunk.BasicText(" from "))
            assertThat(ruleDisplay.textChunks[2])
                .isEqualTo(TextChunk.Icon(app, "1000-fake.app.messaging.cat"))

            assertThat(ruleDisplay.textChunks[3]).isInstanceOf(TextChunk.ClickableText::class.java)
            val clickableChunk = ruleDisplay.textChunks[3] as TextChunk.ClickableText
            assertThat(clickableChunk.text).isEqualTo("Chat the Cat")
            assertThat(clickableChunk.isAmbiguous).isFalse()

            clickableChunk.onClick()
            assertThat(lastEnteredEditField)
                .isInstanceOf(RulesScreenViewState.EditField.Apps::class.java)
        }

    @Test
    fun buildRuleText_twoContacts() =
        kosmos.runTest {
            val contact =
                PersonModel.Contact(
                    lookupUri = "mom-uri".toUri(),
                    name = "Mom Cell",
                    photoUri = "mom-photo".toUri(),
                )
            val underTest =
                notificationRuleEditViewModelFactory.create(
                    DraftRuleModel.New(
                        action = ActionModel.Highlight,
                        filter =
                            DraftFilterModel(
                                people =
                                    RuleValue.Specified(PeopleModel(listOf(contact, CONTACT_CAT))),
                                includedApps = null,
                            ),
                    ),
                    onNavigateToCurrentRulesScreen = {},
                )

            val ruleDisplay =
                underTest.buildRuleText(
                    onEnterEditField = {},
                    onExitEditField = {},
                    resources = applicationContext.resources,
                )

            assertThat(ruleDisplay.textChunks).hasSize(4)
            assertThat(ruleDisplay.textChunks[0]).isEqualTo(TextChunk.BasicText("Notifications"))
            assertThat(ruleDisplay.textChunks[1]).isEqualTo(TextChunk.BasicText(" from "))
            assertThat(ruleDisplay.textChunks[2]).isEqualTo(TextChunk.Icon(contact, "mom-uri"))

            assertThat(ruleDisplay.textChunks[3]).isInstanceOf(TextChunk.ClickableText::class.java)
            val clickableChunk = ruleDisplay.textChunks[3] as TextChunk.ClickableText
            assertThat(clickableChunk.text).isEqualTo("Mom Cell +1 more")
            assertThat(clickableChunk.isAmbiguous).isFalse()
        }

    @Test
    fun buildRuleText_twoConversationPartners() =
        kosmos.runTest {
            val conversationPartner =
                PersonModel.ConversationPartner(
                    id = "persian",
                    displayLabel = "Conversation with Persian",
                    avatarIcon = Icon.createWithBitmap(createBitmap(10, 10)),
                    appBadgeIcon = null,
                )

            val underTest =
                notificationRuleEditViewModelFactory.create(
                    DraftRuleModel.New(
                        action = ActionModel.Highlight,
                        filter =
                            DraftFilterModel(
                                people =
                                    RuleValue.Specified(
                                        PeopleModel(
                                            listOf(conversationPartner, CONVERSATION_PARTNER_SKITTY)
                                        )
                                    ),
                                includedApps = null,
                            ),
                    ),
                    onNavigateToCurrentRulesScreen = {},
                )

            val ruleDisplay =
                underTest.buildRuleText(
                    onEnterEditField = {},
                    onExitEditField = {},
                    resources = applicationContext.resources,
                )

            assertThat(ruleDisplay.textChunks).hasSize(4)
            assertThat(ruleDisplay.textChunks[0]).isEqualTo(TextChunk.BasicText("Notifications"))
            assertThat(ruleDisplay.textChunks[1]).isEqualTo(TextChunk.BasicText(" from "))
            assertThat(ruleDisplay.textChunks[2])
                .isEqualTo(TextChunk.Icon(conversationPartner, "persian"))

            assertThat(ruleDisplay.textChunks[3]).isInstanceOf(TextChunk.ClickableText::class.java)
            val clickableChunk = ruleDisplay.textChunks[3] as TextChunk.ClickableText
            assertThat(clickableChunk.text).isEqualTo("Conversation with Persian +1 more")
            assertThat(clickableChunk.isAmbiguous).isFalse()
        }

    @Test
    fun buildRuleText_contactAndConversationPartner() =
        kosmos.runTest {
            val contact =
                PersonModel.Contact(
                    lookupUri = "mom-uri".toUri(),
                    name = "Mom Cell",
                    photoUri = "mom-photo".toUri(),
                )

            val underTest =
                notificationRuleEditViewModelFactory.create(
                    DraftRuleModel.New(
                        action = ActionModel.Highlight,
                        filter =
                            DraftFilterModel(
                                people =
                                    RuleValue.Specified(
                                        PeopleModel(listOf(contact, CONVERSATION_PARTNER_SKITTY))
                                    ),
                                includedApps = null,
                            ),
                    ),
                    onNavigateToCurrentRulesScreen = {},
                )

            val ruleDisplay =
                underTest.buildRuleText(
                    onEnterEditField = {},
                    onExitEditField = {},
                    resources = applicationContext.resources,
                )

            assertThat(ruleDisplay.textChunks).hasSize(4)
            assertThat(ruleDisplay.textChunks[0]).isEqualTo(TextChunk.BasicText("Notifications"))
            assertThat(ruleDisplay.textChunks[1]).isEqualTo(TextChunk.BasicText(" from "))
            assertThat(ruleDisplay.textChunks[2]).isEqualTo(TextChunk.Icon(contact, "mom-uri"))

            assertThat(ruleDisplay.textChunks[3]).isInstanceOf(TextChunk.ClickableText::class.java)
            val clickableChunk = ruleDisplay.textChunks[3] as TextChunk.ClickableText
            assertThat(clickableChunk.text).isEqualTo("Mom Cell +1 more")
            assertThat(clickableChunk.isAmbiguous).isFalse()
        }

    @Test
    fun buildRuleText_threeApps() =
        kosmos.runTest {
            val app =
                AppModel(
                    packageName = "fake.app.crossword",
                    label = "Puzzle the Cat",
                    icon = mock<Drawable>(),
                    uid = 2000,
                )
            val underTest =
                notificationRuleEditViewModelFactory.create(
                    DraftRuleModel.New(
                        action = ActionModel.Highlight,
                        filter =
                            DraftFilterModel(
                                includedApps =
                                    RuleValue.Specified(
                                        IncludedAppsModel(listOf(app, APP_CHAT_CAT, APP_POST_CAT))
                                    ),
                                people = null,
                            ),
                    ),
                    onNavigateToCurrentRulesScreen = {},
                )

            val ruleDisplay =
                underTest.buildRuleText(
                    onEnterEditField = {},
                    onExitEditField = {},
                    resources = applicationContext.resources,
                )

            assertThat(ruleDisplay.textChunks).hasSize(4)
            assertThat(ruleDisplay.textChunks[0]).isEqualTo(TextChunk.BasicText("Notifications"))
            assertThat(ruleDisplay.textChunks[1]).isEqualTo(TextChunk.BasicText(" from "))
            assertThat(ruleDisplay.textChunks[2])
                .isEqualTo(TextChunk.Icon(app, "2000-fake.app.crossword"))

            assertThat(ruleDisplay.textChunks[3]).isInstanceOf(TextChunk.ClickableText::class.java)
            val clickableChunk = ruleDisplay.textChunks[3] as TextChunk.ClickableText
            assertThat(clickableChunk.text).isEqualTo("Puzzle the Cat +2 more")
            assertThat(clickableChunk.isAmbiguous).isFalse()
        }

    @Test
    fun buildRuleText_singleKeyword() =
        kosmos.runTest {
            var lastEnteredEditField: RulesScreenViewState.EditField? = null

            val draftRule =
                DraftRuleModel.New(
                    action = ActionModel.Highlight,
                    filter = DraftFilterModel(keywords = KeywordsModel(listOf("cat"))),
                )

            val underTest =
                notificationRuleEditViewModelFactory.create(
                    draftRule,
                    onNavigateToCurrentRulesScreen = {},
                )

            val ruleDisplay =
                underTest.buildRuleText(
                    onEnterEditField = { lastEnteredEditField = it },
                    onExitEditField = {},
                    resources = applicationContext.resources,
                )

            assertThat(ruleDisplay.textChunks).hasSize(3)
            assertThat(ruleDisplay.textChunks[0]).isEqualTo(TextChunk.BasicText("Notifications"))
            assertThat(ruleDisplay.textChunks[1]).isEqualTo(TextChunk.BasicText(" that contain "))

            assertThat(ruleDisplay.textChunks[2]).isInstanceOf(TextChunk.ClickableText::class.java)
            val clickableChunk = ruleDisplay.textChunks[2] as TextChunk.ClickableText
            assertThat(clickableChunk.text).isEqualTo("“cat”")
            assertThat(clickableChunk.isAmbiguous).isFalse()

            clickableChunk.onClick()
            assertThat(lastEnteredEditField)
                .isInstanceOf(RulesScreenViewState.EditField.Keywords::class.java)
        }

    @Test
    fun buildRuleText_multipleKeywords() =
        kosmos.runTest {
            var lastEnteredEditField: RulesScreenViewState.EditField? = null

            val draftRule =
                DraftRuleModel.New(
                    action = ActionModel.Block,
                    filter =
                        DraftFilterModel(
                            keywords = KeywordsModel(listOf("cat", "dog", "pet", "animal"))
                        ),
                )

            val underTest =
                notificationRuleEditViewModelFactory.create(
                    draftRule,
                    onNavigateToCurrentRulesScreen = {},
                )

            val ruleDisplay =
                underTest.buildRuleText(
                    onEnterEditField = { lastEnteredEditField = it },
                    onExitEditField = {},
                    resources = applicationContext.resources,
                )

            assertThat(ruleDisplay.textChunks).hasSize(3)
            assertThat(ruleDisplay.textChunks[0]).isEqualTo(TextChunk.BasicText("Notifications"))
            assertThat(ruleDisplay.textChunks[1]).isEqualTo(TextChunk.BasicText(" that contain "))

            assertThat(ruleDisplay.textChunks[2]).isInstanceOf(TextChunk.ClickableText::class.java)
            val clickableChunk = ruleDisplay.textChunks[2] as TextChunk.ClickableText
            assertThat(clickableChunk.text).isEqualTo("“cat” +3 more")
            assertThat(clickableChunk.isAmbiguous).isFalse()

            clickableChunk.onClick()
            assertThat(lastEnteredEditField)
                .isInstanceOf(RulesScreenViewState.EditField.Keywords::class.java)
        }

    @Test
    fun buildRuleText_bundleAction() =
        kosmos.runTest {
            val draftRule =
                DraftRuleModel.New(
                    action = ActionModel.Bundle(name = "Demo Notifs", emojiIcon = "\uD83C\uDF81"),
                    filter = DraftFilterModel(),
                )

            val underTest =
                notificationRuleEditViewModelFactory.create(
                    draftRule,
                    onNavigateToCurrentRulesScreen = {},
                )

            val ruleDisplay =
                underTest.buildRuleText(
                    onEnterEditField = {},
                    onExitEditField = {},
                    resources = applicationContext.resources,
                )

            assertThat(ruleDisplay.textChunks).hasSize(6)
            assertThat(ruleDisplay.textChunks[0]).isEqualTo(TextChunk.BasicText("Notifications"))
            assertThat(ruleDisplay.textChunks[1]).isEqualTo(TextChunk.BasicText(" into "))
            assertThat(ruleDisplay.textChunks[2])
                .isEqualTo(TextChunk.FieldValueText("“Demo Notifs”"))
            assertThat(ruleDisplay.textChunks[3]).isEqualTo(TextChunk.BasicText(" bundle with "))
            assertThat(ruleDisplay.textChunks[4])
                .isEqualTo(TextChunk.FieldValueText("\uD83C\uDF81"))
            assertThat(ruleDisplay.textChunks[5]).isEqualTo(TextChunk.BasicText(" emoji"))
        }

    @Test
    fun buildRuleText_allFields() =
        kosmos.runTest {
            val contact =
                PersonModel.Contact(
                    lookupUri = "mom-uri".toUri(),
                    name = "Mom Cell",
                    photoUri = "mom-photo".toUri(),
                )
            val app =
                AppModel(
                    packageName = "fake.app.crossword",
                    label = "Puzzle the Cat",
                    icon = mock<Drawable>(),
                    uid = 2000,
                )
            val underTest =
                notificationRuleEditViewModelFactory.create(
                    DraftRuleModel.New(
                        action =
                            ActionModel.Bundle(name = "Demo Notifs", emojiIcon = "\uD83C\uDF81"),
                        filter =
                            DraftFilterModel(
                                people = RuleValue.Specified(PeopleModel(listOf(contact))),
                                includedApps =
                                    RuleValue.Specified(
                                        IncludedAppsModel(listOf(app, APP_CHAT_CAT, APP_POST_CAT))
                                    ),
                                keywords = KeywordsModel(listOf("cat", "dog", "pet", "animal")),
                            ),
                    ),
                    onNavigateToCurrentRulesScreen = {},
                )

            val ruleDisplay =
                underTest.buildRuleText(
                    onEnterEditField = {},
                    onExitEditField = {},
                    resources = applicationContext.resources,
                )

            assertThat(ruleDisplay.textChunks).hasSize(14)

            assertThat(ruleDisplay.textChunks[0]).isEqualTo(TextChunk.BasicText("Notifications"))
            assertThat(ruleDisplay.textChunks[1]).isEqualTo(TextChunk.BasicText(" from "))
            assertThat(ruleDisplay.textChunks[2])
                .isEqualTo(TextChunk.Icon(app, "2000-fake.app.crossword"))

            assertThat(ruleDisplay.textChunks[3]).isInstanceOf(TextChunk.ClickableText::class.java)
            val clickableChunkApps = ruleDisplay.textChunks[3] as TextChunk.ClickableText
            assertThat(clickableChunkApps.text).isEqualTo("Puzzle the Cat +2 more")
            assertThat(clickableChunkApps.isAmbiguous).isFalse()

            assertThat(ruleDisplay.textChunks[4]).isEqualTo(TextChunk.BasicText(" from "))
            assertThat(ruleDisplay.textChunks[5]).isEqualTo(TextChunk.Icon(contact, "mom-uri"))

            assertThat(ruleDisplay.textChunks[6]).isInstanceOf(TextChunk.ClickableText::class.java)
            val clickableChunkContacts = ruleDisplay.textChunks[6] as TextChunk.ClickableText
            assertThat(clickableChunkContacts.text).isEqualTo("Mom Cell")
            assertThat(clickableChunkContacts.isAmbiguous).isFalse()

            assertThat(ruleDisplay.textChunks[7]).isEqualTo(TextChunk.BasicText(" that contain "))

            assertThat(ruleDisplay.textChunks[8]).isInstanceOf(TextChunk.ClickableText::class.java)
            val clickableChunk = ruleDisplay.textChunks[8] as TextChunk.ClickableText
            assertThat(clickableChunk.text).isEqualTo("“cat” +3 more")
            assertThat(clickableChunkContacts.isAmbiguous).isFalse()

            assertThat(ruleDisplay.textChunks[9]).isEqualTo(TextChunk.BasicText(" into "))
            assertThat(ruleDisplay.textChunks[10])
                .isEqualTo(TextChunk.FieldValueText("“Demo Notifs”"))
            assertThat(ruleDisplay.textChunks[11]).isEqualTo(TextChunk.BasicText(" bundle with "))
            assertThat(ruleDisplay.textChunks[12])
                .isEqualTo(TextChunk.FieldValueText("\uD83C\uDF81"))
            assertThat(ruleDisplay.textChunks[13]).isEqualTo(TextChunk.BasicText(" emoji"))
        }

    @Test
    fun onAppsSaved_storesNewList() =
        kosmos.runTest {
            var onExitInvoked = false
            val underTest =
                notificationRuleEditViewModelFactory.create(
                    DraftRuleModel.PreExisting(
                        id = 12,
                        action = ActionModel.Highlight,
                        filter = DraftFilterModel(),
                    ),
                    onNavigateToCurrentRulesScreen = {},
                )

            underTest.onAppsSaved(listOf(APP_CHAT_CAT, APP_POST_CAT)) { onExitInvoked = true }

            assertThat(underTest.rule.filter.includedApps)
                .isEqualTo(
                    RuleValue.Specified(IncludedAppsModel(listOf(APP_CHAT_CAT, APP_POST_CAT)))
                )
            assertThat(onExitInvoked).isTrue()
        }

    @Test
    fun onAppsSaved_emptyList_resetsToNull() =
        kosmos.runTest {
            var onExitInvoked = false
            val underTest =
                notificationRuleEditViewModelFactory.create(
                    DraftRuleModel.New(
                        action = ActionModel.Highlight,
                        filter =
                            DraftFilterModel(
                                includedApps =
                                    RuleValue.Specified(
                                        IncludedAppsModel(listOf(APP_CHAT_CAT, APP_POST_CAT))
                                    )
                            ),
                    ),
                    onNavigateToCurrentRulesScreen = {},
                )

            underTest.onAppsSaved(newApps = emptyList()) { onExitInvoked = true }

            assertThat(underTest.rule.filter.includedApps).isNull()
            assertThat(onExitInvoked).isTrue()
        }

    @Test
    fun onPeopleSaved_storesNewList() =
        kosmos.runTest {
            var onExitInvoked = false
            val underTest =
                notificationRuleEditViewModelFactory.create(
                    DraftRuleModel.New(action = ActionModel.Highlight, filter = DraftFilterModel()),
                    onNavigateToCurrentRulesScreen = {},
                )

            underTest.onPeopleSaved(listOf(CONTACT_CAT, CONVERSATION_PARTNER_SKITTY)) {
                onExitInvoked = true
            }

            assertThat(underTest.rule.filter.people)
                .isEqualTo(
                    RuleValue.Specified(
                        PeopleModel(listOf(CONTACT_CAT, CONVERSATION_PARTNER_SKITTY))
                    )
                )
            assertThat(onExitInvoked).isTrue()
        }

    @Test
    fun onPeopleSaved_emptyList_resetsToNull() =
        kosmos.runTest {
            var onExitInvoked = false
            val underTest =
                notificationRuleEditViewModelFactory.create(
                    DraftRuleModel.PreExisting(
                        id = 10,
                        action = ActionModel.Highlight,
                        filter =
                            DraftFilterModel(
                                people = RuleValue.Specified(PeopleModel(listOf(CONTACT_CAT)))
                            ),
                    ),
                    onNavigateToCurrentRulesScreen = {},
                )

            underTest.onPeopleSaved(newPeople = emptyList()) { onExitInvoked = true }

            assertThat(underTest.rule.filter.people).isNull()
            assertThat(onExitInvoked).isTrue()
        }

    @Test
    fun onKeywordsSaved_storesNewList() =
        kosmos.runTest {
            var onExitInvoked = false
            val underTest =
                notificationRuleEditViewModelFactory.create(
                    DraftRuleModel.New(action = ActionModel.Highlight, filter = DraftFilterModel()),
                    onNavigateToCurrentRulesScreen = {},
                )

            underTest.onKeywordsSaved(listOf("fish", "horse")) { onExitInvoked = true }

            assertThat(underTest.rule.filter.keywords)
                .isEqualTo(KeywordsModel(listOf("fish", "horse")))
            assertThat(onExitInvoked).isTrue()
        }

    @Test
    fun onKeywordsSaved_emptyList_resetsToNull() =
        kosmos.runTest {
            var onExitInvoked = false
            val underTest =
                notificationRuleEditViewModelFactory.create(
                    DraftRuleModel.New(
                        action = ActionModel.Highlight,
                        filter = DraftFilterModel(keywords = KeywordsModel(listOf("fish", "horse"))),
                    ),
                    onNavigateToCurrentRulesScreen = {},
                )

            underTest.onKeywordsSaved(newKeywords = emptyList()) { onExitInvoked = true }

            assertThat(underTest.rule.filter.keywords).isNull()
            assertThat(onExitInvoked).isTrue()
        }

    companion object {
        private val CONTACT_CAT =
            PersonModel.Contact(
                lookupUri = "cat-uri".toUri(),
                name = "Meowth",
                photoUri = "cat-photo".toUri(),
            )

        private val CONVERSATION_PARTNER_SKITTY =
            PersonModel.ConversationPartner(
                id = "skitty",
                displayLabel = "Conversation with Skitty",
                avatarIcon = Icon.createWithBitmap(createBitmap(1, 1)),
                appBadgeIcon = null,
            )

        private val APP_CHAT_CAT =
            AppModel(
                packageName = "fake.app.messaging.cat",
                label = "Chat the Cat",
                icon = mock<Drawable>(),
                uid = 1000,
            )
        private val APP_POST_CAT =
            AppModel(
                packageName = "fake.app.social",
                label = "Post the Cat",
                icon = mock<Drawable>(),
                uid = 1001,
            )
    }
}
