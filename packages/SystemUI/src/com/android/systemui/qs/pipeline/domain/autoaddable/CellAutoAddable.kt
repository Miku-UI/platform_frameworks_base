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

package com.android.systemui.qs.pipeline.domain.autoaddable

import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.qs.flags.QsSplitInternetTile
import com.android.systemui.qs.pipeline.data.repository.TileSpecRepository
import com.android.systemui.qs.pipeline.data.restoreprocessors.CellTileRestoreProcessor
import com.android.systemui.qs.pipeline.domain.autoaddable.CellAutoAddable.Companion.cellTileSpec
import com.android.systemui.qs.pipeline.domain.model.AutoAddSignal
import com.android.systemui.qs.pipeline.domain.model.AutoAddSignal.AutoAddSize
import com.android.systemui.qs.pipeline.domain.model.AutoAddTracking
import com.android.systemui.qs.pipeline.domain.model.AutoAddable
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.statusbar.connectivity.ConnectivityModule.Companion.MOBILE_DATA_TILE_SPEC
import com.android.systemui.statusbar.pipeline.shared.ConnectivityConstants
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * AutoAddable for [cellTileSpec] tile.
 *
 * This autoaddable is part of the migration in 26Q2 from Internet tile to Wifi + Cellular. Wifi
 * tile migration is handled directly by repositories that keep track of list/set of tiles (as it's
 * a simple replacement of internet <-> wifi).
 *
 * For the case of [cellTileSpec], this auto-addable will add the tile exactly once (upon flag
 * enablement) for each user, large, at the end. It makes use of [AutoAddable] mechanism to mark the
 * tile as added, so it cannot be auto-added again for the same user unless it's marked as removed.
 * This marking can only happen by reverting the flag.
 */
class CellAutoAddable
@Inject
constructor(
    private val connectivityConstants: ConnectivityConstants,
    private val tileSpecRepository: TileSpecRepository,
    private val restoreProcessor: CellTileRestoreProcessor,
    @param:Background private val backgroundDispatcher: CoroutineDispatcher,
) : AutoAddable {

    override fun autoAddSignal(userId: Int): Flow<AutoAddSignal> {
        return if (QsSplitInternetTile.isEnabled) {
            /*
             * This logic (instead of just emitting the [AutoAddSignal.Add]) is to provide better support
             * for the following possible cases:
             * 1. Device that was never migrated (never had [QsSplitInternetTile] enabled): in this case,
             *   we will add the tile once, large, at the end of the list for that user. This will happen
             *   because there's no other way that [cellTileSpec] can be in the list of tiles.
             *   will add the tile once, large, at the end of the list for that user.
             * 2. Device performed a restore from a backup from a build that does not contain this
             *   logic: in this case, we use the [CellTileRestoreProcessor] to indicate this, remove the
             *   cellular tile from where it got inserted (due to it being in the default set) and
             *   insert it at the end.
             * 3. Device was started from SUW with this new code: in this case, we will mark the tile
             *   as added, so it won't be auto-added in the future (if the user removes it).
             * 4. Device performed the previous type migration (splitting the Internet tile into wifi +
             *   cell) and the user still has the `cell` tile in their current set: in this case, nothing
             *   will happen. The tile will not move or change size.
             * 5. Device performed the previous type migration (splitting the Internet tile into wifi +
             *   cell) and the user removed the `cell` tile (or it never got added): in this case, we
             *   will add it at the end once.
             *
             * Note that cases 1, 2, and 3 are the desired cases for this re-written migration.
             * In the cases 4, and 5, this will only be experienced by early users (not in the public
             * release) and it's done this way to minimize the disruption to their current list of
             * tiles (case 5).
             */
            merge(
                    flow {
                        tileSpecRepository
                            .tilesSpecs(userId)
                            .map { tiles -> cellTileSpec in tiles }
                            .distinctUntilChanged()
                            .collect { cellTilePresent ->
                                if (!cellTilePresent && connectivityConstants.hasDataCapabilities) {
                                    emit(AutoAddSignal.Add(cellTileSpec, size = AutoAddSize.LARGE))
                                } else if (
                                    cellTilePresent && connectivityConstants.hasDataCapabilities
                                ) {
                                    emit(AutoAddSignal.AddTracking(cellTileSpec))
                                }
                            }
                    },
                    flow {
                        restoreProcessor.restoredWithoutAutoAddedCell(userId).collect {
                            emit(AutoAddSignal.Remove(cellTileSpec))
                        }
                    },
                )
                .flowOn(backgroundDispatcher)
        } else {
            flowOf(AutoAddSignal.RemoveTracking(cellTileSpec))
        }
    }

    override val autoAddTracking: AutoAddTracking
        get() = AutoAddTracking.Always

    override val description: String
        get() = "CellAutoAddable ($autoAddTracking)"

    companion object {
        private val cellTileSpec = TileSpec.create(MOBILE_DATA_TILE_SPEC)
    }
}
