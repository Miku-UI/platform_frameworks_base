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

package com.android.systemui.statusbar.notification.collection

import com.android.systemui.statusbar.notification.NotificationActivityStarter
import com.android.systemui.statusbar.notification.collection.coordinator.VisualStabilityCoordinator
import com.android.systemui.statusbar.notification.collection.provider.HighPriorityProvider
import com.android.systemui.statusbar.notification.headsup.HeadsUpManager
import com.android.systemui.statusbar.notification.people.PeopleNotificationIdentifier
import com.android.systemui.statusbar.notification.row.NotificationActionClickManager
import com.android.systemui.statusbar.notification.row.OnUserInteractionCallback
import javax.inject.Inject

/** Creates an appropriate EntryAdapter for the entry type given */
class EntryAdapterFactoryImpl
@Inject
constructor(
    private val notificationActivityStarter: NotificationActivityStarter,
    private val peopleNotificationIdentifier: PeopleNotificationIdentifier,
    private val visualStabilityCoordinator: VisualStabilityCoordinator,
    private val notificationActionClickManager: NotificationActionClickManager,
    private val highPriorityProvider: HighPriorityProvider,
    private val headsUpManager: HeadsUpManager,
    private val onUserInteractionCallback: OnUserInteractionCallback,
    private val notifPipeline: NotifPipeline,
) : EntryAdapterFactory {
    override fun create(entry: PipelineEntry): EntryAdapter =
        when (entry) {
            is NotificationEntry ->
                NotificationEntryAdapter(
                    notificationActivityStarter,
                    peopleNotificationIdentifier,
                    visualStabilityCoordinator,
                    notificationActionClickManager,
                    highPriorityProvider,
                    headsUpManager,
                    onUserInteractionCallback,
                    entry,
                    notifPipeline,
                )
            is BundleEntry ->
                BundleEntryAdapter(highPriorityProvider, onUserInteractionCallback, entry)
            else -> error("Cannot create entry adapter for $entry")
        }
}
