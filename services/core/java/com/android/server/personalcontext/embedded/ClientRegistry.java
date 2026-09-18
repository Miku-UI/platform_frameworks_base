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

package com.android.server.personalcontext.embedded;

import android.service.personalcontext.RenderToken;
import android.service.personalcontext.embedded.InsightSurfaceClientInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A registry of insight surface clients.
 *
 * @hide
 */
class ClientRegistry {
    private final Map<UUID, InsightSurfaceClientInfo> mClients = new HashMap<>();
    private final Map<UUID, RenderToken> mRenderTokensByClientId = new HashMap<>();
    private final Map<RenderToken, UUID> mClientIdsByRenderToken = new HashMap<>();

    /** Returns {@code true} if there are no clients in the registry. */
    public boolean isEmpty() {
        return mClients.isEmpty();
    }

    /** Returns a list of all clients in the registry. */
    public List<InsightSurfaceClientInfo> getClients() {
        return List.copyOf(mClients.values());
    }

    /**
     * Adds a new client to the registry.
     *
     * @param clientInfo the {@link InsightSurfaceClientInfo} to add
     * @param renderToken the {@link RenderToken} token for the client
     */
    public void addClient(InsightSurfaceClientInfo clientInfo, RenderToken renderToken) {
        final UUID clientId = clientInfo.getId();
        mClients.put(clientId, clientInfo);
        mRenderTokensByClientId.put(clientId, renderToken);
        mClientIdsByRenderToken.put(renderToken, clientId);
        clientInfo.onRegistered();
    }

    /**
     * Get the client with the given id.
     *
     * @param clientId the client to get
     * @return the client with the given id, or {@code null} if the client was not found
     */
    @Nullable
    public InsightSurfaceClientInfo getClient(UUID clientId) {
        return mClients.get(clientId);
    }

    /**
     * Removes a client from the registry.
     *
     * @param clientId the client to remove
     * @return the client that was removed, or {@code null} if the client was not found
     */
    public InsightSurfaceClientInfo removeClient(UUID clientId) {
        final InsightSurfaceClientInfo clientInfo = mClients.remove(clientId);
        final RenderToken renderToken = mRenderTokensByClientId.remove(clientId);
        if (renderToken != null) {
            mClientIdsByRenderToken.remove(renderToken);
        }
        return clientInfo;
    }

    /**
     * Returns the {@link InsightSurfaceClientInfo} for the given render token.
     *
     * @param renderToken the render token to get the client for
     * @return the {@link InsightSurfaceClientInfo} for the given render token, or {@code null} if
     *         the render token was not found
     */
    public InsightSurfaceClientInfo getClientForRenderToken(RenderToken renderToken) {
        final UUID clientId = mClientIdsByRenderToken.get(renderToken);
        return mClients.get(clientId);
    }

    /**
     * Returns the {@link RenderToken} for the given client.
     *
     * @param clientInfo the {@link InsightSurfaceClientInfo} of the client
     * @return the {@link RenderToken} for the client
     */
    public RenderToken getRenderTokenForClient(InsightSurfaceClientInfo clientInfo) {
        synchronized (mClients) {
            return mRenderTokensByClientId.get(clientInfo.getId());
        }
    }

    /**
     * Update the client with new {@link InsightSurfaceClientInfo}.
     *
     * @param oldClient the old {@link InsightSurfaceClientInfo} being updated
     * @param newClient the new {@link InsightSurfaceClientInfo} replacing the old client
     */
    public void updateClient(
            InsightSurfaceClientInfo oldClient, InsightSurfaceClientInfo newClient) {
        synchronized (mClients) {
            mClients.replace(oldClient.getId(), newClient);
        }
    }

    /**
     * Dump info about connected clients.
     */
    public void dump(@NonNull PrintWriter fout) {
        fout.write(" Clients:\n");
        for (InsightSurfaceClientInfo clientInfo : getClients()) {
            final UUID clientId = clientInfo.getId();
            final RenderToken renderToken = mRenderTokensByClientId.get(clientId);
            fout.write("  Client ID: " + clientId + ", RenderToken: " + renderToken + "\n");
        }
    }
}
