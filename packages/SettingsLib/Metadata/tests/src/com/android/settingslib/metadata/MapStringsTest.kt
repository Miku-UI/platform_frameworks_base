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
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class MapStringsTest {

    @Test
    fun toBundle_convertsMapToBundle() {
        val map = mapOf("key1" to "value1", "key2" to "value2")
        val bundle = map.toBundle()

        assertThat(bundle.getString("key1")).isEqualTo("value1")
        assertThat(bundle.getString("key2")).isEqualTo("value2")
        assertThat(bundle.size()).isEqualTo(2)
    }

    @Test
    fun toBundle_withEmptyMap_returnsEmptyBundle() {
        val map = emptyMap<String, String>()
        val bundle = map.toBundle()
        assertThat(bundle.isEmpty).isTrue()
    }

    @Test
    fun toMap_convertsBundleToMap() {
        val bundle = Bundle().apply {
            putString("key1", "value1")
            putString("key2", "value2")
        }
        val map = bundle.toMap()

        assertThat(map).containsExactly("key1", "value1", "key2", "value2")
    }

    @Test
    fun toMap_withEmptyBundle_returnsEmptyMap() {
        val bundle = Bundle()
        val map = bundle.toMap()
        assertThat(map).isEmpty()
    }

    @Test
    fun toMap_withMixedTypesInBundle_onlyIncludesStrings() {
        val bundle = Bundle().apply {
            putString("stringKey", "stringValue")
            putInt("intKey", 123)
            putBoolean("boolKey", true)
        }
        val map = bundle.toMap()

        assertThat(map).containsExactly("stringKey", "stringValue")
    }

    @Test
    fun toSerializableString_withMultipleEntries_correctlyFormats() {
        val map = mapOf("key1" to "value1", "key2" to "value2")
        val result = map.toSerializableString()
        // The order is not guaranteed, so we check for both possibilities
        assertThat(result).isIn(listOf("[key1=value1,key2=value2]", "[key2=value2,key1=value1]"))
    }

    @Test
    fun toSerializableString_withSingleEntry_correctlyFormats() {
        val map = mapOf("key1" to "value1")
        val result = map.toSerializableString()
        assertThat(result).isEqualTo("[key1=value1]")
    }

    @Test
    fun toSerializableString_withEmptyMap_correctlyFormats() {
        val map = emptyMap<String, String>()
        val result = map.toSerializableString()
        assertThat(result).isEqualTo("[]")
    }

    @Test
    fun deserializeToMap_withValidString_parsesCorrectly() {
        val str = "[key1=value1,key2=value2]"
        val map = str.deserializeToMap()
        assertThat(map).containsExactly("key1", "value1", "key2", "value2")
    }

    @Test
    fun deserializeToMap_withValidStringAndSpaces_parsesCorrectly() {
        val str = "[ key1 = value1 , key2=value2 ]"
        val map = str.deserializeToMap()
        assertThat(map).containsExactly("key1", "value1", "key2", "value2")
    }

    @Test
    fun deserializeToMap_withSingleEntry_parsesCorrectly() {
        val str = "[key=value]"
        val map = str.deserializeToMap()
        assertThat(map).containsExactly("key", "value")
    }

    @Test
    fun deserializeToMap_withEmptyContent_returnsEmptyMap() {
        val str = "[]"
        val map = str.deserializeToMap()
        assertThat(map).isEmpty()
    }

    @Test
    fun deserializeToMap_malformedString_noBrackets_throwsException() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            "key=value".deserializeToMap()
        }
        assertThat(exception.message).isEqualTo("String must be enclosed in brackets [].")
    }

    @Test
    fun deserializeToMap_malformedString_missingClosingBracket_throwsException() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            "[key=value".deserializeToMap()
        }

        assertThat(exception.message).isEqualTo("String must be enclosed in brackets [].")
    }

    @Test
    fun deserializeToMap_malformedPair_noEquals_throwsException() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            "[keyvalue]".deserializeToMap()
        }

        assertThat(exception.message).isEqualTo("Malformed key-value pair: 'keyvalue'")
    }

    @Test
    fun deserializeToMap_malformedPair_emptyKey_throwsException() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            "[=value]".deserializeToMap()
        }

        assertThat(exception.message).isEqualTo("Key cannot be empty in pair: '=value'")
    }

    @Test
    fun deserializeToMap_valueContainsEquals_parsesCorrectly() {
        val str = "[key=value=with=equals]"
        val map = str.deserializeToMap()
        assertThat(map).containsExactly("key", "value=with=equals")
    }
}