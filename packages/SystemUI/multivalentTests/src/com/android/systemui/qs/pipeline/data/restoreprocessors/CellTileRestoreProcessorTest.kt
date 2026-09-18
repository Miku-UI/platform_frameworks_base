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

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.qs.flags.QsSplitInternetTile
import com.android.systemui.qs.pipeline.data.model.RestoreData
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.statusbar.connectivity.ConnectivityModule.Companion.MOBILE_DATA_TILE_SPEC
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class CellTileRestoreProcessorTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val underTest = CellTileRestoreProcessor()

    @Test
    @EnableFlags(QsSplitInternetTile.FLAG_NAME)
    @DisableFlags(QsSplitInternetTile.SUPPRESSION_FLAG_NAME)
    fun flagEnabled_restoredWithCellNotMarked_emits() =
        kosmos.runTest {
            val signal by collectLastValue(underTest.restoredWithoutAutoAddedCell(USER))
            val restoreData =
                RestoreData(
                    userId = USER,
                    restoredTiles = listOf(TileSpec.create("a"), TileSpec.create("b")),
                    restoredAutoAddedTiles = emptySet(),
                )
            underTest.postProcessRestore(restoreData)
            assertThat(signal).isEqualTo(Unit)
        }

    @Test
    @EnableFlags(QsSplitInternetTile.FLAG_NAME)
    @DisableFlags(QsSplitInternetTile.SUPPRESSION_FLAG_NAME)
    fun flagEnabled_restoredWithCellMarked_doesntEmit() =
        kosmos.runTest {
            val signal by collectLastValue(underTest.restoredWithoutAutoAddedCell(USER))
            val restoreData =
                RestoreData(
                    userId = USER,
                    restoredTiles = listOf(TileSpec.create("a"), TileSpec.create("b")),
                    restoredAutoAddedTiles = setOf(SPEC),
                )
            underTest.postProcessRestore(restoreData)
            assertThat(signal).isNull()
        }

    @Test
    @DisableFlags(QsSplitInternetTile.FLAG_NAME)
    fun flagDisabled_restoredWithCellMarked_doesntEmit() =
        kosmos.runTest {
            val signal by collectLastValue(underTest.restoredWithoutAutoAddedCell(USER))
            val restoreData =
                RestoreData(
                    userId = USER,
                    restoredTiles = listOf(TileSpec.create("a"), TileSpec.create("b")),
                    restoredAutoAddedTiles = setOf(SPEC),
                )
            underTest.postProcessRestore(restoreData)
            assertThat(signal).isNull()
        }

    @Test
    @DisableFlags(QsSplitInternetTile.FLAG_NAME)
    fun flagDisabled_restoredWithCellNotMarked_doesntEmit() =
        kosmos.runTest {
            val signal by collectLastValue(underTest.restoredWithoutAutoAddedCell(USER))
            val restoreData =
                RestoreData(
                    userId = USER,
                    restoredTiles = listOf(TileSpec.create("a"), TileSpec.create("b")),
                    restoredAutoAddedTiles = emptySet(),
                )
            underTest.postProcessRestore(restoreData)
            assertThat(signal).isNull()
        }

    private companion object {
        const val USER = 10
        val SPEC = TileSpec.create(MOBILE_DATA_TILE_SPEC)
    }
}
