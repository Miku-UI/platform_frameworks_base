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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.testCase
import com.android.systemui.kosmos.testScope
import com.android.systemui.res.R
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class DefaultTilesQSHostRepositoryTest : SysuiTestCase() {

    private val kosmos = testKosmos()

    @Before
    fun setup() {
        overrideDefaultTilesResource(DEFAULT_TILES)
    }

    @Test
    fun getDefaultTiles_notHeadlessSystemUser_returnDefault() =
        with(kosmos) {
            testScope.runTest {
                val isHeadlessSystemUser = false
                val underTest =
                    DefaultTilesQSHostRepository(testCase.context.resources, hsuTilesRepository)

                val result = underTest.getDefaultTiles(isHeadlessSystemUser)

                assertThat(result).isEqualTo(TilesSettingConverter.toTilesList(DEFAULT_TILES))
            }
        }

    @Test
    fun getDefaultTiles_isHeadlessSystemUser_returnHsuAllowList() =
        with(kosmos) {
            testScope.runTest {
                val isHeadlessSystemUser = true
                overrideHsuAllowListResource(arrayOf("x", "y", "z"))
                val underTest =
                    DefaultTilesQSHostRepository(testCase.context.resources, hsuTilesRepository)

                val result = underTest.getDefaultTiles(isHeadlessSystemUser)

                assertThat(result).isEqualTo(TilesSettingConverter.toTilesList("x,y,z"))
            }
        }

    @Test
    fun getDefaultTiles_isHeadlessSystemUserWithEmptyHsuAllowList_returnDefault() =
        with(kosmos) {
            testScope.runTest {
                val isHeadlessSystemUser = true
                overrideHsuAllowListResource(emptyArray())
                val underTest =
                    DefaultTilesQSHostRepository(testCase.context.resources, hsuTilesRepository)

                val result = underTest.getDefaultTiles(isHeadlessSystemUser)

                assertThat(result).isEqualTo(TilesSettingConverter.toTilesList(DEFAULT_TILES))
            }
        }

    private fun overrideDefaultTilesResource(defaultTiles: String) =
        with(kosmos) {
            testCase.context.orCreateTestableResources.addOverride(
                R.string.quick_settings_tiles_default,
                defaultTiles,
            )
            testCase.context.orCreateTestableResources.addOverride(
                R.string.quick_settings_tiles_new_default,
                defaultTiles,
            )
        }

    private fun overrideHsuAllowListResource(allowList: Array<String>) =
        with(kosmos) {
            testCase.context.orCreateTestableResources.addOverride(
                R.array.hsu_allow_list_qs_tiles,
                allowList,
            )
        }

    companion object {
        private const val DEFAULT_TILES = "a,b,c"
    }
}
