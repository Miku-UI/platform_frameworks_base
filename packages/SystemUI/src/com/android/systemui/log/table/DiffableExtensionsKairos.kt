/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.log.table

import com.android.systemui.kairos.BuildScope
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.State
import com.android.systemui.kairos.changes
import com.android.systemui.kairos.effectSync
import com.android.systemui.kairos.util.NameTag

// See [com.android.systemui.log.table.DiffableExtensions.kt] for non-Kairos extension functions.

/** See [logDiffsForTable(TableLogBuffer, String, T)]. */
@ExperimentalKairosApi
@JvmName("logIntDiffsForTable")
fun BuildScope.logDiffsForTable(
    name: NameTag,
    intState: State<Int?>,
    tableLogBuffer: TableLogBuffer,
    columnPrefix: String = "",
    columnName: String,
) {
    var isInitial = true
    intState.observe(name = name) { new ->
        tableLogBuffer.logChange(columnPrefix, columnName, new, isInitial = isInitial)
        isInitial = false
    }
}

/**
 * Each time the flow is updated with a new value, logs the differences between the previous value
 * and the new value to the given [tableLogBuffer].
 *
 * The new value's [Diffable.logDiffs] method will be used to log the differences to the table.
 *
 * @param columnPrefix a prefix that will be applied to every column name that gets logged.
 */
@ExperimentalKairosApi
fun <T : Diffable<T>> BuildScope.logDiffsForTable(
    name: NameTag,
    diffableState: State<T>,
    tableLogBuffer: TableLogBuffer,
    columnPrefix: String = "",
) {
    val initialValue = diffableState.sampleDeferred()
    effectSync(name) {
        // Fully log the initial value to the table.
        tableLogBuffer.logChange(columnPrefix, isInitial = true) { row ->
            initialValue.value.logFull(row)
        }
    }
    diffableState.changes.observeSync(name) { newState ->
        val prevState = diffableState.sample()
        tableLogBuffer.logDiffs(columnPrefix, prevVal = prevState, newVal = newState)
    }
}

/** See [logDiffsForTable(TableLogBuffer, String, T)]. */
@ExperimentalKairosApi
@JvmName("logBooleanDiffsForTable")
fun BuildScope.logDiffsForTable(
    name: NameTag,
    booleanState: State<Boolean>,
    tableLogBuffer: TableLogBuffer,
    columnPrefix: String = "",
    columnName: String,
) {
    var isInitial = true
    booleanState.observe(name = name) { new ->
        tableLogBuffer.logChange(columnPrefix, columnName, new, isInitial = isInitial)
        isInitial = false
    }
}

/** See [logDiffsForTable(TableLogBuffer, String, T)]. */
@ExperimentalKairosApi
@JvmName("logStringDiffsForTable")
fun BuildScope.logDiffsForTable(
    name: NameTag,
    stringState: State<String?>,
    tableLogBuffer: TableLogBuffer,
    columnPrefix: String = "",
    columnName: String,
) {
    var isInitial = true
    stringState.observe(name = name) { new ->
        tableLogBuffer.logChange(columnPrefix, columnName, new, isInitial = isInitial)
        isInitial = false
    }
}

/** See [logDiffsForTable(TableLogBuffer, String, T)]. */
@ExperimentalKairosApi
@JvmName("logListDiffsForTable")
fun <T> BuildScope.logDiffsForTable(
    name: NameTag,
    listState: State<List<T>>,
    tableLogBuffer: TableLogBuffer,
    columnPrefix: String = "",
    columnName: String,
) {
    var isInitial = true
    listState.observe(name = name) { new ->
        tableLogBuffer.logChange(columnPrefix, columnName, new.toString(), isInitial = isInitial)
        isInitial = false
    }
}
