/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import android.app.NotificationManager
import android.multiuser.Flags
import com.android.systemui.statusbar.notification.collection.NotifPipeline
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.coordinator.dagger.CoordinatorScope
import com.android.systemui.statusbar.notification.collection.listbuilder.pluggable.NotifFilter
import com.android.systemui.user.domain.interactor.SelectedUserInteractor
import javax.inject.Inject

/**
 * A coordinator that handles notifications posted to the HSU (Headless System User).
 *
 * <p>HSU notifications are restrict (currently they're disabled, but in the future they will be
 * managed by an allowlist), and should be logged.
 */
@CoordinatorScope
class HsuCoordinator
@Inject
constructor(
    private val selectedUserInteractor: SelectedUserInteractor,
    private val notificationManager: NotificationManager,
) : Coordinator {

    override fun attach(pipeline: NotifPipeline) {
        pipeline.addPreGroupFilter(notifFilter)
    }

    private val notifFilter: NotifFilter =
        object : NotifFilter(TAG) {
            override fun shouldFilterOut(entry: NotificationEntry, now: Long): Boolean {
                if (!selectedUserInteractor.isCurrentUserHeadlessSystemUser.value) {
                    // NOTE: currently this mechanism is just used to log notifications shown in a
                    // login screen (when the current user is the HSU), so we don't need to log
                    // notifications posted on other users.
                    return false
                }
                val filteredOut = Flags.hsuDisableNotifications()
                // NOTE: despite the name, flag hsuAllowlistNotifications is only used for logging
                if (Flags.hsuAllowlistNotifications()) {
                    val status =
                        if (filteredOut) STATUS_DISALLOWED_FEATURE_DISABLED
                        else STATUS_ALLOWED_DISABLED_MODE
                    notificationManager.logHsuNotificationPostStatus(entry.sbn, status)
                }
                return filteredOut
            }
        }

    companion object {
        private const val TAG = "HsuCoordinator"

        // TODO(b/414326600): use proper constants from allowlist class (once available)
        const val STATUS_ALLOWED_DISABLED_MODE = 2
        const val STATUS_DISALLOWED_FEATURE_DISABLED = -3
    }
}
