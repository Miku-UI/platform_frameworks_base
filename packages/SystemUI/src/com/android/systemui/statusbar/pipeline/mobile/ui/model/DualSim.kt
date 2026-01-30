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

package com.android.systemui.statusbar.pipeline.mobile.ui.model

import com.android.systemui.log.table.Diffable
import com.android.systemui.log.table.TableLogBuffer
import com.android.systemui.log.table.TableRowLogger
import com.android.systemui.statusbar.pipeline.mobile.domain.model.SignalIconModel
import com.android.systemui.statusbar.pipeline.mobile.ui.model.DualSimConstants.COL_IS_NULL
import com.android.systemui.statusbar.pipeline.mobile.ui.model.DualSimConstants.COL_PRIMARY_SUB_ID
import com.android.systemui.statusbar.pipeline.mobile.ui.model.DualSimConstants.COL_SECONDARY_SUB_ID
import com.android.systemui.statusbar.pipeline.mobile.ui.model.DualSimConstants.PREFIX_PRIMARY
import com.android.systemui.statusbar.pipeline.mobile.ui.model.DualSimConstants.PREFIX_SECONDARY

data class DualSim(
    private val primarySubId: Int,
    private val secondarySubId: Int,
    val primary: SignalIconModel.Cellular,
    val secondary: SignalIconModel.Cellular,
) : Diffable<DualSim> {
    constructor(
        primary: Pair<Int, SignalIconModel.Cellular>,
        secondary: Pair<Int, SignalIconModel.Cellular>,
    ) : this(
        primarySubId = primary.first,
        primary = primary.second,
        secondarySubId = secondary.first,
        secondary = secondary.second,
    )

    override fun logDiffs(prevVal: DualSim, row: TableRowLogger) {
        if (prevVal != this) {
            logFull(row)
        } else {
            if (primarySubId != prevVal.primarySubId) {
                row.logChange(COL_PRIMARY_SUB_ID, primarySubId)
            }
            if (secondarySubId != prevVal.secondarySubId) {
                row.logChange(COL_SECONDARY_SUB_ID, secondarySubId)
            }
            primary.logDiffs(prevVal.primary, TableRowLoggerWithPrefix(row, PREFIX_PRIMARY))
            secondary.logDiffs(prevVal.secondary, TableRowLoggerWithPrefix(row, PREFIX_SECONDARY))
        }
    }

    override fun logFull(row: TableRowLogger) {
        row.logChange(COL_PRIMARY_SUB_ID, primarySubId)
        row.logChange(COL_SECONDARY_SUB_ID, secondarySubId)
        primary.logFull(TableRowLoggerWithPrefix(row, PREFIX_PRIMARY))
        secondary.logFull(TableRowLoggerWithPrefix(row, PREFIX_SECONDARY))
    }

    /**
     * Wrapper for [TableRowLogger] to add a prefix to all columns. Useful when logging multiple
     * [SignalIconModel] while being able to differentiate them.
     */
    private class TableRowLoggerWithPrefix(
        private val row: TableRowLogger,
        private val prefix: String,
    ) : TableRowLogger {
        override fun logChange(columnName: String, value: String?) {
            row.logChange("$prefix.$columnName", value)
        }

        override fun logChange(columnName: String, value: Boolean) {
            row.logChange("$prefix.$columnName", value)
        }

        override fun logChange(columnName: String, value: Int) {
            row.logChange("$prefix.$columnName", value)
        }
    }
}

/**
 * Tries to build a [DualSim] from the list of [SignalIconModel].
 *
 * A [DualSim] requires the connections to be stackable, meaning that there's exactly two cellular
 * connections.
 *
 * @param idsToIcon list of subscription ids to [SignalIconModel]
 * @return a [DualSim] representing the connections, or null if the connections are not stackable.
 */
fun tryParseDualSim(idsToIcon: List<Pair<Int, SignalIconModel>>): DualSim? {
    var first: Pair<Int, SignalIconModel.Cellular>? = null
    var second: Pair<Int, SignalIconModel.Cellular>? = null
    for ((id, icon) in idsToIcon) {
        when {
            icon !is SignalIconModel.Cellular -> continue
            first == null -> {
                first = id to icon
            }
            second == null -> {
                second = id to icon
            }
            else -> return null
        }
    }
    return first?.let { second?.let { DualSim(first, second) } }
}

/** Logs the [DualSim] difference between [old] and [new] using the provided [TableLogBuffer]. */
fun logDualSimDiff(old: DualSim?, new: DualSim?, tableLogger: TableLogBuffer) {
    if (old != null && new != null) {
        tableLogger.logChange { new.logDiffs(old, it) }
    } else if (old == null && new != null) {
        tableLogger.logChange {
            it.logChange(COL_IS_NULL, false)
            new.logFull(it)
        }
    } else if (old != null) {
        tableLogger.logChange { it.logChange(COL_IS_NULL, true) }
    }
}

private object DualSimConstants {
    const val COL_IS_NULL = "isNull"
    const val COL_PRIMARY_SUB_ID = "primarySubId"
    const val COL_SECONDARY_SUB_ID = "secondarySubId"
    const val PREFIX_PRIMARY = "primary"
    const val PREFIX_SECONDARY = "secondary"
}
