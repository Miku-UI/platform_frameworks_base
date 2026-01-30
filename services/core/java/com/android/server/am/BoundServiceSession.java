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

package com.android.server.am;

import android.app.IBinderSession;
import android.os.IBinder;
import android.os.Trace;
import android.ravenwood.annotation.RavenwoodKeepWholeClass;
import android.util.ArrayMap;
import android.util.IndentingPrintWriter;
import android.util.IntArray;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.IntPair;

import java.lang.ref.WeakReference;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * An implementation of {@link IBinderSession} on top of a {@link ConnectionRecord} that
 * is used to facilitate important binder calls to a bound remote service hosted by a process that
 * is eligible to get frozen by {@link ProcessStateController}.
 *
 * <p>This class simply keeps the count of ongoing transactions over a bound service's binder and
 * notifies {@link ProcessStateController} when the count changes to or from 0.
 */
@RavenwoodKeepWholeClass
public class BoundServiceSession implements IBinderSession {
    private static final String TAG = BoundServiceSession.class.getSimpleName();
    private static final int MAGIC_ID = 0xFBD_5E55;
    private static final String TRACE_TRACK = "bound_service_calls";

    /** Tag to use for all tags after we have {@link #MAX_UNIQUE_TAGS} tags within the session. */
    @VisibleForTesting
    static final String OVERFLOW_TAG = "_overflow_tags";
    /** Any new tags after this limit will be clubbed together under {@link #OVERFLOW_TAG}. */
    @VisibleForTesting
    static final int MAX_UNIQUE_TAGS = 127;

    // We don't hold a strong reference in case this object is held on for a long time after the
    // binding has gone away. This helps us easily avoid leaks and excess OomAdjuster updates
    // while remaining agnostic to binding state changes. This is also a convenient long-term choice
    // for when we enable a BinderProxy pointing to this from outside of the system process.
    private final WeakReference<ConnectionRecord> mConnection;
    private final BiConsumer<ConnectionRecord, Boolean> mProcessStateUpdater;
    private final String mDebugName;

    /**
     * For each unique tag, we generate a stable key which is simply the index of the counter
     * maintained in {@link #mCountByKey} array. We encapsulate this information in the generated
     * token which is returned to the client to pass in {@link #binderTransactionCompleted(long)}.
     */
    @VisibleForTesting
    @GuardedBy("this")
    final ArrayMap<String, Integer> mKeyByTag = new ArrayMap<>();
    @VisibleForTesting
    @GuardedBy("this")
    final IntArray mCountByKey = new IntArray();

    @VisibleForTesting
    @GuardedBy("this")
    int mTotal = 0;

    private boolean mKnownBad = false;

    BoundServiceSession(BiConsumer<ConnectionRecord, Boolean> processStateUpdater,
            WeakReference<ConnectionRecord> weakCr, String debugName) {
        mProcessStateUpdater = processStateUpdater;
        mConnection = weakCr;
        mDebugName = debugName;
    }

    private static long getToken(int key) {
        return IntPair.of(MAGIC_ID, key);
    }

    private static boolean isValidToken(long token) {
        return IntPair.first(token) == MAGIC_ID;
    }

    private static int getKey(long token) {
        return IntPair.second(token);
    }

    @GuardedBy("this")
    private void maybePostProcessStateUpdate() {
        final ConnectionRecord strongCr = mConnection.get();
        if (strongCr == null) {
            Slog.d(TAG, "ConnectionRecord " + mDebugName
                    + " already gone. Possibly the service has unbound.");
            return;
        }
        mProcessStateUpdater.accept(strongCr, mTotal > 0);
    }

    private void logTraceInstant(Supplier<String> messageSupplier) {
        if (Trace.isTagEnabled(Trace.TRACE_TAG_ACTIVITY_MANAGER)) {
            Trace.instantForTrack(Trace.TRACE_TAG_ACTIVITY_MANAGER, TRACE_TRACK, mDebugName
                    + ": " + messageSupplier.get());
        }
    }

    @GuardedBy("this")
    private void handleInvalidToken(String errorMessage) {
        // We cannot take any meaningful action. The bookkeeping got corrupt in the client and we
        // cannot tell for which tag. We'll just reset all counts to 0 and propagate the same to
        // the underlying ConnectionRecord. This also ensures that there are no shenanigans that
        // the remote app can perform with the given token to remain unfrozen.
        logTraceInstant(() -> errorMessage);
        if (!mKnownBad) {
            Slog.wtfStack(TAG,
                    errorMessage + ". Current keys: " + mKeyByTag + "; Counts: " + mCountByKey);
            mKnownBad = true;
        } else {
            Slog.e(TAG, errorMessage + ". Current keys: " + mKeyByTag + "; Counts: " + mCountByKey);
        }
        mKeyByTag.clear();
        mCountByKey.clear();
        if (mTotal != 0) {
            mTotal = 0;
            maybePostProcessStateUpdate();
        }
    }

    @Override
    public long binderTransactionStarting(String debugTag) {
        synchronized (this) {
            final int key;
            if (mKeyByTag.size() >= MAX_UNIQUE_TAGS) {
                // The values in mKeyByTag are always in the range [0, mKeyByTag.size() - 1].
                key = mKeyByTag.getOrDefault(debugTag, MAX_UNIQUE_TAGS);
                if (key == MAX_UNIQUE_TAGS && mKeyByTag.size() == MAX_UNIQUE_TAGS) {
                    Slog.wtfStack(TAG, "Too many tags supplied on " + mDebugName
                            + ". Current tag: " + debugTag + ". Existing map: " + mKeyByTag);
                    mKeyByTag.put(OVERFLOW_TAG, key);
                    mCountByKey.add(0);
                }
            } else {
                key = mKeyByTag.computeIfAbsent(debugTag, unused -> {
                    mCountByKey.add(0);
                    return mCountByKey.size() - 1;
                });
            }
            final long token = getToken(key);
            logTraceInstant(() -> "open(" + debugTag + ", " + token + ")");
            mCountByKey.set(key, mCountByKey.get(key) + 1);
            mTotal++;
            if (mTotal == 1) {
                maybePostProcessStateUpdate();
            }
            return token;
        }
    }

    @Override
    public void binderTransactionCompleted(long token) {
        synchronized (this) {
            if (!isValidToken(token)) {
                handleInvalidToken("Invalid token " + Long.toHexString(token)
                        + " received in binderTransactionCompleted! Closing all transactions on "
                        + mDebugName);
                return;
            }
            final int key = getKey(token);
            if (mCountByKey.size() <= key || mCountByKey.get(key) <= 0) {
                handleInvalidToken("Bad key " + key
                        + " received in binderTransactionCompleted! Closing all transactions on "
                        + mDebugName);
                return;
            }
            logTraceInstant(() -> "close(" + key + ")");
            mCountByKey.set(key, mCountByKey.get(key) - 1);
            mTotal--;
            if (mTotal == 0) {
                maybePostProcessStateUpdate();
            }
        }
    }

    void dump(IndentingPrintWriter ipw) {
        ipw.println("Bound service session: " + mDebugName);

        synchronized (this) {
            ipw.increaseIndent();
            ipw.print("Connection present: ");
            ipw.println((mConnection.get() != null) ? "Yes" : "No");
            ipw.print("Ongoing bound service calls: ");
            ipw.println(mTotal);

            if (mTotal > 0) {
                ipw.increaseIndent();
                for (int i = 0; i < mKeyByTag.size(); i++) {
                    ipw.print(mKeyByTag.keyAt(i), mCountByKey.get(mKeyByTag.valueAt(i)));
                }
                ipw.println();
                ipw.decreaseIndent();
            }

            ipw.decreaseIndent();
        }
    }

    @Override
    public IBinder asBinder() {
        // Only for use within system server.
        return null;
    }
}
