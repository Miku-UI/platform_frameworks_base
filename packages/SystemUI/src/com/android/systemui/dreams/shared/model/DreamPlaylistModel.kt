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

package com.android.systemui.dreams.shared.model

import android.service.dreams.DreamPlaylist
import com.android.systemui.log.table.Diffable
import com.android.systemui.log.table.TableRowLogger
import java.lang.Math.floorMod

/**
 * Immutable data model representing a valid playlist of dreams. This class decouples SystemUI from
 * the framework's [android.service.dreams.DreamPlaylist] class.
 */
data class DreamPlaylistModel(val dreams: List<DreamItemModel>, private val activeIndex: Int?) :
    Diffable<DreamPlaylistModel> {
    init {
        require(activeIndex == null || activeIndex in dreams.indices) {
            "Active index $activeIndex is out of bounds for playlist of size ${dreams.size}"
        }
    }

    /** The currently active dream in the playlist, or null if no dream is active. */
    val activeDream: DreamItemModel?
        get() = activeIndex?.let { dreams.getOrNull(it) }

    /**
     * The next dream in the playlist, wrapping around to the beginning if necessary. Returns null
     * if no dream is active, or if there is no next dream.
     */
    val nextDream: DreamItemModel?
        get() = activeIndex?.let { dreams.getCircular(it + 1) }?.takeIf { it != activeDream }

    /**
     * The previous dream in the playlist, wrapping around to the end if necessary. Returns null if
     * no dream is active, or if there is no previous dream.
     */
    val previousDream: DreamItemModel?
        get() = activeIndex?.let { dreams.getCircular(it - 1) }?.takeIf { it != activeDream }

    override fun logDiffs(prevVal: DreamPlaylistModel, row: TableRowLogger) {
        if (prevVal.dreams != dreams) {
            row.logChange("dreams", dreams.toString())
        }
        if (prevVal.activeIndex != activeIndex) {
            row.logChange("activeIndex", activeIndex.toString())
        }
    }

    override fun logFull(row: TableRowLogger) {
        row.logChange("dreams", dreams.toString())
        row.logChange("activeIndex", activeIndex.toString())
    }

    companion object {
        fun from(playlist: DreamPlaylist): DreamPlaylistModel {
            return DreamPlaylistModel(
                dreams = playlist.dreams.map { DreamItemModel.from(it) },
                activeIndex =
                    playlist.activeIndex.takeIf { it != DreamPlaylist.NO_ACTIVE_DREAM_INDEX },
            )
        }

        val EMPTY = from(DreamPlaylist.EMPTY)
    }
}

/**
 * Returns the element at the specified index, wrapping around if the index is out of bounds.
 * Returns null if the list is empty.
 */
private fun <T> List<T>.getCircular(index: Int): T? {
    if (isEmpty()) {
        return null
    }
    return this[floorMod(index, this.size)]
}
