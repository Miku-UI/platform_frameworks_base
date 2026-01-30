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

import android.util.Log
import com.android.app.tracing.traceSection
import com.android.server.notification.Flags.screenshareNotificationHiding
import com.android.systemui.Flags.screenshareNotificationHidingBugFix
import com.android.systemui.statusbar.notification.collection.BundleEntry
import com.android.systemui.statusbar.notification.collection.GroupEntry
import com.android.systemui.statusbar.notification.collection.NotifPipeline
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.PipelineEntry
import com.android.systemui.statusbar.notification.collection.coordinator.dagger.CoordinatorScope
import com.android.systemui.statusbar.notification.collection.render.GroupExpansionManagerImpl
import com.android.systemui.statusbar.notification.data.model.NotifStats
import com.android.systemui.statusbar.notification.domain.interactor.ActiveNotificationsInteractor
import com.android.systemui.statusbar.notification.domain.interactor.RenderNotificationListInteractor
import com.android.systemui.statusbar.notification.stack.BUCKET_SILENT
import com.android.systemui.statusbar.policy.SensitiveNotificationProtectionController
import javax.inject.Inject

private const val TAG = "StackCoordinator"

/**
 * A small coordinator which updates the notif stack (the view layer which holds notifications) with
 * high-level data after the stack is populated with the final entries.
 */
@CoordinatorScope
class StackCoordinator
@Inject
constructor(
    private val groupExpansionManagerImpl: GroupExpansionManagerImpl,
    private val renderListInteractor: RenderNotificationListInteractor,
    private val activeNotificationsInteractor: ActiveNotificationsInteractor,
    private val sensitiveNotificationProtectionController: SensitiveNotificationProtectionController,
) : Coordinator {

    override fun attach(pipeline: NotifPipeline) {
        pipeline.addOnAfterRenderListListener(::onAfterRenderList)
        groupExpansionManagerImpl.attach(pipeline)
    }

    private fun onAfterRenderList(entries: List<PipelineEntry>) =
        traceSection("StackCoordinator.onAfterRenderList") {
            val notifStats = calculateNotifStats(entries)
            activeNotificationsInteractor.setNotifStats(notifStats)
            renderListInteractor.setRenderedList(entries)
        }

    /**
     * Calculates stats about the notification list. This implementation first recursively unpacks
     * all containers into a single, flat list of every individual notification, then iterates over
     * that list to compute the final stats.
     */
    private fun calculateNotifStats(entries: List<PipelineEntry>): NotifStats {
        var hasNonClearableAlertingNotifs = false
        var hasClearableAlertingNotifs = false
        var hasNonClearableSilentNotifs = false
        var hasClearableSilentNotifs = false

        val isSensitiveContentProtectionActive =
            screenshareNotificationHiding() &&
                    screenshareNotificationHidingBugFix() &&
                    sensitiveNotificationProtectionController.isSensitiveStateActive

        val notifEntryList = getFlatNotifEntryList(entries)
        for (entry in notifEntryList) {
            // Once all four booleans are true, we have all the info we need and can stop iterating
            if (hasNonClearableAlertingNotifs && hasClearableAlertingNotifs &&
                hasNonClearableSilentNotifs && hasClearableSilentNotifs) {
                break
            }

            val section = checkNotNull(entry.section) { "Null section for ${entry.key}" }
            val isSilent = section.bucket == BUCKET_SILENT
            val isClearable =
                !isSensitiveContentProtectionActive && entry.isClearable && !entry.isSensitive.value

            when {
                isSilent && isClearable -> hasClearableSilentNotifs = true
                isSilent && !isClearable -> hasNonClearableSilentNotifs = true
                !isSilent && isClearable -> hasClearableAlertingNotifs = true
                else -> hasNonClearableAlertingNotifs = true
            }
        }

        return NotifStats(
            hasNonClearableAlertingNotifs = hasNonClearableAlertingNotifs,
            hasClearableAlertingNotifs = hasClearableAlertingNotifs,
            hasNonClearableSilentNotifs = hasNonClearableSilentNotifs,
            hasClearableSilentNotifs = hasClearableSilentNotifs,
        )
    }

    /**
     * Recursively traverses list of PipelineEntry to unpack all containers and return a
     * flat list of NotificationEntry
     */
    private fun getFlatNotifEntryList(entries: List<PipelineEntry>): List<NotificationEntry> {
        return buildList {
            for (entry in entries) {
                when (entry) {
                    is NotificationEntry -> {
                        add(entry)
                    }
                    is GroupEntry -> {
                        // NOTE: We must process the children of a group directly instead of relying
                        // on the summary's isClearable() method.
                        //
                        // The summary's isClearable() only tells us if ALL children are clearable
                        // and hides the fact that a group can contain a mix of clearable and
                        // non-clearable notifications.
                        //
                        // Consider a group with:
                        //  - one clearable alerting notif
                        //  - one NON-clearable notif
                        //  The summary's isClearable() would be false. If we only check the
                        //  summary, we miss the clearable notif and incorrectly set
                        //  hasClearableAlertingNotifs to false
                        if (entry.children.isNotEmpty()) {
                            addAll(entry.children)
                            if (entry.summary == null) {
                                Log.w(TAG, "Group ${entry.key} has children but no summary")
                            }
                        } else {
                            entry.summary?.let { add(it) }
                        }
                    }
                    is BundleEntry -> {
                        addAll(getFlatNotifEntryList(entry.children))
                    }
                    else -> {
                        Log.w(
                            TAG,
                            "Unknown PipelineEntry type: ${entry::class.simpleName}" +
                                    "with key ${entry.key}"
                        )
                    }
                }
            }
        }
    }
}