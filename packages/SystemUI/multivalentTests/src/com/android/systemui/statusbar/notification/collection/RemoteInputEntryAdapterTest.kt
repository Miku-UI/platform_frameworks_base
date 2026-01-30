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

import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.app.RemoteInput
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.testing.TestableLooper.RunWithLooper
import android.view.ContentInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.res.R
import com.android.systemui.statusbar.notification.headsup.HeadsUpManager
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
import com.android.systemui.statusbar.notification.row.entryAdapterFactory
import com.android.systemui.statusbar.notification.shared.NotificationBundleUi
import com.android.systemui.testKosmos
import com.android.systemui.wmshell.BubblesTestActivity
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
@EnableFlags(NotificationBundleUi.FLAG_NAME)
class RemoteInputEntryAdapterTest : SysuiTestCase() {
    private val kosmos = testKosmos()

    private val factory: EntryAdapterFactory = kosmos.entryAdapterFactory
    private lateinit var entry : NotificationEntry
    private lateinit var underTest: RemoteInputEntryAdapter

    @get:Rule val setFlagsRule = SetFlagsRule()

    @Before
    fun before() {
        entry =
            NotificationEntryBuilder()
                .setNotification(getConversationNotif().build())
                .build()

        underTest = (factory.create(entry) as NotificationEntryAdapter).remoteInputEntryAdapter
    }

    /** Creates a [Notification.Builder] that is a conversation.  */
    private fun getConversationNotif(): Notification.Builder {
        val timeContent = "content at ${System.currentTimeMillis()}"
        val intent = Intent(context, BubblesTestActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_MUTABLE
        )

        val person = Person.Builder()
            .setName("PERSON")
            .build()
        val remoteInput = RemoteInput.Builder("reply_key").setLabel("reply").build()
        val inputIntent = PendingIntent.getActivity(
            mContext,
            0,
            Intent().setPackage(mContext.packageName),
            PendingIntent.FLAG_MUTABLE
        )
        val icon = Icon.createWithResource(
            mContext,
            R.drawable.ic_android
        )
        val replyAction = Notification.Action.Builder(
            icon,
            "Reply",
            inputIntent
        ).addRemoteInput(remoteInput)
            .build()

        return Notification.Builder(context, "channel")
            .setSmallIcon(R.drawable.ic_android)
            .setContentTitle("Test Notification")
            .setContentText(timeContent)
            .setContentIntent(pendingIntent)
            .setActions(replyAction)
            .setStyle(
                Notification.MessagingStyle(person)
                    .setConversationTitle("Chat")
                    .addMessage(
                        timeContent,
                        System.currentTimeMillis(),
                        person
                    )
            )
    }

    @Test
    fun getKey() {
        assertThat(underTest.key).isEqualTo(entry.key)
    }

    @Test
    fun getStyle() {
        assertThat(underTest.style).isEqualTo(entry.notificationStyle)
    }

    @Test
    fun rowExists() {
        assertThat(underTest.rowExists).isFalse()

        entry.row = mock()
        assertThat(underTest.rowExists).isTrue()
    }

    @Test
    fun getRow() {
        val row : ExpandableNotificationRow = mock()
        entry.row = row

        assertThat(underTest.row).isEqualTo(row)
    }

    @Test
    fun closeRemoteInput() {
        val row : ExpandableNotificationRow = mock()
        entry.row = row

        underTest.closeRemoteInput()

        verify(row).closeRemoteInput()
    }

    @Test
    fun isRemoteInputAnimatingAway() {
        assertThat(underTest.remoteInputAnimatingAway).isEqualTo(false)

        entry.mRemoteEditImeAnimatingAway = true
        assertThat(underTest.remoteInputAnimatingAway).isEqualTo(true)
    }

    @Test
    fun setRemoteInputAnimatingAway() {
        assertThat(underTest.remoteInputAnimatingAway).isEqualTo(false)

        underTest.remoteInputAnimatingAway = true
        assertThat(entry.mRemoteEditImeAnimatingAway).isEqualTo(true)
    }

    @Test
    fun isRemoteInputImeVisible() {
        assertThat(underTest.remoteInputImeVisible).isEqualTo(false)

        entry.mRemoteEditImeVisible = true
        assertThat(underTest.remoteInputImeVisible).isEqualTo(true)
    }

    @Test
    fun setRemoteInputImeVisible() {
        assertThat(underTest.remoteInputImeVisible).isEqualTo(false)

        underTest.remoteInputImeVisible = true
        assertThat(entry.mRemoteEditImeVisible).isEqualTo(true)
    }

    @Test
    fun getRemoteInputAttachment() {
        val contentInfo : ContentInfo = mock()
        entry.remoteInputAttachment = contentInfo

        assertThat(underTest.remoteInputAttachment).isEqualTo(contentInfo)
    }

    @Test
    fun setRemoteInputAttachment() {
        val contentInfo : ContentInfo = mock()

        underTest.remoteInputAttachment = contentInfo

        assertThat(entry.remoteInputAttachment).isEqualTo(contentInfo)
    }

    @Test
    fun setRemoteInputUri() {
        val uri : Uri = mock()

        underTest.remoteInputUri = uri

        assertThat(entry.remoteInputUri).isEqualTo(uri)
    }

    @Test
    fun setRemoteInputMimeType() {
        val mimeType = "mime"

        underTest.remoteInputMimeType = mimeType

        assertThat(entry.remoteInputMimeType).isEqualTo(mimeType)
    }

    @Test
    fun setRemoteInputText() {
        val text = "TEXT"
        underTest.remoteInputText = text

        assertThat(entry.remoteInputText).isEqualTo(text)
    }

    @Test
    fun getRemoteInputText() {
        val text = "TEXT"
        entry.remoteInputText = text

        assertThat(underTest.remoteInputText).isEqualTo(text)
    }

    @Test
    fun setRemoteInputTextWhenReset() {
        val text = "TEXT"
        underTest.remoteInputTextWhenReset = text

        assertThat(entry.remoteInputTextWhenReset).isEqualTo(text)
    }

    @Test
    fun isSameEntryAs() {
        assertThat(underTest.isSameEntryAs(entry)).isTrue()

        val entryCopy =
            NotificationEntryBuilder()
                .setNotification(getConversationNotif().build())
                .build()

        assertThat(underTest.isSameEntryAs(entryCopy)).isFalse()
    }

    @Test
    fun notifyHeightChanged() {
        val row : ExpandableNotificationRow = mock()
        entry.row = row

        underTest.notifyHeightChanged(true)

        verify(row).notifyHeightChanged(true)
    }

    fun setRemoteInputActive(headsUpManager: HeadsUpManager, isActive: Boolean) {
        val headsUpManager : HeadsUpManager = mock()
        val mockEntry: NotificationEntry = mock()
        underTest = (factory.create(mockEntry) as NotificationEntryAdapter).remoteInputEntryAdapter

        underTest.setRemoteInputActive(headsUpManager, true)

        verify(headsUpManager).setRemoteInputActive(mockEntry, true)
    }
}
