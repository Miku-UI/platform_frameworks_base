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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Handler;
import android.os.Handler_ravenwood;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.ravenwood.OpenJdkWorkaround;
import com.android.ravenwood.common.RavenwoodInternalUtils;
import com.android.ravenwood.common.SneakyThrow;
import com.android.ravenwood.common.StackTrace;

import org.junit.AssumptionViolatedException;
import org.junit.runner.Description;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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

    @GuardedBy("sWarnings")
    private static final ArrayList<StackTrace> sWarnings = new ArrayList<>();

    // Several callbacks regarding test lifecycle

    static void init() {
        setDefaultUncaughtExceptionHandler();

        // `pkill -USR1 -f tradefed-isolation.jar` will trigger a full thread dumps
        OpenJdkWorkaround.registerSignalHandler("USR1", () -> {
            RavenwoodBugreportManager.doBugreport("-----SIGUSR1 HANDLER-----", null, null, false);
        });
    }

    public static void setDefaultUncaughtExceptionHandler() {
        if (ENABLE_UNCAUGHT_EXCEPTION_DETECTION) {
            Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler());
        }
    }

    static void enterTestRunner() {
        // Reset the main thread to clear pending messages in the queue
        clearPendingRecoverableUncaughtException();
        // Wait until the main thread is idle to be 100% sure the queue is empty
        try {
            RavenwoodUtils.waitForMainLooperDone();
        } catch (Throwable ignored) {}
        // It's possible that an exception was thrown during the final msg on the main thread
        // the moment the queue is reset. Ignore it as it's not relevant to the current test.
        clearPendingRecoverableUncaughtException();
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
        RavenwoodMessageTracker.getInstance().dumpPendingMessages(
                RavenwoodLogManager.getLogcatOut(TAG, Log.VERBOSE),
                "Test finished.");
        maybeThrowPendingRecoverableUncaughtExceptionAndClear();
        maybeThrowUnrecoverableUncaughtException();
    }

    // Setup timeout to detect slow tests

    private static final ScheduledExecutorService sTimeoutExecutor =
            Executors.newScheduledThreadPool(1, (Runnable r) -> {
                Thread t = Executors.defaultThreadFactory().newThread(r);
                t.setName("Ravenwood:TimeoutMonitor");
                t.setDaemon(true);
                return t;
            });

    private static volatile ScheduledFuture<?> sPendingSlowTimeout;
    private static volatile ScheduledFuture<?> sPendingKillTimeout;

    /**
     * Prints the stack trace from all threads.
     */
    private static void onTestSlowTimedOut() {
        RavenwoodBugreportManager.doBugreport(
                "********* SLOW TEST DETECTED ********", null, null, false);
    }

    /**
     */
    private static void onDieTimedOut() {
        RavenwoodBugreportManager.doBugreport(
                "********* TEST TIMED OUT, KILLING SELF ********", null, null, true);
    }

    private static void scheduleTimeout() {
        cancelTimeout();
        var env = RavenwoodEnvironment.getInstance();
        if (env.getSlowTestTimeoutSeconds() > 0) {
            sPendingSlowTimeout = sTimeoutExecutor.schedule(
                    RavenwoodErrorHandler::onTestSlowTimedOut,
                    env.getSlowTestTimeoutSeconds(),
                    TimeUnit.SECONDS);
        }
        if (env.getDieTimeoutSeconds() > 0) {
            sPendingKillTimeout = sTimeoutExecutor.schedule(
                    RavenwoodErrorHandler::onDieTimedOut,
                    env.getDieTimeoutSeconds(),
                    TimeUnit.SECONDS);
        }
    }

    private static void cancelTimeout() {
        safeCancel(sPendingKillTimeout);
        safeCancel(sPendingSlowTimeout);

        sPendingKillTimeout = null;
        sPendingSlowTimeout = null;
    }

    private static void safeCancel(Future<?> f) {
        if (f != null) {
            f.cancel(false);
        }
    }

    private static class RecoverableUncaughtException extends Exception {
        private RecoverableUncaughtException(String message, Throwable cause) {
            super(message, cause);
        }

        static RecoverableUncaughtException create(Throwable th) {
            if (th instanceof RecoverableUncaughtException r) {
                return r;
            }
            return new RecoverableUncaughtException(
                    "Uncaught exception detected on thread " + Thread.currentThread().getName()
                            + ". *** Continue running remaining tests ***", th);
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
            var msg = "Uncaught exception detected on thread " + Thread.currentThread();
            Log.w(TAG, msg, inner);
            var isRecoverable = isThrowableRecoverable(inner);
            if (isRecoverable) {
                setPendingRecoverableUncaughtException(inner);
            } else {
                setPendingUnrecoverableUncaughtException(thread, inner);
            }
            RavenwoodBugreportManager.doBugreport(
                    msg, thread, inner, !isRecoverable && DIE_ON_UNCAUGHT_EXCEPTION);
        }
    }

    /**
     * Called by {@link android.os.Handler_ravenwood#onBeforeEnqueue}
     */
    public static void onBeforeEnqueue(@NonNull Message msg) {
        // Check for pending exception, and throw it if any.
        // We don't want to enqueue any more messages if a pending exception exists.
        try {
            maybeThrowPendingRecoverableUncaughtExceptionNoClear();
        } catch (Throwable th) {
            onWarningDetected("onBeforeEnqueue: Exception pending. Discarding message "
                    + RavenwoodMessageTracker.messageToString(msg));
            throw th;
        }

        // Track the msg poster in case an exception is thrown later during msg dispatch.
        RavenwoodMessageTracker.getInstance().trackMessage(msg);
    }

    /**
     * Called by {@link android.os.Handler_ravenwood#dispatchMessage}
     */
    public static void dispatchMessage(Handler handler, Message msg) {
        // If there's already an exception caught and pending, don't run any more messages.
        if (hasPendingRecoverableUncaughtException()) {
            onWarningDetected("dispatchMessage: Exception pending. Discarding message "
                    + RavenwoodMessageTracker.messageToString(msg));
            return;
        }
        RavenwoodMessageTracker.getInstance().onDispatchStarted(msg);
        try {
            try {
                handler.dispatchMessageImpl(msg);
            } finally {
                RavenwoodMessageTracker.getInstance().onDispatchFinished(msg);
            }
        } catch (Throwable th) {
            var desc = String.format("Detected %s on looper thread %s", th.getClass().getName(),
                    Thread.currentThread());
            Log.w(TAG, desc);

            if (RavenwoodEnablementChecker.getInstance().wouldRunDisabledTests()) {
                // Once a MessageQueue has an unhandled exception, the entire MessageQueue may be
                // left in a broken state. Try our best to clear the MessageQueue. This is very
                // hacky, so we limit this operation to only when RAVENWOOD_RUN_DISABLED_TESTS=1.
                Handler_ravenwood.clearMessageQueue(Looper.myQueue());
            }

            // It is possible that this message is dispatched through other mechanisms
            // (e.g. TestLooperManager). We only really care about messages that are enqueued
            // through a handler, which will always be tracked through onBeforeEnqueue above.
            var poster = RavenwoodMessageTracker.getInstance().getPoster(msg);
            if (poster != null) {
                // Attach the stacktrace where we posted it as a cause.
                poster.injectAsCause(th);

                // If the exception is recoverable, don't rethrow
                if (isThrowableRecoverable(th)) {
                    setPendingRecoverableUncaughtException(th);
                    return;
                }
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
                "Uncaught exception detected on thread %s, test=%s; Failing all subsequent tests.\n"
                        + "Run with `RAVENWOOD_TOLERATE_UNHANDLED_EXCEPTIONS=1 atest ...` to "
                        + "force run subsequent tests.", thread, sCurrentDescription);

        var outer = new Exception(msg, th);
        Log.e(TAG, "", outer);
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

    private static void clearPendingRecoverableUncaughtException() {
        var pending = sPendingRecoverableUncaughtException.getAndSet(null);
        if (pending != null) {
            Log.e(TAG, "Pending recoverable exception suppressed", pending);
        }
    }

    private static void maybeThrowPendingRecoverableUncaughtExceptionAndClear() {
        var pending = sPendingRecoverableUncaughtException.getAndSet(null);
        if (pending != null) {
            SneakyThrow.sneakyThrow(pending);
        }
    }

    @VisibleForTesting // Used by unit tests too
    public static void maybeThrowPendingRecoverableUncaughtExceptionNoClear() {
        var pending = sPendingRecoverableUncaughtException.get();
        if (pending != null) {
            SneakyThrow.sneakyThrow(pending);
        }
    }

    @VisibleForTesting
    @Nullable
    public static Throwable getPendingRecoverableUncaughtException() {
        return sPendingRecoverableUncaughtException.get();
    }

    // TODO: This should be owned by something else.
    static Description getCurrentDescription() {
        return sCurrentDescription;
    }

    public static void dumpWarnings(PrintStream out) {
        synchronized (sWarnings) {
            var count = sWarnings.size();
            if (count == 0) {
                out.println("No warnings detected.");
                return;
            }
            out.println(count + " warning(s) detected!");
            var i = 0;
            for (var w : sWarnings) {
                out.println("Warning #" + i + ":");
                i++;
                w.printStackTrace(out);
            }
        }
    }

    /**
     * Call it when something that shouldn't happen happaened. We log it throughout the whole
     * process and dump them at the end of each class. We never clear it, so once a warning
     * happens, it'll be repeatedly reported at the end of each subsequent test.
     */
    public static void onWarningDetected(@NonNull String message) {
        var st = RavenwoodImplUtils.getStackTrace(message, RavenwoodErrorHandler.class, true);
        synchronized (sWarnings) {
            Log.w(TAG, "Warning detected! " + message, st);
            sWarnings.add(st);
        }
    }
}
