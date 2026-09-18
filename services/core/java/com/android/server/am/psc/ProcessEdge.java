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

import static com.android.server.am.psc.OomAdjuster.CPU_TIME_REASON_NONE;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.ActivityManager.ProcessCapability;
import android.app.ActivityManager.ProcessState;
import android.ravenwood.annotation.RavenwoodKeepWholeClass;

import com.android.server.am.psc.OomAdjuster.CpuTimeReasons;

import java.util.Objects;

/**
 * Edge that decides system's propagation to the process based on the process's attributes in
 * isolation.
 */
@RavenwoodKeepWholeClass
final class ProcessEdge extends GraphEdge {
    private final @NonNull ProcessNode mNode;

    /**
     * The cached reasons for granting {@link ActivityManager#PROCESS_CAPABILITY_CPU_TIME} through
     * this edge.
     */
    private @CpuTimeReasons int mCpuTimeReasons = CPU_TIME_REASON_NONE;

    ProcessEdge(@NonNull ProcessNode node) {
        mNode = Objects.requireNonNull(node);
    }

    @CpuTimeReasons
    int getCpuTimeReasons() {
        return mCpuTimeReasons;
    }

    void addCpuTimeReasons(@CpuTimeReasons int reasons) {
        mCpuTimeReasons |= reasons;
    }

    void clearCpuTimeReasons() {
        mCpuTimeReasons = CPU_TIME_REASON_NONE;
    }

    /** Returns the {@link SystemNode} singleton instance. */
    @Override
    @NonNull
    SystemNode getSource() {
        return SystemNode.getInstance();
    }

    @Override
    @NonNull
    ProcessNode getTarget() {
        return mNode;
    }

    @Override
    @ProcessCapability
    int evaluateCapabilityFilter() {
        return CapabilityController.evaluateFilter(this);
    }

    @Override
    protected @ProcessState int evaluateProcState() {
        return ProcStateController.evaluateProcState(this);
    }
}
