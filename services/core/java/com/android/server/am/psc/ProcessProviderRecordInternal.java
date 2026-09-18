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

import android.util.ArrayMap;

import java.util.ArrayList;

/**
 * Internal abstraction for accessing content provider-related information within a process.
 * It is primarily used by the OomAdjuster.
 */
public class ProcessProviderRecordInternal {
    /** The last time someone else was using a provider in this process. */
    private long mLastProviderTime = Long.MIN_VALUE;

    /**
     * Map of all content providers published by this process.
     * The key is the provider's class name, and the value is the corresponding
     * {@link ContentProviderRecordInternal}.
     */
    private final ArrayMap<String, ContentProviderRecordInternal> mPubProviders = new ArrayMap<>();

    /** All {@link ContentProviderConnectionInternal} this process is using. */
    private final ArrayList<ContentProviderConnectionInternal> mConProviders = new ArrayList<>();

    public long getLastProviderTime() {
        return mLastProviderTime;
    }

    void setLastProviderTime(long lastProviderTime) {
        mLastProviderTime = lastProviderTime;
    }

    /** Returns the number of published providers in this process. */
    public int numberOfProviders() {
        return mPubProviders.size();
    }

    /** Retrieves the name of the published content provider at the specified index. */
    protected String getProviderNameAt(int index) {
        return mPubProviders.keyAt(index);
    }

    /**
     * Returns the {@link ContentProviderRecordInternal} at the specified index from the list of
     * published providers.
     */
    protected ContentProviderRecordInternal getProviderInternalAt(int index) {
        return mPubProviders.valueAt(index);
    }

    /** Checks if this process publishes a content provider with the given name. */
    boolean hasProvider(String name) {
        return mPubProviders.containsKey(name);
    }

    /** Returns the {@link ContentProviderRecordInternal} for the given name. */
    protected ContentProviderRecordInternal getProviderInternal(String name) {
        return mPubProviders.get(name);
    }

    /** Adds a new published content provider to this process. */
    void installProvider(String name, ContentProviderRecordInternal provider) {
        mPubProviders.put(name, provider);
    }

    /** Removes a published content provider from this process. */
    void removeProvider(String name) {
        mPubProviders.remove(name);
    }

    /** Removes all published content providers from this process. */
    protected void clearProvider() {
        mPubProviders.clear();
    }

    /**
     * Ensures that the internal map for published providers can hold at least the given
     * number of items.
     */
    void ensureProviderCapacity(int capacity) {
        mPubProviders.ensureCapacity(capacity);
    }

    /** Returns the number of content provider connections associated with this process. */
    public int numberOfProviderConnections() {
        return mConProviders.size();
    }

    /**
     * Returns the {@link ContentProviderConnectionInternal} at the specified index
     * from the list of connected providers.
     */
    public ContentProviderConnectionInternal getProviderConnectionInternalAt(int index) {
        return mConProviders.get(index);
    }

    /** Adds a content provider connection to this process's list of connections. */
    void addProviderConnection(ContentProviderConnectionInternal connection) {
        mConProviders.add(connection);
    }

    /** Removes a content provider connection from this process's list of connections. */
    boolean removeProviderConnection(ContentProviderConnectionInternal connection) {
        return mConProviders.remove(connection);
    }

    /** Removes all content provider connections from this process. */
    protected void clearProviderConnection() {
        mConProviders.clear();
    }
}
