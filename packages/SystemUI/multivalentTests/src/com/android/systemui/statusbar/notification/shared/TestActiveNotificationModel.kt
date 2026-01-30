/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 *
 */

package com.android.systemui.statusbar.notification.shared

import com.android.systemui.statusbar.notification.icon.domain.interactor.ActiveNotificationIconModel
import com.google.common.truth.Correspondence

val byKey: Correspondence<ActiveNotificationModel, String> =
    Correspondence.transforming({ it.key }, "has a key of")
val byIconIsAmbient: Correspondence<ActiveNotificationIconModel, Boolean> =
    Correspondence.transforming({ it.isAmbient }, "has an isAmbient value of")
val byAssociatedNotifModel: Correspondence<ActiveNotificationIconModel, ActivePipelineEntryModel> =
    Correspondence.transforming(
        /* actualTransform = */ { it },
        /* expectedTransform = */ { expected ->
            checkNotNull(expected)
            when (expected) {
                is ActiveBundleModel ->
                    ActiveNotificationIconModel(
                        expected.key,
                        expected.key,
                        expected.icon,
                        expected.icon,
                        expected.icon,
                        false,
                    )
                is ActiveNotificationGroupModel ->
                    ActiveNotificationIconModel(
                        expected.key,
                        expected.summary.groupKey!!,
                        expected.summary.shelfIcon,
                        expected.summary.statusBarIcon,
                        expected.summary.aodIcon,
                        expected.summary.isAmbient,
                    )
                is ActiveNotificationModel ->
                    ActiveNotificationIconModel(
                        expected.key,
                        expected.groupKey!!,
                        expected.shelfIcon,
                        expected.statusBarIcon,
                        expected.aodIcon,
                        expected.isAmbient,
                    )
            }
        },
        /* description = */ "is icon model of",
    )

val byIconNotifKey: Correspondence<ActiveNotificationIconModel, String> =
    Correspondence.transforming({ it.notifKey }, "has a notifKey of")
