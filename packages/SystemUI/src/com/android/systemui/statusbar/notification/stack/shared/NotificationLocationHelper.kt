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

package com.android.systemui.statusbar.notification.stack.shared

import com.android.internal.statusbar.NotificationVisibility.NotificationLocation
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.stack.ExpandableViewState.LOCATION_BOTTOM_STACK_HIDDEN
import com.android.systemui.statusbar.notification.stack.ExpandableViewState.LOCATION_BOTTOM_STACK_PEEKING
import com.android.systemui.statusbar.notification.stack.ExpandableViewState.LOCATION_FIRST_HUN
import com.android.systemui.statusbar.notification.stack.ExpandableViewState.LOCATION_GONE
import com.android.systemui.statusbar.notification.stack.ExpandableViewState.LOCATION_HIDDEN_TOP
import com.android.systemui.statusbar.notification.stack.ExpandableViewState.LOCATION_MAIN_AREA

/** Returns the location of the notification referenced by the given [NotificationEntry]. */
fun getNotificationLocation(entry: NotificationEntry?): NotificationLocation {
    if (entry == null || entry.row == null) {
        return NotificationLocation.LOCATION_UNKNOWN
    }
    return convertNotificationLocation(entry.row.viewState.location)
}

private fun convertNotificationLocation(location: Int): NotificationLocation {
    return when (location) {
        LOCATION_FIRST_HUN -> NotificationLocation.LOCATION_FIRST_HEADS_UP
        LOCATION_HIDDEN_TOP -> NotificationLocation.LOCATION_HIDDEN_TOP
        LOCATION_MAIN_AREA -> NotificationLocation.LOCATION_MAIN_AREA
        LOCATION_BOTTOM_STACK_PEEKING -> NotificationLocation.LOCATION_BOTTOM_STACK_PEEKING
        LOCATION_BOTTOM_STACK_HIDDEN -> NotificationLocation.LOCATION_BOTTOM_STACK_HIDDEN
        LOCATION_GONE -> NotificationLocation.LOCATION_GONE
        else -> NotificationLocation.LOCATION_UNKNOWN
    }
}
