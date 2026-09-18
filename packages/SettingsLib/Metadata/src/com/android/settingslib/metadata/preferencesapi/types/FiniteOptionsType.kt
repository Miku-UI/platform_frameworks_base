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

package com.android.settingslib.metadata.preferencesapi.types

import android.content.Context
import com.android.settingslib.metadata.preferencesapi.SafetyAnnotated
import java.util.concurrent.ConcurrentHashMap

private val optionsCache =
    ConcurrentHashMap<String, List<Pair<SafetyAnnotated<*>, SafetyAnnotated<String>>>>()

/**
 * Specialized type that describes the subset of `ApiType`s which have a finite set of values.
 */
interface FiniteOptionsType<InternalType, ExternalType : Any> : ApiType<InternalType, ExternalType> {

    /**
     * Returns all the values a preference with this type could have, together with their
     * description.
     */
    suspend fun getOptions(context: Context): List<Pair<SafetyAnnotated<ExternalType>, SafetyAnnotated<String>>>

    /**
     * Cached version of [getOptions] that retains the result in memory so it doesn't need to be
     * evaluated a second time.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun getCachedOptions(
        context: Context,
        bypassCache: Boolean = false,
    ): List<Pair<SafetyAnnotated<ExternalType>, SafetyAnnotated<String>>> {
        if (bypassCache) {
            optionsCache.remove(this.getKey())
        }
        var cached = optionsCache[this.getKey()]
        if (cached == null) {
            cached = getOptions(context)
            optionsCache[this.getKey()] = cached
        }
        return cached as List<Pair<SafetyAnnotated<ExternalType>, SafetyAnnotated<String>>>
    }
}

/**
 * Clears the options cache. Should only be used to prevent state leakage
 * in Robolectric/JUnit test suites.
 */
fun clearCache() {
    optionsCache.clear()
}

/** FiniteOptionsType for types which have the same internal and external type. */
interface DirectFiniteOptionsType<ExternalType : Any> : FiniteOptionsType<ExternalType, ExternalType> {
    override fun convertInternalToExternal(internalValue: ExternalType): ExternalType = internalValue
    override fun convertExternalToInternal(externalValue: ExternalType): ExternalType = externalValue
}