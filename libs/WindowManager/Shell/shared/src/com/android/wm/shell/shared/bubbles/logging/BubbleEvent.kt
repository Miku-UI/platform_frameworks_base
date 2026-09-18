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

package com.android.wm.shell.shared.bubbles.logging

/** Represents an event that happened to bubbles, for debugging purposes. */
data class BubbleEvent(

    /** What happened to the bubbles. */
    val title: String,

    /** What happened to the bubbles arguments. */
    val titleParams: Array<Any?>? = null,

    /** Additional optional event data. */
    val eventData: String? = null,

    /** Event occurrence time in milliseconds since epoch */
    val timestamp: Long,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BubbleEvent

        if (timestamp != other.timestamp) return false
        if (title != other.title) return false
        // When comparing arrays in Kotlin, - arrays are objects inherited from Java's Object.
        // The default equals() method inherited from Object only checks if the two variables refer
        // to the exact same array object in memory (arrayOf(1, 2) == arrayOf(1, 2) is false)
        if (!titleParams.contentEquals(other.titleParams)) return false
        if (eventData != other.eventData) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + titleParams.contentHashCode()
        result = 31 * result + (eventData?.hashCode() ?: 0)
        return result
    }
}
