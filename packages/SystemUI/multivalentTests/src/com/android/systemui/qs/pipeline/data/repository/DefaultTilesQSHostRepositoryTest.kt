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

package com.android.systemui.qs.pipeline.data.repository

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testCase
import com.android.systemui.qs.flags.QsSplitInternetTile
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.res.R
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class DefaultTilesQSHostRepositoryTest : SysuiTestCase() {

    private val kosmos = testKosmos()

    private val Kosmos.underTest by
        Kosmos.Fixture {
            DefaultTilesQSHostRepository(testCase.context.resources, hsuTilesRepository)
        }

    @Test
    @DisableFlags(QsSplitInternetTile.FLAG_NAME)
    fun flagDisabled_hasInternet_noWifi() =
        kosmos.runTest {
            assertThat(underTest.getDefaultTiles(false)).contains(internetTileSpec)
            assertThat(underTest.getDefaultTiles(false)).doesNotContain(wifiTileSpec)
        }

    @Test
    @EnableFlags(QsSplitInternetTile.FLAG_NAME)
    @DisableFlags(QsSplitInternetTile.SUPPRESSION_FLAG_NAME)
    fun flagEnabled_hasWifi_noInternet() =
        kosmos.runTest {
            assertThat(underTest.getDefaultTiles(false)).contains(wifiTileSpec)
            assertThat(underTest.getDefaultTiles(false)).doesNotContain(internetTileSpec)
        }

    @Test
    fun getDefaultTiles_notHeadlessSystemUser_returnDefault() =
        kosmos.runTest {
            overrideDefaultTilesResource(CUSTOM_DEFAULT_TILES)
            val isHeadlessSystemUser = false

            val result = underTest.getDefaultTiles(isHeadlessSystemUser)

            assertThat(result).isEqualTo(TilesSettingConverter.toTilesList(CUSTOM_DEFAULT_TILES))
        }

    @Test
    fun getDefaultTiles_isHeadlessSystemUser_returnHsuAllowList() =
        kosmos.runTest {
            overrideDefaultTilesResource(CUSTOM_DEFAULT_TILES)
            val isHeadlessSystemUser = true
            overrideHsuAllowListResource(arrayOf("x", "y", "z"))

            val result = underTest.getDefaultTiles(isHeadlessSystemUser)

            assertThat(result).isEqualTo(TilesSettingConverter.toTilesList("x,y,z"))
        }

    @Test
    fun getDefaultTiles_isHeadlessSystemUserWithEmptyHsuAllowList_returnDefault() =
        kosmos.runTest {
            overrideDefaultTilesResource(CUSTOM_DEFAULT_TILES)
            val isHeadlessSystemUser = true
            overrideHsuAllowListResource(emptyArray())

            val result = underTest.getDefaultTiles(isHeadlessSystemUser)

            assertThat(result).isEqualTo(TilesSettingConverter.toTilesList(CUSTOM_DEFAULT_TILES))
        }

    @Test
    @EnableFlags(QsSplitInternetTile.FLAG_NAME)
    @DisableFlags(QsSplitInternetTile.SUPPRESSION_FLAG_NAME)
    fun getDefaultTiles_headlessSystemUser_withInternetTile_flagEnabled_hasWifiInstead() =
        kosmos.runTest {
            overrideDefaultTilesResource(CUSTOM_DEFAULT_TILES)
            val isHeadlessSystemUser = true
            overrideHsuAllowListResource(arrayOf("x", "internet", "z"))

            val result = underTest.getDefaultTiles(isHeadlessSystemUser)

            assertThat(result).isEqualTo(TilesSettingConverter.toTilesList("x,wifi,z"))
        }

    private fun Kosmos.overrideDefaultTilesResource(defaultTiles: String) {
        testCase.context.orCreateTestableResources.addOverride(
            if (QsSplitInternetTile.isEnabled) {
                R.string.quick_settings_tiles_default_split
            } else {
                R.string.quick_settings_tiles_default
            },
            defaultTiles,
        )
    }

    private fun Kosmos.overrideHsuAllowListResource(allowList: Array<String>) {
        testCase.context.orCreateTestableResources.addOverride(
            R.array.hsu_allow_list_qs_tiles,
            allowList,
        )
    }

    companion object {
        private val internetTileSpec = TileSpec.create("internet")
        private val wifiTileSpec = TileSpec.create("wifi")
        private const val CUSTOM_DEFAULT_TILES = "a,b,c"
    }
}
