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
package com.android.server.companion.datatransfer.crossdevicesync.network.scanner.fake;

import android.util.IndentingPrintWriter;

import com.android.server.companion.datatransfer.crossdevicesync.network.scanner.Scanner;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** A fake implementation of {@link Scanner} for use in tests. */
public class FakeScanner implements Scanner {
    private final Set<FakeScanningSession> mSessions = new HashSet<>();
    private boolean mIsDestroyed = false;

    public FakeScanner() {}

    @Override
    public ScanningSession startScanning(int associationId, String requestName) {
        if (mIsDestroyed) {
            throw new IllegalStateException("Scanner is destroyed.");
        }
        FakeScanningSession session = new FakeScanningSession(this, associationId, requestName);
        mSessions.add(session);
        return session;
    }

    @Override
    public void closeAllScanningSessions() {
        if (mIsDestroyed) {
            return;
        }
        // Create a copy to avoid ConcurrentModificationException.
        new HashSet<>(mSessions).forEach(FakeScanningSession::close);
    }

    /** Destroys the scanner, closing all sessions and preventing new ones. */
    public void destroy() {
        closeAllScanningSessions();
        mIsDestroyed = true;
    }

    private void removeSession(FakeScanningSession session) {
        mSessions.remove(session);
    }

    /** Returns the set of active scanning sessions. */
    public Set<FakeScanningSession> getSessions() {
        return Collections.unmodifiableSet(mSessions);
    }

    /** Returns any single scanning session, or {@code null} if no sessions are active. */
    public FakeScanningSession getAnySession() {
        return mSessions.isEmpty() ? null : mSessions.iterator().next();
    }

    @Override
    public void dump(IndentingPrintWriter pw) {}

    /** A fake implementation of {@link ScanningSession}. */
    public static class FakeScanningSession implements ScanningSession {
        private final FakeScanner mScanner;
        private final int mAssociationId;
        private final String mRequestName;
        private boolean mActive = true;

        FakeScanningSession(FakeScanner scanner, int associationId, String requestName) {
            mScanner = scanner;
            mAssociationId = associationId;
            mRequestName = requestName;
        }

        @Override
        public int getAssociationId() {
            return mAssociationId;
        }

        @Override
        public boolean isActive() {
            return mActive;
        }

        @Override
        public void close() {
            if (mActive) {
                mActive = false;
                mScanner.removeSession(this);
            }
        }

        /** Returns the request name for this session. */
        public String getRequestName() {
            return mRequestName;
        }
    }
}
