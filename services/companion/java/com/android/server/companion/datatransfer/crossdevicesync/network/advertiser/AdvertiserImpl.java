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
package com.android.server.companion.datatransfer.crossdevicesync.network.advertiser;

import android.util.IndentingPrintWriter;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.server.companion.datatransfer.crossdevicesync.common.DebugConfig;
import com.android.server.companion.datatransfer.crossdevicesync.network.companion.CompanionActionController;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

/** Default implementation of {@link Advertiser}. */
public class AdvertiserImpl implements Advertiser {
    private static final String TAG = "Advertiser";
    private static final boolean DEBUG = DebugConfig.DEBUG_NETWORK;

    private final Object mLock;
    private final CompanionActionController mCompanionActionController;
    private final Executor mMainExecutor;

    @GuardedBy("mLock")
    private final Set<AdvertisingSessionImpl> mSessions = new HashSet<>();

    private final Set<Integer> mAssociationsRequestedAdvertising = new HashSet<>();

    @GuardedBy("mLock")
    private boolean mInvalidationPending;

    public AdvertiserImpl(
            Object networkLock,
            CompanionActionController companionActionController,
            Executor mainExecutor) {
        mLock = networkLock;
        mCompanionActionController = companionActionController;
        mMainExecutor = mainExecutor;
    }

    @Override
    public AdvertisingSession startAdvertising(int associationId, String requestName) {
        synchronized (mLock) {
            AdvertisingSessionImpl session = new AdvertisingSessionImpl(associationId, requestName);
            mSessions.add(session);
            invalidateLocked();
            if (DEBUG) {
                Log.d(TAG, "Started advertising session " + session);
            }
            return session;
        }
    }

    @Override
    public void closeAllAdvertisingSessions() {
        synchronized (mLock) {
            List.copyOf(mSessions).forEach(AdvertisingSessionImpl::close);
        }
    }

    @Override
    public void dump(IndentingPrintWriter pw) {
        synchronized (mLock) {
            pw.println("Advertiser:");
            pw.increaseIndent();
            pw.println("mInvalidationPending=" + mInvalidationPending);
            pw.println("mAssociationsRequestedAdvertising=" + mAssociationsRequestedAdvertising);
            if (mSessions.isEmpty()) {
                pw.println("No advertising sessions.");
            } else {
                pw.println("Advertising sessions:");
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
            for (AdvertisingSessionImpl session : mSessions) {
                associationsWithSession.add(session.getAssociationId());
            }

            // Start advertising for new sessions.
            for (int associationId : associationsWithSession) {
                if (mAssociationsRequestedAdvertising.add(associationId)) {
                    // Fire and forgot the action request. We fully delegate the advertisement
                    // management to companion apps.
                    mCompanionActionController.startNearbyAdvertising(associationId);
                }
            }
            // Stop advertising for sessions that are no longer active.
            mAssociationsRequestedAdvertising.removeIf(
                    associationId -> {
                        if (associationsWithSession.contains(associationId)) {
                            return false;
                        }
                        // Fire and forget the action request. Don't expect failure to happen
                        // for deactivate request.
                        mCompanionActionController.stopNearbyAdvertising(associationId);
                        return true;
                    });
        }
    }

    private final class AdvertisingSessionImpl implements AdvertisingSession {
        private final int mAssociationId;
        private final String mRequestName;

        AdvertisingSessionImpl(int associationId, String requestName) {
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
                        Log.d(TAG, "Closed advertising session " + this);
                    }
                }
            }
        }

        @Override
        public String toString() {
            return "AdvertisingSession(associationId="
                    + mAssociationId
                    + ",requestName="
                    + mRequestName
                    + ",isActive="
                    + isActive()
                    + ")";
        }
    }
}
