/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.content.Context
import android.util.Log
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.shared.notifications.data.repository.NotificationSettingsRepository.Companion.EXPAND_BUNDLE_ALWAYS
import com.android.systemui.shared.notifications.data.repository.NotificationSettingsRepository.Companion.EXPAND_BUNDLE_AUTO
import com.android.systemui.shared.notifications.data.repository.NotificationSettingsRepository.Companion.EXPAND_BUNDLE_NEVER
import com.android.systemui.shared.notifications.domain.interactor.NotificationSettingsInteractor
import com.android.systemui.statusbar.notification.collection.BundleEntry
import com.android.systemui.statusbar.notification.collection.GroupEntry
import com.android.systemui.statusbar.notification.collection.NotifPipeline
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.PipelineEntry
import com.android.systemui.statusbar.notification.collection.coordinator.dagger.CoordinatorScope
import com.android.systemui.statusbar.notification.collection.listbuilder.pluggable.Invalidator
import com.android.systemui.statusbar.notification.collection.provider.SectionStyleProvider
import com.android.systemui.statusbar.notification.collection.render.NotifRowController
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A small coordinator which updates the notif rows with data related to the current shade after
 * they are fully attached.
 */
@CoordinatorScope
class RowAppearanceCoordinator
@Inject
constructor(
    @ShadeDisplayAware context: Context,
    private var mSectionStyleProvider: SectionStyleProvider,
    private val notificationSettingsInteractor: NotificationSettingsInteractor,
    @Application private val coroutineScope: CoroutineScope,
) : Coordinator {

    private var entryToExpand: NotificationEntry? = null
    private var numActiveBundles: Int = 0

    /**
     * `true` if notifications not part of a group should by default be rendered in their expanded
     * state. If `false`, then only the first notification will be expanded if possible.
     */
    private val mAlwaysExpandNonGroupedNotification =
        context.resources.getBoolean(R.bool.config_alwaysExpandNonGroupedNotifications)

    /**
     * `true` if the first non-group expandable notification should be expanded automatically when
     * possible. If `false`, then the first non-group expandable notification should not be
     * expanded.
     */
    private val mAutoExpandFirstNotification =
        context.resources.getBoolean(R.bool.config_autoExpandFirstNotification)

    override fun attach(pipeline: NotifPipeline) {
        bindBundleExpansionInvalidator(pipeline)
        pipeline.addOnBeforeRenderListListener(::onBeforeRenderList)
        pipeline.addOnAfterRenderListListener(::onAfterRenderList)
        pipeline.addOnAfterRenderEntryListener(::onAfterRenderEntry)
        pipeline.addOnAfterRenderBundleEntryListener(::onAfterRenderBundleEntry)
    }

    private fun bindBundleExpansionInvalidator(pipeline: NotifPipeline) {
        val invalidator = object : Invalidator("bundle expansion setting") {}
        pipeline.addPreRenderInvalidator(invalidator)
        coroutineScope.launch {
            notificationSettingsInteractor.shouldExpandBundles.collect {
                invalidator.invalidateList("bundle expansion setting changed")
            }
        }
    }

    private fun onBeforeRenderList(list: List<PipelineEntry>) {
        entryToExpand = list.firstOrNull()?.let { getEntryToExpand(it) }
    }

    private fun getEntryToExpand(pipelineEntry: PipelineEntry): NotificationEntry? {
        return pipelineEntry.asListEntry()?.representativeEntry?.takeIf { entry ->
            !mSectionStyleProvider.isMinimizedSection(entry.section!!)
        }
    }

    private fun onAfterRenderList(entries: List<PipelineEntry>) {
        numActiveBundles = entries.filterIsInstance<BundleEntry>().size
    }

    private fun onAfterRenderEntry(entry: NotificationEntry, controller: NotifRowController) {
        if (entry.isBundled) {
            // If AUTO and there are multiple bundles, then auto-expand notifications that
            // look like singletons: notifications that aren't in a group and notifications that are
            // the only child in a group (only has summary or is single child in group).
            // In all other cases, no auto-expansion for bundle children.
            val looksLikeSingleNotification =
                !(entry.parent is GroupEntry && entry.sbn.isGroup) ||
                    (entry.parent as GroupEntry).children.size == 0 ||
                    (entry.parent as GroupEntry).children.size == 1

            controller.setSystemExpanded(
                numActiveBundles > 1 &&
                    notificationSettingsInteractor.shouldExpandBundles.value ==
                        EXPAND_BUNDLE_AUTO &&
                    looksLikeSingleNotification
            )
        } else {
            // If mAlwaysExpandNonGroupedNotification is false, then only expand the
            // very first notification if it's not a child of grouped notifications and when
            // mAutoExpandFirstNotification is true.
            controller.setSystemExpanded(
                (mAlwaysExpandNonGroupedNotification ||
                    (mAutoExpandFirstNotification && entry == entryToExpand))
            )
        }
    }

    private fun isBundleSystemExpanded(entry: BundleEntry) : Boolean {
        val bundleExpansion: Int = notificationSettingsInteractor.shouldExpandBundles.value

        return when (bundleExpansion) {
            EXPAND_BUNDLE_NEVER -> false
            EXPAND_BUNDLE_ALWAYS -> true
            else -> numActiveBundles == 1 && entry.children.size == 1
        }
    }

    private fun onAfterRenderBundleEntry(entry: BundleEntry, controller: NotifRowController) {
        controller.setSystemExpanded(isBundleSystemExpanded(entry))
    }
}
