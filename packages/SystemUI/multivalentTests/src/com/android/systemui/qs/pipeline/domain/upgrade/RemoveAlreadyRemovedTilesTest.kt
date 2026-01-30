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

package com.android.systemui.qs.pipeline.domain.upgrade

import android.content.ComponentName
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.qs.pipeline.data.repository.customTileAddedRepository
import com.android.systemui.qs.pipeline.data.repository.fakeInstalledTilesRepository
import com.android.systemui.qs.pipeline.domain.interactor.currentTilesInteractor
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(Flags.FLAG_RESET_TILES_REMOVES_CUSTOM_TILES)
class RemoveAlreadyRemovedTilesTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val Kosmos.underTest: CustomTileAddedUpgrade by
        Kosmos.Fixture { RemoveAlreadyRemovedTiles(currentTilesInteractor) }

    @Test fun version() = kosmos.runTest { assertThat(underTest.version).isEqualTo(2) }

    @Test
    fun upgrade_requestRemoveComponents() =
        kosmos.runTest {
            val currentUser = currentTilesInteractor.userId.value
            val storedComponents =
                listOf(
                    TileSpec.create(ComponentName("a", "b")),
                    TileSpec.create(ComponentName("c", "d")),
                    TileSpec.create(ComponentName("e", "f")),
                )
            fakeInstalledTilesRepository.setInstalledPackagesForUser(
                currentUser,
                storedComponents.map { it.componentName }.toSet(),
            )
            val currentTiles = listOf(TileSpec.create("a"), storedComponents[0])
            currentTilesInteractor.setTiles(currentTiles)

            // There are stored components that are not part of the current tiles
            storedComponents.forEach {
                customTileAddedRepository.setTileAdded(it.componentName, currentUser, added = true)
            }

            with(underTest) { customTileAddedRepository.upgradeForUser(currentUser) }

            assertThat(
                    customTileAddedRepository.isTileAdded(
                        storedComponents[0].componentName,
                        currentUser,
                    )
                )
                .isTrue()
            assertThat(
                    customTileAddedRepository.isTileAdded(
                        storedComponents[1].componentName,
                        currentUser,
                    )
                )
                .isFalse()
            assertThat(
                    customTileAddedRepository.isTileAdded(
                        storedComponents[2].componentName,
                        currentUser,
                    )
                )
                .isFalse()
        }
}
