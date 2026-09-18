/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.statusbar.notification.collection.render

import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.statusbar.notification.collection.EntryAdapterFactoryImpl
import com.android.systemui.statusbar.notification.collection.GroupEntry
import com.android.systemui.statusbar.notification.collection.GroupEntryBuilder
import com.android.systemui.statusbar.notification.collection.NotificationEntryBuilder
import com.android.systemui.statusbar.notification.row.entryAdapterFactory
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class GroupMembershipManagerTest : SysuiTestCase() {

    @get:Rule val setFlagsRule = SetFlagsRule()

    private val kosmos = testKosmos()
    private val factory: EntryAdapterFactoryImpl = kosmos.entryAdapterFactory

    private var underTest = GroupMembershipManagerImpl()

    @Test
    fun isChildEntryAdapterInGroup_topLevel() {
        val topLevelEntry = NotificationEntryBuilder().setParent(GroupEntry.ROOT_ENTRY).build()
        assertThat(underTest.isChildInGroup(factory.create(topLevelEntry))).isFalse()
    }

    @Test
    fun isChildEntryAdapterInGroup_noParent() {
        val noParentEntry = NotificationEntryBuilder().setParent(null).build()
        assertThat(underTest.isChildInGroup(factory.create(noParentEntry))).isFalse()
    }

    @Test
    fun isChildEntryAdapterInGroup_summary() {
        val groupKey = "group"
        val summary =
            NotificationEntryBuilder()
                .setGroup(mContext, groupKey)
                .setGroupSummary(mContext, true)
                .build()
        GroupEntryBuilder().setKey(groupKey).setSummary(summary).build()

        assertThat(underTest.isChildInGroup(factory.create(summary))).isFalse()
    }

    @Test
    fun isChildEntryAdapterInGroup_child() {
        val groupKey = "group"
        val summary =
            NotificationEntryBuilder()
                .setGroup(mContext, groupKey)
                .setGroupSummary(mContext, true)
                .build()
        val entry = NotificationEntryBuilder().setGroup(mContext, groupKey).build()
        GroupEntryBuilder().setKey(groupKey).setSummary(summary).addChild(entry).build()

        assertThat(underTest.isChildInGroup(factory.create(entry))).isTrue()
    }

    @Test
    fun isGroupRoot_topLevelEntry() {
        val entry = NotificationEntryBuilder().setParent(GroupEntry.ROOT_ENTRY).build()
        assertThat(underTest.isGroupRoot(factory.create(entry))).isFalse()
    }

    @Test
    fun isGroupRoot_summary() {
        val groupKey = "group"
        val summary =
            NotificationEntryBuilder()
                .setGroup(mContext, groupKey)
                .setGroupSummary(mContext, true)
                .build()
        GroupEntryBuilder().setKey(groupKey).setSummary(summary).build()

        assertThat(underTest.isGroupRoot(factory.create(summary))).isTrue()
    }

    @Test
    fun isGroupRoot_child() {
        val groupKey = "group"
        val summary =
            NotificationEntryBuilder()
                .setGroup(mContext, groupKey)
                .setGroupSummary(mContext, true)
                .build()
        val entry = NotificationEntryBuilder().setGroup(mContext, groupKey).build()
        GroupEntryBuilder().setKey(groupKey).setSummary(summary).addChild(entry).build()

        assertThat(underTest.isGroupRoot(factory.create(entry))).isFalse()
    }
}
