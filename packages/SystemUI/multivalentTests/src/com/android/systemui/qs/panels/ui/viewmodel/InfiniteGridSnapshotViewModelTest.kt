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

package com.android.systemui.qs.panels.ui.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.qs.panels.domain.interactor.iconTilesInteractor
import com.android.systemui.qs.pipeline.domain.interactor.currentTilesInteractor
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class InfiniteGridSnapshotViewModelTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.underTest by Kosmos.Fixture { infiniteGridSnapshotViewModelFactory.create() }

    @Before
    fun setUp() =
        kosmos.run {
            currentTilesInteractor.setTiles(TestCurrentTiles)
            iconTilesInteractor.setLargeTiles(TestLargeTiles)
        }

    @Test
    fun undo_multipleTimes_restoresState() =
        kosmos.runTest {
            // Take multiple snapshots with changes in between
            for (i in 0..3) {
                underTest.takeSnapshot(
                    currentTilesInteractor.currentTilesSpecs,
                    iconTilesInteractor.largeTilesSpecs.value,
                )
                currentTilesInteractor.setTiles(listOf(TestCurrentTiles[i]))
                iconTilesInteractor.setLargeTiles(setOf(TestCurrentTiles[i]))
            }

            // Assert we can go back the stack of states with undo
            for (i in 3 downTo 0) {
                assertThat(currentTilesInteractor.currentTilesSpecs)
                    .containsExactly(TestCurrentTiles[i])
                assertThat(iconTilesInteractor.largeTilesSpecs.value)
                    .containsExactly(TestCurrentTiles[i])

                // Undo the change
                assertThat(underTest.canUndo).isTrue()
                underTest.undo()
            }

            // Assert that it is no longer possible to undo
            assertThat(underTest.canUndo).isFalse()
            // Assert that the tiles are back to the initial state
            assertThat(currentTilesInteractor.currentTilesSpecs)
                .containsExactlyElementsIn(TestCurrentTiles)
                .inOrder()
            // Assert that the large tiles are back to the initial state
            assertThat(iconTilesInteractor.largeTilesSpecs.value)
                .containsExactlyElementsIn(TestLargeTiles)
        }

    @Test
    fun undo_overMaximum_forgetsOlderSnapshots() =
        kosmos.runTest {
            // Set a simple initial state
            currentTilesInteractor.setTiles(listOf(TestCurrentTiles[0]))

            // Take snapshots more times than the maximum amount, with changes in between
            for (i in 1..<TestCurrentTiles.size) {
                underTest.takeSnapshot(
                    currentTilesInteractor.currentTilesSpecs,
                    iconTilesInteractor.largeTilesSpecs.value,
                )
                currentTilesInteractor.setTiles(listOf(TestCurrentTiles[i]))
            }

            // Undo until we exhausted the snapshots stack
            var undoCount = 0
            while (underTest.canUndo) {
                undoCount++
                underTest.undo()
            }

            // Assert we used undo the same amount of times as the maximum allowed
            assertThat(undoCount).isEqualTo(SNAPSHOTS_MAX_SIZE)
            // Assert that the tiles are NOT back to the initial state
            assertThat(currentTilesInteractor.currentTilesSpecs).doesNotContain(TestCurrentTiles[0])
        }

    @Test
    fun undo_onEmptyStack_isIgnored() =
        kosmos.runTest {
            // Apply a change
            currentTilesInteractor.setTiles(listOf(TestCurrentTiles[0]))

            // Attempt to undo without taking a snapshot
            underTest.undo()

            // Assert that nothing changed
            assertThat(currentTilesInteractor.currentTilesSpecs)
                .containsExactly(TestCurrentTiles[0])
        }

    @Test
    fun clearStack_forgetsOlderSnapshots() =
        kosmos.runTest {
            // Apply a change
            currentTilesInteractor.setTiles(listOf(TestCurrentTiles[0]))

            // Take a snapshot
            underTest.takeSnapshot(
                currentTilesInteractor.currentTilesSpecs,
                iconTilesInteractor.largeTilesSpecs.value,
            )

            // Assert that the snapshot is saved
            assertThat(underTest.canUndo).isTrue()

            // Clear the stack and attempt to undo
            underTest.clearStack()
            underTest.undo()

            // Assert that change was not reverted
            assertThat(currentTilesInteractor.currentTilesSpecs)
                .containsExactly(TestCurrentTiles[0])
        }

    private companion object {
        val SNAPSHOTS_MAX_SIZE = 10
        val TestCurrentTiles = buildList { repeat(15) { add(TileSpec.create("$it")) } }
        val TestLargeTiles = setOf(TestCurrentTiles[0], TestCurrentTiles[1])
    }
}
