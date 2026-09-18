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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.wm.shell.shared.bubbles.logging.BubbleEventHistoryLogger.Companion.DATE_FORMAT
import com.android.wm.shell.shared.bubbles.logging.BubbleEventHistoryLogger.Companion.DATE_FORMATTER
import com.android.wm.shell.shared.bubbles.logging.BubbleEventHistoryLogger.Companion.MAX_EVENTS_DEBUG
import com.android.wm.shell.shared.bubbles.logging.BubbleEventHistoryLogger.Companion.MAX_EVENTS_RELEASE
import com.google.common.truth.Truth.assertThat
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/** Unit tests for [BubbleEventHistoryLogger]. */
@SmallTest
@RunWith(AndroidJUnit4::class)
class BubbleEventHistoryLoggerTest {

    private val logger = BubbleEventHistoryLogger(isUserBuild = false)
    private val releaseLogger = BubbleEventHistoryLogger(isUserBuild = true)
    private val logPattern = Regex("^\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3} .: .*$")

    @Test
    fun dump_printsHeaderWhenNoEvents() {
        val expectedOutput = "Bubbles events history:\n"
        assertThat(getDumpOutput()).isEqualTo(expectedOutput)
    }

    @Test
    fun dump_printsHeaderWithEvents() {
        val timestamp = System.currentTimeMillis()
        val formattedTimeStamp = DATE_FORMATTER.format(timestamp)
        logger.logEvent("e: test | %s", "eventData", timestamp = timestamp)
        logger.logEvent("d: hey", timestamp = timestamp)
        val expectedOutput =
            """
            Bubbles events history:
              $formattedTimeStamp e: test | eventData
              $formattedTimeStamp d: hey
            """
                .trimIndent() + "\n"
        assertThat(getDumpOutput()).isEqualTo(expectedOutput)
    }

    @Test
    fun dump_respectsMAX_EVENTS() {
        repeat(MAX_EVENTS_DEBUG + 10) { logger.d(message = "title") }
        repeat(MAX_EVENTS_RELEASE + 10) { releaseLogger.i(message = "title") }

        val linesCount = getTrimmedLogLines().size
        val releaseLinesCount = getTrimmedLogLines(releaseLogger).size

        assertThat(linesCount).isEqualTo(MAX_EVENTS_DEBUG)
        assertThat(releaseLinesCount).isEqualTo(MAX_EVENTS_RELEASE)
    }

    @Test
    fun dump_printsEventsInChronologicalOrderStartingFromTheOldestEvent() {
        val repetitions = MAX_EVENTS_DEBUG * 2
        repeat(repetitions) { repetition ->
            logger.logEvent(title = "", timestamp = repetition.toLong())
        }
        val lastEventDateTime = DATE_FORMATTER.format(repetitions - 1)
        val logLines = getTrimmedLogLines()

        assertThat(logLines).isInOrder()
        // last log entry corresponds to the most recent event
        assertThat(logLines.last()).contains(lastEventDateTime)
    }

    @Test
    fun dump_printsEventsInExpectedFormat() {
        logger.record("test", eventData = "dump record")
        logger.d("test %b", true, eventData = "eventData")
        logger.v("test %d", 0, eventData = "eventData")
        logger.i("test %s", "stringArgument", eventData = "eventData")
        logger.w("test")
        logger.e("test", eventData = "eventData")

        val logLines = getTrimmedLogLines()

        assertLogFormat(logLines[0], "r: test | dump record")
        assertLogFormat(logLines[1], "d: test true | eventData")
        assertLogFormat(logLines[2], "v: test 0 | eventData")
        assertLogFormat(logLines[3], "i: test stringArgument | eventData")
        assertLogFormat(logLines[4], "w: test")
        assertLogFormat(logLines[5], "e: test | eventData")
    }

    @Test
    fun dump_release_logger_printsInfoAndUpEventsInExpectedFormat() {
        try {
            setAllLogsEnabled(false)
            releaseLogger.record("test", eventData = "dump record")
            releaseLogger.d("test %b", true, eventData = "eventData")
            releaseLogger.v("test %d", 0, eventData = "eventData")
            releaseLogger.i("test %s", "stringArgument", eventData = "eventData")
            releaseLogger.w("test")
            releaseLogger.e("test", eventData = "eventData")

            val logLines = getTrimmedLogLines(releaseLogger)

            assertLogFormat(logLines[0], "r: test | dump record")
            assertLogFormat(logLines[1], "i: test stringArgument | eventData")
            assertLogFormat(logLines[2], "w: test")
            assertLogFormat(logLines[3], "e: test | eventData")
        } finally {
            setAllLogsEnabled(true)
        }
    }

    @Test
    fun multiThreadLogging_dump_RespectsMAX_EVENTS() {
        val numberOfThreads = 50
        val eventsPerThread = MAX_EVENTS_DEBUG // each thread will emmit MAX_EVENTS
        val startLatch = CountDownLatch(1) // Main thread signals worker threads to start
        val doneLatch = CountDownLatch(numberOfThreads) // Worker threads signal
        val executorService = Executors.newFixedThreadPool(numberOfThreads)
        for (i in 0 until numberOfThreads) {
            executorService.submit {
                try {
                    startLatch.await() // Wait until the main thread gives the green light
                    repeat(eventsPerThread) {
                        logger.logEvent("Thread $i", eventData = "Data $i-$it")
                    }
                } finally {
                    doneLatch.countDown() // Signal that this thread has finished
                }
            }
        }

        // Give all threads the signal to start logging which unblocks all threads
        startLatch.countDown()
        // Wait for all threads to complete their logging tasks
        // Add a timeout to prevent the test from hanging indefinitely if something goes wrong
        assertThat(doneLatch.await(5, TimeUnit.SECONDS)).isTrue()

        // Check that there are no more than MAX_EVENTS events in the log history
        val logLinesCount = getTrimmedLogLines().size
        assertThat(logLinesCount).isEqualTo(MAX_EVENTS_DEBUG)
    }

    @Test
    fun flush_clearsEvents() {
        // Given we stored some logs
        logger.d("debug log")
        assertThat(logger.recentEvents).isNotEmpty()

        // When we flush the logs
        logger.flush()

        // Then the logs are cleared
        assertThat(logger.recentEvents).isEmpty()
    }

    @Test
    fun logger_doesNotKeepObjectRefs() {
        // When log primitives and objects
        logger.i("debug int=%d", 0)
        logger.i("debug long=%d", 0L)
        logger.i("debug float=%f", 0f)
        logger.i("debug double=%f", 0.0)
        logger.i("debug string=%s", "test me, please!")
        logger.i("debug object=%s", BubbleEventHistoryLogger())

        // Then primitive wrappers and toString() representation of object is saved
        assertThat(logger.recentEvents[0].titleParams!!.first())
            .isInstanceOf(Int::class.javaObjectType)
        assertThat(logger.recentEvents[1].titleParams!!.first())
            .isInstanceOf(Long::class.javaObjectType)
        assertThat(logger.recentEvents[2].titleParams!!.first())
            .isInstanceOf(Float::class.javaObjectType)
        assertThat(logger.recentEvents[3].titleParams!!.first())
            .isInstanceOf(Double::class.javaObjectType)
        assertThat(logger.recentEvents[4].titleParams!!.first()).isInstanceOf(String::class.java)
        assertThat(logger.recentEvents[5].titleParams!!.first()).isInstanceOf(String::class.java)
    }

    private fun assertLogFormat(logEntry: String, expectedLogWithoutDate: String) {
        assertThat(logEntry.matches(logPattern)).isTrue()
        val trimmedDateTime = logEntry.substring(DATE_FORMAT.length + 1)
        assertThat(trimmedDateTime).isEqualTo(expectedLogWithoutDate)
    }

    private fun getTrimmedLogLines(logger: BubbleEventHistoryLogger = this.logger): List<String> {
        return getDumpOutput(logger).split("\n").drop(1).dropLast(1).map { it.trim() }
    }

    private fun getDumpOutput(logger: BubbleEventHistoryLogger = this.logger): String {
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)

        logger.dump(pw = printWriter)
        printWriter.flush() // Ensure all content is written to StringWriter
        return stringWriter.toString()
    }

    companion object {

        private val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation

        @BeforeClass
        @JvmStatic
        fun setup() {
            setAllLogsEnabled(true)
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            setAllLogsEnabled(false)
        }

        private fun setAllLogsEnabled(enabled: Boolean) {
            val commandArg = if (enabled) "VERBOSE" else "\"\""
            uiAutomation.executeShellCommand("setprop log.tag.BubblesHistoryLogger $commandArg")
            // give system a time to propagate the change
            Thread.sleep(100)
        }
    }
}
