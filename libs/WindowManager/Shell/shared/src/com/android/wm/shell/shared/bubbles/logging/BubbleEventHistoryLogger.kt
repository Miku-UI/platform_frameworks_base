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

package com.android.wm.shell.shared.bubbles.logging

import android.icu.text.SimpleDateFormat
import android.os.Build
import android.text.TextUtils
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.android.wm.shell.shared.bubbles.logging.BubbleEventHistoryLogger.Companion.MAX_EVENTS_DEBUG
import java.io.PrintWriter
import java.util.Locale

/**
 * An implementation of [DebugLogger] that stores a history of events, respecting the
 * [MAX_EVENTS_DEBUG] limit. It can add events history as part of a system dump method.
 */
class BubbleEventHistoryLogger(isUserBuild: Boolean = Build.IS_USER && !Build.IS_DEBUGGABLE) :
    DebugLogger {

    private val maxLogEvents =
        if (isUserBuild) {
            MAX_EVENTS_RELEASE
        } else {
            MAX_EVENTS_DEBUG
        }

    @VisibleForTesting val recentEvents: MutableList<BubbleEvent> = mutableListOf()

    override fun d(message: String, vararg parameters: Any?, eventData: String?) {
        if (!Log.isLoggable(TAG, Log.DEBUG)) return
        logEvent(title = "d: $message", titleParams = parameters, eventData = eventData)
    }

    override fun v(message: String, vararg parameters: Any?, eventData: String?) {
        if (!Log.isLoggable(TAG, Log.VERBOSE)) return
        logEvent(title = "v: $message", titleParams = parameters, eventData = eventData)
    }

    override fun i(message: String, vararg parameters: Any?, eventData: String?) {
        logEvent(title = "i: $message", titleParams = parameters, eventData = eventData)
    }

    override fun w(message: String, vararg parameters: Any?, eventData: String?) {
        logEvent(title = "w: $message", titleParams = parameters, eventData = eventData)
    }

    override fun e(message: String, vararg parameters: Any?, eventData: String?) {
        logEvent(title = "e: $message", titleParams = parameters, eventData = eventData)
    }

    /** Flushes all stored logs. */
    fun flush() {
        recentEvents.clear()
    }

    /**
     * Logs a RECORD level message.
     *
     * The [message] is a format string, with [parameters] substituted into it. An optional
     * [eventData] string may also be provided, which implementations can include in the log output.
     */
    fun record(message: String, vararg parameters: Any?, eventData: String?) {
        logEvent(title = "r: $message", titleParams = parameters, eventData = eventData)
    }

    @VisibleForTesting
    @Synchronized
    fun logEvent(
        title: String,
        vararg titleParams: Any?,
        eventData: String? = null,
        timestamp: Long = System.currentTimeMillis(),
    ) {
        if (recentEvents.size >= maxLogEvents) {
            recentEvents.removeAt(0)
        }
        // Transform the params to prevent memory leaks.
        // If the object is a primitive wrapper, keep it. Otherwise, call toString() now.
        val convertedParams =
            titleParams
                .map { param ->
                    when (param) {
                        null -> null
                        is Boolean,
                        is Int,
                        is Long,
                        is Float,
                        is Double,
                        is Char,
                        is Byte,
                        is Short,
                        is String -> param

                        else -> param.toString() // Immediately break the strong reference
                    }
                }
                .toTypedArray<Any?>()
        @Suppress("UNCHECKED_CAST")
        recentEvents.add(
            BubbleEvent(
                title = title,
                titleParams = convertedParams,
                eventData = eventData,
                timestamp = timestamp,
            )
        )
    }

    /**
     * Dumps the collected events history to the provided [PrintWriter], adding the [prefix] at the
     * beginning of each line.
     */
    fun dump(pw: PrintWriter, prefix: String = "") {
        val recentEventsCopy = synchronized(this) { ArrayList(recentEvents) }
        pw.println("${prefix}Bubbles events history:")
        recentEventsCopy.forEach { event ->
            try {
                val eventFormattedTime = DATE_FORMATTER.format(event.timestamp)
                val eventFormattedTitle =
                    if (event.titleParams.isNullOrEmpty()) {
                        event.title
                    } else {
                        TextUtils.formatSimple(event.title, *event.titleParams)
                    }
                val data = if (event.eventData.isNullOrBlank()) "" else " | ${event.eventData}"
                pw.println("$prefix  $eventFormattedTime ${eventFormattedTitle}$data")
            } catch (e: Throwable) {
                Log.w(TAG, "Caught an exception while logging: $event")
            }
        }
    }

    companion object {
        const val TAG = "BubblesHistoryLogger"
        const val DATE_FORMAT = "MM-dd HH:mm:ss.SSS"
        const val MAX_EVENTS_RELEASE: Int = 20
        const val MAX_EVENTS_DEBUG: Int = 200
        @VisibleForTesting val DATE_FORMATTER = SimpleDateFormat(DATE_FORMAT, Locale.US)
    }
}
