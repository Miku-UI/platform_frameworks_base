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

package com.android.systemui.statusbar.notification.data.model

import com.android.systemui.statusbar.notification.shared.ActiveNotificationGroupModel
import com.android.systemui.statusbar.notification.shared.ActiveNotificationModel

/** Simple ActiveNotificationModel builder for use in tests. */
fun activeNotificationGroupModel(
    groupKey: String,
    isSuppressedFromStatusBar: Boolean = false,
    numChildren: Int = 2,
): ActiveNotificationGroupModel {
    val summary = activeNotificationModel("summary", groupKey = groupKey, isGroupSummary = true)
    val children = ArrayList<ActiveNotificationModel>()
    for (i in 1..numChildren) {
        children.add(
            activeNotificationModel(
                key = "notif$groupKey$i",
                groupKey = groupKey,
                isSuppressedFromStatusBar = isSuppressedFromStatusBar,
            )
        )
    }
    return ActiveNotificationGroupModel(key = groupKey, summary = summary, children = children)
}
