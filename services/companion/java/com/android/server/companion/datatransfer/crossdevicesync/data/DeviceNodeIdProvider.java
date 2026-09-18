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
package com.android.server.companion.datatransfer.crossdevicesync.data;

/** Interface that provides unique id that identifies current device in the sync network. */
public interface DeviceNodeIdProvider {

    /**
     * Get a unique string that identifies current device in the sync network. This will be used by
     * shared data store for data consistency management, and is independent of the association id
     * in companion device manager.
     *
     * <p>Note: node id is unique per data store and is not shared across data stores. It will be
     * initialized when a data store is first created, and will be destroyed when a data store is
     * deleted. If the data store with the same name is initialized again after deletion, it's
     * guaranteed to get a new node id that is different from the previous one. This is important
     * for a distributed data base, since a re-created data store is treated as a new device in the
     * sync network.
     */
    String getOrCreateNodeIdForDataStore(String dataStore);

    /**
     * Note deletion of a data store. After calling this method, the node id associated with the
     * data store will no longer be available. The next {@link
     * #getOrCreateNodeIdForDataStore(String)} call using the same data store name will return a new
     * node id.
     */
    void noteDataStoreDeletion(String dataStore);
}
