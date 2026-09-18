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
package com.android.server.companion.datatransfer.crossdevicesync.network.scanner;

import android.util.IndentingPrintWriter;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.server.companion.datatransfer.crossdevicesync.common.DebugConfig;
import com.android.server.companion.datatransfer.crossdevicesync.network.companion.CompanionActionController;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

/** Default implementation of {@link Scanner}. */
public class ScannerImpl implements Scanner {
    private static final String TAG = "Scanner";
    private static final boolean DEBUG = DebugConfig.DEBUG_NETWORK;

    private final Object mLock;
    private final CompanionActionController mCompanionActionController;
    private final Executor mMainExecutor;

    @GuardedBy("mLock")
    private final Set<ScanningSessionImpl> mSessions = new HashSet<>();

    private final Set<Integer> mAssociationsRequestedScanning = new HashSet<>();

    @GuardedBy("mLock")
    private boolean mInvalidationPending;

    public ScannerImpl(
            Object networkLock,
            CompanionActionController companionActionController,
            Executor mainExecutor) {
        mLock = networkLock;
        mCompanionActionController = companionActionController;
        mMainExecutor = mainExecutor;
    }

    @Override
    public ScanningSession startScanning(int associationId, String requestName) {
        synchronized (mLock) {
            ScanningSessionImpl session = new ScanningSessionImpl(associationId, requestName);
            mSessions.add(session);
            invalidateLocked();
            if (DEBUG) {
                Log.d(TAG, "Started scanning session " + session);
            }
            return session;
        }
    }

    @Override
    public void closeAllScanningSessions() {
        synchronized (mLock) {
            List.copyOf(mSessions).forEach(ScanningSessionImpl::close);
        }
    }

    @Override
    public void dump(IndentingPrintWriter pw) {
        synchronized (mLock) {
            pw.println("Scanner:");
            pw.increaseIndent();
            pw.println("mInvalidationPending=" + mInvalidationPending);
            pw.println("mAssociationsRequestedScanning=" + mAssociationsRequestedScanning);
            if (mSessions.isEmpty()) {
                pw.println("No scanning sessions.");
            } else {
                pw.println("Scanning sessions:");
                pw.increaseIndent();
                mSessions.forEach(pw::println);
                pw.decreaseIndent();
            }
            pw.decreaseIndent();
        }
    }

    @GuardedBy("mLock")
    private void invalidateLocked() {
        if (mInvalidationPending) {
            return;
        }
        mInvalidationPending = true;
        mMainExecutor.execute(this::doInvalidate);
    }

    private void doInvalidate() {
        synchronized (mLock) {
            if (!mInvalidationPending) {
                return;
            }
            mInvalidationPending = false;
            Set<Integer> associationsWithSession = new HashSet<>();
            for (ScanningSessionImpl session : mSessions) {
                associationsWithSession.add(session.getAssociationId());
            }

            // Start scanning for new sessions.
            for (int associationId : associationsWithSession) {
                if (mAssociationsRequestedScanning.add(associationId)) {
                    // Fire and forgot the action request. We fully delegate the scan management
                    // to companion apps.
                    mCompanionActionController.startNearbyScanning(associationId);
                }
            }
            // Stop scanning for sessions that are no longer active.
            mAssociationsRequestedScanning.removeIf(
                    associationId -> {
                        if (associationsWithSession.contains(associationId)) {
                            return false;
                        }
                        // Fire and forget the action request. Don't expect failure to happen
                        // for deactivate request.
                        mCompanionActionController.stopNearbyScanning(associationId);
                        return true;
                    });
        }
    }

    private final class ScanningSessionImpl implements ScanningSession {
        private final int mAssociationId;
        private final String mRequestName;

        ScanningSessionImpl(int associationId, String requestName) {
            mAssociationId = associationId;
            mRequestName = requestName;
        }

        @Override
        public int getAssociationId() {
            return mAssociationId;
        }

        @Override
        public boolean isActive() {
            synchronized (mLock) {
                return mSessions.contains(this);
            }
        }

        @Override
        public void close() {
            synchronized (mLock) {
                if (mSessions.remove(this)) {
                    invalidateLocked();
                    if (DEBUG) {
                        Log.d(TAG, "Closed scanning session " + this);
                    }
                }
            }
        }

        @Override
        public String toString() {
            return "ScanningSession(associationId="
                    + mAssociationId
                    + ",requestName="
                    + mRequestName
                    + ",isActive="
                    + isActive()
                    + ")";
        }
    }
}
