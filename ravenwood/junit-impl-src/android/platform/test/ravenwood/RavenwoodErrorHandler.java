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

import static android.platform.test.ravenwood.RavenwoodDriver.sRawStdErr;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Message;
import android.util.Log;

import com.android.ravenwood.OpenJdkWorkaround;
import com.android.ravenwood.common.RavenwoodInternalUtils;
import com.android.ravenwood.common.SneakyThrow;

import org.junit.AssumptionViolatedException;
import org.junit.runner.Description;

import java.io.PrintStream;
import java.util.Comparator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class RavenwoodErrorHandler {
    private static final String TAG = RavenwoodInternalUtils.TAG;

    /**
     * When enabled, detect uncaught exceptions from background threads.
     */
    static final boolean ENABLE_UNCAUGHT_EXCEPTION_DETECTION =
            !"0".equals(System.getenv("RAVENWOOD_ENABLE_UNCAUGHT_EXCEPTION_DETECTION"));

    /**
     * When enabled, uncaught Assertion exceptions from background threads are tolerated.
     */
    private static final boolean TOLERATE_UNHANDLED_ASSERTS =
            !"0".equals(System.getenv("RAVENWOOD_TOLERATE_UNHANDLED_ASSERTS"));

    /**
     * When enabled, all uncaught exceptions from background threads are tolerated.
     */
    private static final boolean TOLERATE_UNHANDLED_EXCEPTIONS =
            !"0".equals(System.getenv("RAVENWOOD_TOLERATE_UNHANDLED_EXCEPTIONS"));

    private static final boolean DIE_ON_UNCAUGHT_EXCEPTION = false;

    volatile static Description sCurrentDescription;

    // Several callbacks regarding test lifecycle

    static void init() {
        if (ENABLE_UNCAUGHT_EXCEPTION_DETECTION) {
            Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler());
        }

        // `pkill -USR1 -f tradefed-isolation.jar` will trigger a full thread dumps
        OpenJdkWorkaround.registerSignalHandler("USR1", () -> {
            sRawStdErr.println("-----SIGUSR1 HANDLER-----");
            RavenwoodErrorHandler.doBugreport(null, null, false);
        });
    }

    /**
     * Called when a test method is about to be started.
     */
    static void enterTestMethod(Description description) {
        sCurrentDescription = description;
        // If an uncaught exception has been detected, don't run subsequent test methods
        // in the same test.
        maybeThrowUnrecoverableUncaughtException();
        scheduleTimeout();
    }

    static void exitTestMethod(Description description) {
        cancelTimeout();
        maybeThrowPendingRecoverableUncaughtExceptionAndClear();
        maybeThrowUnrecoverableUncaughtException();
    }

    static void exitTestClass() {
        maybeThrowPendingRecoverableUncaughtExceptionAndClear();
    }

    // Setup timeout to detect slow tests

    /**
     * When enabled, attempt to dump all thread stacks just before we hit the
     * overall Tradefed timeout, to aid in debugging deadlocks.
     *
     * Note, this timeout will _not_ stop the test, as there isn't really a clean way to do it.
     * It'll merely print stacktraces.
     */
    private static final boolean ENABLE_TIMEOUT_STACKS =
            !"0".equals(System.getenv("RAVENWOOD_ENABLE_TIMEOUT_STACKS"));

    private static final int DEFAULT_TIMEOUT_SECONDS = 10;
    private static final int TIMEOUT_MILLIS = getTimeoutSeconds() * 1000;

    private static int getTimeoutSeconds() {
        var e = System.getenv("RAVENWOOD_TIMEOUT_SECONDS");
        if (e == null || e.isEmpty()) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
        return Integer.parseInt(e);
    }

    private static final ScheduledExecutorService sTimeoutExecutor =
            Executors.newScheduledThreadPool(1, (Runnable r) -> {
                Thread t = Executors.defaultThreadFactory().newThread(r);
                t.setName("Ravenwood:TimeoutMonitor");
                t.setDaemon(true);
                return t;
            });

    private static volatile ScheduledFuture<?> sPendingTimeout;

    /**
     * Prints the stack trace from all threads.
     */
    private static void onTestTimedOut() {
        sRawStdErr.println("********* SLOW TEST DETECTED ********");
        dumpStacks(null, null);
    }

    private static void scheduleTimeout() {
        if (!ENABLE_TIMEOUT_STACKS) return;
        cancelTimeout();
        sPendingTimeout = sTimeoutExecutor.schedule(
                RavenwoodErrorHandler::onTestTimedOut,
                TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    private static void cancelTimeout() {
        var pt = sPendingTimeout;
        if (pt != null) {
            pt.cancel(false);
            sPendingTimeout = null;
        }
    }

    private static class RecoverableUncaughtException extends Exception {
        private RecoverableUncaughtException(String message, Throwable cause) {
            super(message, cause);
        }

        @Override
        public String getMessage() {
            return super.getMessage() + " : " + getCause().getMessage();
        }

        static RecoverableUncaughtException create(Throwable th) {
            if (th instanceof RecoverableUncaughtException r) {
                return r;
            }
            var outer = new RecoverableUncaughtException(
                    "Uncaught exception detected on thread " + Thread.currentThread().getName()
                            + ": *** Continuing running the remaining tests ***", th);
            Log.e(TAG, outer.getMessage(), outer);
            return outer;
        }
    }

    /**
     * Return if an exception is benign and okay to continue running the remaining tests.
     */
    private static boolean isThrowableRecoverable(Throwable th) {
        if (th instanceof RecoverableUncaughtException) {
            return true;
        }
        if (TOLERATE_UNHANDLED_ASSERTS
                && (th instanceof AssertionError || th instanceof AssumptionViolatedException)) {
            return true;
        }
        return TOLERATE_UNHANDLED_EXCEPTIONS;
    }

    // Unhandled exception callbacks

    static class UncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {
        @Override
        public void uncaughtException(Thread thread, Throwable inner) {
            if (isThrowableRecoverable(inner)) {
                setPendingRecoverableUncaughtException(inner);
                return;
            }
            setPendingUnrecoverableUncaughtException(thread, inner);
            doBugreport(thread, inner, DIE_ON_UNCAUGHT_EXCEPTION);
        }
    }

    /**
     * Called by {@link android.os.Handler_ravenwood#onBeforeEnqueue}
     */
    public static void onBeforeEnqueue(@NonNull Message msg) {
        // Check for pending exception, and throw it if any.
        maybeThrowPendingRecoverableUncaughtExceptionNoClear();
        // Track the msg poster in case an exception is thrown later during msg dispatch.
        RavenwoodMessageTracker.getInstance().trackMessagePoster(msg);
    }

    /**
     * Called by {@link android.os.Looper_ravenwood#dispatchMessage}
     */
    public static void dispatchMessage(Message msg) {
        // If there's already an exception caught and pending, don't run any more messages.
        if (hasPendingRecoverableUncaughtException()) {
            return;
        }
        try {
            msg.getTarget().dispatchMessage(msg);
        } catch (Throwable th) {
            var desc = String.format("Detected %s on looper thread %s", th.getClass().getName(),
                    Thread.currentThread());
            sRawStdErr.println(desc);

            // If it's a tracked message, attach the stacktrace where we posted it as a cause.
            RavenwoodMessageTracker.getInstance().injectPosterAsCause(th, msg);
            if (isThrowableRecoverable(th)) {
                setPendingRecoverableUncaughtException(th);
                return;
            }
            throw th;
        }
    }

    // Unrecoverable exceptions

    /**
     * It's an exception detected from a BG thread (which is not recoverable). Once
     * we detect one, we make the current and all subsequent tests failed.
     */
    private static final AtomicReference<Throwable> sUnrecoverableUncaughtException =
            new AtomicReference<>();

    private static void setPendingUnrecoverableUncaughtException(Thread thread, Throwable th) {
        var msg = String.format(
                "Uncaught exception detected on thread %s, test=%s:"
                        + " %s; Failing all subsequent tests. "
                        + "Run with `RAVENWOOD_TOLERATE_UNHANDLED_EXCEPTIONS=1 atest ...` to "
                        + "force run subsequent tests",
                thread, sCurrentDescription, RavenwoodInternalUtils.getStackTraceString(th));

        var outer = new Exception(msg, th);
        Log.e(TAG, outer.getMessage(), outer);

        sUnrecoverableUncaughtException.compareAndSet(null, outer);
    }

    public static void maybeThrowUnrecoverableUncaughtException() {
        var e = sUnrecoverableUncaughtException.get();
        if (e != null) {
            SneakyThrow.sneakyThrow(e);
        }
    }

    // Recoverable exceptions

    /**
     * This is a "recoverable" uncaught exception from a BG thread. When we detect one,
     * we just make the current test failed, but continue running the subsequent tests normally.
     */
    private static final AtomicReference<Throwable> sPendingRecoverableUncaughtException =
            new AtomicReference<>();

    private static void setPendingRecoverableUncaughtException(Throwable th) {
        sPendingRecoverableUncaughtException.compareAndSet(null,
                RecoverableUncaughtException.create(th));
    }

    private static boolean hasPendingRecoverableUncaughtException() {
        return sPendingRecoverableUncaughtException.get() != null;
    }

    private static void maybeThrowPendingRecoverableUncaughtException(boolean clear) {
        final Throwable pending;
        if (clear) {
            pending = sPendingRecoverableUncaughtException.getAndSet(null);
        } else {
            pending = sPendingRecoverableUncaughtException.get();
        }
        if (pending != null) {
            SneakyThrow.sneakyThrow(pending);
        }
    }

    public static void maybeThrowPendingRecoverableUncaughtExceptionAndClear() {
        maybeThrowPendingRecoverableUncaughtException(true);
    }

    public static void maybeThrowPendingRecoverableUncaughtExceptionNoClear() {
        maybeThrowPendingRecoverableUncaughtException(false);
    }

    // Dump all thread stack traces

    private static final Object sDumpStackLock = new Object();

    /**
     * Prints the stack trace from all threads.
     */
    private static void dumpStacks(
            @Nullable Thread exceptionThread, @Nullable Throwable throwable) {
        synchronized (sDumpStackLock) {
            final PrintStream out = sRawStdErr;
            out.println("-----BEGIN ALL THREAD STACKS-----");

            var desc = sCurrentDescription;
            if (desc != null) {
                out.format("Running test: %s:%s#%s\n",
                        RavenwoodEnvironment.getInstance().getTestModuleName(),
                        desc.getClassName(), desc.getMethodName());
            }

            var stacks = Thread.getAllStackTraces();
            var threads = stacks.keySet().stream().sorted(
                    Comparator.comparingLong(Thread::getId)).collect(Collectors.toList());

            // Put the test and the main thread at the top.
            var env = RavenwoodEnvironment.getInstance();
            var testThread = env.getTestThread();
            var mainThread = env.getMainThread();
            if (mainThread != null) {
                threads.remove(mainThread);
                threads.add(0, mainThread);
            }
            if (testThread != null) {
                threads.remove(testThread);
                threads.add(0, testThread);
            }
            // Put the exception thread at the top.
            // Also inject the stacktrace from the exception.
            if (exceptionThread != null) {
                threads.remove(exceptionThread);
                threads.add(0, exceptionThread);
                stacks.put(exceptionThread, throwable.getStackTrace());
            }
            for (var th : threads) {
                out.println();

                out.print("Thread");
                if (th == exceptionThread) {
                    out.print(" [** EXCEPTION THREAD **]");
                }
                out.print(": " + th.getName() + " / " + th);
                out.println();

                for (StackTraceElement e :  stacks.get(th)) {
                    out.println("\tat " + e);
                }
            }
            out.println("-----END ALL THREAD STACKS-----");
        }
    }

    static void doBugreport(
            @Nullable Thread exceptionThread, @Nullable Throwable throwable, boolean killSelf) {
        // TODO: Print more information
        dumpStacks(exceptionThread, throwable);
        if (killSelf) {
            System.exit(13);
        }
    }
}
