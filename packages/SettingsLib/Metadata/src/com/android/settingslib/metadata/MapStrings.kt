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

package com.android.settingslib.metadata

import android.os.Bundle

/**
 * Converts a Map<String, String> instance into an Android [Bundle].
 *
 * @return A new [Bundle] containing all the key-value pairs stored in this object.
 */
fun Map<String, String>.toBundle(): Bundle {
    val bundle = Bundle()
    forEach { (k, v) -> bundle.putString(k, v) }
    return bundle
}

/**
 * Converts an Android [Bundle] instance into a Map<String, String>.
 *
 * @return A new [Map] containing all the string values stored in this [Bundle].
 */
fun Bundle.toMap(): Map<String, String> {
    val map = mutableMapOf<String, String>()
    for (key in keySet()) {
        @Suppress("DEPRECATION")
        if (get(key) is String) {
            getString(key)?.let { value -> map[key] = value }
        }
    }

    return map
}

/**
 * Converts a Map<String, String> instance into a string format suitable for serializable and
 * parsing. The resulted format is `[key1=value1,key2=value2,...]`.
 */
fun Map<String, String>.toSerializableString(): String {
    return entries.joinToString(separator = ",", prefix = "[", postfix = "]") {
            (key, value) -> "$key=$value"
    }
}

/**
 * Creates a Map<String, String> instance from a string representation.
 *
 * The expected format is `[key1=value1,key2=value2,...]`. This method parses the string
 * and is the counterpart to [toSerializableString].
 *
 * @return A Map<String, String> instance.
 * @throws IllegalArgumentException if the string is malformed.
 */
fun String.deserializeToMap(): Map<String, String> {
    if (!startsWith("[") || !endsWith("]")) {
        throw IllegalArgumentException("String must be enclosed in brackets [].")
    }

    val content = substring(1, length - 1)
    if (content.isEmpty()) {
        return emptyMap()
    }

    return content.split(',').associate { pair ->
        val parts = pair.split('=', limit = 2)
        if (parts.size != 2) {
            throw IllegalArgumentException("Malformed key-value pair: '$pair'")
        }
        val key = parts[0].trim()
        val value = parts[1].trim()
        if (key.isEmpty()) {
            throw IllegalArgumentException("Key cannot be empty in pair: '$pair'")
        }
        key to value
    }
}