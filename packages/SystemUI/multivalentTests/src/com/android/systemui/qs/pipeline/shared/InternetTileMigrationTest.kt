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

package com.android.systemui.qs.pipeline.shared

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.qs.flags.QsSplitInternetTile
import com.android.systemui.qs.pipeline.shared.InternetTileMigration.migrateInternetTile
import com.android.systemui.statusbar.connectivity.ConnectivityModule.Companion.INTERNET_TILE_SPEC
import com.android.systemui.statusbar.connectivity.ConnectivityModule.Companion.WIFI_TILE_SPEC
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class InternetTileMigrationTest : SysuiTestCase() {

    @Test
    @EnableFlags(QsSplitInternetTile.FLAG_NAME)
    @DisableFlags(QsSplitInternetTile.SUPPRESSION_FLAG_NAME)
    fun migrateListWithInternet() {
        val initialList =
            listOf(TileSpec.create("a"), TileSpec.create("b"), internetSpec, TileSpec.create("c"))

        val migrated: List<TileSpec> = initialList.migrateInternetTile()

        assertThat(migrated)
            .isEqualTo(
                listOf(TileSpec.create("a"), TileSpec.create("b"), wifiSpec, TileSpec.create("c"))
            )
    }

    @Test
    @EnableFlags(QsSplitInternetTile.FLAG_NAME)
    @DisableFlags(QsSplitInternetTile.SUPPRESSION_FLAG_NAME)
    fun migrateListWithoutInternet() {
        val initialList = listOf(TileSpec.create("a"), TileSpec.create("b"), TileSpec.create("c"))

        val migrated: List<TileSpec> = initialList.migrateInternetTile()

        assertThat(migrated)
            .isEqualTo(listOf(TileSpec.create("a"), TileSpec.create("b"), TileSpec.create("c")))
    }

    @Test
    @EnableFlags(QsSplitInternetTile.FLAG_NAME)
    @DisableFlags(QsSplitInternetTile.SUPPRESSION_FLAG_NAME)
    fun migrateSetWithInternet() {
        val initialList =
            setOf(TileSpec.create("a"), TileSpec.create("b"), internetSpec, TileSpec.create("c"))

        val migrated: Set<TileSpec> = initialList.migrateInternetTile()

        assertThat(migrated)
            .isEqualTo(
                setOf(TileSpec.create("a"), TileSpec.create("b"), wifiSpec, TileSpec.create("c"))
            )
    }

    @Test
    @EnableFlags(QsSplitInternetTile.FLAG_NAME)
    @DisableFlags(QsSplitInternetTile.SUPPRESSION_FLAG_NAME)
    fun migrateSetWithoutInternet() {
        val initialList = setOf(TileSpec.create("a"), TileSpec.create("b"), TileSpec.create("c"))

        val migrated: Set<TileSpec> = initialList.migrateInternetTile()

        assertThat(migrated)
            .isEqualTo(setOf(TileSpec.create("a"), TileSpec.create("b"), TileSpec.create("c")))
    }

    @Test
    @DisableFlags(QsSplitInternetTile.FLAG_NAME)
    fun migrateListWithWifi() {
        val initialList =
            listOf(TileSpec.create("a"), TileSpec.create("b"), wifiSpec, TileSpec.create("c"))

        val migrated: List<TileSpec> = initialList.migrateInternetTile()

        assertThat(migrated)
            .isEqualTo(
                listOf(
                    TileSpec.create("a"),
                    TileSpec.create("b"),
                    internetSpec,
                    TileSpec.create("c"),
                )
            )
    }

    @Test
    @DisableFlags(QsSplitInternetTile.FLAG_NAME)
    fun migrateListWithoutWifi() {
        val initialList = listOf(TileSpec.create("a"), TileSpec.create("b"), TileSpec.create("c"))

        val migrated: List<TileSpec> = initialList.migrateInternetTile()

        assertThat(migrated)
            .isEqualTo(listOf(TileSpec.create("a"), TileSpec.create("b"), TileSpec.create("c")))
    }

    @Test
    @DisableFlags(QsSplitInternetTile.FLAG_NAME)
    fun migrateSetWithWifi() {
        val initialList =
            setOf(TileSpec.create("a"), TileSpec.create("b"), wifiSpec, TileSpec.create("c"))

        val migrated: Set<TileSpec> = initialList.migrateInternetTile()

        assertThat(migrated)
            .isEqualTo(
                setOf(
                    TileSpec.create("a"),
                    TileSpec.create("b"),
                    internetSpec,
                    TileSpec.create("c"),
                )
            )
    }

    @Test
    @DisableFlags(QsSplitInternetTile.FLAG_NAME)
    fun migrateSetWithoutWifi() {
        val initialList = setOf(TileSpec.create("a"), TileSpec.create("b"), TileSpec.create("c"))

        val migrated: Set<TileSpec> = initialList.migrateInternetTile()

        assertThat(migrated)
            .isEqualTo(setOf(TileSpec.create("a"), TileSpec.create("b"), TileSpec.create("c")))
    }

    private companion object {
        val internetSpec = TileSpec.create(INTERNET_TILE_SPEC)
        val wifiSpec = TileSpec.create(WIFI_TILE_SPEC)
    }
}
