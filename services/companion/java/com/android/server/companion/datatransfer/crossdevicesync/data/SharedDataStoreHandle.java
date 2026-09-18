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

package com.android.server.companion.datatransfer.crossdevicesync.data;

import android.annotation.NonNull;
import android.annotation.Nullable;

import com.android.internal.infra.AndroidFuture;
import com.android.server.companion.datatransfer.crossdevicesync.network.NetworkManager.Network;

import com.google.android.submerge.Converter;

/**
 * A handle to a {@link SharedDataStore}. Used to perform operations without opening a {@link
 * SharedDataStore}.
 *
 * @param <T> The type of the data stored in the data store.
 */
public interface SharedDataStoreHandle<T> {

    /** Get the data store name. */
    @NonNull
    String getName();

    /**
     * Check if the data store is locked. A data store is locked when it's open or in the middle of
     * close. While it's locked, it can't be deleted or re-initialized.
     */
    boolean isLocked();

    /** Lock this handle. */
    boolean lock(@NonNull SharedDataStore<T> dataStore);

    /** Unlock this handle. */
    void unlock(@NonNull SharedDataStore<T> dataStore);

    /** If the data store is locked, get the locked data store. */
    @Nullable
    SharedDataStore<T> getLockedDataStore();

    /**
     * Open and initialize a data store. If the data store is already locked, return the locked data
     * store instead. If a schema error occurs when opening the data store, the data store will be
     * deleted and open will be retried.
     *
     * @param network the network to be used for syncing
     * @return a future containing the initialized data store, or an exception
     */
    @NonNull
    AndroidFuture<? extends SharedDataStore<T>> openDataStore(@NonNull Network network);

    /**
     * Delete the shared data store. This operation is non-recoverable.
     *
     * @return a {@link AndroidFuture} that completes with a boolean indicating if the data store is
     *     successfully deleted.
     * @throws IllegalStateException if the data store is locked.
     */
    @NonNull
    AndroidFuture<Boolean> delete();

    /** Orderly close the data store if it's open, and release all resources. */
    default void destroy() {
        destroy(false);
    }

    /**
     * Orderly close the data store if it's open, and release all resources. Can optionally delete
     * the data store as part of the clean-up.
     *
     * @param deleteDataStore Whether to delete the data store as part of the clean-up.
     */
    void destroy(boolean deleteDataStore);

    /** A factory to create a {@link SharedDataStoreHandle}. */
    interface Factory {
        /**
         * Creates a {@link SharedDataStoreHandle}
         *
         * @param name The name of the data store
         * @param converter used to serialize/deserialize the data
         * @param schemaProvider The {@link SchemaProvider} for the data store
         * @param <T> The type of the data stored in the data store.
         * @return A {@link SharedDataStoreHandle} for the given data store.
         */
        @NonNull
        <T> SharedDataStoreHandle<T> create(
                @NonNull String name,
                @NonNull Converter<T> converter,
                @NonNull SchemaProvider<T> schemaProvider);
    }
}
