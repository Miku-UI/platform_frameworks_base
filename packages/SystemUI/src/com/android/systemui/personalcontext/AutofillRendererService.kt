/*
 * Copyright 2025 The Android Open Source Project
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

import android.app.PendingIntent
import android.app.slice.Slice
import android.content.Context
import android.service.autofill.Dataset
import android.service.autofill.Field
import android.service.autofill.InlinePresentation
import android.service.personalcontext.RenderToken
import android.service.personalcontext.hint.AutofillInlineRequestHint
import android.service.personalcontext.hint.BundleHint
import android.service.personalcontext.insight.ActionableInsight
import android.service.personalcontext.insight.BundleInsight
import android.service.personalcontext.insight.ContextInsight
import android.service.personalcontext.insight.DisplayInsight
import android.service.personalcontext.insight.InsightActionDetails
import android.service.personalcontext.insight.InsightCollection
import android.service.personalcontext.insight.InsightDisplayDetails
import android.service.personalcontext.insight.InsightFilter
import android.service.personalcontext.insight.InsightTraverser
import android.service.personalcontext.insight.InsightVisitor
import android.service.personalcontext.insight.PublishedContextInsight
import android.service.personalcontext.renderer.InsightRendererService
import android.util.Log
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.view.inputmethod.InlineSuggestionsRequest
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import javax.inject.Inject

/**
 * Renderer used to receive personal context insights for autofill, convert them to a displayable
 * format using [InlineSuggestionUi], then forward them to the autofill framework to be displayed.
 *
 * <p>This renderer is in SysUI due to its dependency on androidx libraries, which cannot be used in
 * framework.
 */
class AutofillRendererService
@Inject
constructor(private val context: Context, private val autofillManager: AutofillManager?) :
    InsightRendererService() {

    override fun onInitializeFilter(): InsightFilter {
        return InsightFilter.Builder()
            // Display insight is allowed for plain autofill results.
            .addInsightType(DisplayInsight::class.java)
            // Actionable insights can be used to attach a click action to the autofill result.
            .addInsightType(ActionableInsight::class.java)
            // Multiple insights allowed, will result in multiple datasets.
            .addInsightType(InsightCollection::class.java)
            // TODO(b/490164951): remove once there's a proper API for this
            // BundleInsight is used to signal there are no suggestions so that autofill framework
            // can stop waiting for a response from personal context.
            .addInsightType(BundleInsight::class.java)
            .build()
    }

    override fun onRender(
        publishedContextInsight: PublishedContextInsight,
        renderToken: RenderToken,
    ) {
        if (autofillManager == null) {
            return
        }

        if (publishedContextInsight.insight is BundleInsight) {
            // BundleInsight represents no suggestions being produced.
            findAutofillHint(publishedContextInsight.insight)?.let { autofillHint ->
                // TODO(b/490164951): remove once there's a proper API for this
                autofillManager.notifySystemInlineSuggestions(
                    autofillHint.sessionId,
                    emptyList<Dataset>(),
                )
                return
            }
        }

        val inlineSuggestionDetails = mutableListOf<InlineSuggestionDetails>()
        InsightTraverser.traverse(
            publishedContextInsight.insight,
            object : InsightVisitor {
                override fun visit(insight: ActionableInsight, index: Int) {
                    findAutofillHint(insight)?.let { autofillHint ->
                        inlineSuggestionDetails.add(
                            InlineSuggestionDetails(
                                getDatasetId(insight),
                                insight.displayDetails,
                                insight.actionDetails,
                                autofillHint,
                                findInlineSuggestionsHints(insight),
                                publishedContextInsight,
                                renderToken,
                                index,
                            )
                        )
                    }
                }

                override fun visit(insight: DisplayInsight, index: Int) {
                    findAutofillHint(insight)?.let { autofillHint ->
                        inlineSuggestionDetails.add(
                            InlineSuggestionDetails(
                                getDatasetId(insight),
                                insight.details,
                                null,
                                autofillHint,
                                findInlineSuggestionsHints(insight),
                                publishedContextInsight,
                                renderToken,
                                index,
                            )
                        )
                    }
                }
            },
        )

        if (inlineSuggestionDetails.isEmpty()) {
            Log.e(TAG, "No autofill insights found")
            return
        }

        val sessionIds = inlineSuggestionDetails.map { it.autofillHint.sessionId }.toSet()
        if (sessionIds.size > 1) {
            Log.e(TAG, "Multiple sessionIds provided in the same render call")
            return
        }

        val sessionId = sessionIds.elementAt(0)
        val inlineSuggestions = createInlineSuggestions(inlineSuggestionDetails)
        // Return even if there are no results so that autofill can stop waiting for a response.
        autofillManager.notifySystemInlineSuggestions(sessionId, inlineSuggestions)
    }

    private fun findAutofillHint(insight: ContextInsight): AutofillInlineRequestHint? {
        val hints = insight.originHints
        for (hint in hints) {
            if (hint.contextHint is AutofillInlineRequestHint) {
                return hint.contextHint as AutofillInlineRequestHint
            }
        }
        return null
    }

    /**
     * Looks for an optional [BundleHint] containing a list of hint strings to be passed into the
     * inline suggestions UI. Note that these are not the same hints as the personal context system.
     *
     * @return a list of hint strings, or an empty list if none are found
     */
    // TODO(b/490420098): replace with proper API
    private fun findInlineSuggestionsHints(insight: ContextInsight): List<String> {
        for (hint in insight.originHints) {
            val contextHint = hint.contextHint
            if (contextHint is BundleHint) {
                contextHint.dataBundle.getStringArray(KEY_INLINE_SUGGESTIONS_HINTS)?.let {
                    return it.toList()
                }
            }
        }
        return emptyList()
    }

    /**
     * Returns a string ID to attach to the [Dataset] generated from the given insight.
     *
     * <p>Looks for an optional [BundleHint] containing a dataset ID to attach to the generated
     * dataset or defaults to the insight ID if none is found.
     *
     * @return a string dataset ID
     */
    // TODO(b/490420098): replace with proper API
    private fun getDatasetId(insight: ContextInsight): String {
        for (hint in insight.originHints) {
            val contextHint = hint.contextHint
            if (contextHint is BundleHint) {
                contextHint.dataBundle.getString(KEY_DATASET_ID)?.let {
                    return it
                }
            }
        }
        return insight.insightId.toString()
    }

    fun createInlineSuggestions(
        inlineSuggestionDetails: List<InlineSuggestionDetails>
    ): List<Dataset> {
        val datasets = mutableListOf<Dataset>()
        // We already verified the session IDs match, just use the first request.
        val inlineSuggestionsRequest: InlineSuggestionsRequest =
            inlineSuggestionDetails.first().autofillHint.inlineSuggestionsRequest
        if (inlineSuggestionsRequest.inlinePresentationSpecs.isEmpty()) {
            return datasets
        }

        // We can only return as many suggestions as there are specs OR insights. zip does this
        // implicitly as it always produces a list that's the length of the smaller list, dropping
        // elements of the longer list.
        for ((spec, suggestionDetails) in
            inlineSuggestionsRequest.inlinePresentationSpecs.zip(inlineSuggestionDetails)) {
            // Personal context inline autofill only supports V1 suggestion UI template.
            if (!UiVersions.getVersions(spec.style).contains(UiVersions.INLINE_UI_VERSION_1)) {
                Log.w(TAG, "createInlineSuggestions: spec version wrong")
                continue
            }

            val suggestionSlice: Slice = createInlineSuggestionSlice(suggestionDetails) ?: continue
            val inlinePresentation = InlinePresentation(suggestionSlice, spec, /* pinned= */ false)

            datasets.add(createDataset(suggestionDetails, inlinePresentation))
        }

        return datasets
    }

    /** Creates the UI [Slice] to be displayed in the inline autofill results. */
    fun createInlineSuggestionSlice(suggestionDetails: InlineSuggestionDetails): Slice? {
        val displayDetails = suggestionDetails.displayDetails
        val attributionAction =
            PendingIntent.getBroadcast(
                context,
                // Use insight index as the requestCode so that each PendingIntent is unique to the
                // system, otherwise they will resolve to the same cached entry as extras are not
                // considered for uniqueness.
                suggestionDetails.index,
                AutofillAttributionStartable.getAttributionIntent(
                    this.applicationContext,
                    suggestionDetails.insight,
                    suggestionDetails.renderToken,
                    suggestionDetails.index,
                ),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val inlineSuggestionUiBuilder =
            InlineSuggestionUi.newContentBuilder(attributionAction)
                .setHints(suggestionDetails.inlineSuggestionHints)
                .setTitle(displayDetails.title ?: return null)

        displayDetails.subtitle?.let { inlineSuggestionUiBuilder.setSubtitle(it) }
        displayDetails.contentDescription?.let {
            inlineSuggestionUiBuilder.setContentDescription(it)
        }
        displayDetails.icon?.let { inlineSuggestionUiBuilder.setStartIcon(it) }

        return inlineSuggestionUiBuilder.build().slice
    }

    fun createDataset(
        suggestionDetails: InlineSuggestionDetails,
        presentation: InlinePresentation,
    ): Dataset {
        val builder = Dataset.Builder(presentation).setId(suggestionDetails.id)
        suggestionDetails.actionDetails?.remoteAction?.actionIntent?.intentSender?.let {
            builder.setAuthentication(it)
        }
        builder.setField(
            suggestionDetails.autofillHint.focusedId,
            Field.Builder()
                .setValue(AutofillValue.forText(suggestionDetails.displayDetails.title))
                .build(),
        )
        return builder.build()
    }

    /** Data class that holds the information necessary to create an inline suggestion result. */
    data class InlineSuggestionDetails(
        val id: String,
        val displayDetails: InsightDisplayDetails,
        val actionDetails: InsightActionDetails?,
        val autofillHint: AutofillInlineRequestHint,
        val inlineSuggestionHints: List<String>,

        // Used for attribution.
        val insight: PublishedContextInsight,
        val renderToken: RenderToken,
        /**
         * Index of the insight inside the InsightCollection
         *
         * @see InsightVisitor
         */
        val index: Int,
    )

    companion object {
        private const val TAG = "AutofillRenderService"

        /**
         * String key on a {@link BundleHint} for an array of string hints to provide to
         * [InlineSuggestionUi].
         */
        const val KEY_INLINE_SUGGESTIONS_HINTS = "inlineSuggestionHints"

        /**
         * String key on a {@link BundleHint} for a string ID to set on the [Dataset] generated from
         * the insight.
         */
        const val KEY_DATASET_ID = "datasetId"
    }
}
