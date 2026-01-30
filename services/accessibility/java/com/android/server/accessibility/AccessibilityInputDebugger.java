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

package com.android.server.accessibility;

import android.util.Slog;
import android.view.InputEvent;

/**
 * Provides debugging capabilities for {@link InputEvent}s processed by an
 * {@link AccessibilityInputFilter}. This class caches recent input events
 * (both received and sent) to assist in diagnosing issues, particularly
 * when an exception occurs during event injection.
 */
public class AccessibilityInputDebugger {

    private static final String TAG = "A11yInputDebugger";

    // Determines the capacity of the recent event caches.
    private static final int RECENT_EVENTS_TO_LOG = 10;

    // Copy of the most recent received input events.
    private final InputEvent[] mRecentReceivedEvents = new InputEvent[RECENT_EVENTS_TO_LOG];
    private int mMostRecentReceivedEventIndex = 0;

    // Copy of the most recent injected input events.
    private final InputEvent[] mRecentSentEvents = new InputEvent[RECENT_EVENTS_TO_LOG];
    private int mMostRecentSentEventIndex = 0;

    /**
     * Records an {@link InputEvent} that has been received by the accessibility input filter.
     */
    void onReceiveEvent(InputEvent event) {
        final int index = (mMostRecentReceivedEventIndex + 1) % RECENT_EVENTS_TO_LOG;
        mMostRecentReceivedEventIndex = index;
        if (mRecentReceivedEvents[index] != null) {
            mRecentReceivedEvents[index].recycle();
        }
        mRecentReceivedEvents[index] = event.copy();
    }

    /**
     * Records an {@link InputEvent} that is about to be sent (injected) by the
     * accessibility input filter.
     */
    void onSendEvent(InputEvent event) {
        final int index = (mMostRecentSentEventIndex + 1) % RECENT_EVENTS_TO_LOG;
        mMostRecentSentEventIndex = index;
        if (mRecentSentEvents[index] != null) {
            mRecentSentEvents[index].recycle();
        }
        mRecentSentEvents[index] = event.copy();
    }

    /**
     * Called when an exception occurs while sending an {@link InputEvent}. This method logs the
     * exception and dumps the cached received and sent events, to aid in debugging the cause of
     * the exception.
     */
    void onSendEventException(Exception exception) {
        Slog.w(TAG, "on accessibility send input event exception: " + exception);
        dumpCachedEvents();
    }

    /**
     * Clears all cached {@link InputEvent}s, releasing their resources.
     */
    void clearCachedEvents() {
        clearReceivedEventCaches();
        clearSentEventCaches();
    }

    private void clearReceivedEventCaches() {
        for (int i = 0; i < RECENT_EVENTS_TO_LOG; i++) {
            if (mRecentReceivedEvents[i] != null) {
                mRecentReceivedEvents[i].recycle();
                mRecentReceivedEvents[i] = null;
            }
        }
        mMostRecentReceivedEventIndex = 0;
    }

    private void clearSentEventCaches() {
        for (int i = 0; i < RECENT_EVENTS_TO_LOG; i++) {
            if (mRecentSentEvents[i] != null) {
                mRecentSentEvents[i].recycle();
                mRecentSentEvents[i] = null;
            }
        }
        mMostRecentSentEventIndex = 0;
    }

    private void dumpCachedEvents() {
        if (RECENT_EVENTS_TO_LOG == 0) {
            return;
        }

        StringBuilder message = new StringBuilder();
        // Recent received events
        message.append("\n  -- recent received events --");
        for (int i = 0; i < RECENT_EVENTS_TO_LOG; i++) {
            final int index = (mMostRecentReceivedEventIndex + RECENT_EVENTS_TO_LOG - i)
                    % RECENT_EVENTS_TO_LOG;
            final InputEvent event = mRecentReceivedEvents[index];
            if (event == null) {
                break;
            }
            message.append("\n  ");
            appendEvent(message, i + 1, event);
        }
        // Recent sent events
        message.append("\n  -- recent sent events --");
        for (int i = 0; i < RECENT_EVENTS_TO_LOG; i++) {
            final int index = (mMostRecentSentEventIndex + RECENT_EVENTS_TO_LOG - i)
                    % RECENT_EVENTS_TO_LOG;
            final InputEvent event = mRecentSentEvents[index];
            if (event == null) {
                break;
            }
            message.append("\n  ");
            appendEvent(message, i + 1, event);
        }
        Slog.w(TAG, message.toString());

        // Clear the caches so the future dumps will not have already printed events
        clearCachedEvents();
    }

    private static void appendEvent(StringBuilder message, int index, InputEvent event) {
        message.append(index).append(": sent at ").append(event.getEventTimeNanos());
        message.append(", ");
        message.append(event);
    }
}
