/*
 * Copyright (C) 2006 The Android Open Source Project
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

import static android.os.Message.*;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.annotation.TestApi;
import android.app.ActivityThread;
import android.compat.annotation.UnsupportedAppUsage;
import android.ravenwood.annotation.RavenwoodKeepWholeClass;
import android.ravenwood.annotation.RavenwoodRedirect;
import android.ravenwood.annotation.RavenwoodRedirectionClass;
import android.ravenwood.annotation.RavenwoodThrow;
import android.util.Log;
import android.util.Printer;
import android.util.SparseArray;
import android.util.proto.ProtoOutputStream;

import dalvik.annotation.optimization.NeverCompile;

import java.io.FileDescriptor;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Low-level class holding the list of messages to be dispatched by a
 * {@link Looper}.  Messages are not added directly to a MessageQueue,
 * but rather through {@link Handler} objects associated with the Looper.
 *
 * <p>You can retrieve the MessageQueue for the current thread with
 * {@link Looper#myQueue() Looper.myQueue()}.
 */
@RavenwoodKeepWholeClass
@RavenwoodRedirectionClass("MessageQueue_ravenwood")
public final class MessageQueue {
    private static final String TAG = "MessageQueue";
    private static final String TAG_L = "LegacyMessageQueue";
    private static final String TAG_C = "ConcurrentMessageQueue";
    private static final boolean DEBUG = false;

    // True if the message queue can be quit.
    @UnsupportedAppUsage
    private final boolean mQuitAllowed;

    /**
     * Used by all native methods.
     *
     * <p>In legacy mode, usage of this field (directly, or indirectly via native method
     * invocations) must be guarded with the lock.
     *
     * <p>In concurrent mode, the Looper thread may access freely, but other threads must first call
     * {@link #incrementMptrRefs()}, check the result, and if true then access the native
     * object, followed by a call to {@link #decrementMptrRefs()}.
     */
    @UnsupportedAppUsage
    @SuppressWarnings("unused")
    private long mPtr; // used by native code

    @UnsupportedAppUsage(
            maxTargetSdk = Build.VERSION_CODES.BAKLAVA,
            publicAlternatives =
                    "To manipulate the queue in Instrumentation tests, use {@link"
                        + " android.os.TestLooperManager}")
    Message mMessages;

    private Message mLast;
    @UnsupportedAppUsage
    private final ArrayList<IdleHandler> mIdleHandlers = new ArrayList<IdleHandler>();
    private SparseArray<FileDescriptorRecord> mFileDescriptorRecords;
    private IdleHandler[] mPendingIdleHandlers;
    private boolean mQuitting;

    // Indicates whether next() is blocked waiting in pollOnce() with a non-zero timeout.
    private boolean mBlocked;

    // Tracks the number of async message. We use this in enqueueMessage() to avoid searching the
    // queue for async messages when inserting a message at the tail.
    private int mAsyncMessageCount;

    private final AtomicLong mMessageCount = new AtomicLong();
    private final Thread mLooperThread;
    private final String mThreadName;
    private final long mTid;

    /**
     * Select between two implementations of message queue. The legacy implementation is used
     * by default as it provides maximum compatibility with applications and tests that
     * reach into MessageQueue via the mMessages field. The concurrent implementation is used for
     * system processes and provides a higher level of concurrency and higher enqueue throughput
     * than the legacy implementation.
     */
    private static boolean sUseConcurrentInitialized = false;
    private static boolean sUseConcurrent;

    /**
     * Determine if the native looper will skip epoll_wait syscalls if nativePollOnce is called with
     * a timeout of 0, which indicates that there are already pending messages.
     */
    private static boolean sSkipEpollWaitForZeroTimeoutInitialized = false;

    /**
     * Caches process-level checks that determine `sUseConcurrent`.
     * This is to avoid redoing checks that shouldn't change during the process's lifetime.
     */
    private static Boolean sIsProcessAllowedToUseConcurrent = null;

    @RavenwoodRedirect
    private native static long nativeInit();
    @RavenwoodRedirect
    private native static void nativeDestroy(long ptr);
    @UnsupportedAppUsage
    @RavenwoodRedirect
    private native void nativePollOnce(long ptr, int timeoutMillis); /*non-static for callbacks*/

    @RavenwoodRedirect
    private native static void nativeWake(long ptr);
    @RavenwoodRedirect
    private native static boolean nativeIsPolling(long ptr);
    @RavenwoodRedirect
    private native static void nativeSetFileDescriptorEvents(long ptr, int fd, int events);
    @RavenwoodRedirect
    private native static void nativeSetSkipEpollWaitForZeroTimeout(long ptr);

    MessageQueue(boolean quitAllowed) {
        getUseConcurrent();
        mQuitAllowed = quitAllowed;
        mPtr = nativeInit();
        mLooperThread = Thread.currentThread();
        mThreadName = mLooperThread.getName();
        mTid = Process.myTid();
        setSkipEpollWaitForZeroTimeout(mPtr);
    }

    static boolean getUseConcurrent() {
        if (!sUseConcurrentInitialized) {
            // We may race and compute the underlying value more than once.
            // This is fine because computeUseConcurrent is idempotent.
            final boolean useConcurrent = computeUseConcurrent();
            sUseConcurrent = useConcurrent;
            sUseConcurrentInitialized = true;
            return useConcurrent;
        }
        return sUseConcurrent;
    }

    private static boolean computeUseConcurrent() {
        if (Flags.useConcurrentMessageQueueInApps()) {
            // b/379472827: Robolectric tests use reflection to access MessageQueue.mMessages.
            // This is a hack to allow Robolectric tests to use the legacy implementation.
            try {
                Class.forName("org.robolectric.Robolectric");
                // This is a Robolectric test. Concurrent MessageQueue is not supported yet.
                return false;
            } catch (ClassNotFoundException e) {
                // This is not a Robolectric test.
                return true;
            }
        }

        final String processName = Process.myProcessName();
        if (processName == null) {
            // Assume that this is a host-side test and avoid concurrent mode for now.
            return false;
        }

        // Concurrent mode modifies behavior that is observable via reflection and is commonly
        // used by tests.
        // For now, we limit it to system processes to avoid breaking apps and their tests.
        if (UserHandle.isCore(Process.myUid())) {
            // Some platform tests run in core UIDs.
            // Use this awful heuristic to detect them.
            if (processName.contains("test") || processName.contains("Test")) {
                return false;
            } else {
                return true;
            }
        }

        // We can lift these restrictions in the future after we've made it possible for test
        // authors to test Looper and MessageQueue without resorting to reflection.
        return false;
    }

    static void setSkipEpollWaitForZeroTimeout(long ptr) {
        if (sSkipEpollWaitForZeroTimeoutInitialized) {
            return;
        }
        if (Flags.nativeLooperSkipEpollWaitForZeroTimeout()) {
            nativeSetSkipEpollWaitForZeroTimeout(ptr);
        }
        sSkipEpollWaitForZeroTimeoutInitialized = true;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            dispose();
        } finally {
            super.finalize();
        }
    }

    private void decAndTraceMessageCount() {
        mMessageCount.decrementAndGet();
        if (PerfettoTrace.isMQCategoryEnabled()) {
            traceMessageCount();
        }
    }

    private void incAndTraceMessageCount(Message msg, long when) {
        mMessageCount.incrementAndGet();
        if (PerfettoTrace.isMQCategoryEnabled()) {
            msg.sendingThreadName = Thread.currentThread().getName();
            final long eventId = msg.eventId = PerfettoTrace.getFlowId();

            traceMessageCount();
            final long messageDelayMs = Math.max(0L, when - SystemClock.uptimeMillis());
            if (PerfettoTrace.IS_USE_SDK_TRACING_API_V3) {
                com.android.internal.dev.perfetto.sdk.PerfettoTrace.instant(
                                PerfettoTrace.MQ_CATEGORY_V3, "message_queue_send")
                        .setFlow(eventId)
                        .beginProto()
                        .beginNested(2004 /* message_queue */)
                        .addField(2 /* receiving_thread_name */, mThreadName)
                        .addField(3 /* message_code */, msg.what)
                        .addField(4 /* message_delay_ms */, messageDelayMs)
                        .endNested()
                        .endProto()
                        .emit();
            } else {
                PerfettoTrace.instant(PerfettoTrace.MQ_CATEGORY, "message_queue_send")
                        .setFlow(eventId)
                        .beginProto()
                        .beginNested(2004 /* message_queue */)
                        .addField(2 /* receiving_thread_name */, mThreadName)
                        .addField(3 /* message_code */, msg.what)
                        .addField(4 /* message_delay_ms */, messageDelayMs)
                        .endNested()
                        .endProto()
                        .emit();
            }
        }
    }

    private void traceMessageCount() {
        if (PerfettoTrace.IS_USE_SDK_TRACING_API_V3) {
            com.android.internal.dev.perfetto.sdk.PerfettoTrace.counter(
                            PerfettoTrace.MQ_CATEGORY_V3, mMessageCount.get())
                    .usingThreadCounterTrack(mTid, mThreadName)
                    .emit();
        } else {
            PerfettoTrace.counter(PerfettoTrace.MQ_CATEGORY, mMessageCount.get())
                    .usingThreadCounterTrack(mTid, mThreadName)
                    .emit();
        }
    }

    // Disposes of the underlying message queue.
    // Must only be called on the looper thread or the finalizer.
    private void dispose() {
        if (mPtr != 0) {
            nativeDestroy(mPtr);
            mPtr = 0;
        }
    }

    static final class EnqueueOrder implements Comparator<Message> {
        @Override
        public int compare(Message m1, Message m2) {
            return Message.compareMessages(m1, m2);
        }
    }

    private static final EnqueueOrder sEnqueueOrder = new EnqueueOrder();


    private static boolean isBarrier(Message msg) {
        return msg != null && msg.target == null;
    }

    private boolean isIdleConcurrent() {
        final long now = SystemClock.uptimeMillis();

        if (stackHasMessages(null, 0, null, null, now, sMatchDeliverableMessages, false)) {
            return false;
        }

        final Message msg = first(mPriorityQueue);
        if (msg != null && msg.when <= now) {
            return false;
        }

        final Message asyncMsg = first(mAsyncPriorityQueue);
        if (asyncMsg != null && asyncMsg.when <= now) {
            return false;
        }

        return true;
    }

    private boolean isIdleLegacy() {
        synchronized (this) {
            final long now = SystemClock.uptimeMillis();
            return mMessages == null || now < mMessages.when;
        }
    }

    /**
     * Returns true if the looper has no pending messages which are due to be processed
     * and is not blocked on a sync barrier.
     *
     * <p>This method is safe to call from any thread.
     *
     * @return True if the looper is idle.
     */
    public boolean isIdle() {
        if (sUseConcurrent) {
            return isIdleConcurrent();
        } else {
            return isIdleLegacy();
        }
    }

    private void addIdleHandlerConcurrent(@NonNull IdleHandler handler) {
        synchronized (mIdleHandlersLock) {
            mIdleHandlers.add(handler);
        }
    }

    private void addIdleHandlerLegacy(@NonNull IdleHandler handler) {
        synchronized (this) {
            mIdleHandlers.add(handler);
        }
    }

    /**
     * Add a new {@link IdleHandler} to this message queue.  This may be
     * removed automatically for you by returning false from
     * {@link IdleHandler#queueIdle IdleHandler.queueIdle()} when it is
     * invoked, or explicitly removing it with {@link #removeIdleHandler}.
     *
     * <p>This method is safe to call from any thread.
     *
     * @param handler The IdleHandler to be added.
     */
    public void addIdleHandler(@NonNull IdleHandler handler) {
        if (handler == null) {
            throw new NullPointerException("Can't add a null IdleHandler");
        }
        if (sUseConcurrent) {
            addIdleHandlerConcurrent(handler);
        } else {
            addIdleHandlerLegacy(handler);
        }
    }
    private void removeIdleHandlerConcurrent(@NonNull IdleHandler handler) {
        synchronized (mIdleHandlersLock) {
            mIdleHandlers.remove(handler);
        }
    }
    private void removeIdleHandlerLegacy(@NonNull IdleHandler handler) {
        synchronized (this) {
            mIdleHandlers.remove(handler);
        }
    }

    /**
     * Remove an {@link IdleHandler} from the queue that was previously added
     * with {@link #addIdleHandler}.  If the given object is not currently
     * in the idle list, nothing is done.
     *
     * <p>This method is safe to call from any thread.
     *
     * @param handler The IdleHandler to be removed.
     */
    public void removeIdleHandler(@NonNull IdleHandler handler) {
        if (sUseConcurrent) {
            removeIdleHandlerConcurrent(handler);
        } else {
            removeIdleHandlerLegacy(handler);
        }
    }

    private boolean isPollingConcurrent() {
        // If the loop is quitting then it must not be idling.
        if (!getQuitting() && incrementMptrRefs()) {
            try {
                return nativeIsPolling(mPtr);
            } finally {
                decrementMptrRefs();
            }
        }
        return false;
    }

    private boolean isPollingLegacy() {
        synchronized (this) {
            return isPollingLocked();
        }
    }

    /**
     * Returns whether this looper's thread is currently polling for more work to do.
     * This is a good signal that the loop is still alive rather than being stuck
     * handling a callback.  Note that this method is intrinsically racy, since the
     * state of the loop can change before you get the result back.
     *
     * <p>This method is safe to call from any thread.
     *
     * @return True if the looper is currently polling for events.
     * @hide
     */
    public boolean isPolling() {
        if (sUseConcurrent) {
            return isPollingConcurrent();
        } else {
            return isPollingLegacy();
        }
    }

    private boolean isPollingLocked() {
        // If the loop is quitting then it must not be idling.
        // We can assume mPtr != 0 when mQuitting is false.
        return !mQuitting && nativeIsPolling(mPtr);
    }
    private void addOnFileDescriptorEventListenerConcurrent(@NonNull FileDescriptor fd,
            @OnFileDescriptorEventListener.Events int events,
            @NonNull OnFileDescriptorEventListener listener) {
        synchronized (mFileDescriptorRecordsLock) {
            updateOnFileDescriptorEventListenerLocked(fd, events, listener);
        }
    }

    private void addOnFileDescriptorEventListenerLegacy(@NonNull FileDescriptor fd,
            @OnFileDescriptorEventListener.Events int events,
            @NonNull OnFileDescriptorEventListener listener) {
        synchronized (this) {
            updateOnFileDescriptorEventListenerLocked(fd, events, listener);
        }
    }

    /**
     * Adds a file descriptor listener to receive notification when file descriptor
     * related events occur.
     * <p>
     * If the file descriptor has already been registered, the specified events
     * and listener will replace any that were previously associated with it.
     * It is not possible to set more than one listener per file descriptor.
     * </p><p>
     * It is important to always unregister the listener when the file descriptor
     * is no longer of use.
     * </p>
     *
     * @param fd The file descriptor for which a listener will be registered.
     * @param events The set of events to receive: a combination of the
     * {@link OnFileDescriptorEventListener#EVENT_INPUT},
     * {@link OnFileDescriptorEventListener#EVENT_OUTPUT}, and
     * {@link OnFileDescriptorEventListener#EVENT_ERROR} event masks.  If the requested
     * set of events is zero, then the listener is unregistered.
     * @param listener The listener to invoke when file descriptor events occur.
     *
     * @see OnFileDescriptorEventListener
     * @see #removeOnFileDescriptorEventListener
     */
    @RavenwoodThrow(blockedBy = android.os.ParcelFileDescriptor.class)
    public void addOnFileDescriptorEventListener(@NonNull FileDescriptor fd,
            @OnFileDescriptorEventListener.Events int events,
            @NonNull OnFileDescriptorEventListener listener) {
        if (fd == null) {
            throw new IllegalArgumentException("fd must not be null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }

        if (sUseConcurrent) {
            addOnFileDescriptorEventListenerConcurrent(fd, events, listener);
        } else {
            addOnFileDescriptorEventListenerLegacy(fd, events, listener);
        }
    }

    private void removeOnFileDescriptorEventListenerConcurrent(@NonNull FileDescriptor fd) {
        synchronized (mFileDescriptorRecordsLock) {
            updateOnFileDescriptorEventListenerLocked(fd, 0, null);
        }
    }

    private void removeOnFileDescriptorEventListenerLegacy(@NonNull FileDescriptor fd) {
        synchronized (this) {
            updateOnFileDescriptorEventListenerLocked(fd, 0, null);
        }
    }

    /**
     * Removes a file descriptor listener.
     * <p>
     * This method does nothing if no listener has been registered for the
     * specified file descriptor.
     * </p>
     *
     * @param fd The file descriptor whose listener will be unregistered.
     *
     * @see OnFileDescriptorEventListener
     * @see #addOnFileDescriptorEventListener
     */
    @RavenwoodThrow(blockedBy = android.os.ParcelFileDescriptor.class)
    public void removeOnFileDescriptorEventListener(@NonNull FileDescriptor fd) {
        if (fd == null) {
            throw new IllegalArgumentException("fd must not be null");
        }
        if (sUseConcurrent) {
            removeOnFileDescriptorEventListenerConcurrent(fd);
        } else {
            removeOnFileDescriptorEventListenerLegacy(fd);
        }
    }

    @RavenwoodThrow(blockedBy = android.os.ParcelFileDescriptor.class)
    private void updateOnFileDescriptorEventListenerLocked(FileDescriptor fd, int events,
            OnFileDescriptorEventListener listener) {
        final int fdNum = fd.getInt$();

        int index = -1;
        FileDescriptorRecord record = null;
        if (mFileDescriptorRecords != null) {
            index = mFileDescriptorRecords.indexOfKey(fdNum);
            if (index >= 0) {
                record = mFileDescriptorRecords.valueAt(index);
                if (record != null && record.mEvents == events) {
                    return;
                }
            }
        }

        if (events != 0) {
            events |= OnFileDescriptorEventListener.EVENT_ERROR;
            if (record == null) {
                if (mFileDescriptorRecords == null) {
                    mFileDescriptorRecords = new SparseArray<FileDescriptorRecord>();
                }
                record = new FileDescriptorRecord(fd, events, listener);
                mFileDescriptorRecords.put(fdNum, record);
            } else {
                record.mListener = listener;
                record.mEvents = events;
                record.mSeq += 1;
            }
            setFileDescriptorEvents(fdNum, events);
        } else if (record != null) {
            record.mEvents = 0;
            mFileDescriptorRecords.removeAt(index);

            setFileDescriptorEvents(fdNum, 0);
        }
    }

    // Called from native code.
    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    private int dispatchEvents(int fd, int events) {
        // Get the file descriptor record and any state that might change.
        final FileDescriptorRecord record;
        final int oldWatchedEvents;
        final OnFileDescriptorEventListener listener;
        final int seq;
        if (sUseConcurrent) {
            synchronized (mFileDescriptorRecordsLock) {
                record = mFileDescriptorRecords.get(fd);
                if (record == null) {
                    return 0; // spurious, no listener registered
                }

                oldWatchedEvents = record.mEvents;
                events &= oldWatchedEvents; // filter events based on current watched set
                if (events == 0) {
                    return oldWatchedEvents; // spurious, watched events changed
                }

                listener = record.mListener;
                seq = record.mSeq;
            }
        } else {
            synchronized (this) {
                record = mFileDescriptorRecords.get(fd);
                if (record == null) {
                    return 0; // spurious, no listener registered
                }

                oldWatchedEvents = record.mEvents;
                events &= oldWatchedEvents; // filter events based on current watched set
                if (events == 0) {
                    return oldWatchedEvents; // spurious, watched events changed
                }

                listener = record.mListener;
                seq = record.mSeq;
            }
        }
        // Invoke the listener outside of the lock.
        int newWatchedEvents = listener.onFileDescriptorEvents(
                record.mDescriptor, events);
        if (newWatchedEvents != 0) {
            newWatchedEvents |= OnFileDescriptorEventListener.EVENT_ERROR;
        }

        // Update the file descriptor record if the listener changed the set of
        // events to watch and the listener itself hasn't been updated since.
        if (newWatchedEvents != oldWatchedEvents) {
            if (sUseConcurrent) {
                synchronized (mFileDescriptorRecordsLock) {
                    int index = mFileDescriptorRecords.indexOfKey(fd);
                    if (index >= 0 && mFileDescriptorRecords.valueAt(index) == record
                            && record.mSeq == seq) {
                        record.mEvents = newWatchedEvents;
                        if (newWatchedEvents == 0) {
                            mFileDescriptorRecords.removeAt(index);
                        }
                    }
                }
            } else {
                synchronized (this) {
                    int index = mFileDescriptorRecords.indexOfKey(fd);
                    if (index >= 0 && mFileDescriptorRecords.valueAt(index) == record
                            && record.mSeq == seq) {
                        record.mEvents = newWatchedEvents;
                        if (newWatchedEvents == 0) {
                            mFileDescriptorRecords.removeAt(index);
                        }
                    }
                }
            }
        }

        // Return the new set of events to watch for native code to take care of.
        return newWatchedEvents;
    }

    private static final AtomicLong mMessagesDelivered = new AtomicLong();

    /* These are only read/written from the Looper thread. For use with Concurrent MQ */
    private int mNextPollTimeoutMillis;
    private boolean mMessageDirectlyQueued;
    private boolean mWorkerShouldQuit;
    private Message nextMessage(boolean peek, boolean returnEarliest) {
        int i = 0;

        while (true) {
            if (DEBUG) {
                Log.d(TAG_C, "nextMessage loop #" + i++);
            }

            mDrainingLock.lock();
            try {
                mNextIsDrainingStack = true;
            } finally {
                mDrainingLock.unlock();
            }

            StackNode oldTop;
            QuittingNode quittingNode = null;
            /*
             * Set our state to active, drain any items from the stack into our priority queues.
             * If we are quitting we won't swap away the stack as we want to retain the quitting
             * node for enqueue and remove to see.
             */
            oldTop = swapAndSetStackStateActive();
            boolean shouldRemoveMessages = false;
            if (oldTop.isQuittingNode()) {
                quittingNode = (QuittingNode) oldTop;
                if (!mWorkerShouldQuit) {
                    mWorkerShouldQuit = true;
                    /*
                     * Only remove messages from the queue the first time we encounter a quitting
                     * node, to avoid O(n^2) runtime if we quit safely and there's a lot of nodes
                     * in the queue.
                     */
                    shouldRemoveMessages = true;
                }
            }
            drainStack(oldTop);

            mDrainingLock.lock();
            try {
                mNextIsDrainingStack = false;
                mDrainCompleted.signalAll();
            } finally {
                mDrainingLock.unlock();
            }

            if (shouldRemoveMessages) {
                if (quittingNode.mRemoveAll) {
                    removeAllMessages();
                } else {
                    removeAllFutureMessages(quittingNode.mTS);
                }
            }

            /*
             * The objective of this next block of code is to:
             *  - find a message to return (if any is ready)
             *  - find a next message we would like to return, after scheduling.
             *     - we make our scheduling decision based on this next message (if it exists).
             *
             * We have two queues to juggle and the presence of barriers throws an additional
             * wrench into our plans.
             *
             * The last wrinkle is that remove() may delete items from underneath us. If we hit
             * that case, we simply restart the loop.
             */

            /* Get the first node from each queue */
            Message msg = first(mPriorityQueue);
            Message asyncMsg = first(mAsyncPriorityQueue);
            final long now = SystemClock.uptimeMillis();

            if (DEBUG) {
                if (msg != null) {
                    Log.d(TAG_C, "Next found node"
                            + " what: " + msg.what
                            + " when: " + msg.when
                            + " seq: " + msg.insertSeq
                            + " barrier: " + isBarrier(msg)
                            + " now: " + now);
                }
                if (asyncMsg != null) {
                    Log.d(TAG_C, "Next found async node"
                            + " what: " + asyncMsg.what
                            + " when: " + asyncMsg.when
                            + " seq: " + asyncMsg.insertSeq
                            + " barrier: " + isBarrier(asyncMsg)
                            + " now: " + now);
                }
            }

            /*
             * the node which we will return, null if none are ready
             */
            Message found = null;
            /*
             * The node from which we will determine our next wakeup time.
             * Null indicates there is no next message ready. If we found a node,
             * we can leave this null as Looper will call us again after delivering
             * the message.
             */
            Message next = null;

            /*
             * If we have a barrier we should return the async node (if it exists and is ready)
             */
            if (isBarrier(msg)) {
                if (asyncMsg != null && (returnEarliest || now >= asyncMsg.when)) {
                    found = asyncMsg;
                } else {
                    next = asyncMsg;
                }
            } else { /* No barrier. */
                // Pick the earliest of the next sync and async messages, if any.
                Message earliest = msg;
                if (msg == null) {
                    earliest = asyncMsg;
                } else if (asyncMsg != null) {
                    if (Message.compareMessages(msg, asyncMsg) > 0) {
                        earliest = asyncMsg;
                    }
                }

                if (earliest != null) {
                    if (returnEarliest || now >= earliest.when) {
                        found = earliest;
                    } else {
                        next = earliest;
                    }
                }
            }

            if (DEBUG) {
                if (found != null) {
                    Log.d(TAG_C, "Will deliver node"
                            + " what: " + found.what
                            + " when: " + found.when
                            + " seq: " + found.insertSeq
                            + " barrier: " + isBarrier(found)
                            + " async: " + found.isAsynchronous()
                            + " now: " + now);
                } else {
                    Log.d(TAG_C, "No node to deliver");
                }
                if (next != null) {
                    Log.d(TAG_C, "Next node"
                            + " what: " + next.what
                            + " when: " + next.when
                            + " seq: " + next.insertSeq
                            + " barrier: " + isBarrier(next)
                            + " async: " + next.isAsynchronous()
                            + " now: " + now);
                } else {
                    Log.d(TAG_C, "No next node");
                }
            }

            /*
             * If we have a found message, we will get called again so there's no need to set state.
             * In that case we can leave our state as ACTIVE.
             *
             * Otherwise we should determine how to park the thread.
             */
            StateNode nextOp = sStackStateActive;
            if (found == null) {
                if (mWorkerShouldQuit) {
                    // Set to zero so we can keep looping and finding messages until we're done.
                    mNextPollTimeoutMillis = 0;
                } else if (next == null) {
                    /* No message to deliver, sleep indefinitely */
                    mNextPollTimeoutMillis = -1;
                    nextOp = sStackStateParked;
                    if (DEBUG) {
                        Log.d(TAG_C, "nextMessage next state is StackStateParked");
                    }
                } else {
                    /* Message not ready, or we found one to deliver already, set a timeout */
                    long nextMessageWhen = next.when;
                    if (nextMessageWhen > now) {
                        mNextPollTimeoutMillis = (int) Math.min(nextMessageWhen - now,
                                Integer.MAX_VALUE);
                    } else {
                        mNextPollTimeoutMillis = 0;
                    }

                    mStackStateTimedPark.mWhenToWake = now + mNextPollTimeoutMillis;
                    nextOp = mStackStateTimedPark;
                    if (DEBUG) {
                        Log.d(TAG_C, "nextMessage next state is StackStateTimedParked"
                                + " timeout ms " + mNextPollTimeoutMillis
                                + " mWhenToWake: " + mStackStateTimedPark.mWhenToWake
                                + " now: " + now);
                    }
                }
            }

            /*
             * Try to swap our state from Active back to Park or TimedPark. If we raced with
             * enqueue, loop back around to pick up any new items.
             */
            if (mWorkerShouldQuit || sState.compareAndSet(this, sStackStateActive, nextOp)) {
                mMessageCounts.clearCounts();
                if (found != null) {
                    if (!peek && !removeFromPriorityQueue(found)) {
                        /*
                         * RemoveMessages() might be able to pull messages out from under us
                         * However we can detect that here and just loop around if it happens.
                         */
                        continue;
                    }

                    return found;
                }
                return null;
            }
        }
    }

    private Message nextConcurrent() {
        final long ptr = mPtr;
        if (ptr == 0) {
            return null;
        }

        mNextPollTimeoutMillis = 0;
        int pendingIdleHandlerCount = -1; // -1 only during first iteration
        while (true) {
            if (mNextPollTimeoutMillis != 0) {
                Binder.flushPendingCommands();
            }

            mMessageDirectlyQueued = false;
            nativePollOnce(ptr, mNextPollTimeoutMillis);

            Message msg = nextMessage(false, false);
            if (msg != null) {
                msg.markInUse();
                decAndTraceMessageCount();
                return msg;
            }

            // Prevent any race between quit()/nativeWake() and dispose()
            if (mWorkerShouldQuit) {
                setMptrTeardownAndWaitForRefsToDrop();
                dispose();
                return null;
            }

            synchronized (mIdleHandlersLock) {
                // If first time idle, then get the number of idlers to run.
                // Idle handles only run if the queue is empty or if the first message
                // in the queue (possibly a barrier) is due to be handled in the future.
                if (pendingIdleHandlerCount < 0
                        && isIdle()) {
                    pendingIdleHandlerCount = mIdleHandlers.size();
                }
                if (pendingIdleHandlerCount <= 0) {
                    // No idle handlers to run.  Loop and wait some more.
                    continue;
                }

                if (mPendingIdleHandlers == null) {
                    mPendingIdleHandlers = new IdleHandler[Math.max(pendingIdleHandlerCount, 4)];
                }
                mPendingIdleHandlers = mIdleHandlers.toArray(mPendingIdleHandlers);
            }

            // Run the idle handlers.
            // We only ever reach this code block during the first iteration.
            for (int i = 0; i < pendingIdleHandlerCount; i++) {
                final IdleHandler idler = mPendingIdleHandlers[i];
                mPendingIdleHandlers[i] = null; // release the reference to the handler

                boolean keep = false;
                try {
                    keep = idler.queueIdle();
                } catch (Throwable t) {
                    Log.wtf(TAG_C, "IdleHandler threw exception", t);
                }

                if (!keep) {
                    synchronized (mIdleHandlersLock) {
                        mIdleHandlers.remove(idler);
                    }
                }
            }

            // Reset the idle handler count to 0 so we do not run them again.
            pendingIdleHandlerCount = 0;

            // While calling an idle handler, a new message could have been delivered
            // so go back and look again for a pending message without waiting.
            mNextPollTimeoutMillis = 0;
        }
    }

    private Message nextLegacy() {
        // Return here if the message loop has already quit and been disposed.
        // This can happen if the application tries to restart a looper after quit
        // which is not supported.
        final long ptr = mPtr;
        if (ptr == 0) {
            return null;
        }

        int pendingIdleHandlerCount = -1; // -1 only during first iteration
        int nextPollTimeoutMillis = 0;
        for (;;) {
            if (nextPollTimeoutMillis != 0) {
                Binder.flushPendingCommands();
            }

            nativePollOnce(ptr, nextPollTimeoutMillis);

            synchronized (this) {
                // Try to retrieve the next message.  Return if found.
                final long now = SystemClock.uptimeMillis();
                Message prevMsg = null;
                Message msg = mMessages;
                if (msg != null && msg.target == null) {
                    // Stalled by a barrier.  Find the next asynchronous message in the queue.
                    do {
                        prevMsg = msg;
                        msg = msg.next;
                    } while (msg != null && !msg.isAsynchronous());
                }
                if (msg != null) {
                    if (now < msg.when) {
                        // Next message is not ready.  Set a timeout to wake up when it is ready.
                        nextPollTimeoutMillis = (int) Math.min(msg.when - now, Integer.MAX_VALUE);
                    } else {
                        // Got a message.
                        mBlocked = false;
                        if (prevMsg != null) {
                            prevMsg.next = msg.next;
                            if (prevMsg.next == null) {
                                mLast = prevMsg;
                            }
                        } else {
                            mMessages = msg.next;
                            if (msg.next == null) {
                                mLast = null;
                            }
                        }
                        msg.next = null;
                        if (DEBUG) Log.v(TAG_L, "Returning message: " + msg);
                        msg.markInUse();
                        if (msg.isAsynchronous()) {
                            mAsyncMessageCount--;
                        }
                        decAndTraceMessageCount();
                        return msg;
                    }
                } else {
                    // No more messages.
                    nextPollTimeoutMillis = -1;
                }

                // Process the quit message now that all pending messages have been handled.
                if (mQuitting) {
                    dispose();
                    return null;
                }

                // If first time idle, then get the number of idlers to run.
                // Idle handles only run if the queue is empty or if the first message
                // in the queue (possibly a barrier) is due to be handled in the future.
                if (pendingIdleHandlerCount < 0
                        && (mMessages == null || now < mMessages.when)) {
                    pendingIdleHandlerCount = mIdleHandlers.size();
                }
                if (pendingIdleHandlerCount <= 0) {
                    // No idle handlers to run.  Loop and wait some more.
                    mBlocked = true;
                    continue;
                }

                if (mPendingIdleHandlers == null) {
                    mPendingIdleHandlers = new IdleHandler[Math.max(pendingIdleHandlerCount, 4)];
                }
                mPendingIdleHandlers = mIdleHandlers.toArray(mPendingIdleHandlers);
            }

            // Run the idle handlers.
            // We only ever reach this code block during the first iteration.
            for (int i = 0; i < pendingIdleHandlerCount; i++) {
                final IdleHandler idler = mPendingIdleHandlers[i];
                mPendingIdleHandlers[i] = null; // release the reference to the handler

                boolean keep = false;
                try {
                    keep = idler.queueIdle();
                } catch (Throwable t) {
                    Log.wtf(TAG_L, "IdleHandler threw exception", t);
                }

                if (!keep) {
                    synchronized (this) {
                        mIdleHandlers.remove(idler);
                    }
                }
            }

            // Reset the idle handler count to 0 so we do not run them again.
            pendingIdleHandlerCount = 0;

            // While calling an idle handler, a new message could have been delivered
            // so go back and look again for a pending message without waiting.
            nextPollTimeoutMillis = 0;
        }
    }

    @UnsupportedAppUsage(
            maxTargetSdk = Build.VERSION_CODES.BAKLAVA,
            publicAlternatives =
                    "To manipulate the queue in Instrumentation tests, use {@link"
                        + " android.os.TestLooperManager}")
    Message next() {
        if (sUseConcurrent) {
            return nextConcurrent();
        } else {
            return nextLegacy();
        }
    }

    /**
     * Returns the last message in the queue in execution order.
     *
     * Caller must ensure that this doesn't race 'next' from the Looper thread.
     * @hide
     */
    public @Nullable Message peekLastMessageForTest() {
        ActivityThread.throwIfNotInstrumenting();
        if (sUseConcurrent) {
            return peekLastMessageConcurrent();
        } else {
            return peekLastMessageLegacy();
        }
    }

    /**
     * Matches no messages, but stores the message with the latest execution time.
     */
    static final class FindLastMessage extends MessageCompare {
        Message lastMsg;
        @Override
        public boolean compareMessage(Message m, Handler h, int what, Object object, Runnable r,
                long when) {
            if (m.target != null && (lastMsg == null || lastMsg.when <= m.when)) {
                lastMsg = m;
            }
            return false;
        }
    }

    private Message peekLastMessageConcurrent() {
        final FindLastMessage findLastMessage = new FindLastMessage();
        findOrRemoveMessages(null, 0, null, null, 0, findLastMessage, false);
        return findLastMessage.lastMsg;
    }

    private Message peekLastMessageLegacy() {
        synchronized (this) {
            Message lastMsg = null;

            Message current = mMessages;
            while (current != null) {
                if (current.target != null && (lastMsg == null || lastMsg.when <= current.when)) {
                    lastMsg = current;
                }
                current = current.next;
            }

            return lastMsg;
        }
    }

    /**
     * Resets this queue's state and allows it to continue being used.
     *
     * @hide
     */
    public void resetForTest() {
        ActivityThread.throwIfNotInstrumenting();
        if (sUseConcurrent) {
            resetConcurrent();
        } else {
            resetLegacy();
        }
    }

    private void resetConcurrent() {
        // This queue is already quitting, so we can't reset its state and continue using it.
        if (getQuitting()) {
            return;
        }
        synchronized (mIdleHandlersLock) {
            mIdleHandlers.clear();
        }
        synchronized (mFileDescriptorRecordsLock) {
            removeAllFdRecords();
        }
        removeAllMessages();

        // We reset the sync barrier tokens to reflect the queue's state reset. This helps ensure
        // that the queue's behavior is deterministic in both individual tests and in a test suite.
        resetSyncBarrierTokens();
    }

    private void resetLegacy() {
        synchronized (this) {
            // This queue is already quitting, so we can't reset its state and continue using it.
            if (mQuitting) {
                return;
            }
            mIdleHandlers.clear();
            removeAllFdRecords();
            removeAllMessagesLocked();
            // We reset the sync barrier tokens to reflect the queue's state reset. This helps
            // ensure that the queue's behavior is deterministic in both individual tests and in a
            // test suite.
            resetSyncBarrierTokens();
            nativeWake(mPtr);
        }
    }

    private void removeAllFdRecords() {
        if (mFileDescriptorRecords != null) {
            while (mFileDescriptorRecords.size() > 0) {
                removeOnFileDescriptorEventListener(mFileDescriptorRecords.valueAt(0).mDescriptor);
            }
        }
    }

    private void resetSyncBarrierTokens() {
        mNextBarrierTokenAtomic.set(1);
        mNextBarrierToken = 0;
    }

    void quit(boolean safe) {
        if (!mQuitAllowed) {
            throw new IllegalStateException("Main thread not allowed to quit.");
        }

        if (sUseConcurrent) {
            QuittingNode quittingNode = new QuittingNode(safe);
            while (true) {
                StackNode old = (StackNode) sState.getVolatile(this);
                if (old.isQuittingNode()) {
                    return;
                }
                quittingNode.mNext = old;
                if (old.isMessageNode()) {
                    quittingNode.mBottomOfStack = ((MessageNode) old).mBottomOfStack;
                } else {
                    quittingNode.mBottomOfStack = (StateNode) old;
                }

                if (sState.compareAndSet(this, old, quittingNode)) {
                    if (incrementMptrRefs()) {
                        try {
                            nativeWake(mPtr);
                        } finally {
                            decrementMptrRefs();
                        }
                    }
                    return;
                }
            }
        } else {
            synchronized (this) {
                if (mQuitting) {
                    return;
                }
                mQuitting = true;

                if (safe) {
                    removeAllFutureMessagesLocked();
                } else {
                    removeAllMessagesLocked();
                }

                // We can assume mPtr != 0 because mQuitting was previously false.
                nativeWake(mPtr);
            }
        }
    }

    private int postSyncBarrierConcurrent() {
        return postSyncBarrier(SystemClock.uptimeMillis());

    }

    private int postSyncBarrierLegacy() {
        return postSyncBarrier(SystemClock.uptimeMillis());
    }

    /**
     * Posts a synchronization barrier to the Looper's message queue.
     *
     * Message processing occurs as usual until the message queue encounters the
     * synchronization barrier that has been posted.  When the barrier is encountered,
     * later synchronous messages in the queue are stalled (prevented from being executed)
     * until the barrier is released by calling {@link #removeSyncBarrier} and specifying
     * the token that identifies the synchronization barrier.
     *
     * This method is used to immediately postpone execution of all subsequently posted
     * synchronous messages until a condition is met that releases the barrier.
     * Asynchronous messages (see {@link Message#isAsynchronous} are exempt from the barrier
     * and continue to be processed as usual.
     *
     * This call must be always matched by a call to {@link #removeSyncBarrier} with
     * the same token to ensure that the message queue resumes normal operation.
     * Otherwise the application will probably hang!
     *
     * @return A token that uniquely identifies the barrier.  This token must be
     * passed to {@link #removeSyncBarrier} to release the barrier.
     *
     * @hide
     */
    @UnsupportedAppUsage
    @TestApi
    public int postSyncBarrier() {
        if (sUseConcurrent) {
            return postSyncBarrierConcurrent();
        } else {
            return postSyncBarrierLegacy();
        }
    }

    private int postSyncBarrier(long when) {
        // Enqueue a new sync barrier token.
        // We don't need to wake the queue because the purpose of a barrier is to stall it.
        if (sUseConcurrent) {
            final int token = mNextBarrierTokenAtomic.getAndIncrement();

            // b/376573804: apps and tests may expect to be able to use reflection
            // to read this value. Make some effort to support this legacy use case.
            mNextBarrierToken = token + 1;

            final Message msg = Message.obtain();

            msg.markInUse();
            msg.arg1 = token;

            if (!enqueueMessageUnchecked(msg, when)) {
                Log.wtf(TAG_C, "Unexpected error while adding sync barrier!");
                return -1;
            }

            return token;
        }

        synchronized (this) {
            final int token = mNextBarrierToken++;
            final Message msg = Message.obtain();
            msg.markInUse();
            msg.when = when;
            msg.arg1 = token;
            incAndTraceMessageCount(msg, when);

            if (mLast != null && mLast.when <= when) {
                /* Message goes to tail of list */
                mLast.next = msg;
                mLast = msg;
                msg.next = null;
                return token;
            }

            Message prev = null;
            Message p = mMessages;
            if (when != 0) {
                while (p != null && p.when <= when) {
                    prev = p;
                    p = p.next;
                }
            }

            if (p == null) {
                /* We reached the tail of the list, or list is empty. */
                mLast = msg;
            }

            if (prev != null) { // invariant: p == prev.next
                msg.next = p;
                prev.next = msg;
            } else {
                msg.next = p;
                mMessages = msg;
            }
            return token;
        }
    }

    private void removeSyncBarrierConcurrent(int token) {
        boolean removed;
        final MatchBarrierToken matchBarrierToken = new MatchBarrierToken(token);

        removed = findOrRemoveMessages(null, 0, null, null, 0, matchBarrierToken, true);
        if (!removed) {
            throw new IllegalStateException("The specified message queue synchronization "
                    + " barrier token has not been posted or has already been removed.");
        }
        if (Thread.currentThread() != mLooperThread) {
            // Wake up next() in case it was sleeping on this barrier.
            concurrentWake();
        }
    }

    private void removeSyncBarrierLegacy(int token) {
        synchronized (this) {
            Message prev = null;
            Message p = mMessages;
            while (p != null && (p.target != null || p.arg1 != token)) {
                prev = p;
                p = p.next;
            }
            if (p == null) {
                throw new IllegalStateException("The specified message queue synchronization "
                        + " barrier token has not been posted or has already been removed.");
            }
            final boolean needWake;
            if (prev != null) {
                prev.next = p.next;
                if (prev.next == null) {
                    mLast = prev;
                }
                needWake = false;
            } else {
                mMessages = p.next;
                if (mMessages == null) {
                    mLast = null;
                }
                needWake = mMessages == null || mMessages.target != null;
            }
            p.recycleUnchecked();
            decAndTraceMessageCount();

            // If the loop is quitting then it is already awake.
            // We can assume mPtr != 0 when mQuitting is false.
            if (needWake && !mQuitting) {
                nativeWake(mPtr);
            }
        }
    }

    /**
     * Removes a synchronization barrier.
     *
     * @param token The synchronization barrier token that was returned by
     * {@link #postSyncBarrier}.
     *
     * @throws IllegalStateException if the barrier was not found.
     *
     * @hide
     */
    @UnsupportedAppUsage
    @TestApi
    public void removeSyncBarrier(int token) {
        // Remove a sync barrier token from the queue.
        // If the queue is no longer stalled by a barrier then wake it.
        if (sUseConcurrent) {
            removeSyncBarrierConcurrent(token);
        } else {
            removeSyncBarrierLegacy(token);
        }

    }

    private boolean enqueueMessageConcurrent(Message msg, long when) {
        if (msg.isInUse()) {
            throw new IllegalStateException(msg + " This message is already in use.");
        }

        return enqueueMessageUnchecked(msg, when);
    }

    @NeverCompile
    private static void logDeadThread(Message msg) {
        IllegalStateException e = new IllegalStateException(
                msg.target + " sending message to a Handler on a dead thread");
        Log.w(TAG, e.getMessage(), e);
        msg.recycleUnchecked();
    }

    private boolean enqueueMessageLegacy(Message msg, long when) {
        synchronized (this) {
            if (msg.isInUse()) {
                throw new IllegalStateException(msg + " This message is already in use.");
            }

            if (mQuitting) {
                logDeadThread(msg);
                return false;
            }

            msg.markInUse();
            msg.when = when;
            incAndTraceMessageCount(msg, when);

            Message p = mMessages;
            boolean needWake;
            if (p == null || when == 0 || when < p.when) {
                // New head, wake up the event queue if blocked.
                msg.next = p;
                mMessages = msg;
                needWake = mBlocked;
                if (p == null) {
                    mLast = mMessages;
                }
            } else {
                // Message is to be inserted at tail or middle of queue. Usually we don't have to
                // wake up the event queue unless there is a barrier at the head of the queue and
                // the message is the earliest asynchronous message in the queue.
                needWake = mBlocked && p.target == null && msg.isAsynchronous();

                // For readability, we split this portion of the function into two blocks based on
                // whether tail tracking is enabled. This has a minor implication for the case
                // where tail tracking is disabled. See the comment below.
                if (when >= mLast.when) {
                    needWake = needWake && mAsyncMessageCount == 0;
                    msg.next = null;
                    mLast.next = msg;
                    mLast = msg;
                } else {
                    // Inserted within the middle of the queue.
                    Message prev;
                    for (;;) {
                        prev = p;
                        p = p.next;
                        if (p == null || when < p.when) {
                            break;
                        }
                        if (needWake && p.isAsynchronous()) {
                            needWake = false;
                        }
                    }
                    if (p == null) {
                        /* Inserting at tail of queue */
                        mLast = msg;
                    }
                    msg.next = p; // invariant: p == prev.next
                    prev.next = msg;
                }
            }

            if (msg.isAsynchronous()) {
                mAsyncMessageCount++;
            }

            // We can assume mPtr != 0 because mQuitting is false.
            if (needWake) {
                nativeWake(mPtr);
            }
        }
        return true;
    }

    boolean enqueueMessage(Message msg, long when) {
        if (msg.target == null) {
            throw new IllegalArgumentException("Message must have a target.");
        }

        if (sUseConcurrent) {
            return enqueueMessageConcurrent(msg, when);
        } else {
            return enqueueMessageLegacy(msg, when);
        }
    }

    private Message legacyPeekOrPoll(boolean peek) {
        synchronized (this) {
            // Try to retrieve the next message.  Return if found.
            final long now = SystemClock.uptimeMillis();
            Message prevMsg = null;
            Message msg = mMessages;
            if (msg != null && msg.target == null) {
                // Stalled by a barrier.  Find the next asynchronous message in the queue.
                do {
                    prevMsg = msg;
                    msg = msg.next;
                } while (msg != null && !msg.isAsynchronous());
            }
            if (msg != null) {
                if (peek) {
                    return msg;
                }
                if (now >= msg.when) {
                    // Got a message.
                    mBlocked = false;
                }
                if (prevMsg != null) {
                    prevMsg.next = msg.next;
                    if (prevMsg.next == null) {
                        mLast = prevMsg;
                    }
                } else {
                    mMessages = msg.next;
                    if (msg.next == null) {
                        mLast = null;
                    }
                }
                msg.next = null;
                msg.markInUse();
                if (msg.isAsynchronous()) {
                    mAsyncMessageCount--;
                }
                decAndTraceMessageCount();
                return msg;
            }
        }
        return null;
    }

    /**
     * Get the timestamp of the next executable message in our priority queue.
     * Returns null if there are no messages ready for delivery.
     *
     * Caller must ensure that this doesn't race 'next' from the Looper thread.
     */
    @SuppressLint("VisiblySynchronized") // Legacy MessageQueue synchronizes on this
    Long peekWhenForTest() {
        ActivityThread.throwIfNotInstrumenting();
        Message ret;
        if (sUseConcurrent) {
            ret = nextMessage(true, true);
        } else {
            ret = legacyPeekOrPoll(true);
        }
        return ret != null ? ret.when : null;
    }

    /**
     * Return the next executable message in our priority queue.
     * Returns null if there are no messages ready for delivery
     *
     * Caller must ensure that this doesn't race 'next' from the Looper thread.
     */
    @SuppressLint("VisiblySynchronized") // Legacy MessageQueue synchronizes on this
    @Nullable
    Message pollForTest() {
        ActivityThread.throwIfNotInstrumenting();
        if (sUseConcurrent) {
            return nextMessage(false, true);
        } else {
            return legacyPeekOrPoll(false);
        }
    }

    /**
     * @return true if we are blocked on a sync barrier
     *
     * Calls to this method must not be allowed to race with `next`.
     * Specifically, the Looper thread must be paused before calling this method,
     * and may not be resumed until after returning from this method.
     */
    boolean isBlockedOnSyncBarrier() {
        ActivityThread.throwIfNotInstrumenting();
        if (sUseConcurrent) {
            // Call nextMessage to get the stack drained into our priority queues
            nextMessage(true, false);
            return (isBarrier(first(mPriorityQueue)));
        } else {
            Message msg = mMessages;
            return msg != null && msg.target == null;
        }
    }

    private boolean hasMessagesConcurrent(Handler h, int what, Object object) {
        return findOrRemoveMessages(h, what, object, null, 0, sMatchHandlerWhatAndObject, false);
    }

    private boolean hasMessagesLegacy(Handler h, int what, Object object) {
        synchronized (this) {
            Message p = mMessages;
            while (p != null) {
                if (p.target == h && p.what == what && (object == null || p.obj == object)) {
                    return true;
                }
                p = p.next;
            }
            return false;
        }
    }

    boolean hasMessages(Handler h, int what, Object object) {
        if (h == null) {
            return false;
        }
        if (sUseConcurrent) {
            return hasMessagesConcurrent(h, what, object);
        } else {
            return hasMessagesLegacy(h, what, object);
        }
    }


    private boolean hasEqualMessagesConcurrent(Handler h, int what, Object object) {
        return findOrRemoveMessages(h, what, object, null, 0, sMatchHandlerWhatAndObjectEquals,
                false);
    }

    private boolean hasEqualMessagesLegacy(Handler h, int what, Object object) {
        synchronized (this) {
            Message p = mMessages;
            while (p != null) {
                if (p.target == h && p.what == what && (object == null || object.equals(p.obj))) {
                    return true;
                }
                p = p.next;
            }
            return false;
        }
    }

    boolean hasEqualMessages(Handler h, int what, Object object) {
        if (h == null) {
            return false;
        }
        if (sUseConcurrent) {
            return hasEqualMessagesConcurrent(h, what, object);
        } else {
            return hasEqualMessagesLegacy(h, what, object);
        }
    }

    private boolean hasMessagesConcurrent(Handler h, Runnable r, Object object) {
        return findOrRemoveMessages(h, -1, object, r, 0, sMatchHandlerRunnableAndObject, false);
    }

    private boolean hasMessagesLegacy(Handler h, Runnable r, Object object) {
        synchronized (this) {
            Message p = mMessages;
            while (p != null) {
                if (p.target == h && p.callback == r && (object == null || p.obj == object)) {
                    return true;
                }
                p = p.next;
            }
            return false;
        }
    }

    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    boolean hasMessages(Handler h, Runnable r, Object object) {
        if (h == null) {
            return false;
        }
        if (sUseConcurrent) {
            return hasMessagesConcurrent(h, r, object);
        } else {
            return hasMessagesLegacy(h, r, object);
        }
    }

    private boolean hasMessagesConcurrent(Handler h) {
        return findOrRemoveMessages(h, -1, null, null, 0, sMatchHandler, false);
    }

    private boolean hasMessagesLegacy(Handler h) {
        synchronized (this) {
            Message p = mMessages;
            while (p != null) {
                if (p.target == h) {
                    return true;
                }
                p = p.next;
            }
            return false;
        }
    }

    boolean hasMessages(Handler h) {
        if (h == null) {
            return false;
        }
        if (sUseConcurrent) {
            return hasMessagesConcurrent(h);
        } else {
            return hasMessagesLegacy(h);
        }
    }

    private void removeMessagesConcurrent(Handler h, int what, Object object) {
        findOrRemoveMessages(h, what, object, null, 0, sMatchHandlerWhatAndObject, true);
    }

    private void removeMessagesLegacy(Handler h, int what, Object object) {
        synchronized (this) {
            Message p = mMessages;

            // Remove all messages at front.
            while (p != null && p.target == h && p.what == what
                   && (object == null || p.obj == object)) {
                Message n = p.next;
                mMessages = n;
                if (p.isAsynchronous()) {
                    mAsyncMessageCount--;
                }
                p.recycleUnchecked();
                decAndTraceMessageCount();
                p = n;
            }

            if (p == null) {
                mLast = mMessages;
            }

            // Remove all messages after front.
            while (p != null) {
                Message n = p.next;
                if (n != null) {
                    if (n.target == h && n.what == what
                            && (object == null || n.obj == object)) {
                        Message nn = n.next;
                        if (n.isAsynchronous()) {
                            mAsyncMessageCount--;
                        }
                        n.recycleUnchecked();
                        decAndTraceMessageCount();
                        p.next = nn;
                        if (p.next == null) {
                            mLast = p;
                        }
                        continue;
                    }
                }
                p = n;
            }
        }
    }

    void removeMessages(Handler h, int what, Object object) {
        if (h == null) {
            return;
        }
        if (sUseConcurrent) {
            removeMessagesConcurrent(h, what, object);
        } else {
            removeMessagesLegacy(h, what, object);
        }
    }

    private void removeEqualMessagesConcurrent(Handler h, int what, Object object) {
            findOrRemoveMessages(h, what, object, null, 0, sMatchHandlerWhatAndObjectEquals,
                    true);
    }

    private void removeEqualMessagesLegacy(Handler h, int what, Object object) {
        synchronized (this) {
            Message p = mMessages;

            // Remove all messages at front.
            while (p != null && p.target == h && p.what == what
                   && (object == null || object.equals(p.obj))) {
                Message n = p.next;
                mMessages = n;
                if (p.isAsynchronous()) {
                    mAsyncMessageCount--;
                }
                p.recycleUnchecked();
                decAndTraceMessageCount();
                p = n;
            }

            if (p == null) {
                mLast = mMessages;
            }

            // Remove all messages after front.
            while (p != null) {
                Message n = p.next;
                if (n != null) {
                    if (n.target == h && n.what == what
                            && (object == null || object.equals(n.obj))) {
                        Message nn = n.next;
                        if (n.isAsynchronous()) {
                            mAsyncMessageCount--;
                        }
                        n.recycleUnchecked();
                        decAndTraceMessageCount();
                        p.next = nn;
                        if (p.next == null) {
                            mLast = p;
                        }
                        continue;
                    }
                }
                p = n;
            }
        }
    }

    void removeEqualMessages(Handler h, int what, Object object) {
        if (h == null) {
            return;
        }

        if (sUseConcurrent) {
            removeEqualMessagesConcurrent(h, what, object);
        } else {
            removeEqualMessagesLegacy(h, what, object);
        }
    }

    private void removeMessagesConcurrent(Handler h, Runnable r, Object object) {
        findOrRemoveMessages(h, -1, object, r, 0, sMatchHandlerRunnableAndObject, true);
    }

    private void removeMessagesLegacy(Handler h, Runnable r, Object object) {
        synchronized (this) {
            Message p = mMessages;

            // Remove all messages at front.
            while (p != null && p.target == h && p.callback == r
                   && (object == null || p.obj == object)) {
                Message n = p.next;
                mMessages = n;
                if (p.isAsynchronous()) {
                    mAsyncMessageCount--;
                }
                p.recycleUnchecked();
                decAndTraceMessageCount();
                p = n;
            }

            if (p == null) {
                mLast = mMessages;
            }

            // Remove all messages after front.
            while (p != null) {
                Message n = p.next;
                if (n != null) {
                    if (n.target == h && n.callback == r
                            && (object == null || n.obj == object)) {
                        Message nn = n.next;
                        if (n.isAsynchronous()) {
                            mAsyncMessageCount--;
                        }
                        n.recycleUnchecked();
                        decAndTraceMessageCount();
                        p.next = nn;
                        if (p.next == null) {
                            mLast = p;
                        }
                        continue;
                    }
                }
                p = n;
            }
        }
    }

    void removeMessages(Handler h, Runnable r, Object object) {
        if (h == null || r == null) {
            return;
        }

        if (sUseConcurrent) {
            removeMessagesConcurrent(h, r, object);
        } else {
            removeMessagesLegacy(h, r, object);
        }
    }

    private void removeEqualMessagesConcurrent(Handler h, Runnable r, Object object) {
        findOrRemoveMessages(h, -1, object, r, 0, sMatchHandlerRunnableAndObjectEquals, true);
    }

    private void removeEqualMessagesLegacy(Handler h, Runnable r, Object object) {
        synchronized (this) {
            Message p = mMessages;

            // Remove all messages at front.
            while (p != null && p.target == h && p.callback == r
                   && (object == null || object.equals(p.obj))) {
                Message n = p.next;
                mMessages = n;
                if (p.isAsynchronous()) {
                    mAsyncMessageCount--;
                }
                p.recycleUnchecked();
                decAndTraceMessageCount();
                p = n;
            }

            if (p == null) {
                mLast = mMessages;
            }

            // Remove all messages after front.
            while (p != null) {
                Message n = p.next;
                if (n != null) {
                    if (n.target == h && n.callback == r
                            && (object == null || object.equals(n.obj))) {
                        Message nn = n.next;
                        if (n.isAsynchronous()) {
                            mAsyncMessageCount--;
                        }
                        n.recycleUnchecked();
                        decAndTraceMessageCount();
                        p.next = nn;
                        if (p.next == null) {
                            mLast = p;
                        }
                        continue;
                    }
                }
                p = n;
            }
        }
    }

    void removeEqualMessages(Handler h, Runnable r, Object object) {
        if (h == null || r == null) {
            return;
        }

        if (sUseConcurrent) {
            removeEqualMessagesConcurrent(h, r, object);
        } else {
            removeEqualMessagesLegacy(h, r, object);
        }
    }

    private void removeCallbacksAndMessagesConcurrent(Handler h, Object object) {
            findOrRemoveMessages(h, -1, object, null, 0, sMatchHandlerAndObject, true);
    }

    private void removeCallbacksAndMessagesLegacy(Handler h, Object object) {
        synchronized (this) {
            Message p = mMessages;

            // Remove all messages at front.
            while (p != null && p.target == h
                    && (object == null || p.obj == object)) {
                Message n = p.next;
                mMessages = n;
                if (p.isAsynchronous()) {
                    mAsyncMessageCount--;
                }
                p.recycleUnchecked();
                decAndTraceMessageCount();
                p = n;
            }

            if (p == null) {
                mLast = mMessages;
            }

            // Remove all messages after front.
            while (p != null) {
                Message n = p.next;
                if (n != null) {
                    if (n.target == h && (object == null || n.obj == object)) {
                        Message nn = n.next;
                        if (n.isAsynchronous()) {
                            mAsyncMessageCount--;
                        }
                        n.recycleUnchecked();
                        decAndTraceMessageCount();
                        p.next = nn;
                        if (p.next == null) {
                            mLast = p;
                        }
                        continue;
                    }
                }
                p = n;
            }
        }
    }

    void removeCallbacksAndMessages(Handler h, Object object) {
        if (h == null) {
            return;
        }

        if (sUseConcurrent) {
            removeCallbacksAndMessagesConcurrent(h, object);
        } else {
            removeCallbacksAndMessagesLegacy(h, object);
        }
    }

    void removeCallbacksAndEqualMessagesConcurrent(Handler h, Object object) {
        findOrRemoveMessages(h, -1, object, null, 0, sMatchHandlerAndObjectEquals, true);
    }

    void removeCallbacksAndEqualMessagesLegacy(Handler h, Object object) {
        synchronized (this) {
            Message p = mMessages;

            // Remove all messages at front.
            while (p != null && p.target == h
                    && (object == null || object.equals(p.obj))) {
                Message n = p.next;
                mMessages = n;
                if (p.isAsynchronous()) {
                    mAsyncMessageCount--;
                }
                p.recycleUnchecked();
                decAndTraceMessageCount();
                p = n;
            }

            if (p == null) {
                mLast = mMessages;
            }

            // Remove all messages after front.
            while (p != null) {
                Message n = p.next;
                if (n != null) {
                    if (n.target == h && (object == null || object.equals(n.obj))) {
                        Message nn = n.next;
                        if (n.isAsynchronous()) {
                            mAsyncMessageCount--;
                        }
                        n.recycleUnchecked();
                        decAndTraceMessageCount();
                        p.next = nn;
                        if (p.next == null) {
                            mLast = p;
                        }
                        continue;
                    }
                }
                p = n;
            }
        }
    }

    void removeCallbacksAndEqualMessages(Handler h, Object object) {
        if (h == null) {
            return;
        }

        if (sUseConcurrent) {
            removeCallbacksAndEqualMessagesConcurrent(h, object);
        } else {
            removeCallbacksAndEqualMessagesLegacy(h, object);
        }
    }

    private void removeAllMessagesLocked() {
        Message p = mMessages;
        while (p != null) {
            Message n = p.next;
            p.recycleUnchecked();
            p = n;
        }
        mMessages = null;
        mLast = null;
        mAsyncMessageCount = 0;
        mMessageCount.set(0);
        traceMessageCount();
    }

    private void removeAllFutureMessagesLocked() {
        final long now = SystemClock.uptimeMillis();
        Message p = mMessages;
        if (p != null) {
            if (p.when > now) {
                removeAllMessagesLocked();
            } else {
                Message n;
                for (;;) {
                    n = p.next;
                    if (n == null) {
                        return;
                    }
                    if (n.when > now) {
                        break;
                    }
                    p = n;
                }
                p.next = null;
                mLast = p;

                do {
                    p = n;
                    n = p.next;
                    if (p.isAsynchronous()) {
                        mAsyncMessageCount--;
                    }
                    p.recycleUnchecked();
                    decAndTraceMessageCount();
                } while (n != null);
            }
        }
    }

    private void removeAllMessages() {
        findOrRemoveMessages(null, -1, null, null, 0, sMatchAllMessages, true);
    }

    private void removeAllFutureMessages(long now) {
        findOrRemoveMessages(null, -1, null, null, now, sMatchAllFutureMessages, true);
    }

    @NeverCompile
    private void printPriorityQueueNodes() {
        Log.d(TAG_C, "* Dump priority queue");
        for (Message msg : mPriorityQueue) {
            Log.d(TAG_C,
                    "** Message what: " + msg.what
                    + " when " + msg.when
                    + " seq: " + msg.insertSeq);
        }
    }

    @NeverCompile
    private int dumpPriorityQueue(ConcurrentSkipListSet<Message> queue, Printer pw,
            String prefix, Handler h, int n) {
        int count = 0;
        long now = SystemClock.uptimeMillis();

        for (Message msg : queue) {
            if (h == null || h == msg.target) {
                pw.println(prefix + "Message " + (n + count) + ": " + msg.toString(now));
            }
            count++;
        }
        return count;
    }

    @NeverCompile
    void dump(Printer pw, String prefix, Handler h) {
        if (sUseConcurrent) {
            long now = SystemClock.uptimeMillis();
            int n = 0;

            pw.println(prefix + "(MessageQueue is using Concurrent implementation)");

            StackNode node = (StackNode) sState.getVolatile(this);
            while (node != null) {
                if (node.isMessageNode()) {
                    Message msg = ((MessageNode) node).mMessage;
                    if (h == null || h == msg.target) {
                        pw.println(prefix + "Message " + n + ": " + msg.toString(now));
                    }
                    node = ((MessageNode) node).mNext;
                } else {
                    pw.println(prefix + "State: " + node);
                    node = null;
                }
                n++;
            }

            pw.println(prefix + "PriorityQueue Messages: ");
            n += dumpPriorityQueue(mPriorityQueue, pw, prefix, h, n);
            pw.println(prefix + "AsyncPriorityQueue Messages: ");
            n += dumpPriorityQueue(mAsyncPriorityQueue, pw, prefix, h, n);

            pw.println(prefix + "(Total messages: " + n + ", polling=" + isPolling()
                    + ", quitting=" + getQuitting() + ")");
            return;
        }

        synchronized (this) {
            pw.println(prefix + "(MessageQueue is using Legacy implementation)");
            long now = SystemClock.uptimeMillis();
            int n = 0;
            for (Message msg = mMessages; msg != null; msg = msg.next) {
                if (h == null || h == msg.target) {
                    pw.println(prefix + "Message " + n + ": " + msg.toString(now));
                }
                n++;
            }
            pw.println(prefix + "(Total messages: " + n + ", polling=" + isPollingLocked()
                    + ", quitting=" + mQuitting + ")");
        }
    }

    @NeverCompile
    private int dumpPriorityQueue(ConcurrentSkipListSet<Message> queue,
            ProtoOutputStream proto) {
        int count = 0;
        for (Message msg : queue) {
            msg.dumpDebug(proto, MessageQueueProto.MESSAGES);
            count++;
        }
        return count;
    }

    @NeverCompile
    void dumpDebug(ProtoOutputStream proto, long fieldId) {
        if (sUseConcurrent) {
            final long messageQueueToken = proto.start(fieldId);

            StackNode node = (StackNode) sState.getVolatile(this);
            while (node.isMessageNode()) {
                Message msg = ((MessageNode) node).mMessage;
                msg.dumpDebug(proto, MessageQueueProto.MESSAGES);
                node = ((MessageNode) node).mNext;
            }

            dumpPriorityQueue(mPriorityQueue, proto);
            dumpPriorityQueue(mAsyncPriorityQueue, proto);

            proto.write(MessageQueueProto.IS_POLLING_LOCKED, isPolling());
            proto.write(MessageQueueProto.IS_QUITTING, getQuitting());
            proto.end(messageQueueToken);
            return;
        }

        final long messageQueueToken = proto.start(fieldId);
        synchronized (this) {
            for (Message msg = mMessages; msg != null; msg = msg.next) {
                msg.dumpDebug(proto, MessageQueueProto.MESSAGES);
            }
            proto.write(MessageQueueProto.IS_POLLING_LOCKED, isPollingLocked());
            proto.write(MessageQueueProto.IS_QUITTING, mQuitting);
        }
        proto.end(messageQueueToken);
    }

    /**
     * Callback interface for discovering when a thread is going to block
     * waiting for more messages.
     */
    public static interface IdleHandler {
        /**
         * Called when the message queue has run out of messages and will now
         * wait for more.  Return true to keep your idle handler active, false
         * to have it removed.  This may be called if there are still messages
         * pending in the queue, but they are all scheduled to be dispatched
         * after the current time.
         */
        boolean queueIdle();
    }

    /**
     * A listener which is invoked when file descriptor related events occur.
     */
    public interface OnFileDescriptorEventListener {
        /**
         * File descriptor event: Indicates that the file descriptor is ready for input
         * operations, such as reading.
         * <p>
         * The listener should read all available data from the file descriptor
         * then return <code>true</code> to keep the listener active or <code>false</code>
         * to remove the listener.
         * </p><p>
         * In the case of a socket, this event may be generated to indicate
         * that there is at least one incoming connection that the listener
         * should accept.
         * </p><p>
         * This event will only be generated if the {@link #EVENT_INPUT} event mask was
         * specified when the listener was added.
         * </p>
         */
        public static final int EVENT_INPUT = 1 << 0;

        /**
         * File descriptor event: Indicates that the file descriptor is ready for output
         * operations, such as writing.
         * <p>
         * The listener should write as much data as it needs.  If it could not
         * write everything at once, then it should return <code>true</code> to
         * keep the listener active.  Otherwise, it should return <code>false</code>
         * to remove the listener then re-register it later when it needs to write
         * something else.
         * </p><p>
         * This event will only be generated if the {@link #EVENT_OUTPUT} event mask was
         * specified when the listener was added.
         * </p>
         */
        public static final int EVENT_OUTPUT = 1 << 1;

        /**
         * File descriptor event: Indicates that the file descriptor encountered a
         * fatal error.
         * <p>
         * File descriptor errors can occur for various reasons.  One common error
         * is when the remote peer of a socket or pipe closes its end of the connection.
         * </p><p>
         * This event may be generated at any time regardless of whether the
         * {@link #EVENT_ERROR} event mask was specified when the listener was added.
         * </p>
         */
        public static final int EVENT_ERROR = 1 << 2;

        /** @hide */
        @Retention(RetentionPolicy.SOURCE)
        @IntDef(flag = true, prefix = { "EVENT_" }, value = {
                EVENT_INPUT,
                EVENT_OUTPUT,
                EVENT_ERROR
        })
        public @interface Events {}

        /**
         * Called when a file descriptor receives events.
         *
         * @param fd The file descriptor.
         * @param events The set of events that occurred: a combination of the
         * {@link #EVENT_INPUT}, {@link #EVENT_OUTPUT}, and {@link #EVENT_ERROR} event masks.
         * @return The new set of events to watch, or 0 to unregister the listener.
         *
         * @see #EVENT_INPUT
         * @see #EVENT_OUTPUT
         * @see #EVENT_ERROR
         */
        @Events int onFileDescriptorEvents(@NonNull FileDescriptor fd, @Events int events);
    }

    static final class FileDescriptorRecord {
        public final FileDescriptor mDescriptor;
        public int mEvents;
        public OnFileDescriptorEventListener mListener;
        public int mSeq;

        public FileDescriptorRecord(FileDescriptor descriptor,
                int events, OnFileDescriptorEventListener listener) {
            mDescriptor = descriptor;
            mEvents = events;
            mListener = listener;
        }
    }

    /**
     * ConcurrentMessageQueue specific classes methods and variables
     */
    /* Helper to choose the correct queue to insert into. */
    private void insertIntoPriorityQueue(Message msg) {
        if (msg.isAsynchronous()) {
            mAsyncPriorityQueue.add(msg);
        } else {
            mPriorityQueue.add(msg);
        }
    }

    private boolean removeFromPriorityQueue(Message msg) {
        if (msg.isAsynchronous()) {
            return mAsyncPriorityQueue.remove(msg);
        } else {
            return mPriorityQueue.remove(msg);
        }
    }

    private static Message first(ConcurrentSkipListSet<Message> queue) {
        // If the queue is empty, avoid calling queue.first() which will allocate
        // an exception that we'll immediately ignore.
        // We might race with another thread that's removing from the queue and
        // end up with the exception anyway, but at least we tried.
        if (queue.isEmpty()) {
            return null;
        }
        try {
            return queue.first();
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    /* Move any non-cancelled messages into the priority queue */
    private void drainStack(StackNode oldTop) {
        QuittingNode quittingNode = oldTop.isQuittingNode() ? (QuittingNode) oldTop : null;
        if (quittingNode != null) {
            oldTop = quittingNode.mNext;
            /*
             * The stack is still visible so we must be careful.
             * Enqueue will only ever see the quitting node so we don't have to worry about races
             * there.
             * Remove may walk the stack but it should be fine to either see the
             * new stack or the old one.
             */
            quittingNode.mNext = quittingNode.mBottomOfStack;
        }
        while (oldTop.isMessageNode()) {
            MessageNode oldTopMessageNode = (MessageNode) oldTop;
            if (oldTopMessageNode.removeFromStack()) {
                insertIntoPriorityQueue(oldTopMessageNode.mMessage);
            }
            MessageNode inserted = oldTopMessageNode;
            oldTop = oldTopMessageNode.mNext;
            /*
             * removeMessages can walk this list while we are consuming it.
             * Set our next pointer to null *after* we add the message to our
             * priority queue. This way removeMessages() will always find the
             * message, either in our list or in the priority queue.
             */
            inserted.mNext = null;
        }
    }

    /**
     *  Set the stack state to Active, return a list of nodes to walk.
     *  If we are already active or quitting simply return the list without swapping.
     *  In the quitting case this will leave the stack state to whatever value it previously had.
     */
    private StackNode swapAndSetStackStateActive() {
        while (true) {
            /* Set stack state to Active, get node list to walk later */
            StackNode current = (StackNode) sState.getVolatile(this);
            if (current == sStackStateActive || current.isQuittingNode()
                    || sState.compareAndSet(this, current, sStackStateActive)) {
                return current;
            }
        }
    }
    private StateNode getStateNode(StackNode node) {
        if (node.isMessageNode()) {
            return ((MessageNode) node).mBottomOfStack;
        }
        if (node.isQuittingNode()) {
            return ((QuittingNode) node).mBottomOfStack;
        }
        return (StateNode) node;
    }

    private void waitForDrainCompleted() {
        mDrainingLock.lock();
        while (mNextIsDrainingStack) {
            mDrainCompleted.awaitUninterruptibly();
        }
        mDrainingLock.unlock();
    }

    @IntDef(value = {
        STACK_NODE_MESSAGE,
        STACK_NODE_ACTIVE,
        STACK_NODE_PARKED,
        STACK_NODE_TIMEDPARK,
        STACK_NODE_QUITTING})
    @Retention(RetentionPolicy.SOURCE)
    private @interface StackNodeType {}

    /*
     * Stack node types. STACK_NODE_MESSAGE indicates a node containing a message.
     * The other types indicate what state our Looper thread is in. The bottom of
     * the stack is always a single state node. Message nodes are added on top.
     */
    private static final int STACK_NODE_MESSAGE = 0;
    /*
     * Active state indicates that next() is processing messages
     */
    private static final int STACK_NODE_ACTIVE = 1;
    /*
     * Parked state indicates that the Looper thread is sleeping indefinitely (nothing to deliver)
     */
    private static final int STACK_NODE_PARKED = 2;
    /*
     * Timed Park state indicates that the Looper thread is sleeping, waiting for a message
     * deadline
     */
    private static final int STACK_NODE_TIMEDPARK = 3;
    /*
     * Tells us that the looper is quitting. Quit() will place this on top of the stack and
     * wake our looper thread. Once a quitting node is on top of the stack, it stays there. If
     * enqueue sees this node it will refuse to queue up new messages. Remove knows to skip a
     * quitting node.
     */
    private static final int STACK_NODE_QUITTING = 4;

    /* Describes a node in the Treiber stack */
    static class StackNode {
        @StackNodeType
        private final int mType;

        StackNode(@StackNodeType int type) {
            mType = type;
        }

        @StackNodeType
        final int getNodeType() {
            return mType;
        }

        final boolean isMessageNode() {
            return mType == STACK_NODE_MESSAGE;
        }

        final boolean isQuittingNode() {
            return mType == STACK_NODE_QUITTING;
        }
    }

    static final class QuittingNode extends StackNode {
        volatile StackNode mNext;
        StateNode mBottomOfStack;
        final boolean mRemoveAll;
        final long mTS;

        QuittingNode(boolean safe) {
            super(STACK_NODE_QUITTING);
            if (safe) {
                mTS = SystemClock.uptimeMillis();
                mRemoveAll = false;
            } else {
                mTS = 0;
                mRemoveAll = true;
            }
        }
    }

    static final class MessageNode extends StackNode {
        final Message mMessage;
        volatile StackNode mNext;
        StateNode mBottomOfStack;
        boolean mWokeUp;
        private static final VarHandle sRemovedFromStack;
        private volatile boolean mRemovedFromStackValue;
        static {
            try {
                // We need to use VarHandle rather than java.util.concurrent.atomic.*
                // for performance reasons. See: b/421437036
                MethodHandles.Lookup l = MethodHandles.lookup();
                sRemovedFromStack = l.findVarHandle(MessageQueue.MessageNode.class,
                        "mRemovedFromStackValue", boolean.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        MessageNode(@NonNull Message message) {
            super(STACK_NODE_MESSAGE);
            mMessage = message;
        }

        boolean removeFromStack() {
            return sRemovedFromStack.compareAndSet(this, false, true);
        }
    }

    static class StateNode extends StackNode {
        StateNode(int type) {
            super(type);
        }
    }

    static final class TimedParkStateNode extends StateNode {
        long mWhenToWake;

        TimedParkStateNode() {
            super(STACK_NODE_TIMEDPARK);
        }
    }

    private static final StateNode sStackStateActive = new StateNode(STACK_NODE_ACTIVE);
    private static final StateNode sStackStateParked = new StateNode(STACK_NODE_PARKED);
    private final TimedParkStateNode mStackStateTimedPark = new TimedParkStateNode();

    /* This is the top of our treiber stack. */
    private static final VarHandle sState;

    private volatile StackNode mStateValue = sStackStateParked;
    private final ConcurrentSkipListSet<Message> mPriorityQueue =
            new ConcurrentSkipListSet<Message>(sEnqueueOrder);
    private final ConcurrentSkipListSet<Message> mAsyncPriorityQueue =
            new ConcurrentSkipListSet<Message>(sEnqueueOrder);

    /*
     * This helps us ensure that messages with the same timestamp are inserted in FIFO order.
     * Increments on each insert, starting at 0. MessageNode.compareTo() will compare sequences
     * when delivery timestamps are identical.
     */
    private static final VarHandle sNextInsertSeq;
    private volatile long mNextInsertSeqValue = 0;
    /*
     * The exception to the FIFO order rule is sendMessageAtFrontOfQueue().
     * Those messages must be in LIFO order.
     * Decrements on each front of queue insert.
     */
    private static final VarHandle sNextFrontInsertSeq;
    private volatile long mNextFrontInsertSeqValue = -1;

    /*
     * Ref count our access to mPtr.
     * next() doesn't want to dispose of mPtr until after quit() is called.
     * isPolling() also needs to ensure safe access to mPtr.
     * So keep a ref count of access to mPtr. If quitting is set, we disallow new refs.
     * next() will only proceed with disposing of the pointer once all refs are dropped.
     */
    private static VarHandle sMptrRefCount;
    private volatile long mMptrRefCountValue = 0;

    static {
        try {
            // We need to use VarHandle rather than java.util.concurrent.atomic.*
            // for performance reasons. See: b/421437036
            MethodHandles.Lookup l = MethodHandles.lookup();
            sState = l.findVarHandle(MessageQueue.class, "mStateValue",
                    MessageQueue.StackNode.class);
            sNextInsertSeq = l.findVarHandle(MessageQueue.class, "mNextInsertSeqValue",
                    long.class);
            sNextFrontInsertSeq = l.findVarHandle(MessageQueue.class, "mNextFrontInsertSeqValue",
                    long.class);
            sMptrRefCount = l.findVarHandle(MessageQueue.class, "mMptrRefCountValue",
                    long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // Use MSB to indicate mPtr teardown state. Lower 63 bits hold ref count.
    private static final long MPTR_TEARDOWN_MASK = 1L << 63;

    /**
     * Increment the mPtr ref count.
     *
     * If this method returns true then the caller may use mPtr until they call
     * {@link #decrementMptrRefs()}.
     * If this method returns false then the caller must not use mPtr, and must
     * instead assume that the MessageQueue is quitting or has already quit and
     * act accordingly.
     */
    private boolean incrementMptrRefs() {
        while (true) {
            final long oldVal = mMptrRefCountValue;
            if ((oldVal & MPTR_TEARDOWN_MASK) != 0) {
                // If we're quitting then we're not allowed to increment the ref count.
                return false;
            }
            if (sMptrRefCount.compareAndSet(this, oldVal, oldVal + 1)) {
                // Successfully incremented the ref count without quitting.
                return true;
            }
        }
    }

    /**
     * Decrement the mPtr ref count.
     *
     * Call after {@link #incrementMptrRefs()} to release the ref on mPtr.
     */
    private void decrementMptrRefs() {
        long oldVal = (long) sMptrRefCount.getAndAdd(this, -1);
        // If quitting and we were the last ref, wake up looper thread
        if (oldVal - 1 == MPTR_TEARDOWN_MASK) {
            LockSupport.unpark(mLooperThread);
        }
    }

    /**
     * Wake the looper thread.
     *
     * {@link #nativeWake(long)} may be called directly only by the looper thread.
     * Otherwise, call this method to ensure safe access to mPtr.
     */
    private void concurrentWake() {
        if (incrementMptrRefs()) {
            try {
                nativeWake(mPtr);
            } finally {
                decrementMptrRefs();
            }
        }
    }

    private void setFileDescriptorEvents(int fdNum, int events) {
        if (sUseConcurrent) {
            if (incrementMptrRefs()) {
                try {
                    nativeSetFileDescriptorEvents(mPtr, fdNum, events);
                } finally {
                    decrementMptrRefs();
                }
            }
        } else {
            nativeSetFileDescriptorEvents(mPtr, fdNum, events);
        }
    }

    private boolean getQuitting() {
        return ((StackNode) sState.getVolatile(this)).isQuittingNode();
    }

    // Must only be called from looper thread
    private void setMptrTeardownAndWaitForRefsToDrop() {
        while (true) {
            final long oldVal = mMptrRefCountValue;
            if (sMptrRefCount.compareAndSet(this, oldVal, oldVal | MPTR_TEARDOWN_MASK)) {
                // Successfully set teardown state.
                break;
            }
        }

        boolean wasInterrupted = false;
        try {
            while ((mMptrRefCountValue & ~MPTR_TEARDOWN_MASK) != 0) {
                LockSupport.park();
                wasInterrupted |= Thread.interrupted();
            }
        } finally {
            if (wasInterrupted) {
                mLooperThread.interrupt();
            }
        }
    }

    /*
     * Tracks the number of queued and cancelled messages in our stack.
     *
     * On item cancellation, determine whether to wake next() to flush tombstoned messages.
     * We track queued and cancelled counts as two ints packed into a single long.
     */
    static final class MessageCounts {
        private static VarHandle sCounts;
        private volatile long mCountsValue = 0;
        static {
            try {
                // We need to use VarHandle rather than java.util.concurrent.atomic.*
                // for performance reasons. See: b/421437036
                MethodHandles.Lookup l = MethodHandles.lookup();
                sCounts = l.findVarHandle(MessageQueue.MessageCounts.class, "mCountsValue",
                        long.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        /* We use a special value to indicate when next() has been woken for flush. */
        private static final long AWAKE = Long.MAX_VALUE;
        /*
         * Minimum number of messages in the stack which we need before we consider flushing
         * tombstoned items.
         */
        private static final int MESSAGE_FLUSH_THRESHOLD = 10;

        private static int numQueued(long val) {
            return (int) (val >>> Integer.SIZE);
        }

        private static int numCancelled(long val) {
            return (int) val;
        }

        private static long combineCounts(int queued, int cancelled) {
            return ((long) queued << Integer.SIZE) | (long) cancelled;
        }

        public void incrementQueued() {
            while (true) {
                long oldVal = mCountsValue;
                int queued = numQueued(oldVal);
                int cancelled = numCancelled(oldVal);
                /* Use Math.max() to avoid overflow of queued count */
                long newVal = combineCounts(Math.max(queued + 1, queued), cancelled);

                /* Don't overwrite 'AWAKE' state */
                if (oldVal == AWAKE || sCounts.compareAndSet(this, oldVal, newVal)) {
                    break;
                }
            }
        }

        public boolean incrementCancelled() {
            while (true) {
                long oldVal = mCountsValue;
                if (oldVal == AWAKE) {
                    return false;
                }
                int queued = numQueued(oldVal);
                int cancelled = numCancelled(oldVal);
                boolean needsPurge = queued > MESSAGE_FLUSH_THRESHOLD
                        && (queued >> 1) < cancelled;
                long newVal;
                if (needsPurge) {
                    newVal = AWAKE;
                } else {
                    newVal = combineCounts(queued,
                            Math.max(cancelled + 1, cancelled));
                }

                if (sCounts.compareAndSet(this, oldVal, newVal)) {
                    return needsPurge;
                }
            }
        }

        public void clearCounts() {
            mCountsValue = 0;
        }
    }

    private final MessageCounts mMessageCounts = new MessageCounts();

    private final Object mIdleHandlersLock = new Object();
    private final Object mFileDescriptorRecordsLock = new Object();

    // The next barrier token.
    // Barriers are indicated by messages with a null target whose arg1 field carries the token.
    private final AtomicInteger mNextBarrierTokenAtomic = new AtomicInteger(1);

    // Must retain this for compatibility reasons.
    @UnsupportedAppUsage
    private int mNextBarrierToken;

    /* Protects mNextIsDrainingStack */
    private final ReentrantLock mDrainingLock = new ReentrantLock();
    private boolean mNextIsDrainingStack = false;
    private final Condition mDrainCompleted = mDrainingLock.newCondition();

    private boolean enqueueMessageUnchecked(@NonNull Message msg, long when) {
        long seq = when != 0 ? ((long) sNextInsertSeq.getAndAdd(this, 1L) + 1L)
                : ((long) sNextFrontInsertSeq.getAndAdd(this, -1L) - 1L);
        msg.when = when;
        msg.insertSeq = seq;
        msg.markInUse();
        incAndTraceMessageCount(msg, when);

        if (DEBUG) {
            Log.d(TAG_C, "Insert message"
                    + " what: " + msg.what
                    + " when: " + msg.when
                    + " seq: " + msg.insertSeq
                    + " barrier: " + isBarrier(msg)
                    + " async: " + msg.isAsynchronous()
                    + " now: " + SystemClock.uptimeMillis());
        }

        /* If we are running on the looper thread we can add directly to the priority queue */
        if (Thread.currentThread() == mLooperThread) {
            if (getQuitting()) {
                logDeadThread(msg);
                return false;
            }

            insertIntoPriorityQueue(msg);
            /*
             * We still need to do this even though we are the current thread,
             * otherwise next() may sleep indefinitely.
             */
            if (!mMessageDirectlyQueued) {
                mMessageDirectlyQueued = true;
                nativeWake(mPtr);
            }
            return true;
        }

        MessageNode node = new MessageNode(msg);
        while (true) {
            StackNode old = (StackNode) sState.getVolatile(this);
            boolean wakeNeeded;
            boolean inactive;

            node.mNext = old;
            switch (old.getNodeType()) {
                case STACK_NODE_ACTIVE:
                    /*
                     * The worker thread is currently active and will process any elements added to
                     * the stack before parking again.
                     */
                    node.mBottomOfStack = (StateNode) old;
                    inactive = false;
                    node.mWokeUp = true;
                    wakeNeeded = false;
                    break;

                case STACK_NODE_PARKED:
                    node.mBottomOfStack = (StateNode) old;
                    inactive = true;
                    node.mWokeUp = true;
                    wakeNeeded = true;
                    break;

                case STACK_NODE_TIMEDPARK:
                    node.mBottomOfStack = (StateNode) old;
                    inactive = true;
                    wakeNeeded = mStackStateTimedPark.mWhenToWake >= msg.when;
                    node.mWokeUp = wakeNeeded;
                    break;

                case STACK_NODE_QUITTING:
                    logDeadThread(msg);
                    decAndTraceMessageCount();
                    return false;

                default:
                    MessageNode oldMessage = (MessageNode) old;

                    node.mBottomOfStack = oldMessage.mBottomOfStack;
                    int bottomType = node.mBottomOfStack.getNodeType();
                    inactive = bottomType >= STACK_NODE_PARKED;
                    wakeNeeded = (bottomType == STACK_NODE_TIMEDPARK
                            && mStackStateTimedPark.mWhenToWake >= node.mMessage.when
                            && !oldMessage.mWokeUp);
                    node.mWokeUp = oldMessage.mWokeUp || wakeNeeded;
                    break;
            }
            if (sState.compareAndSet(this, old, node)) {
                if (inactive) {
                    if (wakeNeeded) {
                        concurrentWake();
                    } else {
                        mMessageCounts.incrementQueued();
                    }
                }
                return true;
            }
        }
    }

    private boolean stackHasMessages(Handler h, int what, Object object, Runnable r, long when,
            MessageCompare compare, boolean removeMatches) {
        boolean found = false;
        StackNode top = (StackNode) sState.getVolatile(this);
        StateNode bottom = getStateNode(top);

        /*
         * If the top node is a state node, there are no reachable messages. We should still
         * wait for next to complete draining the stack.
         */
        if (top == bottom) {
            waitForDrainCompleted();
            return false;
        }

        if (top.isQuittingNode()) {
            QuittingNode quittingNode = (QuittingNode) top;
            StackNode next = quittingNode.mNext;
            if (next.isMessageNode()) {
                top = next;
            } else {
                waitForDrainCompleted();
                return false;
            }
        }
        /*
         * We have messages that we may tombstone. Walk the stack until we hit the bottom or we
         * hit a null pointer.
         * If we hit the bottom, we are done.
         * If we hit a null pointer, then the stack is being consumed by next() and we must cycle
         * until the stack has been drained.
         */
        MessageNode p = (MessageNode) top;

        while (true) {
            final Message msg = p.mMessage;
            if (compare.compareMessage(msg, h, what, object, r, when)) {
                found = true;
                if (DEBUG) {
                    Log.d(TAG_C, "stackHasMessages node matches");
                }
                if (removeMatches) {
                    if (p.removeFromStack()) {
                        msg.clear();
                        decAndTraceMessageCount();
                        if (mMessageCounts.incrementCancelled()) {
                            concurrentWake();
                        }
                    }
                } else {
                    break;
                }
            }

            StackNode n = p.mNext;
            if (n == null) {
                /* Next() is walking the stack, we must re-sample */
                if (DEBUG) {
                    Log.d(TAG_C, "stackHasMessages next() is walking the stack, we must re-sample");
                }
                break;
            }
            if (!n.isMessageNode()) {
                /* We reached the end of the stack */
                break;
            }
            p = (MessageNode) n;
        }

        waitForDrainCompleted();

        return found;
    }

    private boolean priorityQueueHasMessage(ConcurrentSkipListSet<Message> queue, Handler h,
            int what, Object object, Runnable r, long when, MessageCompare compare,
            boolean removeMatches) {
        boolean found = false;
        for (Message msg : queue) {
            if (compare.compareMessage(msg, h, what, object, r, when)) {
                if (removeMatches) {
                    if (queue.remove(msg)) {
                        msg.clear();
                        decAndTraceMessageCount();
                        found = true;
                    }
                } else {
                    return true;
                }
            }
        }
        return found;
    }

    private boolean findOrRemoveMessages(Handler h, int what, Object object, Runnable r, long when,
            MessageCompare compare, boolean removeMatches) {
        boolean foundInStack, foundInQueue;

        foundInStack = stackHasMessages(h, what, object, r, when, compare, removeMatches);
        foundInQueue = priorityQueueHasMessage(mPriorityQueue, h, what, object, r, when, compare,
                removeMatches);
        foundInQueue |= priorityQueueHasMessage(mAsyncPriorityQueue, h, what, object, r, when,
                compare, removeMatches);

        return foundInStack || foundInQueue;
    }

}
