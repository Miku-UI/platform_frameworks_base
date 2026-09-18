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

package lockedregioncodeinjection;

/**
 * A target class for testing tracing without pre/post methods.
 */
public class TestOnlyTraceTarget {
    public static int traceBeforeAcquireCount = 0;
    public static int traceAfterAcquireCount = 0;
    public static int traceBeforeReleaseCount = 0;
    public static int traceAfterReleaseCount = 0;

    /**
     * Increments the before-acquire trace count.
     */
    public static void traceBeforeAcquire() {
        traceBeforeAcquireCount++;
    }

    /**
     * Increments the after-acquire trace count.
     */
    public static void traceAfterAcquire() {
        traceAfterAcquireCount++;
    }

    /**
     * Increments the before-release trace count.
     */
    public static void traceBeforeRelease() {
        traceBeforeReleaseCount++;
    }

    /**
     * Increments the after-release trace count.
     */
    public static void traceAfterRelease() {
        traceAfterReleaseCount++;
    }

    /**
     * Resets all trace counts to zero.
     */
    public static void resetCount() {
        traceBeforeAcquireCount = 0;
        traceAfterAcquireCount = 0;
        traceBeforeReleaseCount = 0;
        traceAfterReleaseCount = 0;
    }
}
