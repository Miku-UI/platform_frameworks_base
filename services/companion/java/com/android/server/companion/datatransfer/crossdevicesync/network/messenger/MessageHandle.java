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
package com.android.server.companion.datatransfer.crossdevicesync.network.messenger;

import android.annotation.Nullable;
import android.util.IndentingPrintWriter;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.infra.AndroidFuture;
import com.android.server.companion.datatransfer.crossdevicesync.common.Clock;
import com.android.server.companion.datatransfer.crossdevicesync.common.DebugConfig;
import com.android.server.companion.datatransfer.crossdevicesync.common.Dumpable;
import com.android.server.companion.datatransfer.crossdevicesync.network.model.Message;

/** A handle containing a message and its control state. */
class MessageHandle extends AndroidFuture<Boolean> implements Dumpable {
    private static final String TAG = "MessageHandle";
    private static final boolean DEBUG = DebugConfig.DEBUG_NETWORK;

    public static final int STATE_WAITING_FOR_TRANSPORT = 0;
    public static final int STATE_SENDING = 1;
    public static final int STATE_PENDING_RETRY = 2;
    public static final int STATE_DELIVERED = 3;
    public static final int STATE_FAILED = 4;
    public static final int STATE_CANCELLED = 5;

    private final Object mLock;
    private final Clock mClock;
    private final int mMaxAttempts;
    private final long mHandleId;
    private final String mName;
    @Nullable private final Message mMessage;

    @GuardedBy("mLock")
    private int mState = STATE_WAITING_FOR_TRANSPORT;

    @GuardedBy("mLock")
    private long mLastChangeTimestamp;

    @GuardedBy("mLock")
    private int mFailedAttempts;

    @GuardedBy("mLock")
    @Nullable
    private Throwable mError;

    MessageHandle(
            Object networkLock,
            Clock clock,
            long handleId,
            int maxAttempts,
            String name,
            @Nullable Message message) {
        if (message != null && message.handleId() != handleId) {
            throw new IllegalArgumentException(
                    "Mismatched handle id! handleId="
                            + handleId
                            + ", message.handleId()="
                            + message.handleId());
        }
        mLock = networkLock;
        mClock = clock;
        mHandleId = handleId;
        mMaxAttempts = maxAttempts;
        mLastChangeTimestamp = clock.elapsedRealtime();
        mName = name;
        mMessage = message;
    }

    public long getHandleId() {
        return mHandleId;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        synchronized (mLock) {
            if (isDone()) {
                return false;
            }
            setStateLocked(STATE_CANCELLED, mClock.elapsedRealtime());
        }
        return super.cancel(mayInterruptIfRunning);
    }

    public void noteSendingFailure(long now, @Nullable Throwable error) {
        boolean failed = false;
        synchronized (mLock) {
            if (mState != STATE_SENDING) {
                return;
            }
            mFailedAttempts++;
            if (mFailedAttempts >= mMaxAttempts) {
                mError = error;
                setStateLocked(STATE_FAILED, now);
                failed = true;
            } else {
                setStateLocked(STATE_PENDING_RETRY, now);
            }
        }
        if (failed) {
            completeExceptionally(error != null ? error : new Exception("Delivery failed"));
        }
    }

    public void noteTransportFailure(long now, @Nullable Throwable error) {
        boolean failed = false;
        synchronized (mLock) {
            if (isDone()) {
                return;
            }
            if (mState == STATE_PENDING_RETRY) {
                // Transport failure immediately after a sending failure shouldn't be considered
                // an extra failure attempt. So we don't increment failed attempts.
                setStateLocked(STATE_WAITING_FOR_TRANSPORT, now);
                return;
            }
            mFailedAttempts++;
            if (mFailedAttempts >= mMaxAttempts) {
                mError = error;
                setStateLocked(STATE_FAILED, now);
                failed = true;
            } else {
                setStateLocked(STATE_WAITING_FOR_TRANSPORT, now);
            }
        }
        if (failed) {
            completeExceptionally(error != null ? error : new Exception("Delivery failed"));
        }
    }

    public void noteSending(long now) {
        synchronized (mLock) {
            if (isDone()) {
                return;
            }
            setStateLocked(STATE_SENDING, now);
        }
    }

    public void noteSuccess(long now) {
        synchronized (mLock) {
            if (isDone()) {
                return;
            }
            setStateLocked(STATE_DELIVERED, now);
        }
        complete(true);
    }

    @GuardedBy("mLock")
    private void setStateLocked(int state, long now) {
        if (mState == state) {
            return;
        }
        if (DEBUG) {
            Log.d(
                    TAG,
                    "Message handle \""
                            + mName
                            + "\" has changed its state: "
                            + stateToString(mState)
                            + " -> "
                            + stateToString(state));
        }
        mState = state;
        mLastChangeTimestamp = now;
    }

    public int getState() {
        synchronized (mLock) {
            return mState;
        }
    }

    public long getLastChangeTime() {
        synchronized (mLock) {
            return mLastChangeTimestamp;
        }
    }

    @Nullable
    public Message getMessage() {
        return mMessage;
    }

    private static String stateToString(int state) {
        return switch (state) {
            case STATE_WAITING_FOR_TRANSPORT -> "WAITING_FOR_TRANSPORT";
            case STATE_SENDING -> "SENDING";
            case STATE_PENDING_RETRY -> "PENDING_RETRY";
            case STATE_DELIVERED -> "DELIVERED";
            case STATE_FAILED -> "FAILED";
            case STATE_CANCELLED -> "CANCELLED";
            default -> "UNKNOWN(" + state + ")";
        };
    }

    @Override
    public void dump(IndentingPrintWriter pw) {
        synchronized (mLock) {
            pw.println("MessageHandle:");
            pw.increaseIndent();
            pw.println("id=" + mHandleId);
            pw.println("name=" + mName);
            pw.println("state=" + stateToString(mState));
            pw.println("lastChangeTimestamp=" + mLastChangeTimestamp);
            pw.println("failedAttempts=" + mFailedAttempts);
            pw.println("error=" + mError);
            if (mMessage != null) {
                pw.println("messageSize=" + mMessage.payload().length);
            }
            pw.decreaseIndent();
        }
    }
}
