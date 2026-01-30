/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.qs.composefragment.ui

import com.android.systemui.log.table.Diffable
import com.android.systemui.log.table.TableRowLogger

/** Params for [notificationScrimClip]. */
data class NotificationScrimClipParams(
    val top: Int = 0,
    val bottom: Int = 0,
    val leftInset: Int = 0,
    val rightInset: Int = 0,
    val radius: Int = 0,
) : Diffable<NotificationScrimClipParams> {
    override fun logDiffs(prevVal: NotificationScrimClipParams, row: TableRowLogger) {
        if (top != prevVal.top) {
            row.logChange(Columns.COL_TOP, top)
        }
        if (bottom != prevVal.bottom) {
            row.logChange(Columns.COL_BOTTOM, bottom)
        }
        if (leftInset != prevVal.leftInset) {
            row.logChange(Columns.COL_LEFT_INSET, leftInset)
        }
        if (rightInset != prevVal.rightInset) {
            row.logChange(Columns.COL_RIGHT_INSET, rightInset)
        }
        if (radius != prevVal.radius) {
            row.logChange(Columns.COL_RADIUS, radius)
        }
    }

    override fun logFull(row: TableRowLogger) {
        row.logChange(Columns.COL_TOP, top)
        row.logChange(Columns.COL_BOTTOM, bottom)
        row.logChange(Columns.COL_LEFT_INSET, leftInset)
        row.logChange(Columns.COL_RIGHT_INSET, rightInset)
        row.logChange(Columns.COL_RADIUS, radius)
    }
}

private object Columns {
    const val COL_TOP = "top"
    const val COL_BOTTOM = "bottom"
    const val COL_LEFT_INSET = "left_inset"
    const val COL_RIGHT_INSET = "right_inset"
    const val COL_RADIUS = "radius"
}
