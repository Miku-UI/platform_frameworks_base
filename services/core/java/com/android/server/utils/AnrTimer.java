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

package com.android.server.utils;

import static android.text.TextUtils.formatSimple;

import static java.util.Comparator.comparingInt;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.Trace;
import android.text.format.TimeMigrationUtils;
import android.util.ArrayMap;
import android.util.IndentingPrintWriter;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.Keep;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.TimeoutRecord;
import com.android.internal.util.RingBuffer;

import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.SortedSet;
import java.util.StringJoiner;
import java.util.TreeSet;

/**
 * This class managers AnrTimers.  An AnrTimer is a substitute for a delayed Message.  In legacy
 * mode, the timer just sends a delayed message.  In modern mode, the timer is implemented in
 * native code; on expiration, the message is sent without delay.
 *
 * <p>There are five external operations on a timer:
 * <ul>
 *
 * <li>{@link #start} starts a timer.  The timer is started with an object that the message
 * argument.  The timer is also given the pid and uid of the target. A timer that is started must
 * be canceled, accepted, or discarded.
 *
 * <li>{@link #cancel} stops a timer and removes any in-flight expiration messages.
 *
 * <li>{@link #accept} acknowledges that the timer has expired, and that an ANR should be
 * generated.  This clears bookkeeping information for the timer.
 *
 * <li>{@link #discard} acknowledges that the timer has expired but, for other reasons, no ANR
 * will be generated.  This clears bookkeeping information for the timer.
 *
 *</li></p>
 *
 * <p>There is one internal operation on a timer: {@link #expire}.  A timer may have automatic
 * extensions enabled.  If so, the extension is computed and if the extension is non-zero, the timer
 * is restarted with the extension timeout.  If extensions are disabled or if the extension is zero,
 * the client process is notified of the expiration.
 *
 * <p>Instances use native resources but not system resources when the feature is enabled.
 * Instances should be explicitly closed unless they are being closed as part of process
 * exit. (So, instances in system server generally need not be explicitly closed since they are
 * created during process start and will last until process exit.)
 *
 * <p>AnrTimer parameterized by the type <code>V</code>.  The public methods on AnrTimer require
 * an instance of <code>V</code>; the instance of <code>V</code> is a key that identifies a
 * specific timer.
 *
 * @hide
 */
public class AnrTimer<V> implements AutoCloseable {

    /**
     * The log tag.
     */
    final static String TAG = "AnrTimer";

    /**
     * The trace track for these events.  There is a single track for all AnrTimer instances.  The
     * tracks give a sense of handler latency: the time between timer expiration and ANR
     * collection.
     */
    private final static String TRACK = "AnrTimerTrack";

    /**
     * Enable debug messages.
     */
    private static final boolean DEBUG = false;

    /**
     * The trace tag is the same usd by ActivityManager.
     */
    private static final long TRACE_TAG = Trace.TRACE_TAG_ACTIVITY_MANAGER;

    /**
     * Fetch the Linux pid from the object. The returned value may be zero to indicate that there
     * is no valid pid available.
     * @return a valid pid or zero.
     */
    public int getPid(V obj) {
        return 0;
    }

    /**
     * Fetch the Linux uid from the object. The returned value may be zero to indicate that there
     * is no valid uid available.
     * @return a valid uid or zero.
     */
    public int getUid(V obj) {
        return 0;
    }

    /**
     * Return true if tracing is feature-enabled.  This has no effect unless tracing is configured.
     * Note that this does not represent any per-process overrides via an Injector.
     */
    public static boolean traceFeatureEnabled() {
        return Flags.anrTimerTrace();
    }

    /**
     * This class allows test code to provide instance-specific overrides.
     */
    static class Injector {
        boolean traceEnabled() {
            return AnrTimer.traceFeatureEnabled();
        }
    }

    /** The default injector. */
    private static final Injector sDefaultInjector = new Injector();

    /**
     * Token that distinguishes early notifications from timer expirations.
     */
    private static final int TOKEN_EXPIRATION = 0;

    /**
     * Token for Long Method Tracing notifications.
     * This token is used in early notifications to trigger long method tracing.
     */
    private static final int TOKEN_LONG_METHOD_TRACING = 0x4d54;

    /** Minimum duration to trace long methods */
    private static final int MIN_LMT_DURATION_MS = 1000;

    /**
     * This class provides build-style arguments to an AnrTimer constructor.  This simplifies the
     * number of AnrTimer constructors needed, especially as new options are added.
     */
    public static class Args {

        /**
         * Represents a point in time (as percent of total) and an associated token. Zero is a
         * reserved token value.
         */
        public record SplitPoint(int percent, int token) {
            public SplitPoint {
                if (token == 0) {
                    throw new IllegalArgumentException("token may not be zero");
                }
                if (percent <= 0 || percent > 100) {
                    throw new IllegalArgumentException("percent must be in (0,100]");
                }
            }
        }

        /** Split point for long method tracing, at 50% elapsed time. */
        private static final SplitPoint sLongMethodTracingPoint =
                new SplitPoint(50, TOKEN_LONG_METHOD_TRACING);

        /** The Injector (used only for testing). */
        private Injector mInjector = AnrTimer.sDefaultInjector;

        /** Enable native timers (if they are available). */
        private boolean mEnable = true;

        /** Grant timer extensions when the system is heavily loaded. */
        private boolean mExtend = false;

        /**
         * All split points, each specifying a percent threshold and an associated token.
         *
         * This set is sorted by percent first so the collection is ordered the way we
         * want, then token second so tow split points with the same percent do not collide.
         *
         * A TreeSet is used to maintain this sorted order, and the uniqueness of split points.
         *
         */
        private final SortedSet<SplitPoint> mSplitPoints =
                new TreeSet<>(comparingInt(SplitPoint::percent)
                        .thenComparingInt(SplitPoint::token));

        /** Make this AnrTimer use test-mode clocking.  This is only useful in tests. */
        private boolean mTestMode = false;

        // This is only used for testing, so it is limited to package visibility.
        Args injector(@NonNull Injector injector) {
            mInjector = injector;
            return this;
        }

        public Args enable(boolean flag) {
            mEnable = flag;
            return this;
        }

        public Args extend(boolean flag) {
            mExtend = flag;
            return this;
        }

        public Args testMode(boolean flag) {
            mTestMode = flag;
            return this;
        }

        /**
         * Add a split point.  For the specific purpose of long method tracing, consider using the
         * {@link #longMethodTracing} method instead.
         */
        public Args splitPoint(SplitPoint point) {
            mSplitPoints.add(point);
            return this;
        }

        /**
         * Enables or disables long method tracing.
         * When enabled, the timer will trigger long method tracing if it reaches 50%
         * of its timeout duration.
         *
         * @param enabled {@code true} to enable long method tracing; {@code false} to disable it.
         * @return this {@link Args} instance for chaining.
         */
        public Args longMethodTracing(boolean enabled) {
            if (enabled) {
                mSplitPoints.add(sLongMethodTracingPoint);
            } else {
                mSplitPoints.remove(sLongMethodTracingPoint);
            }
            return this;

        }

        /**
         * Extracts the percent values from all {@code SplitPoint} objects into an array.
         * <p>
         * This method creates an integer array containing the percent value
         * from each {@code SplitPoint} in the same order they appear in the original list.
         *
         * @return A new {@code int[]} array of all percent values. Never returns null.
         * @see SplitPoint#percent()
         */
        public int[] getSplitPercentArray() {
            int[] percents = new int[mSplitPoints.size()];
            int i = 0;
            for (SplitPoint sp : mSplitPoints) {
                percents[i++] = sp.percent();
            }
            return percents;
        }

        /**
         * Extracts the token values from all {@code SplitPoint} objects into an array.
         * <p>
         * This method creates an integer array containing the token value
         * from each {@code SplitPoint} in the same order they appear in the original list.
         *
         * @return A new {@code int[]} array of all token values. Never returns null.
         * @see SplitPoint#token()
         */
        public int[] getSplitTokenArray() {
            int[] tokens = new int[mSplitPoints.size()];
            int i = 0;
            for (SplitPoint sp : mSplitPoints) {
                tokens[i++] = sp.token();
            }
            return tokens;
        }
    }

    /**
     * Information about a timer that has expired.
     */
    public static class ExpiredTimer {
        // The timer ID.
        final int mTimerId;

        // The start uptime of the timer in millis.
        public final long mStartMs;

        // The total duration uptime of the timer in millis.
        // Includes any extensions.
        public final long mDurationMs;

        ExpiredTimer(int id, long startMs, long durationMs) {
            mTimerId = id;
            mStartMs = startMs;
            mDurationMs = durationMs;
        }
    }

    /**
     * An error is defined by its issue, the operation that detected the error, the tag of the
     * affected service, a short stack of the bad call, and the stringified arg associated with
     * the error.
     */
    private static final class Error {
        /** The issue is the kind of error that was detected.  This is a free-form string. */
        final String issue;
        /** The operation that detected the error: start, cancel, accept, or discard. */
        final String operation;
        /** The argument (stringified) passed in to the operation. */
        final String arg;
        /** The tag of the associated AnrTimer. */
        final String tag;
        /** A partial stack that localizes the caller of the operation. */
        final StackTraceElement[] stack;
        /** The date, in local time, the error was created. */
        final long timestamp;

        Error(@NonNull String issue, @NonNull String operation, @NonNull String tag,
                @NonNull StackTraceElement[] stack, @NonNull String arg) {
            this.issue = issue;
            this.operation = operation;
            this.tag = tag;
            this.stack = stack;
            this.arg = arg;
            this.timestamp = SystemClock.elapsedRealtime();
        }

        /**
         * Dump a single error to the output stream.
         */
        private void dump(IndentingPrintWriter ipw, int seq) {
            ipw.format("%2d: op:%s tag:%s issue:%s arg:%s\n", seq, operation, tag, issue, arg);

            final long offset = System.currentTimeMillis() - SystemClock.elapsedRealtime();
            final long etime = offset + timestamp;
            ipw.println("    date:" + TimeMigrationUtils.formatMillisWithFixedFormat(etime));
            ipw.increaseIndent();
            for (int i = 0; i < stack.length; i++) {
                ipw.println("    " + stack[i].toString());
            }
            ipw.decreaseIndent();
        }
    }

    /**
     * A list of errors detected during processing.  Errors correspond to "timer not found"
     * conditions.  The stack trace identifies the source of the call.  The list is
     * first-in/first-out, and the size is limited to 20.
     */
    @GuardedBy("sErrors")
    private static final RingBuffer<Error> sErrors = new RingBuffer<>(Error.class, 20);

    /** A lock for the AnrTimer instance. */
    private final Object mLock = new Object();

    /** The map from client argument to the associated timer ID. */
    @GuardedBy("mLock")
    private final ArrayMap<V, Integer> mTimerIdMap = new ArrayMap<>();

    /** Reverse map from timer ID to client argument, needed by the expire() callback. */
    @GuardedBy("mLock")
    private final SparseArray<V> mTimerArgMap = new SparseArray<>();

    /** Map from timer ID to ExpiredTimer. */
    @GuardedBy("mLock")
    private final SparseArray<ExpiredTimer> mExpiredTimers = new SparseArray<>();

    /** The highwater mark of started, but not closed, timers. */
    @GuardedBy("mLock")
    private int mMaxStarted = 0;

    /** The total number of timers started. */
    @GuardedBy("mLock")
    private int mTotalStarted = 0;

    /** The total number of errors detected. */
    @GuardedBy("mLock")
    private int mTotalErrors = 0;

    /** The total number of timers that have expired. */
    @GuardedBy("mLock")
    private int mTotalExpired = 0;

    /** The handler for messages sent from this instance. */
    private final Handler mHandler;

    /** The message type for messages sent from this interface. */
    private final int mWhat;

    /** A label that identifies the AnrTimer associated with a Timer in log messages. */
    private final String mLabel;

    /** The configuration for this instance. */
    private final Args mArgs;

    /** The top-level switch for the feature enabled or disabled. */
    private final FeatureSwitch mFeature;

    /**
     * Create one AnrTimer instance.  The instance is given a handler and a "what".  Individual
     * timers are started with {@link #start}.  If a timer expires, then a {@link Message} is sent
     * immediately to the handler with {@link Message.what} set to what and {@link Message.obj} set
     * to the timer key.
     *
     * AnrTimer instances have a label, which must be unique.  The label is used for reporting and
     * debug.
     *
     * If an individual timer expires internally, and the "extend" parameter is true, then the
     * AnrTimer may extend the individual timer rather than immediately delivering the timeout to
     * the client.  The extension policy is not part of the instance.
     *
     * @param handler The handler to which the expiration message will be delivered.
     * @param what The "what" parameter for the expiration message.
     * @param label A name for this instance.
     * @param args Configuration information for this instance.
     */
    public AnrTimer(@NonNull Handler handler, int what, @NonNull String label, @NonNull Args args) {
        mHandler = handler;
        mWhat = what;
        mLabel = label;
        mArgs = args;
        mFeature = createFeatureSwitch();
    }

    // Return the correct feature.  FeatureEnabled is returned if and only if the feature is
    // flag-enabled and if the native shadow was successfully created.  Otherwise, FeatureDisabled
    // is returned.
    private FeatureSwitch createFeatureSwitch() {
        final boolean enabled = mArgs.mEnable && nativeTimersSupported();
        if (!enabled) {
            return new FeatureDisabled();
        } else {
            try {
                return new FeatureEnabled();
            } catch (RuntimeException e) {
                // Something went wrong in the native layer.  Log the error and fall back on the
                // feature-disabled logic.
                Log.e(TAG, e.toString());
                return new FeatureDisabled();
            }
        }
    }

    /**
     * Create an AnrTimer instance with the default {@link #Injector} and the default configuration.
     * See {@link AnrTimer(Handler, int, String, boolean, Injector} for a functional description.
     *
     * @param handler The handler to which the expiration message will be delivered.
     * @param what The "what" parameter for the expiration message.
     * @param label A name for this instance.
     */
    public AnrTimer(@NonNull Handler handler, int what, @NonNull String label) {
        this(handler, what, label, new Args());
    }

    /**
     * Return true if the service is enabled on this instance.  Clients should use this method to
     * decide if the feature is enabled, and not read the flags directly.  This method should be
     * deleted if and when the feature is enabled permanently.
     *
     * @return true if the service is flag-enabled.
     */
    public boolean serviceEnabled() {
        return mFeature.enabled();
    }

    /**
     * Emit a trace instant event with an arbitrary list of arguments.
     * Arguments are comma-joined and wrapped in <code>op(...)</code>.
     */
    private static void trace(String op, Object... args) {
        StringJoiner joiner = new StringJoiner(",", op + "(", ")");

        for (Object arg : args) joiner.add(String.valueOf(arg));

        final String label = joiner.toString();
        Trace.instantForTrack(TRACE_TAG, TRACK, label);
        if (DEBUG) Log.i(TAG, label);
    }

    /**
     * The FeatureSwitch class provides a quick switch between feature-enabled behavior and
     * feature-disabled behavior.
     */
    private abstract class FeatureSwitch {
        abstract void start(@NonNull V arg, int pid, int uid, long timeoutMs);

        abstract boolean cancel(@NonNull V arg);

        @Nullable
        abstract ExpiredTimer accept(@NonNull V arg);

        abstract boolean discard(@NonNull V arg);

        abstract boolean enabled();

        abstract void dump(IndentingPrintWriter pw, boolean verbose);

        abstract void close();

        abstract void setTime(long now);
    }

    /**
     * The FeatureDisabled class bypasses almost all AnrTimer logic.  It is used when the AnrTimer
     * service is disabled via Flags.anrTimerService().
     */
    private class FeatureDisabled extends FeatureSwitch {
        /** Start a timer by sending a message to the client's handler. */
        @Override
        void start(@NonNull V arg, int pid, int uid, long timeoutMs) {
            cancel(arg);
            final Message msg = mHandler.obtainMessage(mWhat, arg);
            mHandler.sendMessageDelayed(msg, timeoutMs);
        }

        /** Cancel a timer by removing the message from the client's handler. */
        @Override
        boolean cancel(@NonNull V arg) {
            mHandler.removeMessages(mWhat, arg);
            return true;
        }

        /** accept() is a no-op when the feature is disabled. */
        @Override
        @Nullable
        ExpiredTimer accept(@NonNull V arg) {
            return null;
        }

        /** discard() is a no-op when the feature is disabled. */
        @Override
        boolean discard(@NonNull V arg) {
            return true;
        }

        /** The feature is not enabled. */
        @Override
        boolean enabled() {
            return false;
        }

        /** Dump the limited statistics captured when the feature is disabled. */
        @Override
        void dump(IndentingPrintWriter pw, boolean verbose) {
            synchronized (mLock) {
                pw.format("started=%d maxStarted=%d running=%d expired=%d errors=%d\n",
                        mTotalStarted, mMaxStarted, mTimerIdMap.size(),
                        mTotalExpired, mTotalErrors);
            }
        }

        /** close() is a no-op when the feature is disabled. */
        @Override
        void close() {
        }

        /** The disabled timer does not support this operation. */
        @Override
        void setTime(long now) {
            throw new UnsupportedOperationException("setTime unavailable in disabled mode");
        }
    }

    /**
     * A static list of AnrTimer instances.  The list is traversed by dumpsys.  Only instances
     * using native resources are included.
     */
    @GuardedBy("sAnrTimerList")
    private static final LongSparseArray<WeakReference<AnrTimer>> sAnrTimerList =
        new LongSparseArray<>();

    /**
     * The FeatureEnabled class enables the AnrTimer logic.  It is used when the AnrTimer service
     * is enabled via Flags.anrTimerService().
     */
    private class FeatureEnabled extends FeatureSwitch {

        /**
         * The native timer that supports this instance. The value is set to non-zero when the
         * native timer is created and it is set back to zero when the native timer is freed.
         */
        private long mNative = 0;

        /** The total number of timers that were restarted without an explicit cancel. */
        @GuardedBy("mLock")
        private int mTotalRestarted = 0;

        /** Create the native AnrTimerService that will host all timers from this instance. */
        FeatureEnabled() {
            mNative = nativeAnrTimerCreate(mLabel, mArgs.mExtend, mArgs.getSplitPercentArray(),
                    mArgs.getSplitTokenArray(), mArgs.mTestMode);
            if (mNative == 0) throw new IllegalArgumentException("unable to create native timer");
            synchronized (sAnrTimerList) {
                sAnrTimerList.put(mNative, new WeakReference(AnrTimer.this));
            }
        }

        /**
         * Start a timer.
         */
        @Override
        void start(@NonNull V arg, int pid, int uid, long timeoutMs) {
            synchronized (mLock) {
                if (mNative == 0) throw new IllegalStateException("timer service is closed");

                // If there is an existing timer, cancel it.  This is a nop if the timer does not
                // exist.
                if (cancel(arg)) mTotalRestarted++;

                final int timerId = nativeAnrTimerStart(mNative, pid, uid, timeoutMs);
                if (timerId > 0) {
                    mTimerIdMap.put(arg, timerId);
                    mTimerArgMap.put(timerId, arg);
                    mTotalStarted++;
                    mMaxStarted = Math.max(mMaxStarted, mTimerIdMap.size());
                } else {
                    throw new RuntimeException("unable to start timer");
                }
            }
        }

        /**
         * Cancel a timer.  No error is reported if the timer is not found because some clients
         * cancel timers from common code that runs even if a timer was never started.
         */
        @Override
        boolean cancel(@NonNull V arg) {
            synchronized (mLock) {
                Integer timer = removeLocked(arg);
                if (timer == null) {
                    return false;
                }
                // Race conditions may lead to timer cancellation after the service was closed.
                if (mNative == 0) return false;
                if (!nativeAnrTimerCancel(mNative, timer)) {
                    // There may be an expiration message in flight.  Cancel it.
                    mHandler.removeMessages(mWhat, arg);
                    return false;
                }
                return true;
            }
        }

        /**
         * Accept a timer in the framework-level handler.  The timeout has been accepted and the
         * client's timeout handler is executing.  If the function returns a non-null ExpiredTimer
         * then the associated process may have been paused (or otherwise modified in preparation
         * for debugging). The ExpiredTimer must be closed.
         */
        @Override
        @Nullable
        ExpiredTimer accept(@NonNull V arg) {
            synchronized (mLock) {
                ExpiredTimer timer = removeLockedTimer(arg);
                if (timer == null) {
                    notFoundLocked("accept", arg);
                    return null;
                }
                // Race conditions may lead to timer acceptance after the service was closed.
                if (mNative == 0) return null;
                boolean accepted = nativeAnrTimerAccept(mNative, timer.mTimerId);
                trace("accept", timer);
                // If "accepted" is true then the native layer has pending operations against this
                // timer.  Wrap the timer ID in a ExpiredTimer and return it to the caller.  If
                // "accepted" is false then the native later does not have any pending operations.
                if (!accepted) {
                    timer = null;
                }
                return timer;
            }
        }

        /**
         * Discard a timer in the framework-level handler.  For whatever reason, the timer is no
         * longer interesting.  No statistics are collected.  Return false if the time was not
         * found.
         */
        @Override
        boolean discard(@NonNull V arg) {
            synchronized (mLock) {
                Integer timer = removeLocked(arg);
                if (timer == null) {
                    notFoundLocked("discard", arg);
                    return false;
                }
                // Race conditions may lead to timer discard  after the service was closed.
                if (mNative == 0) return false;
                nativeAnrTimerDiscard(mNative, timer);
                trace("discard", timer);
                return true;
            }
        }

        /** The feature is enabled. */
        @Override
        boolean enabled() {
            return true;
        }

        /** Dump statistics from the native layer. */
        @Override
        void dump(IndentingPrintWriter pw, boolean verbose) {
            synchronized (mLock) {
                if (mNative == 0) {
                    pw.println("closed");
                    return;
                }
                String[] nativeDump = nativeAnrTimerDump(mNative);
                if (nativeDump == null) {
                    pw.println("no-data");
                    return;
                }
                for (String s : nativeDump) {
                    pw.println(s);
                }
                // The following counter is only available at the Java level.
                pw.println("restarted:" + mTotalRestarted);
            }
        }

        /** Free native resources. */
        @Override
        void close() {
            // Remove self from the list of active timers.
            synchronized (sAnrTimerList) {
                sAnrTimerList.remove(mNative);
            }
            synchronized (mLock) {
                if (mNative != 0) nativeAnrTimerClose(mNative);
                mNative = 0;
            }
        }

        /**
         * Delete the entries associated with arg from the maps and return the ExpiredTimer of the
         * timer, if any.
         */
        @GuardedBy("mLock")
        private ExpiredTimer removeLockedTimer(V arg) {
            final Integer r = mTimerIdMap.remove(arg);
            ExpiredTimer l = null;
            if (r != null) {
                mTimerArgMap.remove(r);
                l = mExpiredTimers.removeReturnOld(r);
            }
            return l;
        }

        /**
         * Delete the entries associated with arg from the maps and return the ID of the timer, if
         * any.
         */
        @GuardedBy("mLock")
        private Integer removeLocked(V arg) {
            final Integer r = mTimerIdMap.remove(arg);
            if (r != null) {
                mTimerArgMap.remove(r);
                mExpiredTimers.removeReturnOld(r);
            }
            return r;
        }

        /** This is always safe to call; it does nothing if the timer is not in test mode. */
        @Override
        void setTime(long now) {
            if (!mArgs.mTestMode) {
                throw new UnsupportedOperationException("setTime called outside test mode");
            } else if (!nativeAnrTimerSetTime(mNative, now)) {
                throw new RuntimeException("setTime failure");
            }
        }
    }

    /**
     * Start a timer associated with arg.  The same object must be used to cancel, accept, or
     * discard a timer later.  If a timer already exists with the same arg, then the existing timer
     * is canceled and a new timer is created.  The timeout is signed but negative delays are
     * nonsensical.  Rather than throw an exception, timeouts less than 0ms are forced to 0ms.  This
     * allows a client to deliver an immediate timeout via the AnrTimer.
     *
     * @param arg The key by which the timer is known.  This is never examined or modified.
     * @param timeoutMs The timer timeout, in milliseconds.
     */
    public void start(@NonNull V arg, long timeoutMs) {
        start(arg, getPid(arg), getUid(arg), timeoutMs);
    }

    /**
     * Start a timer associated with arg, pid, and uid.  The same object must be used to cancel,
     * accept, or discard a timer later.  If a timer already exists with the same arg, then the
     * existing timer is canceled and a new timer is created.  The timeout is signed but negative
     * delays are nonsensical.  Rather than throw an exception, timeouts less than 0ms are forced to
     * 0ms.  This allows a client to deliver an immediate timeout via the AnrTimer.
     *
     * @param arg The key by which the timer is known.  This is never examined or modified.
     * @param pid The process ID of the process that is being timed.
     * @param uid The UID of the process that is being timed.
     * @param timeoutMs The timer timeout, in milliseconds.
     */
    public void start(@NonNull V arg, int pid, int uid, long timeoutMs) {
        if (timeoutMs < 0) timeoutMs = 0;
        mFeature.start(arg, pid, uid, timeoutMs);
    }

    /**
     * Cancel the running timer associated with arg.  The timer is forgotten.  If the timer has
     * expired, the call is treated as a discard.  The function returns true if a running timer was
     * found, and false if an expired timer was found or if no timer was found.  After this call,
     * the timer does not exist.
     *
     * Note: the return value is always true if the feature is not enabled.
     *
     * @param arg The key by which the timer is known.  This is never examined or modified.
     * @return True if a running timer was canceled.
     */
    public boolean cancel(@NonNull V arg) {
        return mFeature.cancel(arg);
    }

    /**
     * Accept the expired timer associated with arg.  This indicates that the caller considers the
     * timer expiration to be a true ANR.  (See {@link #discard} for an alternate response.)  The
     * function stores a {@link ExpiredTimer} in the {@link TimeoutRecord} argument.  The
     * ExpiredTimer records information about the expired timer for retrieval during ANR report
     * generation.  After this call, the timer does not exist.
     *
     * It is a protocol error to accept a running timer, however, the running timer will be
     * canceled.
     *
     * @param arg The key by which the timer is known.  This is never examined or modified.
     * @param timeoutRecord The TimeoutRecord that will hold information about the expired timer.
     */
    public void accept(@NonNull V arg, @NonNull TimeoutRecord timeoutRecord) {
        timeoutRecord.setExpiredTimer(mFeature.accept(arg));
    }

    /**
     * Discard the expired timer associated with arg.  This indicates that the caller considers the
     * timer expiration to be a false ANR.  ((See {@link #accept} for an alternate response.)  One
     * reason to discard an expired timer is if the process being timed was also being debugged:
     * such a process could be stopped at a breakpoint and its failure to respond would not be an
     * error.  After this call thie timer does not exist. It is an error to discard a running timer,
     * however the running timer will be canceled.
     *
     * Note: the return value is always true if the feature is not enabled.
     *
     * @param arg The key by which the timer is known.  This is never examined or modified.
     * @return True if an expired timer was discarded.
     */
    public boolean discard(@NonNull V arg) {
        return mFeature.discard(arg);
    }

    /**
     * The notifier that a timer has fired.  The timerId and original pid/uid are supplied.  The
     * elapsed time is the actual time since the timer was scheduled, which may be different from
     * the original timeout if the timer was extended or if other delays occurred. This method
     * takes mLock so that a timer cannot expire in the middle of another operation (like start or
     * cancel).
     *
     * This method is called from native code.  The function must return true if the expiration
     * message is delivered to the upper layers and false if it could not be delivered.
     */
    @Keep
    private boolean expire(int timerId, int pid, int uid, long startMs, long elapsedMs) {
        trace("expired", timerId, pid, uid, mLabel, elapsedMs);
        final V arg;
        synchronized (mLock) {
            arg = mTimerArgMap.get(timerId);
            if (arg == null) {
                Log.e(TAG, formatSimple("failed to expire timer %s:%d : arg not found",
                                mLabel, timerId));
                mTotalErrors++;
                return false;
            }
            mExpiredTimers.put(timerId, new ExpiredTimer(timerId, startMs, elapsedMs));
            mTotalExpired++;
        }
        final Message msg = Message.obtain(mHandler, mWhat, arg);
        // arg1 is zero to signal that this is an expiration callback, and not an early notification
        // callback.
        // this an expiration.
        msg.arg1 = TOKEN_EXPIRATION;
        mHandler.sendMessage(msg);
        return true;
    }

    /**
     * Called when a timer reaches an early notification split point.
     * This allows for proactive actions before the timer fully expires.
     *
     * @param timerId the timer ID
     * @param pid pid of the timed operation
     * @param uid uid of the timed operation
     * @param elapsedMs milliseconds elapsed since timer start
     * @param token identifies the type of early notification
     */
    @Keep
    private void notifyEarly(int timerId, int pid, int uid,
                            long elapsedMs, int token) {
        // Long method tracing is a special case for early notifications.  It is handled directly
        // in this method.
        if (token == TOKEN_LONG_METHOD_TRACING) {
            trace("notifyEarly", timerId, pid, uid, mLabel, elapsedMs, token);
            LongMethodTracer.trigger(pid,
                    (int) Math.max(MIN_LMT_DURATION_MS, elapsedMs * 1.5));
            return;
        }

        // The token is not requesting long method tracing.  The event is forwarded to the message
        // handler.  This path is used during testing although it is allowed in all cases.
        V arg = null;
        synchronized (mLock) {
            arg = mTimerArgMap.get(timerId);
            if (arg == null) {
                Log.e(TAG, formatSimple("failed early notiffor for timer %s:%d : arg not found",
                                mLabel, timerId));
                mTotalErrors++;
                return;
            }
        }

        final Message msg = Message.obtain(mHandler, mWhat, arg);
        // arg1 is used to signal early notifications; a non-zero arg1 means this an early
        // notification, and arg1 is the token that is passed to the callback.
        msg.arg1 = token;
        mHandler.sendMessage(msg);
    }

    /**
     * Close the object and free any native resources.
     */
    public void close() {
        mFeature.close();
    }

    /**
     * Set the current time as seen by this AnrTimer.  This is only effective for native timers
     * that were created with testMode enabled.
     */
    @VisibleForTesting
    public void setTime(long now) {
        mFeature.setTime(now);
    }

    /**
     * Return the ExpiredTimer associated with a TimeoutRecord.  The TimeoutRecord is not modified.
     */
    @Nullable
    public static ExpiredTimer expiredTimer(TimeoutRecord tr) {
        Object expiredTimer = tr.getExpiredTimer();
        if (expiredTimer instanceof ExpiredTimer lock) {
            return lock;
        } else {
            return null;
        }
    }

    /**
     * Ensure any native resources are freed when the object is GC'ed.  Best practice is to close
     * the object explicitly, but overriding finalize() avoids accidental leaks.
     */
    @SuppressWarnings("Finalize")
    @Override
    protected void finalize() throws Throwable {
        close();
        super.finalize();
    }

    /**
     * Dump a single AnrTimer.
     */
    private void dump(IndentingPrintWriter pw) {
        synchronized (mLock) {
            pw.format("timer: %s\n", mLabel);
            pw.increaseIndent();
            mFeature.dump(pw, false);
            pw.decreaseIndent();
        }
    }

    /**
     * The current time in milliseconds.
     */
    private static long now() {
        return SystemClock.uptimeMillis();
    }

    /**
     * Dump all errors to the output stream.
     */
    private static void dumpErrors(IndentingPrintWriter ipw) {
        Error errors[];
        synchronized (sErrors) {
            if (sErrors.size() == 0) return;
            errors = sErrors.toArray();
        }
        ipw.println("Errors");
        ipw.increaseIndent();
        for (int i = 0; i < errors.length; i++) {
            if (errors[i] != null) errors[i].dump(ipw, i);
        }
        ipw.decreaseIndent();
    }

    /**
     * Log an error.  A limited stack trace leading to the client call that triggered the error is
     * recorded.  The stack trace assumes that this method is not called directly.
     *
     * If DEBUG is true, a log message is generated as well.
     */
    @GuardedBy("mLock")
    private void recordErrorLocked(String operation, String errorMsg, Object arg) {
        StackTraceElement[] s = Thread.currentThread().getStackTrace();
        final String what = Objects.toString(arg);
        // The copy range starts at the caller of the timer operation, and includes three levels.
        // This should be enough to isolate the location of the call.
        StackTraceElement[] location = Arrays.copyOfRange(s, 6, 9);
        synchronized (sErrors) {
            sErrors.append(new Error(errorMsg, operation, mLabel, location, what));
        }
        if (DEBUG) Log.w(TAG, operation + " " + errorMsg + " " + mLabel + " timer " + what);
        mTotalErrors++;
    }

    /** Record an error about a timer not found. */
    @GuardedBy("mLock")
    private void notFoundLocked(String operation, Object arg) {
        recordErrorLocked(operation, "notFound", arg);
    }

    /** Compare two AnrTimers in display order. */
    private static final Comparator<AnrTimer> sComparator =
            Comparator.nullsLast(new Comparator<>() {
                    @Override
                    public int compare(AnrTimer o1, AnrTimer o2) {
                        return o1.mLabel.compareTo(o2.mLabel);
                    }});

    /** Dumpsys output, allowing for overrides. */
    @VisibleForTesting
    static void dump(@NonNull PrintWriter pw, boolean verbose, @NonNull Injector injector) {
        final IndentingPrintWriter ipw = new IndentingPrintWriter(pw);
        ipw.println("AnrTimer statistics");
        ipw.increaseIndent();
        synchronized (sAnrTimerList) {
            // Find the currently live instances and sort them by their label.  The goal is to
            // have consistent output ordering.
            final int size = sAnrTimerList.size();
            AnrTimer[] active = new AnrTimer[size];
            int valid = 0;
            for (int i = 0; i < size; i++) {
                AnrTimer a = sAnrTimerList.valueAt(i).get();
                if (a != null) active[valid++] = a;
            }
            Arrays.sort(active, 0, valid, sComparator);
            for (int i = 0; i < valid; i++) {
                if (active[i] != null) active[i].dump(ipw);
            }
        }
        if (verbose) dumpErrors(ipw);
        ipw.format("AnrTimerEnd\n");
        ipw.decreaseIndent();
    }

    /** Dumpsys output.  There is no output if the feature is not enabled. */
    public static void dump(@NonNull PrintWriter pw, boolean verbose) {
        dump(pw, verbose, sDefaultInjector);
    }

    /**
     * Set a trace specification.  The input is a set of strings.  On success, the function pushes
     * the trace specification to all timers, and then returns a response message.  On failure,
     * the function throws IllegalArgumentException and tracing is disabled.
     *
     * An empty specification has no effect other than returning the current trace specification.
     */
    @Nullable
    public static String traceTimers(@Nullable String[] spec) {
        return nativeAnrTimerTrace(spec);
    }

    /**
     * Return true if the native timers are supported.  Native timers are supported if the method
     * nativeAnrTimerSupported() can be executed and it returns true.
     */
    public static boolean nativeTimersSupported() {
        try {
            return nativeAnrTimerSupported();
        } catch (java.lang.UnsatisfiedLinkError e) {
            return false;
        }
    }

    /**
     * Native methods
     */

    /** Return true if the native AnrTimer code is operational. */
    private static native boolean nativeAnrTimerSupported();

    /**
     * Create a new native timer with the given name and flags.  The name is only for logging.
     * Unlike the other methods, this is an instance method: the "this" parameter is passed into
     * the native layer.
     *
     * When testMode is true, the native timer is disconnected from any real clock.  Use
     * nativeAnrTimerSetTime() to change the time seen by a testMode timer.
     */
    private native long nativeAnrTimerCreate(String name, boolean extend,
            int[] splitPercent, int[] splitToken, boolean testMode);

    /** Release the native resources.  No further operations are premitted. */
    private static native int nativeAnrTimerClose(long service);

    /** Start a timer and return its ID.  Zero is returned on error. */
    private static native int nativeAnrTimerStart(long service, int pid, int uid, long timeoutMs);

    /**
     * Cancel a timer by ID.  Return true if the timer was running and canceled.  Return false if
     * the timer was not found or if the timer had already expired.
     */
    private static native boolean nativeAnrTimerCancel(long service, int timerId);

    /**
     * Accept an expired timer by ID.  Return true if the timer must be released.  Return false if
     * the native layer is completely finished with this timer.
     */
    private static native boolean nativeAnrTimerAccept(long service, int timerId);

    /** Discard an expired timer by ID.  Return true if the timer was found.  */
    private static native boolean nativeAnrTimerDiscard(long service, int timerId);

    /**
     * Configure tracing.  The input array is a set of words pulled from the command line.  All
     * parsing happens inside the native layer.  The function returns a string which is either an
     * error message (so nothing happened) or the current configuration after applying the config.
     * Passing an null array or an empty array simply returns the current configuration.
     * The function returns null if the native layer is not implemented.
     */
    private static native @Nullable String nativeAnrTimerTrace(@Nullable String[] config);

    /** Retrieve runtime dump information from the native layer. */
    private static native String[] nativeAnrTimerDump(long service);

    /**
     * Set the clock for a native time service.  If the time service was created in test mode,
     * this changes the service's view of "now" and returns true.  Otherwise it has no effect and
     * returns false.
     */
    private static native boolean nativeAnrTimerSetTime(long service, long now);
}
