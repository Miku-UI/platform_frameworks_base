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
package com.android.ravenwoodtest.coretest;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.platform.test.ravenwood.MessageWasPostedHereStackTrace;
import android.platform.test.ravenwood.RavenwoodUtils;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests related to the main thread.
 *
 * Some tests require $RAVENWOOD_RUN_SLOW_TESTS to set to "1".
 */
public class RavenwoodMainThreadTest {
    private static final boolean RUN_SLOW_TESTS =
            "1".equals(System.getenv("RAVENWOOD_RUN_SLOW_TESTS"));

    @Test
    public void testRunOnMainThread() {
        AtomicReference<Thread> thr = new AtomicReference<>();
        RavenwoodUtils.runOnMainThreadSync(() -> {
            thr.set(Thread.currentThread());
        });
        var th = thr.get();
        assertThat(th).isNotNull();
        assertThat(th).isNotEqualTo(Thread.currentThread());
    }

    /**
     * Sleep a long time on the main thread. This test would then "pass", but Ravenwood
     * should show the "SLOW TEST DETECTED" stack traces.
     *
     * This test requires `RAVENWOOD_RUN_SLOW_TESTS=1`.
     */
    @Test
    @androidx.test.filters.LargeTest
    public void testMainThreadSlow() {
        assumeTrue(RUN_SLOW_TESTS);

        RavenwoodUtils.runOnMainThreadSync(() -> {
            try {
                Thread.sleep(12_000);
            } catch (InterruptedException e) {
                fail("Interrupted");
            }
        });
    }

    /**
     * runOnMainThreadSync() should report back the inner exception, if any.
     *
     * Note this test does _not_ involves "recoverable exception" check in
     * RavenwoodDriver because the exception is caught in side the
     * Runnable that's executed on the main handler. This purely tests runOnMainThreadSync()'s
     * exception propagation.
     */
    @Test
    public void testRunOnMainThreadSync() {
        var th = assertThrows(AssertionError.class, () -> {
            RavenwoodUtils.runOnMainThreadSync(() -> {
                fail("Assertion failure on main thread!");
            });
        });

        // Also make sure the cause is set correctly.
        assertHasMessageWasPostedHereStackTraceAsCause(th, "testRunOnMainThreadSync");
    }

    /**
     * Ensure a given {@link Throwable} is an instance of MessageWasPostedHereStackTrace,
     * has the current thread, and its stacktrace contains a give method name.
     */
    public static void assertHasMessageWasPostedHereStackTraceAsCause(
            Throwable th, String expectedMethodNameInStacktrace) {
        MessageWasPostedHereStackTrace cause = null;

        var current = th;
        for (;;) {
            if (current instanceof MessageWasPostedHereStackTrace) {
                cause = (MessageWasPostedHereStackTrace) current;
                break;
            }
            var next = current.getCause();
            if (next == null || next == th) {
                break;
            }
            current = next;
        }
        if (cause == null) {
            fail("Exception did't have MessageWasPostedHereStackTrace as cause: " + th);
        }

        assertThat(cause.getPostedThread()).isEqualTo(Thread.currentThread());

        // Make sure the stacktrace contains a given method name.
        for (var ste : cause.getStackTrace()) {
            if (ste.getMethodName().equals(expectedMethodNameInStacktrace)) {
                return; // Found
            }
        }
        fail("Expected method '" + expectedMethodNameInStacktrace
                + "' in stack trace, but not found");
    }
}
