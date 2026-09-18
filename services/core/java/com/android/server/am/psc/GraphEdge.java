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
package com.android.server.am.psc;

import static android.app.ActivityManager.PROCESS_CAPABILITY_NONE;
import static android.app.ActivityManager.PROCESS_STATE_UNKNOWN;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager.ProcessCapability;
import android.app.ActivityManager.ProcessState;
import android.ravenwood.annotation.RavenwoodKeepWholeClass;

/**
 * A directional edge representing a relationship between nodes (processes) in the process graph. It
 * serves as a generic base class for defining how process importance values such as capabilities,
 * process states, and scheduling groups are propagated and evaluated across different relationship
 * types, including service bindings, content provider connections, and intrinsic process
 * attributes.
 *
 * The source node of the edge is the process or system that provides the context for the
 * propagation, while the target node is the process whose state is being evaluated and updated.
 *
 * This class is utilized by specialized controllers such as {@link CapabilityController} during
 * graph traversal to determine process importance values by evaluating policies associated with
 * each edge.
 */
@RavenwoodKeepWholeClass
abstract class GraphEdge {
    /** The cached value of {@link #evaluateCapabilityFilter}. */
    private @ProcessCapability int mCachedCapabilityFilter = PROCESS_CAPABILITY_NONE;

    /** The process state propagated through this edge. */
    private @ProcessState int mProcState = PROCESS_STATE_UNKNOWN;

    @ProcessCapability
    int getCachedCapabilityFilter() {
        return mCachedCapabilityFilter;
    }

    /**
     * Updates the cached capability filter by evaluating the edge's properties.
     */
    void updateCachedCapabilityFilter() {
        mCachedCapabilityFilter = evaluateCapabilityFilter();
    }

    /**
     * Returns the source node of the edge.
     */
    abstract @NonNull GraphNode getSource();

    /**
     * Returns the target node of the edge, which is by construction always a {@link ProcessNode}.
     * For binding edges, returns {@code null} if the host process record is {@code null}.
     */
    abstract @Nullable ProcessNode getTarget();

    /**
     * Evaluates whether the edge propagates each capability.
     *
     * @return a bitmask where each bit indicates whether the edge propagates a capability.
     */
    abstract @ProcessCapability int evaluateCapabilityFilter();

    @ProcessState int getProcState() {
        return mProcState;
    }

    /**
     * Updates the process state by evaluating the edge's properties.
     */
    void updateProcState() {
        mProcState = Math.max(getSource().getProcState(), evaluateProcState());
    }

    /**
     * Evaluates the process state that should be propagated through this edge.
     *
     * @return the evaluated {@link ProcessState}.
     */
    protected abstract @ProcessState int evaluateProcState();
}
