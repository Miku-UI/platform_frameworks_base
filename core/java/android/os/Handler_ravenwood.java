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
package android.os;

import android.annotation.NonNull;
import android.platform.test.ravenwood.RavenwoodErrorHandler;
import android.platform.test.ravenwood.RavenwoodMessageTracker;
import android.util.Log;

import java.util.ArrayList;

public class Handler_ravenwood {
    private static final String TAG = "Handler_ravenwood";

    private Handler_ravenwood() {
    }

    /**
     * Called by {@link Handler#onBeforeEnqueue(MessageQueue, Message, long)}
     */
    public static void onBeforeEnqueue(@NonNull MessageQueue queue, @NonNull Message msg,
            long uptimeMillis) {
        RavenwoodErrorHandler.onBeforeEnqueue(msg);
    }

    /**
     * Called by {@link Handler#dispatchMessage(Message)}
     */
    public static void dispatchMessage(Handler handler, @NonNull Message msg) {
        RavenwoodErrorHandler.dispatchMessage(handler, msg);
    }

    /**
     * Go through the MessageQueue to force dispatch all messages immediately, and remove
     * existing sync barriers. The reason why we do not directly call resetForTest() is because
     * we still want idle handlers to be called after this message dispatch to unblock waits.
     *
     * WARNING: THIS IS VERY HACKY, but it works good enough for Ravenwood.
     */
    public static void clearMessageQueue(MessageQueue queue) {
        RavenwoodErrorHandler.onWarningDetected("Force clearing message!");
        // Extract all messages immediately and dispatch them all.
        // We do this because other threads may be waiting on a specific message to be dispatched.
        // Because new messages may be enqueued during the dispatch, we collect the entire list
        // of pending messages as a snapshot, and ONLY execute messages in the snapshot.
        var pendingList = new ArrayList<Message>();
        Message pending;
        while ((pending = queue.pollForTest()) != null) {
            pendingList.add(pending);
        }
        pendingList.forEach(msg -> {
            try {
                Log.w(TAG, "clearMessageQueue: force running message: "
                        + RavenwoodMessageTracker.messageToString(msg));
                msg.getTarget().dispatchMessageImpl(msg);
            } catch (Throwable ignored) {
                Log.w(TAG, "clearMessageQueue: exception thrown while force running message",
                        ignored);
            }
        });
        // New messages may be queued, clear all of them.
        while (queue.pollForTest() != null);

        // If the message queue is blocked by sync barrier, we need to remove it.
        if (queue.isBlockedOnSyncBarrier()) {
            // Fetch the latest token to guess the previous token.
            int token = queue.postSyncBarrier();
            queue.removeSyncBarrier(token);
            // Try to remove the previous sync barrier.
            try {
                Log.w(TAG, "clearMessageQueue: Trying to remove sync barrier: " + (token - 1));
                queue.removeSyncBarrier(token - 1);
            } catch (Throwable ignored) {
            }
        }
    }
}
