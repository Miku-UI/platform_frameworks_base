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

package com.android.systemui.statusbar.notification.data.repository

import android.content.applicationContext
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.statusbar.notification.data.model.activeBundleModel
import com.android.systemui.statusbar.notification.data.model.activeNotificationModel
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentBuilder
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel.Style.Base
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModels
import com.android.systemui.statusbar.notification.shared.ActiveBundleModel
import com.android.systemui.statusbar.notification.shared.ActiveNotificationGroupModel
import com.android.systemui.statusbar.notification.shared.ActiveNotificationModel

fun Kosmos.getPopulatedActiveNotificationsStore(): ActiveNotificationsStore {
    val testIcons =
        listOf(
            activeNotificationModel(key = "notif1", groupKey = "g1"),
            activeNotificationModel(key = "notif2", groupKey = "g2", isAmbient = true),
            activeNotificationModel(key = "notif3", groupKey = "g3", isRowDismissed = true),
            activeNotificationModel(key = "notif4", groupKey = "g4", isSilent = true),
            activeNotificationModel(key = "notif5", groupKey = "g5", isLastMessageFromReply = true),
            activeNotificationModel(
                key = "notif6",
                groupKey = "g6",
                isSuppressedFromStatusBar = true,
            ),
            activeNotificationModel(key = "notif7", groupKey = "g7", isPulsing = true),
            activeNotificationModel(
                key = "notif8",
                groupKey = "g8",
                isSuppressedFromStatusBar = true,
                isAmbient = true,
            ),
            activeNotificationModel(
                key = "notif9",
                groupKey = "g9",
                promotedContent = promotedContent("notif9", Base),
            ),
            activeBundleModel(key = "bundle1", context = applicationContext),
            activeBundleModel(
                key = "bundle2",
                isSuppressedFromStatusBar = true,
                context = applicationContext,
            ),
        )

    return ActiveNotificationsStore.Builder()
        .apply {
            testIcons.forEach { entry ->
                when (entry) {
                    is ActiveBundleModel -> addBundle(entry)
                    is ActiveNotificationGroupModel -> addNotifGroup(entry)
                    is ActiveNotificationModel -> addIndividualNotif(entry)
                }
            }
        }
        .build()
}

private fun promotedContent(
    key: String,
    style: PromotedNotificationContentModel.Style,
): PromotedNotificationContentModels {
    return PromotedNotificationContentBuilder(key).applyToShared { this.style = style }.build()
}
