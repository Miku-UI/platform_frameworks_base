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

import com.android.server.companion.datatransfer.crossdevicesync.data.SharedDataStore
import com.android.server.companion.datatransfer.crossdevicesync.data.SharedDataStoreFactory
import com.android.server.companion.datatransfer.crossdevicesync.data.SharedDataStoreHandle

/** A test implementation of [SharedDataStoreFactory]. */
class TestSharedDataStoreFactory<T>(private val mFactory: SharedDataStoreFactory<T>) :
    SharedDataStoreFactory<T> {
    val dataStores: MutableList<SharedDataStore<T>> = ArrayList()
    private var mAsync: Boolean = false

    override fun create(handle: SharedDataStoreHandle<T>): SharedDataStore<T> {
        val dataStore = mFactory.create(handle)
        dataStores.add(dataStore)
        if (dataStore is FakeSharedDataStore<T>) {
            dataStore.setAsync(mAsync)
        }
        return dataStore
    }

    /** Return the first created data store. */
    fun firstDataStore(): SharedDataStore<T> = dataStores.first()

    /** Return the second created data store. */
    fun secondDataStore(): SharedDataStore<T> = dataStores[1]

    /** Return the last created data store. */
    fun lastDataStore(): SharedDataStore<T> = dataStores.last()

    /**
     * Make existing and future data stores async. Only works on {@link FakeSharedDataStore} and
     * doesn't work on real data stores.
     */
    fun setDataStoresAsync(async: Boolean): TestSharedDataStoreFactory<T> {
        mAsync = async
        dataStores.forEach { d ->
            if (d is FakeSharedDataStore<T>) {
                d.setAsync(async)
            } else {
                throw UnsupportedOperationException(
                    "setDataStoresAsync is not supported when using the real SharedDataStore" +
                        " implementation."
                )
            }
        }
        return this
    }
}
