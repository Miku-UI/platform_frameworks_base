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

package com.android.settingslib.graph

/** Flags for preference getter operation. */
object PreferenceGetterFlags {
    /** Flag to include preference value. */
    const val VALUE = 1 shl 0
    /** Flag to include preference value descriptor. */
    const val VALUE_DESCRIPTOR = 1 shl 1
    /** Flag to include basic metadata information. */
    const val METADATA = 1 shl 2
    /** Flag to include all preference screens regardless of the `isFlagEnabled()` value. */
    const val FORCE_INCLUDE_ALL_SCREENS = 1 shl 3
    /** Flag to include parameters for parameterized screens. */
    const val PARAMETERS = 1 shl 4
    /**
     * Flag to exclude preference hierarchy.
     *
     * If the bit is set, hierarchy will not be included to reduce the data size of preference graph
     * and the call site can retrieve the graph lazily.
     *
     * Leverage [withHierarchy] and [withoutHierarchy] to manipulate this bit.
     */
    const val EXCLUDE_HIERARCHY = 1 shl 5
    /**
     * Flag to shrink hierarchy (i.e. non-recursive).
     *
     * If the bit is set, only hierarchy of given screens are included, otherwise preference graph
     * will be built recursively to include all reachable screens.
     */
    const val SHRINK_HIERARCHY = 1 shl 6
    const val ALL = (1 shl 7) - 1

    fun Int.includeValue() = (this and VALUE) != 0

    fun Int.includeValueDescriptor() = (this and VALUE_DESCRIPTOR) != 0

    fun Int.includeMetadata() = (this and METADATA) != 0

    fun Int.forceIncludeAllScreens() = (this and FORCE_INCLUDE_ALL_SCREENS) != 0

    /** Returns a new flag to exclude hierarchy. */
    fun Int.withoutHierarchy() = this or EXCLUDE_HIERARCHY

    /** Returns a new flag to include hierarchy. */
    fun Int.withHierarchy() = this and EXCLUDE_HIERARCHY.inv()

    /**
     * Returns a new flag with eager mode.
     *
     * In eager mode, all preference hierarchies are included and built recursively.
     */
    fun Int.setEagerMode() = this and (EXCLUDE_HIERARCHY or SHRINK_HIERARCHY).inv()

    /**
     * Returns a new flag with lazy mode.
     *
     * In lazy mode, preference hierarchy is excluded.
     */
    fun Int.setLazyMode() = (this or EXCLUDE_HIERARCHY or SHRINK_HIERARCHY) and PARAMETERS.inv()
}
