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

import android.annotation.NonNull;
import android.app.ActivityManager.ProcessState;
import android.os.Binder;

import com.android.server.am.Flags;

/**
 * An abstract class encapsulating common internal properties for a link between a content provider
 * and client.
 */
public abstract class ContentProviderConnectionInternal extends Binder implements
        OomAdjusterImpl.Connection {
    /**
     * The provider binding edge from the client to the content provider.
     * This is {@code null} unless {@link Flags#enableCapabilityControllerComputation} is true.
     */
    private final ProviderBindingEdge mProviderBindingEdge;

    /** Track the given proc state change. */
    public abstract void trackProcState(@ProcessState int procState, int seq);

    /** Returns the content provider in this connection. */
    public abstract ContentProviderRecordInternal getProvider();

    /** Returns the client process that initiated this content provider connection. */
    public abstract @NonNull ProcessRecordInternal getClient();

    public ContentProviderConnectionInternal() {
        if (Flags.enableCapabilityControllerComputation()) {
            mProviderBindingEdge = new ProviderBindingEdge(this);
        } else {
            mProviderBindingEdge = null;
        }
    }

    /** This is {@code null} unless {@link Flags#enableCapabilityControllerComputation} is true. */
    final ProviderBindingEdge getProviderBindingEdge() {
        return mProviderBindingEdge;
    }

    @Override
    public void computeHostOomAdjLSP(OomAdjuster oomAdjuster, ProcessRecordInternal host,
            ProcessRecordInternal client, long now, ProcessRecordInternal topApp, boolean doingAll,
            int oomAdjReason, int cachedAdj) {
        oomAdjuster.computeProviderHostOomAdjLSP(this, host, client, false);
    }

    @Override
    public boolean canAffectCapabilities() {
        return false;
    }
}
