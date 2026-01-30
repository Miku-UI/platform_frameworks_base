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

package com.android.systemui.qs.pipeline.data.repository

import android.content.res.Resources
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.qs.pipeline.data.model.AllowedTiles
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import javax.inject.Inject

/** Repository for keeping track of the set of allowed tiles for the headless system user (hsu). */
@SysUISingleton
class HsuTilesRepository @Inject constructor(@ShadeDisplayAware private val resources: Resources) {

    /** Set of allowed tiles for the headless system user. */
    val allowedTiles: AllowedTiles

    init {
        var allowList = resources.getStringArray(R.array.hsu_allow_list_qs_tiles)
        allowedTiles =
            if (allowList.size != 0)
                AllowedTiles.SpecificTiles(allowList.map { spec -> TileSpec.create(spec) })
            else AllowedTiles.AllTiles
    }

    /** Returns true if the given [spec] is allowed. */
    fun isTileAllowed(spec: TileSpec): Boolean {
        return allowedTiles.isTileAllowed(spec)
    }
}
