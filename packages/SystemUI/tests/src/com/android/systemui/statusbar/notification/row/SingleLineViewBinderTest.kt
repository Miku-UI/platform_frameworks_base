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
import android.app.Notification.Metric
import android.app.Notification.MetricStyle
import android.app.Person
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper
import android.view.View.GONE
import androidx.core.view.isVisible
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.R
import com.android.systemui.SysuiTestCase
import com.android.systemui.statusbar.notification.NmSummarizationAllFlag
import com.android.systemui.statusbar.notification.collection.buildNotificationEntry
import com.android.systemui.statusbar.notification.row.NotificationRowContentBinder.FLAG_CONTENT_VIEW_PUBLIC_SINGLE_LINE
import com.android.systemui.statusbar.notification.row.NotificationRowContentBinder.FLAG_CONTENT_VIEW_SINGLE_LINE
import com.android.systemui.statusbar.notification.row.SingleLineViewInflater.inflatePrivateSingleLineView
import com.android.systemui.statusbar.notification.row.SingleLineViewInflater.inflatePublicSingleLineView
import com.android.systemui.statusbar.notification.row.ui.viewbinder.SingleLineViewBinder
import com.android.systemui.statusbar.notification.row.ui.viewmodel.SingleLineViewPayload
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper
class SingleLineViewBinderTest : SysuiTestCase() {
    private lateinit var notificationBuilder: Notification.Builder
    private val kosmos = testKosmos()

    @Before
    fun setUp() {
        allowTestableLooperAsMainThread()
        notificationBuilder = Notification.Builder(mContext, CHANNEL_ID)
        notificationBuilder
            .setSmallIcon(R.drawable.ic_corp_icon)
            .setContentTitle(CONTENT_TITLE)
            .setContentText(CONTENT_TEXT)
    }

    @Test
    fun bindNonConversationSingleLineView() {
        // GIVEN: a row with bigText style notification
        val style = Notification.BigTextStyle().bigText(CONTENT_TEXT)
        notificationBuilder.setStyle(style)
        val notification = notificationBuilder.build()
        val entry = kosmos.buildNotificationEntry(notification)

        val view =
            inflatePrivateSingleLineView(
                payload = SingleLineViewPayload.StandardPayload,
                reinflateFlags = FLAG_CONTENT_VIEW_SINGLE_LINE,
                entry = entry,
                context = context,
                logger = mock(),
            )

        val publicView =
            inflatePublicSingleLineView(
                payload = SingleLineViewPayload.StandardPayload,
                reinflateFlags = FLAG_CONTENT_VIEW_PUBLIC_SINGLE_LINE,
                entry = entry,
                context = context,
                logger = mock(),
            )
        assertThat(publicView).isNotNull()

        val viewModel =
            SingleLineViewInflater.inflateSingleLineViewModel(
                notification = notification,
                messagingStyle = null,
                metricStyle = null,
                builder = notificationBuilder,
                systemUiContext = context,
                redactText = false,
                summarization = null,
            )

        // WHEN: binds the viewHolder
        SingleLineViewBinder.bind(viewModel, view)

        // THEN: the single-line view should be bind with viewModel's title and content text
        assertThat(view?.titleView?.text).isEqualTo(viewModel.titleText)
        assertThat(view?.textView?.text).isEqualTo(viewModel.contentText)
    }

    @Test
    fun bindGroupConversationSingleLineView() {
        // GIVEN a row with a group conversation notification
        val user =
            Person.Builder()
                //                .setIcon(Icon.createWithResource(mContext,
                // R.drawable.ic_account_circle))
                .setName(USER_NAME)
                .build()
        val style =
            Notification.MessagingStyle(user)
                .addMessage(MESSAGE_TEXT, System.currentTimeMillis(), user)
                .addMessage(
                    "How about lunch?",
                    System.currentTimeMillis(),
                    Person.Builder().setName("user2").build(),
                )
                .setGroupConversation(true)
        notificationBuilder.setStyle(style).setShortcutId(SHORTCUT_ID)
        val notification = notificationBuilder.build()
        val entry = kosmos.buildNotificationEntry(notification)

        val view =
            inflatePrivateSingleLineView(
                payload =
                    SingleLineViewPayload.ConversationData(
                        conversationSenderName = mock(),
                        avatar = mock(),
                        summarization = mock(),
                    ),
                reinflateFlags = FLAG_CONTENT_VIEW_SINGLE_LINE,
                entry = entry,
                context = context,
                logger = mock(),
            )
                as HybridConversationNotificationView

        val publicView =
            inflatePublicSingleLineView(
                payload =
                    SingleLineViewPayload.ConversationData(
                        conversationSenderName = mock(),
                        avatar = mock(),
                        summarization = mock(),
                    ),
                reinflateFlags = FLAG_CONTENT_VIEW_PUBLIC_SINGLE_LINE,
                entry = entry,
                context = context,
                logger = mock(),
            )
                as HybridConversationNotificationView
        assertThat(publicView).isNotNull()

        val viewModel =
            SingleLineViewInflater.inflateSingleLineViewModel(
                notification = notification,
                messagingStyle = style,
                metricStyle = null,
                builder = notificationBuilder,
                systemUiContext = context,
                redactText = false,
                summarization = null,
            )
        // WHEN: binds the view
        SingleLineViewBinder.bind(viewModel, view)

        // THEN: the single-line conversation view should be bound with view model's corresponding
        // fields
        assertThat(view.titleView?.text).isEqualTo(viewModel.titleText)
        assertThat(view.textView?.text).isEqualTo(viewModel.contentText)
        assertThat(view.conversationSenderNameView.text)
            .isEqualTo(
                (viewModel.payload as? SingleLineViewPayload.ConversationData)
                    ?.conversationSenderName
            )
    }

    @Test
    fun bindConversationSingleLineView_nonConversationViewModel() {
        // GIVEN: a ConversationSingleLineView, and a nonConversationViewModel
        val style = Notification.BigTextStyle().bigText(CONTENT_TEXT)
        notificationBuilder.setStyle(style)
        val notification = notificationBuilder.build()
        val entry = kosmos.buildNotificationEntry(notification)

        val view =
            inflatePrivateSingleLineView(
                payload =
                    SingleLineViewPayload.ConversationData(
                        conversationSenderName = mock(),
                        avatar = mock(),
                        summarization = mock(),
                    ),
                reinflateFlags = FLAG_CONTENT_VIEW_SINGLE_LINE,
                entry = entry,
                context = context,
                logger = mock(),
            )

        val publicView =
            inflatePublicSingleLineView(
                payload =
                    SingleLineViewPayload.ConversationData(
                        conversationSenderName = mock(),
                        avatar = mock(),
                        summarization = mock(),
                    ),
                reinflateFlags = FLAG_CONTENT_VIEW_PUBLIC_SINGLE_LINE,
                entry = entry,
                context = context,
                logger = mock(),
            )
        assertThat(publicView).isNotNull()

        val viewModel =
            SingleLineViewInflater.inflateSingleLineViewModel(
                notification = notification,
                messagingStyle = null,
                metricStyle = null,
                builder = notificationBuilder,
                systemUiContext = context,
                redactText = false,
                summarization = null,
            )
        // WHEN: binds the view with the view model
        SingleLineViewBinder.bind(viewModel, view)

        // THEN: the single-line view should be bound with view model's corresponding
        // fields as a normal non-conversation single-line view
        assertThat(view?.titleView?.text).isEqualTo(viewModel.titleText)
        assertThat(view?.textView?.text).isEqualTo(viewModel.contentText)
        assertThat((viewModel.payload as? SingleLineViewPayload.ConversationData)).isNull()
    }

    @Test
    fun bindSummarizedGroupConversationSingleLineView() {
        // GIVEN a row with a group conversation notification
        val user = Person.Builder().setName(USER_NAME).build()
        val style =
            Notification.MessagingStyle(user)
                .addMessage(MESSAGE_TEXT, System.currentTimeMillis(), user)
                .addMessage(
                    "How about lunch?",
                    System.currentTimeMillis(),
                    Person.Builder().setName("user2").build(),
                )
                .setGroupConversation(true)
        notificationBuilder.setStyle(style).setShortcutId(SHORTCUT_ID)
        val notification = notificationBuilder.build()
        val entry =
            kosmos.buildNotificationEntry(notification) {
                updateRanking { it.setSummarization(SUMMARIZATION) }
            }

        val view =
            inflatePrivateSingleLineView(
                payload =
                    SingleLineViewPayload.ConversationData(
                        conversationSenderName = mock(),
                        avatar = mock(),
                        summarization = mock(),
                    ),
                reinflateFlags = FLAG_CONTENT_VIEW_SINGLE_LINE,
                entry = entry,
                context = context,
                logger = mock(),
            )
                as HybridConversationNotificationView

        val publicView =
            inflatePublicSingleLineView(
                payload =
                    SingleLineViewPayload.ConversationData(
                        conversationSenderName = mock(),
                        avatar = mock(),
                        summarization = mock(),
                    ),
                reinflateFlags = FLAG_CONTENT_VIEW_PUBLIC_SINGLE_LINE,
                entry = entry,
                context = context,
                logger = mock(),
            )
                as HybridConversationNotificationView
        assertThat(publicView).isNotNull()

        val viewModel =
            SingleLineViewInflater.inflateSingleLineViewModel(
                notification = notification,
                messagingStyle = style,
                metricStyle = null,
                builder = notificationBuilder,
                systemUiContext = context,
                redactText = false,
                summarization = SUMMARIZATION,
            )
        // WHEN: binds the view
        SingleLineViewBinder.bind(viewModel, view)

        // THEN: the single-line conversation view should only include summarization content
        assertThat(view.textView?.text).isEqualTo(SUMMARIZATION)
        assertThat(view.conversationSenderNameView.text).isEqualTo("")
        assertThat(view.conversationSenderNameView.visibility).isEqualTo(GONE)
    }

    @Test
    @EnableFlags(NmSummarizationAllFlag.FLAG_NAME)
    fun bindNonConversationSingleLineView_withSummarization() {
        // GIVEN: a row with bigText style notification
        val style = Notification.BigTextStyle().bigText(CONTENT_TEXT)
        notificationBuilder.setStyle(style)
        notificationBuilder.setSummarizedContent(SUMMARIZATION)
        val notification = notificationBuilder.build()
        val entry = kosmos.buildNotificationEntry(notification)

        val view =
            inflatePrivateSingleLineView(
                payload = SingleLineViewPayload.StandardPayload,
                reinflateFlags = FLAG_CONTENT_VIEW_SINGLE_LINE,
                entry = entry,
                context = context,
                logger = mock(),
            )

        val publicView =
            inflatePublicSingleLineView(
                payload = SingleLineViewPayload.StandardPayload,
                reinflateFlags = FLAG_CONTENT_VIEW_PUBLIC_SINGLE_LINE,
                entry = entry,
                context = context,
                logger = mock(),
            )
        assertThat(publicView).isNotNull()

        val viewModel =
            SingleLineViewInflater.inflateSingleLineViewModel(
                notification = notification,
                messagingStyle = null,
                metricStyle = null,
                builder = notificationBuilder,
                systemUiContext = context,
                redactText = false,
                summarization = SUMMARIZATION,
            )

        // WHEN: binds the viewHolder
        SingleLineViewBinder.bind(viewModel, view)

        // THEN: the single-line view should be bind with viewModel's title and summarization text
        assertThat(view?.titleView?.text).isEqualTo(viewModel.titleText)
        assertThat(view?.textView?.text).isEqualTo(viewModel.summarization)
        assertThat(viewModel.summarization).isEqualTo(SUMMARIZATION)
    }

    @Test
    @EnableFlags(android.app.Flags.FLAG_API_METRIC_STYLE)
    fun bindMetricSingleLineView_noTitle() {
        // GIVEN: a row with MetricStyle notification with Text metric
        val metric = Metric(Metric.FixedInt(1245), "Steps")
        val style = MetricStyle()
        style.addMetric(metric)
        notificationBuilder.setStyle(style)
        notificationBuilder.setContentTitle(null)
        val notification = notificationBuilder.build()
        val entry = kosmos.buildNotificationEntry(notification)

        val view =
            inflatePrivateSingleLineView(
                payload = SingleLineViewPayload.MetricPayload(data = mock()),
                reinflateFlags = FLAG_CONTENT_VIEW_SINGLE_LINE,
                entry = entry,
                context = context,
                logger = mock(),
            )
                as HybridMetricNotificationView

        val viewModel =
            SingleLineViewInflater.inflateSingleLineViewModel(
                notification = notification,
                messagingStyle = null,
                metricStyle = style,
                builder = notificationBuilder,
                systemUiContext = context,
                redactText = false,
                summarization = null,
            )

        // WHEN: binds the viewHolder
        SingleLineViewBinder.bind(viewModel, view)

        // THEN: the single-line view should be bind with viewModel's title and metric data
        assertThat(view.titleView?.text).isEqualTo("Steps")
        assertThat(view.textView?.text).isEqualTo("")

        assertThat(view.metricLabel.text).isEqualTo("")
        assertThat(view.metricLabel.isVisible).isFalse()
        assertThat(view.metricChronometer.isVisible).isFalse()
        assertThat(view.metricValue.isVisible).isTrue()
        assertThat(view.metricValue.text).isEqualTo("1,245")
    }

    @Test
    @EnableFlags(android.app.Flags.FLAG_API_METRIC_STYLE)
    fun bindMetricSingleLineView_withText() {
        // GIVEN: a row with MetricStyle notification with Text metric
        val metric = Metric(Metric.FixedInt(1245), "Steps")
        val style = MetricStyle()
        style.addMetric(metric)
        notificationBuilder.setStyle(style)
        notificationBuilder.setContentTitle("Title")
        val notification = notificationBuilder.build()
        val entry = kosmos.buildNotificationEntry(notification)

        val view =
            inflatePrivateSingleLineView(
                payload = SingleLineViewPayload.MetricPayload(data = mock()),
                reinflateFlags = FLAG_CONTENT_VIEW_SINGLE_LINE,
                entry = entry,
                context = context,
                logger = mock(),
            )
                as HybridMetricNotificationView

        val viewModel =
            SingleLineViewInflater.inflateSingleLineViewModel(
                notification = notification,
                messagingStyle = null,
                metricStyle = style,
                builder = notificationBuilder,
                systemUiContext = context,
                redactText = false,
                summarization = null,
            )

        // WHEN: binds the viewHolder
        SingleLineViewBinder.bind(viewModel, view)

        // THEN: the single-line view should be bind with viewModel's title and metric data
        assertThat(view.titleView?.text).isEqualTo("Title")
        assertThat(view.textView?.text).isEqualTo("")

        assertThat(view.metricLabel.text).isEqualTo("Steps")
        assertThat(view.metricChronometer.isVisible).isFalse()
        assertThat(view.metricValue.isVisible).isTrue()
        assertThat(view.metricValue.text).isEqualTo("1,245")
    }

    @Test
    @EnableFlags(android.app.Flags.FLAG_API_METRIC_STYLE)
    fun bindMetricSingleLineView_withTimeDifference() {
        // GIVEN: a row with MetricStyle notification with TimeDifference metric
        val metric =
            Metric(
                Metric.TimeDifference.forPausedStopwatch(
                    Duration.ofMinutes(10),
                    Metric.TimeDifference.FORMAT_CHRONOMETER,
                ),
                "Timer",
            )
        val style = MetricStyle()
        style.addMetric(metric)
        notificationBuilder.setStyle(style)
        notificationBuilder.setContentTitle("Title")
        val notification = notificationBuilder.build()
        val entry = kosmos.buildNotificationEntry(notification)

        val view =
            inflatePrivateSingleLineView(
                payload = SingleLineViewPayload.MetricPayload(data = mock()),
                reinflateFlags = FLAG_CONTENT_VIEW_SINGLE_LINE,
                entry = entry,
                context = context,
                logger = mock(),
            )
                as HybridMetricNotificationView

        val viewModel =
            SingleLineViewInflater.inflateSingleLineViewModel(
                notification = notification,
                messagingStyle = null,
                metricStyle = style,
                builder = notificationBuilder,
                systemUiContext = context,
                redactText = false,
                summarization = null,
            )

        // WHEN: binds the viewHolder
        SingleLineViewBinder.bind(viewModel, view)

        // THEN: the single-line view should be bind with viewModel's title and metric data
        assertThat(view.titleView?.text).isEqualTo("Title")
        assertThat(view.textView?.text).isEqualTo("")

        assertThat(view.metricLabel.isVisible).isTrue()
        assertThat(view.metricLabel.text).isEqualTo("Timer")
        assertThat(view.metricChronometer.isVisible).isTrue()
        assertThat(view.metricChronometer.text).isEqualTo("10:00")
        assertThat(view.metricValue.isVisible).isFalse()
    }

    private companion object {
        const val CHANNEL_ID = "CHANNEL_ID"
        const val CONTENT_TITLE = "A Cool New Feature"
        const val CONTENT_TEXT = "Checkout out new feature!"
        const val USER_NAME = "USER_NAME"
        const val MESSAGE_TEXT = "MESSAGE_TEXT"
        const val SHORTCUT_ID = "Shortcut"
        const val SUMMARIZATION = "summarization"
    }
}
