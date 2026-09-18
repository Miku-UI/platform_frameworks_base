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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager.ProcessCapability;
import android.app.ActivityManager.ProcessState;
import android.ravenwood.annotation.RavenwoodKeepWholeClass;

import java.util.Objects;

/**
 * Edge that decides propagation through {@link ContentProviderConnectionInternal} from client to
 * content provider.
 */
@RavenwoodKeepWholeClass
class ProviderBindingEdge extends GraphEdge {
    private final @NonNull ContentProviderConnectionInternal mConn;

    ProviderBindingEdge(@NonNull ContentProviderConnectionInternal conn) {
        mConn = Objects.requireNonNull(conn);
    }

    @Override
    @NonNull
    ProcessNode getSource() {
        return mConn.getClient().getProcessNode();
    }

    @Override
    @Nullable
    ProcessNode getTarget() {
        final ProcessRecordInternal provider = mConn.getProvider().getHostProcess();
        // The target can be null if the ContentProviderConnection is not yet associated with a host
        // process, or if the host process has died.
        if (provider == null) return null;
        return provider.getProcessNode();
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
