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

package com.android.server.userrecovery;

import static android.app.userrecovery.flags.Flags.enableUserRecoveryManager;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.userrecovery.CertificateBlob;
import android.app.userrecovery.EscrowToken;
import android.app.userrecovery.IUserRecoveryManager;
import android.app.userrecovery.IUserRecoverySession;
import android.app.userrecovery.RecoveryAgentResponse;
import android.app.userrecovery.RecoveryChallenge;
import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Slog;

import com.android.server.SystemService;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class UserRecoveryManagerService extends SystemService {
    private static final String TAG = "UserRecoveryManagerSvc";
    private UserRecoveryManagerImpl mBinder;
    private RecoveryKeyManager mRecoveryKeyManager;

    public UserRecoveryManagerService(Context context) {
        super(context);
    }

    @Override
    public void onStart() {
        if (enableUserRecoveryManager()) {
            Slog.i(TAG, "Starting UserRecoveryManagerService");
            mRecoveryKeyManager = new RecoveryKeyManager(getContext());
            mBinder = new UserRecoveryManagerImpl(getContext(), mRecoveryKeyManager);
            Slog.i(TAG, "Registering binder for " + Context.USER_RECOVERY_SERVICE);
            publishBinderService(Context.USER_RECOVERY_SERVICE, mBinder);
        } else {
            Slog.i(TAG, "UserRecoveryManagerService not enabled");
        }
    }

    private static class UserRecoveryManagerImpl extends IUserRecoveryManager.Stub {
        private static final String STUB_TAG = "UserRecoveryManagerImpl";
        private final Context mContext;
        private final RecoveryKeyManager mRecoveryKeyManager;

        // Map to keep track of active sessions.
        private final Map<IBinder, UserRecoverySessionImpl> mActiveSessions = Collections
                .synchronizedMap(new HashMap<>());

        UserRecoveryManagerImpl(Context context, RecoveryKeyManager keyManager) {
            mContext = context;
            mRecoveryKeyManager = keyManager;
        }

        @Override
        public IUserRecoverySession createRecoverySession(int userId) {
            Slog.d(STUB_TAG, "createRecoverySession for user " + userId);
            // TODO: Permission checks for the caller
            UserRecoverySessionImpl session = new UserRecoverySessionImpl(
                    mContext, userId, mRecoveryKeyManager, this::removeSession);
            IBinder sessionBinder = session.asBinder();
            mActiveSessions.put(sessionBinder, session);

            try {
                sessionBinder.linkToDeath(() -> {
                    Slog.w(STUB_TAG, "Client for session died, cleaning up for userId: " + userId);
                    session.close(); // This will also call removeSession
                }, 0);
            } catch (RemoteException e) {
                Slog.e(STUB_TAG, "Failed to link to death for session, cleaning up", e);
                session.close();
                return null;
            }

            Slog.d(STUB_TAG, "Created session: " + session.getSessionId() + " for user " + userId);
            return session;
        }

        @Override
        @Nullable
        public byte[] requestRart(int userId) {
            Slog.d(STUB_TAG, "requestRart for user " + userId);
            // TODO: Permission checks
            // TODO: Implement mRecoveryKeyManager.generateRart(userId);
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        @Nullable
        public RecoveryChallenge startRecovery(int userId) {
            Slog.d(STUB_TAG, "startRecovery for user " + userId);
            // TODO: Permission checks
            // TODO: Implement mRecoveryKeyManager.initiateRecovery(userId);
            throw new UnsupportedOperationException("Not implemented");
        }

        // Called by UserRecoverySessionImpl to remove itself from the map
        private void removeSession(IBinder sessionBinder) {
            if (mActiveSessions.remove(sessionBinder) != null) {
                Slog.d(STUB_TAG, "Session removed. Active sessions: " + mActiveSessions.size());
            }
        }
    }

    // --- Implementation of IUserRecoverySession ---
    private static class UserRecoverySessionImpl extends IUserRecoverySession.Stub {
        private static final String SESSION_TAG = "UserRecoverySessionImpl";
        private final Context mContext;
        private final int mUserId;
        private final RecoveryKeyManager mRecoveryKeyManager;
        private final String mSessionId;
        private final SessionRemover mSessionRemover;

        interface SessionRemover {
            void removeSession(IBinder binder);
        }

        UserRecoverySessionImpl(Context context, int userId,
                RecoveryKeyManager keyManager, SessionRemover remover) {
            mContext = context;
            mUserId = userId;
            mRecoveryKeyManager = keyManager;
            mSessionId = UUID.randomUUID().toString();
            mSessionRemover = remover;
        }

        public String getSessionId() {
            return mSessionId;
        }

        @Override
        public void saveEscrowToken(@NonNull EscrowToken escrowToken) {
            Slog.d(SESSION_TAG, "saveEscrowToken for session " + mSessionId + ", user " + mUserId);
            // TODO: Permission checks
            // TODO: Validate token
            // TODO: mRecoveryKeyManager.storeEscrowToken(mUserId, mSessionId, escrowToken);
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public void saveKeyPair(@NonNull byte[] keyBlob, @NonNull List<CertificateBlob> certChain) {
            Slog.d(SESSION_TAG, "saveKeyPair for session " + mSessionId + ", user " + mUserId);
            // TODO: Permission checks
            // WARNING: Check sizes of keyBlob and certChain against Binder limits.
            // Consider using ParcelFileDescriptor if data can be large.
            // TODO: mRecoveryKeyManager.storeKeyPair(mUserId, mSessionId, keyBlob,
            // certChain);
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public boolean requestValidation(@NonNull RecoveryAgentResponse recoveryResponse) {
            Slog.d(SESSION_TAG,
                    "requestValidation for session " + mSessionId + ", user " + mUserId);
            // TODO: Permission checks
            // TODO: mRecoveryKeyManager.validateResponse(mUserId, mSessionId,
            // recoveryResponse);
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public void close() {
            Slog.d(SESSION_TAG, "close called for session " + mSessionId + ", user " + mUserId);
            // TODO: Clean up any session-specific resources in RecoveryKeyManager
            // mRecoveryKeyManager.cleanUpSession(mUserId, mSessionId);

            // Remove from the active sessions map in the parent
            mSessionRemover.removeSession(this.asBinder());
        }
    }
}
