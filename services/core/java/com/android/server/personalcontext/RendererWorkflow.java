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

package com.android.server.personalcontext;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.hint.PublishedContextHint;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.PublishedContextInsight;
import android.text.TextUtils;
import android.util.Log;
import android.util.Slog;

import com.android.server.personalcontext.component.Renderer;

import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import javax.crypto.spec.SecretKeySpec;

/**
 * Workflow for taking hints that have been passed in to the system and distributing them to all
 * interested refiners and understanders.
 *
 * @hide
 */
public final class RendererWorkflow {
    private static final String TAG = "RendererWorkflow";
    private static final AtomicLong FLOW_COUNTER = new AtomicLong(0);

    /** Starts a new pass of the renderer workflow with an insight. */
    public static void start(
            ComponentProvider provider,
            Collection<PublishedContextInsight> publishedContextInsights,
            SecretKeySpec secretKey,
            EventListener eventListener,
            ScheduledExecutorService executor) {
        for (PublishedContextInsight publishedInsight : publishedContextInsights) {
            start(provider, publishedInsight, secretKey, eventListener, executor);
        }
    }

    /** Starts a new pass of the renderer workflow with an insight. */
    public static void start(
            ComponentProvider provider,
            PublishedContextInsight publishedInight,
            SecretKeySpec secretKey,
            EventListener eventListener,
            ScheduledExecutorService executor) {
        // Build a new workflow instance and start it.
        new RendererWorkflow(provider, publishedInight, secretKey, eventListener, executor).start();
    }

    private final long mFlowId = FLOW_COUNTER.incrementAndGet();
    private final ComponentProvider mProvider;
    private final PublishedContextInsight mPublishedContextInsight;
    private final SecretKeySpec mSecretKey;
    private final EventListener mEventListener;
    private final ScheduledExecutorService mExecutor;

    private RendererWorkflow(
            ComponentProvider provider,
            PublishedContextInsight publishedContextInsight,
            SecretKeySpec secretKey,
            EventListener eventListener,
            ScheduledExecutorService executor) {
        mProvider = provider;
        mPublishedContextInsight = publishedContextInsight;
        mSecretKey = secretKey;
        mEventListener = eventListener != null ? eventListener : new EventListener() {};
        mExecutor = executor;

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, "Starting renderer workflow " + mFlowId);
        }
    }

    private static boolean isInsightValid(PublishedContextInsight publishedContextInsight,
            SecretKeySpec secretKey)
            throws GeneralSecurityException {
        final ContextInsight insight = publishedContextInsight.getInsight();
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, "Validating insight " + insight);
        }

        for (PublishedContextHint hint : insight.getOriginHints()) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Slog.d(TAG, "  Validating hint " + hint);
            }

            if (!hint.isSignatureValid(secretKey)) {
                Slog.d(TAG, "  Hint signature not valid: " + hint);
                return false;
            }
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, "Insight valid");
        }
        return true;
    }

    private void start() {
        mExecutor.execute(() -> {
            try {
                mEventListener.onRendererWorkflowStarted(mFlowId, mPublishedContextInsight);

                // Validate the insight.
                if (!isInsightValid(mPublishedContextInsight, mSecretKey)) {
                    throw new IllegalStateException(
                            TextUtils.formatSimple("Insight %s has invalid hint(s)",
                                    mPublishedContextInsight));
                }

                // Extract the RenderToken from the insight if there is one.
                final Set<RenderToken> renderTokens =
                        mPublishedContextInsight.getInsight().getRenderTokens();

                if (!renderTokens.isEmpty()) {
                    // If we have RenderTokens, then only send the insight to those renderers.
                    for (RenderToken renderToken : renderTokens) {
                        final Renderer renderer =
                                mProvider.getRendererById(renderToken.getRendererComponentId());
                        if (renderer == null) {
                            Log.e(TAG, String.format("Renderer for %s not found", renderToken));
                            mEventListener.onRendererWorkflowError(
                                    mFlowId, new IllegalStateException("Renderer not found"));
                            continue;
                        }

                        mEventListener.onInsightSentToRenderer(mFlowId, mPublishedContextInsight,
                                renderer);
                        renderer.render(mPublishedContextInsight, renderToken);
                    }
                } else {
                    // If we don't have a RenderToken, then send the insight to all renderers...
                    // TODO: Figure out what to do when we have multiple catch-all renderers.
                    for (Renderer renderer : mProvider.getRenderers()) {
                        // ... but only if the renderer actually wants it.
                        if (renderer.isInterestedInInsight(mPublishedContextInsight)) {
                            mEventListener.onInsightSentToRenderer(mFlowId,
                                    mPublishedContextInsight, renderer);
                            renderer.render(mPublishedContextInsight, renderer.mintRenderToken());
                        }
                    }
                }

                mEventListener.onRendererWorkflowFinished(mFlowId);
            } catch (Exception e) {
                Slog.e(TAG, "Render workflow failed", e);
                mEventListener.onRendererWorkflowError(mFlowId, e);
            }
        });
    }

    /**
     * Provides components needed by a RendererWorkflow.
     * @hide
     */
    public interface ComponentProvider {
        /** Gets currently-configured renderers. */
        @NonNull
        Collection<Renderer> getRenderers();

        /** Gets a single currently-configured renderer by id. */
        @Nullable
        Renderer getRendererById(UUID id);
    }

    /**
     * Listener interface, for testing / logging purposes.
     * @hide
     */
    public interface EventListener {
        /** Called when a workflow is started. */
        default void onRendererWorkflowStarted(long flowId,
                PublishedContextInsight publishedContextInsight) { }

        /** Called when an insight is sent to a renderer. */
        default void onInsightSentToRenderer(
                long flowId, PublishedContextInsight publishedContextInsight, Renderer renderer) { }

        /** Called when a workflow stops. */
        default void onRendererWorkflowFinished(long flowId) { }

        /** Called when a workflow has an unexpected error. */
        default void onRendererWorkflowError(long flowId, Throwable t) { }
    }
}
