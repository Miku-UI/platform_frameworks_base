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

import static android.app.ActivityManager.PROCESS_CAPABILITY_ALL;
import static android.app.ActivityManager.PROCESS_CAPABILITY_BFSL;
import static android.app.ActivityManager.PROCESS_CAPABILITY_CPU_TIME;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_CAMERA;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_LOCATION;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_MICROPHONE;
import static android.app.ActivityManager.PROCESS_CAPABILITY_IMPLICIT_CPU_TIME;
import static android.app.ActivityManager.PROCESS_CAPABILITY_INSTRUMENTATION_DEFAULTS;
import static android.app.ActivityManager.PROCESS_CAPABILITY_NONE;
import static android.app.ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE;
import static android.app.ActivityManager.PROCESS_STATE_BOUND_TOP;
import static android.app.ActivityManager.PROCESS_STATE_FOREGROUND_SERVICE;
import static android.app.ActivityManager.PROCESS_STATE_PERSISTENT;
import static android.app.ActivityManager.PROCESS_STATE_PERSISTENT_UI;
import static android.app.ActivityManager.PROCESS_STATE_TOP;
import static android.app.ActivityManager.PROCESS_STATE_UNKNOWN;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
import static android.media.audio.Flags.roForegroundAudioControl;

import static com.android.server.am.psc.Constants.FOREGROUND_APP_ADJ;
import static com.android.server.am.psc.OomAdjuster.ALL_CPU_TIME_CAPABILITIES;
import static com.android.server.am.psc.OomAdjuster.CPU_TIME_REASON_ALLOW_LIST;
import static com.android.server.am.psc.OomAdjuster.CPU_TIME_REASON_OTHER;
import static com.android.server.am.psc.OomAdjusterImpl.Connection.CPU_TIME_TRANSMISSION_NONE;

import android.annotation.NonNull;
import android.app.ActivityManager.ProcessCapability;
import android.app.ActivityManager.ProcessState;
import android.net.NetworkPolicyManager;
import android.ravenwood.annotation.RavenwoodKeepWholeClass;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.am.Flags;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.function.Consumer;

/** The class that computes capabilities and CPU time reasons for processes. */
@RavenwoodKeepWholeClass
final class CapabilityController {
    private static final String TAG = "CapabilityController";

    /**
     * A bitmask of all capabilities that can be granted by a foreground service.
     * Used for an optimization in {@link #evaluateForegroundServicePolicy}.
     */
    private static final @ProcessCapability int ALL_FOREGROUND_SERVICE_CAPABILITIES =
            PROCESS_CAPABILITY_FOREGROUND_LOCATION | PROCESS_CAPABILITY_FOREGROUND_CAMERA
                    | PROCESS_CAPABILITY_FOREGROUND_MICROPHONE
                    | PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL;

    private static final Consumer<GraphEdge> sUpdateTargetConsumer =
            CapabilityController::updateTargetCapability;

    /**
     * Evaluates a filter by combining all the policies of a process edge and also updates its CPU
     * time reasons.
     */
    static @ProcessCapability int evaluateFilter(@NonNull ProcessEdge edge) {
        edge.clearCpuTimeReasons();
        // No capability is granted to a non-running process.
        if (!edge.getTarget().isProcessRunning()) return PROCESS_CAPABILITY_NONE;
        // TODO(b/473696073): Optimize: If all the capabilities that a policy is able to give are
        //  already given by its preceding policies, the policy evaluation can be skipped.
        return evaluateMaxAdjPolicy(edge)
                | evaluateForegroundServicePolicy(edge)
                | evaluateProcStatePolicy(edge)
                | evaluateCpuTimePolicy(edge)
                | evaluateImplicitCpuTimePolicy(edge);
    }

    /** Evaluates a filter based on the process's max oom score (maxAdj). */
    private static @ProcessCapability int evaluateMaxAdjPolicy(@NonNull ProcessEdge edge) {
        if (edge.getTarget().getMaxAdj() <= FOREGROUND_APP_ADJ) {
            edge.addCpuTimeReasons(CPU_TIME_REASON_OTHER);
            return PROCESS_CAPABILITY_ALL;
        } else {
            return PROCESS_CAPABILITY_NONE;
        }
    }

    /**
     * Grants capabilities based on the process's foreground services. This includes BFSL for
     * non-short FGS, and other foreground capabilities including location, camera, microphone,
     * and audio control.
     */
    private static @ProcessCapability int evaluateForegroundServicePolicy(
            @NonNull ProcessEdge edge) {
        final ProcessNode node = edge.getTarget();
        if (!node.hasForegroundServices()) {
            return PROCESS_CAPABILITY_NONE;
        }
        // Grant BFSL for regular (non-short) FGS.
        @ProcessCapability int result = node.hasNonShortForegroundServices()
                ? PROCESS_CAPABILITY_BFSL : PROCESS_CAPABILITY_NONE;
        for (int i = node.getNumberOfRunningServices() - 1; i >= 0; i--) {
            result |= getForegroundServiceCapability(node, i);
            // Stop the iteration if we have already granted all foreground capabilities.
            if ((result & ALL_FOREGROUND_SERVICE_CAPABILITIES)
                    == ALL_FOREGROUND_SERVICE_CAPABILITIES) {
                break;
            }
        }
        return result;
    }

    /**
     * Gets foreground service capabilities from the node's service record at index {@code index}.
     */
    // LINT.IfChange(getForegroundServiceCapability)
    private static @ProcessCapability int getForegroundServiceCapability(@NonNull ProcessNode node,
            int index) {
        if (!node.isForegroundService(index) || !node.isFgsAllowedWiuForCapabilities(index)) {
            return PROCESS_CAPABILITY_NONE;
        }

        final int fgsType = node.getForegroundServiceType(index);
        @ProcessCapability int result = PROCESS_CAPABILITY_NONE;
        // Any FGS grants audio control if the flag is enabled.
        if (roForegroundAudioControl()) {
            result |= PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL;
        }

        if ((fgsType & FOREGROUND_SERVICE_TYPE_LOCATION) != 0) {
            result |= PROCESS_CAPABILITY_FOREGROUND_LOCATION;
        }

        if (node.getCachedCompatChangeCameraMicrophoneCapability()) {
            if ((fgsType & FOREGROUND_SERVICE_TYPE_CAMERA) != 0) {
                result |= PROCESS_CAPABILITY_FOREGROUND_CAMERA;
            }
            if ((fgsType & FOREGROUND_SERVICE_TYPE_MICROPHONE) != 0) {
                result |= PROCESS_CAPABILITY_FOREGROUND_MICROPHONE;
            }
        } else {
            // For apps that don't have the compat change enabled, any FGS grants
            // camera and microphone capabilities.
            result |= PROCESS_CAPABILITY_FOREGROUND_CAMERA
                    | PROCESS_CAPABILITY_FOREGROUND_MICROPHONE;
        }

        return result;
    }
    // LINT.ThenChange(OomAdjusterImpl.java:getForegroundServiceCapability)

    /** Evaluates process capabilities based on its process state. */
    // LINT.IfChange(evaluateProcStatePolicy)
    private static @ProcessCapability int evaluateProcStatePolicy(@NonNull ProcessEdge edge) {
        final ProcessNode node = edge.getTarget();
        final @ProcessState int procState = node.getCurProcState();
        final @ProcessCapability int networkCapabilities =
                NetworkPolicyManager.getDefaultProcessNetworkCapabilities(procState);
        final boolean hasActiveInstrumentation = node.hasActiveInstrumentation();
        @ProcessCapability int baseCapabilities;
        switch (procState) {
            case PROCESS_STATE_PERSISTENT:
            case PROCESS_STATE_PERSISTENT_UI:
            case PROCESS_STATE_TOP:
                // Note: When explicitCpuCapabilityForTopProcesses is false, top processes
                // inherit PROCESS_CAPABILITY_IMPLICIT_CPU_TIME from PROCESS_CAPABILITY_ALL.
                // In this legacy path no specific IMPLICIT_CPU_TIME_REASON is added by default
                // but this will be solved once the flag is completely rolled out.
                if (Flags.explicitCpuCapabilityForTopProcesses()) {
                    baseCapabilities =
                            PROCESS_CAPABILITY_ALL & (~ALL_CPU_TIME_CAPABILITIES); // BFSL allowed
                } else {
                    baseCapabilities = PROCESS_CAPABILITY_ALL;
                }
                break;
            case PROCESS_STATE_BOUND_TOP:
                if (hasActiveInstrumentation) {
                    baseCapabilities = PROCESS_CAPABILITY_INSTRUMENTATION_DEFAULTS;
                } else {
                    baseCapabilities = PROCESS_CAPABILITY_BFSL;
                }
                break;
            case PROCESS_STATE_FOREGROUND_SERVICE:
                if (hasActiveInstrumentation) {
                    baseCapabilities = PROCESS_CAPABILITY_INSTRUMENTATION_DEFAULTS;
                } else {
                    // Capabilities from foreground service are handled in
                    // evaluateForegroundServicePolicy.
                    baseCapabilities = PROCESS_CAPABILITY_NONE;
                }
                break;
            default:
                baseCapabilities = PROCESS_CAPABILITY_NONE;
                break;
        }
        if (hasActiveInstrumentation) {
            // TODO(b/471530626): Whether the process is running remote animation or not is ignored,
            //  which is different from current OomAdjuster impl. Revisit this if needed.
            // Regarding BFSL grants, there will be a final check using #revokeBfslCapability() that
            // revokes BFSL if the target process state is worse than BFGS.
            baseCapabilities |= PROCESS_CAPABILITY_BFSL;
        }
        return baseCapabilities | networkCapabilities;
    }
    // LINT.ThenChange(OomAdjuster.java:getDefaultCapability)

    /** Evaluates process CPU time capability and updates CPU time reasons on the process edge. */
    // LINT.IfChange(evaluateCpuTimePolicy)
    private static @ProcessCapability int evaluateCpuTimePolicy(@NonNull ProcessEdge edge) {
        final ProcessNode node = edge.getTarget();
        if (node.isCurAllowListed()) {
            edge.addCpuTimeReasons(CPU_TIME_REASON_ALLOW_LIST);
            return PROCESS_CAPABILITY_CPU_TIME;
        }
        // TODO: b/482137218 - Replace all usages of CPU_TIME_REASON_OTHER with explicit reasons.
        if (node.getCurProcState() <= PROCESS_STATE_TOP
                && node.getCurProcState() != PROCESS_STATE_UNKNOWN) {
            edge.addCpuTimeReasons(CPU_TIME_REASON_OTHER);
            return PROCESS_CAPABILITY_CPU_TIME;
        }
        if (node.hasForegroundActivities()) {
            edge.addCpuTimeReasons(CPU_TIME_REASON_OTHER);
            return PROCESS_CAPABILITY_CPU_TIME;
        }
        if (node.hasExecutingServices()) {
            edge.addCpuTimeReasons(CPU_TIME_REASON_OTHER);
            return PROCESS_CAPABILITY_CPU_TIME;
        }
        if (node.hasForegroundServices()) {
            edge.addCpuTimeReasons(CPU_TIME_REASON_OTHER);
            return PROCESS_CAPABILITY_CPU_TIME;
        }
        if (node.isReceivingBroadcast()) {
            edge.addCpuTimeReasons(CPU_TIME_REASON_OTHER);
            return PROCESS_CAPABILITY_CPU_TIME;
        }
        if (node.hasActiveInstrumentation()) {
            edge.addCpuTimeReasons(CPU_TIME_REASON_OTHER);
            return PROCESS_CAPABILITY_CPU_TIME;
        }
        return PROCESS_CAPABILITY_NONE;
    }
    // LINT.ThenChange(OomAdjuster.java:getCpuCapability)

    /** Evaluates implicit CPU time capability on the process edge. */
    private static @ProcessCapability int evaluateImplicitCpuTimePolicy(@NonNull ProcessEdge edge) {
        return edge.getTarget().hasIntrinsicImplicitCpuTime()
                ? PROCESS_CAPABILITY_IMPLICIT_CPU_TIME : PROCESS_CAPABILITY_NONE;
    }

    /**
     * Evaluates a filter by combining all the policies of a {@link ProviderBindingEdge}.
     */
    static @ProcessCapability int evaluateFilter(@NonNull ProviderBindingEdge edge) {
        return evaluateBfslPolicy(edge) | evaluateCpuTimePolicy(edge);
    }

    /** Evaluates whether a {@link ProviderBindingEdge} propagates BFSL. */
    private static @ProcessCapability int evaluateBfslPolicy(@NonNull ProviderBindingEdge unused) {
        // Regarding BFSL grants, there will be a final check using #revokeBfslCapability() that
        // revokes BFSL if the target process state is worse than BFGS. Always propagate BFSL here.
        return PROCESS_CAPABILITY_BFSL;
    }

    /** Evaluates whether a {@link ProviderBindingEdge} propagates CPU time capabilities. */
    private static @ProcessCapability int evaluateCpuTimePolicy(
            @NonNull ProviderBindingEdge unused) {
        // Always propagate CPU time capabilities since
        // ContentProviderConnectionInternal#cpuTimeTransmissionType() always returns
        // CPU_TIME_TRANSMISSION_NORMAL.
        return ALL_CPU_TIME_CAPABILITIES;
    }

    /**
     * Evaluates a filter by combining all the policies of a {@link ServiceBindingEdge}.
     */
    static @ProcessCapability int evaluateFilter(@NonNull ServiceBindingEdge edge) {
        // TODO: b/476905700 - Add more policies.
        return evaluateBfslPolicy(edge) | evaluateAudioPolicy(edge) | evaluateCpuTimePolicy(edge);
    }

    /** Evaluates whether a {@link ServiceBindingEdge} propagates BFSL. */
    private static @ProcessCapability int evaluateBfslPolicy(@NonNull ServiceBindingEdge unused) {
        // Regarding BFSL grants, there will be a final check using #revokeBfslCapability() that
        // revokes BFSL if the target process state is worse than BFGS. Always propagate BFSL here.
        return PROCESS_CAPABILITY_BFSL;
    }

    /** Evaluates whether a {@link ServiceBindingEdge} propagates audio control. */
    private static @ProcessCapability int evaluateAudioPolicy(@NonNull ServiceBindingEdge unused) {
        // Always propagate audio control.
        return PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL;
    }

    /** Evaluates whether a {@link ServiceBindingEdge} propagates CPU time capabilities. */
    private static @ProcessCapability int evaluateCpuTimePolicy(@NonNull ServiceBindingEdge edge) {
        // LINT.IfChange(getCpuTimeFilterFromTransmissionType)
        return edge.getCpuTimeTransmissionType() == CPU_TIME_TRANSMISSION_NONE
                ? PROCESS_CAPABILITY_NONE : ALL_CPU_TIME_CAPABILITIES;
        // LINT.ThenChange(OomAdjuster.java:getCpuCapabilitiesFromTransmissionType)
    }

    /** Evaluates output capabilities of an edge based on its filter and source input. */
    @VisibleForTesting
    static @ProcessCapability int evaluateOutputCapability(@NonNull GraphEdge edge) {
        return edge.getCachedCapabilityFilter() & edge.getSource().getCapability();
    }

    /** Evaluates capabilities for a newly attaching process. */
    @VisibleForTesting
    static @ProcessCapability int evaluateAttachingProcessCapability(
            @NonNull ProcessNode node) {
        return node.hasForegroundActivities() ? PROCESS_CAPABILITY_ALL : ALL_CPU_TIME_CAPABILITIES;
    }

    /**
     * Updates the capabilities of the target node by merging the output capabilities of the edge.
     *
     * @return {@code true} if the capabilities of the target node were updated.
     */
    @VisibleForTesting
    static boolean updateTargetCapability(@NonNull GraphEdge edge) {
        final ProcessNode target = edge.getTarget();
        @ProcessCapability int newCapability;
        // Incoming edges can be ignored for attaching processes.
        if (target.isPendingFinishAttach()) {
            newCapability = evaluateAttachingProcessCapability(target);
        } else {
            newCapability = target.getCapability() | evaluateOutputCapability(edge);
        }

        if (newCapability == target.getCapability()) {
            return false;
        }
        target.setCapability(newCapability);
        return true;
    }

    /** Revokes BFSL in the given {@code node} if its procState is worse than BFGS. */
    private static void revokeBfslCapability(@NonNull ProcessNode node) {
        if (node.getCurProcState() > PROCESS_STATE_BOUND_FOREGROUND_SERVICE) {
            node.setCapability(node.getCapability() & ~PROCESS_CAPABILITY_BFSL);
        }
    }

    private final Consumer<GraphEdge> mUpdateAndEnqueueTargetConsumer =
            this::updateAndEnqueueTarget;

    /** Queue for nodes whose output capabilities need to be propagated. */
    private final ArrayDeque<GraphNode> mPropagationQueue = new ArrayDeque<>();

    /**
     * Performs a partial update from a list of edges.
     *
     * @param edges          The list of edges that have triggered the update.
     * @param reachableNodes The list of nodes that are reachable from {@code edges} and may have
     *                       their capabilities changed in the update.
     */
    void update(@NonNull ArrayList<GraphEdge> edges,
            @NonNull ArrayList<ProcessNode> reachableNodes) {
        if (!mPropagationQueue.isEmpty()) {
            Slog.w(TAG, "mPropagationQueue is not empty before partial update");
            mPropagationQueue.clear();
        }

        // Update the filter for all target edges.
        for (int i = 0, size = edges.size(); i < size; i++) {
            edges.get(i).updateCachedCapabilityFilter();
        }

        // Clear the capabilities for all reachable nodes.
        for (int i = 0, size = reachableNodes.size(); i < size; i++) {
            reachableNodes.get(i).clearCapability();
        }
        // Compute initial capabilities for all reachable nodes. The loop is not merged into the
        // previous one because we want the cached capabilities on all reachable nodes to be
        // cleared first.
        for (int i = 0, size = reachableNodes.size(); i < size; i++) {
            // There are two kinds of edges here:
            // 1. Edges that have an unreachable source. For such an edge, its output capabilities
            //    remain unchanged during propagation, since its source remains unchanged.
            // 2. Edges that have a reachable source. For such an edge, its initial output
            //    capabilities are a subset of its final output capabilities after propagation,
            //    for capability computation is monotonic and all reachable nodes' capabilities
            //    have been cleared before this step. We don't exclude such edges from this step
            //    because the pre-computation here speeds up the convergence in the propagation
            //    step below.
            // TODO: b/493522532 - Consider whether to use caching to avoid recomputing the first
            //  type of edges' output capabilities.
            final ProcessNode node = reachableNodes.get(i);
            node.forEachIncomingEdge(sUpdateTargetConsumer);
            if (node.getCapability() != PROCESS_CAPABILITY_NONE) {
                enqueueNode(node);
            }
        }

        // Propagate capabilities from nodes in the queue until no more capabilities can be added.
        propagate();

        // Revoke PROCESS_CAPABILITY_BFSL for nodes whose procState is worse than BFGS.
        for (int i = 0, size = reachableNodes.size(); i < size; i++) {
            revokeBfslCapability(reachableNodes.get(i));
        }
    }

    /**
     * Propagates capabilities from nodes in {@link #mPropagationQueue} until the queue becomes
     * empty.
     */
    private void propagate() {
        while (!mPropagationQueue.isEmpty()) {
            final GraphNode node = mPropagationQueue.pollFirst();
            node.setEnqueued(false);
            // Check all its outgoing edges and enqueue targets that have gained new capabilities.
            node.forEachOutgoingEdge(mUpdateAndEnqueueTargetConsumer);
        }
    }

    /**
     * Updates the target node of {@code edge} and adds it to {@link #mPropagationQueue} if it has
     * gained any new capability.
     */
    private void updateAndEnqueueTarget(@NonNull GraphEdge edge) {
        if (updateTargetCapability(edge)) {
            enqueueNode(edge.getTarget());
        }
    }

    /** Adds {@code node} to {@link #mPropagationQueue} if it is not enqueued. */
    private void enqueueNode(@NonNull GraphNode node) {
        if (!node.isEnqueued()) {
            node.setEnqueued(true);
            mPropagationQueue.add(node);
        }
    }
}
