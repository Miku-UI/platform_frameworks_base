/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.inputmethod;

import static android.view.ViewProtoLogGroups.IME_TRACKER;

import android.Manifest;
import android.annotation.CurrentTimeMillisLong;
import android.annotation.DurationMillisLong;
import android.annotation.ElapsedRealtimeLong;
import android.annotation.EnforcePermission;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.ImeTracker;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.infra.AndroidFuture;
import com.android.internal.inputmethod.IImeTracker;
import com.android.internal.inputmethod.InputMethodDebug;
import com.android.internal.inputmethod.SoftInputShowHideReason;
import com.android.internal.protolog.ProtoLog;
import com.android.internal.util.FrameworkStatsLog;

import java.io.PrintWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for managing and logging {@link ImeTracker.Token} instances, tracking the lifecycle
 * and information of IME requests.
 *
 * <p>Suppresses {@link GuardedBy} warnings due to a limitation when synchronizing on a lock from
 * the super class.</p>
 */
@SuppressWarnings("GuardedBy")
public final class ImeTrackerService extends IImeTracker.Stub {

    private static final String TAG = ImeTracker.TAG;

    /** The default value for {@link #mTimeoutMs}. */
    private static final long DEFAULT_TIMEOUT_MS = 10_000;

    /** History of requests. */
    @GuardedBy("mLock")
    private final History mHistory;

    /** Handler for registering timeouts for active entries. */
    @GuardedBy("mLock")
    private final Handler mHandler;

    /** The threshold in milliseconds after which a history entry is considered timed out. */
    private final long mTimeoutMs;

    /** Recorder for metrics data from entries. */
    private final MetricsRecorder mMetricsRecorder;

    /** Collection of listeners waiting until there are no more pending requests. */
    @GuardedBy("mLock")
    private final ArrayList<AndroidFuture<Void>> mPendingRequestsListeners = new ArrayList<>();

    /** Interface for recording metrics data from entries. */
    @FunctionalInterface
    interface MetricsRecorder {

        /**
         * Records metrics data from the given entry.
         *
         * @param entry the entry to record the data of.
         */
        void record(@NonNull History.Entry entry);
    }

    final Object mLock = new Object();

    ImeTrackerService(@NonNull Handler handler) {
        this(new History(), handler, DEFAULT_TIMEOUT_MS, entry -> FrameworkStatsLog.write(
                FrameworkStatsLog.IME_REQUEST_FINISHED,
                entry.mUid, entry.mDuration, entry.mType, entry.mStatus, entry.mReason,
                entry.mOrigin, entry.mPhase, entry.mFromUser));
    }

    @VisibleForTesting
    ImeTrackerService(@NonNull History history, @NonNull Handler handler, long timeoutMs,
            @NonNull MetricsRecorder metricsRecorder) {
        mHistory = history;
        mHandler = handler;
        mTimeoutMs = timeoutMs;
        mMetricsRecorder = metricsRecorder;
    }

    @Override
    public void onStart(@NonNull ImeTracker.Token statsToken, int uid, @ImeTracker.Type int type,
            @ImeTracker.Origin int origin, @SoftInputShowHideReason int reason, boolean fromUser,
            @CurrentTimeMillisLong long startWallTimeMs,
            @ElapsedRealtimeLong long startTimestampMs) {
        synchronized (mLock) {
            final long id = statsToken.getId();
            final String tag = statsToken.getTag();
            final var entry = mHistory.getActive(id);
            if (entry != null) {
                if (!entry.mTag.equals(tag)) {
                    // Tags don't match as the ID is being reused after the initial entry completed.
                    log("%s: onStart on previously finished token", tag);
                    return;
                }
                if (entry.mStarted) {
                    log("%s: onStart on previously started and not finished token: %s", tag,
                            entry.mTag);
                    return;
                }

                entry.onStart(tag, uid, type, origin, reason, fromUser, startWallTimeMs,
                        startTimestampMs);

                if (entry.mFinished) {
                    complete(id, entry);
                }
            } else {
                if (mHistory.isActiveFull()) {
                    log("%s: onStart while active entries are full, not tracking request", tag);
                    return;
                }

                final var newEntry = new History.Entry();
                // Prefer current time to passed in startTime when creating the entry in onStart.
                newEntry.onStart(tag, uid, type, origin, reason, fromUser,
                        System.currentTimeMillis() /* startWallTimeMs */,
                        SystemClock.elapsedRealtime() /* startTimestampMs */);

                mHistory.putActive(id, newEntry);
                registerTimeout(id, newEntry);
            }
        }
    }

    @Override
    public void onProgress(@NonNull ImeTracker.Token statsToken, @ImeTracker.Phase int phase) {
        synchronized (mLock) {
            final long id = statsToken.getId();
            final String tag = statsToken.getTag();
            final var entry = mHistory.getActive(id);
            if (entry != null) {
                if (!entry.mTag.equals(tag)) {
                    // Tags don't match as the ID is being reused after the initial entry completed.
                    return;
                }
                if (entry.mFinished) {
                    return;
                }

                entry.onProgress(phase);
            } else {
                if (mHistory.isCompleted(id)) {
                    return;
                }

                if (mHistory.isActiveFull()) {
                    log("%s: onProgress while active entries are full, not tracking request", tag);
                    return;
                }

                final var newEntry = new History.Entry();
                newEntry.mTag = tag;
                newEntry.onProgress(phase);

                mHistory.putActive(id, newEntry);
                registerTimeout(id, newEntry);
            }
        }
    }

    /**
     * Called when an IME request finished.
     *
     * @param statsToken the token tracking the request.
     * @param status     the status the request finished with.
     * @param phase      the phase the request finished at, if it exists
     *                   (or {@link ImeTracker#PHASE_NOT_SET} otherwise).
     */
    @GuardedBy("mLock")
    private void onFinished(@NonNull ImeTracker.Token statsToken, @ImeTracker.Status int status,
            @ImeTracker.Phase int phase) {
        final long id = statsToken.getId();
        final String tag = statsToken.getTag();
        final var entry = mHistory.getActive(id);
        if (entry != null) {
            if (!entry.mTag.equals(tag)) {
                //  Tags don't match as the ID is being reused after the initial entry completed.
                log("%s: onFinished on previously finished token at %s with %s", tag,
                        ImeTracker.Debug.phaseToString(phase),
                        ImeTracker.Debug.statusToString(status));
                return;
            }
            if (entry.mFinished) {
                log("%s: onFinished on previously finished but active token at %s with %s", tag,
                        ImeTracker.Debug.phaseToString(phase),
                        ImeTracker.Debug.statusToString(status));
                return;
            }

            entry.onFinish(status, phase);

            if (entry.mStarted) {
                complete(id, entry);
            }
        } else {
            if (mHistory.isCompleted(id)) {
                log("%s: onFinished on previously finished token at %s with %s", tag,
                        ImeTracker.Debug.phaseToString(phase),
                        ImeTracker.Debug.statusToString(status));
                return;
            }

            if (mHistory.isActiveFull()) {
                log("%s: onFinished at %s with %s while active entries are full,"
                        + " not tracking request", tag,
                        ImeTracker.Debug.phaseToString(phase),
                        ImeTracker.Debug.statusToString(status));
                return;
            }

            final var newEntry = new History.Entry();
            newEntry.mTag = tag;
            newEntry.onFinish(status, phase);

            mHistory.putActive(id, newEntry);
            registerTimeout(id, newEntry);
        }
    }

    /**
     * Called when an entry timed out.
     *
     * @param id    the id of the entry.
     * @param entry the entry that timed out.
     */
    private void onTimeout(long id, @NonNull History.Entry entry) {
        synchronized (mLock) {
            final var existingEntry = mHistory.getActive(id);
            if (existingEntry != entry) {
                // If the existing entry is null, it corresponds to a request that was already
                // completed. If the existing entry is non-null but different from the given entry,
                // the same id was used for the already completed entry, and re-used for the new
                // one. In both cases, we should discard this old timeout request.
                return;
            }

            log("%s: onTimeout at %s", entry.mTag, ImeTracker.Debug.phaseToString(entry.mPhase));

            entry.onFinish(ImeTracker.STATUS_TIMEOUT, entry.mPhase);

            complete(id, entry);
        }
    }

    @Override
    public void onFailed(@NonNull ImeTracker.Token statsToken, @ImeTracker.Phase int phase) {
        synchronized (mLock) {
            onFinished(statsToken, ImeTracker.STATUS_FAIL, phase);
        }
    }

    @Override
    public void onCancelled(@NonNull ImeTracker.Token statsToken, @ImeTracker.Phase int phase) {
        synchronized (mLock) {
            onFinished(statsToken, ImeTracker.STATUS_CANCEL, phase);
        }
    }

    @Override
    public void onShown(@NonNull ImeTracker.Token statsToken) {
        synchronized (mLock) {
            onFinished(statsToken, ImeTracker.STATUS_SUCCESS, ImeTracker.PHASE_NOT_SET);
        }
    }

    @Override
    public void onHidden(@NonNull ImeTracker.Token statsToken) {
        synchronized (mLock) {
            onFinished(statsToken, ImeTracker.STATUS_SUCCESS, ImeTracker.PHASE_NOT_SET);
        }
    }

    @Override
    public void onDispatched(@NonNull ImeTracker.Token statsToken) {
        synchronized (mLock) {
            onFinished(statsToken, ImeTracker.STATUS_SUCCESS, ImeTracker.PHASE_NOT_SET);
        }
    }

    /**
     * Called when the IME request is updated with new data available in IMMS.
     *
     * @param statsToken        the token tracking the request.
     * @param requestWindowName the name of the window that created the request.
     */
    public void onImmsUpdate(@NonNull ImeTracker.Token statsToken,
            @NonNull String requestWindowName) {
        synchronized (mLock) {
            final var entry = mHistory.getActive(statsToken.getId());
            if (entry != null) {
                entry.mRequestWindowName = requestWindowName;
            }
        }
    }

    /**
     * Dump the internal state to the service to the given print writer.
     *
     * @param pw     the print writer to dump to.
     * @param prefix the prefix to prepend to each line of the dump.
     */
    public void dump(@NonNull PrintWriter pw, @NonNull String prefix) {
        synchronized (mLock) {
            mHistory.dump(pw, prefix);
        }
    }

    @EnforcePermission(Manifest.permission.TEST_INPUT_METHOD)
    @Override
    public void waitUntilNoPendingRequests(@NonNull AndroidFuture<Void> future, long timeoutMs) {
        super.waitUntilNoPendingRequests_enforcePermission();
        synchronized (mLock) {
            if (mHistory.activeEntries().isEmpty()) {
                future.complete(null);
            } else {
                mPendingRequestsListeners.add(future);
                mHandler.postDelayed(() -> {
                    future.completeExceptionally(new TimeoutException());
                    mPendingRequestsListeners.remove(future);
                }, future, timeoutMs);
            }
        }
    }

    @EnforcePermission(Manifest.permission.TEST_INPUT_METHOD)
    @Override
    public void finishTrackingPendingRequests(@NonNull AndroidFuture<Void> future) {
        super.finishTrackingPendingRequests_enforcePermission();
        try {
            synchronized (mLock) {
                mHistory.activeEntries().clear();
            }
            future.complete(null);
        } catch (Throwable e) {
            future.completeExceptionally(e);
        }
    }

    /**
     * Completes the entry, and records its data for metrics. An entry can only be completed
     * when it is both {@link #onStart started} and {@link #onFinished finished}, in any order.
     *
     * @param id    the id of the entry.
     * @param entry the entry to complete.
     */
    @GuardedBy("mLock")
    private void complete(long id, @NonNull History.Entry entry) {
        entry.mDuration = entry.mFinishTimestampMs - entry.mStartTimestampMs;
        mHandler.removeCallbacksAndMessages(entry /* token */);
        mHistory.complete(id, entry);
        mMetricsRecorder.record(entry);
        if (!mPendingRequestsListeners.isEmpty() && mHistory.mActiveEntries.isEmpty()) {
            for (int i = 0; i < mPendingRequestsListeners.size(); i++) {
                final var listener = mPendingRequestsListeners.get(i);
                listener.complete(null);
                mHandler.removeCallbacksAndMessages(listener);
            }
            mPendingRequestsListeners.clear();
        }
    }

    /**
     * Registers a timeout for the given entry. If the entry is not {@link #complete completed}
     * within the {@link #mTimeoutMs} period, it is {@link #onTimeout timed out}.
     *
     * @param id    the id of the entry.
     * @param entry the entry to register the timeout for.
     */
    @GuardedBy("mLock")
    private void registerTimeout(long id, @NonNull History.Entry entry) {
        // Register a delayed task to handle the case where the new entry times out.
        mHandler.postDelayed(() -> onTimeout(id, entry), entry /* token */, mTimeoutMs);
    }

    private static void log(@NonNull String messageString, @NonNull Object... args) {
        if (android.tracing.Flags.imetrackerProtolog()) {
            ProtoLog.i(IME_TRACKER, messageString, args);
        } else {
            // Log only to logcat
            final var message = TextUtils.formatSimple(messageString, args);
            Log.i(TAG, message);
        }
    }

    /**
     * A history of active and completed entries of data from IME requests.
     */
    static final class History {

        /** The maximum number of completed entries to store in {@link #mCompletedEntries}. */
        private static final int COMPLETED_CAPACITY = 200;

        /**
         * The maximum number of active entries to track in {@link #mActiveEntries} simultaneously.
         */
        @VisibleForTesting
        static final int ACTIVE_CAPACITY = 1000;

        /**
         * Circular buffer of completed requests, mapped by their unique ID. The buffer has a fixed
         * capacity ({@link #COMPLETED_CAPACITY}), with older entries overwritten when the capacity
         * is reached.
         */
        @GuardedBy("mLock")
        private final LinkedHashMap<Long, History.Entry> mCompletedEntries =
                new LinkedHashMap<>(COMPLETED_CAPACITY) {

                    @Override
                    protected boolean removeEldestEntry(Map.Entry<Long, History.Entry> eldest) {
                        return size() > COMPLETED_CAPACITY;
                    }
                };

        /** Map of currently active requests, mapped by their unique ID. */
        @GuardedBy("mLock")
        private final HashMap<Long, Entry> mActiveEntries = new HashMap<>();

        /**
         * Associates the specified active entry with the specified id.
         *
         * @param id    the id of the entry.
         * @param entry the entry to add.
         */
        @GuardedBy("mLock")
        void putActive(long id, @NonNull Entry entry) {
            mActiveEntries.put(id, entry);
        }

        /**
         * Returns the active entry to which the given id is mapped, or {@code null} if there is no
         * active entry mapped to the id.
         *
         * @param id the id of the entry to get.
         */
        @Nullable
        @GuardedBy("mLock")
        Entry getActive(long id) {
            return mActiveEntries.get(id);
        }

        /** Returns a {@link Collection} view of the active entries contained in this history. */
        @NonNull
        @GuardedBy("mLock")
        Collection<Entry> activeEntries() {
            return mActiveEntries.values();
        }

        /**
         * Completes the specified entry by removing it from {@link #mActiveEntries} and adding it
         * to {@link #mCompletedEntries}.
         *
         * @param id    the id of the entry.
         * @param entry the entry to complete.
         */
        @GuardedBy("mLock")
        void complete(long id, @NonNull Entry entry) {
            mActiveEntries.remove(id);
            mCompletedEntries.put(id, entry);
        }

        /**
         * Checks whether the entry with the given id is completed. Note, this can return
         * {@code false} for a previously completed entry if it was overwritten in the
         * {@link #mCompletedEntries} buffer due to capacity constraints.
         *
         * @param id the id of the entry to check.
         */
        @GuardedBy("mLock")
        boolean isCompleted(long id) {
            return mCompletedEntries.containsKey(id);
        }

        /** Checks whether the collection of active entries is full. */
        @GuardedBy("mLock")
        boolean isActiveFull() {
            return mActiveEntries.size() >= ACTIVE_CAPACITY;
        }

        /**
         * Dump the internal state to the history to the given print writer.
         *
         * @param pw     the print writer to dump to.
         * @param prefix the prefix to prepend to each line of the dump.
         */
        @GuardedBy("mLock")
        void dump(@NonNull PrintWriter pw, @NonNull String prefix) {
            final var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                    .withZone(ZoneId.systemDefault());

            pw.print(prefix);
            pw.println("mActiveEntries: " + mActiveEntries.size() + " elements");

            for (final var entry : mActiveEntries.values()) {
                entry.dump(pw, prefix + "  ", formatter);
            }
            pw.print(prefix);
            pw.println("mCompletedEntries: " + mCompletedEntries.size() + " elements");

            for (final var entry : mCompletedEntries.values()) {
                entry.dump(pw, prefix + "  ", formatter);
            }
        }

        /** An entry containing data about an IME request. */
        static final class Entry {

            /** Counter for the sequence number. */
            private static final AtomicInteger sSequenceNumber = new AtomicInteger();

            /** The entry's sequence number. */
            private final int mSequenceNumber;

            /**
             * Whether the starting data of the entry has been recorded. This can be momentarily
             * {@code false} if the entry is initialized someplace other than {@link #onStart}.
             */
            private boolean mStarted;

            /**
             * Whether the finish data of the entry has been recorded.
             */
            private boolean mFinished;

            /** Wall time in milliseconds when the request was started. */
            @CurrentTimeMillisLong
            private long mStartWallTimeMs;

            /** Time since boot in milliseconds when the request was started. */
            @ElapsedRealtimeLong
            private long mStartTimestampMs;

            /** Time since boot in milliseconds when the request was finished. */
            @ElapsedRealtimeLong
            private long mFinishTimestampMs;

            /** Duration in milliseconds of the request from start to finish. */
            @DurationMillisLong
            private long mDuration;

            /**
             * Clock time in milliseconds when the last {@link #onProgress} call was received. Used
             * in analyzing timed out requests.
             */
            @CurrentTimeMillisLong
            private long mLastProgressWallTimeMs;

            /**
             * Logging tag, of the shape "component:random_hexadecimal".
             *
             * <p>This is set through {@link #onStart}, which is not always where the entry is
             * initialized. In most cases, by the time this is read, it is already updated.</p>
             */
            @NonNull
            String mTag;

            /** Uid of the client that started the request. */
            int mUid;

            /** Type of the request. */
            @ImeTracker.Type
            int mType;

            /** Status of the request. */
            @ImeTracker.Status
            int mStatus;

            /** Origin of the request. */
            @ImeTracker.Origin
            int mOrigin;

            /** Reason for starting the request. */
            @SoftInputShowHideReason
            int mReason;

            /** Latest phase that the request reached. */
            @ImeTracker.Phase
            int mPhase;

            /** Whether this request was created directly from user interaction. */
            boolean mFromUser;

            /**
             * Name of the window that created the request.
             *
             * <p>This is set later through {@link #onImmsUpdate}.</p>
             */
            @NonNull
            private String mRequestWindowName;

            Entry() {
                mSequenceNumber = sSequenceNumber.getAndIncrement();
                mLastProgressWallTimeMs = System.currentTimeMillis();
                mTag = "not set";
                mUid = -1;
                mType = ImeTracker.TYPE_NOT_SET;
                mStatus = ImeTracker.STATUS_RUN;
                mOrigin = ImeTracker.ORIGIN_NOT_SET;
                mReason = SoftInputShowHideReason.NOT_SET;
                mPhase = ImeTracker.PHASE_NOT_SET;
                mFromUser = false;
                mRequestWindowName = "not set";
            }

            /**
             * Records the starting data of the entry.
             *
             * @param tag              the logging tag.
             * @param uid              the uid of the client that started the request.
             * @param type             the type of the request.
             * @param origin           the origin of the request.
             * @param reason           the reason for starting the request.
             * @param fromUser         whether this request was created directly from user
             *                         interaction.
             * @param startWallTimeMs  the wall time in milliseconds when the request was started.
             * @param startTimestampMs the time since boot in milliseconds when the request was
             *                         started.
             */
            @GuardedBy("mLock")
            void onStart(@NonNull String tag, int uid, @ImeTracker.Type int type,
                    @ImeTracker.Origin int origin, @SoftInputShowHideReason int reason,
                    boolean fromUser, @CurrentTimeMillisLong long startWallTimeMs,
                    @ElapsedRealtimeLong long startTimestampMs) {
                mTag = tag;
                mUid = uid;
                mType = type;
                mOrigin = origin;
                mReason = reason;
                mFromUser = fromUser;
                mStartWallTimeMs = startWallTimeMs;
                mStartTimestampMs = startTimestampMs;
                mStarted = true;
            }

            /**
             * Records the progress data of the entry.
             *
             * @param phase the new phase the request reached.
             */
            @GuardedBy("mLock")
            void onProgress(@ImeTracker.Phase int phase) {
                mPhase = phase;
                mLastProgressWallTimeMs = System.currentTimeMillis();
            }

            /**
             * Records the finish data of the entry.
             *
             * @param status the status the request finished with.
             * @param phase  the phase the request finished at, if it exists
             *               (or {@link ImeTracker#PHASE_NOT_SET} otherwise).
             */
            @GuardedBy("mLock")
            void onFinish(@ImeTracker.Status int status, @ImeTracker.Phase int phase) {
                if (phase != ImeTracker.PHASE_NOT_SET) {
                    mPhase = phase;
                }
                mStatus = status;
                mFinishTimestampMs = SystemClock.elapsedRealtime();
                mFinished = true;
            }

            /**
             * Dump the internal state to the entry to the given print writer.
             *
             * @param pw     the print writer to dump to.
             * @param prefix the prefix to prepend to each line of the dump.
             */
            @GuardedBy("mLock")
            void dump(@NonNull PrintWriter pw, @NonNull String prefix,
                    @NonNull DateTimeFormatter formatter) {
                pw.print(prefix);
                pw.print("#" + mSequenceNumber);
                pw.print(" " + ImeTracker.Debug.typeToString(mType));
                pw.print(" - " + ImeTracker.Debug.statusToString(mStatus));
                pw.print(" - " + mTag);
                pw.println(" (" + mDuration + "ms):");

                pw.print(prefix);
                pw.print("  startTime=" + formatter.format(Instant.ofEpochMilli(mStartWallTimeMs)));
                pw.print(" (timestamp=" + mStartTimestampMs + ")");
                pw.println(" " + ImeTracker.Debug.originToString(mOrigin));

                pw.print(prefix);
                pw.print("  reason=" + InputMethodDebug.softInputDisplayReasonToString(mReason));
                pw.print(" " + ImeTracker.Debug.phaseToString(mPhase));

                if (mStatus == ImeTracker.STATUS_TIMEOUT) {
                    pw.print(" lastProgressTime="
                            + formatter.format(Instant.ofEpochMilli(mLastProgressWallTimeMs)));
                }
                pw.println();

                pw.print(prefix);
                pw.println("  requestWindowName=" + mRequestWindowName);
            }

            @Override
            public String toString() {
                return "Entry{"
                        + "mSequenceNumber: " + mSequenceNumber
                        + ", mTag: " + mTag
                        + ", mUid: " + mUid
                        + ", mType: " + ImeTracker.Debug.typeToString(mType)
                        + ", mStatus: " + ImeTracker.Debug.statusToString(mStatus)
                        + ", mOrigin: " + ImeTracker.Debug.originToString(mOrigin)
                        + ", mReason: " + InputMethodDebug.softInputDisplayReasonToString(mReason)
                        + ", mPhase: " + ImeTracker.Debug.phaseToString(mPhase)
                        + ", mFromUser: " + mFromUser
                        + ", mRequestWindowName: " + mRequestWindowName
                        + ", mStartWallTime: " + mStartWallTimeMs
                        + ", mStartTimestamp: " + mStartTimestampMs
                        + ", mFinishTimestamp: " + mFinishTimestampMs
                        + ", mDuration: " + mDuration
                        + ", mStarted: " + mStarted
                        + ", mFinished: " + mFinished
                        + "}";
            }
        }
    }
}
