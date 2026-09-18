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

package com.android.server.am.psc;

import static android.app.ActivityManager.MAX_PROCESS_STATE;
import static android.app.ActivityManager.PROCESS_STATE_UNKNOWN;

import android.annotation.NonNull;
import android.app.ActivityManager.ProcessState;
import android.ravenwood.annotation.RavenwoodKeepWholeClass;
import android.util.BucketPriorityQueue;
import android.util.Slog;

import java.util.List;
import java.util.function.Consumer;

/**
 * The controller responsible for computing and managing {@link ProcessState} using
 * the binding-based graph traversal algorithm.
 *
 * <p>During calculation:
 * <ul>
 *   <li>{@link android.app.ActivityManager#PROCESS_STATE_UNKNOWN} represents the <b>null state</b>.
 *   It indicates that a node's or edge's value hasn't been calculated or has been cleared
 *   before calculation.</li>
 *   <li>{@link android.app.ActivityManager#MAX_PROCESS_STATE} represents the <b>identity
 *   element</b> for value aggregation via {@link Math#min(int, int)}. A node's process state is
 *   determined by the smallest value (highest priority) among its incoming edges.</li>
 * </ul>
 */
@RavenwoodKeepWholeClass
final class ProcStateController {
    private static final String TAG = "ProcStateController";

    private final BucketPriorityQueue<GraphEdge> mEdgeQueue =
            new BucketPriorityQueue<>(MAX_PROCESS_STATE);

    /**
     * Performs a full update of process states for all processes in the system.
     *
     * <p>This method initiates a system-wide traversal starting from the root {@code systemNode}.
     * It resets the state of all directly reachable nodes and propagates the most important
     * states through the process graph using a Dijkstra-like algorithm.
     *
     * @param systemNode The root node representing the system from which traversal begins.
     */
    void fullUpdate(@NonNull GraphNode systemNode) {
        if (!mEdgeQueue.isEmpty()) {
            Slog.wtf(TAG, "mEdgeQueue is not empty before fullUpdate, dropping stale edges.");
            mEdgeQueue.clear();
        }

        // Reset all the nodes, evaluate and enqueue all the ProcessEdges.
        systemNode.forEachOutgoingEdge(mResetTargetAndEvaluateEdgeConsumer);

        propagate();
    }

    /**
     * Performs a partial update of process states for a specific set of graph edges.
     *
     * @param edges          The list of graph edges that have changed and need re-evaluation.
     * @param reachableNodes The list of nodes that are affected by these changes and need
     *                       re-computation.
     */
    void partialUpdate(@NonNull List<GraphEdge> edges, @NonNull List<ProcessNode> reachableNodes) {
        // TODO: b/479360024 - Implement the method.
    }

    /**
     * Propagates process states through the graph using a Dijkstra-like traversal.
     *
     * <p>This traversal ensures that each node is assigned the highest priority process state
     * (lowest numerical value) from its incoming edges.
     */
    private void propagate() {
        while (!mEdgeQueue.isEmpty()) {
            final GraphEdge edge = mEdgeQueue.remove();
            final ProcessNode target = edge.getTarget();

            // In Dijkstra algorithm, the first dequeued value must be the best option. So we should
            // skip the result if the target node is already set.
            if (target == null || target.getProcState() != PROCESS_STATE_UNKNOWN) {
                continue;
            }

            target.setProcState(edge.getProcState());
            target.forEachOutgoingEdge(mEvaluateAndEnqueueEdgeConsumer);
        }
    }

    /**
     * Resets the target node's cached process state, evaluates the edge's process state,
     * and adds the edge to the bucket queue for traversal.
     *
     * @param edge The graph edge pointing to the node to be reset and evaluated.
     */
    private void resetTargetAndEvaluateEdge(@NonNull GraphEdge edge) {
        final ProcessNode node = edge.getTarget();
        if (node == null) {
            return;
        }

        node.setProcState(PROCESS_STATE_UNKNOWN);
        evaluateAndEnqueueEdge(edge);
    }
    private final Consumer<GraphEdge> mResetTargetAndEvaluateEdgeConsumer =
            this::resetTargetAndEvaluateEdge;

    /**
     * Evaluates the edge's process state and adds it to the bucket queue for traversal.
     *
     * @param edge The graph edge to be evaluated and enqueued.
     */
    private void evaluateAndEnqueueEdge(@NonNull GraphEdge edge) {
        edge.updateProcState();
        mEdgeQueue.add(edge, edge.getProcState());
    }
    private final Consumer<GraphEdge> mEvaluateAndEnqueueEdgeConsumer =
            this::evaluateAndEnqueueEdge;

    /**
     * Evaluates the {@link ProcessState} propagated from the given {@link ProcessEdge} to
     * its target.
     */
    static @ProcessState int evaluateProcState(@NonNull ProcessEdge edge) {
        // TODO: b/479360024 - Implement the method.
        return MAX_PROCESS_STATE;
    }

    /**
     * Evaluates the {@link ProcessState} propagated from the given {@link ServiceBindingEdge} to
     * its target.
     */
    static @ProcessState int evaluateProcState(@NonNull ServiceBindingEdge edge) {
        // TODO: b/479360024 - Implement the method.
        return MAX_PROCESS_STATE;
    }

    /**
     * Evaluates the {@link ProcessState} propagated from the given {@link ProviderBindingEdge} to
     * its target.
     */
    static @ProcessState int evaluateProcState(@NonNull ProviderBindingEdge edge) {
        // TODO: b/479360024 - Implement the method.
        return MAX_PROCESS_STATE;
    }
}
