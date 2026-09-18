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

import static android.app.ActivityManager.MIN_PROCESS_STATE;
import static android.app.ActivityManager.PROCESS_CAPABILITY_ALL;

import android.annotation.NonNull;
import android.app.ActivityManager.ProcessCapability;
import android.app.ActivityManager.ProcessState;
import android.ravenwood.annotation.RavenwoodKeepWholeClass;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Represents the system node in the process graph, acting as the root source of importance.
 */
@RavenwoodKeepWholeClass
final class SystemNode extends GraphNode {
    private static SystemNode sInstance;

    /**
     * Initializes the singleton instance of SystemNode.
     */
    public static void initInstance(@NonNull ProcessListInternal processList) {
        sInstance = new SystemNode(processList);
    }

    /**
     * Returns the singleton instance of SystemNode.
     *
     * @throws IllegalStateException if the instance has not been initialized.
     */
    public static @NonNull SystemNode getInstance() {
        if (sInstance == null) {
            throw new IllegalStateException("SystemNode is not initialized");
        }
        return sInstance;
    }

    /** A reference to the underlying ProcessListInternal to retrieve all processes. */
    private final @NonNull ProcessListInternal mProcessList;

    private SystemNode(@NonNull ProcessListInternal processList) {
        mProcessList = Objects.requireNonNull(processList);
    }

    @Override
    public void forEachOutgoingEdge(@NonNull Consumer<GraphEdge> consumer) {
        final List<? extends ProcessRecordInternal> processes = mProcessList.getLruProcessesLOSP();
        for (int i = processes.size() - 1; i >= 0; i--) {
            consumer.accept(processes.get(i).getProcessEdge());
        }
    }

    @Override
    public @ProcessCapability int getCapability() {
        return PROCESS_CAPABILITY_ALL;
    }

    /**
     * Returns the cached process state of the system node, which is always
     * {@link ActivityManager#MIN_PROCESS_STATE}.
     */
    @Override
    public @ProcessState int getProcState() {
        return MIN_PROCESS_STATE;
    }
}
