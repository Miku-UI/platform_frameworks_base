/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.systemui.statusbar.notification.people

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.statusbar.notification.collection.BundleEntry
import com.android.systemui.statusbar.notification.collection.BundleSpec
import com.android.systemui.statusbar.notification.collection.GroupEntry
import com.android.systemui.statusbar.notification.collection.InternalNotificationsApi
import com.android.systemui.statusbar.notification.collection.buildSummaryNotificationEntry
import com.android.systemui.statusbar.notification.collection.makeEntryOfPeopleType
import com.android.systemui.statusbar.notification.collection.render.groupMembershipManager
import com.android.systemui.statusbar.notification.people.PeopleNotificationIdentifier.Companion.TYPE_IMPORTANT_PERSON
import com.android.systemui.statusbar.notification.people.PeopleNotificationIdentifier.Companion.TYPE_PERSON
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class PeopleNotificationIdentifierTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private lateinit var underTest: PeopleNotificationIdentifierImpl

    @Before
    fun setUp() {
        underTest = PeopleNotificationIdentifierImpl(kosmos.groupMembershipManager)
    }

    @Test
    fun getPeopleNotificationType_entryIsImportant() {
        assertThat(
                underTest.getPeopleNotificationType(
                    kosmos.makeEntryOfPeopleType(TYPE_IMPORTANT_PERSON)
                )
            )
            .isEqualTo(TYPE_IMPORTANT_PERSON)
    }

    @Test
    fun getPeopleNotificationType_importantChild() {
        val child = kosmos.makeEntryOfPeopleType(TYPE_PERSON)
        val child2 = kosmos.makeEntryOfPeopleType(TYPE_IMPORTANT_PERSON)
        val summary = kosmos.buildSummaryNotificationEntry(listOf(child, child2))

        assertThat(underTest.getPeopleNotificationType(summary)).isEqualTo(TYPE_IMPORTANT_PERSON)
    }

    @Test
    fun getPeopleNotificationType_noImportantChildren() {
        val child = kosmos.makeEntryOfPeopleType(TYPE_PERSON)
        val child2 = kosmos.makeEntryOfPeopleType(TYPE_PERSON)
        val summary = kosmos.buildSummaryNotificationEntry(listOf(child, child2))

        assertThat(underTest.getPeopleNotificationType(summary)).isEqualTo(TYPE_PERSON)
    }

    @Test
    fun getPeopleNotificationType_nestedGroup() {
        val child = kosmos.makeEntryOfPeopleType(TYPE_PERSON)
        val innerSummary = kosmos.buildSummaryNotificationEntry(listOf(child))
        val child1 = kosmos.makeEntryOfPeopleType(TYPE_PERSON)
        val child2 = kosmos.makeEntryOfPeopleType(TYPE_IMPORTANT_PERSON)
        val summary = kosmos.buildSummaryNotificationEntry(listOf(child1, child2, innerSummary))

        assertThat(underTest.getPeopleNotificationType(summary)).isEqualTo(TYPE_IMPORTANT_PERSON)
    }

    @Test
    fun getPeopleNotificationType_circularGroup() {
        val child = kosmos.makeEntryOfPeopleType(TYPE_PERSON)
        val innerSummary = kosmos.buildSummaryNotificationEntry(listOf(child))
        val child1 = kosmos.makeEntryOfPeopleType(TYPE_PERSON)
        val child2 = kosmos.makeEntryOfPeopleType(TYPE_IMPORTANT_PERSON)
        val summary = kosmos.buildSummaryNotificationEntry(listOf(child1, child2, innerSummary))
        (summary.parent as? GroupEntry)?.rawChildren?.add(summary)

        assertThat(underTest.getPeopleNotificationType(summary)).isEqualTo(TYPE_IMPORTANT_PERSON)
    }

    @OptIn(InternalNotificationsApi::class)
    @Test
    fun getPeopleNotificationType_groupInBundle() {
        val bundleEntry = BundleEntry(BundleSpec.NEWS)
        val child1 = kosmos.makeEntryOfPeopleType(TYPE_PERSON)
        val child2 = kosmos.makeEntryOfPeopleType(TYPE_IMPORTANT_PERSON)
        val summary = kosmos.buildSummaryNotificationEntry(listOf(child1, child2))
        summary.parent?.parent = bundleEntry
        bundleEntry.addChild(summary)

        assertThat(underTest.getPeopleNotificationType(summary)).isEqualTo(TYPE_IMPORTANT_PERSON)
    }
}
