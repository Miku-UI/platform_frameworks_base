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

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.toMutableStateList
import com.android.systemui.qs.panels.domain.interactor.IconTilesInteractor
import com.android.systemui.qs.panels.ui.viewmodel.InfiniteGridSnapshotViewModel.InfiniteGridSnapshot
import com.android.systemui.qs.pipeline.domain.interactor.CurrentTilesInteractor
import com.android.systemui.qs.pipeline.shared.TileSpec
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/**
 * ViewModel for saving and applying snapshots of the current tiles and their sizes.
 *
 * A snapshot contains the information on which tiles where current at the time of the snapshot,
 * along with which tiles were large. Using [undo] will apply the most recent snapshot and as a
 * result change the set of current tiles and their sizes.
 */
class InfiniteGridSnapshotViewModel
@AssistedInject
constructor(
    private val currentTilesInteractor: CurrentTilesInteractor,
    private val iconTilesInteractor: IconTilesInteractor,
) {
    private val snapshots: MutableList<InfiniteGridSnapshot> =
        emptyList<InfiniteGridSnapshot>().toMutableStateList()

    val canUndo by derivedStateOf { snapshots.isNotEmpty() }

    /** Apply the most recent snapshot of current and large tiles. */
    fun undo() {
        snapshots.removeLastOrNull()?.let {
            currentTilesInteractor.setTiles(it.tiles)
            iconTilesInteractor.setLargeTiles(it.largeTiles)
        }
    }

    /** Create a snapshot of the current tiles and large tiles and save it. */
    fun takeSnapshot(tiles: List<TileSpec>, largeTiles: Set<TileSpec>) {
        val snapshot = InfiniteGridSnapshot(tiles, largeTiles)
        if (snapshot != snapshots.lastOrNull()) {
            snapshots.add(snapshot)
        }

        // Clear oldest snapshots if we're over the limit
        while (snapshots.size > SNAPSHOTS_MAX_SIZE) snapshots.removeFirstOrNull()
    }

    /** Clear the stack of snapshots. This is a irreversible action. */
    fun clearStack() {
        snapshots.clear()
    }

    @AssistedFactory
    interface Factory {
        fun create(): InfiniteGridSnapshotViewModel
    }

    data class InfiniteGridSnapshot(val tiles: List<TileSpec>, val largeTiles: Set<TileSpec>)

    private companion object {
        const val SNAPSHOTS_MAX_SIZE = 10
    }
}
