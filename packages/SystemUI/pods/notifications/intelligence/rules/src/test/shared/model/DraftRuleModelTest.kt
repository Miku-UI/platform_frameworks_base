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

package com.android.systemui.notifications.intelligence.rules.shared.model

import android.graphics.drawable.ShapeDrawable
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel.Companion.toDraft
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel.Companion.toFullRule
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class DraftRuleModelTest : SysuiTestCase() {
    @Test
    fun toDraft_filterNull() {
        val rule = RuleModel(id = 1, action = ActionModel.Block, filter = null)

        val draftRule = rule.toDraft()

        assertThat(draftRule).isInstanceOf(DraftRuleModel.PreExisting::class.java)
        assertThat((draftRule as DraftRuleModel.PreExisting).id).isEqualTo(1)
        assertThat(draftRule.action).isEqualTo(ActionModel.Block)
        assertThat(draftRule.filter.people).isNull()
        assertThat(draftRule.filter.includedApps).isNull()
        assertThat(draftRule.filter.keywords).isNull()
    }

    @Test
    fun toDraft_allFilterValuesNull() {
        val rule =
            RuleModel(
                id = 1,
                action = ActionModel.Block,
                filter = FilterModel(people = null, includedApps = null, keywords = null),
            )

        val draftRule = rule.toDraft()

        assertThat(draftRule).isInstanceOf(DraftRuleModel.PreExisting::class.java)
        assertThat((draftRule as DraftRuleModel.PreExisting).id).isEqualTo(1)
        assertThat(draftRule.action).isEqualTo(ActionModel.Block)
        assertThat(draftRule.filter.people).isNull()
        assertThat(draftRule.filter.includedApps).isNull()
        assertThat(draftRule.filter.keywords).isNull()
    }

    @Test
    fun toDraft_allFilledIn() {
        val contacts = PeopleModel(listOf(FAKE_CONTACT))
        val includedApps = IncludedAppsModel(listOf(FAKE_APP))
        val keywords = KeywordsModel(listOf("cat", "dog", "fish"))
        val rule =
            RuleModel(
                id = 2,
                action = ActionModel.Silence,
                filter =
                    FilterModel(people = contacts, includedApps = includedApps, keywords = keywords),
            )

        val draftRule = rule.toDraft()

        assertThat(draftRule.action).isEqualTo(ActionModel.Silence)
        assertThat(draftRule.filter.people).isEqualTo(RuleValue.Specified(contacts))
        assertThat(draftRule.filter.includedApps).isEqualTo(RuleValue.Specified(includedApps))
        assertThat(draftRule.filter.keywords).isEqualTo(keywords)
    }

    @Test
    fun copyDraft_noArgs_isEqual() {
        val draft =
            DraftRuleModel.New(
                ActionModel.HighlightAndAlert,
                DraftFilterModel(people = RuleValue.Ambiguous("test")),
            )

        val copy = draft.copyDraft()

        assertThat(copy).isEqualTo(draft)
    }

    @Test
    fun copyDraft_changesActionOnly() {
        val draft =
            DraftRuleModel.New(
                ActionModel.HighlightAndAlert,
                DraftFilterModel(people = RuleValue.Ambiguous("test")),
            )

        val copy =
            draft.copyDraft(
                action = ActionModel.Bundle(name = "bundle", emojiIcon = "\uD83D\uDC9D")
            )

        assertThat(copy)
            .isEqualTo(
                DraftRuleModel.New(
                    action = ActionModel.Bundle(name = "bundle", emojiIcon = "\uD83D\uDC9D"),
                    filter = DraftFilterModel(people = RuleValue.Ambiguous("test")),
                )
            )
    }

    @Test
    fun copyDraft_changesFilterOnly() {
        val draft =
            DraftRuleModel.New(
                ActionModel.HighlightAndAlert,
                DraftFilterModel(people = RuleValue.Ambiguous("test")),
            )

        val newFilter = DraftFilterModel(people = RuleValue.Ambiguous("new"))
        val copy = draft.copyDraft(filter = newFilter)

        assertThat(copy)
            .isEqualTo(
                DraftRuleModel.New(action = ActionModel.HighlightAndAlert, filter = newFilter)
            )
    }

    @Test
    fun toFullRule_new_hasAmbiguousValues_throws() {
        val newRule =
            DraftRuleModel.New(
                action = ActionModel.Highlight,
                filter =
                    DraftFilterModel(
                        includedApps = RuleValue.Ambiguous("social media apps"),
                        people = RuleValue.Specified(PeopleModel(listOf(FAKE_CONTACT))),
                    ),
            )

        assertThrows(IllegalArgumentException::class.java) { newRule.toFullRule(1) }
    }

    @Test
    fun toFullRule_new_noAmbiguousValues() {
        val newRule =
            DraftRuleModel.New(
                action = ActionModel.Silence,
                filter =
                    DraftFilterModel(
                        includedApps = null,
                        people = RuleValue.Specified(PeopleModel(listOf(FAKE_CONTACT))),
                        keywords = KeywordsModel(listOf("example")),
                    ),
            )

        val fullRule = newRule.toFullRule(id = 4)

        assertThat(fullRule.id).isEqualTo(4)
        assertThat(fullRule.action).isEqualTo(ActionModel.Silence)
        assertThat(fullRule.filter).isNotNull()
        assertThat(fullRule.filter!!.includedApps).isNull()
        assertThat(fullRule.filter!!.people).isEqualTo(PeopleModel(listOf(FAKE_CONTACT)))
        assertThat(fullRule.filter!!.keywords).isEqualTo(KeywordsModel(listOf("example")))
    }

    @Test
    fun toFullRule_preExisting_hasAmbiguousValues_throws() {
        val newRule =
            DraftRuleModel.PreExisting(
                id = 10,
                action = ActionModel.Silence,
                filter =
                    DraftFilterModel(
                        includedApps = RuleValue.Specified(IncludedAppsModel(listOf(FAKE_APP))),
                        people = RuleValue.Ambiguous("siblings"),
                    ),
            )

        assertThrows(IllegalArgumentException::class.java) { newRule.toFullRule() }
    }

    @Test
    fun toFullRule_preExisting_noAmbiguousValues() {
        val newRule =
            DraftRuleModel.PreExisting(
                id = 10,
                action = ActionModel.Silence,
                filter =
                    DraftFilterModel(
                        includedApps = RuleValue.Specified(IncludedAppsModel(listOf(FAKE_APP))),
                        people = null,
                        keywords = KeywordsModel(listOf("example")),
                    ),
            )

        val fullRule = newRule.toFullRule()

        assertThat(fullRule.id).isEqualTo(10)
        assertThat(fullRule.action).isEqualTo(ActionModel.Silence)
        assertThat(fullRule.filter).isNotNull()
        assertThat(fullRule.filter!!.includedApps).isEqualTo(IncludedAppsModel(listOf(FAKE_APP)))
        assertThat(fullRule.filter!!.people).isNull()
        assertThat(fullRule.filter!!.keywords).isEqualTo(KeywordsModel(listOf("example")))
    }

    @Test
    fun toFullRule_new_noFilterValues() {
        val newRule =
            DraftRuleModel.New(
                action = ActionModel.Highlight,
                filter = DraftFilterModel(includedApps = null, people = null, keywords = null),
            )

        val fullRule = newRule.toFullRule(id = 10)

        assertThat(fullRule.id).isEqualTo(10)
        assertThat(fullRule.action).isEqualTo(ActionModel.Highlight)
        assertThat(fullRule.filter).isNull()
    }

    @Test
    fun toFullRule_preExisting_noFilterValues() {
        val preExistingRule =
            DraftRuleModel.PreExisting(
                id = 10,
                action = ActionModel.Highlight,
                filter = DraftFilterModel(includedApps = null, people = null, keywords = null),
            )

        val fullRule = preExistingRule.toFullRule()

        assertThat(fullRule.id).isEqualTo(10)
        assertThat(fullRule.action).isEqualTo(ActionModel.Highlight)
        assertThat(fullRule.filter).isNull()
    }

    companion object {
        private val FAKE_CONTACT =
            PersonModel.Contact(lookupUri = "key".toUri(), name = "name", photoUri = "uri".toUri())
        private val FAKE_APP =
            AppModel(
                uid = 13,
                label = "app label",
                icon = ShapeDrawable(),
                packageName = "app.name",
            )
    }
}
