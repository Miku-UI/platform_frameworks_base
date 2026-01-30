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
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.res.R
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class HsuTilesRepositoryTest : SysuiTestCase() {

    private val kosmos = testKosmos()

    @Test
    fun isTileAllowed_withEmptyAllowList_allowAllTiles() =
        with(kosmos) {
            testScope.runTest {
                overrideAllowListResource(emptyArray())
                val underTest = HsuTilesRepository(testCase.context.resources)

                val result = underTest.isTileAllowed(TileSpec.create("a"))

                assertThat(result).isTrue()
            }
        }

    @Test
    fun isTileAllowed_withAllowList_returnTrueForAllowedTile() =
        with(kosmos) {
            testScope.runTest {
                overrideAllowListResource(arrayOf("a", "b"))
                val underTest = HsuTilesRepository(testCase.context.resources)

                val result = underTest.isTileAllowed(TileSpec.create("a"))

                assertThat(result).isTrue()
            }
        }

    @Test
    fun isTileAllowed_withAllowList_returnFalseForNotAllowedTile() =
        with(kosmos) {
            testScope.runTest {
                overrideAllowListResource(arrayOf("a", "b"))
                val underTest = HsuTilesRepository(testCase.context.resources)

                val result = underTest.isTileAllowed(TileSpec.create("c"))

                assertThat(result).isFalse()
            }
        }

    private fun overrideAllowListResource(allowList: Array<String>) =
        with(kosmos) {
            testCase.context.orCreateTestableResources.addOverride(
                R.array.hsu_allow_list_qs_tiles,
                allowList,
            )
        }
}
