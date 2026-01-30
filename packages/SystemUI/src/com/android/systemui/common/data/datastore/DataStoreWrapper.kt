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

package com.android.systemui.common.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Wraps an instance of Jetpack [DataStore]; intended to make unit testing possible.
 *
 * The sacrifice made is that all values must be [String] (but that's okay, you can convert anything
 * to/from `String`).
 */
interface DataStoreWrapper {
    val data: Flow<Map<String, String>>

    suspend fun edit(block: (MutableMap<String, String>) -> Unit)
}

class DataStoreWrapperImpl(private val dataStore: DataStore<Preferences>) : DataStoreWrapper {

    override val data: Flow<Map<String, String>> = dataStore.data.map { it.toStringMap() }

    override suspend fun edit(block: (MutableMap<String, String>) -> Unit) {
        dataStore.edit { prefs ->
            val mutableMap = prefs.toStringMap().toMutableMap()
            block(mutableMap)
            prefs.clear()
            mutableMap.forEach { (name, value) -> prefs[stringPreferencesKey(name)] = value }
        }
    }

    private fun Preferences.toStringMap(): Map<String, String> {
        return this.asMap().map { (key, value) -> key.name to value.toString() }.toMap()
    }
}
