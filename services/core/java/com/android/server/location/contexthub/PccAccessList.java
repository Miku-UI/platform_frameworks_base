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

package com.android.server.location.contexthub;

import android.hardware.contexthub.HubEndpointInfo;
import android.os.Process;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * A singleton class to manage an access list for Private Compute Core (PCC) clients.
 *
 * @hide
 */
public class PccAccessList {
    private static volatile PccAccessList sInstance;

    /** The set of endpoints that have been deemed as PCC-accessed. */
    private final Set<HubEndpointInfo.HubEndpointIdentifier> mPccEndpointIds =
            ConcurrentHashMap.newKeySet();

    /** A callback to be invoked when the PCC access list is updated. */
    public interface PccAccessChangeCallback {
        /**
         * @param endpointId The endpoint ID that was added to the PCC access list.
         */
        void onPccAccessChanged(HubEndpointInfo.HubEndpointIdentifier endpointId);
    }

    /** The registered callback. */
    private PccAccessChangeCallback mCallback;

    private PccAccessList() {}

    /**
     * @return The singleton instance of this class.
     */
    /* package */ static PccAccessList getInstance() {
        if (sInstance == null) {
            synchronized (PccAccessList.class) {
                if (sInstance == null) {
                    sInstance = new PccAccessList();
                }
            }
        }
        return sInstance;
    }

    /**
     * Notes that a client with the given UID is attempting to access an endpoint. If the UID
     * belongs to Private Compute Core (PCC), the endpoint is added to a list of PCC-accessed
     * endpoints.
     *
     * @param uid The UID of the client.
     * @param endpointInfo The endpoint being accessed.
     */
    /* package */ void maybeNotePccAccessForEndpoint(int uid, HubEndpointInfo endpointInfo) {
        notePccAccessInternal(uid, endpointInfo.getIdentifier());
    }

    /**
     * Equivalent to {@link #maybeNotePccAccessForEndpoint(int, HubEndpointInfo)} but for nanoapps.
     */
    /* package */ void maybeNotePccAccessForNanoapp(int uid, int contextHubId, long nanoAppId) {
        notePccAccessInternal(
                uid, new HubEndpointInfo.HubEndpointIdentifier(contextHubId, nanoAppId));
    }

    /**
     * Checks if a client has access to communicate with a PCC endpoint.
     *
     * @param uid The UID of the client.
     * @param endpointInfo The endpoint info to check for.
     * @return {@code true} if the client has access, {@code false} otherwise.
     */
    /* package */ boolean checkPccAccessForEndpoint(int uid, HubEndpointInfo endpointInfo) {
        return checkPccAccessInternal(uid, endpointInfo.getIdentifier());
    }

    /** Equivalent to {@link #checkPccAccessForEndpoint(int, HubEndpointInfo)} but for nanoapps. */
    /* package */ boolean checkPccAccessForNanoapp(int uid, int contextHubId, long nanoAppId) {
        return checkPccAccessInternal(
                uid, new HubEndpointInfo.HubEndpointIdentifier(contextHubId, nanoAppId));
    }

    private void notePccAccessInternal(int uid, HubEndpointInfo.HubEndpointIdentifier endpointId) {
        if (isPccUid(uid)) {
            if (mPccEndpointIds.add(endpointId)) {
                PccAccessChangeCallback callback;
                synchronized (this) {
                    callback = mCallback;
                }
                if (callback != null) {
                    callback.onPccAccessChanged(endpointId);
                }
            }
        }
    }

    /** Registers a callback to be invoked when the PCC access list is updated. */
    /* package */ synchronized void registerPccAccessChangeCallback(
            PccAccessChangeCallback callback) {
        Objects.requireNonNull(callback);
        mCallback = callback;
    }

    /**
     * Checks if a client has access to communicate with a PCC endpoint.
     *
     * @param uid The UID of the client.
     * @param endpointId The identifier of the endpoint to check for.
     * @return {@code true} if the client has access, {@code false} otherwise.
     */
    private boolean checkPccAccessInternal(
            int uid, HubEndpointInfo.HubEndpointIdentifier endpointId) {
        return !mPccEndpointIds.contains(endpointId) || isPccUid(uid);
    }

    /**
     * @return true if the given UID is a PCC UID.
     */
    private static boolean isPccUid(int uid) {
        if (android.app.privatecompute.flags.Flags.enablePccFrameworkSupport()) {
            return Process.isPrivateComputeCoreUid(uid);
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("PccAccessList[");
        if (!mPccEndpointIds.isEmpty()) {
            sb.append("endpoints: ")
                    .append(
                            mPccEndpointIds.stream()
                                    .map(Object::toString)
                                    .collect(Collectors.joining(", ")));
        }
        sb.append("]");
        return sb.toString();
    }
}
