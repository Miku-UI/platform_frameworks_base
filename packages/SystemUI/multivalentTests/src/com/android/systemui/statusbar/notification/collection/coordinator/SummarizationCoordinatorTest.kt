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
package com.android.systemui.statusbar.notification.collection.coordinator

import android.app.Notification.EXTRA_SUMMARIZED_CONTENT
import android.content.testableContext
import android.graphics.drawable.AnimatedVectorDrawable
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper.RunWithLooper
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.shade.domain.interactor.shadeInteractor
import com.android.systemui.statusbar.RankingBuilder
import com.android.systemui.statusbar.notification.NmSummarizationAllFlag
import com.android.systemui.statusbar.notification.collection.NotifPipeline
import com.android.systemui.statusbar.notification.collection.buildNotificationEntry
import com.android.systemui.statusbar.notification.collection.coordinator.shared.NotificationSummarizationAllowAnimation
import com.android.systemui.statusbar.notification.collection.makeEntryOfPeopleType
import com.android.systemui.statusbar.notification.collection.notifcollection.NotifCollectionListener
import com.android.systemui.statusbar.notification.data.model.SummarizationIconViewModel
import com.android.systemui.statusbar.notification.data.repository.SummarizationAnimationRepository
import com.android.systemui.statusbar.notification.domain.interactor.SummarizationInteractor
import com.android.systemui.statusbar.notification.domain.interactor.activeNotificationsInteractor
import com.android.systemui.testKosmos
import com.android.systemui.util.mockito.mock
import com.android.systemui.util.mockito.withArgCaptor
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations.initMocks
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
class SummarizationCoordinatorTest : SysuiTestCase() {
    private lateinit var coordinator: SummarizationCoordinator
    private lateinit var notifCollectionListener: NotifCollectionListener
    private lateinit var repository: SummarizationAnimationRepository
    private lateinit var decorator: SummarizationDecorator
    private lateinit var interactor: SummarizationInteractor
    private lateinit var viewModel: SummarizationIconViewModel

    @Mock private lateinit var pipeline: NotifPipeline
    private val kosmos = testKosmos()

    @Before
    fun setUp() {
        initMocks(this)
        repository = SummarizationAnimationRepository()
        decorator = SummarizationDecorator(kosmos.testableContext)
        interactor =
            SummarizationInteractor(
                repository,
                kosmos.shadeInteractor,
                kosmos.activeNotificationsInteractor,
            )
        viewModel = SummarizationIconViewModel(repository, interactor)
        coordinator =
            SummarizationCoordinator(
                mContext,
                kosmos.applicationCoroutineScope,
                mock(),
                decorator,
                interactor,
                viewModel,
            )
        coordinator.attach(pipeline)
        notifCollectionListener = withArgCaptor {
            verify(pipeline).addCollectionListener(capture())
        }
    }

    @Test
    @EnableFlags(NmSummarizationAllFlag.FLAG_NAME)
    @DisableFlags(NotificationSummarizationAllowAnimation.FLAG_NAME)
    fun onBeforeRenderList_messagingStyleWithSummarization() {
        val summarization = "hello"
        val entry = kosmos.makeEntryOfPeopleType()
        entry.setRanking(RankingBuilder(entry.ranking).setSummarization(summarization).build())

        notifCollectionListener.onEntryAdded(entry)

        val processedSummary =
            entry.sbn.notification.extras.getCharSequence(EXTRA_SUMMARIZED_CONTENT)
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

        // When animation flag is off, we should not populate repository
        assertThat(repository.drawables[entry.key]).isNull()
    }

    @Test
    @EnableFlags(NotificationSummarizationAllowAnimation.FLAG_NAME)
    fun onBeforeRenderList_messagingStyleWithSummarizationAnimatable() {
        val summarization = "hello"
        val entry = kosmos.makeEntryOfPeopleType()
        entry.setRanking(RankingBuilder(entry.ranking).setSummarization(summarization).build())

        notifCollectionListener.onEntryAdded(entry)

        val processedSummary =
            entry.sbn.notification.extras.getCharSequence(EXTRA_SUMMARIZED_CONTENT)
        assertThat(processedSummary.toString()).isEqualTo("   $summarization")

        val checkSpans = SpannableStringBuilder(processedSummary)
        val drawable =
            checkSpans
                .getSpans(/* queryStart= */ 0, /* queryEnd= */ 2, /* kind= */ ImageSpan::class.java)
                .get(0)
                .drawable
        assert(drawable is AnimatedVectorDrawable)
        assertEquals(repository.drawables[entry.key], drawable as AnimatedVectorDrawable)
    }

    @Test
    @EnableFlags(NotificationSummarizationAllowAnimation.FLAG_NAME)
    fun onBeforeRenderList_messagingStyleWithSummarizationAnimatable_updateReusesDrawable() {
        val summarization = "hello"
        val entry = kosmos.makeEntryOfPeopleType()
        entry.setRanking(RankingBuilder(entry.ranking).setSummarization(summarization).build())

        notifCollectionListener.onEntryAdded(entry)

        val processedSummary =
            entry.sbn.notification.extras.getCharSequence(EXTRA_SUMMARIZED_CONTENT)
        assertThat(processedSummary.toString()).isEqualTo("   $summarization")

        val originalDrawable =
            SpannableStringBuilder(processedSummary)
                .getSpans(/* queryStart= */ 0, /* queryEnd= */ 2, /* kind= */ ImageSpan::class.java)
                .get(0)
                .drawable

        notifCollectionListener.onEntryUpdated(entry)

        val newDrawable =
            SpannableStringBuilder(processedSummary)
                .getSpans(/* queryStart= */ 0, /* queryEnd= */ 2, /* kind= */ ImageSpan::class.java)
                .get(0)
                .drawable

        assertEquals(originalDrawable, newDrawable)
    }

    @Test
    @EnableFlags(NmSummarizationAllFlag.FLAG_NAME)
    fun onBeforeRenderList_messagingStyleUpdateSummarizationToNull() {
        val entry = kosmos.makeEntryOfPeopleType()
        entry.setRanking(RankingBuilder(entry.ranking).setSummarization("hello").build())
        notifCollectionListener.onEntryAdded(entry)
        assertThat(entry.sbn.notification.extras.getCharSequence(EXTRA_SUMMARIZED_CONTENT))
            .isNotNull()

        entry.setRanking(RankingBuilder(entry.ranking).setSummarization(null).build())
        notifCollectionListener.onEntryUpdated(entry)
        assertThat(entry.sbn.notification.extras.getCharSequence(EXTRA_SUMMARIZED_CONTENT)).isNull()
    }

    @Test
    @EnableFlags(NmSummarizationAllFlag.FLAG_NAME)
    fun onBeforeRenderList_noStyle_appProvidedSummarization() {
        val summarization = "hello"
        val entry =
            kosmos.buildNotificationEntry {
                modifyNotification(context).setSummarizedContent(summarization)
            }

        notifCollectionListener.onEntryAdded(entry)

        val processedSummary =
            entry.sbn.notification.extras.getCharSequence(EXTRA_SUMMARIZED_CONTENT)
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
    @EnableFlags(NmSummarizationAllFlag.FLAG_NAME)
    fun onBeforeRenderList_messagingStyleWithoutSummarization() {
        val entry = kosmos.makeEntryOfPeopleType()
        notifCollectionListener.onEntryAdded(entry)

        assertThat(entry.sbn.notification.extras.getCharSequence(EXTRA_SUMMARIZED_CONTENT)).isNull()
    }

    @Test
    @EnableFlags(NmSummarizationAllFlag.FLAG_NAME)
    fun onBeforeRenderList_messagingStyleWithSummarization_summarizationAddedAfterPost() {
        val summarization = "hello"
        val entry = kosmos.makeEntryOfPeopleType()

        notifCollectionListener.onEntryAdded(entry)
        assertThat(entry.sbn.notification.extras.getCharSequence(EXTRA_SUMMARIZED_CONTENT)).isNull()

        entry.setRanking(RankingBuilder(entry.ranking).setSummarization(summarization).build())

        whenever(pipeline.allNotifs).thenReturn(listOf(entry))

        notifCollectionListener.onRankingApplied()

        val processedSummary =
            entry.sbn.notification.extras.getCharSequence(EXTRA_SUMMARIZED_CONTENT)
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
    @EnableFlags(NotificationSummarizationAllowAnimation.FLAG_NAME)
    fun onBeforeRenderList_cleanupSummarizationAnimatable() {
        val summarization = "hello"
        val entry = kosmos.makeEntryOfPeopleType()
        entry.setRanking(RankingBuilder(entry.ranking).setSummarization(summarization).build())

        notifCollectionListener.onEntryAdded(entry)
        assertNotNull(repository.drawables[entry.key])
        notifCollectionListener.onEntryCleanUp(entry)
        assertNull(repository.drawables[entry.key])
    }
}
