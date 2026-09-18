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

package com.android.systemui.privacy.logging

import android.util.IndentingPrintWriter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.privacy.LocationAccumulatedLogger
import com.android.systemui.util.time.FakeSystemClock
import com.google.common.truth.Truth.assertThat
import java.io.PrintWriter
import java.io.StringWriter
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class LocationAccumulatedLoggerTest : SysuiTestCase() {

    private lateinit var clock: FakeSystemClock
    private lateinit var logger: LocationAccumulatedLogger

    @Before
    fun setup() {
        clock = FakeSystemClock()
        logger = LocationAccumulatedLogger(clock)
    }

    @Test
    fun testStartLogging_setsStartTime() {
        logger.startLogging()
        clock.advanceTime(1000L)
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)

        logger.writeToPrintWriter(IndentingPrintWriter(printWriter))
        val output = stringWriter.toString()
        assertThat(output).contains("Total logging duration: 1s")
    }

    @Test
    fun testLogForType_onOffCycle_updatesDurationAndBlinks() {
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        logger.startLogging()

        // On
        logger.logForType(LocationAccumulatedLogger.LocationLogType.NON_SYSTEM_FG, true)
        clock.advanceTime(5000L)
        // Off
        logger.logForType(LocationAccumulatedLogger.LocationLogType.NON_SYSTEM_FG, false)

        logger.writeToPrintWriter(IndentingPrintWriter(printWriter))
        val output = stringWriter.toString()
        assertThat(output).contains("NON_SYSTEM_FG: blinks=1, onDuration=5s")
    }

    @Test
    fun testLogForType_multipleOnOffCycles() {
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        logger.startLogging()

        // Cycle 1
        logger.logForType(LocationAccumulatedLogger.LocationLogType.ALL, true)
        clock.advanceTime(10_000L) // 10s on
        logger.logForType(LocationAccumulatedLogger.LocationLogType.ALL, false)
        clock.advanceTime(5_000L) // 5s off

        // Cycle 2
        logger.logForType(LocationAccumulatedLogger.LocationLogType.ALL, true)
        clock.advanceTime(20_000L) // 20s on
        logger.logForType(LocationAccumulatedLogger.LocationLogType.ALL, false)

        logger.writeToPrintWriter(IndentingPrintWriter(printWriter))
        val output = stringWriter.toString()
        assertThat(output).contains("ALL: blinks=2, onDuration=30s")
    }

    @Test
    fun testWriteToPrintWriter_noLoggingStarted_writesNothing() {
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)

        logger.writeToPrintWriter(IndentingPrintWriter(printWriter))

        val output = stringWriter.toString()
        assertThat(output).isEmpty()
    }

    @Test
    fun testWriteToPrintWriter_logsAllTypes() {
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        logger.startLogging()

        logger.logForType(LocationAccumulatedLogger.LocationLogType.NON_SYSTEM_FG, true)
        clock.advanceTime(1_000L)
        logger.logForType(LocationAccumulatedLogger.LocationLogType.NON_SYSTEM_FG, false)

        logger.logForType(LocationAccumulatedLogger.LocationLogType.SYSTEM, true)
        clock.advanceTime(2_000L)
        logger.logForType(LocationAccumulatedLogger.LocationLogType.SYSTEM, false)

        logger.logForType(LocationAccumulatedLogger.LocationLogType.BACKGROUND, true)
        clock.advanceTime(3_000L)
        logger.logForType(LocationAccumulatedLogger.LocationLogType.BACKGROUND, false)

        logger.logForType(LocationAccumulatedLogger.LocationLogType.ALL, true)
        clock.advanceTime(4_000L)
        logger.logForType(LocationAccumulatedLogger.LocationLogType.ALL, false)

        logger.writeToPrintWriter(IndentingPrintWriter(printWriter))
        val output = stringWriter.toString()
        assertThat(output).contains("NON_SYSTEM_FG: blinks=1, onDuration=1s")
        assertThat(output).contains("SYSTEM: blinks=1, onDuration=2s")
        assertThat(output).contains("BACKGROUND: blinks=1, onDuration=3s")
        assertThat(output).contains("ALL: blinks=1, onDuration=4s")
    }
}
