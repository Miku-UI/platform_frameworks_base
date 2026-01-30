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

package com.android.systemui.qs.pipeline.domain.upgrade

import com.android.systemui.Flags.resetTilesRemovesCustomTiles
import com.android.systemui.qs.pipeline.data.repository.CustomTileAddedRepository
import com.android.systemui.qs.pipeline.domain.interactor.CurrentTilesInteractor
import com.android.systemui.qs.pipeline.shared.TileSpec
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

class RemoveAlreadyRemovedTiles
@Inject
constructor(private val currentTilesInteractor: CurrentTilesInteractor) : CustomTileAddedUpgrade {

    init {
        check(resetTilesRemovesCustomTiles())
    }

    override val version: Int
        get() = 2

    override suspend fun CustomTileAddedRepository.upgradeForUser(userId: Int) {
        val currentTiles =
            currentTilesInteractor.currentTiles
                .filter { it.isNotEmpty() }
                .combine(currentTilesInteractor.userId.filter { it == userId }) { tiles, _ ->
                    tiles
                }
                .first()
                .map { it.spec }
                .filterIsInstance<TileSpec.CustomTileSpec>()
                .map { it.componentName }
        removeNonCurrentTiles(currentTiles, userId)
    }
}
