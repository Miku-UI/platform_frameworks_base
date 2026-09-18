/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Handler;
import android.os.Looper;
import android.os.MessageQueue;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.ravenwood.common.SneakyThrow;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Utilities for writing (bivalent) ravenwood tests.
 */
public class RavenwoodUtils {
    private RavenwoodUtils() {
    }

    private static final int DEFAULT_TIMEOUT_SECONDS = 10;

    @GuardedBy("sHandlers")
    private static final Map<Looper, Handler> sHandlers = new HashMap<>();

    /**
     * Date format used for log filename.
     */
    public static final DateTimeFormatter LOG_FILE_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /**
     * Return a handler for any looper.
     */
    @NonNull
    private static Handler getHandler(@NonNull Looper looper) {
        synchronized (sHandlers) {
            return sHandlers.computeIfAbsent(looper, (l) -> new Handler(l));
        }
    }

    /**
     * Returns the main thread handler.
     */
    public static Handler getMainHandler() {
        return getHandler(Looper.getMainLooper());
    }

    /**
     * Run a Callable on Handler and wait for it to complete.
     */
    @Nullable
    public static <T> T runOnHandlerSync(@NonNull Handler h, @NonNull Callable<T> c) {
        return runOnHandlerSync(h, c, DEFAULT_TIMEOUT_SECONDS * 1000);
    }

    @Nullable
    public static <T> T runOnHandlerSync(@NonNull Handler h,
            @NonNull Callable<T> c,
            int timeoutMillis) {
        var result = new AtomicReference<T>();
        var thrown = new AtomicReference<Throwable>();
        var latch = new CountDownLatch(1);
        var tooLate = new AtomicBoolean(false);

        var postedHere = new MessageWasPostedHereStackTrace();
        h.post(() -> {
            try {
                if (tooLate.get()) {
                    return;
                }
                result.set(c.call());
            } catch (Throwable th) {
                thrown.set(th);
            } finally {
                latch.countDown();
            }
        });
        try {
            var success = latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
            if (!success) {
                // TODO: Take a bugreport?
                throw new RuntimeException("Timed out while waiting for callback to finish");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while waiting on the Runnable", e);
        }
        tooLate.set(true);
        var th = thrown.get();
        if (th != null) {
            // Inject the current stacktrace as a cause for easier debugging.
            postedHere.injectAsCause(th);
            SneakyThrow.sneakyThrow(th);
        }
        return result.get();
    }


    /**
     * Run a Runnable on Handler and wait for it to complete.
     */
    public static void runOnHandlerSync(@NonNull Handler h, @NonNull Runnable r) {
        runOnHandlerSync(h, () -> {
            r.run();
            return null;
        });
    }

    /**
     * Run a Callable on main thread and wait for it to complete.
     */
    @Nullable
    public static <T> T runOnMainThreadSync(@NonNull Callable<T> c) {
        return runOnHandlerSync(getMainHandler(), c);
    }

    /**
     * Run a Runnable on main thread and wait for it to complete.
     */
    public static void runOnMainThreadSync(@NonNull ThrowingRunnable r) {
        runOnHandlerSync(getMainHandler(), () -> {
            r.run();
            return null;
        });
    }

    /**
     * Wait for a looper to be idle.
     *
     * When running on Ravenwood, this will also throw the pending exception, if any.
     */
    public static void waitForLooperDone(Looper looper) {
        if (Looper.myLooper() == looper) {
            throw new IllegalStateException("Cannot wait for the current looper");
        }
        var idler = new Idler();
        looper.getQueue().addIdleHandler(idler);
        // Wake up the queue, if sleeping.
        getHandler(looper).post(() -> {});

        idler.waitForIdle();
    }

    /**
     * Wait for a looper to be idle.
     *
     * When running on Ravenwood, this will also throw the pending exception, if any.
     */
    public static void waitForMainLooperDone() {
        waitForLooperDone(Looper.getMainLooper());
    }

    private static class Idler implements MessageQueue.IdleHandler {
        private final CountDownLatch mLatch = new CountDownLatch(1);

        @Override
        public boolean queueIdle() {
            mLatch.countDown();
            return false; // One-shot idle handler returns true.
        }

        public boolean waitForIdle() {
            try {
                return mLatch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Log.w("Idler", "Interrupted");
                return false;
            }
        }
    }

    /** Used by {@link #runOnMainThreadSync(ThrowingRunnable)}}  */
    public interface ThrowingRunnable {
        /** run the code. */
        void run() throws Exception;
    }
}
