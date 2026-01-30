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

import android.service.notification.Adjustment
import com.android.internal.logging.UiEvent
import com.android.internal.logging.UiEventLogger
import com.android.internal.util.FrameworkStatsLog
import com.android.systemui.notifications.ui.composable.row.BundleHeader
import com.android.systemui.statusbar.notification.collection.BundleEntry
import javax.inject.Inject

enum class BundleInteractedEvent(private val _id: Int) : UiEventLogger.UiEventEnum {
    @UiEvent(doc = "User dismissed a bundle") NOTIF_BUNDLE_DISMISSED(2264),
    @UiEvent(doc = "User expanded a bundle") NOTIF_BUNDLE_EXPANDED(2343),
    @UiEvent(doc = "User collapsed a bundle") NOTIF_BUNDLE_COLLAPSED(2344);

    override fun getId() = _id
}

class BundleInteractionLogger @Inject constructor() {

    fun logBundleExpansionChanged(@Adjustment.Types bundleType: Int, nowExpanded: Boolean) {
        FrameworkStatsLog.write(
            FrameworkStatsLog.NOTIFICATION_BUNDLE_INTERACTED,
            /* optional int32 event_id */ if (nowExpanded)
                BundleInteractedEvent.NOTIF_BUNDLE_EXPANDED.id
            else BundleInteractedEvent.NOTIF_BUNDLE_COLLAPSED.id,
            /* optional int32 type */ bundleType,
            /* optional bool contents_shown */ true, // irrelevant but inherently true
        )
    }

    fun logBundleDismissed(bundle: BundleEntry) {
        FrameworkStatsLog.write(
            FrameworkStatsLog.NOTIFICATION_BUNDLE_INTERACTED,
            /* optional int32 event_id */ BundleInteractedEvent.NOTIF_BUNDLE_DISMISSED.id,
            /* optional int32 type */ bundle.bundleRepository.bundleType,
            /* optional bool contents_shown */ bundleContentsShown(bundle),
        )
    }

    fun bundleContentsShown(bundle: BundleEntry): Boolean {
        // Consider a bundle's contents to have been shown either if it has ever been collapsed
        // before or if it's expanded now. Otherwise, assume it to have never been opened.
        // TODO: b/422234721 - this logic doesn't correctly handle if the bundle was collapsed by
        //                     the entire shade closing rather than directly by the user
        return (bundle.bundleRepository.lastCollapseTime > 0L) ||
            bundle.bundleRepository.state?.currentScene == BundleHeader.Scenes.Expanded
    }
}
