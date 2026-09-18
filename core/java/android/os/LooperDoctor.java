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

package android.os;

import android.util.Log;

import com.android.internal.dev.perfetto.sdk.PerfettoTrace;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.LockSupport;

/**
 * A diagnostic watchdog for monitoring the health and performance of {@link Looper} and
 * {@link MessageQueue} instances.
 * <p>
 * {@code LooperDoctor} detects common thread stalls and queue bloat, including delayed
 * message delivery, excessive handler execution times, and unbounded queue growth.
 * <p>
 * <b>Design Goals:</b>
 * <ul>
 * <li><b>Avoid priority inversions:</b> Operates without locks to guarantee that monitored threads
 * never suffer priority inversions due to lock contention with the background monitoring thread.
 * </li><li><b>Zero Idle Overhead:</b> The watchdog thread sleeps indefinitely when monitored
 * loopers are idle, waking only when a handler timeout is actively approaching.</li>
 * <li><b>Minimal Hot-Path Impact:</b> Message enqueueing and dispatching paths incur negligible
 * overhead, ensuring the tracking mechanics do not degrade standard message delivery.</li>
 * <li><b>Pay-For-What-You-Use:</b> Imposes minimal computational or memory overhead
 * on {@code Looper} instances that are not actively being monitored by a doctor.</li>
 * <li><b>Actionable Diagnostics:</b> Integrates directly with Perfetto to emit trace triggers
 * and callstacks, providing immediate, actionable trace data rather than unactionable log spam.
 * </li></ul>
 *
 * @hide
 */
public final class LooperDoctor {
    private static final String TAG = "LooperDoctor";
    private static final boolean DEBUG = false;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            sMaxQueueLength = l.findVarHandle(LooperDoctor.class, "mMaxQueueLength", long.class);
            sThreadRef = l.findStaticVarHandle(LooperDoctor.class, "sThreadRefValue",
                    LooperDoctorThread.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // How late a Message can be before we trigger data collection.
    private final long mLateMessageThresholdMs;
    public long getLateMessageThresholdMs() {
        return mLateMessageThresholdMs;
    }

    // Maximum number of outstanding Messages allowed before we trigger data
    // collection. This value will be increased whenever we detected a queue length anomaly.
    private static final VarHandle sMaxQueueLength;
    private volatile long mMaxQueueLength;
    public long getMaxQueueLength() {
        return (long) sMaxQueueLength.getOpaque(this);
    }

    // Maximum amount of time we allow a handler to run before we consider it
    // to have timed out.
    private final long mMaxHandlerRuntimeMs;
    public long getMaxHandlerRuntimeMs() {
        return mMaxHandlerRuntimeMs;
    }

    private static final long MESSAGE_DEADLINE_MS_DEFAULT = 5_000;
    private static final long MAX_QUEUE_LENGTH_DEFAULT = 1_000;
    private static final long MAX_HANDLER_RUNTIME_MS_DEFAULT = 500;

    private LooperDoctor(Builder builder) {
        mLateMessageThresholdMs = builder.mLateMessageThresholdMs;
        mMaxQueueLength = builder.mMaxQueueLength;
        mMaxHandlerRuntimeMs = builder.mMaxHandlerRuntimeMs;
    }

    public static class Builder {
        private long mLateMessageThresholdMs = MESSAGE_DEADLINE_MS_DEFAULT;
        private long mMaxQueueLength = MAX_QUEUE_LENGTH_DEFAULT;
        private long mMaxHandlerRuntimeMs = MAX_HANDLER_RUNTIME_MS_DEFAULT;

        /**
         * How late a Message can be before we trigger data collection.
         */
        public Builder setLateMessageThresholdMs(long threshold) {
            if (threshold > 0) {
                mLateMessageThresholdMs = threshold;
            }
            return this;
        }

        /**
         * Maximum number of outstanding Messages allowed before we trigger data collection.
         * This value will be increased whenever we detected a queue length anomaly.
         */
        public Builder setMaxQueueLength(long length) {
            if (length > 0) {
                mMaxQueueLength = length;
            }
            return this;
        }

        /**
         * Maximum amount of time we allow a handler to run before we consider it to have timed
         * out.
         */
        public Builder setMaxHandlerRuntimeMs(long runtime) {
            if (runtime > 0) {
                mMaxHandlerRuntimeMs = runtime;
            }
            return this;
        }

        /**
         * Allocate the Looper Doctor with the requested config, or defaults if no config
         * specified.
         */
        public LooperDoctor build() {
            return new LooperDoctor(this);
        }
    }

    // Give the user one second to start a trace in case one is not running yet.
    // This does not block LooperDoctor.
    private static final int PERFETTO_TRIGGER_WAIT_MS = 1000;

    public static final String LOOPER_DOCTOR_TRIGGER_LATE_MESSAGE =
            "com.android.LooperDoctor.LateMessage";
    public static final String LOOPER_DOCTOR_TRIGGER_QUEUE_LENGTH =
            "com.android.LooperDoctor.QueueLength";
    public static final String LOOPER_DOCTOR_TRIGGER_LATE_HANDLER =
            "com.android.LooperDoctor.LateHandler";
    public static final String LOOPER_DOCTOR_EVENT_LATE_HANDLER =
            "LateHandlerWithCallstack";

    /**
     * Tell the LooperDoctor that the Looper thread has started.
     *
     * @param looperThread the Looper Thread to monitor
     * @return An alarm object which should be passed to startMessageTimer and stopMessageTimer
     */
    public LooperDoctorAlarm notifyLooperStartedLooping(Thread looperThread) {
        maybeStartLooperDoctorThread();
        return LooperDoctorThread.addAlarm(looperThread);
    }

    // Leave the LooperDoctor Thread running for now.
    // Tests can override this to stress thread creation/destruction
    public static boolean sDropThreadRefOnExit = false;

    /**
     * Tell the LooperDoctor that the Looper thread has quit.
     *
     * @param alarm The alarm returned from notifyLooperStarted.
     */
    public void notifyLooperQuit(LooperDoctorAlarm alarm) {
        if (sDropThreadRefOnExit) {
            maybeStopLooperDoctorThread();
        }
        LooperDoctorThread.removeAlarm(alarm);
    }

    /**
     * Indicate that a message was enqueued just now
     *
     * @param msg the message that was enqueued
     */
    public void messageEnqueued(Message msg) {
        msg.enqueueTime = SystemClock.uptimeMillis();
    }

    /**
     * Indicate that a message was de-queued
     *
     * @param msg the message that was enqueued
     * @param now the current time in MS from when the system booted
     */
    public void messageDequeuedForDelivery(Message msg, long now) {
        if (msg.enqueueTime == -1) {
            return;
        }

        long deadline = msg.when;
        if (msg.when == 0) {
            // msg was sent for immediate delivery, so treat enqueueTime as when
            deadline = msg.enqueueTime;
        }
        if (now - deadline > mLateMessageThresholdMs) {
            PerfettoTrace.activateTrigger(LOOPER_DOCTOR_TRIGGER_LATE_MESSAGE,
                    PERFETTO_TRIGGER_WAIT_MS);
        }
    }

    /**
     * Report a change in the message queue length
     *
     * @param count The current length of the MessageQueue
     */
    public void checkMessageQueueLength(long count) {
        long maxQueueLength = (long) sMaxQueueLength.getOpaque(this);
        if (count > maxQueueLength) {
            PerfettoTrace.activateTrigger(LOOPER_DOCTOR_TRIGGER_QUEUE_LENGTH,
                    PERFETTO_TRIGGER_WAIT_MS);
            // This is to keep from re-alerting on the same threshold breach.
            // We use cmpxchg to make sure concurrent threads don't multiply
            // this value multiple times. Error checking of the cmpxchg is not
            // needed as we're fine if some other thread raced us to set the new
            // queue length.
            // TODO: We should implement some form of decay
            sMaxQueueLength.compareAndExchange(this, maxQueueLength, maxQueueLength * 2);
        }
    }

    public static class LooperDoctorAlarm {
        public static final VarHandle sWhen;
        public volatile long mWhen = LooperDoctorThread.WAKEUP_NEVER;
        public Thread mLooperThread = null;

        static {
            try {
                MethodHandles.Lookup l = MethodHandles.lookup();
                sWhen = l.findVarHandle(LooperDoctor.LooperDoctorAlarm.class, "mWhen",
                        long.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    /**
     * Begin tracking a message which is being delivered.
     *
     * @param alarm The LooperDoctorAlarm returned from startMonitorThread()
     */
    public void startMessageTimer(LooperDoctorAlarm alarm) {
        LooperDoctorThread doctorThread = sThreadRefValue;
        if (doctorThread == null) {
            // Thread not allocated yet, should be a rare case
            return;
        }
        long when = mMaxHandlerRuntimeMs + SystemClock.uptimeMillis();
        alarm.mWhen = when;
        long nextWakeup = doctorThread.mNextWakeup;
        if (when < nextWakeup) {
            // Similar to the null check above there's a very rare chance this could be
            // an old thread. Unpark is safe in any case.
            LockSupport.unpark(doctorThread);
        }
    }

    /**
     * Indicate that a message has completed delivery.
     *
     * @param alarm The LooperDoctorAlarm returned from startMonitorThread()
     */
    public void stopMessageTimer(LooperDoctorAlarm alarm) {
        alarm.mWhen = LooperDoctorThread.WAKEUP_NEVER;
    }

    /**
     * The background watchdog thread responsible for evaluating handler timeouts across all
     * monitored {@link Looper} instances.
     * <p>
     * This thread monitors a lock-free collection of {@link LooperDoctorAlarm} instances, checking
     * if any currently executing messages have exceeded their maximum allowed runtime. If a
     * violation is detected, it triggers a Perfetto event and captures the offending thread's
     * stack trace.
     * <p>
     * <b>Lifecycle and Cardinality:</b>
     * <br>
     * To conserve system resources, there is strictly <b>0 or 1</b> instance of this thread
     * running globally at any given time. Its lifecycle is entirely demand-driven:
     * <ul>
     * <li><b>Startup:</b> Lazily instantiated and started the moment the first
     * {@code LooperDoctor} begins monitoring a {@code Looper}.</li>
     * <li><b>Teardown:</b> Automatically unparked, shut down, and garbage-collected the moment
     * the active alarm count drops to zero (i.e., when the last monitored {@code Looper} quits
     * or clears its doctor).</li>
     * </ul>
     */
    static class LooperDoctorThread extends Thread {
        private static final String TAG = "LooperDoctorThread";
        static final String THREAD_NAME = "LooperDoctorThread";

        static final CopyOnWriteArrayList<LooperDoctorAlarm> sList = new CopyOnWriteArrayList<>();
        static final VarHandle sAlarmThreadCount;
        static volatile long sAlarmThreadCountValue;
        static final long WAKEUP_NEVER = Long.MAX_VALUE;
        volatile long mNextWakeup = WAKEUP_NEVER;
        volatile boolean mShutdown = false;

        static {
            try {
                MethodHandles.Lookup l = MethodHandles.lookup();
                sAlarmThreadCount = l.findStaticVarHandle(LooperDoctor.LooperDoctorThread.class,
                        "sAlarmThreadCountValue", long.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        LooperDoctorThread() {
            super(THREAD_NAME);
        }

        @Override
        public void run() {
            if (DEBUG) {
                Log.d(TAG, "Thread started");
            }
            while (true) {
                if (mShutdown) {
                    break;
                }

                long nextWakeup = WAKEUP_NEVER;
                long now = SystemClock.uptimeMillis();
                for (LooperDoctorAlarm alarm : sList) {
                    long alarmWhen = alarm.mWhen;
                    if (alarmWhen == WAKEUP_NEVER) {
                        continue;
                    }
                    if (alarmWhen <= now) {
                        PerfettoTrace.activateTrigger(LOOPER_DOCTOR_TRIGGER_LATE_HANDLER,
                                PERFETTO_TRIGGER_WAIT_MS);
                        PerfettoTrace.expensiveDebugCallStack(PerfettoCategories.MQ_CATEGORY,
                                LOOPER_DOCTOR_EVENT_LATE_HANDLER,
                                alarm.mLooperThread.getStackTrace()).emit();

                        // cmpxchg in case we race with the looper
                        // If this fails we can expect a new alarm was set.
                        // If we need to recheck this value the setter will have unparked our
                        // thread and we will get it on the next loop around.
                        if (alarm.sWhen.compareAndSet(alarm, alarmWhen, WAKEUP_NEVER)) {
                            continue;
                        }
                    }
                    if (nextWakeup > alarmWhen) {
                        nextWakeup = alarmWhen;
                    }
                }

                mNextWakeup = nextWakeup;
                if (nextWakeup == WAKEUP_NEVER) {
                    LockSupport.park();
                } else {
                    // Calculate relative delay and convert to nanoseconds
                    // We're fine if nextWakeup has already passed. parkNanos()
                    // will handle a negative sleep gracefuly and we'll catch
                    // the alarm on our next loop.
                    long delayNanos = (nextWakeup - SystemClock.uptimeMillis()) * 1_000_000L;
                    LockSupport.parkNanos(delayNanos);
                }
                if (Thread.interrupted()) {
                    mShutdown = true;
                }
            }
        }

        private static LooperDoctorAlarm addAlarm(Thread looperThread) {
            LooperDoctorAlarm alarm = new LooperDoctorAlarm();
            alarm.mLooperThread = looperThread;
            LooperDoctorThread.sList.add(alarm);
            return alarm;
        }

        private static void removeAlarm(LooperDoctorAlarm alarm) {
            LooperDoctorThread.sList.remove(alarm);
        }
    }

    /**
     * Increment ref count on Looper Doctor Thread. Start the thread if necessary.
     * Must eventually call maybeStopLooperDoctorThread() to release the reference.
     */
    private void maybeStartLooperDoctorThread() {
        long count = (long) LooperDoctorThread.sAlarmThreadCount
                .getAndAddAcquire(1L);
        if (count == 0) {
            // First ref starts the thread
            // New refs are fine to race as we are careful to check for nullness of sThreadRef in
            // startMessageTimer()
            // Since we have a ref it's now impossible for threads to enter teardown of
            // sThreadRef.
            // That said we can race a thread that has already begun teardown before we gained our
            // ref. The teardown thread makes a copy of sThreadRef before dropping refs and never
            // reads sThreadRef after that so we are safe to clobber it here.
            if (DEBUG) {
                Log.d(TAG, "Start thread");
            }

            LooperDoctorThread thread = new LooperDoctorThread();
            sThreadRefValue = thread;
            thread.start();
        }
    }

    /**
     * Increment ref count on Looper Doctor Thread. Stop the thread if necessary.
     * It is incorrect to call this without having first called maybeStartLooperDoctorThread().
     */
    private void maybeStopLooperDoctorThread() {
        // Keep a copy of sThreadRef because if the count goes to zero it can be clobbered
        // by the next caller of maybeStartLooperDoctorThread()
        LooperDoctorThread thread = sThreadRefValue;
        long count = (long) LooperDoctorThread.sAlarmThreadCount
                .getAndAdd(-1L);

        if (count == 1) {
            // Last ref stops the thread.
            // We are careful not to clobber sThreadRef at this point as it is possible
            // for another thread to re-gain the first ref after we have dropped ours.
            if (DEBUG) {
                Log.d(TAG, "Stop thread");
            }

            // Swap out the reference for a null pointer so this can be gc'd
            // We don't care if this fails as it means another (starting) thread already made
            // the reference unreachable by overwriting it with a fresh thread.
            sThreadRef.compareAndExchange(thread, (LooperDoctorThread) null);

            thread.mShutdown = true;
            LockSupport.unpark(thread);
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static final VarHandle sThreadRef;
    private static volatile LooperDoctorThread sThreadRefValue;
}
