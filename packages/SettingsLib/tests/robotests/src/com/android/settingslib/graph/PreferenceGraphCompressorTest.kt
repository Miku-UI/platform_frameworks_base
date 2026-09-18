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

package com.android.settingslib.graph

import com.android.settingslib.graph.proto.PreferenceGraphProto
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreferenceGraphCompressorTest {

    private val ORIGINAL_PROTO_SIZE = 1676126
    private val SHRUNKEN_PROTO_SIZE = 752510

    @Test
    fun shrinkAndExpand_withBinaryFile_returnsOriginalProto() {
        val inputStream = javaClass.classLoader.getResourceAsStream("test_graph.pb")
        val originalProto = PreferenceGraphProto.parseFrom(inputStream)

        val shrunkProto = PreferenceGraphCompressor.shrink(originalProto)
        val expandedProto = PreferenceGraphCompressor.expand(shrunkProto)

        assertThat(expandedProto.toByteString()).isEqualTo(originalProto.toByteString())
    }

    @Test
    fun shrink_reducesSize() {
        val inputStream = javaClass.classLoader.getResourceAsStream("test_graph.pb")
        val originalProto = PreferenceGraphProto.parseFrom(inputStream)
        assertThat(originalProto.serializedSize).isEqualTo(ORIGINAL_PROTO_SIZE)

        val shrunkProto = PreferenceGraphCompressor.shrink(originalProto)

        assertThat(shrunkProto.serializedSize).isEqualTo(SHRUNKEN_PROTO_SIZE)
    }

    @Test
    fun expand_onOriginalProto_isNoOp() {
        val inputStream = javaClass.classLoader.getResourceAsStream("test_graph.pb")
        val originalProto = PreferenceGraphProto.parseFrom(inputStream)

        val expandedProto = PreferenceGraphCompressor.expand(originalProto)

        assertThat(expandedProto.toByteString()).isEqualTo(originalProto.toByteString())
    }
}
