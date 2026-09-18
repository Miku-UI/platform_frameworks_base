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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyParametersTest {

    @Test
    fun get_existingKey_returnsValue() {
        val params = KeyParameters(mapOf("key1" to "value1"))
        assertThat(params["key1"]).isEqualTo("value1")
    }

    @Test
    fun get_nonExistentKey_returnsNull() {
        val params = KeyParameters(mapOf("key1" to "value1"))
        assertThat(params["key2"]).isNull()
    }

    @Test
    fun isEmpty_whenEmpty_isTrue() {
        val params = KeyParameters(emptyMap())
        assertThat(params.isEmpty).isTrue()
    }

    @Test
    fun isEmpty_whenNotEmpty_isFalse() {
        val params = KeyParameters(mapOf("key1" to "value1"))
        assertThat(params.isEmpty).isFalse()
    }

    @Test
    fun toBundle_returnsCorrectBundle() {
        val map = mapOf("key1" to "value1", "key2" to "value2")
        val params = KeyParameters(map)
        val bundle = params.toBundle()

        assertThat(bundle.getString("key1")).isEqualTo("value1")
        assertThat(bundle.getString("key2")).isEqualTo("value2")
        assertThat(bundle.size()).isEqualTo(2)
    }

    @Test
    fun toBundle_emptyMap_returnsEmptyBundle() {
        val params = KeyParameters(emptyMap())
        val bundle = params.toBundle()
        assertThat(bundle.isEmpty).isTrue()
    }

    @Test
    fun toParametersString_multipleParams_returnsCorrectString() {
        val params = KeyParameters(mapOf("key1" to "val1", "key2" to "val2"))
        // The order of elements in a map is not guaranteed.
        assertThat(params.toParametersString()).isIn(
            listOf("[key1=val1,key2=val2]", "[key2=val2,key1=val1]")
        )
    }

    @Test
    fun toParametersString_emptyMap_returnsEmptyBrackets() {
        val params = KeyParameters(emptyMap())
        assertThat(params.toParametersString()).isEqualTo("[]")
    }

    @Test
    fun toParametersString_paramWithSpecialChars_returnsCorrectString() {
        val params = KeyParameters(
            mapOf("key1" to "value with spaces, and=equals")
        )
        assertThat(params.toParametersString()).isEqualTo("[key1=value with spaces, and=equals]")
    }
}