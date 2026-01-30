/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertWithMessage;

import android.annotation.NonNull;
import android.os.SystemClock;
import android.view.inputmethod.ImeTracker;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.inputmethod.SoftInputShowHideReason;
import com.android.internal.protolog.ProtoLog;
import com.android.server.inputmethod.ImeTrackerService.History;
import com.android.server.testutils.OffsettableClock;
import com.android.server.testutils.TestHandler;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

/**
 * Unit tests for the IME Tracker service.
 *
 * <p>Suppresses {@link GuardedBy} warnings due to a limitation when synchronizing on a lock from
 * the super class.</p>
 */
@SuppressWarnings("GuardedBy")
public class ImeTrackerServiceTest {

    /** The threshold in milliseconds after which a history entry is considered timed out. */
    private static final long TIMEOUT_MS = 3_000;

    private OffsettableClock mClock;

    private TestHandler mHandler;

    private History mHistory;

    private ArrayList<History.Entry> mRecordedEntries;

    private ImeTrackerService mService;

    @Before
    public void setUp() {
        mClock = new OffsettableClock();
        mHandler = new TestHandler(null /* callback */, mClock);
        mHistory = new History();
        mRecordedEntries = new ArrayList<>();
        ProtoLog.init(IME_TRACKER);
        mService = new ImeTrackerService(mHistory, mHandler, TIMEOUT_MS, mRecordedEntries::add);
    }

    /** Check that an entry that is first started and then shown contains given data. */
    @Test
    public void testStartAndShow() {
        final int id = 0;
        final var tag = "A";
        final var token = new ImeTracker.Token(id, tag);
        final int uid = 10;
        final int type = ImeTracker.TYPE_SHOW;
        final int origin = ImeTracker.ORIGIN_CLIENT;
        final int reason = SoftInputShowHideReason.SHOW_SOFT_INPUT;
        final boolean fromUser = false;

        mService.onStart(token, uid, type, origin, reason, fromUser,
                System.currentTimeMillis() /* startWallTimeMs */,
                SystemClock.elapsedRealtime() /* startTimestampMs */);
        synchronized (mService.mLock) {
            assertWithMessage("Created entry").that(mHistory.getActive(id)).isNotNull();
        }

        mService.onProgress(token, ImeTracker.PHASE_CLIENT_VIEW_SERVED);

        mService.onShown(token);
        synchronized (mService.mLock) {
            assertWithMessage("No active entries remaining").that(mHistory.activeEntries())
                    .isEmpty();
        }

        advanceTime(TIMEOUT_MS);

        assertWithMessage("One entry recorded").that(mRecordedEntries).hasSize(1);
        verifyEntry(mRecordedEntries.getFirst(), tag, uid, type, ImeTracker.STATUS_SUCCESS, origin,
                reason, ImeTracker.PHASE_CLIENT_VIEW_SERVED, fromUser);
    }

    /** Check that an entry that is first started and then hidden contains given data. */
    @Test
    public void testStartAndHide() {
        final int id = 0;
        final var tag = "B";
        final var token = new ImeTracker.Token(id, tag);
        final int uid = 10;
        final int type = ImeTracker.TYPE_HIDE;
        final int origin = ImeTracker.ORIGIN_SERVER;
        final int reason = SoftInputShowHideReason.HIDE_SWITCH_USER;
        final boolean fromUser = false;

        mService.onStart(token, uid, type, origin, reason, fromUser,
                System.currentTimeMillis() /* startWallTimeMs */,
                SystemClock.elapsedRealtime() /* startTimestampMs */);
        synchronized (mService.mLock) {
            assertWithMessage("Created entry").that(mHistory.getActive(id)).isNotNull();
        }

        mService.onProgress(token, ImeTracker.PHASE_CLIENT_ALREADY_HIDDEN);

        mService.onHidden(token);
        synchronized (mService.mLock) {
            assertWithMessage("No active entries remaining").that(mHistory.activeEntries())
                    .isEmpty();
        }

        advanceTime(TIMEOUT_MS);

        assertWithMessage("One entry recorded").that(mRecordedEntries).hasSize(1);
        verifyEntry(mRecordedEntries.getFirst(), tag, uid, type, ImeTracker.STATUS_SUCCESS, origin,
                reason, ImeTracker.PHASE_CLIENT_ALREADY_HIDDEN, fromUser);
    }

    /** Check that an entry that is first started and then cancelled contains given data. */
    @Test
    public void testStartAndCancel() {
        final int id = 0;
        final var tag = "A";
        final var token = new ImeTracker.Token(id, tag);
        final int uid = 20;
        final int type = ImeTracker.TYPE_SHOW;
        final int origin = ImeTracker.ORIGIN_WM_SHELL;
        final int reason = SoftInputShowHideReason.SHOW_AUTO_EDITOR_FORWARD_NAV;
        final boolean fromUser = false;

        mService.onStart(token, uid, type, origin, reason, fromUser,
                System.currentTimeMillis() /* startWallTimeMs */,
                SystemClock.elapsedRealtime() /* startTimestampMs */);
        synchronized (mService.mLock) {
            assertWithMessage("Created entry").that(mHistory.getActive(id)).isNotNull();
        }

        mService.onProgress(token, ImeTracker.PHASE_CLIENT_CONTROL_ANIMATION);

        mService.onCancelled(token, ImeTracker.PHASE_CLIENT_ANIMATION_CANCEL);
        synchronized (mService.mLock) {
            assertWithMessage("No active entries remaining").that(mHistory.activeEntries())
                    .isEmpty();
        }

        advanceTime(TIMEOUT_MS);

        assertWithMessage("One entry recorded").that(mRecordedEntries).hasSize(1);
        verifyEntry(mRecordedEntries.getFirst(), tag, uid, type, ImeTracker.STATUS_CANCEL, origin,
                reason, ImeTracker.PHASE_CLIENT_ANIMATION_CANCEL, fromUser);
    }

    /** Check that an entry that is first started and then failed contains given data. */
    @Test
    public void testStartAndFail() {
        final int id = 0;
        final var tag = "A";
        final var token = new ImeTracker.Token(id, tag);
        final int uid = 10;
        final int type = ImeTracker.TYPE_SHOW;
        final int origin = ImeTracker.ORIGIN_IME;
        final int reason = SoftInputShowHideReason.SHOW_SOFT_INPUT_FROM_IME;
        final boolean fromUser = true;

        mService.onStart(token, uid, type, origin, reason, fromUser,
                System.currentTimeMillis() /* startWallTimeMs */,
                SystemClock.elapsedRealtime() /* startTimestampMs */);
        synchronized (mService.mLock) {
            assertWithMessage("Created entry").that(mHistory.getActive(id)).isNotNull();
        }

        mService.onProgress(token, ImeTracker.PHASE_IME_SHOW_WINDOW);

        mService.onFailed(token, ImeTracker.PHASE_CLIENT_SHOW_INSETS);
        synchronized (mService.mLock) {
            assertWithMessage("No active entries remaining").that(mHistory.activeEntries())
                    .isEmpty();
        }

        advanceTime(TIMEOUT_MS);

        assertWithMessage("One entry recorded").that(mRecordedEntries).hasSize(1);
        verifyEntry(mRecordedEntries.getFirst(), tag, uid, type, ImeTracker.STATUS_FAIL, origin,
                reason, ImeTracker.PHASE_CLIENT_SHOW_INSETS, fromUser);
    }

    /** Check that an entry that is first started and then dispatched contains given data. */
    @Test
    public void testStartAndDispatch() {
        final int id = 0;
        final var tag = "A";
        final var token = new ImeTracker.Token(id, tag);
        final int uid = 10;
        final int type = ImeTracker.TYPE_SHOW;
        final int origin = ImeTracker.ORIGIN_IME;
        final int reason = SoftInputShowHideReason.CONTROL_WINDOW_INSETS_ANIMATION;
        final boolean fromUser = false;

        mService.onStart(token, uid, type, origin, reason, fromUser,
                System.currentTimeMillis() /* startWallTimeMs */,
                SystemClock.elapsedRealtime() /* startTimestampMs */);
        synchronized (mService.mLock) {
            assertWithMessage("Created entry").that(mHistory.getActive(id)).isNotNull();
        }

        mService.onProgress(token, ImeTracker.PHASE_CLIENT_ANIMATION_RUNNING);

        mService.onDispatched(token);
        synchronized (mService.mLock) {
            assertWithMessage("No active entries remaining").that(mHistory.activeEntries())
                    .isEmpty();
        }

        advanceTime(TIMEOUT_MS);

        assertWithMessage("One entry recorded").that(mRecordedEntries).hasSize(1);
        verifyEntry(mRecordedEntries.getFirst(), tag, uid, type, ImeTracker.STATUS_SUCCESS, origin,
                reason, ImeTracker.PHASE_CLIENT_ANIMATION_RUNNING, fromUser);
    }

    /** Check that an entry that is started twice will ignore the second start. */
    @Test
    public void testStartTwice() {
        final int id = 0;
        final var tag = "A";
        final var token = new ImeTracker.Token(id, tag);
        final int uid = 10;
        final int type = ImeTracker.TYPE_SHOW;
        final int origin = ImeTracker.ORIGIN_CLIENT;
        final int reason = SoftInputShowHideReason.SHOW_SOFT_INPUT;
        final boolean fromUser = false;

        mService.onStart(token, uid, type, origin, reason, fromUser,
                System.currentTimeMillis() /* startWallTimeMs */,
                SystemClock.elapsedRealtime() /* startTimestampMs */);
        synchronized (mService.mLock) {
            assertWithMessage("Created entry").that(mHistory.getActive(id)).isNotNull();
        }

        mService.onStart(token, 20, ImeTracker.TYPE_HIDE, ImeTracker.ORIGIN_SERVER,
                SoftInputShowHideReason.SHOW_RESTORE_IME_VISIBILITY, true /* fromUser */,
                1000 /* startWallTimeMs */,
                1000 /* startTimestampMs */);
        synchronized (mService.mLock) {
            assertWithMessage("One active entry").that(mHistory.activeEntries()).hasSize(1);
        }

        mService.onProgress(token, ImeTracker.PHASE_SERVER_SYSTEM_READY);

        mService.onShown(token);
        synchronized (mService.mLock) {
            assertWithMessage("No active entries remaining").that(mHistory.activeEntries())
                    .isEmpty();
        }

        advanceTime(TIMEOUT_MS);

        assertWithMessage("One entry recorded").that(mRecordedEntries).hasSize(1);
        verifyEntry(mRecordedEntries.getFirst(), tag, uid, type, ImeTracker.STATUS_SUCCESS, origin,
                reason, ImeTracker.PHASE_SERVER_SYSTEM_READY, fromUser);
    }

    /**
     * Check that an entry that is started after it is completed will allow creating a new entry.
     */
    @Test
    public void testStartAfterComplete() {
        final int id = 0;
        final var tag = "A";
        final var token = new ImeTracker.Token(id, tag);
        final int uid = 10;
        final int type = ImeTracker.TYPE_SHOW;
        final int origin = ImeTracker.ORIGIN_CLIENT;
        final int reason = SoftInputShowHideReason.SHOW_SOFT_INPUT;
        final boolean fromUser = false;

        mService.onStart(token, uid, type, origin, reason, fromUser,
                System.currentTimeMillis() /* startWallTimeMs */,
                SystemClock.elapsedRealtime() /* startTimestampMs */);
        synchronized (mService.mLock) {
            assertWithMessage("Created entry").that(mHistory.getActive(id)).isNotNull();
        }

        mService.onShown(token);
        synchronized (mService.mLock) {
            assertWithMessage("No active entries remaining").that(mHistory.activeEntries())
                    .isEmpty();
        }

        final int secondType = ImeTracker.TYPE_HIDE;
        mService.onStart(token, uid, secondType, origin, reason, fromUser,
                System.currentTimeMillis() /* startWallTimeMs */,
                SystemClock.elapsedRealtime() /* startTimestampMs */);
        synchronized (mService.mLock) {
            assertWithMessage("Second created entry").that(mHistory.getActive(id)).isNotNull();
        }

        mService.onFailed(token, ImeTracker.PHASE_CLIENT_VIEW_SERVED);
        synchronized (mService.mLock) {
            assertWithMessage("No active entries remaining").that(mHistory.activeEntries())
                    .isEmpty();
        }

        advanceTime(TIMEOUT_MS);

        assertWithMessage("Two entries recorded").that(mRecordedEntries).hasSize(2);
        verifyEntry(mRecordedEntries.get(0), tag, uid, type, ImeTracker.STATUS_SUCCESS, origin,
                reason, ImeTracker.PHASE_NOT_SET, fromUser);
        verifyEntry(mRecordedEntries.get(1), tag, uid, secondType, ImeTracker.STATUS_FAIL, origin,
                reason, ImeTracker.PHASE_CLIENT_VIEW_SERVED, fromUser);
    }

    /**
     * Check that a start from a completed entry won't affect a new entry that is
     * re-using the id.
     */
    @Test
    public void testStartOnReusedId() {
        final int id = 0;
        final var tag = "A";
        final var token = new ImeTracker.Token(id, tag);
        final int uid = 10;
        final int type = ImeTracker.TYPE_SHOW;
        final int origin = ImeTracker.ORIGIN_CLIENT;
        final int reason = SoftInputShowHideReason.SHOW_SOFT_INPUT;
        final boolean fromUser = false;

        final var secondTag = "B";
        final var secondToken = new ImeTracker.Token(id, secondTag);
        final int secondType = ImeTracker.TYPE_HIDE;
        mService.onCancelled(secondToken, ImeTracker.PHASE_SERVER_SHOULD_HIDE);
        synchronized (mService.mLock) {
            assertWithMessage("Second created entry").that(mHistory.getActive(id)).isNotNull();
        }

        // Emulate start on token of completed entry. Completing an entry with the id will allow
        // re-using the id for token creation only in onStart, but that scenario is already
        // checked in testStartTwice. This emulated scenario can happen if the completed entry is
        // old, and the history is flushed due to many new entries.
        mService.onStart(token, uid, type, origin, reason, fromUser,
                System.currentTimeMillis() /* startWallTimeMs */,
                SystemClock.elapsedRealtime() /* startTimestampMs */);
        synchronized (mService.mLock) {
            assertWithMessage("One active entry").that(mHistory.activeEntries()).hasSize(1);
        }

        mService.onStart(secondToken, uid, secondType, origin, reason, fromUser,
                System.currentTimeMillis() /* startWallTimeMs */,
                SystemClock.elapsedRealtime() /* startTimestampMs */);
        synchronized (mService.mLock) {
            assertWithMessage("No active entries remaining").that(mHistory.activeEntries())
                    .isEmpty();
        }

        advanceTime(TIMEOUT_MS);

        assertWithMessage("One recorded").that(mRecordedEntries).hasSize(1);
        verifyEntry(mRecordedEntries.getFirst(), secondTag, uid, secondType,
                ImeTracker.STATUS_CANCEL, origin, reason, ImeTracker.PHASE_SERVER_SHOULD_HIDE,
                fromUser);
    }

    /**
     * Check that an entry that is first progressed, then started and then finished will be
     * completed normally, and contain the given data.
     */
    @Test
    public void testProgressAndStartAndFinish() {
        final int id = 0;
        final var tag = "A";
        final var token = new ImeTracker.Token(id, tag);
        final int uid = 10;
        final int type = ImeTracker.TYPE_SHOW;
        final int origin = ImeTracker.ORIGIN_CLIENT;
        final int reason = SoftInputShowHideReason.SHOW_SOFT_INPUT;
        final boolean fromUser = false;

        mService.onProgress(token, ImeTracker.PHASE_SERVER_HAS_IME);
        synchronized (mService.mLock) {
            assertWithMessage("Created entry").that(mHistory.getActive(id)).isNotNull();
        }

        mService.onStart(token, uid, type, origin, reason, fromUser,
                System.currentTimeMillis() /* startWallTimeMs */,
                SystemClock.elapsedRealtime() /* startTimestampMs */);

        mService.onShown(token);
        synchronized (mService.mLock) {
            assertWithMessage("No active entries remaining").that(mHistory.activeEntries())
                    .isEmpty();
        }

        advanceTime(TIMEOUT_MS);

        assertWithMessage("One entry recorded").that(mRecordedEntries).hasSize(1);
        verifyEntry(mRecordedEntries.getFirst(), tag, uid, type, ImeTracker.STATUS_SUCCESS, origin,
                reason, ImeTracker.PHASE_SERVER_HAS_IME, fromUser);
    }

    /**
     * Check that an entry that is progressed after it is finished will ignore the progress, and
     * contain the data given up that point.
     */
    @Test
    public void testProgressAfterFinish() {
        final int id = 0;
        final var tag = "A";
        final var token = new ImeTracker.Token(id, tag);
        final int uid = 10;
        final int type = ImeTracker.TYPE_SHOW;
        final int origin = ImeTracker.ORIGIN_CLIENT;
        final int reason = SoftInputShowHideReason.SHOW_SOFT_INPUT;
        final boolean fromUser = false;

        mService.onShown(token);
        synchronized (mService.mLock) {
            assertWithMessage("Created entry").that(mHistory.getActive(id)).isNotNull();
        }

        mService.onProgress(token, ImeTracker.PHASE_SERVER_CLIENT_KNOWN);

        mService.onStart(token, uid, type, origin, reason, fromUser,
                System.currentTimeMillis() /* startWallTimeMs */,
                SystemClock.elapsedRealtime() /* startTimestampMs */);
        synchronized (mService.mLock) {
            assertWithMessage("No active entries remaining").that(mHistory.activeEntries())
                    .isEmpty();
        }

        advanceTime(TIMEOUT_MS);

        assertWithMessage("One entry recorded").that(mRecordedEntries).hasSize(1);
        verifyEntry(mRecordedEntries.getFirst(), tag, uid, type, ImeTracker.STATUS_SUCCESS, origin,
                reason, ImeTracker.PHASE_NOT_SET, fromUser);
    }

    /**
     * Check that an entry that is progressed after it is completed will ignore the progress,
     * without creating a new entry.
     */
    @Test
    public void testProgressAfterComplete() {
        final int id = 0;
        final var tag = "A";
        final var token = new ImeTracker.Token(id, tag);
        final int uid = 10;
        final int type = ImeTracker.TYPE_SHOW;
        final int origin = ImeTracker.ORIGIN_CLIENT;
        final int reason = SoftInputShowHideReason.SHOW_SOFT_INPUT;
        final boolean fromUser = false;

        mService.onStart(token, uid, type, origin, reason, fromUser,
                System.currentTimeMillis() /* startWallTimeMs */,
                SystemClock.elapsedRealtime() /* startTimestampMs */);
        synchronized (mService.mLock) {
            assertWithMessage("Created entry").that(mHistory.getActive(id)).isNotNull();
        }

        mService.onProgress(token, ImeTracker.PHASE_CLIENT_VIEW_SERVED);

        mService.onShown(token);
        synchronized (mService.mLock) {
            assertWithMessage("No active entries remaining").that(mHistory.activeEntries())
                    .isEmpty();
        }

        mService.onProgress(token, ImeTracker.PHASE_SERVER_CLIENT_KNOWN);
        synchronized (mService.mLock) {
            assertWithMessage("No entry created for progress after complete")
                    .that(mHistory.getActive(id)).isNull();
        }

        advanceTime(TIMEOUT_MS);

        assertWithMessage("One entry recorded").that(mRecordedEntries).hasSize(1);
        verifyEntry(mRecordedEntries.getFirst(), tag, uid, type, ImeTracker.STATUS_SUCCESS, origin,
                reason, ImeTracker.PHASE_CLIENT_VIEW_SERVED, fromUser);
    }

    /**
     * Check that a progress from a completed entry won't affect a new entry that is
     * re-using the id.
     */
    @Test
    public void testProgressOnReusedId() {
        final int id = 0;
        final var tag = "A";
        final var token = new ImeTracker.Token(id, tag);
        final int uid = 10;
        final int type = ImeTracker.TYPE_SHOW;
        final int origin = ImeTracker.ORIGIN_CLIENT;
        final int reason = SoftInputShowHideReason.SHOW_SOFT_INPUT;
        final boolean fromUser = false;

        mService.onStart(token, uid, type, origin, reason, fromUser,
                System.currentTimeMillis() /* startWallTimeMs */,
                SystemClock.elapsedRealtime() /* startTimestampMs */);
        synchronized (mService.mLock) {
            assertWithMessage("Created entry").that(mHistory.getActive(id)).isNotNull();
        }

        mService.onShown(token);
        synchronized (mService.mLock) {
            assertWithMessage("No active entries remaining").that(mHistory.activeEntries())
                    .isEmpty();
        }

        final var secondTag = "B";
        final var secondToken = new ImeTracker.Token(id, secondTag);
        final int secondType = ImeTracker.TYPE_HIDE;
        mService.onStart(secondToken, uid, secondType, origin, reason, fromUser,
                System.currentTimeMillis() /* startWallTimeMs */,
                SystemClock.elapsedRealtime() /* startTimestampMs */);
        synchronized (mService.mLock) {
            assertWithMessage("Second created entry").that(mHistory.getActive(id)).isNotNull();
        }

        // Progress on token of completed entry.
        mService.onProgress(token, ImeTracker.PHASE_CLIENT_VIEW_SERVED);
        synchronized (mService.mLock) {
            assertWithMessage("One active entry").that(mHistory.activeEntries()).hasSize(1);
        }

        mService.onCancelled(secondToken, ImeTracker.PHASE_SERVER_SHOULD_HIDE);
        synchronized (mService.mLock) {
            assertWithMessage("No active entries remaining").that(mHistory.activeEntries())
                    .isEmpty();
        }

        advanceTime(TIMEOUT_MS);

        assertWithMessage("Two entries recorded").that(mRecordedEntries).hasSize(2);
        verifyEntry(mRecordedEntries.get(0), tag, uid, type, ImeTracker.STATUS_SUCCESS, origin,
                reason, ImeTracker.PHASE_NOT_SET, fromUser);
        verifyEntry(mRecordedEntries.get(1), secondTag, uid, secondType, ImeTracker.STATUS_CANCEL,
                origin, reason, ImeTracker.PHASE_SERVER_SHOULD_HIDE, fromUser);
    }

    /**
     * Check that an entry that is first finished, then started and will be completed normally,
     * and contain the given data.
     */
    @Test
    public void testFinishAndStart() {
        final int id = 0;
        final var tag = "A";
        final var token = new ImeTracker.Token(id, tag);
        final int uid = 10;
        final int type = ImeTracker.TYPE_SHOW;
        final int origin = ImeTracker.ORIGIN_CLIENT;
        final int reason = SoftInputShowHideReason.SHOW_SOFT_INPUT;
        final boolean fromUser = false;

        mService.onShown(token);
        synchronized (mService.mLock) {
            assertWithMessage("Created entry").that(mHistory.getActive(id)).isNotNull();
        }

        mService.onStart(token, uid, type, origin, reason, fromUser,
                System.currentTimeMillis() /* startWallTimeMs */,
                SystemClock.elapsedRealtime() /* startTimestampMs */);
        synchronized (mService.mLock) {
            assertWithMessage("No active entries remaining").that(mHistory.activeEntries())
                    .isEmpty();
        }

        advanceTime(TIMEOUT_MS);

        assertWithMessage("One entry recorded").that(mRecordedEntries).hasSize(1);
        verifyEntry(mRecordedEntries.getFirst(), tag, uid, type, ImeTracker.STATUS_SUCCESS, origin,
                reason, ImeTracker.PHASE_NOT_SET, fromUser);
    }

    /** Check that an entry that is finished twice will ignore the second finish. */
    @Test
    public void testFinishTwice() {
        final int id = 0;
        final var tag = "A";
        final var token = new ImeTracker.Token(id, tag);
        final int uid = 10;
        final int type = ImeTracker.TYPE_SHOW;
        final int origin = ImeTracker.ORIGIN_CLIENT;
        final int reason = SoftInputShowHideReason.SHOW_SOFT_INPUT;
        final boolean fromUser = false;

        mService.onShown(token);
        synchronized (mService.mLock) {
            assertWithMessage("Created entry").that(mHistory.getActive(id)).isNotNull();
        }

        mService.onFailed(token, ImeTracker.PHASE_CLIENT_VIEW_SERVED);

        mService.onStart(token, uid, type, origin, reason, fromUser,
                System.currentTimeMillis() /* startWallTimeMs */,
                SystemClock.elapsedRealtime() /* startTimestampMs */);
        synchronized (mService.mLock) {
            assertWithMessage("No active entries remaining").that(mHistory.activeEntries())
                    .isEmpty();
        }

        advanceTime(TIMEOUT_MS);

        assertWithMessage("One entry recorded").that(mRecordedEntries).hasSize(1);
        verifyEntry(mRecordedEntries.getFirst(), tag, uid, type, ImeTracker.STATUS_SUCCESS, origin,
                reason, ImeTracker.PHASE_NOT_SET, fromUser);
    }

    /**
     * Check that an entry that is finished after it is completed will ignore the finish,
     * without creating a new entry.
     */
    @Test
    public void testFinishAfterComplete() {
        final int id = 0;
        final var tag = "A";
        final var token = new ImeTracker.Token(id, tag);
        final int uid = 10;
        final int type = ImeTracker.TYPE_SHOW;
        final int origin = ImeTracker.ORIGIN_CLIENT;
        final int reason = SoftInputShowHideReason.SHOW_SOFT_INPUT;
        final boolean fromUser = false;

        mService.onStart(token, uid, type, origin, reason, fromUser,
                System.currentTimeMillis() /* startWallTimeMs */,
                SystemClock.elapsedRealtime() /* startTimestampMs */);
        synchronized (mService.mLock) {
            assertWithMessage("Created entry").that(mHistory.getActive(id)).isNotNull();
        }

        mService.onShown(token);
        synchronized (mService.mLock) {
            assertWithMessage("No active entries remaining").that(mHistory.activeEntries())
                    .isEmpty();
        }

        mService.onFailed(token, ImeTracker.PHASE_CLIENT_VIEW_SERVED);
        synchronized (mService.mLock) {
            assertWithMessage("No entry created for finish after complete")
                    .that(mHistory.getActive(id)).isNull();
        }

        advanceTime(TIMEOUT_MS);

        synchronized (mService.mLock) {
            assertWithMessage("No active entries remaining").that(mHistory.activeEntries())
                    .isEmpty();
        }
        assertWithMessage("One entry recorded").that(mRecordedEntries).hasSize(1);
        verifyEntry(mRecordedEntries.getFirst(), tag, uid, type, ImeTracker.STATUS_SUCCESS, origin,
                reason, ImeTracker.PHASE_NOT_SET, fromUser);
    }

    /**
     * Check that a finish from a completed entry won't affect a new entry that is
     * re-using the id.
     */
    @Test
    public void testFinishOnReusedId() {
        final int id = 0;
        final var tag = "A";
        final var token = new ImeTracker.Token(id, tag);
        final int uid = 10;
        final int type = ImeTracker.TYPE_SHOW;
        final int origin = ImeTracker.ORIGIN_CLIENT;
        final int reason = SoftInputShowHideReason.SHOW_SOFT_INPUT;
        final boolean fromUser = false;

        mService.onStart(token, uid, type, origin, reason, fromUser,
                System.currentTimeMillis() /* startWallTimeMs */,
                SystemClock.elapsedRealtime() /* startTimestampMs */);
        synchronized (mService.mLock) {
            assertWithMessage("Created entry").that(mHistory.getActive(id)).isNotNull();
        }

        mService.onShown(token);
        synchronized (mService.mLock) {
            assertWithMessage("No active entries remaining").that(mHistory.activeEntries())
                    .isEmpty();
        }

        final var secondTag = "B";
        final var secondToken = new ImeTracker.Token(id, secondTag);
        final int secondType = ImeTracker.TYPE_HIDE;
        mService.onStart(secondToken, uid, secondType, origin, reason, fromUser,
                System.currentTimeMillis() /* startWallTimeMs */,
                SystemClock.elapsedRealtime() /* startTimestampMs */);
        synchronized (mService.mLock) {
            assertWithMessage("Second created entry").that(mHistory.getActive(id)).isNotNull();
        }

        // Finish on token of completed entry.
        mService.onFailed(token, ImeTracker.PHASE_CLIENT_VIEW_SERVED);
        synchronized (mService.mLock) {
            assertWithMessage("One active entry").that(mHistory.activeEntries()).hasSize(1);
        }

        mService.onCancelled(secondToken, ImeTracker.PHASE_SERVER_SHOULD_HIDE);
        synchronized (mService.mLock) {
            assertWithMessage("No active entries remaining").that(
                    mHistory.activeEntries()).isEmpty();
        }

        advanceTime(TIMEOUT_MS);

        assertWithMessage("Two entries recorded").that(mRecordedEntries).hasSize(2);
        verifyEntry(mRecordedEntries.get(0), tag, uid, type, ImeTracker.STATUS_SUCCESS, origin,
                reason, ImeTracker.PHASE_NOT_SET, fromUser);
        verifyEntry(mRecordedEntries.get(1), secondTag, uid, secondType, ImeTracker.STATUS_CANCEL,
                origin, reason, ImeTracker.PHASE_SERVER_SHOULD_HIDE, fromUser);
    }

    /** Check that a started entry that is not completed will time out. */
    @Test
    public void testTimeoutFromStart() {
        final int id = 0;
        final var tag = "A";
        final var token = new ImeTracker.Token(id, tag);
        final int uid = 10;
        final int type = ImeTracker.TYPE_SHOW;
        final int origin = ImeTracker.ORIGIN_CLIENT;
        final int reason = SoftInputShowHideReason.SHOW_SOFT_INPUT;
        final boolean fromUser = false;

        mService.onStart(token, uid, type, origin, reason, fromUser,
                System.currentTimeMillis() /* startWallTimeMs */,
                SystemClock.elapsedRealtime() /* startTimestampMs */);
        synchronized (mService.mLock) {
            assertWithMessage("Created entry").that(mHistory.getActive(id)).isNotNull();
        }

        mService.onProgress(token, ImeTracker.PHASE_SERVER_WAIT_IME);

        advanceTime(TIMEOUT_MS);

        synchronized (mService.mLock) {
            assertWithMessage("No active entries remaining").that(mHistory.activeEntries())
                    .isEmpty();
        }
        assertWithMessage("One entry recorded").that(mRecordedEntries).hasSize(1);
        verifyEntry(mRecordedEntries.getFirst(), tag, uid, type, ImeTracker.STATUS_TIMEOUT, origin,
                reason, ImeTracker.PHASE_SERVER_WAIT_IME, fromUser);
    }

    /** Check that a progressed entry that is not completed will time out. */
    @Test
    public void testTimeoutFromProgress() {
        final int id = 0;
        final var tag = "A";
        final var token = new ImeTracker.Token(id, tag);
        final int uid = -1;
        final int type = ImeTracker.TYPE_NOT_SET;
        final int origin = ImeTracker.ORIGIN_NOT_SET;
        final int reason = SoftInputShowHideReason.NOT_SET;
        final boolean fromUser = false;

        mService.onProgress(token, ImeTracker.PHASE_SERVER_HAS_IME);
        synchronized (mService.mLock) {
            assertWithMessage("Created entry").that(mHistory.getActive(id)).isNotNull();
        }

        advanceTime(TIMEOUT_MS);

        synchronized (mService.mLock) {
            assertWithMessage("No active entries remaining").that(mHistory.activeEntries())
                    .isEmpty();
        }
        assertWithMessage("One entry recorded").that(mRecordedEntries).hasSize(1);
        verifyEntry(mRecordedEntries.getFirst(), tag, uid, type, ImeTracker.STATUS_TIMEOUT, origin,
                reason, ImeTracker.PHASE_SERVER_HAS_IME, fromUser);
    }

    /** Check that a finished entry that is not completed will time out. */
    @Test
    public void testTimeoutFromFinish() {
        final int id = 0;
        final var tag = "A";
        final var token = new ImeTracker.Token(id, tag);
        final int uid = -1;
        final int type = ImeTracker.TYPE_NOT_SET;
        final int origin = ImeTracker.ORIGIN_NOT_SET;
        final int reason = SoftInputShowHideReason.NOT_SET;
        final boolean fromUser = false;

        mService.onShown(token);
        synchronized (mService.mLock) {
            assertWithMessage("Created entry").that(mHistory.getActive(id)).isNotNull();
        }

        advanceTime(TIMEOUT_MS);

        synchronized (mService.mLock) {
            assertWithMessage("No active entries remaining").that(mHistory.activeEntries())
                    .isEmpty();
        }
        assertWithMessage("One entry recorded").that(mRecordedEntries).hasSize(1);
        verifyEntry(mRecordedEntries.getFirst(), tag, uid, type, ImeTracker.STATUS_TIMEOUT, origin,
                reason, ImeTracker.PHASE_NOT_SET, fromUser);
    }

    /**
     * Check that a timeout from a completed entry won't affect a new entry that is
     * re-using the id.
     */
    @Test
    public void testTimeoutOnReusedId() {
        final int id = 0;
        final var tag = "A";
        final var token = new ImeTracker.Token(id, tag);
        final int uid = 10;
        final int type = ImeTracker.TYPE_SHOW;
        final int origin = ImeTracker.ORIGIN_CLIENT;
        final int reason = SoftInputShowHideReason.SHOW_SOFT_INPUT;
        final boolean fromUser = false;

        mService.onStart(token, uid, type, origin, reason, fromUser,
                System.currentTimeMillis() /* startWallTimeMs */,
                SystemClock.elapsedRealtime() /* startTimestampMs */);
        synchronized (mService.mLock) {
            assertWithMessage("Created entry").that(mHistory.getActive(id)).isNotNull();
        }

        mService.onShown(token);
        synchronized (mService.mLock) {
            assertWithMessage("No active entries remaining").that(mHistory.activeEntries())
                    .isEmpty();
        }

        // Advance halfway through first timeout.
        advanceTime(TIMEOUT_MS / 2);

        final var secondTag = "B";
        final var secondToken = new ImeTracker.Token(id, secondTag);
        final int secondType = ImeTracker.TYPE_HIDE;
        mService.onStart(secondToken, uid, secondType, origin, reason, fromUser,
                System.currentTimeMillis() /* startWallTimeMs */,
                SystemClock.elapsedRealtime() /* startTimestampMs */);
        synchronized (mService.mLock) {
            assertWithMessage("Second created entry").that(mHistory.getActive(id)).isNotNull();
        }

        // Advance past first timeout, but not past second timeout.
        advanceTime(TIMEOUT_MS / 2);

        mService.onCancelled(secondToken, ImeTracker.PHASE_SERVER_SHOULD_HIDE);
        synchronized (mService.mLock) {
            assertWithMessage("No active entries remaining").that(
                    mHistory.activeEntries()).isEmpty();
        }

        // Advance past second timeout.
        advanceTime(TIMEOUT_MS / 2);

        assertWithMessage("Two entries recorded").that(mRecordedEntries).hasSize(2);
        verifyEntry(mRecordedEntries.get(0), tag, uid, type, ImeTracker.STATUS_SUCCESS, origin,
                reason, ImeTracker.PHASE_NOT_SET, fromUser);
        verifyEntry(mRecordedEntries.get(1), secondTag, uid, secondType, ImeTracker.STATUS_CANCEL,
                origin, reason, ImeTracker.PHASE_SERVER_SHOULD_HIDE, fromUser);
    }

    /**
     * Verifies that no new request entries can be created when the collection of active entries
     * is full.
     */
    @Test
    public void testCannotCreateWhenActiveFull() {
        ImeTracker.Token token = null;
        final int uid = 10;
        final int type = ImeTracker.TYPE_SHOW;
        final int origin = ImeTracker.ORIGIN_CLIENT;
        final int reason = SoftInputShowHideReason.SHOW_SOFT_INPUT;
        final boolean fromUser = false;

        synchronized (mService.mLock) {
            for (int id = 0; id < History.ACTIVE_CAPACITY; id++) {
                final var tag = "tag#" + id;
                token = new ImeTracker.Token(id, tag);
                mService.onStart(token, uid, type, origin, reason, fromUser,
                        System.currentTimeMillis() /* startWallTimeMs */,
                        SystemClock.elapsedRealtime() /* startTimestampMs */);
                assertWithMessage("Created entry").that(mHistory.getActive(id)).isNotNull();
            }

            assertWithMessage("Active entries should be full")
                    .that(mHistory.isActiveFull()).isTrue();
        }

        final var otherId = -1;
        final var otherTag = "B";
        final var otherToken = new ImeTracker.Token(otherId, otherTag);
        final int otherType = ImeTracker.TYPE_HIDE;

        mService.onStart(otherToken, uid, otherType, origin, reason, fromUser,
                System.currentTimeMillis() /* startWallTimeMs */,
                SystemClock.elapsedRealtime() /* startTimestampMs */);
        synchronized (mService.mLock) {
            assertWithMessage(
                    "Other entry should not be created in onStart when active entries are full")
                    .that(mHistory.getActive(otherId)).isNull();
        }

        mService.onProgress(otherToken, ImeTracker.PHASE_SERVER_HAS_IME);
        synchronized (mService.mLock) {
            assertWithMessage(
                    "Other entry should not be created in onProgress when active entries are full")
                    .that(mHistory.getActive(otherId)).isNull();
        }

        mService.onHidden(otherToken);
        synchronized (mService.mLock) {
            assertWithMessage(
                    "Other entry should not be created in onHidden when active entries are full")
                    .that(mHistory.getActive(otherId)).isNull();
        }

        mService.onShown(token);
        synchronized (mService.mLock) {
            assertWithMessage("Last created entry was completed")
                    .that(mHistory.getActive(token.getId())).isNull();
            assertWithMessage("Active entries should no longer be full")
                    .that(mHistory.isActiveFull()).isFalse();
        }

        mService.onStart(otherToken, uid, otherType, origin, reason, fromUser,
                System.currentTimeMillis() /* startWallTimeMs */,
                SystemClock.elapsedRealtime() /* startTimestampMs */);
        synchronized (mService.mLock) {
            assertWithMessage(
                    "Other entry should be created in onStart when active entries are not full")
                    .that(mHistory.getActive(otherId)).isNotNull();
        }
    }

    /**
     * Advances the time on the test handler by the specified amount.
     *
     * @param timeMs how long to advance the time by.
     */
    private void advanceTime(long timeMs) {
        mClock.fastForward(timeMs);
        mHandler.timeAdvance();
    }

    /** Verifies that the given entry contains the expected data. */
    private static void verifyEntry(@NonNull History.Entry entry,
            @NonNull String tag, int uid, @ImeTracker.Type int type,
            @ImeTracker.Status int status, @ImeTracker.Origin int origin,
            @SoftInputShowHideReason int reason, @ImeTracker.Phase int phase, boolean fromUser) {
        assertWithMessage("Tag matches").that(entry.mTag).isEqualTo(tag);
        assertWithMessage("Uid matches").that(entry.mUid).isEqualTo(uid);
        assertWithMessage("Type matches").that(entry.mType).isEqualTo(type);
        assertWithMessage("Status matches").that(entry.mStatus).isEqualTo(status);
        assertWithMessage("Origin matches").that(entry.mOrigin).isEqualTo(origin);
        assertWithMessage("Reason matches").that(entry.mReason).isEqualTo(reason);
        assertWithMessage("Phase matches").that(entry.mPhase).isEqualTo(phase);
        assertWithMessage("FromUser matches").that(entry.mFromUser).isEqualTo(fromUser);
    }
}
