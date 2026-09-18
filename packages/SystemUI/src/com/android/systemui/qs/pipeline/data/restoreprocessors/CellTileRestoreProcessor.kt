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

package com.android.systemui.qs.pipeline.data.restoreprocessors

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.qs.flags.QsSplitInternetTile
import com.android.systemui.qs.pipeline.data.model.RestoreData
import com.android.systemui.qs.pipeline.data.model.RestoreProcessor
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.statusbar.connectivity.ConnectivityModule.Companion.MOBILE_DATA_TILE_SPEC
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Restore processor for handling migration and auto-adding of `cell` tile.
 *
 * It will indicate when a restore has finished from a backup that in which the `cell` tile had not
 * been auto added. This could have happened in one of the two scenarios:
 * * Pre-migration (internet tile) backup
 * * Backup from build with old migration (without this code).
 *
 * In either of those cases, the `cell` tile will not be in the set of marked tiles from auto-add,
 * and we will emit a signal.
 *
 * @see com.android.systemui.qs.pipeline.domain.autoaddable.CellAutoAddable
 */
@SysUISingleton
class CellTileRestoreProcessor @Inject constructor() : RestoreProcessor {

    private val mutex = Mutex()
    private val channelPerUser: MutableMap<Int, Channel<Unit>> = mutableMapOf()

    /**
     * Emits when a restore for [userId] is finished and that [RestoreData] did not have `cell` tile
     * marked as auto added.
     */
    suspend fun restoredWithoutAutoAddedCell(userId: Int): Flow<Unit> {
        return getChannelForUser(userId).receiveAsFlow()
    }

    private suspend fun getChannelForUser(userId: Int): Channel<Unit> {
        return mutex.withLock {
            channelPerUser.getOrPut(userId) {
                Channel(capacity = 5, onBufferOverflow = BufferOverflow.DROP_OLDEST)
            }
        }
    }

    override suspend fun postProcessRestore(restoreData: RestoreData) {
        if (QsSplitInternetTile.isEnabled) {
            if (cellTileSpec !in restoreData.restoredAutoAddedTiles) {
                getChannelForUser(restoreData.userId).send(Unit)
            }
        }
    }

    private companion object {
        val cellTileSpec = TileSpec.create(MOBILE_DATA_TILE_SPEC)
    }
}
