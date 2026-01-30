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

import com.android.server.am.OomAdjuster;
import com.android.server.am.OomAdjusterImpl;

/**
 * An interface encapsulating common internal properties for a link between a content provider and
 * client.
 */
public interface ContentProviderConnectionInternal extends OomAdjusterImpl.Connection {
    /** Track the given proc state change. */
    void trackProcState(int procState, int seq);

    /** Returns the content provider in this connection. */
    ContentProviderRecordInternal getProvider();

    /** Returns the client process that initiated this content provider connection. */
    ProcessRecordInternal getClient();

    @Override
    default void computeHostOomAdjLSP(OomAdjuster oomAdjuster, ProcessRecordInternal host,
            ProcessRecordInternal client, long now, ProcessRecordInternal topApp, boolean doingAll,
            int oomAdjReason, int cachedAdj) {
        oomAdjuster.computeProviderHostOomAdjLSP(this, host, client, false);
    }

    @Override
    default boolean canAffectCapabilities() {
        return false;
    }
}
