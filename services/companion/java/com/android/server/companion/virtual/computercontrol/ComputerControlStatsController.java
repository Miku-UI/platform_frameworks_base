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

package com.android.server.companion.virtual.computercontrol;

import static com.android.server.companion.virtual.computercontrol.ComputerControlStatsLog.COMPUTER_CONTROL_FAILED_SESSION_REPORTED;
import static com.android.server.companion.virtual.computercontrol.ComputerControlStatsLog.COMPUTER_CONTROL_FAILED_SESSION_REPORTED__REASON__DEVICE_LOCKED;
import static com.android.server.companion.virtual.computercontrol.ComputerControlStatsLog.COMPUTER_CONTROL_FAILED_SESSION_REPORTED__REASON__FAIL_REASON_UNKNOWN;
import static com.android.server.companion.virtual.computercontrol.ComputerControlStatsLog.COMPUTER_CONTROL_FAILED_SESSION_REPORTED__REASON__PERMISSION_DENIED;
import static com.android.server.companion.virtual.computercontrol.ComputerControlStatsLog.COMPUTER_CONTROL_FAILED_SESSION_REPORTED__REASON__SESSION_LIMIT_REACHED;
import static com.android.server.companion.virtual.computercontrol.ComputerControlStatsLog.COMPUTER_CONTROL_SESSION_REPORTED;
import static com.android.server.companion.virtual.computercontrol.ComputerControlStatsLog.COMPUTER_CONTROL_SESSION_REPORTED__BLOCK_REASONS__BLOCK_REASON_UNKNOWN;
import static com.android.server.companion.virtual.computercontrol.ComputerControlStatsLog.COMPUTER_CONTROL_SESSION_REPORTED__BLOCK_REASONS__DISALLOWED_ACTIVITY_LAUNCH;
import static com.android.server.companion.virtual.computercontrol.ComputerControlStatsLog.COMPUTER_CONTROL_SESSION_REPORTED__BLOCK_REASONS__SECURE_CONTENT;
import static com.android.server.companion.virtual.computercontrol.ComputerControlStatsLog.COMPUTER_CONTROL_SESSION_REPORTED__CLOSE_REASON__CALLER_INITIATED;
import static com.android.server.companion.virtual.computercontrol.ComputerControlStatsLog.COMPUTER_CONTROL_SESSION_REPORTED__CLOSE_REASON__CLOSE_REASON_UNKNOWN;
import static com.android.server.companion.virtual.computercontrol.ComputerControlStatsLog.COMPUTER_CONTROL_SESSION_REPORTED__CLOSE_REASON__SESSION_EMPTY;
import static com.android.server.companion.virtual.computercontrol.ComputerControlStatsLog.COMPUTER_CONTROL_SESSION_REPORTED__CLOSE_REASON__SESSION_TIMED_OUT;
import static com.android.server.companion.virtual.computercontrol.ComputerControlStatsLog.COMPUTER_CONTROL_SESSION_REPORTED__CLOSE_REASON__USER_INITIATED;
import static com.android.server.companion.virtual.computercontrol.ComputerControlStatsLog.COMPUTER_CONTROL_SESSION_REPORTED__PERFORMED_ACTIONS__ACTION_UNKNOWN;
import static com.android.server.companion.virtual.computercontrol.ComputerControlStatsLog.COMPUTER_CONTROL_SESSION_REPORTED__PERFORMED_ACTIONS__GO_BACK;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.companion.virtual.computercontrol.ComputerControlSession;
import android.companion.virtual.computercontrol.ComputerControlSessionParams;
import android.content.AttributionSource;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.util.IntArray;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Abstraction for {@link ComputerControlStatsLog}.
 *
 * <p>Each instance manages a single {@link ComputerControlSession}. Failed sessions are handled
 * statically.
 */
final class ComputerControlStatsController {
    private static final String TAG = "ComputerControlStatsController";

    private final PackageManager mPackageManager;
    private final AttributionSource mAttributionSource;
    private final ComputerControlSessionParams mParams;
    private final Supplier<Instant> mClock;

    private final AtomicBoolean mIsWritten = new AtomicBoolean(false);
    private final AtomicReference<Instant> mCreationTime = new AtomicReference<>();

    @GuardedBy("mBlockReasons")
    private final IntArray mBlockReasons = new IntArray();

    private final AtomicInteger mMirroredViews = new AtomicInteger(0);
    private final AtomicInteger mMirroredViewsInteractive = new AtomicInteger(0);
    // Tracks the currently active mirror views to calculate the total duration.
    private final AtomicReference<Instant> mMirrorViewLastCreatedTime = new AtomicReference<>();
    private final AtomicReference<Duration> mMirroringTotalDuration =
            new AtomicReference<>(Duration.ZERO);

    @GuardedBy("mApplicationLaunchPackages")
    private final ArrayList<String> mApplicationLaunchPackages = new ArrayList<>();

    private final AtomicInteger mTaps = new AtomicInteger(0);
    private final AtomicInteger mSwipes = new AtomicInteger(0);
    private final AtomicInteger mLongPresses = new AtomicInteger(0);
    private final AtomicInteger mInsertTexts = new AtomicInteger(0);

    @GuardedBy("mPerformedActions")
    private final IntArray mPerformedActions = new IntArray();

    /**
     * Logs a failed session creation with a {@link ComputerControlStatsLog} reason.
     *
     * <p>Does not need an instance as no maintenance is needed.
     */
    static void writeFailedSessionWithStatsReason(
            @NonNull PackageManager packageManager,
            @NonNull AttributionSource attributionSource,
            @NonNull ComputerControlSessionParams params,
            int statsReason) {
        ComputerControlStatsLog.write(
                COMPUTER_CONTROL_FAILED_SESSION_REPORTED,
                attributionSourceToUids(attributionSource),
                attributionSourceToTags(attributionSource),
                packageNamesToUids(
                        packageManager,
                        params.getTargetPackageNames(),
                        getUserId(attributionSource)),
                params.getTargetPackageNames().size(),
                statsReason);
    }

    /**
     * Logs a failed session creation with a {@link ComputerControlSession.SessionCreationError}.
     *
     * <p>Does not need an instance as no maintenance is needed.
     */
    static void writeFailedSessionWithSessionCreationError(
            @NonNull PackageManager packageManager,
            @NonNull AttributionSource attributionSource,
            @NonNull ComputerControlSessionParams params,
            @ComputerControlSession.SessionCreationError int error) {
        writeFailedSessionWithStatsReason(
                packageManager, attributionSource, params, sessionCreationErrorToStats(error));
    }

    @VisibleForTesting
    ComputerControlStatsController(
            @NonNull PackageManager packageManager,
            @NonNull AttributionSource attributionSource,
            @NonNull ComputerControlSessionParams params,
            @NonNull Supplier<Instant> clock) {
        mPackageManager = packageManager;
        mAttributionSource = attributionSource;
        mParams = params;
        mClock = clock;
    }

    ComputerControlStatsController(
            @NonNull PackageManager packageManager,
            @NonNull AttributionSource attributionSource,
            @NonNull ComputerControlSessionParams params) {
        this(packageManager, attributionSource, params, Instant::now);
    }

    void monitor() {
        synchronized (mBlockReasons) { /* no-op */ }
        synchronized (mApplicationLaunchPackages) { /* no-op */ }
        synchronized (mPerformedActions) { /* no-op */ }
    }

    /** Marks the start of the session, unless it was already marked. */
    void onSessionActive() {
        mCreationTime.compareAndSet(null, mClock.get());
    }

    /** Writes the stats to the {@link ComputerControlStatsLog}. */
    void onSessionClosed(@ComputerControlSession.SessionCloseReason int closeReason) {
        if (mIsWritten.getAndSet(true)) {
            Slog.e(TAG, "Session was closed more than once.");
            return;
        }
        updateMirroringTotalDuration();
        synchronized (mBlockReasons) {
            synchronized (mApplicationLaunchPackages) {
                synchronized (mPerformedActions) {
                    Instant creationTime = mCreationTime.get();
                    Duration duration;
                    if (creationTime == null) {
                        duration = Duration.ZERO;
                    } else {
                        duration = Duration.between(creationTime, mClock.get());
                    }
                    int userId = getUserId(mAttributionSource);
                    ComputerControlStatsLog.write(
                            COMPUTER_CONTROL_SESSION_REPORTED,
                            attributionSourceToUids(mAttributionSource),
                            attributionSourceToTags(mAttributionSource),
                            packageNamesToUids(
                                    mPackageManager, mParams.getTargetPackageNames(), userId),
                            mParams.getTargetPackageNames().size(),
                            closeReasonToStats(closeReason),
                            duration.toMillis(),
                            mBlockReasons.toArray(),
                            mBlockReasons.size(),
                            mMirroredViews.get(),
                            mMirroredViewsInteractive.get(),
                            packageNamesToUids(mPackageManager, mApplicationLaunchPackages, userId),
                            mApplicationLaunchPackages.size(),
                            mTaps.get(),
                            mSwipes.get(),
                            mLongPresses.get(),
                            mInsertTexts.get(),
                            mPerformedActions.toArray(),
                            mPerformedActions.size(),
                            requireNonNull(mMirroringTotalDuration.get()).toMillis());
                }
            }
        }
    }

    /** Records a block reason. */
    void onSessionBlocked(@ComputerControlSession.SessionBlockReason int reason) {
        synchronized (mBlockReasons) {
            mBlockReasons.add(blockReasonToStats(reason));
        }
    }

    /** A new mirror view was created. */
    void onMirrorViewCreated() {
        mMirroredViews.incrementAndGet();
    }

    /** Mirroring started, there was none before. */
    void onMirroringStarted() {
        // In case of multiple start calls, we only track the first one.
        mMirrorViewLastCreatedTime.compareAndSet(null, mClock.get());
    }

    /** All mirroring was stopped. */
    void onMirroringStopped() {
        // Resets last created time.
        updateMirroringTotalDuration();
    }

    void onMirrorViewInteractive(boolean interactive) {
        if (interactive) {
            mMirroredViewsInteractive.incrementAndGet();
        }
    }

    void onApplicationLaunched(@NonNull String packageName) {
        synchronized (mApplicationLaunchPackages) {
            mApplicationLaunchPackages.add(packageName);
        }
    }

    void onTap() {
        mTaps.incrementAndGet();
    }

    void onSwipe() {
        mSwipes.incrementAndGet();
    }

    void onLongPress() {
        mLongPresses.incrementAndGet();
    }

    void onInsertText() {
        mInsertTexts.incrementAndGet();
    }

    void onPerformAction(@ComputerControlSession.Action int actionCode) {
        synchronized (mPerformedActions) {
            mPerformedActions.add(actionToStats(actionCode));
        }
    }

    /** Maps a close reason to the corresponding stats enum value. */
    private static int closeReasonToStats(@ComputerControlSession.SessionCloseReason int reason) {
        return switch (reason) {
            case ComputerControlSession.CLOSE_REASON_CALLER_INITIATED ->
                    COMPUTER_CONTROL_SESSION_REPORTED__CLOSE_REASON__CALLER_INITIATED;
            case ComputerControlSession.CLOSE_REASON_SESSION_EMPTY ->
                    COMPUTER_CONTROL_SESSION_REPORTED__CLOSE_REASON__SESSION_EMPTY;
            case ComputerControlSession.CLOSE_REASON_SESSION_TIMED_OUT ->
                    COMPUTER_CONTROL_SESSION_REPORTED__CLOSE_REASON__SESSION_TIMED_OUT;
            case ComputerControlSession.CLOSE_REASON_USER_INITIATED ->
                    COMPUTER_CONTROL_SESSION_REPORTED__CLOSE_REASON__USER_INITIATED;
            case ComputerControlSession.CLOSE_REASON_UNKNOWN ->
                    COMPUTER_CONTROL_SESSION_REPORTED__CLOSE_REASON__CLOSE_REASON_UNKNOWN;
            default -> COMPUTER_CONTROL_SESSION_REPORTED__CLOSE_REASON__CLOSE_REASON_UNKNOWN;
        };
    }

    /** Maps a block reason to the corresponding stats enum value. */
    private static int blockReasonToStats(@ComputerControlSession.SessionBlockReason int reason) {
        return switch (reason) {
            case ComputerControlSession.BLOCK_REASON_SECURE_CONTENT ->
                    COMPUTER_CONTROL_SESSION_REPORTED__BLOCK_REASONS__SECURE_CONTENT;
            case ComputerControlSession.BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH ->
                    COMPUTER_CONTROL_SESSION_REPORTED__BLOCK_REASONS__DISALLOWED_ACTIVITY_LAUNCH;
            case ComputerControlSession.BLOCK_REASON_UNKNOWN ->
                    COMPUTER_CONTROL_SESSION_REPORTED__BLOCK_REASONS__BLOCK_REASON_UNKNOWN;
            default -> COMPUTER_CONTROL_SESSION_REPORTED__BLOCK_REASONS__BLOCK_REASON_UNKNOWN;
        };
    }

    /** Maps a session creation error to the corresponding stats enum value. */
    private static int sessionCreationErrorToStats(
            @ComputerControlSession.SessionCreationError int reason) {
        return switch (reason) {
            case ComputerControlSession.ERROR_DEVICE_LOCKED ->
                    COMPUTER_CONTROL_FAILED_SESSION_REPORTED__REASON__DEVICE_LOCKED;
            case ComputerControlSession.ERROR_SESSION_LIMIT_REACHED ->
                    COMPUTER_CONTROL_FAILED_SESSION_REPORTED__REASON__SESSION_LIMIT_REACHED;
            case ComputerControlSession.ERROR_PERMISSION_DENIED ->
                    COMPUTER_CONTROL_FAILED_SESSION_REPORTED__REASON__PERMISSION_DENIED;
            case ComputerControlSession.ERROR_UNKNOWN ->
                    COMPUTER_CONTROL_FAILED_SESSION_REPORTED__REASON__FAIL_REASON_UNKNOWN;
            default -> COMPUTER_CONTROL_FAILED_SESSION_REPORTED__REASON__FAIL_REASON_UNKNOWN;
        };
    }

    /** Maps a session action to the corresponding stats enum value. */
    private static int actionToStats(@ComputerControlSession.Action int action) {
        return switch (action) {
            case ComputerControlSession.ACTION_GO_BACK ->
                    COMPUTER_CONTROL_SESSION_REPORTED__PERFORMED_ACTIONS__GO_BACK;
            default -> COMPUTER_CONTROL_SESSION_REPORTED__PERFORMED_ACTIONS__ACTION_UNKNOWN;
        };
    }

    /** Returns the uids of the attribution source chain. */
    private static int[] attributionSourceToUids(@NonNull AttributionSource attributionSource) {
        int size = attributionSourceSize(attributionSource);
        int[] uids = new int[size];
        int i = size - 1;
        AttributionSource current = attributionSource;
        while (current != null) {
            uids[i--] = current.getUid();
            current = current.getNext();
        }
        return uids;
    }

    /** Returns the tags of the attribution source chain. */
    private static String[] attributionSourceToTags(@NonNull AttributionSource attributionSource) {
        int size = attributionSourceSize(attributionSource);
        String[] tags = new String[size];
        int i = size - 1;
        AttributionSource current = attributionSource;
        while (current != null) {
            tags[i--] = current.getAttributionTag();
            current = current.getNext();
        }
        return tags;
    }

    /** Returns the uids of the package names. */
    private static int[] packageNamesToUids(
            @NonNull PackageManager packageManager,
            @NonNull List<String> packageNames,
            @UserIdInt int userId) {
        IntArray uids = new IntArray(packageNames.size());
        for (String packageName : packageNames) {
            try {
                uids.add(packageManager.getPackageUidAsUser(packageName, userId));
            } catch (PackageManager.NameNotFoundException e) {
                // Ignoring, it's up to the caller to handle invalid input - not the logger.
            }
        }
        return uids.toArray();
    }

    /** Returns the size of the attribution source chain. */
    private static int attributionSourceSize(@NonNull AttributionSource attributionSource) {
        int size = 0;
        AttributionSource current = attributionSource;
        while (current != null) {
            size++;
            current = current.getNext();
        }
        return size;
    }

    private static int getUserId(@NonNull AttributionSource attributionSource) {
        return UserHandle.getUserId(attributionSource.getUid());
    }

    /**
     * Updates the total duration of the mirror view based on the last created time, if any.
     *
     * <p>This resets the last created time to {@code null}.
     */
    private void updateMirroringTotalDuration() {
        Instant lastCreatedTime = mMirrorViewLastCreatedTime.getAndSet(null);
        if (lastCreatedTime != null) {
            mMirroringTotalDuration.accumulateAndGet(
                    Duration.between(lastCreatedTime, mClock.get()), Duration::plus);
        }
    }
}
