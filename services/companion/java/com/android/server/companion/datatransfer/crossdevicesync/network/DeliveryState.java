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
package com.android.server.companion.datatransfer.crossdevicesync.network;

import android.annotation.Nullable;

import com.android.internal.infra.AndroidFuture;
import com.android.server.companion.datatransfer.crossdevicesync.network.scanner.Scanner.ScanningSession;

/** A class representing a message delivery state. */
class DeliveryState {
    /** Message is in queue and not processed yet. */
    public static final int QUEUED = 0;

    /** Message is waiting for presence. */
    public static final int WAITING_FOR_PRESENCE = 1;

    /** Message is being sent. */
    public static final int SENDING = 2;

    /** Message delivery failed. */
    public static final int DELIVERY_FAILED = 3;

    /** Message is delivered. */
    public static final int DELIVERED = 4;

    /** Message is cancelled. */
    public static final int CANCELLED = 5;

    public int state;
    public long lastStateChangeTimestamp;
    public String reason;
    @Nullable public AndroidFuture<?> messageSendingFuture;
    @Nullable public ScanningSession scanningSession;

    DeliveryState(long now) {
        state = QUEUED;
        lastStateChangeTimestamp = now;
        reason = "message queued";
    }

    /** Sets the delivery state and the reason for the state change. */
    public boolean setState(int state, long now, String reason) {
        if (this.state == state) {
            return false;
        }
        this.state = state;
        this.lastStateChangeTimestamp = now;
        this.reason = reason;
        return true;
    }

    /** Returns {@code true} if the message delivery is considered complete. */
    public boolean isDone() {
        return state == DeliveryState.DELIVERED || state == DeliveryState.CANCELLED;
    }

    @Override
    public String toString() {
        return "DeliveryState{"
                + "state="
                + stateToString(state)
                + ", lastStateChangeTimestamp="
                + lastStateChangeTimestamp
                + ", reason="
                + reason
                + ", hasMessageSendingFuture="
                + (messageSendingFuture != null)
                + ", hasScanningSession="
                + (scanningSession != null)
                + ", isDone="
                + isDone()
                + "}";
    }

    public static String stateToString(int state) {
        return switch (state) {
            case QUEUED -> "QUEUED";
            case WAITING_FOR_PRESENCE -> "WAITING_FOR_PRESENCE";
            case SENDING -> "SENDING";
            case DELIVERED -> "DELIVERED";
            case DELIVERY_FAILED -> "DELIVERY_FAILED";
            case CANCELLED -> "CANCELLED";
            default -> "UNKNOWN(" + state + ")";
        };
    }
}
