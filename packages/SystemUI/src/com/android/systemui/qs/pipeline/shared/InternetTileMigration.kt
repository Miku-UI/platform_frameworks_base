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

import com.android.systemui.log.core.Logger
import com.android.systemui.qs.flags.QsSplitInternetTile
import com.android.systemui.statusbar.connectivity.ConnectivityModule.Companion.INTERNET_TILE_SPEC
import com.android.systemui.statusbar.connectivity.ConnectivityModule.Companion.WIFI_TILE_SPEC

object InternetTileMigration {
    private val internetTileSpec = TileSpec.create(INTERNET_TILE_SPEC)
    private val wifiTileSpec = TileSpec.create(WIFI_TILE_SPEC)

    /**
     * Migrates a list of tiles from `internet` to `wifi` or viceversa depending on the value of
     * [QsSplitInternetTile.isEnabled].
     *
     * If [QsSplitInternetTile.isEnabled] is `true`, instances of `internet` will be converted to
     * `wifi`.
     *
     * If [QsSplitInternetTile.isEnabled] is `false`, instances of `wifi` will be converted to
     * `internet`.
     */
    @JvmStatic
    fun List<TileSpec>.migrateInternetTile(): List<TileSpec> {
        return migrateInternetTileInternal()
    }

    /**
     * Migrates a set of tiles from `internet` to `wifi` or viceversa depending on the value of
     * [QsSplitInternetTile.isEnabled].
     *
     * If [QsSplitInternetTile.isEnabled] is `true`, `internet` will be converted to `wifi`.
     *
     * If [QsSplitInternetTile.isEnabled] is `false`, `wifi` will be converted to `internet`.
     */
    @JvmStatic
    fun Set<TileSpec>.migrateInternetTile(): Set<TileSpec> {
        return migrateInternetTileInternal().toSet()
    }

    private fun Collection<TileSpec>.migrateInternetTileInternal(): List<TileSpec> {
        return map(flagCheckedMap)
    }

    private inline val flagCheckedMap: (TileSpec) -> TileSpec
        get() =
            if (QsSplitInternetTile.isEnabled) {
                ::fromInternetToWifi
            } else {
                ::fromWifiToInternet
            }

    private fun fromInternetToWifi(tile: TileSpec): TileSpec {
        return if (tile == internetTileSpec) wifiTileSpec else tile
    }

    private fun fromWifiToInternet(tile: TileSpec): TileSpec {
        return if (tile == wifiTileSpec) internetTileSpec else tile
    }

    private const val migrationInternetToWifi = "internet -> wifi"
    private const val migrationWifiToInternet = "wifi -> internet"

    /** String to be used by loggers indicating the migration that is performed. */
    @JvmStatic
    val migrationString =
        if (QsSplitInternetTile.isEnabled) {
            migrationInternetToWifi
        } else {
            migrationWifiToInternet
        }

    fun Logger.logMigration() {
        i("Migration performed: $migrationString")
    }
}
