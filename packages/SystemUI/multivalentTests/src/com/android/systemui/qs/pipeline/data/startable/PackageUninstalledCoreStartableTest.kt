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

package com.android.systemui.qs.pipeline.data.startable

import android.content.ComponentName
import android.os.UserHandle
import android.service.quicksettings.Tile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.data.repository.fakePackageChangeRepository
import com.android.systemui.common.data.repository.packageChangeRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.qs.external.TileServiceKey
import com.android.systemui.qs.external.customTileStatePersister
import com.android.systemui.qs.external.fakeCustomTileStatePersister
import com.android.systemui.qs.pipeline.data.repository.customTileAddedRepository
import com.android.systemui.qs.pipeline.data.repository.tileSpecRepository
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class PackageUninstalledCoreStartableTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val Kosmos.underTest by
        Kosmos.Fixture {
            PackageUninstalledCoreStartable(
                tileSpecRepository,
                fakeCustomTileStatePersister,
                customTileAddedRepository,
                packageChangeRepository,
                backgroundScope,
            )
        }

    @Test
    fun uninstalledTileServicesRemovedFromStoredState() =
        kosmos.runTest {
            underTest.start()

            val user = 10
            val tiles by collectLastValue(tileSpecRepository.tilesSpecs(user))
            tileSpecRepository.setTiles(user, allTiles)
            allTiles.forEach {
                customTileAddedRepository.setTileAdded(it.componentName, user, true)
                fakeCustomTileStatePersister.persistState(
                    TileServiceKey(it.componentName, user),
                    Tile(),
                )
            }

            // uninstall package 1
            fakePackageChangeRepository.notifyUninstall(PACKAGE_1, UserHandle.of(user))

            assertThat(tiles).containsExactly(allTiles[2], allTiles[3]).inOrder()

            COMPONENT_1_PACKAGE_1.run {
                assertThat(customTileStatePersister.readState(TileServiceKey(this, user))).isNull()
                assertThat(customTileAddedRepository.isTileAdded(this, user)).isFalse()
            }
            COMPONENT_2_PACKAGE_1.run {
                assertThat(customTileStatePersister.readState(TileServiceKey(this, user))).isNull()
                assertThat(customTileAddedRepository.isTileAdded(this, user)).isFalse()
            }

            COMPONENT_1_PACKAGE_2.run {
                assertThat(customTileStatePersister.readState(TileServiceKey(this, user)))
                    .isNotNull()
                assertThat(customTileAddedRepository.isTileAdded(this, user)).isTrue()
            }
            COMPONENT_2_PACKAGE_2.run {
                assertThat(customTileStatePersister.readState(TileServiceKey(this, user)))
                    .isNotNull()
                assertThat(customTileAddedRepository.isTileAdded(this, user)).isTrue()
            }
        }

    @Test
    fun uninstalledTileServicesOtherUser_nothingRemoved() =
        kosmos.runTest {
            underTest.start()

            val user = 10
            val tiles by collectLastValue(tileSpecRepository.tilesSpecs(user))
            tileSpecRepository.setTiles(user, allTiles)
            allTiles.forEach {
                customTileAddedRepository.setTileAdded(it.componentName, user, true)
                fakeCustomTileStatePersister.persistState(
                    TileServiceKey(it.componentName, user),
                    Tile(),
                )
            }

            // uninstall package 1 in other user
            fakePackageChangeRepository.notifyUninstall(PACKAGE_1, UserHandle.of(user + 1))

            assertThat(tiles).isEqualTo(allTiles)

            COMPONENT_1_PACKAGE_1.run {
                assertThat(customTileStatePersister.readState(TileServiceKey(this, user)))
                    .isNotNull()
                assertThat(customTileAddedRepository.isTileAdded(this, user)).isTrue()
            }
            COMPONENT_2_PACKAGE_1.run {
                assertThat(customTileStatePersister.readState(TileServiceKey(this, user)))
                    .isNotNull()
                assertThat(customTileAddedRepository.isTileAdded(this, user)).isTrue()
            }

            COMPONENT_1_PACKAGE_2.run {
                assertThat(customTileStatePersister.readState(TileServiceKey(this, user)))
                    .isNotNull()
                assertThat(customTileAddedRepository.isTileAdded(this, user)).isTrue()
            }
            COMPONENT_2_PACKAGE_2.run {
                assertThat(customTileStatePersister.readState(TileServiceKey(this, user)))
                    .isNotNull()
                assertThat(customTileAddedRepository.isTileAdded(this, user)).isTrue()
            }
        }

    private companion object {
        const val PACKAGE_1 = "pkg1"
        const val PACKAGE_2 = "pkg2"
        val COMPONENT_1_PACKAGE_1 = ComponentName(PACKAGE_1, "cls1")
        val COMPONENT_2_PACKAGE_1 = ComponentName(PACKAGE_1, "cls2")
        val COMPONENT_1_PACKAGE_2 = ComponentName(PACKAGE_2, "cls1")
        val COMPONENT_2_PACKAGE_2 = ComponentName(PACKAGE_2, "cls2")
        val allTiles =
            listOf(
                TileSpec.create(COMPONENT_1_PACKAGE_1),
                TileSpec.create(COMPONENT_2_PACKAGE_1),
                TileSpec.create(COMPONENT_1_PACKAGE_2),
                TileSpec.create(COMPONENT_2_PACKAGE_2),
            )
    }
}
