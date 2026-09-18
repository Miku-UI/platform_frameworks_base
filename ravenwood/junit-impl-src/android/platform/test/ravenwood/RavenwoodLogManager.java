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
package android.platform.test.ravenwood;

import android.util.Log;

import com.android.internal.os.LoggingPrintStream;

import java.io.PrintStream;

/**
 * Manages log output.
 *
 * Only provides low level functionalities, so it's a static class
 */
public class RavenwoodLogManager {
    private RavenwoodLogManager() {
    }

    private static final PrintStream sRawOut = RavenwoodDriver.sRawStdOut;

    public static class RavenwoodLoggingPrintStream extends LoggingPrintStream {
        private final String mTag;
        private final int mLogLevel;

        RavenwoodLoggingPrintStream(String tag, int logLevel) {
            mTag = tag;
            this.mLogLevel = logLevel;
        }

        @Override
        protected void log(String line) {
            Log.println(mLogLevel, mTag, line);
        }

        /**
         * Flushes the current buffer, but don't flush the last line if it doesn't finish
         * with a new line.
         */
        @Override
        public synchronized void flush() {
            flush(false);
        }

        /**
         * Flush the buffer, even if the buffer doesn't end with a newline.
         */
        public void flushCompletely() {
            flush(true);
        }
    }

    /**
     * Create a {@link PrintStream} that prints as logcat.
     */
    public static RavenwoodLoggingPrintStream getLogcatOut(String tag, int logLevel) {
        return new RavenwoodLoggingPrintStream(tag, logLevel);
    }

    /**
     * Print a raw string on stdout.
     */
    public static void printRawString(String message) {
        var out = sRawOut;
        out.print(message);
        out.flush();
    }
}
