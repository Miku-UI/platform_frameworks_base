/* Copyright 2025 The Android Open Source Project
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

package com.android.server.audio;

import android.os.Handler;
import android.os.Message;
import android.util.IntArray;
import android.util.Log;

import java.util.Arrays;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

/**
 * Tracks the activity of clients (uids): given a stream of a set of active uids,
 * emits a stream where inactivity is debounced. A client is only considered inactive
 * if it remains absent from the active set for a specified duration.
 *
 * Thread-safety note: Thread safety is the responsibility of the caller. In particular,
 * the intention is for all the logic to occur on the provided handler thread, under the
 * same set/order of locks. Internal safety is maintained due to serialization on the handler.
 */
public class ActivityDebouncer {
    private final Handler mHandler;
    private final int mInactivityMessage;
    private final IntConsumer mUidActiveConsumer;
    private final int mInactivityDebounceMs;
    private final IntArray mActiveUids = new IntArray();

    /**
     * @param handler Handler for scheduling debounce messages
     * @param inactivityMessageWhat Message code that a uid should be considered inactive (object
     * arg is the uid). The client is responsible for handling this message with
     * {@code onClientActiveChanged((Integer) msg.arg, false)}
     * @param uidActiveConsumer called when the client goes active from within {@link #update}
     * @param inactivityDebounceMs debounce period for inactivity messages
     */
    public ActivityDebouncer(Handler handler, int inactivityMessageWhat,
            IntConsumer uidActiveConsumer, int inactivityDebounceMs) {
        mHandler = handler;
        mInactivityMessage = inactivityMessageWhat;
        mUidActiveConsumer = uidActiveConsumer;
        mInactivityDebounceMs = inactivityDebounceMs;
    }

    /**
     * Updates the activity state based on a provided array of currently active UIDs. Should be
     * called from the handler thread, under the same lock sequence as {@code
     * onClientActivityChanged}.
     * @param currentlyActiveUids An **sorted** array of UIDs that are currently active.
     */
    public void update(int[] currentlyActiveUids) {
        // UIDs that just became active
        for (int uid : currentlyActiveUids) {
            if (mActiveUids.indexOf(uid) < 0) {
                // This is a newly active UID.
                mActiveUids.add(uid);
                // Cancel any pending inactivity message for this UID.
                mHandler.removeEqualMessages(mInactivityMessage, Integer.valueOf(uid));
                mUidActiveConsumer.accept(uid);
            }
        }

        // UIDs that may have become inactive
        for (int i = mActiveUids.size() - 1; i >= 0; i--) {
            int uid = mActiveUids.get(i);
            if (Arrays.binarySearch(currentlyActiveUids, uid) < 0) {
                mActiveUids.remove(i);
                // object necessary for correct cancellation
                dispatchInactive(uid);
            }
        }
    }

    /*
     * Doesn't call the listener, but forces a uid to be treated active independent of an update.
     */
    public void simulateActive(int uid) {
        // This uid isn't actually active, so simulate an activity blip
        if (mActiveUids.indexOf(uid) < 0) {
            // Cancel any pending inactivity message for this UID.
            mHandler.removeEqualMessages(mInactivityMessage, Integer.valueOf(uid));
            dispatchInactive(uid);
        }
    }

    private void dispatchInactive(int uid) {
        Message msg = mHandler.obtainMessage(mInactivityMessage, Integer.valueOf(uid));
        mHandler.sendMessageDelayed(msg, mInactivityDebounceMs);
    }
}
