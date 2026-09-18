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

package com.android.server.lskfreset;

import static android.app.lskfreset.flags.Flags.enableLskfResetManager;

import android.annotation.NonNull;
import android.annotation.RequiresNoPermission;
import android.app.lskfreset.EscrowToken;
import android.app.lskfreset.ILskfResetManager;
import android.app.lskfreset.ILskfResetSession;
import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.SystemService;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class LskfResetManagerService extends SystemService {
    private static final String TAG = "LskfResetManagerSvc";

    private ScheduledExecutorService mExecutor;

    private LskfResetKeyManager mLskfResetKeyManager;
    private LskfResetManagerImpl mBinder;

    // Map to keep track of active sessions.
    private final Map<IBinder, LskfResetSessionImpl> mActiveSessions =
            Collections.synchronizedMap(new HashMap<>());

    /** Class for injecting dependencies for testing. */
    @VisibleForTesting
    static class Injector {
        ScheduledExecutorService getExecutor() {
            return Executors.newSingleThreadScheduledExecutor();
        }
    }

    public LskfResetManagerService(Context context) {
        this(context, new Injector());
    }

    LskfResetManagerService(Context context, Injector injector) {
        super(context);
        mExecutor = injector.getExecutor();
    }

    @VisibleForTesting
    ILskfResetManager getBinderService() {
        return mBinder;
    }

    @VisibleForTesting
    boolean isSessionActive(ILskfResetSession session) {
        return mActiveSessions.containsKey(session.asBinder());
    }

    /**
     * Forcibly terminate the given session. This is intended to simulate what would happen if the
     * remote client holding the session died and the linkToDeath operation was triggered. This will
     * fail if the session is not currently alive and open.
     *
     * @param session The session to terminate.
     */
    @VisibleForTesting
    void killSession(ILskfResetSession session) {
        IBinder sessionBinder = session.asBinder();
        LskfResetSessionImpl sessionImpl = mActiveSessions.get(sessionBinder);
        if (sessionImpl == null) {
            throw new IllegalStateException("Cannot kill session that is not active");
        }
        boolean isAlive = sessionBinder.unlinkToDeath(sessionImpl, 0);
        if (!isAlive) {
            throw new IllegalStateException("Cannot kill session that is not alive");
        }
        sessionImpl.binderDied();
    }

    @Override
    public void onStart() {
        if (enableLskfResetManager()) {
            Slog.i(TAG, "Starting LskfResetManagerService");
            mBinder = new LskfResetManagerImpl();
            Slog.i(TAG, "Registering binder for " + Context.LSKF_RESET_SERVICE);
            try {
                mLskfResetKeyManager = new LskfResetKeyManager(getContext());
                publishBinderService(Context.LSKF_RESET_SERVICE, mBinder);
            } catch (Throwable t) {
                Slog.e(TAG, "Could not start the LskfResetManagerService.", t);
            }
        } else {
            Slog.i(TAG, "LskfResetManagerService not enabled");
        }
    }

    private class LskfResetManagerImpl extends ILskfResetManager.Stub {
        private static final String STUB_TAG = "LskfResetManagerImpl";

        @Override
        @RequiresNoPermission
        public ILskfResetSession createLskfResetSession(UserHandle user) {
            // TODO: Permission checks for the caller
            Slog.d(STUB_TAG, "createLskfResetSession for user " + user);

            LskfResetSessionImpl session = new LskfResetSessionImpl(user);
            IBinder sessionBinder = session.asBinder();
            mActiveSessions.put(sessionBinder, session);

            try {
                sessionBinder.linkToDeath(session, 0);
            } catch (RemoteException e) {
                Slog.e(STUB_TAG, "Failed to link to death for session, cleaning up", e);
                session.binderDied();
                return null;
            }

            Slog.d(STUB_TAG, "Created session: " + session.getSessionId() + " for user: " + user);
            return session;
        }
    }

    private class LskfResetSessionImpl extends ILskfResetSession.Stub
            implements IBinder.DeathRecipient {
        private static final String SESSION_TAG = "LskfResetSessionImpl";
        private static final long SESSION_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(1);

        private final UserHandle mUser;
        private final UUID mSessionId;

        private final OpenSession mOpenSession;

        @GuardedBy("mOpenSession")
        private boolean mClosed;

        @GuardedBy("mOpenSession")
        private ScheduledFuture<?> mScheduledTimeout;

        /**
         * Implements the actual underlying session operations with the assumption that the session
         * has not been closed and will not be close while this is active.
         */
        private class OpenSession {
            public void saveEscrowToken(@NonNull EscrowToken escrowToken) {
                // TODO: Validate token
                // TODO: mLskfResetKeyManager.storeEscrowToken(mUser, mSessionId, escrowToken);
                throw new UnsupportedOperationException("Not implemented");
            }
        }

        LskfResetSessionImpl(UserHandle user) {
            mUser = user;
            mSessionId = UUID.randomUUID();
            mOpenSession = new OpenSession();
            mClosed = false;
            mScheduledTimeout =
                    mExecutor.schedule(
                            () -> {
                                closeSession(null);
                            },
                            SESSION_TIMEOUT_MS,
                            TimeUnit.MILLISECONDS);
        }

        UUID getSessionId() {
            return mSessionId;
        }

        /**
         * Close the underlying session if it is not closed already.
         *
         * @param runOnAlreadyClosed An optional runnable that will be executed if the caller is
         *     trying to close an already-closed session. This is used to treat a double-close as a
         *     failure in certain contexts.
         */
        private void closeSession(Runnable runOnAlreadyClosed) {
            synchronized (mOpenSession) {
                if (mScheduledTimeout != null) {
                    mScheduledTimeout.cancel(false);
                    mScheduledTimeout = null;
                }
                if (mClosed) {
                    if (runOnAlreadyClosed != null) runOnAlreadyClosed.run();
                    return;
                }
                // Remove from the active sessions map in the parent
                if (mActiveSessions.remove(asBinder()) != null) {
                    Slog.d(
                            SESSION_TAG,
                            "Session removed. Active sessions: " + mActiveSessions.size());
                }
                mClosed = true;
            }
        }

        @Override
        public void binderDied() {
            Slog.w(SESSION_TAG, "Client for session died, cleaning up for user: " + mUser);
            closeSession(null);
        }

        @Override
        @RequiresNoPermission
        public void saveEscrowToken(@NonNull EscrowToken escrowToken) {
            Slog.d(SESSION_TAG, "saveEscrowToken for session " + mSessionId + ", user " + mUser);
            // TODO: Permission checks
            synchronized (mOpenSession) {
                if (mClosed) throw new IllegalStateException("Session is already closed");
                mOpenSession.saveEscrowToken(escrowToken);
            }
        }

        @Override
        @RequiresNoPermission
        public void close() {
            Slog.d(SESSION_TAG, "close called for session " + mSessionId + ", user " + mUser);
            // TODO: Permission checks
            closeSession(
                    () -> {
                        throw new IllegalStateException("Session is already closed");
                    });
        }
    }
}
