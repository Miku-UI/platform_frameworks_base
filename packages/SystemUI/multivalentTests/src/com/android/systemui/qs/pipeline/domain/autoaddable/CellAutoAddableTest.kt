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

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.collectValues
import com.android.systemui.kosmos.runTest
import com.android.systemui.qs.flags.QsSplitInternetTile
import com.android.systemui.qs.pipeline.data.model.RestoreData
import com.android.systemui.qs.pipeline.data.model.cellTileRestoreProcessor
import com.android.systemui.qs.pipeline.data.repository.tileSpecRepository
import com.android.systemui.qs.pipeline.domain.model.AutoAddSignal
import com.android.systemui.qs.pipeline.domain.model.AutoAddTracking
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.statusbar.connectivity.ConnectivityModule.Companion.MOBILE_DATA_TILE_SPEC
import com.android.systemui.statusbar.pipeline.shared.connectivityConstants
import com.android.systemui.statusbar.pipeline.shared.fake
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class CellAutoAddableTest : SysuiTestCase() {
    private val kosmos = testKosmos()

    private val Kosmos.underTest by Kosmos.Fixture { cellAutoAddable }

    @Test
    fun tracking_always() =
        kosmos.runTest { assertThat(underTest.autoAddTracking).isEqualTo(AutoAddTracking.Always) }

    @Test
    @DisableFlags(QsSplitInternetTile.FLAG_NAME)
    fun flagDisabled_RemoveTracked() =
        kosmos.runTest {
            val signals by collectValues(underTest.autoAddSignal(USER))

            assertThat(signals.size).isEqualTo(1)
            assertThat(signals.single()).isEqualTo(AutoAddSignal.RemoveTracking(cellTileSpec))
        }

    @Test
    @EnableFlags(QsSplitInternetTile.FLAG_NAME)
    @DisableFlags(QsSplitInternetTile.SUPPRESSION_FLAG_NAME)
    fun flagEnabled_tileNotCurrent_hasDataCapabilities_addSignalLarge() =
        kosmos.runTest {
            tileSpecRepository.setTiles(USER, listOf(TileSpec.create("a")))
            connectivityConstants.fake.hasDataCapabilities = true

            val signals by collectValues(underTest.autoAddSignal(USER))

            assertThat(signals.size).isEqualTo(1)
            assertThat(signals.single())
                .isEqualTo(AutoAddSignal.Add(cellTileSpec, size = AutoAddSignal.AutoAddSize.LARGE))
        }

    @Test
    @EnableFlags(QsSplitInternetTile.FLAG_NAME)
    @DisableFlags(QsSplitInternetTile.SUPPRESSION_FLAG_NAME)
    fun flagEnabled_tileNotCurrent_doesNotHaveDataCapabilities_noAddSignal() =
        kosmos.runTest {
            tileSpecRepository.setTiles(USER, listOf(TileSpec.create("a")))
            connectivityConstants.fake.hasDataCapabilities = false

            val signals by collectValues(underTest.autoAddSignal(USER))

            assertThat(signals).isEmpty()
        }

    @Test
    @EnableFlags(QsSplitInternetTile.FLAG_NAME)
    @DisableFlags(QsSplitInternetTile.SUPPRESSION_FLAG_NAME)
    fun flagEnabled_tileCurrent_hasDataCapabilities_markAddedSignal() =
        kosmos.runTest {
            tileSpecRepository.setTiles(USER, listOf(TileSpec.create("a"), cellTileSpec))
            connectivityConstants.fake.hasDataCapabilities = true

            val signals by collectValues(underTest.autoAddSignal(USER))

            assertThat(signals.single()).isEqualTo(AutoAddSignal.AddTracking(cellTileSpec))
        }

    @Test
    @EnableFlags(QsSplitInternetTile.FLAG_NAME)
    @DisableFlags(QsSplitInternetTile.SUPPRESSION_FLAG_NAME)
    fun flagEnabled_tileCurrent_doesNotHaveDataCapabilities_noAddSignal() =
        kosmos.runTest {
            tileSpecRepository.setTiles(USER, listOf(TileSpec.create("a"), cellTileSpec))
            connectivityConstants.fake.hasDataCapabilities = false

            val signals by collectValues(underTest.autoAddSignal(USER))

            assertThat(signals).isEmpty()
        }

    @Test
    @EnableFlags(QsSplitInternetTile.FLAG_NAME)
    @DisableFlags(QsSplitInternetTile.SUPPRESSION_FLAG_NAME)
    fun flagEnabled_tileCurrent_restoredWithoutMarked_removeSignal() =
        kosmos.runTest {
            tileSpecRepository.setTiles(USER, listOf(TileSpec.create("a"), cellTileSpec))
            val signal by collectLastValue(underTest.autoAddSignal(USER))

            val restoreData =
                RestoreData(
                    userId = USER,
                    restoredTiles = listOf(TileSpec.create("b")),
                    restoredAutoAddedTiles = emptySet(),
                )
            cellTileRestoreProcessor.postProcessRestore(restoreData)

            assertThat(signal).isEqualTo(AutoAddSignal.Remove(cellTileSpec))
        }

    private companion object {
        const val USER = 10
        val cellTileSpec = TileSpec.create(MOBILE_DATA_TILE_SPEC)
    }
}
