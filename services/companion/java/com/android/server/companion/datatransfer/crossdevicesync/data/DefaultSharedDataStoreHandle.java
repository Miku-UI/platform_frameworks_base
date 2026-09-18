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

import static com.android.server.companion.datatransfer.crossdevicesync.common.Utils.failedAndroidFuture;
import static com.android.server.companion.datatransfer.crossdevicesync.common.Utils.handleFailureAsync;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.infra.AndroidFuture;
import com.android.server.companion.datatransfer.crossdevicesync.data.storage.IStorage;
import com.android.server.companion.datatransfer.crossdevicesync.network.NetworkManager.Network;

/**
 * A {@link SharedDataStoreHandle} that represents a {@link SharedDataStore}.
 *
 * @param <T> the type of the data stored in the {@link SharedDataStore}
 */
public class DefaultSharedDataStoreHandle<T> implements SharedDataStoreHandle<T> {
    private static final String TAG = "SharedDataStoreHandle";

    private static final int STATE_OPEN = 0;
    private static final int STATE_DESTROYING = 1;
    private static final int STATE_DESTROYED = 2;

    private final String mName;
    private final Object mLock;
    private final DeviceNodeIdProvider mNodeIdProvider;
    private final IStorage mStorage;
    private final SharedDataStoreFactory<T> mSharedDataStoreFactory;

    @GuardedBy("mLock")
    @Nullable
    private SharedDataStore<T> mLockedDataStore;

    @GuardedBy("mLock")
    private int mState = STATE_OPEN;

    public DefaultSharedDataStoreHandle(
            @NonNull String name,
            @NonNull Object lock,
            @NonNull DeviceNodeIdProvider nodeIdProvider,
            @NonNull IStorage storage,
            @NonNull SharedDataStoreFactory<T> sharedDataStoreFactory) {
        mName = name;
        mLock = lock;
        mNodeIdProvider = nodeIdProvider;
        mStorage = storage;
        mSharedDataStoreFactory = sharedDataStoreFactory;
    }

    @NonNull
    @Override
    public String getName() {
        return mName;
    }

    @Override
    public boolean isLocked() {
        synchronized (mLock) {
            return mLockedDataStore != null;
        }
    }

    @Override
    public boolean lock(@NonNull SharedDataStore<T> dataStore) {
        synchronized (mLock) {
            requireOpenLocked();
            if (mLockedDataStore == null) {
                mLockedDataStore = dataStore;
                return true;
            } else {
                return false;
            }
        }
    }

    @Override
    public void unlock(@NonNull SharedDataStore<T> dataStore) {
        synchronized (mLock) {
            requireNotDestroyedLocked();
            if (mLockedDataStore == dataStore) {
                mLockedDataStore = null;
            }
        }
    }

    @Nullable
    @Override
    public SharedDataStore<T> getLockedDataStore() {
        synchronized (mLock) {
            return mLockedDataStore;
        }
    }

    @NonNull
    @Override
    public AndroidFuture<? extends SharedDataStore<T>> openDataStore(@NonNull Network network) {
        synchronized (mLock) {
            try {
                requireOpenLocked();
                if (mLockedDataStore != null) {
                    return AndroidFuture.completedFuture(mLockedDataStore);
                }
                return initializeDataStore(
                        mSharedDataStoreFactory.create(this), network, /* shouldRetry= */ true);
            } catch (IllegalStateException e) {
                return failedAndroidFuture(e);
            }
        }
    }

    @GuardedBy("mLock")
    private void requireOpenLocked() {
        if (mState != STATE_OPEN) {
            throw new IllegalStateException(
                    "Illegal state " + stateToString(mState) + " in handle " + mName);
        }
    }

    @GuardedBy("mLock")
    private void requireNotDestroyedLocked() {
        if (mState == STATE_DESTROYED) {
            throw new IllegalStateException(
                    "Illegal state " + stateToString(mState) + " in handle " + mName);
        }
    }

    @NonNull
    private AndroidFuture<SharedDataStore<T>> initializeDataStore(
            @NonNull SharedDataStore<T> dataStoreToInit,
            @NonNull Network network,
            boolean shouldRetry) {
        synchronized (mLock) {
            requireOpenLocked();
            AndroidFuture<SharedDataStore<T>> initFuture =
                    dataStoreToInit.init(network).thenApply(unused -> dataStoreToInit);
            return handleFailureAsync(
                    initFuture,
                    t -> {
                        if (shouldRetry
                                && (t instanceof IllegalSchemaChangeException
                                        || t instanceof SchemaValidationException)) {
                            Log.w(
                                    TAG,
                                    "Schema error occurred, deleting the data store "
                                            + dataStoreToInit,
                                    t);
                            return delete().thenCompose(
                                            unused ->
                                                    initializeDataStore(
                                                            mSharedDataStoreFactory.create(this),
                                                            network,
                                                            /* shouldRetry= */ false));
                        } else {
                            // Return the failed future as is.
                            return initFuture;
                        }
                    });
        }
    }

    private boolean doDelete(boolean forDestroying) {
        synchronized (mLock) {
            if (forDestroying) {
                requireNotDestroyedLocked();
            } else {
                requireOpenLocked();
            }
            if (isLocked()) {
                throw new IllegalStateException("Cannot delete locked data store " + mName);
            }
            if (mStorage.deleteDatabase()) {
                mNodeIdProvider.noteDataStoreDeletion(mName);
                Log.w(TAG, "Deleted data store \"" + mName + "\".");
                return true;
            } else {
                return false;
            }
        }
    }

    @NonNull
    @Override
    public AndroidFuture<Boolean> delete() {
        return delete(/* forDestroying= */ false);
    }

    @NonNull
    private AndroidFuture<Boolean> delete(boolean forDestroying) {
        synchronized (mLock) {
            try {
                if (forDestroying) {
                    requireNotDestroyedLocked();
                } else {
                    requireOpenLocked();
                }
                if (isLocked()) {
                    throw new IllegalStateException("Cannot delete locked data store " + mName);
                }
                Log.w(TAG, "Deleting data store \"" + mName + "\".");
                return mStorage.submitToIoThread(() -> doDelete(forDestroying));
            } catch (IllegalStateException e) {
                return failedAndroidFuture(e);
            }
        }
    }

    @Override
    public void destroy(boolean deleteDataStore) {
        synchronized (mLock) {
            if (mState != STATE_OPEN) {
                // Duplicate call
                return;
            }
            mState = STATE_DESTROYING;
            Log.i(TAG, "Destroying handle " + mName);
            AndroidFuture<?> future =
                    mLockedDataStore != null
                            ? mLockedDataStore.close()
                            : AndroidFuture.completedFuture(null);
            if (deleteDataStore) {
                future = future.thenCompose(unused -> delete(/* forDestroying= */ true));
            }
            future.whenComplete(
                    (unused, t) -> {
                        noteDestroyed();
                        mStorage.shutdownIoThread();
                    });
        }
    }

    private void noteDestroyed() {
        synchronized (mLock) {
            if (mState == STATE_DESTROYED) {
                return;
            }
            mState = STATE_DESTROYED;
            Log.i(TAG, "Handle " + mName + " is destroyed");
        }
    }

    @Override
    public String toString() {
        return TAG + "[" + mName + "]" + (isLocked() ? " (locked)" : "");
    }

    private static String stateToString(int state) {
        return switch (state) {
            case STATE_OPEN -> "OPEN";
            case STATE_DESTROYING -> "DESTROYING";
            case STATE_DESTROYED -> "DESTROYED";
            default -> "UNKNOWN(" + state + ")";
        };
    }
}
