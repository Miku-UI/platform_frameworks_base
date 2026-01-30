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

package com.android.systemui.statusbar.notification

import android.app.Flags
import android.app.Notification
import android.app.Notification.EXTRA_SUMMARIZED_CONTENT
import android.content.pm.LauncherApps
import android.content.pm.launcherApps
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper.RunWithLooper
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import android.text.style.StyleSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.statusbar.RankingBuilder
import com.android.systemui.statusbar.notification.collection.buildNotificationEntry
import com.android.systemui.statusbar.notification.collection.makeEntryOfPeopleType
import com.android.systemui.statusbar.notification.row.NotificationRowContentBinderLogger
import com.android.systemui.statusbar.notification.row.notificationRowContentBinderLogger
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
class ConversationNotificationProcessorTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private lateinit var conversationNotificationProcessor: ConversationNotificationProcessor
    private lateinit var launcherApps: LauncherApps
    private lateinit var logger: NotificationRowContentBinderLogger
    private lateinit var conversationNotificationManager: ConversationNotificationManager

    @Before
    fun setup() {
        launcherApps = kosmos.launcherApps
        conversationNotificationManager = kosmos.conversationNotificationManager
        logger = kosmos.notificationRowContentBinderLogger

        conversationNotificationProcessor =
            ConversationNotificationProcessor(
                context,
                launcherApps,
                conversationNotificationManager,
            )
    }

    @Test
    fun processNotification_notMessagingStyle() {
        val entry = kosmos.buildNotificationEntry()
        val builder = Notification.Builder.recoverBuilder(context, entry.sbn.notification)

        assertThat(conversationNotificationProcessor.processNotification(entry, builder, logger))
            .isNull()
    }

    @Test
    @DisableFlags(Flags.FLAG_NM_SUMMARIZATION, Flags.FLAG_NM_SUMMARIZATION_UI)
    fun processNotification_messagingStyleWithSummarization_flagOff() {
        val summarization = "hello"
        val entry = kosmos.makeEntryOfPeopleType()
        entry.setRanking(RankingBuilder(entry.ranking).setSummarization(summarization).build())
        val builder = Notification.Builder.recoverBuilder(context, entry.sbn.notification)

        assertThat(conversationNotificationProcessor.processNotification(entry, builder, logger))
            .isNotNull()
        assertThat(builder.build().extras.getCharSequence(EXTRA_SUMMARIZED_CONTENT)).isNull()
    }

    @Test
    @EnableFlags(Flags.FLAG_NM_SUMMARIZATION)
    fun processNotification_messagingStyleWithSummarization() {
        val summarization = "hello"
        val entry = kosmos.makeEntryOfPeopleType()
        entry.setRanking(RankingBuilder(entry.ranking).setSummarization(summarization).build())
        val builder = Notification.Builder.recoverBuilder(context, entry.sbn.notification)

        assertThat(conversationNotificationProcessor.processNotification(entry, builder, logger))
            .isNotNull()

        val processedSummary = builder.build().extras.getCharSequence(EXTRA_SUMMARIZED_CONTENT)
        assertThat(processedSummary.toString()).isEqualTo("   $summarization")

        val checkSpans = SpannableStringBuilder(processedSummary)
        assertThat(
                checkSpans.getSpans(
                    /* queryStart = */ 0,
                    /* queryEnd = */ 2,
                    /* kind = */ ImageSpan::class.java,
                )
            )
            .isNotNull()
    }

    @Test
    @EnableFlags(Flags.FLAG_NM_SUMMARIZATION)
    fun processNotification_messagingStyleUpdateSummarizationToNull() {
        val entry = kosmos.makeEntryOfPeopleType()
        entry.setRanking(RankingBuilder(entry.ranking).setSummarization("hello").build())
        val builder = Notification.Builder.recoverBuilder(context, entry.sbn.notification)
        assertThat(conversationNotificationProcessor.processNotification(entry, builder, logger))
            .isNotNull()

        entry.setRanking(RankingBuilder(entry.ranking).setSummarization(null).build())

        assertThat(conversationNotificationProcessor.processNotification(entry, builder, logger))
            .isNotNull()
        assertThat(builder.build().extras.getCharSequence(EXTRA_SUMMARIZED_CONTENT)).isNull()
    }

    @Test
    @EnableFlags(Flags.FLAG_NM_SUMMARIZATION)
    fun processNotification_messagingStyleWithoutSummarization() {
        val entry = kosmos.makeEntryOfPeopleType()
        val builder = Notification.Builder.recoverBuilder(context, entry.sbn.notification)

        assertThat(conversationNotificationProcessor.processNotification(entry, builder, logger))
            .isNotNull()
        assertThat(builder.build().extras.getCharSequence(EXTRA_SUMMARIZED_CONTENT)).isNull()
    }
}
