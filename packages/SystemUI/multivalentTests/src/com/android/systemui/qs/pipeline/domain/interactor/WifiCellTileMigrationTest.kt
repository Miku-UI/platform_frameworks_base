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

package com.android.systemui.qs.pipeline.domain.interactor

import android.content.res.mainResources
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.qs.flags.QsSplitInternetTile
import com.android.systemui.qs.panels.domain.interactor.iconTilesInteractor
import com.android.systemui.qs.pipeline.data.model.RestoreData
import com.android.systemui.qs.pipeline.data.repository.DefaultTilesQSHostRepository
import com.android.systemui.qs.pipeline.data.repository.TileSpecRepository
import com.android.systemui.qs.pipeline.data.repository.TileSpecSettingsRepository
import com.android.systemui.qs.pipeline.data.repository.UserTileSpecRepository
import com.android.systemui.qs.pipeline.data.repository.autoAddRepository
import com.android.systemui.qs.pipeline.data.repository.fakeRestoreRepository
import com.android.systemui.qs.pipeline.data.repository.hsuTilesRepository
import com.android.systemui.qs.pipeline.data.repository.retailModeRepository
import com.android.systemui.qs.pipeline.data.repository.tileSpecRepository
import com.android.systemui.qs.pipeline.domain.autoaddable.withCellAutoAddable
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.statusbar.connectivity.ConnectivityModule.Companion.INTERNET_TILE_SPEC
import com.android.systemui.statusbar.connectivity.ConnectivityModule.Companion.MOBILE_DATA_TILE_SPEC
import com.android.systemui.statusbar.connectivity.ConnectivityModule.Companion.WIFI_TILE_SPEC
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.FakeUserRepository
import com.android.systemui.user.data.repository.fakeUserRepository
import com.android.systemui.user.data.repository.userRepository
import com.android.systemui.user.domain.interactor.headlessSystemUserMode
import com.android.systemui.util.settings.data.repository.secureSettingsForUserRepository
import com.android.systemui.util.settings.fakeSettings
import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

/**
 * This is a comprehensive integration test of the Wifi + Cell Tile migration addition for 26Q2.
 *
 * It verifies various possible scenarios for when the tile is enabled/this code is merged, and
 * verifies that the result is as expected.
 *
 * Note that the cases of:
 * * user has already migrated
 * * user has not migrated but has removed their internet tile are usually not distinguishable, as
 *   the only marker that we have of a user that has not migrated yet is that they have an internet
 *   tile.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(QsSplitInternetTile.FLAG_NAME)
@DisableFlags(QsSplitInternetTile.SUPPRESSION_FLAG_NAME)
class WifiCellTileMigrationTest : SysuiTestCase() {

    private val kosmos =
        testKosmos().useUnconfinedTestDispatcher().apply {
            tileSpecRepository = realTileSpecRepository()
            withCellAutoAddable()
        }

    /**
     * This test the following scenario:
     * * User has not migrated yet: has an internet tile
     * * Internet tile is large
     * * Internet tile is the only tile (first AND last)
     *
     * Result:
     * * Internet tile replaced with Wifi
     * * Internet tile was large, so Wifi tile is large
     * * Large Cell tile added at the end
     * * Cell tile marked as auto added
     */
    @Test
    fun loadedLargeInternetFromSettings_singleTile_replacedWithLargeWifiAndLargeCellAtEnd() =
        kosmos.runTest {
            fakeUserRepository.asDefaultUser()
            setTilesInSettings(listOf(internetSpec))
            iconTilesInteractor.setLargeTiles(setOf(internetSpec))

            val autoAddedTiles by collectLastValue(autoAddRepository.autoAddedTiles(USER))
            val largeTiles by collectLastValue(iconTilesInteractor.largeTilesSpecs)
            autoAddInteractor.init(currentTilesInteractor, iconTilesInteractor)

            /*
             * Internet replaced with wifi. Cell added at the end
             */
            assertThat(currentTilesInteractor.currentTilesSpecs)
                .isEqualTo(listOf(wifiSpec, cellSpec))

            /*
             * Large internet replaced with large wifi. Large Cell added
             */
            assertThat(largeTiles).containsAtLeast(wifiSpec, cellSpec)
            assertThat(largeTiles).doesNotContain(internetSpec)

            /*
             * Cell is marked as autoAdded
             */
            assertThat(autoAddedTiles).contains(cellSpec)
        }

    /**
     * This test the following scenario:
     * * User has not migrated yet: has an internet tile
     * * Internet tile is large
     *
     * Result:
     * * Internet tile replaced with Wifi
     * * Internet tile was large, so Wifi tile is large
     * * Large Cell tile added at the end
     * * Cell tile marked as auto added
     */
    @Test
    fun loadedLargeInternetFromSettings_replacedWithLargeWifiAndLargeCellAtEnd() =
        kosmos.runTest {
            fakeUserRepository.asDefaultUser()
            setTilesInSettings(listOf(TileSpec.create("a"), internetSpec, TileSpec.create("b")))
            iconTilesInteractor.setLargeTiles(setOf(internetSpec))

            val autoAddedTiles by collectLastValue(autoAddRepository.autoAddedTiles(USER))
            val largeTiles by collectLastValue(iconTilesInteractor.largeTilesSpecs)
            autoAddInteractor.init(currentTilesInteractor, iconTilesInteractor)

            /*
             * Internet replaced with wifi. Cell added at the end
             */
            assertThat(currentTilesInteractor.currentTilesSpecs)
                .isEqualTo(listOf(TileSpec.create("a"), wifiSpec, TileSpec.create("b"), cellSpec))

            /*
             * Large internet replaced with large wifi. Large Cell added
             */
            assertThat(largeTiles).containsAtLeast(wifiSpec, cellSpec)
            assertThat(largeTiles).doesNotContain(internetSpec)

            /*
             * Cell is marked as autoAdded
             */
            assertThat(autoAddedTiles).contains(cellSpec)
        }

    /**
     * This test the following scenario:
     * * User has not migrated yet: has an internet tile
     * * Internet tile is small
     *
     * Result:
     * * Internet tile replaced with Wifi
     * * Internet tile was small, so Wifi tile is small
     * * Large Cell tile added at the end
     * * Cell tile marked as auto-added
     */
    @Test
    fun loadedSmallInternetFromSettings_replacedWithSmallWifiAndLargeCellAtEnd() =
        kosmos.runTest {
            fakeUserRepository.asDefaultUser()
            setTilesInSettings(listOf(TileSpec.create("a"), internetSpec, TileSpec.create("b")))
            iconTilesInteractor.setLargeTiles(emptySet())

            val autoAddedTiles by collectLastValue(autoAddRepository.autoAddedTiles(USER))
            val largeTiles by collectLastValue(iconTilesInteractor.largeTilesSpecs)
            autoAddInteractor.init(currentTilesInteractor, iconTilesInteractor)

            /*
             * Internet replaced with wifi. Cell added at the end
             */
            assertThat(currentTilesInteractor.currentTilesSpecs)
                .isEqualTo(listOf(TileSpec.create("a"), wifiSpec, TileSpec.create("b"), cellSpec))

            /*
             * Small internet replaced with small wifi. Large Cell added
             */
            assertThat(largeTiles).contains(cellSpec)
            assertThat(largeTiles).doesNotContain(internetSpec)
            assertThat(largeTiles).doesNotContain(wifiSpec)

            /*
             * Cell is marked as autoAdded
             */
            assertThat(autoAddedTiles).contains(cellSpec)
        }

    /**
     * This test the following scenario:
     * * User has migrated already (original migration flow)
     * * User has a large Cell tile, not at the end
     * * Cell tile is not marked as auto-added (original migration flow)
     *
     * Result:
     * * Cell tile is now marked as added
     */
    @Test
    fun loadedAlreadyHasLargeCell_noChanges() =
        kosmos.runTest {
            fakeUserRepository.asDefaultUser()
            setTilesInSettings(
                listOf(TileSpec.create("a"), wifiSpec, cellSpec, TileSpec.create("b"))
            )
            iconTilesInteractor.setLargeTiles(setOf(cellSpec))
            autoAddRepository.unmarkTileAdded(USER, cellSpec)

            val autoAddedTiles by collectLastValue(autoAddRepository.autoAddedTiles(USER))
            val largeTiles by collectLastValue(iconTilesInteractor.largeTilesSpecs)
            autoAddInteractor.init(currentTilesInteractor, iconTilesInteractor)

            /*
             * Internet replaced with wifi. Cell added at the end
             */
            assertThat(currentTilesInteractor.currentTilesSpecs)
                .isEqualTo(listOf(TileSpec.create("a"), wifiSpec, cellSpec, TileSpec.create("b")))

            /*
             * Only large tile is Cell
             */
            assertThat(largeTiles).contains(cellSpec)
            assertThat(largeTiles).doesNotContain(internetSpec)
            assertThat(largeTiles).doesNotContain(wifiSpec)

            /*
             * Cell is marked as auto-added
             */
            assertThat(autoAddedTiles).contains(cellSpec)
        }

    /**
     * This test the following scenario:
     * * User has migrated already (original migration flow)
     * * User has a small Cell tile, not at the end
     * * Cell tile is not marked as auto-added (original migration flow)
     *
     * Result:
     * * Cell tile is now marked as added
     */
    @Test
    fun loadedAlreadyHasSmallCell_noChanges() =
        kosmos.runTest {
            fakeUserRepository.asDefaultUser()
            setTilesInSettings(
                listOf(TileSpec.create("a"), wifiSpec, cellSpec, TileSpec.create("b"))
            )
            iconTilesInteractor.setLargeTiles(emptySet())
            autoAddRepository.unmarkTileAdded(USER, cellSpec)

            val autoAddedTiles by collectLastValue(autoAddRepository.autoAddedTiles(USER))
            val largeTiles by collectLastValue(iconTilesInteractor.largeTilesSpecs)
            autoAddInteractor.init(currentTilesInteractor, iconTilesInteractor)

            /*
             * Internet replaced with wifi. Cell added at the end
             */
            assertThat(currentTilesInteractor.currentTilesSpecs)
                .isEqualTo(listOf(TileSpec.create("a"), wifiSpec, cellSpec, TileSpec.create("b")))

            /*
             * No large tiles
             */
            assertThat(largeTiles).doesNotContain(cellSpec)
            assertThat(largeTiles).doesNotContain(internetSpec)
            assertThat(largeTiles).doesNotContain(wifiSpec)

            /*
             * Cell is marked as auto-added
             */
            assertThat(autoAddedTiles).contains(cellSpec)
        }

    /**
     * This test the following scenario:
     * * User has migrated already (original migration flow, not marked as auto-added)
     * * User has a small Cell tile, not at the end
     * * User removes cell tile
     *
     * Result:
     * * The cell tile is not auto-added
     */
    @Test
    fun loadedAlreadyHasSmallCell_removed_notAddedAgain() =
        kosmos.runTest {
            fakeUserRepository.asDefaultUser()
            setTilesInSettings(
                listOf(TileSpec.create("a"), wifiSpec, cellSpec, TileSpec.create("b"))
            )
            iconTilesInteractor.setLargeTiles(emptySet())
            autoAddRepository.unmarkTileAdded(USER, cellSpec)

            val largeTiles by collectLastValue(iconTilesInteractor.largeTilesSpecs)
            autoAddInteractor.init(currentTilesInteractor, iconTilesInteractor)

            currentTilesInteractor.removeTiles(setOf(cellSpec))

            assertThat(currentTilesInteractor.currentTilesSpecs)
                .isEqualTo(listOf(TileSpec.create("a"), wifiSpec, TileSpec.create("b")))

            assertThat(largeTiles).doesNotContain(cellSpec)
        }

    /*
     * This test the following scenario:
     *
     * * User does not have an internet tile
     * * User does not have wifi or cell tile
     *
     * Result:
     * * Only Cell tile is added at the end
     * * Cell tile is large
     * * Cell tile is marked as auto added
     *
     * NOTE: This scenario can happen either in a device experiencing the new migration for the
     * first time (with a removed internet tile), or in a device that already experienced the old
     * migration and removed both wifi and cell
     */
    @Test
    fun noInternetTile_largeCellTileAddedAtTheEnd() =
        kosmos.runTest {
            fakeUserRepository.asDefaultUser()
            setTilesInSettings(listOf(TileSpec.create("a"), TileSpec.create("b")))
            iconTilesInteractor.setLargeTiles(emptySet())
            autoAddRepository.unmarkTileAdded(USER, cellSpec)

            val autoAddedTiles by collectLastValue(autoAddRepository.autoAddedTiles(USER))
            val largeTiles by collectLastValue(iconTilesInteractor.largeTilesSpecs)
            autoAddInteractor.init(currentTilesInteractor, iconTilesInteractor)

            /*
             * Internet replaced with wifi. Cell added at the end
             */
            assertThat(currentTilesInteractor.currentTilesSpecs)
                .isEqualTo(listOf(TileSpec.create("a"), TileSpec.create("b"), cellSpec))

            /*
             * Only large cell
             */
            assertThat(largeTiles).contains(cellSpec)
            assertThat(largeTiles).doesNotContain(internetSpec)
            assertThat(largeTiles).doesNotContain(wifiSpec)

            /*
             * Cell is marked as auto-added
             */
            assertThat(autoAddedTiles).contains(cellSpec)
        }

    /*
     * This test the following scenario:
     *
     * * User does not have an internet tile or Cell tile
     * * User has a wifi tile (small in this case)
     *
     * Result:
     * * Cell tile is added at the end
     * * Cell tile is large
     * * Cell tile is marked as auto added
     *
     * NOTE: This scenario can happen either in a device experiencing the new migration for the
     * first time, or in a device that experienced the old migration but removed the Cell tile.
     * We have no way of distinguishing these two scenarios as the wifi tile replaces the internet
     * tile almost immediately upon reading the state from Settings.
     */
    @Test
    fun justWifiTile_largeCellTileAddedAtTheEnd() =
        kosmos.runTest {
            fakeUserRepository.asDefaultUser()
            setTilesInSettings(listOf(TileSpec.create("a"), wifiSpec, TileSpec.create("b")))
            iconTilesInteractor.setLargeTiles(emptySet())
            autoAddRepository.unmarkTileAdded(USER, cellSpec)

            val autoAddedTiles by collectLastValue(autoAddRepository.autoAddedTiles(USER))
            val largeTiles by collectLastValue(iconTilesInteractor.largeTilesSpecs)
            autoAddInteractor.init(currentTilesInteractor, iconTilesInteractor)

            /*
             * Internet replaced with wifi. Cell added at the end
             */
            assertThat(currentTilesInteractor.currentTilesSpecs)
                .isEqualTo(listOf(TileSpec.create("a"), wifiSpec, TileSpec.create("b"), cellSpec))

            /*
             * Only large cell
             */
            assertThat(largeTiles).contains(cellSpec)
            assertThat(largeTiles).doesNotContain(internetSpec)
            assertThat(largeTiles).doesNotContain(wifiSpec)

            /*
             * Cell is marked as auto-added
             */
            assertThat(autoAddedTiles).contains(cellSpec)
        }

    /*
     * This tests the following scenario:
     * * User is restoring from a device pre-migration, and it has an internet tile.
     *
     * Result:
     * * Wifi tile replaces internet tile in the same position
     * * Cell tile is added as large to the end
     * * Cell tile is marked as auto-added
     */
    @Test
    fun restoreWithInternetTile_replacedWithWifi_cellAddedAtEnd() =
        kosmos.runTest {
            fakeUserRepository.asDefaultUser()
            restoreReconciliationInteractor.start()

            val restoredTiles =
                listOf(
                    TileSpec.create("a"),
                    internetSpec,
                    TileSpec.create("b"),
                    TileSpec.create("c"),
                )
            val autoAddedRestored = setOf(TileSpec.create("hotspot"))
            iconTilesInteractor.setLargeTiles(setOf(internetSpec))

            val autoAddedTiles by collectLastValue(autoAddRepository.autoAddedTiles(USER))
            val largeTiles by collectLastValue(iconTilesInteractor.largeTilesSpecs)
            autoAddInteractor.init(currentTilesInteractor, iconTilesInteractor)

            fakeRestoreRepository.onDataRestored(
                RestoreData(restoredTiles, autoAddedRestored, USER)
            )

            /*
             * Internet replaced with wifi. Cell added at the end
             */
            assertThat(currentTilesInteractor.currentTilesSpecs)
                .isEqualTo(
                    listOf(
                        TileSpec.create("a"),
                        wifiSpec,
                        TileSpec.create("b"),
                        TileSpec.create("c"),
                        cellSpec,
                    )
                )

            /*
             * Cell and Wifi expected as large
             */
            assertThat(largeTiles).containsAtLeast(cellSpec, wifiSpec)
            assertThat(largeTiles).doesNotContain(internetSpec)

            /*
             * Cell is marked as auto-added
             */
            assertThat(autoAddedTiles).contains(cellSpec)
        }

    /*
     * This tests the following scenario:
     * * User is restoring from a device without the internet tile (and without cell or wifi)
     *
     * Result:
     * * Wifi tile is not added anywhere
     * * Large Cell tile is added at the end and marked as auto-added
     *
     * NOTE: This could be one of two indistinguishable cases: either the user is restoring from
     * a device pre-migration, or the user is restoring from a device that underwent the old
     * migration.
     */
    @Test
    fun restoreWithoutInternetCellWifiTile_cellAddedAtEnd() =
        kosmos.runTest {
            fakeUserRepository.asDefaultUser()
            restoreReconciliationInteractor.start()
            val restoredTiles = listOf(TileSpec.create("a"), TileSpec.create("b"))
            val autoAddedRestored = setOf(TileSpec.create("hotspot"))
            iconTilesInteractor.setLargeTiles(emptySet())

            val autoAddedTiles by collectLastValue(autoAddRepository.autoAddedTiles(USER))
            val largeTiles by collectLastValue(iconTilesInteractor.largeTilesSpecs)
            autoAddInteractor.init(currentTilesInteractor, iconTilesInteractor)

            fakeRestoreRepository.onDataRestored(
                RestoreData(restoredTiles, autoAddedRestored, USER)
            )

            /*
             * Internet replaced with wifi. Cell added at the end
             */
            assertThat(currentTilesInteractor.currentTilesSpecs)
                .isEqualTo(listOf(TileSpec.create("a"), TileSpec.create("b"), cellSpec))

            /*
             * Only Cell large tile
             */
            assertThat(largeTiles).contains(cellSpec)
            assertThat(largeTiles).doesNotContain(internetSpec)
            assertThat(largeTiles).doesNotContain(wifiSpec)

            /*
             * Cell is marked as auto-added
             */
            assertThat(autoAddedTiles).contains(cellSpec)
        }

    /*
     * This tests the following scenario:
     * * User is restoring from a backup from a device with the previous migration
     * * User has cell tile added in the restore
     * * Cell tile is not marked as auto-added in the restore (due to previous migration)
     *
     * Result:
     * * Wifi tile is not added anywhere
     * * Large Cell tile is moved to the end and marked as auto-added
     *
     * NOTE: This is not a fully desirable scenario, but it won't be experienced by many users.
     * It requires that the backup is created with a build that has the flag enabled (post
     * migration), but does not contain the CL that introduces the new migration.
     */
    @Test
    fun restoreWithCellTile_afterPreviousMigration_tileMovedToTheEndLarge() =
        kosmos.runTest {
            fakeUserRepository.asDefaultUser()
            restoreReconciliationInteractor.start()
            val restoredTiles = listOf(TileSpec.create("a"), cellSpec, TileSpec.create("b"))
            val autoAddedRestored = setOf(TileSpec.create("hotspot"))
            iconTilesInteractor.setLargeTiles(emptySet())

            val autoAddedTiles by collectLastValue(autoAddRepository.autoAddedTiles(USER))
            val largeTiles by collectLastValue(iconTilesInteractor.largeTilesSpecs)
            autoAddInteractor.init(currentTilesInteractor, iconTilesInteractor)

            fakeRestoreRepository.onDataRestored(
                RestoreData(restoredTiles, autoAddedRestored, USER)
            )

            /*
             * Internet replaced with wifi. Cell added at the end
             */
            assertThat(currentTilesInteractor.currentTilesSpecs)
                .isEqualTo(listOf(TileSpec.create("a"), TileSpec.create("b"), cellSpec))

            /*
             * Only Cell large tile
             */
            assertThat(largeTiles).contains(cellSpec)
            assertThat(largeTiles).doesNotContain(internetSpec)
            assertThat(largeTiles).doesNotContain(wifiSpec)

            /*
             * Cell is marked as auto-added
             */
            assertThat(autoAddedTiles).contains(cellSpec)
        }

    /**
     * This tests the following scenario:
     * * User is restoring from a backup that underwent this new migration
     * * Cell tile was in the restored set
     * * Cell tile was marked as auto added in the restore data (as it's after new migration)
     *
     * Result:
     * * Tile list is the restored list
     * * Cell tile is not moved or have its size changed
     * * Cell tile is marked as auto-added
     */
    @Test
    fun restoreWithSmallCellTile_afterCurrentMigration_tileNotMovedAndSizeNotChanged() =
        kosmos.runTest {
            fakeUserRepository.asDefaultUser()
            restoreReconciliationInteractor.start()
            val restoredTiles = listOf(TileSpec.create("a"), cellSpec, TileSpec.create("b"))
            val autoAddedRestored = setOf(TileSpec.create("hotspot"), cellSpec)
            iconTilesInteractor.setLargeTiles(emptySet())

            val autoAddedTiles by collectLastValue(autoAddRepository.autoAddedTiles(USER))
            val largeTiles by collectLastValue(iconTilesInteractor.largeTilesSpecs)
            autoAddInteractor.init(currentTilesInteractor, iconTilesInteractor)

            fakeRestoreRepository.onDataRestored(
                RestoreData(restoredTiles, autoAddedRestored, USER)
            )

            /*
             * Cell tile is not moved
             */
            assertThat(currentTilesInteractor.currentTilesSpecs)
                .isEqualTo(listOf(TileSpec.create("a"), cellSpec, TileSpec.create("b")))

            /*
             * Cell time remains small
             */
            assertThat(largeTiles).doesNotContain(cellSpec)
            assertThat(largeTiles).doesNotContain(internetSpec)
            assertThat(largeTiles).doesNotContain(wifiSpec)

            /*
             * Cell is marked as auto-added
             */
            assertThat(autoAddedTiles).contains(cellSpec)
        }

    /*
     * This tests the following scenario:
     * * User starts from the default set of tiles
     * * Default set contains Cell tile
     * * User removes the Cell tile
     *
     * Result:
     * * The Cell tile is not auto added
     * * The Cell tile is marked as auto added, so it cannot be added again.
     */
    @Test
    fun startingWithDefaultSet_cellTileRemoved_tileIsGoneAndMarked() =
        kosmos.runTest {
            fakeUserRepository.asDefaultUser()
            autoAddInteractor.init(currentTilesInteractor, iconTilesInteractor)
            val autoAddedTiles by collectLastValue(autoAddRepository.autoAddedTiles(USER))

            /*
             * Assumption: Cell tile starts in the current tiles
             */
            assumeTrue(currentTilesInteractor.currentTilesSpecs.contains(cellSpec))

            currentTilesInteractor.removeTiles(setOf(cellSpec))

            /** Cell tile has not been automatically re-added */
            assertThat(currentTilesInteractor.currentTilesSpecs).doesNotContain(cellSpec)

            /** Cell tile is marked as auto added */
            assertThat(autoAddedTiles).contains(cellSpec)
        }

    private suspend fun Kosmos.setTilesInSettings(tiles: List<TileSpec>) {
        secureSettingsForUserRepository.setStringForUser(
            userId = USER,
            name = Settings.Secure.QS_TILES,
            value = tiles.joinToString(separator = ",") { it.spec },
        )
    }

    private fun Kosmos.realTileSpecRepository(): TileSpecRepository {
        val realDefaultTilesRepository =
            DefaultTilesQSHostRepository(mainResources, hsuTilesRepository)
        return TileSpecSettingsRepository(
            mainResources,
            mock(),
            retailModeRepository,
            object : UserTileSpecRepository.Factory {
                override fun create(userId: Int): UserTileSpecRepository {
                    return UserTileSpecRepository(
                        userId,
                        realDefaultTilesRepository,
                        fakeSettings,
                        headlessSystemUserMode,
                        mock(),
                        userRepository,
                        backgroundScope,
                        testDispatcher,
                    )
                }
            },
            backgroundScope,
        )
    }

    private companion object {
        val USER = FakeUserRepository.DEFAULT_SELECTED_USER
        val internetSpec = TileSpec.create(INTERNET_TILE_SPEC)
        val wifiSpec = TileSpec.create(WIFI_TILE_SPEC)
        val cellSpec = TileSpec.create(MOBILE_DATA_TILE_SPEC)
    }
}
