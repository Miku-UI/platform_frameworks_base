/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.qs.pipeline.data.model

import com.android.systemui.qs.pipeline.shared.TileSpec

/** Interface to represent which tiles are allowed. */
sealed interface AllowedTiles {

    /** Returns true if the given [spec] is allowed. */
    fun isTileAllowed(spec: TileSpec): Boolean

    /** Implementation of [AllowedTiles] that allows all tiles. */
    data object AllTiles : AllowedTiles {
        override fun isTileAllowed(spec: TileSpec): Boolean {
            return true
        }
    }

    /** Implementation of [AllowedTiles] that allows only specific tiles. */
    class SpecificTiles(val tiles: List<TileSpec>) : AllowedTiles {
        override fun isTileAllowed(spec: TileSpec): Boolean {
            return spec in tiles
        }
    }
}
