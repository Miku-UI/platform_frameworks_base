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

package com.android.systemui.qs.panels.ui.viewmodel

import com.android.systemui.qs.panels.shared.model.SizedTile
import com.android.systemui.qs.panels.shared.model.TileRow

/** The viewmodel for a paginated grid layout. */
interface PaginatableViewModel {
    /** Keys to use to invalidate the pages, other than the tiles and rows. */
    val pageKeys: Array<Any>

    fun splitIntoPages(tiles: List<TileViewModel>, rows: Int): List<List<TileViewModel>>

    companion object {
        /**
         * Splits a list of [SizedTile] into rows, each with at most [columns] occupied.
         *
         * It will leave gaps at the end of a row if the next [SizedTile] has [SizedTile.width] that
         * is larger than the space remaining in the row.
         */
        fun splitInRows(
            tiles: List<SizedTile<TileViewModel>>,
            columns: Int,
        ): List<List<SizedTile<TileViewModel>>> {
            val row = TileRow<TileViewModel>(columns)

            return buildList {
                for (tile in tiles) {
                    check(tile.width <= columns)
                    if (!row.maybeAddTile(tile)) {
                        // Couldn't add tile to previous row, create a row with the current tiles
                        // and start a new one
                        add(row.tiles)
                        row.clear()
                        row.maybeAddTile(tile)
                    }
                }
                if (row.tiles.isNotEmpty()) {
                    add(row.tiles)
                }
            }
        }
    }

    interface Factory {
        fun create(): PaginatableViewModel
    }
}
