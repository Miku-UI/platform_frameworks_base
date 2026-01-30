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

package com.android.systemui.common.ui.compose

import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.layoutId

inline fun <reified LayoutIdType> List<Measurable>.byLayoutId(): Map<LayoutIdType, Measurable> {
    var index = -1
    return associateBy { measurable ->
        index++
        val id = measurable.layoutId
        checkNotNull(id) { "Measurable at index $index doesn't have a layoutId modifier!" }
        try {
            id as LayoutIdType
        } catch (e: ClassCastException) {
            throw IllegalStateException(
                "Measurable at index $index has a layoutId of type ${id.javaClass.simpleName}, " +
                    "but LayoutIdType ${LayoutIdType::class.java.simpleName} was expected.",
                e,
            )
        }
    }
}

fun <LayoutIdType> List<Measurable>.singleton(layoutId: LayoutIdType): Measurable {
    check(size == 1) {
        "Only one Measurable expected but there are actually $size measurables. Use byLayoutId instead"
    }
    return first { it.layoutId == layoutId }
}
