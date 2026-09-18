/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.security.authenticationpolicy.agent;

import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.content.Context;
import android.provider.Settings;
import android.util.Slog;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * A Map wrapper of {@link AgentSession} entries. Each entry in the map is also exposed
 * to via a secure settings if the session is <b>not</b> authorized. This allows other system
 * components to check for that case even if they do not have permission to use the other agent
 * APIs in this service. This should only be used for convenience, like showing a UI
 * affordance to the user before they attempt to try an action that will fail when the session
 * state is checked during execution (i.e. it's a hint).
 *
 * The map is backed by a {@link java.util.concurrent.ConcurrentHashMap} and offers the
 * same read/write behavior for map entries. There are no ordering guarantees about the state of
 * the values exposed via settings.
 *
 * @hide
 */
public class AgentSessionMap {

    private static final String TAG = "AgentSessionMap";
    public static final String SETTINGS_KEY = "xdev-ai-agent-missing-session";

    private final Context mContext;
    private final int mUserId;
    private final Map<Key, AgentSession> mAgentSessionList = new ConcurrentHashMap<>();

    /**
     * Create a new session map.
     *
     * @param context system_server context
     * @param userId user id
     */
    AgentSessionMap(Context context, @UserIdInt int userId) {
        mContext = context;
        mUserId = userId;
        updateSetting("" /* value */);
    }

    /** Lookup a session with the given key. */
    @Nullable
    public AgentSession get(Key key) {
        return mAgentSessionList.get(key);
    }

    /** Add a new session to the map. */
    public void put(Key key, AgentSession value) {
        try {
            if (value.getUserId() == mUserId) {
                mAgentSessionList.put(key, value);
            } else {
                Slog.e(TAG, "Invalid user id: " + value.getUserId() + ", ignoring session");
            }
        } finally {
            updateSetting();
        }
    }

    /** Remove an existing session from the map. */
    @Nullable
    public AgentSession remove(Key key) {
        try {
            return mAgentSessionList.remove(key);
        } finally {
            updateSetting();
        }
    }

    /** Clear the map so it is empty. */
    public void clear() {
        try {
            mAgentSessionList.clear();
        } finally {
            updateSetting();
        }
    }

    /** Authorize all sessions in this map. */
    public void authorizeAll(@UserIdInt int userId) {
        try {
            mAgentSessionList.replaceAll((key, session) ->
                    session.getUserId() == userId
                            ? AgentSession.authorized(session)
                            : AgentSession.notAuthorized(session));
        } finally {
            updateSetting();
        }
    }

    /** Authorize a session only if it exists in the map already. */
    @Nullable
    public AgentSession authorizeIfPresent(@UserIdInt int userId, Key key) {
        try {
            return mAgentSessionList.computeIfPresent(key, (k, session) -> {
                if (userId == mUserId && !session.isAllowed()) {
                    return AgentSession.authorized(session);
                } else {
                    return session;
                }
            });
        } finally {
            updateSetting();
        }
    }

    /** Revoke authorization for a session only if it exists in the map already. */
    @Nullable
    public AgentSession revokeIfPresent(@UserIdInt int userId, Key key) {
        try {
            return mAgentSessionList.computeIfPresent(key, (k, session) -> {
                if (userId == mUserId && session.isAllowed()) {
                    return AgentSession.notAuthorized(session);
                } else {
                    return session;
                }
            });
        } finally {
            updateSetting();
        }
    }

    private void updateSetting() {
        updateSetting(null /* value */);
    }

    private void updateSetting(String overrideValue) {
        if (mUserId < 0) {
            return;
        }

        final String value = overrideValue != null ? overrideValue : mAgentSessionList.entrySet()
                .stream()
                .filter(e -> {
                    final var session = e.getValue();
                    return (session.getUserId() == mUserId) && !session.isAllowed();
                })
                .map(e -> String.valueOf(e.getKey()))
                .collect(Collectors.joining(","));
        final boolean ok = Settings.Secure.putStringForUser(mContext.getContentResolver(),
                SETTINGS_KEY, value, mUserId);
        if (!ok) {
            Slog.w(TAG, "Unable to shadow invalid connections");
        }
    }

    /** Key for an AgentSessionMap entry. */
    public static final class Key {
        private final int mId;
        private final int mSourceType;

        private static final int SOURCE_TYPE_CDM_ASSOCIATION_ID = 1;
        private static final int SOURCE_TYPE_VDM_DISPLAY_ID = 2;

        public Key(int id, int sourceType) {
            this.mId = id;
            this.mSourceType = sourceType;
        }

        public int id() {
            return mId;
        }

        public int sourceType() {
            return mSourceType;
        }

        /**
         * Creates a key for a local agent associated with a
         * {@link android.companion.virtual.VirtualDeviceManager} virtual device.
         *
         * @param id device id
         */
        public static Key ofLocal(int id) {
            return new Key(id, SOURCE_TYPE_VDM_DISPLAY_ID);
        }

        /**
         * Creates a key for remote agent associated with an
         * {@link android.companion.CompanionDeviceManager} association to a remote device.
         *
         * @param id association id
         */
        public static Key ofRemote(int id) {
            return new Key(id, SOURCE_TYPE_CDM_ASSOCIATION_ID);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key)) return false;
            Key key = (Key) o;
            return mId == key.mId && mSourceType == key.mSourceType;
        }

        @Override
        public int hashCode() {
            return 31 * mId + mSourceType;
        }

        @Override
        public String toString() {
            if (mSourceType == SOURCE_TYPE_CDM_ASSOCIATION_ID) return "remote_" + mId;
            if (mSourceType == SOURCE_TYPE_VDM_DISPLAY_ID) return "local_" + mId;
            return Integer.toString(mId);
        }
    }
}
