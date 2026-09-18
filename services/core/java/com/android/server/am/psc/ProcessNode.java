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

import static android.app.ActivityManager.PROCESS_CAPABILITY_NONE;
import static android.app.ActivityManager.PROCESS_STATE_UNKNOWN;

import static com.android.server.am.psc.PlatformCompatCache.CACHED_COMPAT_CHANGE_CAMERA_MICROPHONE_CAPABILITY;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.ActivityManager.ProcessCapability;
import android.app.ActivityManager.ProcessState;
import android.content.pm.ServiceInfo.ForegroundServiceType;
import android.ravenwood.annotation.RavenwoodKeepWholeClass;

import com.android.server.am.Flags;

import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Represents a process within the process graph. Each node is embedded within a
 * {@link ProcessRecordInternal}.
 *
 * <p>This class stores the calculated importance values such as capabilities and the process state
 * for the process. The calculated values are used by specialized controllers such as
 * {@link CapabilityController} to evaluate the node's outgoing edges, which are service or provider
 * bindings, to determine the importance of the edges' target processes during graph traversal.
 */
@RavenwoodKeepWholeClass
class ProcessNode extends GraphNode {
    /** A reference to the underlying ProcessRecordInternal. */
    private final @NonNull ProcessRecordInternal mProc;

    /** Capabilities granted to this process. */
    private @ProcessCapability int mCapability = PROCESS_CAPABILITY_NONE;

    /**
     * Whether this process node has {@link ActivityManager#PROCESS_CAPABILITY_IMPLICIT_CPU_TIME}
     * intrinsically (i.e., not propagated from other processes).
     *
     * <p>If it is true, this process will get {@link OomAdjuster#IMPLICIT_CPU_TIME_REASON_OTHER}.
     */
    // TODO(b/479393330): Remove this property and evaluate implicit CPU time directly from
    //  ProcessRecordInternal once the computation can be decoupled from oomadj.
    private boolean mHasIntrinsicImplicitCpuTime;

    /** The process state of this process, calculated by {@link ProcStateController}. */
    private @ProcessState int mProcState = PROCESS_STATE_UNKNOWN;

    ProcessNode(@NonNull ProcessRecordInternal proc) {
        mProc = Objects.requireNonNull(proc);
    }

    // LINT.IfChange(forEachOutgoingEdge)
    @Override
    public void forEachOutgoingEdge(@NonNull Consumer<GraphEdge> consumer) {
        // Iterate over all outgoing ServiceBindingEdges from this node to non-sandbox service
        // nodes.
        final ProcessServiceRecordInternal psr = mProc.getServices();
        for (int i = psr.numberOfConnections() - 1; i >= 0; i--) {
            final ServiceBindingEdge edge = psr.getConnectionInternalAt(i).getServiceBindingEdge();
            edge.updateTarget();
            final ProcessNode target = edge.getTarget();
            if (target == null || this == target || edge.isSandboxAttributedConnection()) {
                continue;
            }
            consumer.accept(edge);
        }

        // Iterate over all outgoing ServiceBindingEdges from this node to sandbox service nodes.
        for (int i = psr.numberOfSdkSandboxConnections() - 1; i >= 0; i--) {
            final ServiceBindingEdge edge = psr.getSdkSandboxConnectionInternalAt(i)
                    .getServiceBindingEdge();
            edge.updateTarget();
            final ProcessNode target = edge.getTarget();
            if (target == null || this == target) {
                continue;
            }
            consumer.accept(edge);
        }

        // Iterate over all outgoing ProviderBindingEdges from this node to provider nodes.
        final ProcessProviderRecordInternal ppr = mProc.getProviders();
        for (int i = ppr.numberOfProviderConnections() - 1; i >= 0; i--) {
            final ProviderBindingEdge edge = ppr.getProviderConnectionInternalAt(i)
                    .getProviderBindingEdge();
            final ProcessNode target = edge.getTarget();
            if (target == null || this == target) {
                continue;
            }
            consumer.accept(edge);
        }
    }
    // LINT.ThenChange(OomAdjusterImpl.java:forEachConnectionLSP)

    /**
     * Streams the incoming edges of this node to {@code consumer}. This method should only be used
     * when {@link Flags#enableCapabilityControllerComputation} is enabled.
     */
    // LINT.IfChange(forEachIncomingEdge)
    void forEachIncomingEdge(@NonNull Consumer<GraphEdge> consumer) {
        // Visit the incoming ProcessEdge.
        consumer.accept(mProc.getProcessEdge());

        // Iterate over all incoming ServiceBindingEdges from client nodes to this node.
        final ProcessServiceRecordInternal psr = mProc.getServices();
        for (int i = psr.numberOfRunningServices() - 1; i >= 0; i--) {
            final ServiceRecordInternal s = psr.getRunningServiceInternalAt(i);
            for (int j = s.getConnectionsSize() - 1; j >= 0; j--) {
                final ArrayList<? extends ConnectionRecordInternal> clist = s.getConnectionAt(j);
                for (int k = clist.size() - 1; k >= 0; k--) {
                    final ServiceBindingEdge edge = clist.get(k).getServiceBindingEdge();
                    if (this == edge.getSource()) continue;
                    edge.setTarget(this);
                    consumer.accept(edge);
                }
            }
        }

        // Iterate over all incoming ProviderBindingEdges from client nodes to this node.
        final ProcessProviderRecordInternal ppr = mProc.getProviders();
        for (int i = ppr.numberOfProviders() - 1; i >= 0; i--) {
            final ContentProviderRecordInternal cpr = ppr.getProviderInternalAt(i);
            for (int j = cpr.numberOfConnections() - 1; j >= 0; j--) {
                final ProviderBindingEdge edge = cpr.getConnectionsAt(j).getProviderBindingEdge();
                if (this == edge.getSource()) continue;
                consumer.accept(edge);
            }
        }
    }
    // LINT.ThenChange(OomAdjusterImpl.java:forEachClientConnectionLSP)

    @Override
    public final @ProcessCapability int getCapability() {
        return mCapability;
    }

    final void clearCapability() {
        mCapability = PROCESS_CAPABILITY_NONE;
    }

    final void setCapability(@ProcessCapability int capability) {
        mCapability = capability;
    }

    // TODO: b/483182189 - Move state getters below to ProcessEdge.
    boolean hasIntrinsicImplicitCpuTime() {
        return mHasIntrinsicImplicitCpuTime;
    }

    void setHasIntrinsicImplicitCpuTime(boolean hasIntrinsicImplicitCpuTime) {
        mHasIntrinsicImplicitCpuTime = hasIntrinsicImplicitCpuTime;
    }

    @Override
    public @ProcessState int getProcState() {
        return mProcState;
    }

    void setProcState(@ProcessState int procState) {
        mProcState = procState;
    }

    int getMaxAdj() {
        return mProc.getMaxAdj();
    }

    boolean isProcessRunning() {
        return mProc.isProcessRunning();
    }

    boolean isCurAllowListed() {
        final UidRecordInternal uidRec = mProc.getUidRecord();
        return uidRec != null && uidRec.isCurAllowListed();
    }

    boolean isReceivingBroadcast() {
        return mProc.getReceivers().isReceivingBroadcast();
    }

    boolean isPendingFinishAttach() {
        return mProc.isPendingFinishAttach();
    }

    boolean hasActiveInstrumentation() {
        return mProc.hasActiveInstrumentation();
    }

    boolean hasForegroundServices() {
        return mProc.getServices().hasForegroundServices();
    }

    boolean hasNonShortForegroundServices() {
        return mProc.getServices().hasNonShortForegroundServices();
    }

    boolean hasForegroundActivities() {
        return mProc.getHasForegroundActivities();
    }

    boolean hasExecutingServices() {
        return mProc.getServices().hasExecutingServices();
    }

    int getNumberOfRunningServices() {
        return mProc.getServices().numberOfRunningServices();
    }

    /**
     * @param index The index of the running service to check.
     * @return {@code true} if the service at the given index is a foreground service.
     */
    boolean isForegroundService(int index) {
        return getRunningServiceAt(index).isForeground();
    }

    /**
     * @param index The index of the running service to check.
     *              <p>Note: The caller is responsible for ensuring that the service at the given
     *              index is a foreground service (e.g., by calling {@link #isForegroundService})
     *              before calling this method.
     * @return {@code true} if the FGS at the given index is allowed to have while-in-use
     * capabilities.
     */
    boolean isFgsAllowedWiuForCapabilities(int index) {
        return getRunningServiceAt(index).isFgsAllowedWiu_forCapabilities();
    }

    /**
     * @param index The index of the running service to get the FGS type from.
     *              <p>Note: The caller is responsible for ensuring that the service at the given
     *              index is a foreground service (e.g., by calling {@link #isForegroundService})
     *              before calling this method.
     * @return The foreground service type of the service at the given index.
     */
    @ForegroundServiceType
    int getForegroundServiceType(int index) {
        return getRunningServiceAt(index).getForegroundServiceType();
    }

    boolean getCachedCompatChangeCameraMicrophoneCapability() {
        return mProc.getCachedCompatChange(
                CACHED_COMPAT_CHANGE_CAMERA_MICROPHONE_CAPABILITY);
    }

    @ProcessState
    int getCurProcState() {
        return mProc.getCurProcState();
    }

    private ServiceRecordInternal getRunningServiceAt(int index) {
        return mProc.getServices().getRunningServiceInternalAt(index);
    }
}
