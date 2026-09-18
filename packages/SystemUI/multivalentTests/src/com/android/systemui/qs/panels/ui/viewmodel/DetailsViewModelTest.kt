/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.uiEventLoggerFake
import com.android.systemui.Flags.FLAG_DUAL_SHADE
import com.android.systemui.Flags.FLAG_QS_NEW_TILES
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.qs.FakeQSTile
import com.android.systemui.qs.QSEvent
import com.android.systemui.qs.flags.QsSplitInternetTile
import com.android.systemui.qs.pipeline.data.repository.tileSpecRepository
import com.android.systemui.qs.pipeline.domain.interactor.currentTilesInteractor
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.tiles.dialog.audioDetailsViewModelFactory
import com.android.systemui.shade.domain.interactor.disableDualShade
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
@EnableSceneContainer
@DisableFlags(FLAG_QS_NEW_TILES) // Wifi tile does not have
class DetailsViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val internetTileName = if (QsSplitInternetTile.isEnabled) "wifi" else "internet"
    private val spec = TileSpec.create(internetTileName)
    private val specNoDetails = TileSpec.create("NoDetailsTile")

    private val Kosmos.underTest: DetailsViewModel by Kosmos.Fixture { detailsViewModel }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun changeTileDetailsViewModelWithDualShadeEnabled() =
        kosmos.runTest {
            enableDualShade()
            val specs = listOf(spec, specNoDetails)
            tileSpecRepository.setTiles(userId = 0, specs)

            val tiles by collectLastValue(currentTilesInteractor.currentTiles)

            assertThat(currentTilesInteractor.currentTilesSpecs).hasSize(2)

            val secondTile = checkNotNull(tiles)[1]
            assertThat(secondTile.spec).isEqualTo(specNoDetails)
            (secondTile.tile as FakeQSTile).hasDetailsViewModel = false

            assertThat(underTest.activeTileDetails).isNull()

            // Click on the tile that has the `spec`.
            assertThat(underTest.onTileClicked(spec)).isTrue()
            assertThat(underTest.activeTileDetails).isNotNull()
            assertThat(underTest.activeTileDetails?.title).isEqualTo(internetTileName)

            // Click on a tile that doesn't have a valid spec.
            assertThat(underTest.onTileClicked(null)).isFalse()
            assertThat(underTest.activeTileDetails).isNull()

            // Click again on the tile that has the `spec`.
            assertThat(underTest.onTileClicked(spec)).isTrue()
            assertThat(underTest.activeTileDetails).isNotNull()
            assertThat(underTest.activeTileDetails?.title).isEqualTo(internetTileName)

            // Click on a tile that doesn't have a detailed view.
            assertThat(underTest.onTileClicked(specNoDetails)).isFalse()
            assertThat(underTest.activeTileDetails).isNull()

            // Click on the volume settings button.
            underTest.onVolumeSettingsButtonClicked(audioDetailsViewModelFactory.create())
            assertThat(underTest.activeTileDetails).isNotNull()
            assertThat(underTest.activeTileDetails?.title).isEqualTo("Volume")

            underTest.closeDetailedView()
            assertThat(underTest.activeTileDetails).isNull()

            assertThat(underTest.onTileClicked(null)).isFalse()
        }

    @Test
    @DisableFlags(FLAG_DUAL_SHADE)
    fun ignoreChangingTileDetailsViewModelWithDualShadeDisabled() =
        kosmos.runTest {
            disableDualShade()
            val specs = listOf(spec, specNoDetails)
            tileSpecRepository.setTiles(userId = 0, specs)

            val tiles by collectLastValue(currentTilesInteractor.currentTiles)

            assertThat(currentTilesInteractor.currentTilesSpecs).hasSize(2)

            val secondTile = checkNotNull(tiles)[1]
            assertThat(secondTile.spec).isEqualTo(specNoDetails)
            (secondTile.tile as FakeQSTile).hasDetailsViewModel = false

            assertThat(underTest.activeTileDetails).isNull()

            // Click on the tile that has the `spec`.
            assertThat(underTest.onTileClicked(spec)).isFalse()
            assertThat(underTest.activeTileDetails).isNull()

            // Click on a tile that doesn't have a valid spec.
            assertThat(underTest.onTileClicked(null)).isFalse()
            assertThat(underTest.activeTileDetails).isNull()

            // Click on a tile that doesn't have a detailed view.
            assertThat(underTest.onTileClicked(specNoDetails)).isFalse()
            assertThat(underTest.activeTileDetails).isNull()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun testMetricsLogging_openDetailsView() =
        kosmos.runTest {
            enableDualShade()
            val specs = listOf(spec)
            tileSpecRepository.setTiles(userId = 0, specs)
            val tiles by collectLastValue(currentTilesInteractor.currentTiles)
            val firstTile = checkNotNull(tiles)[0]

            // Open first tile details
            underTest.onTileClicked(spec)

            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
            val event = uiEventLoggerFake.get(0)
            assertThat(event.eventId).isEqualTo(QSEvent.QS_DETAILS_OPEN.id)
            assertThat(event.packageName).isEqualTo(firstTile.tile.metricsSpec)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun testMetricsLogging_settingsClick() =
        kosmos.runTest {
            enableDualShade()
            val specs = listOf(spec)
            tileSpecRepository.setTiles(userId = 0, specs)
            val tiles by collectLastValue(currentTilesInteractor.currentTiles)
            val firstTile = checkNotNull(tiles)[0]

            // Open first tile details to set _activeTile
            underTest.onTileClicked(spec)

            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
            val event1 = uiEventLoggerFake.get(0)
            assertThat(event1.eventId).isEqualTo(QSEvent.QS_DETAILS_OPEN.id)
            assertThat(event1.packageName).isEqualTo(firstTile.tile.metricsSpec)

            // Click settings
            underTest.logOnSettingsClicked()

            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(2)
            val event2 = uiEventLoggerFake.get(1)
            assertThat(event2.eventId).isEqualTo(QSEvent.QS_DETAILS_SETTINGS_CLICK.id)
            assertThat(event2.packageName).isEqualTo(firstTile.tile.metricsSpec)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun testMetricsLogging_closeDetailsView() =
        kosmos.runTest {
            enableDualShade()
            val spec2 = TileSpec.create("another_tile")
            val specs = listOf(spec, spec2)
            tileSpecRepository.setTiles(userId = 0, specs)
            val tiles by collectLastValue(currentTilesInteractor.currentTiles)
            val firstTile = checkNotNull(tiles)[0]
            val secondTile = checkNotNull(tiles)[1]

            // Open first tile details
            underTest.onTileClicked(spec)
            // Open second tile details, which should close the first one
            underTest.onTileClicked(spec2)

            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(3) // 2 opens, 1 close
            val closeEvent = uiEventLoggerFake.get(1) // open (0) -> close (1) -> open (2)
            assertThat(closeEvent.eventId).isEqualTo(QSEvent.QS_DETAILS_CLOSE.id)
            assertThat(closeEvent.packageName).isEqualTo(firstTile.tile.metricsSpec)

            // Close the currently active details view (second tile)
            underTest.closeDetailedView()

            assertThat(uiEventLoggerFake.numLogs()).isEqualTo(4) // +1 close
            val event = uiEventLoggerFake.get(3)
            assertThat(event.eventId).isEqualTo(QSEvent.QS_DETAILS_CLOSE.id)
            assertThat(event.packageName).isEqualTo(secondTile.tile.metricsSpec)
        }
}
