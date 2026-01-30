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

/**
 * Internal abstraction for accessing content provider-related information within a process.
 * It is primarily used by the OomAdjuster.
 */
public abstract class ProcessProviderRecordInternal {
    /** The last time someone else was using a provider in this process. */
    private long mLastProviderTime = Long.MIN_VALUE;

    public long getLastProviderTime() {
        return mLastProviderTime;
    }

    public void setLastProviderTime(long lastProviderTime) {
        mLastProviderTime = lastProviderTime;
    }

    /** Returns the number of published providers in this process. */
    public abstract int numberOfProviders();

    /**
     * Returns the {@link ContentProviderRecordInternal} at the specified index from the list of
     * published providers.
     */
    public abstract ContentProviderRecordInternal getProviderAt(int index);

    /** Returns the number of content provider connections associated with this process. */
    public abstract int numberOfProviderConnections();

    /**
     * Returns the {@link ContentProviderConnectionInternal} at the specified index
     * from the list of connected providers.
     */
    public abstract ContentProviderConnectionInternal getProviderConnectionAt(int index);
}
