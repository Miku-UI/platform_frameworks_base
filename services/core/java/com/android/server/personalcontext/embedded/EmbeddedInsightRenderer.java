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

import static android.service.personalcontext.embedded.InsightSurfaceSessionException.ERROR_FAILED_TO_CREATE_SESSION;

import android.content.Context;
import android.os.RemoteException;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.embedded.InsightSurfaceClientInfo;
import android.service.personalcontext.insight.PublishedContextInsight;
import android.util.Log;
import android.util.Slog;

import androidx.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.personalcontext.AccessController;
import com.android.server.personalcontext.OperatingModeProvider;
import com.android.server.personalcontext.component.Renderer;

import java.io.PrintWriter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/** @hide */
public class EmbeddedInsightRenderer implements Renderer {
    private static final String TAG = "EmbeddedInsightRenderer";

    private final UUID mComponentId = UUID.randomUUID();
    private final ClientRegistry mClientRegistry;
    private final VisualizerRegistry mVisualizerRegistry;
    private final Executor mExecutor;
    private boolean mIsRegistered;

    public EmbeddedInsightRenderer(
            Context context,
            OperatingModeProvider operatingModeProvider,
            AccessController accessController,
            Executor executor) {
        this(
                new ClientRegistry(),
                new VisualizerRegistry(context, operatingModeProvider, accessController, executor),
                executor);
    }

    /** Construct an {@link EmbeddedInsightRenderer} for test purposes. */
    @VisibleForTesting
    EmbeddedInsightRenderer(
            ClientRegistry clientRegistry,
            VisualizerRegistry visualizerRegistry,
            Executor executor) {
        mClientRegistry = clientRegistry;
        mVisualizerRegistry = visualizerRegistry;
        mExecutor = executor;
    }

    /**
     * The embedded insight renderer has been registered with the ACE framework. Perform any
     * necessary initialization.
     * TODO(b/463464084): Introduce a lifecycle manager to be notified of lifecycle events instead.
     */
    public void onRegistered() {
        logDebug("registering...");

        if (mIsRegistered) {
            Slog.w(TAG, "Already registered!");
            return;
        }

        // The visualizer registry already pushes this work to the executor's thread.
        mVisualizerRegistry.startRegisteringVisualizers(clientIds -> {
            for (UUID id : clientIds) {
                final InsightSurfaceClientInfo clientInfo = mClientRegistry.getClient(id);
                if (clientInfo != null) {
                    // Note that we don't unregister a client just because its connected visualizer
                    // died.
                    // TODO(b/485873401): Create a better error code for this case.
                    clientInfo.onVisualizationError(ERROR_FAILED_TO_CREATE_SESSION);
                }
            }
        });

        mIsRegistered = true;
    }

    /**
     * The embedded insight renderer has been unregistered and should remove all clients and
     * visualizers.
     */
    public void onUnregistered() {
        logDebug("unregistering...");

        if (!mIsRegistered) {
            Slog.w(TAG, "Not registered!");
            return;
        }

        for (InsightSurfaceClientInfo client : mClientRegistry.getClients()) {
            unregisterInsightSurfaceClient(client.getId());
        }

        mVisualizerRegistry.stopMonitoringPackagesForVisualizers();
        mIsRegistered = false;
    }

    /**
     * Register an insight surface client and return the {@link RenderToken} associated with that
     * client via the onRegistered callback.
     */
    public void registerInsightSurfaceClient(InsightSurfaceClientInfo clientInfo) {
        mExecutor.execute(() -> {
            logDebug("registering insight surface client, id=" + clientInfo.getId());

            final RenderToken clientRenderToken = new RenderToken(mComponentId, null);

            mClientRegistry.addClient(clientInfo, clientRenderToken);

            // Link the client to death so we can unregister it if it dies.
            try {
                clientInfo.getClient().asBinder().linkToDeath(() -> {
                    logDebug("client has died: " + clientInfo.getId());
                    unregisterInsightSurfaceClient(clientInfo.getId());
                }, 0);
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        });
    }

    /** Return the renderer's registered clients. */
    @VisibleForTesting
    public List<InsightSurfaceClientInfo> getClients() {
        return mClientRegistry.getClients();
    }

    /** Unregister the insight surface client with the given id. */
    public void unregisterInsightSurfaceClient(UUID id) {
        mExecutor.execute(() -> {
            logDebug("unregistering insight surface client, id=" + id);
            final InsightSurfaceClientInfo clientInfo = mClientRegistry.removeClient(id);
            if (clientInfo == null) {
                Slog.w(TAG, "client not found for id=" + id);
                return;
            }

            mVisualizerRegistry.performActionOnVisualizers(
                    visualizer -> visualizer.onClientDisconnected(clientInfo));
        });
    }

    /** Gets the {@link RenderToken} for the given {@link InsightSurfaceClientInfo}. */
    public RenderToken getRenderTokenForClient(InsightSurfaceClientInfo clientInfo) {
        return mClientRegistry.getRenderTokenForClient(clientInfo);
    }

    /** Update the client info of an embedded client. */
    public void updateClientInfo(
            InsightSurfaceClientInfo oldClientInfo, InsightSurfaceClientInfo newClientInfo) {
        mClientRegistry.updateClient(oldClientInfo, newClientInfo);
    }

    @Override
    public boolean isInterestedInInsight(PublishedContextInsight insight) {
        // Embedded insights should be rendered due to a RenderToken, which bypasses this filter.
        // We don't want any other random insights.
        return false;
    }

    @Override
    public void render(@NonNull PublishedContextInsight publishedContextInsight,
            RenderToken renderToken) {
        mExecutor.execute(() -> {
            if (mClientRegistry.isEmpty()) {
                logDebug("NO embedded surface clients!");
                return;
            }

            if (mVisualizerRegistry.isEmpty()) {
                logDebug("NO visualizers!");
                return;
            }

            final InsightSurfaceClientInfo client = clientFromRenderToken(renderToken);
            if (client == null) {
                logDebug("no client found for insight ["
                        + publishedContextInsight.getInsight() + "]");
                return;
            }

            logDebug("creating visualization for client");
            mVisualizerRegistry.createVisualizationForClient(publishedContextInsight, client,
                    renderToken);
        });
    }

    @Override
    public UUID getComponentId() {
        return mComponentId;
    }

    /**
     * Dump info about connected clients and visualizers.
     */
    public void dump(@NonNull PrintWriter fout) {
        fout.write("EmbeddedInsightRenderer\n");
        fout.write("=======================\n");
        mClientRegistry.dump(fout);
        mVisualizerRegistry.dump(fout);
        fout.write("\n");
    }

    private InsightSurfaceClientInfo clientFromRenderToken(RenderToken renderToken) {
        if (mClientRegistry.isEmpty()) {
            return null;
        }

        return mClientRegistry.getClientForRenderToken(renderToken);
    }

    private static void logDebug(String msg) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, msg);
        }
    }
}
