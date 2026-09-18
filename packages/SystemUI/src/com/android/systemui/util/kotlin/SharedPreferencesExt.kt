/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.util.kotlin

import android.content.SharedPreferences
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow

object SharedPreferencesExt {
    /** Returns a flow of [Unit] that is invoked each time shared preference is updated. */
    fun SharedPreferences.observe(): Flow<Unit> = conflatedCallbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        registerOnSharedPreferenceChangeListener(listener)
        awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun SharedPreferences.observeBoolean(key: String, defValue: Boolean): Flow<Boolean> =
        observeValue(key) { getBoolean(key, defValue) }

    fun SharedPreferences.observeLong(key: String, defValue: Long): Flow<Long> =
        observeValue(key) { getLong(key, defValue) }

    fun SharedPreferences.observeString(key: String, defValue: String): Flow<String> =
        observeValue(key) { getString(key, defValue) ?: defValue }

    private fun <T> SharedPreferences.observeValue(
        key: String,
        fetchValue: SharedPreferences.() -> T,
    ): Flow<T> = conflatedCallbackFlow {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
                if (k == key) {
                    trySend(fetchValue())
                }
            }
        trySend(fetchValue())
        registerOnSharedPreferenceChangeListener(listener)
        awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
