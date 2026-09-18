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
import android.os.Message;
import android.os.MessageQueue_ravenwood;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

import java.io.PrintStream;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Keeps track of stacktraces where a message is posted.
 */
public final class RavenwoodMessageTracker {
    private static final String TAG = RavenwoodDriver.TAG;
    public static final RavenwoodMessageTracker sInstance = new RavenwoodMessageTracker();

    public static RavenwoodMessageTracker getInstance() {
        return sInstance;
    }

    private RavenwoodMessageTracker() {
    }

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final WeakHashMap<Message, MessageWasPostedHereStackTrace>
            mMessagePosters = new WeakHashMap<>();

    /**
     * Current Message being dispatched for each thread. Using a weak hash instead of a thread
     * local to allow other treads to peek into it.
     */
    @GuardedBy("mCurrentMessage")
    private final WeakHashMap<Thread, Message> mCurrentMessage = new WeakHashMap<>();

    /** Stop tracking {@code msg} */
    public void untrackMessage(@NonNull Message msg) {
        // Log.d(TAG, "untrackMessage: " + messageToString(msg), new Throwable());
        synchronized (mLock) {
            mMessagePosters.remove(Objects.requireNonNull(msg));
        }
    }

    /** Start tracking {@code msg} and remember the current stacktrace. */
    public void trackMessage(@NonNull Message msg) {
        // If we're handling a message, chain the poster of the current message too.
        var here = new MessageWasPostedHereStackTrace(getCurrentMessagePoster());
        here.removeStackTraceUntil(
                (ste) -> ste.getMethodName().equals("enqueueMessage"),
                /*removeMatchingFrame=*/ true);
        synchronized (mLock) {
            mMessagePosters.put(Objects.requireNonNull(msg), here);
        }
    }

    void onDispatchStarted(@NonNull Message msg) {
        synchronized (mCurrentMessage) {
            var cur = mCurrentMessage.get(Thread.currentThread());
            if (cur != null) {
                Log.w(TAG, "onDispatchStarted: current thread is already handling a message");
                return;
            }
            mCurrentMessage.put(Thread.currentThread(), msg);
        }
    }

    void onDispatchFinished(@NonNull Message msg) {
        synchronized (mCurrentMessage) {
            var cur = mCurrentMessage.get(Thread.currentThread());
            if (cur != msg) {
                // If someone is directly calling Handler.dispatch, we may see nest calls.
                return;
            }
            mCurrentMessage.remove(Thread.currentThread());
        }
    }

    @Nullable
    public Message getCurrentMessage() {
        synchronized (mCurrentMessage) {
            return mCurrentMessage.get(Thread.currentThread());
        }
    }

    /** @return the remembered stacktrace for a tracked message. */
    @Nullable
    MessageWasPostedHereStackTrace getPoster(@NonNull Message msg) {
        synchronized (mLock) {
            return mMessagePosters.get(Objects.requireNonNull(msg));
        }
    }

    /** @return the remembered stacktrace for the current message. */
    @Nullable
    MessageWasPostedHereStackTrace getCurrentMessagePoster() {
        var msg = getCurrentMessage();
        if (msg == null) {
            return null;
        }
        synchronized (mLock) {
            return mMessagePosters.get(Objects.requireNonNull(msg));
        }
    }

    /** if {@code msg} is traced, set the remembered stacktrace as a cause to {@code th}.*/
    public void injectPosterAsCause(@NonNull Throwable th, @NonNull Message msg) {
        Objects.requireNonNull(th);
        var poster = getPoster(msg);
        if (poster != null) {
            poster.injectAsCause(th);
        }
    }

    /**
     * Print all pending messages in logcat.
     */
    public void dumpPendingMessages(PrintStream out, String extraMessage) {
        var showPendingMessagePosters = RavenwoodEnvironment.getInstance().getBoolEnvVar(
                "RAVENWOOD_SHOW_PENDING_MESSAGE_POSTERS");
        var max = RavenwoodEnvironment.getInstance().getIntEnvVar(
                "RAVENWOOD_SHOW_PENDING_MESSAGE_MAX", 10);

        synchronized (mLock) {
            MessageQueue_ravenwood.dumpAllSyncBarriers(out);

            var size = mMessagePosters.size();
            if (size == 0) {
                out.println("No pending messages");
                return;
            }
            if (extraMessage != null) {
                out.print(extraMessage);
                out.print(" ");
            }
            out.println("There are still " + size + " message(s) in the queue");
            var i = 0;
            for (var entry : mMessagePosters.entrySet()) {
                if (i >= max) {
                    out.println("(Omitting more pending messages)");
                    break;
                }
                var m = entry.getKey();
                var poster = showPendingMessagePosters ? entry.getValue() : null;

                String thread = "(n/a)";
                var target = m.getTarget();
                if (target != null) {
                    thread = target.getLooper().getThread().getName();
                }

                out.println("Pending message #" + (i++) + ": Thread=" + thread + ", "
                        + messageToString(m));
                if (poster != null) {
                    poster.printStackTrace(out);
                }
            }
        }
    }

    public static String messageToString(Message msg) {
        if (msg == null) {
            return "{null Message}";
        }
        try {
            return msg.hashCode() + "/" + msg;
        } catch (RuntimeException th) {
            // If the message is recycled (and maybe reused) already, Message.toString() could
            // throw.
            return msg.hashCode() + "/[toString() not accessible - recycled?]";
        }
    }
}
