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

import android.app.Notification
import android.app.Notification.EXTRA_SUMMARIZED_CONTENT
import android.content.Context
import android.graphics.drawable.AnimatedVectorDrawable
import android.text.TextUtils
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.statusbar.notification.NmSummarizationAllFlag
import com.android.systemui.statusbar.notification.OnboardingAffordanceManager
import com.android.systemui.statusbar.notification.Summarization
import com.android.systemui.statusbar.notification.collection.NotifPipeline
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.coordinator.dagger.CoordinatorScope
import com.android.systemui.statusbar.notification.collection.coordinator.shared.NotificationSummarizationAllowAnimation
import com.android.systemui.statusbar.notification.collection.listbuilder.pluggable.Invalidator
import com.android.systemui.statusbar.notification.collection.notifcollection.NotifCollectionListener
import com.android.systemui.statusbar.notification.data.model.SummarizationIconViewModel
import com.android.systemui.statusbar.notification.domain.interactor.SummarizationInteractor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Coordinator for notification summarization features. */
@CoordinatorScope
class SummarizationCoordinator
@Inject
constructor(
    @ShadeDisplayAware private val context: Context,
    @Application private val scope: CoroutineScope,
    @Summarization private val onboardingAffordanceManager: OnboardingAffordanceManager,
    private val decorator: SummarizationDecorator,
    private val interactor: SummarizationInteractor,
    private val viewModel: SummarizationIconViewModel,
) : Coordinator {
    override fun attach(pipeline: NotifPipeline) {
        bindOnboardingAffordanceInvalidator(pipeline)

        // Use this implementation for decoration if either NmSummAll or AllowAnim is enabled
        // (not the older ConversationNotifications.kt implementation).
        // Rig up animation flows only if animation is on.
        // Never add both collection listeners: once the summarization is decorated by either,
        // the second one will not re-decorate.

        if (
            NmSummarizationAllFlag.isEnabled && !NotificationSummarizationAllowAnimation.isEnabled
        ) {
            pipeline.addCollectionListener(
                object : NotifCollectionListener {
                    override fun onEntryAdded(entry: NotificationEntry) {
                        decorateSummarization(entry)
                    }

                    override fun onEntryUpdated(entry: NotificationEntry) {
                        decorateSummarization(entry)
                    }

                    override fun onRankingApplied() {
                        for (entry in pipeline.allNotifs) {
                            decorateSummarization(entry)
                        }
                    }
                }
            )
        }

        if (NotificationSummarizationAllowAnimation.isEnabled) {
            pipeline.addCollectionListener(
                object : NotifCollectionListener {
                    override fun onEntryAdded(entry: NotificationEntry) {
                        if (NmSummarizationAllFlag.isEnabled || isMessagingStyle(entry))
                            decorateSummarizationAnimatable(entry)
                    }

                    override fun onEntryUpdated(entry: NotificationEntry) {
                        if (NmSummarizationAllFlag.isEnabled || isMessagingStyle(entry))
                            decorateSummarizationAnimatable(entry)
                    }

                    override fun onRankingApplied() {
                        for (entry in pipeline.allNotifs) {
                            if (NmSummarizationAllFlag.isEnabled || isMessagingStyle(entry))
                                decorateSummarizationAnimatable(entry)
                        }
                    }

                    override fun onEntryCleanUp(entry: NotificationEntry) {
                        cleanupSummarizationAnimatable(entry)
                    }
                }
            )
            bindDecorationFlow()
        }
    }

    private fun bindOnboardingAffordanceInvalidator(pipeline: NotifPipeline) {
        val invalidator = object : Invalidator("summarization onboarding") {}
        pipeline.addPreRenderInvalidator(invalidator)
        scope.launch {
            onboardingAffordanceManager.view.collect {
                invalidator.invalidateList("summarization onboarding view changed")
            }
        }
    }

    private fun bindDecorationFlow() {
        if (NotificationSummarizationAllowAnimation.isUnexpectedlyInLegacyMode()) return
        scope.launch {
            viewModel.animationReadyEntryKeys.collect { entryKeys ->
                viewModel.onTriggered(entryKeys)
            }
        }
    }

    private fun isMessagingStyle(entry: NotificationEntry): Boolean {
        return entry.sbn.notification.isStyle(Notification.MessagingStyle::class.java)
    }

    private fun decorateSummarizationAnimatable(entry: NotificationEntry) {
        if (NotificationSummarizationAllowAnimation.isUnexpectedlyInLegacyMode()) return
        if (!TextUtils.isEmpty(entry.summarization)) {
            // Use previously existing icon if present, otherwise make one
            val icon = decorator.decorateSummarization(entry, interactor.iconForEntry(entry.key))
            if (icon is AnimatedVectorDrawable) {
                interactor.trackDecoratedEntry(entry, icon)
            }
        } else {
            entry.sbn.notification.extras.putCharSequence(EXTRA_SUMMARIZED_CONTENT, null)
            // in case entry has become un-summarized, eg by turning off feature entirely
            interactor.trackDecoratedEntry(entry, null)
        }
    }

    private fun decorateSummarization(entry: NotificationEntry) {
        NotificationSummarizationAllowAnimation.assertInLegacyMode()
        if (!TextUtils.isEmpty(entry.summarization)) {
            decorator.decorateSummarization(entry, null)
        } else {
            entry.sbn.notification.extras.putCharSequence(EXTRA_SUMMARIZED_CONTENT, null)
        }
    }

    private fun cleanupSummarizationAnimatable(entry: NotificationEntry) {
        interactor.cleanupEntry(entry)
    }
}
