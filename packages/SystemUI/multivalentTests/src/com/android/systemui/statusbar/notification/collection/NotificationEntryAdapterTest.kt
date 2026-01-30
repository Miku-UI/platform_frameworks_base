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

package com.android.systemui.statusbar.notification.collection

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannel.NEWS_ID
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.UserHandle
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.service.notification.NotificationListenerService.REASON_CANCEL
import android.testing.TestableLooper.RunWithLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.res.R
import com.android.systemui.statusbar.RankingBuilder
import com.android.systemui.statusbar.notification.collection.coordinator.mockVisualStabilityCoordinator
import com.android.systemui.statusbar.notification.collection.notifcollection.NotifLifetimeExtender
import com.android.systemui.statusbar.notification.collection.provider.mockHighPriorityProvider
import com.android.systemui.statusbar.notification.mockNotificationActivityStarter
import com.android.systemui.statusbar.notification.people.PeopleNotificationIdentifier.Companion.TYPE_FULL_PERSON
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
import com.android.systemui.statusbar.notification.row.entryAdapterFactory
import com.android.systemui.statusbar.notification.row.mockNotificationActionClickManager
import com.android.systemui.statusbar.notification.row.onUserInteractionCallback
import com.android.systemui.statusbar.notification.shared.NotificationBundleUi
import com.android.systemui.statusbar.notification.stack.BUCKET_ALERTING
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
@EnableFlags(NotificationBundleUi.FLAG_NAME)
class NotificationEntryAdapterTest : SysuiTestCase() {
    private val kosmos = testKosmos().apply { onUserInteractionCallback = mock() }

    private val factory: EntryAdapterFactory = kosmos.entryAdapterFactory
    private lateinit var underTest: NotificationEntryAdapter

    @get:Rule val setFlagsRule = SetFlagsRule()

    @Test
    fun getBackingHashCode() {
        val entry = NotificationEntryBuilder().build()

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.backingHashCode).isEqualTo(entry.hashCode())
    }

    @Test
    fun getParent_adapter() {
        val ge = GroupEntryBuilder().build()
        val notification: Notification =
            Notification.Builder(mContext, "").setSmallIcon(R.drawable.ic_person).build()

        val entry =
            NotificationEntryBuilder()
                .setNotification(notification)
                .setUser(UserHandle(ActivityManager.getCurrentUser()))
                .setParent(ge)
                .build()

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.parent).isEqualTo(entry.parent)
    }

    @Test
    fun isTopLevelEntry_adapter() {
        val notification: Notification =
            Notification.Builder(mContext, "").setSmallIcon(R.drawable.ic_person).build()

        val entry =
            NotificationEntryBuilder()
                .setNotification(notification)
                .setUser(UserHandle(ActivityManager.getCurrentUser()))
                .setParent(GroupEntry.ROOT_ENTRY)
                .build()

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.isTopLevelEntry).isTrue()
    }

    @Test
    fun getKey_adapter() {
        val notification: Notification =
            Notification.Builder(mContext, "").setSmallIcon(R.drawable.ic_person).build()

        val entry =
            NotificationEntryBuilder()
                .setNotification(notification)
                .setUser(UserHandle(ActivityManager.getCurrentUser()))
                .build()

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.key).isEqualTo(entry.key)
    }

    @Test
    fun getRow_adapter() {
        val row: ExpandableNotificationRow = mock()
        val notification: Notification =
            Notification.Builder(mContext, "").setSmallIcon(R.drawable.ic_person).build()

        val entry =
            NotificationEntryBuilder()
                .setNotification(notification)
                .setUser(UserHandle(ActivityManager.getCurrentUser()))
                .build()
        entry.row = row

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.row).isEqualTo(entry.row)
    }

    @Test
    fun isGroupRoot_adapter_groupSummary() {
        val row: ExpandableNotificationRow = mock()
        val notification: Notification =
            Notification.Builder(mContext, "")
                .setSmallIcon(R.drawable.ic_person)
                .setGroupSummary(true)
                .setGroup("key")
                .build()

        val entry =
            NotificationEntryBuilder()
                .setNotification(notification)
                .setUser(UserHandle(ActivityManager.getCurrentUser()))
                .setParent(GroupEntry.ROOT_ENTRY)
                .build()
        entry.row = row

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.isGroupRoot).isFalse()
    }

    @Test
    fun isGroupRoot_adapter_groupChild() {
        val notification: Notification =
            Notification.Builder(mContext, "")
                .setSmallIcon(R.drawable.ic_person)
                .setGroupSummary(true)
                .setGroup("key")
                .build()

        val parent = NotificationEntryBuilder().setParent(GroupEntry.ROOT_ENTRY).build()
        val groupEntry = GroupEntryBuilder().setSummary(parent)

        val entry =
            NotificationEntryBuilder()
                .setNotification(notification)
                .setUser(UserHandle(ActivityManager.getCurrentUser()))
                .setParent(groupEntry.build())
                .build()

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.isGroupRoot).isFalse()
    }

    @Test
    fun isClearable_adapter() {
        val row: ExpandableNotificationRow = mock()
        val notification: Notification =
            Notification.Builder(mContext, "").setSmallIcon(R.drawable.ic_person).build()

        val entry =
            NotificationEntryBuilder()
                .setNotification(notification)
                .setUser(UserHandle(ActivityManager.getCurrentUser()))
                .build()
        entry.row = row

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.isClearable).isEqualTo(entry.isClearable)
    }

    @Test
    fun getSummarization_adapter() {
        val row: ExpandableNotificationRow = mock()
        val notification: Notification =
            Notification.Builder(mContext, "").setSmallIcon(R.drawable.ic_person).build()

        val entry =
            NotificationEntryBuilder()
                .setNotification(notification)
                .setUser(UserHandle(ActivityManager.getCurrentUser()))
                .build()
        val ranking = RankingBuilder(entry.ranking).setSummarization("hello").build()
        entry.setRanking(ranking)
        entry.row = row

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.summarization).isEqualTo("hello")
    }

    @Test
    fun getIcons_adapter() {
        val row: ExpandableNotificationRow = mock()
        val notification: Notification =
            Notification.Builder(mContext, "").setSmallIcon(R.drawable.ic_person).build()

        val entry =
            NotificationEntryBuilder()
                .setNotification(notification)
                .setUser(UserHandle(ActivityManager.getCurrentUser()))
                .build()
        entry.row = row

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.icons).isEqualTo(entry.icons)
    }

    @Test
    fun isColorized() {
        val notification: Notification =
            Notification.Builder(mContext, "")
                .setSmallIcon(R.drawable.ic_person)
                .setColorized(true)
                .build()

        val entry = NotificationEntryBuilder().setNotification(notification).build()

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.isColorized).isEqualTo(entry.sbn.notification.isColorized)
    }

    @Test
    fun getSbn() {
        val notification: Notification =
            Notification.Builder(mContext, "").setSmallIcon(R.drawable.ic_person).build()

        val entry = NotificationEntryBuilder().setNotification(notification).build()

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.sbn).isEqualTo(entry.sbn)
    }

    @Test
    fun getRanking() {
        val notification: Notification =
            Notification.Builder(mContext, "")
                .setSmallIcon(R.drawable.ic_person)
                .addAction(Mockito.mock(Notification.Action::class.java))
                .build()

        val entry = NotificationEntryBuilder().setNotification(notification).build()

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.ranking).isEqualTo(entry.ranking)
    }

    @Test
    fun endLifetimeExtension() {
        val notification: Notification =
            Notification.Builder(mContext, "")
                .setSmallIcon(R.drawable.ic_person)
                .addAction(Mockito.mock(Notification.Action::class.java))
                .build()

        val entry = NotificationEntryBuilder().setNotification(notification).build()
        val callback =
            Mockito.mock(NotifLifetimeExtender.OnEndLifetimeExtensionCallback::class.java)

        underTest = factory.create(entry) as NotificationEntryAdapter
        underTest.endLifetimeExtension(callback, Mockito.mock(NotifLifetimeExtender::class.java))
        verify(callback).onEndLifetimeExtension(any(), eq(entry))
    }

    @Test
    fun onImportanceChanged() {
        val notification: Notification =
            Notification.Builder(mContext, "")
                .setSmallIcon(R.drawable.ic_person)
                .addAction(Mockito.mock(Notification.Action::class.java))
                .build()

        val entry = NotificationEntryBuilder().setNotification(notification).build()

        underTest = factory.create(entry) as NotificationEntryAdapter

        underTest.onImportanceChanged()
        verify(kosmos.mockVisualStabilityCoordinator)
            .temporarilyAllowSectionChanges(eq(entry), anyLong())
    }

    @Test
    fun markForUserTriggeredMovement() {
        val notification: Notification =
            Notification.Builder(mContext, "")
                .setSmallIcon(R.drawable.ic_person)
                .addAction(Mockito.mock(Notification.Action::class.java))
                .build()

        val entry = NotificationEntryBuilder().setNotification(notification).build()
        underTest = factory.create(entry) as NotificationEntryAdapter

        assertThat(underTest.isMarkedForUserTriggeredMovement)
            .isEqualTo(entry.isMarkedForUserTriggeredMovement)

        underTest.markForUserTriggeredMovement(true)
        assertThat(underTest.isMarkedForUserTriggeredMovement)
            .isEqualTo(entry.isMarkedForUserTriggeredMovement)
    }

    @Test
    fun isHighPriority() {
        val notification: Notification =
            Notification.Builder(mContext, "")
                .setSmallIcon(R.drawable.ic_person)
                .addAction(Mockito.mock(Notification.Action::class.java))
                .build()

        val entry = NotificationEntryBuilder().setNotification(notification).build()
        underTest = factory.create(entry) as NotificationEntryAdapter

        underTest.isHighPriority

        verify(kosmos.mockHighPriorityProvider).isHighPriority(entry)
    }

    @Test
    fun isBlockable() {
        val notification: Notification =
            Notification.Builder(mContext, "")
                .setSmallIcon(R.drawable.ic_person)
                .addAction(Mockito.mock(Notification.Action::class.java))
                .build()

        val entry = NotificationEntryBuilder().setNotification(notification).build()
        underTest = factory.create(entry) as NotificationEntryAdapter

        assertThat(underTest.isBlockable).isEqualTo(entry.isBlockable)
    }

    @Test
    fun canDragAndDrop() {
        val pi: PendingIntent = mock()
        Mockito.`when`(pi.isActivity).thenReturn(true)
        val notification: Notification =
            Notification.Builder(mContext, "")
                .setSmallIcon(R.drawable.ic_person)
                .setContentIntent(pi)
                .build()

        val entry = NotificationEntryBuilder().setNotification(notification).build()

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.canDragAndDrop()).isTrue()
    }

    @Test
    fun isBubble() {
        val notification: Notification =
            Notification.Builder(mContext, "")
                .setSmallIcon(R.drawable.ic_person)
                .setFlag(Notification.FLAG_BUBBLE, true)
                .build()

        val entry = NotificationEntryBuilder().setNotification(notification).build()

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.isBubble).isEqualTo(entry.isBubble)
    }

    @Test
    fun getStyle() {
        val notification: Notification =
            Notification.Builder(mContext, "")
                .setSmallIcon(R.drawable.ic_person)
                .setStyle(Notification.BigTextStyle())
                .build()

        val entry = NotificationEntryBuilder().setNotification(notification).build()

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.style).isEqualTo(entry.notificationStyle)
    }

    @Test
    fun getSectionBucket() {
        val notification: Notification =
            Notification.Builder(mContext, "")
                .setSmallIcon(R.drawable.ic_person)
                .setStyle(Notification.BigTextStyle())
                .build()

        val entry = NotificationEntryBuilder().setNotification(notification).build()
        entry.bucket = BUCKET_ALERTING

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.sectionBucket).isEqualTo(BUCKET_ALERTING)
    }

    @Test
    fun isAmbient() {
        val notification: Notification =
            Notification.Builder(mContext, "").setSmallIcon(R.drawable.ic_person).build()

        val entry =
            NotificationEntryBuilder()
                .setNotification(notification)
                .setImportance(NotificationManager.IMPORTANCE_MIN)
                .build()

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.isAmbient).isTrue()
    }

    @Test
    fun getPeopleNotificationType() {
        val entry = kosmos.makeEntryOfPeopleType()

        underTest = factory.create(entry) as NotificationEntryAdapter

        assertThat(underTest.peopleNotificationType).isEqualTo(TYPE_FULL_PERSON)
    }

    @Test
    fun canShowFullScreen() {
        val notification: Notification =
            Notification.Builder(mContext, "")
                .setSmallIcon(R.drawable.ic_person)
                .setFullScreenIntent(mock(), true)
                .build()

        val entry =
            NotificationEntryBuilder()
                .setNotification(notification)
                .setImportance(NotificationManager.IMPORTANCE_MIN)
                .build()

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.isFullScreenCapable).isTrue()
    }

    @Test
    fun onDragSuccess() {
        val notification: Notification =
            Notification.Builder(mContext, "")
                .setSmallIcon(R.drawable.ic_person)
                .addAction(mock())
                .build()
        val entry = NotificationEntryBuilder().setNotification(notification).build()

        underTest = factory.create(entry) as NotificationEntryAdapter

        underTest.onDragSuccess()
        verify(kosmos.mockNotificationActivityStarter).onDragSuccess(entry)
    }

    @Test
    fun onNotificationBubbleIconClicked() {
        val notification: Notification =
            Notification.Builder(mContext, "").setSmallIcon(R.drawable.ic_person).build()

        val entry = NotificationEntryBuilder().setNotification(notification).build()

        underTest = factory.create(entry) as NotificationEntryAdapter

        underTest.onNotificationBubbleIconClicked()
        verify(kosmos.mockNotificationActivityStarter).onNotificationBubbleIconClicked(entry)
    }

    @Test
    fun onNotificationActionClicked() {
        val notification: Notification =
            Notification.Builder(mContext, "")
                .setSmallIcon(R.drawable.ic_person)
                .addAction(mock())
                .build()

        val entry = NotificationEntryBuilder().setNotification(notification).build()

        underTest = factory.create(entry) as NotificationEntryAdapter
        underTest.onNotificationActionClicked()
        verify(kosmos.mockNotificationActionClickManager).onNotificationActionClicked(entry)
    }

    @Test
    fun isParentDismissed() {
        val notification: Notification =
            Notification.Builder(mContext, "").setSmallIcon(R.drawable.ic_person).build()

        val entry = NotificationEntryBuilder().setNotification(notification).build()
        entry.dismissState = NotificationEntry.DismissState.PARENT_DISMISSED

        underTest = factory.create(entry) as NotificationEntryAdapter

        assertThat(underTest.isParentDismissed).isTrue()
    }

    @Test
    fun onEntryClicked() {
        val notification: Notification =
            Notification.Builder(mContext, "")
                .setSmallIcon(R.drawable.ic_person)
                .addAction(mock())
                .build()
        val entry = NotificationEntryBuilder().setNotification(notification).build()
        val row: ExpandableNotificationRow = mock()

        underTest = factory.create(entry) as NotificationEntryAdapter

        underTest.onEntryClicked(row)
        verify(kosmos.mockNotificationActivityStarter).onNotificationClicked(entry, row)
    }

    @Test
    fun registerFutureDismissal() {
        val notification: Notification =
            Notification.Builder(mContext, "")
                .setSmallIcon(R.drawable.ic_person)
                .addAction(mock())
                .build()
        val entry = NotificationEntryBuilder().setNotification(notification).build()
        val callback = kosmos.onUserInteractionCallback
        whenever(callback.registerFutureDismissal(any<NotificationEntry>(), any()))
            .thenReturn(mock())

        underTest = factory.create(entry) as NotificationEntryAdapter

        underTest.registerFutureDismissal()
        verify(callback).registerFutureDismissal(entry, REASON_CANCEL)
    }

    fun getRemoteInputEntryAdapter() {
        val notification: Notification =
            Notification.Builder(mContext, "").setSmallIcon(R.drawable.ic_person).build()

        val entry = NotificationEntryBuilder().setNotification(notification).build()

        underTest = factory.create(entry) as NotificationEntryAdapter

        assertThat(underTest.remoteInputEntryAdapter)
            .isSameInstanceAs(entry.remoteInputEntryAdapter)
    }

    @Test
    fun isBundled() {
        val notification: Notification =
            Notification.Builder(mContext, "").setSmallIcon(R.drawable.ic_person).build()

        val entry =
            NotificationEntryBuilder()
                .setNotification(notification)
                .setChannel(NotificationChannel(NEWS_ID, NEWS_ID, 2))
                .build()

        underTest = factory.create(entry) as NotificationEntryAdapter
        assertThat(underTest.isBundled).isTrue()
    }

    @Test
    fun onBundleDisabled_individualNotification() {
        val notification: Notification =
            Notification.Builder(mContext, "")
                .setSmallIcon(R.drawable.ic_person)
                .addAction(Mockito.mock(Notification.Action::class.java))
                .build()

        val entry = NotificationEntryBuilder().setNotification(notification).build()
        underTest = factory.create(entry) as NotificationEntryAdapter

        underTest.onBundleDisabledForEntry()
        verify(kosmos.mockVisualStabilityCoordinator)
            .temporarilyAllowFreeMovement(eq(entry), anyLong())
    }

    @Test
    fun onBundleDisabled_groupRoot() {
        val summaryRow: ExpandableNotificationRow = mock()
        val childRow: ExpandableNotificationRow = mock()
        val summary: Notification =
            Notification.Builder(mContext, "")
                .setSmallIcon(R.drawable.ic_person)
                .setGroupSummary(true)
                .setGroup("key")
                .build()

        val child: Notification =
            Notification.Builder(mContext, "")
                .setSmallIcon(R.drawable.ic_person)
                .addAction(Mockito.mock(Notification.Action::class.java))
                .setGroup("key")
                .build()

        val group = GroupEntryBuilder().setParent(GroupEntry.ROOT_ENTRY).build()

        val summaryEntry =
            NotificationEntryBuilder().setNotification(summary).setParent(group).build()
        group.setSummary(summaryEntry)
        summaryEntry.row = summaryRow

        val childEntry = NotificationEntryBuilder().setNotification(child).setParent(group).build()
        childEntry.row = childRow
        val childAdapter = factory.create(childEntry) as NotificationEntryAdapter
        whenever(childRow.entryAdapter).thenReturn(childAdapter)
        whenever(summaryRow.attachedChildren).thenReturn(listOf(childRow))

        underTest = factory.create(summaryEntry) as NotificationEntryAdapter
        underTest.onBundleDisabledForEntry()
        verify(kosmos.mockVisualStabilityCoordinator)
            .temporarilyAllowFreeMovement(eq(summaryEntry), anyLong())
        verify(kosmos.mockVisualStabilityCoordinator)
            .temporarilyAllowFreeMovement(eq(childEntry), anyLong())
    }
}
