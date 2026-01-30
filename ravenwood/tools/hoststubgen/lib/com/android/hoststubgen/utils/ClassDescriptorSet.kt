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
package com.android.hoststubgen.utils

import com.android.hoststubgen.asm.toHumanReadableClassName
import com.android.hoststubgen.asm.toJvmClassName

/**
 * Represents a set of "class descriptors" for faster lookup, which supports all of the
 * "com/android/ravenwood/Xxx", "Lcom/android/ravenwood/Xxx;" and
 * "com.android.ravenwood.Xxx" formats.
 */
class ClassDescriptorSet {
    private val matches = mutableSetOf<String>()

    fun addType(typeName: String) {
        val internalName = typeName.toJvmClassName()
        matches.add(internalName)
        matches.add("L$internalName;")
        matches.add(typeName.toHumanReadableClassName())
    }

    fun contains(descriptor: String?): Boolean {
        if (descriptor == null) {
            return false
        }
        return matches.contains(descriptor)
    }
}