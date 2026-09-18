/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.server.testutils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.matchesPattern;

import android.os.Message;
import android.os.TestLooperManager;
import android.util.Log;

import androidx.annotation.Nullable;

import junit.framework.Assert;

import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class TestUtils {
    private TestUtils() {
    }

    public interface ExceptionRunnable {
        void run() throws Exception;
    }

    public static void assertExpectException(Class<? extends Throwable> expectedExceptionType,
            String expectedExceptionMessageRegex, ExceptionRunnable r) {
        try {
            r.run();
        } catch (Throwable e) {
            Assert.assertTrue(
                    "Expected exception type was " + expectedExceptionType.getName()
                    + " but caught " + e.getClass().getName(),
                    expectedExceptionType.isAssignableFrom(e.getClass()));
            if (expectedExceptionMessageRegex != null) {
                assertThat(e.getMessage(),
                        matchesPattern(".*" + expectedExceptionMessageRegex + ".*"));
            }
            return; // Pass.
        }
        Assert.fail("Expected exception type " + expectedExceptionType.getName()
                + " was not thrown");
    }

    /**
     * EasyMock-style "strict" mock that throws immediately on any interaction that was not
     * explicitly allowed.
     *
     * You can allow certain method calls on a allowlist basis by stubbing them e.g. with
     * {@link Mockito#doAnswer}, {@link Mockito#doNothing}, etc.
     */
    public static <T> T strictMock(Class<T> c) {
        return Mockito.mock(c, (Answer) invocation -> {
            throw new AssertionError("Unexpected invocation: " + invocation);
        });
    }

    /**
     * Dispatch all the ready looper messages. The loopers might depend on each other and send
     * messages to each other, so this method loops through all of them until there are no ready
     * messages left in any of them.
     * @param tlms The test looper managers
     */
    public static void flushLoopers(TestLooperManager... tlms) {
        boolean noMoreMessages;
        do {
            noMoreMessages = true;
            for (TestLooperManager tlm : tlms) {
                Message m = tlm.poll();
                if (m != null) {
                    tlm.execute(m);
                    tlm.recycle(m);
                    noMoreMessages = false;
                }
            }
        } while(!noMoreMessages);
    }

    public enum ExecutionOrder {
        /** Execute cleanups in the order they were added (First-In, First-Out). */
        FIFO,
        /** Execute cleanups in the reverse order they were added (Last-In,
         * First-Out - default for resource cleanup). */
        LIFO
    }

    /**
     * Helper class allowing to execute cleanup steps in LIFO or FIFO order while
     * suppressing exceptions, and then throw all suppressed exceptions at the end of close().
     */
    public static class CleanupExecutor implements AutoCloseable {
        private final AtomicReference<Throwable> mPrimaryExceptionRef = new AtomicReference<>(null);
        private final List<Runnable> mCleanups = new ArrayList<>();
        private final String mTag;
        private final ExecutionOrder mExecutionOrder;

        public CleanupExecutor(String tag, ExecutionOrder executionOrder) {
            mTag = tag;
            mExecutionOrder = executionOrder;
        }

        public CleanupExecutor(String tag) {
            this(tag, ExecutionOrder.LIFO);
        }

        /** Add the cleanup to the list of cleanups to be executed in reverse order */
        public void addCleanup(@Nullable Runnable cleanup) {
            mCleanups.add(cleanup);
        }

        /**
         * @return True if there is a suppressed exception.
         */
        public boolean hasException() {
            return mPrimaryExceptionRef.get() != null;
        }

        /**
         * Execute all the cleanups based on the configured order, and throw all
         * the suppressed exceptions at the end of close().
         */
        @Override
        public void close() throws Exception {
            int start = mExecutionOrder == ExecutionOrder.LIFO ? mCleanups.size() - 1 : 0;
            int end = mExecutionOrder == ExecutionOrder.LIFO ? -1 : mCleanups.size();
            int step = mExecutionOrder == ExecutionOrder.LIFO ? -1 : 1;

            for (int i = start; i != end; i += step) {
                executeQuietly(mCleanups.get(i), i);
            }

            mCleanups.clear();
            throwSuppressedExceptions();
        }

        /** Helper to execute the runnable without throwing, and then set the primary exception */
        private void executeQuietly(@Nullable Runnable runnable, int index) {
            if (runnable == null) {
                return;
            }
            try {
                runnable.run();
            } catch (Throwable t) {
                Log.e(mTag, "Failed to execute index " + index + " with " + runnable, t);
                Throwable primary = mPrimaryExceptionRef.get();
                if (primary == null) {
                    mPrimaryExceptionRef.set(t); // Set the first exception
                } else {
                    primary.addSuppressed(t); // Suppress subsequent exceptions
                }
            }
        }

        private void throwSuppressedExceptions() throws Exception {
            var primary = mPrimaryExceptionRef.getAndSet(null);
            if (primary == null) {
                return;
            }
            if (primary instanceof Exception) {
                // Safe: It's an Exception, and the method throws Exception.
                throw (Exception) primary;
            }
            if (primary instanceof Error) {
                // Safe: It's an Error (unchecked), preserving the original type.
                throw (Error) primary;
            }

            // In the highly unlikely case of a custom checked Throwable that's
            // neither Exception nor Error, wrap it in a RuntimeException.
            // (This is usually not necessary but is technically the safest catch-all.)
            throw new RuntimeException("Unexpected Throwable during close", primary);
        }
    }
}
