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
package com.android.systemui.personalcontext

import android.app.slice.SliceItem
import android.content.ComponentName
import android.content.applicationContext
import android.os.Binder
import android.os.Bundle
import android.service.autofill.Dataset
import android.service.personalcontext.RenderToken
import android.service.personalcontext.hint.AutofillInlineRequestHint
import android.service.personalcontext.hint.BundleHint
import android.service.personalcontext.hint.PublishedContextHint
import android.service.personalcontext.insight.BundleInsight
import android.service.personalcontext.insight.DisplayInsight
import android.service.personalcontext.insight.InsightDisplayDetails
import android.service.personalcontext.insight.PublishedContextInsightWrapper
import android.testing.AndroidTestingRunner
import android.util.Size
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.view.autofill.autofillManager
import android.view.inputmethod.InlineSuggestionsRequest
import android.widget.inline.InlinePresentationSpec
import androidx.autofill.inline.VersionUtils.writeSupportedVersions
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.personalcontext.AutofillAttributionStartable.Companion.EXTRA_AUTOFILL_INSIGHT
import com.android.systemui.personalcontext.AutofillAttributionStartable.Companion.EXTRA_RENDER_TOKEN
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.util.Random
import java.util.UUID
import javax.crypto.spec.SecretKeySpec
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidTestingRunner::class)
class AutofillRendererServiceTest : SysuiTestCase() {

    private val kosmos = testKosmos()

    private val underTest: AutofillRendererService by lazy {
        with(kosmos) {
            AutofillRendererService(context = applicationContext, autofillManager = autofillManager)
        }
    }

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        with(kosmos) {
            // Fake the attach call so that the service ContextWrapper is initialized with the
            // context.
            underTest.attach(
                applicationContext,
                null,
                AutofillRendererService::class.java.name,
                null,
                mock(),
                null,
            )
        }
    }

    @Test
    fun testOnRender_displayInsight() =
        kosmos.runTest {
            val title = "test title"
            val subtitle = "test subtitle"
            val publishedContextInsight =
                DisplayInsight.Builder(
                        InsightDisplayDetails.Builder(title).setSubtitle(subtitle).build()
                    )
                    .addOriginHint(
                        PublishedContextHint.Builder(
                                AUTOFILL_INLINE_REQUEST_HINT,
                                generateSignedHintKey(),
                            )
                            .build()
                    )
                    .build()
                    .fakePublish()
            val renderToken = RenderToken(UUID.randomUUID(), null)
            underTest.onRender(publishedContextInsight, renderToken)

            val datasetCaptor = argumentCaptor<MutableList<Dataset>>()
            verify(autofillManager)
                .notifySystemInlineSuggestions(eq(SESSION_ID), datasetCaptor.capture())
            val datasets = datasetCaptor.firstValue
            assertThat(datasets).hasSize(1)

            // Title and subtitle are included in the generated dataset.
            val sliceItems = datasets.first().getFieldInlinePresentation(0)!!.slice.items
            assertThat(sliceItems.stream().anyMatch { it.text.toString() == title }).isTrue()
            assertThat(sliceItems.stream().anyMatch { it.text.toString() == subtitle }).isTrue()

            // Attribution intent is attached and has correct info.
            val actionItems =
                sliceItems.stream().filter { it.format == SliceItem.FORMAT_ACTION }.toList()
            assertThat(actionItems).hasSize(1)
            val actionItem = actionItems.first().action
            assertThat(actionItem).isNotNull()
            assertThat(actionItem.isBroadcast).isTrue()
            val actionIntent = actionItem.intent
            assertThat(actionIntent.action)
                .isEqualTo(AutofillAttributionStartable.ACTION_ATTRIBUTION_TRIGGERED)
            assertThat(actionIntent.`package`).isEqualTo(context.packageName)

            assertThat(
                    actionIntent
                        .getParcelableExtra(
                            EXTRA_AUTOFILL_INSIGHT,
                            PublishedContextInsightWrapper::class.java,
                        )!!
                        .publishedContextInsight
                        .insight
                )
                .isEqualTo(publishedContextInsight.insight)
            assertThat(actionIntent.getParcelableExtra(EXTRA_RENDER_TOKEN, RenderToken::class.java))
                .isEqualTo(renderToken)
        }

    @Test
    fun testOnRender_bundleInsight_returnsEmptyDatasets() =
        kosmos.runTest {
            underTest.onRender(
                BundleInsight.Builder()
                    .addOriginHint(
                        PublishedContextHint.Builder(
                                AUTOFILL_INLINE_REQUEST_HINT,
                                generateSignedHintKey(),
                            )
                            .build()
                    )
                    .build()
                    .fakePublish(),
                RenderToken(UUID.randomUUID(), null),
            )

            // An empty list of datasets is returned for a BundleInsight.
            val datasetCaptor = argumentCaptor<MutableList<Dataset>>()
            verify(autofillManager)
                .notifySystemInlineSuggestions(eq(SESSION_ID), datasetCaptor.capture())
            assertThat(datasetCaptor.firstValue).hasSize(0)
        }

    @Test
    fun testOnRender_displayInsight_withInlineSuggestionHints() =
        kosmos.runTest {
            val inlineSuggestionHints = arrayOf("inline_hint1", "inline_hint2")
            val bundleHint =
                BundleHint.Builder()
                    .setDataBundle(
                        Bundle().also {
                            it.putStringArray(
                                AutofillRendererService.KEY_INLINE_SUGGESTIONS_HINTS,
                                arrayOf("inline_hint1", "inline_hint2"),
                            )
                        }
                    )
                    .build()
            underTest.onRender(
                DisplayInsight.Builder(InsightDisplayDetails.Builder("title").build())
                    .addOriginHint(
                        PublishedContextHint.Builder(
                                AUTOFILL_INLINE_REQUEST_HINT,
                                generateSignedHintKey(),
                            )
                            .build()
                    )
                    .addOriginHint(
                        PublishedContextHint.Builder(bundleHint, generateSignedHintKey()).build()
                    )
                    .build()
                    .fakePublish(),
                RenderToken(UUID.randomUUID(), null),
            )

            val datasetCaptor = argumentCaptor<MutableList<Dataset>>()
            verify(autofillManager)
                .notifySystemInlineSuggestions(eq(SESSION_ID), datasetCaptor.capture())
            assertThat(datasetCaptor.firstValue).hasSize(1)
            assertThat(datasetCaptor.firstValue.first().getFieldInlinePresentation(0)!!.slice.hints)
                .containsExactly(*inlineSuggestionHints)
        }

    @Test
    fun testOnRender_displayInsight_withDatasetIds() =
        kosmos.runTest {
            val expectedDatasetId = "test dataset ID"
            val bundleHint =
                BundleHint.Builder()
                    .setDataBundle(
                        Bundle().also {
                            it.putString(AutofillRendererService.KEY_DATASET_ID, expectedDatasetId)
                        }
                    )
                    .build()
            underTest.onRender(
                DisplayInsight.Builder(InsightDisplayDetails.Builder("title").build())
                    .addOriginHint(
                        PublishedContextHint.Builder(
                                AUTOFILL_INLINE_REQUEST_HINT,
                                generateSignedHintKey(),
                            )
                            .build()
                    )
                    .addOriginHint(
                        PublishedContextHint.Builder(bundleHint, generateSignedHintKey()).build()
                    )
                    .build()
                    .fakePublish(),
                RenderToken(UUID.randomUUID(), null),
            )

            val datasetCaptor = argumentCaptor<MutableList<Dataset>>()
            verify(autofillManager)
                .notifySystemInlineSuggestions(eq(SESSION_ID), datasetCaptor.capture())
            assertThat(datasetCaptor.firstValue).hasSize(1)
            assertThat(datasetCaptor.firstValue.first().id).isEqualTo(expectedDatasetId)
        }

    private companion object {
        const val SESSION_ID = 42

        val AUTOFILL_INLINE_PRESENTATION_SPEC: InlinePresentationSpec =
            InlinePresentationSpec.Builder(Size(100, 100), Size(100, 100))
                .setStyle(Bundle().also { writeSupportedVersions(it) })
                .build()

        val INLINE_SUGGESTIONS_REQUEST =
            InlineSuggestionsRequest.Builder(
                    listOf<InlinePresentationSpec?>(AUTOFILL_INLINE_PRESENTATION_SPEC)
                )
                .build()

        val AUTOFILL_INLINE_REQUEST_HINT =
            AutofillInlineRequestHint.Builder(
                    SESSION_ID,
                    0,
                    Instant.now(),
                    ComponentName("test_package", "test_component"),
                    AutofillId(0),
                    AutofillValue.forText("test"),
                    INLINE_SUGGESTIONS_REQUEST,
                    Binder(),
                )
                .build()

        /** Generates a key to use when signing hints. */
        fun generateSignedHintKey(): SecretKeySpec {
            val key = ByteArray(64)
            Random().nextBytes(key)
            return SecretKeySpec(key, PublishedContextHint.HMAC_ALGORITHM)
        }
    }
}
