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

package com.android.settingslib.graph

import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.settingslib.metadata.contentEquals
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProtoConvertersTest {
    @Test
    fun bundle_toProto_values() {
        val bundle = newBundle()
        val bundleProto = bundle.toProto()
        assertThat(bundleProto.valuesMap).isNotEmpty()
        assertThat(bundleProto.parcelBytes).isEmpty()
        assertThat(bundle contentEquals bundleProto.toBundle()).isTrue()
    }

    @Test
    fun bundle_toProto_parcelableBytes() {
        val bundle = newBundle()
        val userHandle = Process.myUserHandle()
        bundle.putParcelable(Intent.EXTRA_USER, userHandle)
        val bundleProto = bundle.toProto()
        assertThat(bundleProto.valuesMap).isEmpty()
        assertThat(bundleProto.parcelBytes).isNotEmpty()
        assertThat(bundle contentEquals bundleProto.toBundle()).isTrue()
    }

    private fun newBundle() =
        Bundle().apply {
            putString("string", "string")
            putString("nullString", null)
            putByteArray("bytes", byteArrayOf(1))
            putInt("int", 1)
            putLong("long", 2)
            putBoolean("boolean", true)
            putDouble("double", 3.0)
        }
}
