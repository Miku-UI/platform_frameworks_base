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

package com.android.systemui.statusbar.notification.row

import android.app.Notification
import android.app.Person
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper
import androidx.core.graphics.drawable.toBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.res.R
import com.android.systemui.statusbar.notification.row.ui.viewmodel.ConversationAvatar
import com.android.systemui.statusbar.notification.row.ui.viewmodel.FacePile
import com.android.systemui.statusbar.notification.row.ui.viewmodel.SingleIcon
import com.android.systemui.statusbar.notification.row.ui.viewmodel.SingleLineViewModel
import com.android.systemui.statusbar.notification.row.ui.viewmodel.SingleLineViewPayload
import com.android.systemui.statusbar.notification.shared.Metric
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper
class SingleLineViewInflaterTest : SysuiTestCase() {
    // Non-group MessagingStyles only have firstSender
    private lateinit var firstSender: Person
    private lateinit var lastSender: Person
    private lateinit var firstSenderIcon: Icon
    private lateinit var lastSenderIcon: Icon
    private var firstSenderIconDrawable: Drawable? = null
    private var lastSenderIconDrawable: Drawable? = null
    private val currentUser: Person? = null

    private companion object {
        const val FIRST_SENDER_NAME = "First Sender"
        const val LAST_SENDER_NAME = "Second Sender"
        const val LAST_MESSAGE = "How about lunch?"

        const val CONVERSATION_TITLE = "The Sender Family"
        const val CONTENT_TITLE = "A Cool Group"
        const val CONTENT_TEXT = "This is an amazing group chat"

        const val REPLY_ONE = "reply one of two"
        const val REPLY_TWO = "reply two of two"

        const val SHORTCUT_ID = "Shortcut"
    }

    @Before
    fun setUp() {
        firstSenderIcon = Icon.createWithBitmap(getBitmap(context, R.drawable.ic_person))
        firstSenderIconDrawable = firstSenderIcon.loadDrawable(context)
        lastSenderIcon =
            Icon.createWithBitmap(
                getBitmap(context, com.android.internal.R.drawable.ic_account_circle)
            )
        lastSenderIconDrawable = lastSenderIcon.loadDrawable(context)
        firstSender = Person.Builder().setName(FIRST_SENDER_NAME).setIcon(firstSenderIcon).build()
        lastSender = Person.Builder().setName(LAST_SENDER_NAME).setIcon(lastSenderIcon).build()
    }

    @Test
    fun createViewModelForNonConversationSingleLineView() {
        // Given: a non-conversation notification
        val notificationType = NonMessaging()
        val notification = getNotification(NonMessaging())

        // When: inflate the SingleLineViewModel
        val singleLineViewModel = notification.makeSingleLineViewModel(notificationType)

        // Then: the inflated SingleLineViewModel should be as expected
        // conversationData: null, because it's not a conversation notification
        assertThat(singleLineViewModel)
            .isEqualTo(
                SingleLineViewModel(
                    CONTENT_TITLE,
                    CONTENT_TEXT,
                    null,
                    SingleLineViewPayload.StandardPayload,
                )
            )
    }

    @Test
    fun createViewModelForEmptyNotification() {
        // Given: a non-conversation notification with no title and no text
        val notificationType = Empty()
        val notification = getNotification(notificationType)

        // When: inflate the SingleLineViewModel
        val singleLineViewModel = notification.makeSingleLineViewModel(notificationType)

        // Then: the view model should show the placeholder title
        val expectedTitle = context.getString(R.string.empty_notification_single_line_title)
        assertThat(singleLineViewModel.titleText).isEqualTo(expectedTitle)
        assertThat(singleLineViewModel.contentText).isNull()
        assertThat(singleLineViewModel.payload)
            .isInstanceOf(SingleLineViewPayload.StandardPayload::class.java)
    }

    @Test
    fun createViewModelForNonGroupConversationNotification() {
        // Given: a non-group conversation notification
        val notificationType = OneToOneConversation()
        val notification = getNotification(notificationType)

        // When: inflate the SingleLineViewModel
        val singleLineViewModel = notification.makeSingleLineViewModel(notificationType)

        // Then: the inflated SingleLineViewModel should be as expected
        // titleText: Notification.ConversationTitle
        // contentText: the last message text
        // conversationSenderName: null, because it's not a group conversation
        // conversationData.avatar: a single icon of the last sender
        assertThat(singleLineViewModel.titleText).isEqualTo(CONVERSATION_TITLE)
        assertThat(singleLineViewModel.contentText).isEqualTo(LAST_MESSAGE)
        assertThat(singleLineViewModel.payload)
            .isInstanceOf(SingleLineViewPayload.ConversationData::class.java)
        val conversationData = singleLineViewModel.payload as SingleLineViewPayload.ConversationData
        // Sender name should be null for one-on-one conversation
        assertThat(conversationData.conversationSenderName).isNull()
        assertThat(
                conversationData.avatar.equalsTo(SingleIcon(firstSenderIcon.loadDrawable(context)))
            )
            .isTrue()
    }

    @Test
    fun createViewModelForNonGroupConversationNotification_withRemoteInputHistory() {
        // Given: a non-group conversation notification
        val notificationType = OneToOneConversation()
        val notification =
            getNotification(notificationType) { builder ->
                builder.setRemoteInputHistory(arrayOf<CharSequence>(REPLY_ONE, REPLY_TWO))
            }

        // When: inflate the SingleLineViewModel
        val singleLineViewModel = notification.makeSingleLineViewModel(notificationType)

        // Then: the inflated SingleLineViewModel should be as expected
        // titleText: Notification.ConversationTitle
        // contentText: the second reply text
        // conversationSenderName: "User:" because the last message is a remote input
        // conversationData.avatar: a single icon of the last sender
        assertThat(singleLineViewModel.titleText).isEqualTo(CONVERSATION_TITLE)
        assertThat(singleLineViewModel.contentText).isEqualTo(REPLY_TWO)
        assertThat(singleLineViewModel.payload)
            .isInstanceOf(SingleLineViewPayload.ConversationData::class.java)
        val conversationData = singleLineViewModel.payload as SingleLineViewPayload.ConversationData
        // Sender name should be "User:" because the last message is a remote input
        assertThat(conversationData.conversationSenderName).isEqualTo("User:")
        assertThat(
                conversationData.avatar.equalsTo(SingleIcon(firstSenderIcon.loadDrawable(context)))
            )
            .isTrue()
    }

    @Test
    fun createViewModelForNonGroupLegacyMessagingStyleNotification() {
        // Given: a non-group legacy messaging style notification
        val notificationType = LegacyMessaging()
        val notification = getNotification(notificationType)

        // When: inflate the SingleLineViewModel
        val singleLineViewModel = notification.makeSingleLineViewModel(notificationType)

        // Then: the inflated SingleLineViewModel should be as expected
        // titleText: CONVERSATION_TITLE: SENDER_NAME
        // contentText: the last message text
        // conversationData: null, because it's not a conversation notification
        assertThat(singleLineViewModel.titleText)
            .isEqualTo("$CONVERSATION_TITLE: $FIRST_SENDER_NAME")
        assertThat(singleLineViewModel.contentText).isEqualTo(LAST_MESSAGE)
        // Legacy messaging conversation should have standard payload
        assertThat(singleLineViewModel.payload)
            .isInstanceOf(SingleLineViewPayload.StandardPayload::class.java)
    }

    @Test
    fun createViewModelForGroupLegacyMessagingStyleNotification() {
        // Given: a non-group legacy messaging style notification
        val notificationType = LegacyMessagingGroup()
        val notification = getNotification(notificationType)

        // When: inflate the SingleLineViewModel
        val singleLineViewModel = notification.makeSingleLineViewModel(notificationType)

        // Then: the inflated SingleLineViewModel should be as expected
        // titleText: CONVERSATION_TITLE: LAST_SENDER_NAME
        // contentText: the last message text
        // conversationData: null, because it's not a conversation notification
        assertThat(singleLineViewModel.titleText)
            .isEqualTo("$CONVERSATION_TITLE: $LAST_SENDER_NAME")
        assertThat(singleLineViewModel.contentText).isEqualTo(LAST_MESSAGE)
        // Legacy messaging conversation should have standard payload.
        assertThat(singleLineViewModel.payload)
            .isInstanceOf(SingleLineViewPayload.StandardPayload::class.java)
    }

    @Test
    fun createViewModelForNonGroupConversationNotificationWithShortcutIcon() {
        // Given: a non-group conversation notification with a shortcut icon
        val shortcutIcon =
            Icon.createWithResource(context, com.android.internal.R.drawable.ic_account_circle)
        val notificationType = OneToOneConversation(shortcutIcon = shortcutIcon)
        val notification = getNotification(notificationType)

        // When: inflate the SingleLineViewModel
        val singleLineViewModel = notification.makeSingleLineViewModel(notificationType)

        // Then: the inflated SingleLineViewModel should be expected
        // titleText: Notification.ConversationTitle
        // contentText: the last message text
        // conversationSenderName: null, because it's not a group conversation
        // conversationData.avatar: a single icon of the shortcut icon
        assertThat(singleLineViewModel.titleText).isEqualTo(CONVERSATION_TITLE)
        assertThat(singleLineViewModel.contentText).isEqualTo(LAST_MESSAGE)
        assertThat(singleLineViewModel.payload)
            .isInstanceOf(SingleLineViewPayload.ConversationData::class.java)
        val conversationData = singleLineViewModel.payload as SingleLineViewPayload.ConversationData

        // Sender name should be null for one-on-one conversation
        assertThat(conversationData.conversationSenderName).isNull()
        assertThat(conversationData.avatar.equalsTo(SingleIcon(shortcutIcon.loadDrawable(context))))
            .isTrue()
    }

    @Test
    fun createViewModelForGroupConversationNotificationWithLargeIcon() {
        // Given: a group conversation notification with a large icon
        val largeIcon =
            Icon.createWithResource(context, com.android.internal.R.drawable.ic_account_circle)
        val notificationType = GroupConversation(largeIcon = largeIcon)
        val notification = getNotification(notificationType)

        // When: inflate the SingleLineViewModel
        val singleLineViewModel = notification.makeSingleLineViewModel(notificationType)

        // Then: the inflated SingleLineViewModel should be expected
        // titleText: Notification.ConversationTitle
        // contentText: the last message text
        // conversationSenderName: the last non-user sender's name
        // conversationData.avatar: a single icon
        assertThat(singleLineViewModel.titleText).isEqualTo(CONVERSATION_TITLE)
        assertThat(singleLineViewModel.contentText).isEqualTo(LAST_MESSAGE)
        assertThat(singleLineViewModel.payload)
            .isInstanceOf(SingleLineViewPayload.ConversationData::class.java)

        val conversationData = singleLineViewModel.payload as SingleLineViewPayload.ConversationData
        assertThat(conversationData.conversationSenderName)
            .isEqualTo(
                context.resources.getString(
                    com.android.internal.R.string.conversation_single_line_name_display,
                    LAST_SENDER_NAME,
                )
            )
        assertThat(conversationData.avatar.equalsTo(SingleIcon(largeIcon.loadDrawable(context))))
            .isTrue()
    }

    @Test
    fun createViewModelForGroupConversationNotificationWithLargeIcon_withRemoteInputHistory() {
        // Given: a group conversation notification with a large icon
        val largeIcon =
            Icon.createWithResource(context, com.android.internal.R.drawable.ic_account_circle)
        val notificationType = GroupConversation(largeIcon = largeIcon)
        val notification =
            getNotification(notificationType) { builder ->
                builder.setRemoteInputHistory(arrayOf<CharSequence>(REPLY_ONE, REPLY_TWO))
            }

        // When: inflate the SingleLineViewModel
        val singleLineViewModel = notification.makeSingleLineViewModel(notificationType)

        // Then: the inflated SingleLineViewModel should be expected
        // titleText: Notification.ConversationTitle
        // contentText: the second reply text
        // conversationSenderName: the user's name
        // conversationData.avatar: a single icon
        assertThat(singleLineViewModel.titleText).isEqualTo(CONVERSATION_TITLE)
        assertThat(singleLineViewModel.contentText).isEqualTo(REPLY_TWO)
        assertThat(singleLineViewModel.payload)
            .isInstanceOf(SingleLineViewPayload.ConversationData::class.java)

        val conversationData = singleLineViewModel.payload as SingleLineViewPayload.ConversationData
        assertThat(conversationData.conversationSenderName).isEqualTo("User:")
        assertThat(conversationData.avatar.equalsTo(SingleIcon(largeIcon.loadDrawable(context))))
            .isTrue()
    }

    @Test
    fun createViewModelForGroupConversationWithNoIcon() {
        // Given: a group conversation notification
        val notificationType = GroupConversation()
        val notification = getNotification(notificationType)

        // When: inflate the SingleLineViewModel
        val singleLineViewModel = notification.makeSingleLineViewModel(notificationType)

        // Then: the inflated SingleLineViewModel should be expected
        // titleText: Notification.ConversationTitle
        // contentText: the last message text
        // conversationSenderName: the last non-user sender's name
        // conversationData.avatar: a face-pile consists the last sender's icon
        assertThat(singleLineViewModel.titleText).isEqualTo(CONVERSATION_TITLE)
        assertThat(singleLineViewModel.contentText).isEqualTo(LAST_MESSAGE)
        assertThat(singleLineViewModel.payload)
            .isInstanceOf(SingleLineViewPayload.ConversationData::class.java)
        val conversationData = singleLineViewModel.payload as SingleLineViewPayload.ConversationData
        assertThat(conversationData.conversationSenderName)
            .isEqualTo(
                context.resources.getString(
                    com.android.internal.R.string.conversation_single_line_name_display,
                    LAST_SENDER_NAME,
                )
            )

        val backgroundColor =
            Notification.Builder.recoverBuilder(context, notification)
                .getBackgroundColor(/* isHeader= */ false)
        assertThat(
                conversationData.avatar.equalsTo(
                    FacePile(
                        topIconDrawable = firstSenderIconDrawable,
                        bottomIconDrawable = lastSenderIconDrawable,
                        bottomBackgroundColor = backgroundColor,
                    )
                )
            )
            .isTrue()
    }

    @Test
    fun createViewModelForSummarizedConversationNotification() {
        // Given: a non-group conversation notification
        val notificationType = OneToOneConversation()
        val notification = getNotification(notificationType)

        // When: inflate the SingleLineViewModel
        val singleLineViewModel = notification.makeSingleLineViewModel(notificationType, true)

        // Then: the inflated SingleLineViewModel should be as expected
        // titleText: Notification.ConversationTitle
        // contentText: the last message text
        // conversationSenderName: null, because it's not a group conversation
        // conversationData.avatar: a single icon of the last sender
        // summarizedText: the summary text from the ranking
        assertThat(singleLineViewModel.titleText).isEqualTo(CONVERSATION_TITLE)
        assertThat(singleLineViewModel.contentText).isEqualTo(LAST_MESSAGE)
        assertThat(singleLineViewModel.payload)
            .isInstanceOf(SingleLineViewPayload.ConversationData::class.java)
        val conversationData = singleLineViewModel.payload as SingleLineViewPayload.ConversationData
        assertThat(conversationData.conversationSenderName).isNull()
        assertThat(
                conversationData.avatar.equalsTo(SingleIcon(firstSenderIcon.loadDrawable(context)))
            )
            .isTrue()
        assertThat(conversationData.summarization).isEqualTo("summary")
    }

    @Test
    fun createViewModelForPublicConversationNotification() {
        // When: a public conversation notification is inflated
        val singleLineViewModel =
            SingleLineViewInflater.inflatePublicSingleLineViewModel(context, isConversation = true)

        // Then: the view model should be populated with the correct data
        assertThat(singleLineViewModel.payload)
            .isInstanceOf(SingleLineViewPayload.ConversationData::class.java)
        val conversationData = singleLineViewModel.payload as SingleLineViewPayload.ConversationData

        val expectedIcon = context.getDrawable(R.drawable.ic_public_notification_single_line_icon)
        assertThat(conversationData.avatar.equalsTo(SingleIcon(expectedIcon))).isTrue()
        val expectedTitle = context.getString(R.string.public_notification_single_line_title)
        assertThat(singleLineViewModel.titleText).isEqualTo(expectedTitle)
        assertThat(singleLineViewModel.contentText).isNull()
    }

    @Test
    @EnableFlags(
        android.app.Flags.FLAG_API_METRIC_STYLE,
        android.app.Flags.FLAG_METRIC_VALUE_ALTERNATIVE_STRINGS,
    )
    fun createViewModelForMetricSingleLineView() {
        // Given: a MetricStyle notification
        val testMetric = Notification.Metric(Notification.Metric.FixedInt(1245), "Steps")
        val notificationType = MetricNotification(testMetric)
        val notification = getNotification(notificationType)

        // When: inflate the SingleLineViewModel
        val singleLineViewModel = notification.makeSingleLineViewModel(notificationType)

        // Then: the inflated SingleLineViewModel should be as expected
        assertThat(singleLineViewModel.titleText).isEqualTo(CONTENT_TITLE)
        assertThat(singleLineViewModel.contentText).isNull()
        assertThat(singleLineViewModel.payload)
            .isInstanceOf(SingleLineViewPayload.MetricPayload::class.java)
        val metric = (singleLineViewModel.payload as? SingleLineViewPayload.MetricPayload)?.data
        assertThat(metric).isNotNull()
        assertThat(metric?.label).isEqualTo("Steps")
        assertThat(metric).isInstanceOf(Metric.Text::class.java)
        assertThat((metric as Metric.Text).textVariants).containsExactly("1,245", "1.2K")
    }

    sealed class NotificationType(val largeIcon: Icon? = null)

    class NonMessaging(largeIcon: Icon? = null) : NotificationType(largeIcon)

    class Empty(largeIcon: Icon? = null) : NotificationType(largeIcon)

    class LegacyMessaging(largeIcon: Icon? = null) : NotificationType(largeIcon)

    class LegacyMessagingGroup(largeIcon: Icon? = null) : NotificationType(largeIcon)

    class OneToOneConversation(largeIcon: Icon? = null, val shortcutIcon: Icon? = null) :
        NotificationType(largeIcon)

    class GroupConversation(largeIcon: Icon? = null) : NotificationType(largeIcon)

    class MetricNotification(val metric: Notification.Metric) : NotificationType()

    private fun getNotification(
        type: NotificationType,
        extraBuild: (Notification.Builder) -> Unit = {},
    ): Notification {
        val notificationBuilder: Notification.Builder =
            Notification.Builder(mContext, "channelId")
                .setSmallIcon(R.drawable.ic_person)
                .setContentTitle(CONTENT_TITLE)
                .setContentText(CONTENT_TEXT)
                .setLargeIcon(type.largeIcon)
                .also(extraBuild)

        val user = Person.Builder().setName("User").build()

        val buildMessagingStyle =
            Notification.MessagingStyle(user)
                .setConversationTitle(CONVERSATION_TITLE)
                .addMessage("Hi", 0, currentUser)

        return when (type) {
            is NonMessaging ->
                notificationBuilder
                    .setStyle(Notification.BigTextStyle().bigText("Big Text"))
                    .build()

            is Empty -> notificationBuilder.setContentTitle(null).setContentText(null).build()
            is LegacyMessaging -> {
                buildMessagingStyle
                    .addMessage("What's up?", 0, firstSender)
                    .addMessage("Not much", 0, currentUser)
                    .addMessage(LAST_MESSAGE, 0, firstSender)

                val notification = notificationBuilder.setStyle(buildMessagingStyle).build()

                assertThat(notification.shortcutId).isNull()
                notification
            }

            is LegacyMessagingGroup -> {
                buildMessagingStyle
                    .addMessage("What's up?", 0, firstSender)
                    .addMessage("Check out my new hover board!", 0, lastSender)
                    .setGroupConversation(true)
                    .addMessage(LAST_MESSAGE, 0, lastSender)

                val notification = notificationBuilder.setStyle(buildMessagingStyle).build()

                assertThat(notification.shortcutId).isNull()
                notification
            }

            is OneToOneConversation -> {
                buildMessagingStyle
                    .addMessage("What's up?", 0, firstSender)
                    .addMessage("Not much", 0, currentUser)
                    .addMessage(LAST_MESSAGE, 0, firstSender)
                    .setShortcutIcon(type.shortcutIcon)
                notificationBuilder.setShortcutId(SHORTCUT_ID).setStyle(buildMessagingStyle).build()
            }

            is GroupConversation -> {
                buildMessagingStyle
                    .addMessage("What's up?", 0, firstSender)
                    .addMessage("Check out my new hover board!", 0, lastSender)
                    .setGroupConversation(true)
                    .addMessage(LAST_MESSAGE, 0, lastSender)
                notificationBuilder.setShortcutId(SHORTCUT_ID).setStyle(buildMessagingStyle).build()
            }

            is MetricNotification -> {
                val style = Notification.MetricStyle()
                style.addMetric(type.metric)
                notificationBuilder.setStyle(style).build()
            }
        }
    }

    private fun Notification.makeSingleLineViewModel(
        type: NotificationType,
        includeSummarization: Boolean = false,
    ): SingleLineViewModel {
        val builder = Notification.Builder.recoverBuilder(context, this)

        // Validate the recovered builder has the right type of style
        val expectMessagingStyle =
            when (type) {
                is LegacyMessaging,
                is LegacyMessagingGroup,
                is OneToOneConversation,
                is GroupConversation -> true

                else -> false
            }
        if (expectMessagingStyle) {
            assertThat(builder.style).isInstanceOf(Notification.MessagingStyle::class.java)
        } else {
            assertThat(builder.style).isNotInstanceOf(Notification.MessagingStyle::class.java)
        }

        // Inflate the SingleLineViewModel
        // Mock the behavior of NotificationRowContentBinder.doInBackground
        val messagingStyle = builder.getMessagingStyle()
        val isConversation = type is OneToOneConversation || type is GroupConversation
        val metricStyle = builder.style as? Notification.MetricStyle
        val isMetric = type is MetricNotification
        return SingleLineViewInflater.inflateSingleLineViewModel(
            this,
            if (isConversation) messagingStyle else null,
            if (isMetric) metricStyle else null, // Pass metricStyle
            builder,
            context,
            false,
            if (includeSummarization) "summary" else null,
        )
    }

    private fun Notification.Builder.getMessagingStyle(): Notification.MessagingStyle? {
        return style as? Notification.MessagingStyle
    }

    private fun getBitmap(context: Context, resId: Int): Bitmap {
        val largeIconDimension =
            context.resources.getDimension(R.dimen.conversation_single_line_avatar_size)
        val d = context.resources.getDrawable(resId)
        val b =
            Bitmap.createBitmap(
                largeIconDimension.toInt(),
                largeIconDimension.toInt(),
                Bitmap.Config.ARGB_8888,
            )
        val c = Canvas(b)
        val paint = Paint()
        c.drawCircle(
            largeIconDimension / 2,
            largeIconDimension / 2,
            largeIconDimension.coerceAtMost(largeIconDimension) / 2,
            paint,
        )
        d.setBounds(0, 0, largeIconDimension.toInt(), largeIconDimension.toInt())
        paint.setXfermode(PorterDuffXfermode(PorterDuff.Mode.SRC_IN))
        c.saveLayer(0F, 0F, largeIconDimension, largeIconDimension, paint, Canvas.ALL_SAVE_FLAG)
        d.draw(c)
        c.restore()
        return b
    }

    fun ConversationAvatar.equalsTo(other: ConversationAvatar?): Boolean =
        when {
            this === other -> true
            this is SingleIcon && other is SingleIcon -> equalsTo(other)
            this is FacePile && other is FacePile -> equalsTo(other)
            else -> false
        }

    private fun SingleIcon.equalsTo(other: SingleIcon): Boolean =
        iconDrawable?.equalsTo(other.iconDrawable) == true

    private fun FacePile.equalsTo(other: FacePile): Boolean =
        when {
            bottomBackgroundColor != other.bottomBackgroundColor -> false
            topIconDrawable?.equalsTo(other.topIconDrawable) != true -> false
            bottomIconDrawable?.equalsTo(other.bottomIconDrawable) != true -> false
            else -> true
        }

    private fun Drawable.equalsTo(other: Drawable?): Boolean =
        when {
            this === other -> true
            this.pixelsEqualTo(other) -> true
            else -> false
        }

    private fun <T : Drawable> T.pixelsEqualTo(t: T?) =
        toBitmap().pixelsEqualTo(t?.toBitmap(), false)

    private fun Bitmap.pixelsEqualTo(otherBitmap: Bitmap?, shouldRecycle: Boolean = false) =
        otherBitmap?.let { other ->
            if (width == other.width && height == other.height) {
                val res = toPixels().contentEquals(other.toPixels())
                if (shouldRecycle) {
                    doRecycle().also { otherBitmap.doRecycle() }
                }
                res
            } else false
        } ?: kotlin.run { false }

    private fun Bitmap.toPixels() =
        IntArray(width * height).apply { getPixels(this, 0, width, 0, 0, width, height) }

    fun Bitmap.doRecycle() {
        if (!isRecycled) recycle()
    }
}
