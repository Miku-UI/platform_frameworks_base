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
package com.android.hoststubgen.filters

import com.android.hoststubgen.asm.ClassNodes
import com.android.hoststubgen.log
import org.objectweb.asm.commons.Remapper

/**
 * A [Remapper] that uses [OutputFilter.remapType]
 */
class FilterRemapper(
    val filter: OutputFilter,
    val allClasses: ClassNodes
) : Remapper() {
    private val cache = mutableMapOf<String, String>()

    override fun map(typeInternalName: String): String {
        return cache.computeIfAbsent(typeInternalName, this::mapInner)
    }

    private fun mapInner(typeInternalName: String): String {
        // We only rename classes within the same JAR
        allClasses.findClass(typeInternalName) ?: return typeInternalName

        val mapped = filter.remapType(typeInternalName) ?: return typeInternalName
        log.d("Renaming type $typeInternalName to $mapped")
        return mapped
    }
}