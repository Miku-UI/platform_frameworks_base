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
package com.android.server.companion.datatransfer.crossdevicesync.data.fake

import com.android.server.companion.datatransfer.crossdevicesync.common.Utils
import com.android.server.companion.datatransfer.crossdevicesync.data.DeviceNodeIdProvider
import com.android.server.companion.datatransfer.crossdevicesync.data.SharedDataStore
import com.android.server.companion.datatransfer.crossdevicesync.data.SharedDataStoreFactory
import com.android.server.companion.datatransfer.crossdevicesync.data.SharedDataStoreHandle
import com.android.server.companion.datatransfer.crossdevicesync.data.storage.IStorage
import com.android.server.companion.datatransfer.crossdevicesync.network.NetworkManager
import com.android.internal.infra.AndroidFuture

/** Fake implementation of [SharedDataStoreHandle]. */
class FakeSharedDataStoreHandle<T>(
    private val name: String,
    private val mNodeIdProvider: DeviceNodeIdProvider,
    private val mStorage: IStorage,
    private val mDataStoreFactory: SharedDataStoreFactory<T>,
) : SharedDataStoreHandle<T> {
    override fun getName(): String = name

    private var mLockedDataStore: SharedDataStore<T>? = null

    override fun isLocked(): Boolean = mLockedDataStore != null

    override fun lock(dataStore: SharedDataStore<T>): Boolean {
        if (mLockedDataStore == null) {
            mLockedDataStore = dataStore
            return true
        } else {
            return false
        }
    }

    override fun unlock(dataStore: SharedDataStore<T>) {
        if (mLockedDataStore == dataStore) {
            mLockedDataStore = null
        }
    }

    override fun getLockedDataStore(): SharedDataStore<T>? = mLockedDataStore

    override fun openDataStore(network: NetworkManager.Network): AndroidFuture<SharedDataStore<T>> {
        try {
            val dataStore = mDataStoreFactory.create(this)
            return dataStore.init(network).thenApply { dataStore }
        } catch (e: Exception) {
            return Utils.failedAndroidFuture(e)
        }
    }

    override fun delete(): AndroidFuture<Boolean> = mStorage.submitToIoThread(this::doDelete)

    private fun doDelete(): Boolean {
        if (isLocked()) {
            throw IllegalStateException("locked")
        }
        if (mStorage.deleteDatabase()) {
            mNodeIdProvider.noteDataStoreDeletion(name)
            return true
        } else {
            return false
        }
    }

    override fun destroy(deleteDataStore: Boolean) {
        var future: AndroidFuture<*> =
            mLockedDataStore?.close() ?: AndroidFuture.completedFuture(true)
        if (deleteDataStore) {
            future = future.thenCompose { _ -> delete() }
        }
        future.whenComplete { _, _ -> mStorage.shutdownIoThread() }
    }
}
