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

package com.android.server.display.utils

fun createLastValueAmbientFilter(): AmbientFilter =
    object : AmbientFilter("LastValueAmbientFilter", 1) {
        override fun filter(time: Long, buffer: RollingBuffer): Float {
            return if (buffer.isEmpty) {
                -1f
            } else {
                buffer.getValue(buffer.size() - 1)
            }
        }
    }