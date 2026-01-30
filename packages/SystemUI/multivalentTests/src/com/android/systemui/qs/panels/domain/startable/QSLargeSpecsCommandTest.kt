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

package com.android.systemui.qs.panels.domain.startable

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.qs.panels.data.repository.defaultLargeTilesRepository
import com.android.systemui.qs.panels.domain.interactor.iconTilesInteractor
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.statusbar.commandline.commandRegistry
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import java.io.PrintWriter
import java.io.StringWriter
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class QSLargeSpecsCommandTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.underTest by Kosmos.Fixture { qsLargeSpecsCommand }

    @Before fun setUp() = kosmos.run { underTest.start() }

    @Test
    fun commandSetLargeSpecs_correctlySetsSpecs() =
        kosmos.runTest {
            val largeSpecs by collectLastValue(iconTilesInteractor.largeTilesSpecs)

            // Assert default state
            assertThat(largeSpecs)
                .containsAtLeastElementsIn(defaultLargeTilesRepository.defaultLargeTiles)

            // Sets new large tiles
            commandRegistry.onShellCommand(
                PrintWriter(StringWriter()),
                arrayOf("set-large-tiles", "airplane,rotation,flashlight"),
            )

            // Assert new state
            assertThat(largeSpecs)
                .containsExactly(
                    TileSpec.create("airplane"),
                    TileSpec.create("rotation"),
                    TileSpec.create("flashlight"),
                )
        }

    @Test
    fun commandSetLargeSpecs_correctlyRemovesAllSpecs() =
        kosmos.runTest {
            val largeSpecs by collectLastValue(iconTilesInteractor.largeTilesSpecs)

            // Assert default state
            assertThat(largeSpecs)
                .containsAtLeastElementsIn(defaultLargeTilesRepository.defaultLargeTiles)

            // Sets an empty set of specs
            commandRegistry.onShellCommand(PrintWriter(StringWriter()), arrayOf("set-large-tiles"))

            // Assert empty set
            assertThat(largeSpecs).isEmpty()
        }

    @Test
    fun commandRestoreLargeSpecs_correctlyRestoresDefault() =
        kosmos.runTest {
            val largeSpecs by collectLastValue(iconTilesInteractor.largeTilesSpecs)

            // Sets new large tile
            iconTilesInteractor.setLargeTiles(setOf(TileSpec.create("bt")))

            // Assert new state
            assertThat(largeSpecs).containsExactly(TileSpec.create("bt"))

            // Sets new large tiles
            commandRegistry.onShellCommand(
                PrintWriter(StringWriter()),
                arrayOf("restore-large-tiles"),
            )

            // Assert default state
            assertThat(largeSpecs)
                .containsAtLeastElementsIn(defaultLargeTilesRepository.defaultLargeTiles)
        }
}
