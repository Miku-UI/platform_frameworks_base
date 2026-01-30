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

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.app.NotificationManager.IMPORTANCE_LOW
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.Person
import android.content.Context
import android.content.Intent
import android.content.applicationContext
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.UserHandle
import com.android.systemui.activity.EmptyTestActivity
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.res.R
import com.android.systemui.statusbar.notification.icon.IconPack
import com.android.systemui.statusbar.notification.people.PeopleNotificationIdentifier.Companion.PeopleNotificationType
import com.android.systemui.statusbar.notification.people.PeopleNotificationIdentifier.Companion.TYPE_FULL_PERSON
import com.android.systemui.statusbar.notification.people.PeopleNotificationIdentifier.Companion.TYPE_IMPORTANT_PERSON
import com.android.systemui.statusbar.notification.people.PeopleNotificationIdentifier.Companion.TYPE_NON_PERSON
import com.android.systemui.statusbar.notification.promoted.setPromotedContent
import kotlin.random.Random
import org.mockito.kotlin.mock

fun Kosmos.setIconPackWithMockIconViews(entry: NotificationEntry) {
    entry.icons =
        IconPack.buildPack(
            /* statusBarIcon = */ mock(),
            /* statusBarChipIcon = */ mock(),
            /* shelfIcon = */ mock(),
            /* aodIcon = */ mock(),
            /* source = */ null,
        )
}

fun Kosmos.buildPromotedOngoingEntry(
    block: NotificationEntryBuilder.() -> Unit = {}
): NotificationEntry =
    buildNotificationEntry(tag = "ron", promoted = true, style = null, block = block)

fun Kosmos.buildOngoingCallEntry(
    promoted: Boolean = false,
    block: NotificationEntryBuilder.() -> Unit = {},
): NotificationEntry =
    buildNotificationEntry(
        tag = "call",
        promoted = promoted,
        style = makeOngoingCallStyle(),
        block = block,
    )

fun Kosmos.buildNotificationEntry(
    tag: String? = null,
    promoted: Boolean = false,
    style: Notification.Style? = null,
    block: NotificationEntryBuilder.() -> Unit = {},
): NotificationEntry {
    return buildNotificationEntry(
        tag = tag,
        promoted = promoted,
        style = style,
        context = applicationContext,
        block = block,
    )
}

fun Kosmos.buildNotificationEntry(
    tag: String? = null,
    promoted: Boolean = false,
    style: Notification.Style? = null,
    context: Context?,
    block: NotificationEntryBuilder.() -> Unit = {},
): NotificationEntry {
    context ?: applicationContext
    return NotificationEntryBuilder(context)
        .apply {
            setTag(tag)
            setFlag(context, Notification.FLAG_PROMOTED_ONGOING, promoted)
            setChannel(NotificationChannel("messages", "messages", IMPORTANCE_DEFAULT))
            modifyNotification(context)
                .setSmallIcon(Icon.createWithResource(context, R.drawable.ic_device_fan))
                .setStyle(style)
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        0,
                        Intent(Intent.ACTION_VIEW),
                        FLAG_IMMUTABLE,
                    )
                )
                .setRequestPromotedOngoing(promoted)
            updateSbn {
                setId(Random.nextInt())
                setUser(UserHandle.of(ActivityManager.getCurrentUser()))
            }
        }
        .apply(block)
        .build()
        .also {
            setIconPackWithMockIconViews(it)
            if (promoted) setPromotedContent(it)
        }
}

@SuppressLint("MissingPermission")
fun Kosmos.buildNotificationEntry(
    notification: Notification,
    block: NotificationEntryBuilder.() -> Unit = {},
): NotificationEntry =
    NotificationEntryBuilder(applicationContext)
        .apply {
            setNotification(notification)
            updateSbn {
                setUser(UserHandle.of(ActivityManager.getCurrentUser()))
                setId(Random.nextInt())
            }
        }
        .apply(block)
        .build()
        .also { setIconPackWithMockIconViews(it) }

fun Kosmos.buildSummaryNotificationEntry(
    children: List<NotificationEntry>? = null,
    block: NotificationEntryBuilder.() -> Unit = {},
): NotificationEntry {
    val entry = buildNotificationEntry {
        modifyNotification(applicationContext).setGroupSummary(true).setGroup("groupId")
        updateRanking { it.setChannel(NotificationChannel("channel", "Channel", IMPORTANCE_HIGH)) }
        updateSbn {
            setTag("summary")
            setGroup(applicationContext, "groupId")
        }
        val groupEntryBuilder = GroupEntryBuilder()
        children?.forEach {
            groupEntryBuilder.addChild(it)
            it.sbn.overrideGroupKey = "groupId"
        }
        setParent(groupEntryBuilder.build())
        apply(block)
    }
    (entry.parent as? GroupEntry)?.summary = entry
    return entry
}

fun Kosmos.buildChildNotificationEntry(
    block: NotificationEntryBuilder.() -> Unit = {}
): NotificationEntry = buildNotificationEntry {
    modifyNotification(applicationContext).setGroupSummary(false).setGroup("groupId")
    updateRanking { it.setChannel(NotificationChannel("channel", "Channel", IMPORTANCE_HIGH)) }
    updateSbn {
        setTag("child")
        setGroup(applicationContext, "groupId")
    }
    apply(block)
}

private fun Kosmos.makeOngoingCallStyle(): Notification.CallStyle {
    val pendingIntent =
        PendingIntent.getBroadcast(
            applicationContext,
            0,
            Intent("action"),
            PendingIntent.FLAG_IMMUTABLE,
        )
    val person = Person.Builder().setName("person").build()
    return Notification.CallStyle.forOngoingCall(person, pendingIntent)
}

private fun Kosmos.makeMessagingStyleNotification(): Notification.Builder {
    val personIcon = Icon.createWithBitmap(Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888))
    val person = Person.Builder().setIcon(personIcon).setName("Person").build()
    val message = Notification.MessagingStyle.Message("Message!", 4323, person)
    val bubbleIntent =
        PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, EmptyTestActivity::class.java),
            PendingIntent.FLAG_MUTABLE,
        )

    return Notification.Builder(applicationContext, "channelId")
        .setSmallIcon(R.drawable.ic_person)
        .setContentTitle("Title")
        .setContentText("Text")
        .setShortcutId("shortcutId")
        .setStyle(Notification.MessagingStyle(person).addMessage(message))
        .setBubbleMetadata(
            Notification.BubbleMetadata.Builder(
                    bubbleIntent,
                    Icon.createWithResource(applicationContext, R.drawable.android),
                )
                .setDeleteIntent(mock<PendingIntent>())
                .setDesiredHeight(314)
                .setAutoExpandBubble(false)
                .build()
        )
}

fun Kosmos.makeEntryOfPeopleType(
    @PeopleNotificationType type: Int = TYPE_FULL_PERSON,
    block: NotificationEntryBuilder.() -> Unit = {},
): NotificationEntry {
    val channel = NotificationChannel("messages", "messages", IMPORTANCE_DEFAULT)
    channel.isImportantConversation = (type == TYPE_IMPORTANT_PERSON)
    channel.setConversationId("parent", "shortcutId")

    val shortcutInfo = ShortcutInfo.Builder(applicationContext).setId("shortcutId").build()

    return buildNotificationEntry {
        updateRanking {
            it.setIsConversation(type != TYPE_NON_PERSON)
            it.setShortcutInfo(if (type >= TYPE_FULL_PERSON) shortcutInfo else null)
            it.setChannel(channel)
        }
        setNotification(makeMessagingStyleNotification().build())
        apply(block)
    }
}

fun Kosmos.makeClassifiedConversation(channelId: String): NotificationEntry {
    val channel = NotificationChannel(channelId, channelId, IMPORTANCE_LOW)
    val entry =
        NotificationEntryBuilder()
            .updateRanking {
                it.setIsConversation(true)
                it.setShortcutInfo(mock())
                it.setChannel(channel)
            }
            .build()
    return entry
}
