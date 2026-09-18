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
import android.content.ComponentName;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.PublishedContextHint;
import android.service.personalcontext.insight.ContextInsight;
import android.text.TextUtils;
import android.util.Log;
import android.util.Slog;

import com.android.server.personalcontext.component.Refiner;
import com.android.server.personalcontext.component.Renderer;
import com.android.server.personalcontext.util.FragileReference;

import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import javax.crypto.spec.SecretKeySpec;

/**
 * Workflow for taking hints that have been passed in to the system and distributing them to all
 * interested refiners and understanders.
 *
 * @hide
 */
public final class RefinerWorkflow {
    private static final String TAG = "RefinerWorkflow";
    private static final int WORKFLOW_TIMEOUT_MS = 5_000;
    private static final AtomicLong FLOW_COUNTER = new AtomicLong(0);

    public interface InsightConsumer {
        /**
         * Called when new {@link ContextInsight}s are available.
         */
        void accept(UUID componentId, Set<ContextInsight> insights);
    }

    /** Starts a new pass of the refiner workflow with a seeded set of hints. */
    public static RefinerWorkflow start(
            ComponentProvider provider,
            Set<PublishedContextHint> initialHints,
            Set<RenderToken> renderTokens,
            SecretKeySpec secretKey,
            EventListener eventListener,
            ScheduledExecutorService executor,
            InsightConsumer insightConsumer) {
        // Build a new workflow instance.
        final RefinerWorkflow workflow = new RefinerWorkflow(
                provider, renderTokens, secretKey, eventListener, executor, insightConsumer);

        // Seed it with the first round of hints.
        workflow.seedHints(initialHints);

        return workflow;
    }

    private final long mFlowId = FLOW_COUNTER.incrementAndGet();
    private final Set<PublishedContextHint> mAllHints = new HashSet<>();
    private final Map<Refiner, Set<UUID>> mSeenHintIds = new HashMap<>();
    private final Set<RefinerCallback> mPendingRefinerCallbacks = new HashSet<>();
    private final ComponentProvider mProvider;
    private final Set<RenderToken> mRenderTokens;
    private final SecretKeySpec mSecretKey;
    private final EventListener mEventListener;
    private final ScheduledExecutorService mExecutor;
    private final FragileReference<RefinerWorkflow> mFragileSelf = new FragileReference<>(this);

    private final InsightConsumer mInsightConsumer;

    private RefinerWorkflow(
            ComponentProvider provider,
            Set<RenderToken> renderTokens,
            SecretKeySpec secretKey,
            EventListener eventListener,
            ScheduledExecutorService executor,
            InsightConsumer insightConsumer) {
        mProvider = provider;
        mRenderTokens = renderTokens;
        mSecretKey = secretKey;
        mEventListener = eventListener != null ? eventListener : new EventListener() {};
        mExecutor = executor;
        mInsightConsumer = insightConsumer;

        // Auto-expire the workflow after the timeout is up.
        mExecutor.schedule(this::expire, WORKFLOW_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, "Starting refiner workflow " + mFlowId);
        }
    }

    /** Immediately triggers the timeout for responses from refiners. */
    public void expire() {
        // Expire the {@link FragileReference}. Outstanding refiners will be ignored.
        if (mFragileSelf.get() == null) return;

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, TextUtils.formatSimple(
                    "Workflow %s has expired, %s refiner calls pending, no more hints accepted",
                    mFlowId, mPendingRefinerCallbacks.size()));
        }

        mFragileSelf.expire();
        mEventListener.onRefinerWorkflowFinished(mFlowId);
    }

    private void seedHints(Set<PublishedContextHint> initialHints) {
        mExecutor.execute(() -> {
            try {
                // Report this to the listener.
                mEventListener.onRefinerWorkflowStarted(mFlowId, initialHints);

                runPassOnAllRefiners(initialHints);
            } catch (Exception e) {
                Slog.e(TAG, "Error running workflow " + mFlowId, e);
                expire();
            }
        });
    }

    private void addHints(
            Set<ContextHint> newHints,
            Set<PublishedContextHint> attributionHints,
            @NonNull Refiner source,
            @NonNull RefinerCallback callback) {
        mExecutor.execute(() -> {
            try {
                // Log interesting stuff.
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Slog.d(TAG, TextUtils.formatSimple(
                            "Adding %s new hints to workflow %s from %s",
                            newHints == null ? 0 : newHints.size(),
                            mFlowId,
                            source.getComponentId()));

                    if (newHints != null) {
                        for (ContextHint hint : newHints) {
                            Slog.d(TAG, "  Hint: " + hint);
                        }
                    }
                }

                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Slog.d(TAG, TextUtils.formatSimple(
                            "Adding %s new hints to workflow %s from %s",
                            newHints == null ? 0 : newHints.size(),
                            mFlowId,
                            source.getComponentId()));

                    if (newHints != null) {
                        for (ContextHint hint : newHints) {
                            Slog.d(TAG, "  Hint: " + hint);
                        }
                    }
                }

                // Keep track of the fact that we don't need a response from this callback any more.
                mPendingRefinerCallbacks.remove(callback);
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Slog.d(TAG, TextUtils.formatSimple(
                            "Waiting on responses from %s refiner calls",
                            mPendingRefinerCallbacks.size()));
                }

                // Sign the hints.
                final Set<PublishedContextHint> signedHints =
                        signHints(newHints, attributionHints, source);

                // If we don't have any new hints, then there's no point in doing a bunch of work.
                if (!signedHints.isEmpty()) {
                    // Report this to the listener.
                    mEventListener.onHintsReceivedFromRefiner(mFlowId, signedHints, source);

                    // Mark this source as having seen these hints. No point in sending them back.
                    markHintsAsSeen(source, newHints);
                }

                runPassOnAllRefiners(signedHints);
            } catch (Exception e) {
                Slog.e(TAG, "Error running workflow " + mFlowId, e);
                expire();
            }
        });
    }

    /** Marks a collection of hints as seen by a refiner. */
    private void markHintsAsSeen(Refiner refiner, Collection<ContextHint> hints) {
        if (refiner != null && hints != null && !hints.isEmpty()) {
            synchronized (mSeenHintIds) {
                final Set<UUID> seenIds = mSeenHintIds.get(refiner);
                if (seenIds != null) {
                    for (ContextHint hint : hints) {
                        seenIds.add(hint.getHintId());
                    }
                }
            }
        }
    }

    private Set<PublishedContextHint> signHints(
            Collection<ContextHint> newHints,
            Collection<PublishedContextHint> attributionHints,
            Refiner source) throws GeneralSecurityException {
        final Set<PublishedContextHint> result = new HashSet<>();
        if (newHints != null) {
            for (ContextHint hint : newHints) {
                final ComponentName componentName = source.getComponentName();
                final String packageName =
                        componentName != null ? componentName.getPackageName() : null;
                result.add(new PublishedContextHint.Builder(hint, mSecretKey)
                        .setOriginatingPackage(packageName)
                        .addRenderTokens(mRenderTokens)
                        .addAttributionHints(attributionHints)
                        .build());
            }
        }
        return result;
    }

    private void runPassOnAllRefiners(Set<PublishedContextHint> signedHints) {
        if (!signedHints.isEmpty()) {
            // Stage new hints in the collection of pending hints.
            mAllHints.addAll(signedHints);

            // Run a full pass for each refiner available.
            for (Refiner refiner : mProvider.getRefiners()) {
                runPassOnSingleRefiner(refiner);
            }
        }

        // Check to see if we have any work left to do; if not we can shut down the flow.
        if (mPendingRefinerCallbacks.isEmpty()) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Slog.d(TAG, "No remaining work to be done, workflow shutting down");
            }
            expire();
        } else if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, TextUtils.formatSimple(
                    "Waiting on responses from %s refiner calls",
                    mPendingRefinerCallbacks.size()));

            Slog.d(TAG, "Completed refiner pass for workflow " + mFlowId);
        }
    }

    private void runPassOnSingleRefiner(Refiner refiner) {
        // If we don't have a set yet, then the refiner hasn't been run yet for this session.
        final boolean isFirstRun = !mSeenHintIds.containsKey(refiner);

        // Get the list of hint IDs this refiner has seen or create a new one.
        final Set<UUID> seenIds = isFirstRun ? new HashSet<>() : mSeenHintIds.get(refiner);

        // Ask the refiner's filter for the clusters of hints that the refiner wants as input.
        // If this is null then we skip the refiner.
        final Set<Set<PublishedContextHint>> interestedHintClusters =
                refiner.getInterestedHintClusters(mAllHints, seenIds, isFirstRun);

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, TextUtils.formatSimple(
                    "  Refiner wants %s cluster(s) of hints: %s%s",
                    interestedHintClusters != null ? interestedHintClusters.size() : 0,
                    refiner,
                    isFirstRun ? " (First run this flow)" : ""));
        }

        // If the refiner isn't interested in any clusters, skip it.
        if (interestedHintClusters == null || interestedHintClusters.isEmpty()) {
            return;
        }

        // Store the set of hint IDs seen by this refiner. This indicates that the refiner has
        // been run at least once in this workflow.
        if (isFirstRun) {
            mSeenHintIds.put(refiner, seenIds);
        }

        // Add all of the interesting hints' IDs to the set of IDs this refiner has seen.
        synchronized (mSeenHintIds) {
            for (Set<PublishedContextHint> hintCluster : interestedHintClusters) {
                for (PublishedContextHint hint : hintCluster) {
                    seenIds.add(hint.getContextHint().getHintId());
                }
            }
        }

        // Actually run the refiner on each cluster of hints.
        for (Set<PublishedContextHint> hintCluster : interestedHintClusters) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Slog.d(TAG, TextUtils.formatSimple(
                        "    Sending cluster of %s hints", hintCluster.size()));
                for (PublishedContextHint hint : hintCluster) {
                    Slog.d(TAG, "      Hint: " + hint);
                }
            }

            // Report this batch to the listener.
            mEventListener.onHintsSentToRefiner(mFlowId, hintCluster, refiner);

            // Keep track of this callback that we're waiting for.
            final RefinerCallback callback =
                    new RefinerCallback(mFragileSelf, mFlowId, hintCluster, refiner, mExecutor);

            mPendingRefinerCallbacks.add(callback);

            // Finally, actually call the refiner.
            refiner.refine(hintCluster, callback, mInsightConsumer);
        }
    }

    /**
     * Provides components needed by a RefinerWorkflow.
     * @hide
     */
    public interface ComponentProvider {
        /** Gets currently-configured refiners. */
        Collection<Refiner> getRefiners();

        /** Gets renderers that contain the specified properties. */
        Collection<Renderer> getRenderersWithProperties(int properties);
    }

    /**
     * Listener interface, for testing / logging purposes.
     * @hide
     */
    public interface EventListener {
        /** Called when a workflow is started. */
        default void onRefinerWorkflowStarted(
                long flowId, Collection<PublishedContextHint> hints) { }

        /** Called when a set of hints is sent to a refiner. */
        default void onHintsSentToRefiner(
                long flowId, Collection<PublishedContextHint> hints, Refiner refiner) { }

        /** Called when a set of hints is received from a refiner. */
        default void onHintsReceivedFromRefiner(
                long flowId, Collection<PublishedContextHint> hints, Refiner refiner) { }

        /** Called when a workflow stops. */
        default void onRefinerWorkflowFinished(long flowId) { }

        /** Called when a workflow has an unexpected error. */
        default void onRefinerWorkflowError(long flowId, Throwable t) { }
    }

    /** Explicit class for this callback so we don't hold a strong reference to the workflow. */
    private static final class RefinerCallback implements Consumer<Set<ContextHint>> {
        private FragileReference<RefinerWorkflow> mWorkflow;
        private final long mWorkflowId;
        private final Set<PublishedContextHint> mAttributionHints;
        private final Refiner mRefiner;
        private final ScheduledExecutorService mExecutor;

        RefinerCallback(
                FragileReference<RefinerWorkflow> workflow,
                long workflowId,
                Set<PublishedContextHint> attributionHints,
                Refiner refiner,
                ScheduledExecutorService executor) {
            mWorkflow = workflow;
            mWorkflowId = workflowId;
            mAttributionHints = attributionHints;
            mRefiner = refiner;
            mExecutor = executor;
        }

        @Override
        public void accept(Set<ContextHint> hints) {
            mExecutor.execute(() -> {
                final int hintCount = hints == null ? 0 : hints.size();

                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Slog.d(TAG, TextUtils.formatSimple(
                            "Received %s hints for workflow %s from %s",
                            hintCount, mWorkflowId, mRefiner));
                }

                // If mWorkflow is null then we've already reported hints.
                if (mWorkflow == null) {
                    Slog.e(TAG, TextUtils.formatSimple(
                            "Callback has been used with workflow %s, ignoring %s hints",
                            mWorkflowId, hintCount));
                    return;
                }

                // Grab the workflow, and set mWorkflow to null so it can't be used again.
                final RefinerWorkflow workflow = mWorkflow.get();
                mWorkflow = null;

                // If workflow is null then the workflow has timed out.
                if (workflow == null && hintCount > 0) {
                    Slog.w(TAG, TextUtils.formatSimple(
                            "Workflow %s has expired, ignoring %s hints", mWorkflowId, hintCount));
                    return;
                }

                // Workflow is still accepting hints, forward them on.
                workflow.addHints(hints, mAttributionHints, mRefiner, this);
            });
        }
    }
}
