/*
 * Copyright (C) 2007 The Android Open Source Project
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

package android.util;

import static android.util.Log.logStackTrace;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.os.SystemProperties;
import android.test.PerformanceTestCase;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.common.truth.Expect;

import junit.framework.TestCase;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.PrintWriter;
import java.io.StringWriter;

@RunWith(AndroidJUnit4.class)
public final class LogTest {
    private static final String PROPERTY_TAG = "log.tag.LogTest";
    private static final String LOG_TAG = "LogTest";

    private static final String MSG = "in a bottle";

    @Rule
    public final Expect expect = Expect.create();

    @Test
    @Ignore // wtf() call would crash the test - WTF?!?!?
    public void testWtf() {
        Log.wtf(LOG_TAG, MSG);
        Log.wtf(LOG_TAG, MSG, new Throwable("Throwable"));
        Log.wtf(LOG_TAG, new Throwable("Throwable"));
    }

    @Test
    @Ignore
    public void testIsLoggable() {
        // First clear any SystemProperty setting for our test key.
        SystemProperties.set(PROPERTY_TAG, null);
        
        String value = SystemProperties.get(PROPERTY_TAG);
        Assert.assertTrue(value == null || value.length() == 0);
        
        // Check to make sure that all levels expect for INFO, WARN, ERROR, and ASSERT are loggable. 
        Assert.assertFalse(Log.isLoggable(LOG_TAG, Log.VERBOSE));
        Assert.assertFalse(Log.isLoggable(LOG_TAG, Log.DEBUG));
        Assert.assertTrue(Log.isLoggable(LOG_TAG, Log.INFO));
        Assert.assertTrue(Log.isLoggable(LOG_TAG, Log.WARN));
        Assert.assertTrue(Log.isLoggable(LOG_TAG, Log.ERROR));
        Assert.assertTrue(Log.isLoggable(LOG_TAG, Log.ASSERT));
        
        // Set the log level to be VERBOSE for this tag.
        SystemProperties.set(PROPERTY_TAG, "VERBOSE");
        
        // Test to make sure all log levels >= VERBOSE are loggable.
        Assert.assertTrue(Log.isLoggable(LOG_TAG, Log.VERBOSE));
        Assert.assertTrue(Log.isLoggable(LOG_TAG, Log.DEBUG));
        Assert.assertTrue(Log.isLoggable(LOG_TAG, Log.INFO));
        Assert.assertTrue(Log.isLoggable(LOG_TAG, Log.WARN));
        Assert.assertTrue(Log.isLoggable(LOG_TAG, Log.ERROR));
        Assert.assertTrue(Log.isLoggable(LOG_TAG, Log.ASSERT));
        
        // Set the log level to be DEBUG for this tag.
        SystemProperties.set(PROPERTY_TAG, "DEBUG");
        
        // Test to make sure all log levels >= DEBUG are loggable.
        Assert.assertFalse(Log.isLoggable(LOG_TAG, Log.VERBOSE));
        Assert.assertTrue(Log.isLoggable(LOG_TAG, Log.DEBUG));
        Assert.assertTrue(Log.isLoggable(LOG_TAG, Log.INFO));
        Assert.assertTrue(Log.isLoggable(LOG_TAG, Log.WARN));
        Assert.assertTrue(Log.isLoggable(LOG_TAG, Log.ERROR));
        Assert.assertTrue(Log.isLoggable(LOG_TAG, Log.ASSERT));
        
        // Set the log level to be INFO for this tag.
        SystemProperties.set(PROPERTY_TAG, "INFO");
        
        // Test to make sure all log levels >= INFO are loggable.
        Assert.assertFalse(Log.isLoggable(LOG_TAG, Log.VERBOSE));
        Assert.assertFalse(Log.isLoggable(LOG_TAG, Log.DEBUG));
        Assert.assertTrue(Log.isLoggable(LOG_TAG, Log.INFO));
        Assert.assertTrue(Log.isLoggable(LOG_TAG, Log.WARN));
        Assert.assertTrue(Log.isLoggable(LOG_TAG, Log.ERROR));
        Assert.assertTrue(Log.isLoggable(LOG_TAG, Log.ASSERT));
        
        // Set the log level to be WARN for this tag.
        SystemProperties.set(PROPERTY_TAG, "WARN");
        
        // Test to make sure all log levels >= WARN are loggable.
        Assert.assertFalse(Log.isLoggable(LOG_TAG, Log.VERBOSE));
        Assert.assertFalse(Log.isLoggable(LOG_TAG, Log.DEBUG));
        Assert.assertFalse(Log.isLoggable(LOG_TAG, Log.INFO));
        Assert.assertTrue(Log.isLoggable(LOG_TAG, Log.WARN));
        Assert.assertTrue(Log.isLoggable(LOG_TAG, Log.ERROR));
        Assert.assertTrue(Log.isLoggable(LOG_TAG, Log.ASSERT));
        
        // Set the log level to be ERROR for this tag.
        SystemProperties.set(PROPERTY_TAG, "ERROR");
        
        // Test to make sure all log levels >= ERROR are loggable.
        Assert.assertFalse(Log.isLoggable(LOG_TAG, Log.VERBOSE));
        Assert.assertFalse(Log.isLoggable(LOG_TAG, Log.DEBUG));
        Assert.assertFalse(Log.isLoggable(LOG_TAG, Log.INFO));
        Assert.assertFalse(Log.isLoggable(LOG_TAG, Log.WARN));
        Assert.assertTrue(Log.isLoggable(LOG_TAG, Log.ERROR));
        Assert.assertTrue(Log.isLoggable(LOG_TAG, Log.ASSERT));
        
        // Set the log level to be ASSERT for this tag.
        SystemProperties.set(PROPERTY_TAG, "ASSERT");
        
        // Test to make sure all log levels >= ASSERT are loggable.
        Assert.assertFalse(Log.isLoggable(LOG_TAG, Log.VERBOSE));
        Assert.assertFalse(Log.isLoggable(LOG_TAG, Log.DEBUG));
        Assert.assertFalse(Log.isLoggable(LOG_TAG, Log.INFO));
        Assert.assertFalse(Log.isLoggable(LOG_TAG, Log.WARN));
        Assert.assertFalse(Log.isLoggable(LOG_TAG, Log.ERROR));
        Assert.assertTrue(Log.isLoggable(LOG_TAG, Log.ASSERT));
        
        // Set the log level to be SUPPRESS for this tag.
        SystemProperties.set(PROPERTY_TAG, "SUPPRESS");
        
        // Test to make sure all log levels >= ASSERT are loggable.
        Assert.assertFalse(Log.isLoggable(LOG_TAG, Log.VERBOSE));
        Assert.assertFalse(Log.isLoggable(LOG_TAG, Log.DEBUG));
        Assert.assertFalse(Log.isLoggable(LOG_TAG, Log.INFO));
        Assert.assertFalse(Log.isLoggable(LOG_TAG, Log.WARN));
        Assert.assertFalse(Log.isLoggable(LOG_TAG, Log.ERROR));
        Assert.assertFalse(Log.isLoggable(LOG_TAG, Log.ASSERT));
    }

    @Test
    public void testLogStackTrace_invalidArgs() throws Exception {
        try (StringWriter sw = new StringWriter(); PrintWriter pw = new PrintWriter(sw)) {
            assertThrows(NullPointerException.class, () -> logStackTrace(pw, Log.LOG_ID_MAIN,
                    Log.VERBOSE, /* msg= */ null, /* skippedInitialLines= */ 0));
            assertThrows(IllegalArgumentException.class, () -> logStackTrace(pw, Log.LOG_ID_MAIN,
                    Log.VERBOSE, MSG, /* skippedInitialLines= */ -1));
        }
    }

    @Test
    public void testLogStackTrace_noSkippedLine() throws Exception {
        try (StringWriter sw = new StringWriter(); PrintWriter pw = new PrintWriter(sw)) {
            logStackTrace(pw, Log.LOG_ID_MAIN, Log.VERBOSE, MSG, /* skippedInitialLines= */ 0);

            String[] loggedLines = getLoggedLines(sw);
            assertWithMessage("number of logged lines").that(loggedLines.length).isAtLeast(2);
            expect.withMessage("first line").that(loggedLines[0]).isEqualTo(MSG);
            expect.withMessage("second line").that(loggedLines[1])
                    .contains("testLogStackTrace_noSkippedLine");
        }
    }

    @Test
    public void testLogStackTrace_oneSkippedLine() throws Exception {
        try (StringWriter sw = new StringWriter(); PrintWriter pw = new PrintWriter(sw)) {
            oneLevelLog(pw, MSG, /* skippedInitialLines= */ 1);

            String[] loggedLines = getLoggedLines(sw);
            assertWithMessage("number of logged lines").that(loggedLines.length).isAtLeast(2);
            expect.withMessage("first line").that(loggedLines[0]).isEqualTo(MSG);
            expect.withMessage("second line").that(loggedLines[1])
                    .contains("testLogStackTrace_oneSkippedLine");
        }
    }

    @Test
    public void testLogStackTrace_twoSkippedLines() throws Exception {
        try (StringWriter sw = new StringWriter(); PrintWriter pw = new PrintWriter(sw)) {
            twoLevelsLog(pw, MSG, /* skippedInitialLines= */ 2);

            String[] loggedLines = getLoggedLines(sw);
            assertWithMessage("number of logged lines").that(loggedLines.length).isAtLeast(2);
            expect.withMessage("first line").that(loggedLines[0]).isEqualTo(MSG);
            expect.withMessage("second line").that(loggedLines[1])
                    .contains("testLogStackTrace_twoSkippedLines");
        }
    }

    private String[] getLoggedLines(StringWriter writer) {
        String loggedMessage = writer.toString();
        Log.v(LOG_TAG, "Logged message: \n" + loggedMessage);
        assertWithMessage("logged message").that(loggedMessage).isNotEmpty();

        String[] loggedLines = loggedMessage.split("\n");
        assertWithMessage("logged message as array").that(loggedLines).isNotNull();
        return loggedLines;
    }

    /** This method calls a method that calls {@code logStackTrace(msg, skippedInitialLines)}. */
    private static void twoLevelsLog(PrintWriter writer, String msg, int skippedInitialLines) {
        oneLevelLog(writer, msg, skippedInitialLines);
    }

    /** This method calls {@code logStackTrace(msg, skippedLines)} directly.  */
    private static void oneLevelLog(PrintWriter writer, String msg, int skippedInitialLines) {
        logStackTrace(writer, Log.LOG_ID_MAIN, Log.VERBOSE, msg, skippedInitialLines);
    }

    public static class PerformanceTest extends TestCase implements PerformanceTestCase {
        private static final int ITERATIONS = 1000;

        @Override
        public void setUp() {
            SystemProperties.set(LOG_TAG, "VERBOSE");
        }
        
        public boolean isPerformanceOnly() {
            return true;
        }
        
        public int startPerformance(PerformanceTestCase.Intermediates intermediates) {
            intermediates.setInternalIterations(ITERATIONS * 10);
            return 0;
        }

        public void testIsLoggable() {
            boolean canLog = false;
            for (int i = ITERATIONS - 1; i >= 0; i--) {
                canLog = Log.isLoggable(LOG_TAG, Log.VERBOSE);
                canLog = Log.isLoggable(LOG_TAG, Log.VERBOSE);
                canLog = Log.isLoggable(LOG_TAG, Log.VERBOSE);
                canLog = Log.isLoggable(LOG_TAG, Log.VERBOSE);
                canLog = Log.isLoggable(LOG_TAG, Log.VERBOSE);
                canLog = Log.isLoggable(LOG_TAG, Log.VERBOSE);
                canLog = Log.isLoggable(LOG_TAG, Log.VERBOSE);
                canLog = Log.isLoggable(LOG_TAG, Log.VERBOSE);
                canLog = Log.isLoggable(LOG_TAG, Log.VERBOSE);
                canLog = Log.isLoggable(LOG_TAG, Log.VERBOSE);
            }
        }
    }
}
