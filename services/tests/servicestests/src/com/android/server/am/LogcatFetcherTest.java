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

package com.android.server.am;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

/**
 * Test class for {@link LogcatFetcher}.
 *
 * Build/Install/Run:
 *  atest FrameworksServicesTests:LogcatFetcherTest
 */
@SmallTest
public class LogcatFetcherTest {

    @Test
    public void testTrimAndAppendLogs_basic() {
        StringBuilder sb = new StringBuilder();
        List<String> logs = List.of(
                "03-12 14:55:18.080 +0000 22912 22934 E tag_a: Some log line 1",
                "03-12 14:56:18.080 +0000 34143 22934 I tag_b: Some log line 2"
        );

        int appendedSize = LogcatFetcher.trimAndAppendLogs(sb, logs, 1000);

        assertFalse(sb.toString().isEmpty());
        assertTrue(sb.toString().contains("Some log line 1"));
        assertTrue(sb.toString().contains("Some log line 2"));
        assertEquals(logs.size() + 1 /* header */, sb.toString().split("\n").length);
        assertTrue(appendedSize > 0);
    }

    @Test
    public void testTrimAndAppendLogs_emptyLogs() {
        StringBuilder sb = new StringBuilder();
        List<String> logs = Collections.emptyList();

        int size = LogcatFetcher.trimAndAppendLogs(sb, logs, 1000);

        assertEquals(0, size);
        assertTrue(sb.toString().isEmpty());
    }

    @Test
    public void testTrimAndAppendLogs_insufficientBuffer() {
        StringBuilder sb = new StringBuilder();
        List<String> logs = List.of("--------- beginning of divider", "Short log", "Another log");

        int size = LogcatFetcher.trimAndAppendLogs(sb, logs, 5);

        assertEquals(0, size);
        assertTrue(sb.toString().isEmpty());
    }

    @Test
    public void testTrimAndAppendLogs_onlyHeaderFits() {
        StringBuilder sb = new StringBuilder();
        List<String> logs = List.of("--------- divider", "A very very long log message");

        int size = LogcatFetcher.trimAndAppendLogs(sb, logs, 20);

        assertEquals(0, size);
        assertTrue(sb.toString().isEmpty());
    }

    @Test
    public void testTrimAndAppendLogs_exactBufferMatch() {
        StringBuilder sb = new StringBuilder();
        List<String> logs = List.of("--------- beginning of header", "log1", "log2", "log3");
        int exactBufferSize = "--------- beginning of header\nlog2\nlog3\n".length();

        int size = LogcatFetcher.trimAndAppendLogs(sb, logs, exactBufferSize);

        assertTrue(sb.toString().contains("log2"));
        assertTrue(sb.toString().contains("log3"));
        assertFalse(sb.toString().contains("log1"));
    }

    @Test
    public void testExtractLogTimestamp() {
        String timestamp = LogcatFetcher.extractLogTimestamp("03-12 12:34:56.789 a log message");
        assertEquals("03-12 12:34:56.789", timestamp);
    }

    @Test
    public void testExtractLogTimestamp_variedFormats() {
        String logLine = "12-31 23:59:59.999 Random log message";
        String timestamp = LogcatFetcher.extractLogTimestamp(logLine);
        assertEquals("12-31 23:59:59.999", timestamp);

        logLine = "Invalid line without timestamp";
        timestamp = LogcatFetcher.extractLogTimestamp(logLine);
        assertEquals("12-31 23:59:59.999".length(), timestamp.length());
    }

    @Test
    public void testTrimAndAppendLogs_headerOnlyFitsExactly() {
        StringBuilder sb = new StringBuilder();
        List<String> logs = List.of("--------- header", "A log entry");

        int size = LogcatFetcher.trimAndAppendLogs(sb, logs, "--------- header\n".length());

        assertEquals(0, size);
        assertTrue(sb.isEmpty());
    }

}
