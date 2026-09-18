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

package com.android.systemui.communal.data.model

@JvmInline
value class CommunalWidgetId(val value: Int) {
    val isBound: Boolean
        get() = value > 0

    val isPlaceholder: Boolean
        get() = value <= 0

    companion object {
        // Factory for a real ID provided by AppWidgetManager
        fun bound(id: Int): CommunalWidgetId {
            require(id > 0) { "Bound widget IDs must be positive" }
            return CommunalWidgetId(id)
        }

        // Factory to generate a placeholder from the DB Item ID
        fun placeholder(itemId: Long): CommunalWidgetId {
            // Encapsulate the negative logic here
            val id = itemId.toInt()
            // Ensure we never return a positive number, even on overflow
            val safeId = if (id > 0) -id else id
            return CommunalWidgetId(safeId)
        }
    }
}
