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

import android.annotation.NonNull;

/**
 * Immutable metadata for a connected agent.
 *
 * @hide
 */
public class AgentSession {

    private final int mUserId;
    private final boolean mAllowed;

    /**
     * Create an initially authorized AgentSession record to cache any signals / status from
     * when the agent's device connected.
     *
     * @param userId user that owns this session
     */
    public static AgentSession authorized(int userId) {
        return new AgentSession(userId, true);
    }

    /**
     * Update an existing session to authorized and return a new copy.
     *
     * @param session existing session to copy from
     */
    public static AgentSession authorized(@NonNull AgentSession session) {
        return new AgentSession(session.mUserId, true);
    }

    /**
     * Create a session record for a device that is not authorized for automation.
     *
     * @param userId user that owns this session
     */
    public static AgentSession notAuthorized(int userId) {
        return new AgentSession(userId, false);
    }

    /**
     * Update an existing session to not authorized and return a new copy.
     *
     * @param session existing session to copy from
     */
    public static AgentSession notAuthorized(@NonNull AgentSession session) {
        return new AgentSession(session.mUserId, false);
    }

    private AgentSession(int userId, boolean allowAutomation) {
        mUserId = userId;
        mAllowed = allowAutomation;
    }

    /** Get the user of the session owner. */
    public int getUserId() {
        return mUserId;
    }

    /** If the agent is can perform automations on this device. */
    public boolean isAllowed() {
        return mAllowed;
    }
}
