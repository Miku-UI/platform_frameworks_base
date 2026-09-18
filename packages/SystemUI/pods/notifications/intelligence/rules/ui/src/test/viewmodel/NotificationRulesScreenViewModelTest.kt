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
import androidx.compose.runtime.mutableStateListOf
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
import com.android.systemui.notifications.intelligence.rules.shared.model.FilterModel
import com.android.systemui.notifications.intelligence.rules.shared.model.IncludedAppsModel
import com.android.systemui.notifications.intelligence.rules.shared.model.KeywordsModel
import com.android.systemui.notifications.intelligence.rules.shared.model.PeopleModel
import com.android.systemui.notifications.intelligence.rules.shared.model.PersonModel
import com.android.systemui.notifications.intelligence.rules.shared.model.RuleModel
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@SmallTest
@RunWith(AndroidJUnit4::class)
class NotificationRulesScreenViewModelTest : SysuiTestCase() {
    val kosmos = testKosmosNew()
    val backStack = mutableStateListOf<RulesScreenViewState>(RulesScreenViewState.CurrentRules)

    val Kosmos.underTest by
        Kosmos.Fixture { notificationRulesScreenViewModelFactory.create(backStack) }

    @Test
    fun currentScreen_isUpdatedWithBackStack() =
        kosmos.runTest {
            assertThat(underTest.currentScreen).isEqualTo(RulesScreenViewState.CurrentRules)

            val draftRule =
                DraftRuleModel.PreExisting(
                    id = 9,
                    action = ActionModel.Highlight,
                    filter = DraftFilterModel(people = null, includedApps = null),
                )

            backStack.add(
                RulesScreenViewState.EditRule(
                    notificationRuleEditViewModelFactory.create(
                        draftRule,
                        onNavigateToCurrentRulesScreen = {},
                    )
                )
            )

            assertThat(underTest.currentScreen)
                .isInstanceOf(RulesScreenViewState.EditRule::class.java)
            assertThat((underTest.currentScreen as RulesScreenViewState.EditRule).viewModel.rule)
                .isEqualTo(draftRule)
        }

    @Test
    fun buildRuleText_onlyAction() =
        kosmos.runTest {
            val rule = RuleModel(id = ID, action = ActionModel.Highlight, filter = FilterModel())

            val ruleDisplay = underTest.buildRuleText(rule, applicationContext.resources)

            assertThat(ruleDisplay.textChunks).hasSize(1)
            assertThat(ruleDisplay.textChunks[0]).isEqualTo(TextChunk.BasicText("Notifications"))
        }

    @Test
    fun buildRuleText_singleContact() =
        kosmos.runTest {
            val contact =
                PersonModel.Contact(
                    lookupUri = "cat-uri".toUri(),
                    name = "Meowth",
                    photoUri = "cat-photo".toUri(),
                )
            val rule =
                RuleModel(
                    id = ID,
                    action = ActionModel.Highlight,
                    filter = FilterModel(people = PeopleModel(listOf(contact))),
                )

            val ruleDisplay = underTest.buildRuleText(rule, applicationContext.resources)

            assertThat(ruleDisplay.textChunks).hasSize(4)

            assertThat(ruleDisplay.textChunks[0]).isEqualTo(TextChunk.BasicText("Notifications"))
            assertThat(ruleDisplay.textChunks[1]).isEqualTo(TextChunk.BasicText(" from "))
            assertThat(ruleDisplay.textChunks[2]).isEqualTo(TextChunk.Icon(contact, "cat-uri"))

            assertThat(ruleDisplay.textChunks[3]).isInstanceOf(TextChunk.FieldValueText::class.java)
            val valueChunk = ruleDisplay.textChunks[3] as TextChunk.FieldValueText
            assertThat(valueChunk.text).isEqualTo("Meowth")
        }

    @Test
    fun buildRuleText_singleConversationPartner() =
        kosmos.runTest {
            val conversationPartner =
                PersonModel.ConversationPartner(
                    id = "skitty",
                    displayLabel = "Conversation with Skitty",
                    avatarIcon = Icon.createWithBitmap(createBitmap(10, 10)),
                    appBadgeIcon = null,
                )
            val rule =
                RuleModel(
                    id = ID,
                    action = ActionModel.Highlight,
                    filter = FilterModel(people = PeopleModel(listOf(conversationPartner))),
                )

            val ruleDisplay = underTest.buildRuleText(rule, applicationContext.resources)

            assertThat(ruleDisplay.textChunks).hasSize(4)

            assertThat(ruleDisplay.textChunks[0]).isEqualTo(TextChunk.BasicText("Notifications"))
            assertThat(ruleDisplay.textChunks[1]).isEqualTo(TextChunk.BasicText(" from "))
            assertThat(ruleDisplay.textChunks[2])
                .isEqualTo(TextChunk.Icon(conversationPartner, "skitty"))

            assertThat(ruleDisplay.textChunks[3]).isInstanceOf(TextChunk.FieldValueText::class.java)
            val valueChunk = ruleDisplay.textChunks[3] as TextChunk.FieldValueText
            assertThat(valueChunk.text).isEqualTo("Conversation with Skitty")
        }

    @Test
    fun buildRuleText_singleApp() =
        kosmos.runTest {
            val app =
                AppModel(
                    packageName = "fake.app.messaging.cat",
                    label = "Chat the Cat",
                    icon = mock<Drawable>(),
                    uid = 1000,
                )
            val rule =
                RuleModel(
                    id = ID,
                    action = ActionModel.Highlight,
                    filter = FilterModel(includedApps = IncludedAppsModel(listOf(app))),
                )

            val ruleDisplay = underTest.buildRuleText(rule, applicationContext.resources)

            assertThat(ruleDisplay.textChunks).hasSize(4)

            assertThat(ruleDisplay.textChunks[0]).isEqualTo(TextChunk.BasicText("Notifications"))
            assertThat(ruleDisplay.textChunks[1]).isEqualTo(TextChunk.BasicText(" from "))
            assertThat(ruleDisplay.textChunks[2])
                .isEqualTo(TextChunk.Icon(app, "1000-fake.app.messaging.cat"))

            assertThat(ruleDisplay.textChunks[3]).isInstanceOf(TextChunk.FieldValueText::class.java)
            val valueChunk = ruleDisplay.textChunks[3] as TextChunk.FieldValueText
            assertThat(valueChunk.text).isEqualTo("Chat the Cat")
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
            val rule =
                RuleModel(
                    id = ID,
                    action = ActionModel.Highlight,
                    filter = FilterModel(people = PeopleModel(listOf(contact, CONTACT_CAT))),
                )

            val ruleDisplay = underTest.buildRuleText(rule, applicationContext.resources)

            assertThat(ruleDisplay.textChunks).hasSize(4)
            assertThat(ruleDisplay.textChunks[0]).isEqualTo(TextChunk.BasicText("Notifications"))
            assertThat(ruleDisplay.textChunks[1]).isEqualTo(TextChunk.BasicText(" from "))
            assertThat(ruleDisplay.textChunks[2]).isEqualTo(TextChunk.Icon(contact, "mom-uri"))

            assertThat(ruleDisplay.textChunks[3]).isInstanceOf(TextChunk.FieldValueText::class.java)
            val fieldValueChunk = ruleDisplay.textChunks[3] as TextChunk.FieldValueText
            assertThat(fieldValueChunk.text).isEqualTo("Mom Cell +1 more")
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
            val rule =
                RuleModel(
                    id = ID,
                    action = ActionModel.Highlight,
                    filter =
                        FilterModel(
                            people =
                                PeopleModel(
                                    listOf(conversationPartner, CONVERSATION_PARTNER_SKITTY)
                                )
                        ),
                )

            val ruleDisplay = underTest.buildRuleText(rule, applicationContext.resources)

            assertThat(ruleDisplay.textChunks).hasSize(4)
            assertThat(ruleDisplay.textChunks[0]).isEqualTo(TextChunk.BasicText("Notifications"))
            assertThat(ruleDisplay.textChunks[1]).isEqualTo(TextChunk.BasicText(" from "))
            assertThat(ruleDisplay.textChunks[2])
                .isEqualTo(TextChunk.Icon(conversationPartner, "persian"))

            assertThat(ruleDisplay.textChunks[3]).isInstanceOf(TextChunk.FieldValueText::class.java)
            val fieldValueChunk = ruleDisplay.textChunks[3] as TextChunk.FieldValueText
            assertThat(fieldValueChunk.text).isEqualTo("Conversation with Persian +1 more")
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
            val rule =
                RuleModel(
                    id = ID,
                    action = ActionModel.Highlight,
                    filter =
                        FilterModel(
                            people = PeopleModel(listOf(contact, CONVERSATION_PARTNER_SKITTY))
                        ),
                )

            val ruleDisplay = underTest.buildRuleText(rule, applicationContext.resources)

            assertThat(ruleDisplay.textChunks).hasSize(4)
            assertThat(ruleDisplay.textChunks[0]).isEqualTo(TextChunk.BasicText("Notifications"))
            assertThat(ruleDisplay.textChunks[1]).isEqualTo(TextChunk.BasicText(" from "))
            assertThat(ruleDisplay.textChunks[2]).isEqualTo(TextChunk.Icon(contact, "mom-uri"))

            assertThat(ruleDisplay.textChunks[3]).isInstanceOf(TextChunk.FieldValueText::class.java)
            val fieldValueChunk = ruleDisplay.textChunks[3] as TextChunk.FieldValueText
            assertThat(fieldValueChunk.text).isEqualTo("Mom Cell +1 more")
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
            val rule =
                RuleModel(
                    id = ID,
                    action = ActionModel.Highlight,
                    filter =
                        FilterModel(
                            includedApps =
                                IncludedAppsModel(listOf(app, APP_CHAT_CAT, APP_POST_CAT))
                        ),
                )

            val ruleDisplay = underTest.buildRuleText(rule, applicationContext.resources)

            assertThat(ruleDisplay.textChunks).hasSize(4)

            assertThat(ruleDisplay.textChunks[0]).isEqualTo(TextChunk.BasicText("Notifications"))
            assertThat(ruleDisplay.textChunks[1]).isEqualTo(TextChunk.BasicText(" from "))
            assertThat(ruleDisplay.textChunks[2])
                .isEqualTo(TextChunk.Icon(app, "2000-fake.app.crossword"))

            assertThat(ruleDisplay.textChunks[3]).isInstanceOf(TextChunk.FieldValueText::class.java)
            val fieldValueChunk = ruleDisplay.textChunks[3] as TextChunk.FieldValueText
            assertThat(fieldValueChunk.text).isEqualTo("Puzzle the Cat +2 more")
        }

    @Test
    fun buildRuleText_singleKeyword() =
        kosmos.runTest {
            val rule =
                RuleModel(
                    id = ID,
                    action = ActionModel.Block,
                    filter = FilterModel(keywords = KeywordsModel(listOf("cat"))),
                )

            val ruleDisplay = underTest.buildRuleText(rule, applicationContext.resources)

            assertThat(ruleDisplay.textChunks).hasSize(3)
            assertThat(ruleDisplay.textChunks[0]).isEqualTo(TextChunk.BasicText("Notifications"))
            assertThat(ruleDisplay.textChunks[1]).isEqualTo(TextChunk.BasicText(" that contain "))
            assertThat(ruleDisplay.textChunks[2]).isEqualTo(TextChunk.FieldValueText("“cat”"))
        }

    @Test
    fun buildRuleText_multipleKeywords() =
        kosmos.runTest {
            val rule =
                RuleModel(
                    id = ID,
                    action = ActionModel.Highlight,
                    filter =
                        FilterModel(keywords = KeywordsModel(listOf("cat", "dog", "pet", "animal"))),
                )

            val ruleDisplay = underTest.buildRuleText(rule, applicationContext.resources)

            assertThat(ruleDisplay.textChunks).hasSize(3)
            assertThat(ruleDisplay.textChunks[0]).isEqualTo(TextChunk.BasicText("Notifications"))
            assertThat(ruleDisplay.textChunks[1]).isEqualTo(TextChunk.BasicText(" that contain "))
            assertThat(ruleDisplay.textChunks[2])
                .isEqualTo(TextChunk.FieldValueText("“cat” +3 more"))
        }

    @Test
    fun buildRuleText_bundleAction() =
        kosmos.runTest {
            val rule =
                RuleModel(
                    id = ID,
                    action = ActionModel.Bundle(name = "Demo Notifs", emojiIcon = "\uD83C\uDF81"),
                    filter = null,
                )

            val ruleDisplay = underTest.buildRuleText(rule, applicationContext.resources)

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
            val rule =
                RuleModel(
                    id = ID,
                    action = ActionModel.Bundle(name = "Demo Notifs", emojiIcon = "\uD83C\uDF81"),
                    filter =
                        FilterModel(
                            includedApps =
                                IncludedAppsModel(listOf(app, APP_CHAT_CAT, APP_POST_CAT)),
                            people = PeopleModel(listOf(contact)),
                            keywords = KeywordsModel(listOf("cat", "dog", "pet", "animal")),
                        ),
                )

            val ruleDisplay = underTest.buildRuleText(rule, applicationContext.resources)

            assertThat(ruleDisplay.textChunks).hasSize(14)

            assertThat(ruleDisplay.textChunks[0]).isEqualTo(TextChunk.BasicText("Notifications"))
            assertThat(ruleDisplay.textChunks[1]).isEqualTo(TextChunk.BasicText(" from "))
            assertThat(ruleDisplay.textChunks[2])
                .isEqualTo(TextChunk.Icon(app, "2000-fake.app.crossword"))

            assertThat(ruleDisplay.textChunks[3]).isInstanceOf(TextChunk.FieldValueText::class.java)
            val fieldValueChunkApps = ruleDisplay.textChunks[3] as TextChunk.FieldValueText
            assertThat(fieldValueChunkApps.text).isEqualTo("Puzzle the Cat +2 more")

            assertThat(ruleDisplay.textChunks[4]).isEqualTo(TextChunk.BasicText(" from "))
            assertThat(ruleDisplay.textChunks[5]).isEqualTo(TextChunk.Icon(contact, "mom-uri"))

            assertThat(ruleDisplay.textChunks[6]).isInstanceOf(TextChunk.FieldValueText::class.java)
            val fieldValueChunkContacts = ruleDisplay.textChunks[6] as TextChunk.FieldValueText
            assertThat(fieldValueChunkContacts.text).isEqualTo("Mom Cell")

            assertThat(ruleDisplay.textChunks[7]).isEqualTo(TextChunk.BasicText(" that contain "))
            assertThat(ruleDisplay.textChunks[8])
                .isEqualTo(TextChunk.FieldValueText("“cat” +3 more"))

            assertThat(ruleDisplay.textChunks[9]).isEqualTo(TextChunk.BasicText(" into "))
            assertThat(ruleDisplay.textChunks[10])
                .isEqualTo(TextChunk.FieldValueText("“Demo Notifs”"))
            assertThat(ruleDisplay.textChunks[11]).isEqualTo(TextChunk.BasicText(" bundle with "))
            assertThat(ruleDisplay.textChunks[12])
                .isEqualTo(TextChunk.FieldValueText("\uD83C\uDF81"))
            assertThat(ruleDisplay.textChunks[13]).isEqualTo(TextChunk.BasicText(" emoji"))
        }

    companion object {
        private const val ID = 10

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
