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

package com.android.systemui.statusbar.notification.row

import android.app.Notification
import android.content.applicationContext
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.testCase
import com.android.systemui.statusbar.notification.collection.BundleSpec
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.buildPromotedOngoingEntry
import org.mockito.Mockito
import org.mockito.kotlin.whenever

var Kosmos.expandableNotificationRowBuilder by
    Kosmos.Fixture {
        ExpandableNotificationRowBuilder(applicationContext, this, testCase.mDependency)
    }

fun Kosmos.createRowWithNotif(notification: Notification): ExpandableNotificationRow {
    return expandableNotificationRowBuilder.createRow(notification)
}

fun Kosmos.createRowWithEntry(entry: NotificationEntry): ExpandableNotificationRow {
    return expandableNotificationRowBuilder.createRow(entry)
}

fun Kosmos.createRowBundle(spec: BundleSpec): ExpandableNotificationRow {
    return expandableNotificationRowBuilder.createRowBundle(spec)
}

fun Kosmos.createRow(): ExpandableNotificationRow {
    return expandableNotificationRowBuilder.createRow()
}

fun Kosmos.createInitializedRow(): ExpandableNotificationRow {
    val row = Mockito.spy(createRow())
    whenever(row.hasFinishedInitialization()).thenReturn(true)
    return row
}

fun Kosmos.createPromotedOngoingRow(): ExpandableNotificationRow {
    return expandableNotificationRowBuilder.createRow(buildPromotedOngoingEntry())
}

/** An notification backed ExpandableNotificationRow with 4 children. */
fun Kosmos.createRowGroup(childCount: Int = 4): ExpandableNotificationRow {
    return expandableNotificationRowBuilder.createRowGroup(childCount)
}
