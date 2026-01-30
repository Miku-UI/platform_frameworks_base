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

import android.annotation.SuppressLint
import android.icu.text.SimpleDateFormat
import android.os.Trace
import com.android.systemui.common.buffer.RingBuffer
import com.android.systemui.log.LogProxy
import com.android.systemui.log.LogcatEchoTracker
import com.android.systemui.log.core.LogLevel
import com.android.systemui.util.time.SystemClock
import java.io.PrintWriter
import java.util.Locale

@SuppressLint("DumpableNotRegistered") // Registered as dumpable in [TableLogBufferFactory]
class TableLogBufferImpl(
    maxSize: Int,
    private val name: String,
    private val systemClock: SystemClock,
    private val logcatEchoTracker: LogcatEchoTracker,
    private val localLogcat: LogProxy,
) : TableLogBuffer {
    init {
        if (maxSize <= 0) {
            throw IllegalArgumentException("maxSize must be > 0")
        }
    }

    private val buffer = RingBuffer(maxSize) { TableChange() }

    // Stores the most recently evicted value for each column name (indexed on column name).
    //
    // Why it's necessary: Because we use a RingBuffer of a fixed size, it's possible that a column
    // that's logged infrequently will eventually get pushed out by a different column that's
    // logged more frequently. Then, that infrequently-logged column isn't present in the RingBuffer
    // at all and we have no logs that the column ever existed. This is a problem because the
    // column's information is still relevant, valid, and may be critical to debugging issues.
    //
    // Fix: When a change is being evicted from the RingBuffer, we store it in this map (based on
    // its [TableChange.getName()]. This ensures that we always have at least one value for every
    // column ever logged. See b/272016422 for more details.
    private val lastEvictedValues = mutableMapOf<String, TableChange>()

    // A [TableRowLogger] object, re-used each time [logDiffs] is called.
    // (Re-used to avoid object allocation.)
    private val tempRow =
        TableRowLoggerImpl(
            timestamp = 0,
            columnPrefix = "",
            isInitial = false,
            tableLogBuffer = this,
        )

    @Synchronized
    override fun <T : Diffable<T>> logDiffs(columnPrefix: String, prevVal: T, newVal: T) {
        val row = tempRow
        row.timestamp = systemClock.currentTimeMillis()
        row.columnPrefix = columnPrefix
        // Because we have a prevVal and a newVal, we know that this isn't the initial log.
        row.isInitial = false
        newVal.logDiffs(prevVal, row)
    }

    @Synchronized
    override fun logChange(
        columnPrefix: String,
        isInitial: Boolean,
        rowInitializer: (TableRowLogger) -> Unit,
    ) {
        val row = tempRow
        row.timestamp = systemClock.currentTimeMillis()
        row.columnPrefix = columnPrefix
        row.isInitial = isInitial
        rowInitializer(row)
    }

    override fun logChange(prefix: String, columnName: String, value: String?, isInitial: Boolean) {
        logChange(systemClock.currentTimeMillis(), prefix, columnName, value, isInitial)
    }

    override fun logChange(prefix: String, columnName: String, value: Boolean, isInitial: Boolean) {
        logChange(systemClock.currentTimeMillis(), prefix, columnName, value, isInitial)
    }

    override fun logChange(prefix: String, columnName: String, value: Int?, isInitial: Boolean) {
        logChange(systemClock.currentTimeMillis(), prefix, columnName, value, isInitial)
    }

    // Keep these individual [logChange] methods private (don't let clients give us their own
    // timestamps.)

    private fun logChange(
        timestamp: Long,
        prefix: String,
        columnName: String,
        value: String?,
        isInitial: Boolean,
    ) {
        Trace.beginSection("TableLogBuffer#logChange(string)")
        val change = obtain(timestamp, prefix, columnName, isInitial)
        change.set(value)
        echoToDesiredEndpoints(change)
        Trace.endSection()
    }

    private fun logChange(
        timestamp: Long,
        prefix: String,
        columnName: String,
        value: Boolean,
        isInitial: Boolean,
    ) {
        Trace.beginSection("TableLogBuffer#logChange(boolean)")
        val change = obtain(timestamp, prefix, columnName, isInitial)
        change.set(value)
        echoToDesiredEndpoints(change)
        Trace.endSection()
    }

    private fun logChange(
        timestamp: Long,
        prefix: String,
        columnName: String,
        value: Int?,
        isInitial: Boolean,
    ) {
        Trace.beginSection("TableLogBuffer#logChange(int)")
        val change = obtain(timestamp, prefix, columnName, isInitial)
        change.set(value)
        echoToDesiredEndpoints(change)
        Trace.endSection()
    }

    // TODO(b/259454430): Add additional change types here.

    @Synchronized
    private fun obtain(
        timestamp: Long,
        prefix: String,
        columnName: String,
        isInitial: Boolean,
    ): TableChange {
        verifyValidName(prefix, columnName)
        val tableChange = buffer.advance()
        if (tableChange.hasData()) {
            saveEvictedValue(tableChange)
        }
        tableChange.reset(timestamp, prefix, columnName, isInitial)
        return tableChange
    }

    private fun verifyValidName(prefix: String, columnName: String) {
        if (prefix.contains(SEPARATOR)) {
            throw IllegalArgumentException("columnPrefix cannot contain $SEPARATOR but was $prefix")
        }
        if (columnName.contains(SEPARATOR)) {
            throw IllegalArgumentException(
                "columnName cannot contain $SEPARATOR but was $columnName"
            )
        }
    }

    private fun saveEvictedValue(change: TableChange) {
        Trace.beginSection("TableLogBuffer#saveEvictedValue")
        val name = change.getName()
        val previouslyEvicted =
            lastEvictedValues[name] ?: TableChange().also { lastEvictedValues[name] = it }
        // For recycling purposes, update the existing object in the map with the new information
        // instead of creating a new object.
        previouslyEvicted.updateTo(change)
        Trace.endSection()
    }

    private fun echoToDesiredEndpoints(change: TableChange) {
        if (
            logcatEchoTracker.isBufferLoggable(bufferName = name, LogLevel.DEBUG) ||
                logcatEchoTracker.isTagLoggable(change.getColumnName(), LogLevel.DEBUG)
        ) {
            if (change.hasData()) {
                localLogcat.d(name, change.logcatRepresentation())
            }
        }
    }

    @Synchronized
    override fun dump(pw: PrintWriter, args: Array<out String>) {
        pw.append(HEADER_PREFIX).println(name)
        pw.append("version ").println(VERSION)

        lastEvictedValues.values.sortedBy { it.timestamp }.forEach { it.dump(pw) }
        for (i in 0 until buffer.size) {
            buffer[i].dump(pw)
        }
        pw.append(FOOTER_PREFIX).println(name)
    }

    /** Dumps an individual [TableChange]. */
    private fun TableChange.dump(pw: PrintWriter) {
        if (!this.hasData()) {
            return
        }
        val formattedTimestamp = TABLE_LOG_DATE_FORMAT.format(timestamp)
        pw.print(formattedTimestamp)
        pw.print(SEPARATOR)
        pw.print(this.getName())
        pw.print(SEPARATOR)
        pw.print(this.getVal())
        pw.println()
    }

    /** Transforms an individual [TableChange] into a String for logcat */
    private fun TableChange.logcatRepresentation(): String {
        val formattedTimestamp = TABLE_LOG_DATE_FORMAT.format(timestamp)
        return "$formattedTimestamp$SEPARATOR${getName()}$SEPARATOR${getVal()}"
    }

    /**
     * A private implementation of [TableRowLogger].
     *
     * Used so that external clients can't modify [timestamp].
     */
    private class TableRowLoggerImpl(
        var timestamp: Long,
        var columnPrefix: String,
        var isInitial: Boolean,
        val tableLogBuffer: TableLogBufferImpl,
    ) : TableRowLogger {
        /** Logs a change to a string value. */
        override fun logChange(columnName: String, value: String?) {
            tableLogBuffer.logChange(timestamp, columnPrefix, columnName, value, isInitial)
        }

        /** Logs a change to a boolean value. */
        override fun logChange(columnName: String, value: Boolean) {
            tableLogBuffer.logChange(timestamp, columnPrefix, columnName, value, isInitial)
        }

        /** Logs a change to an int value. */
        override fun logChange(columnName: String, value: Int) {
            tableLogBuffer.logChange(timestamp, columnPrefix, columnName, value, isInitial)
        }
    }
}

val TABLE_LOG_DATE_FORMAT = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

private const val HEADER_PREFIX = "SystemUI StateChangeTableSection START: "
private const val FOOTER_PREFIX = "SystemUI StateChangeTableSection END: "
private const val SEPARATOR = "|"
private const val VERSION = "1"
