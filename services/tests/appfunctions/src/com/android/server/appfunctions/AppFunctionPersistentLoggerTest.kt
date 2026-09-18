/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.server.appfunctions

import android.os.Handler
import com.android.internal.os.BackgroundThread
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@RunWith(JUnit4::class)
class AppFunctionPersistentLoggerTest {

    private lateinit var tempDir: File
    private lateinit var logger: AppFunctionPersistentLogger
    private val mockHandler: Handler = mock(Handler::class.java)

    companion object {
        private const val BASE_LOG_FILE_NAME = "test_log.log"
    }

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("appfunction-test").toFile()
        logger = AppFunctionPersistentLogger(tempDir, BASE_LOG_FILE_NAME, mockHandler)
    }

    @After
    fun tearDown() {
        logger.close() // Ensure cleanup task is stopped
        tempDir.deleteRecursively()
    }

    @Test
    fun log_writesMessageToFile() {
        val message = "This is a test log message."
        logger.log(message)

        val logFile = File(tempDir, BASE_LOG_FILE_NAME)
        assertThat(logFile.exists()).isTrue()

        val content = logFile.readText()
        assertThat(content).contains(message)
    }

    @Test
    fun dump_outputsLogContents() {
        logger.log("line 1")
        logger.log("line 2")

        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        logger.dump(printWriter)

        val output = stringWriter.toString()
        assertThat(output).contains("line 1")
        assertThat(output).contains("line 2")
    }

    @Test
    fun pruneOldLogs_deletesExpiredFiles() {
        val oldFile = File(tempDir, "$BASE_LOG_FILE_NAME.1")
        oldFile.createNewFile()
        // Set last modified to be older than the TTL
        oldFile.setLastModified(System.currentTimeMillis() - (AppFunctionPersistentLogger.LOG_TTL_MILLIS + 1000))

        assertThat(oldFile.exists()).isTrue()

        // Capture the runnable and run it to trigger pruneOldLogs
        val runnableCaptor = ArgumentCaptor.forClass(Runnable::class.java)
        verify(mockHandler).post(runnableCaptor.capture())
        runnableCaptor.value.run() // This will call pruneOldLogs

        assertThat(oldFile.exists()).isFalse()
    }

    @Test
    fun close_removesCleanupCallbacks() {
        val runnableCaptor = ArgumentCaptor.forClass(Runnable::class.java)
        verify(mockHandler).post(runnableCaptor.capture())

        logger.close()

        verify(mockHandler).removeCallbacks(runnableCaptor.value)
    }

    @Test
    fun log_rotatesWhenSizeExceedsLimit() {
        val largeMessage = "a".repeat(1024) // 1KB message

        // Write just over the limit to trigger rotation
        for (i in 0..AppFunctionPersistentLogger.MAX_FILE_SIZE_BYTES / largeMessage.length) {
            logger.log(largeMessage)
        }

        val logFile = File(tempDir, BASE_LOG_FILE_NAME)
        val rotatedFile = File(tempDir, "$BASE_LOG_FILE_NAME.1")

        assertThat(rotatedFile.exists()).isTrue()
        assertThat(logFile.exists()).isTrue() // New log file should be created
    }

    @Test
    fun log_maintainsMaxFileCount() {
        val largeMessage = "a".repeat(1024) // 1KB message

        // Fill up and rotate more than MAX_FILE_COUNT times
        for (i in 0 until AppFunctionPersistentLogger.MAX_FILE_COUNT + 2) {
            // Fill up one file to trigger rotation
            for (j in 0..(AppFunctionPersistentLogger.MAX_FILE_SIZE_BYTES / largeMessage.length)) {
                logger.log(largeMessage)
            }
        }

        val logFiles = tempDir.listFiles { _, name -> name.startsWith(BASE_LOG_FILE_NAME) }
        assertThat(logFiles).isNotNull()

        // After many rotations, we should have the current log file and `maxFileCount` archives
        val fileNames = logFiles!!.map { it.name }.toSet()
        val expectedFiles = mutableSetOf(BASE_LOG_FILE_NAME)
        for (i in 1..AppFunctionPersistentLogger.MAX_FILE_COUNT) {
            expectedFiles.add("$BASE_LOG_FILE_NAME.$i")
        }

        assertThat(fileNames).isEqualTo(expectedFiles)
    }
}
