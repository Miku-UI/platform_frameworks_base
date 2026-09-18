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

package com.android.systemui.privacy

import android.text.format.DateUtils
import android.util.IndentingPrintWriter
import com.android.internal.annotations.GuardedBy
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.util.time.SystemClock
import com.android.systemui.util.withIncreasedIndent
import javax.inject.Inject

/** Logger for location indicator accumulated logs. */
@SysUISingleton
class LocationAccumulatedLogger @Inject constructor(private val clock: SystemClock) {
    data class LocationIndicatorLog(
        var blinkCount: Int = 0,
        var onDuration: Long = 0L,
        var onStartTime: Long = -1L,
    )

    enum class LocationLogType {
        NON_SYSTEM_FG,
        SYSTEM,
        BACKGROUND,
        ALL,
    }

    private val lock = Any()
    @GuardedBy("lock") private var loggingStartTime = -1L
    @GuardedBy("lock")
    private val locationLogs =
        mapOf(
            LocationLogType.NON_SYSTEM_FG to LocationIndicatorLog(),
            LocationLogType.SYSTEM to LocationIndicatorLog(),
            LocationLogType.BACKGROUND to LocationIndicatorLog(),
            LocationLogType.ALL to LocationIndicatorLog(),
        )

    fun startLogging() {
        // Start the timer for logging total time in session.
        if (loggingStartTime == -1L) {
            loggingStartTime = clock.elapsedRealtime()
        }
    }

    fun logForType(type: LocationLogType, currentState: Boolean) {
        synchronized(lock) {
            val log = locationLogs[type] ?: return
            if (currentState) { // Turning ON
                log.blinkCount++
                log.onStartTime = clock.elapsedRealtime()
            } else { // Turning OFF
                val now = clock.elapsedRealtime()
                if (log.onStartTime != -1L) {
                    log.onDuration += now - log.onStartTime
                    log.onStartTime = -1L
                }
            }
        }
    }

    fun writeToPrintWriter(ipw: IndentingPrintWriter) {
        if (loggingStartTime != -1L) {
            val totalDurationMs = clock.elapsedRealtime() - loggingStartTime
            ipw.println("Location Indicator logging:")
            ipw.withIncreasedIndent {
                ipw.println("Total logging duration: ${formatDuration(totalDurationMs)}")
                locationLogs.forEach { (logType, log) ->
                    ipw.println(
                        "$logType: blinks=${log.blinkCount}, onDuration=${formatDuration(log.onDuration)}"
                    )
                }
            }
        }
    }

    private fun formatDuration(ms: Long): String {
        return DateUtils.formatDuration(ms, DateUtils.LENGTH_SHORTEST).toString()
    }
}
