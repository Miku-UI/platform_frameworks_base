/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.qs.panels.ui.compose

import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.qs.panels.shared.model.SizedTileImpl
import com.android.systemui.qs.panels.ui.compose.selection.PlacementEvent
import com.android.systemui.qs.panels.ui.model.GridCell
import com.android.systemui.qs.panels.ui.model.TileGridCell
import com.android.systemui.qs.panels.ui.viewmodel.EditTileViewModel
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.shared.model.TileCategory
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class EditTileListStateTest : SysuiTestCase() {
    private val underTest =
        EditTileListState(TestEditTiles, TestLargeTiles, columns = 4, largeTilesSpan = 2)

    @Test
    fun startDrag_listHasSpacers() {
        val cell = underTest.tiles[0] as TileGridCell
        underTest.onStarted(cell, DragType.Add)

        // [ a ] [ b ] [ c ] [ X ]
        // [ Large D ] [ e ] [ X ]
        assertThat(underTest.tiles.toStrings())
            .isEqualTo(listOf("a", "b", "c", "spacer", "d", "e", "spacer"))
        assertThat(underTest.isMoving(cell.tile.tileSpec)).isTrue()
        assertThat(underTest.dragInProgress).isTrue()
    }

    @Test
    fun moveDrag_listChanges() {
        val cell = underTest.tiles[5] as TileGridCell
        underTest.onStarted(cell, DragType.Add)
        underTest.onTargeting(3, false)

        // Tile E goes to index 3
        // [ a ] [ b ] [ c ] [ e ]
        // [ Large D ] [ X ] [ X ]
        assertThat(underTest.tiles.toStrings())
            .isEqualTo(listOf("a", "b", "c", "e", "d", "spacer", "spacer"))
    }

    @Test
    fun moveDragOnSidesOfLargeTile_listChanges() {
        val draggedCell = underTest.tiles[5] as TileGridCell

        underTest.onStarted(draggedCell, DragType.Add)
        underTest.onTargeting(4, true)

        // Tile E goes to the right side of tile D, list is unchanged
        // [ a ] [ b ] [ c ] [ X ]
        // [ Large D ] [ e ] [ X ]
        assertThat(underTest.tiles.toStrings())
            .isEqualTo(listOf("a", "b", "c", "spacer", "d", "e", "spacer"))

        underTest.onTargeting(4, false)

        // Tile E goes to the left side of tile D, they swap positions
        // [ a ] [ b ] [ c ] [ e ]
        // [ Large D ] [ X ] [ X ]
        assertThat(underTest.tiles.toStrings())
            .isEqualTo(listOf("a", "b", "c", "e", "d", "spacer", "spacer"))
    }

    @Test
    fun moveNewTile_tileIsAdded() {
        val newTile = SizedTileImpl(createEditTile("newTile"), 2)

        underTest.onStarted(newTile, DragType.Add)
        underTest.onTargeting(5, false)

        // New tile goes to index 5
        // [ a ] [ b ] [ c ] [ X ]
        // [ Large D ] [ newTile ]
        // [ e ] [ X ] [ X ] [ X ]
        assertThat(underTest.tiles.toStrings())
            .isEqualTo(
                listOf("a", "b", "c", "spacer", "d", "newTile", "e", "spacer", "spacer", "spacer")
            )
    }

    @Test
    fun movedTileOutOfBounds_tileDisappears() {
        val cell = underTest.tiles[0] as TileGridCell
        underTest.onStarted(cell, DragType.Add)
        underTest.movedOutOfBounds()

        assertThat(underTest.tiles.toStrings()).doesNotContain(TestEditTiles[0].tileSpec.spec)
    }

    @Test
    fun targetIndexForPlacementToTileSpec_returnsCorrectIndex() {
        val placementEvent =
            PlacementEvent.PlaceToTileSpec(
                movingSpec = TestEditTiles[0].tileSpec,
                targetSpec = TestEditTiles[3].tileSpec,
            )
        val index = underTest.targetIndexForPlacement(placementEvent)

        assertThat(index).isEqualTo(3)
    }

    @Test
    fun targetIndexForPlacementToIndex_indexOutOfBounds_returnsCorrectIndex() {
        val placementEventTooLow =
            PlacementEvent.PlaceToIndex(movingSpec = TestEditTiles[0].tileSpec, targetIndex = -1)
        val index1 = underTest.targetIndexForPlacement(placementEventTooLow)

        assertThat(index1).isEqualTo(0)

        val placementEventTooHigh =
            PlacementEvent.PlaceToIndex(movingSpec = TestEditTiles[0].tileSpec, targetIndex = 10)
        val index2 = underTest.targetIndexForPlacement(placementEventTooHigh)
        assertThat(index2).isEqualTo(TestEditTiles.size)
    }

    @Test
    fun targetIndexForPlacementToIndex_movingBack_returnsCorrectIndex() {
        /**
         * With the grid: [ a ] [ b ] [ c ] [ Large D ] [ e ] [ f ]
         *
         * Moving 'e' to the spacer at index 3 will result in the tilespec order: a, b, c, e, d, f
         *
         * 'e' is now at index 3
         */
        val placementEvent =
            PlacementEvent.PlaceToIndex(movingSpec = TestEditTiles[4].tileSpec, targetIndex = 3)
        val index = underTest.targetIndexForPlacement(placementEvent)

        assertThat(index).isEqualTo(3)
    }

    @Test
    fun targetIndexForPlacementToIndex_movingForward_returnsCorrectIndex() {
        /**
         * With the grid: [ a ] [ b ] [ c ] [ Large D ] [ e ] [ f ]
         *
         * Moving '1' to the spacer at index 3 will result in the tilespec order: b, c, a, d, e, f
         *
         * 'a' is now at index 2
         */
        val placementEvent =
            PlacementEvent.PlaceToIndex(movingSpec = TestEditTiles[0].tileSpec, targetIndex = 3)
        val index = underTest.targetIndexForPlacement(placementEvent)

        assertThat(index).isEqualTo(2)
    }

    @Test
    fun updateTiles_shouldRearrangeGrid() {
        val newTiles =
            listOf(
                createEditTile("1"),
                createEditTile("2"),
                createEditTile("3"),
                createEditTile("4"),
                createEditTile("5"),
                createEditTile("6"),
                createEditTile("7"),
                createEditTile("8"),
            )
        underTest.updateTiles(
            newTiles,
            largeTiles =
                setOf(
                    newTiles[0].tileSpec,
                    newTiles[1].tileSpec,
                    newTiles[6].tileSpec,
                    newTiles[7].tileSpec,
                ),
        )

        // Update should result in
        // [ Large 1 ] [ Large 2 ]
        // [ 3 ] [ 4 ] [ 5 ] [ 6 ]
        // [ Large 7 ] [ Large 8 ]

        assertThat(underTest.tiles)
            .containsExactly(
                TileGridCell(newTiles[0], row = 0, column = 0, width = 2),
                TileGridCell(newTiles[1], row = 0, column = 2, width = 2),
                TileGridCell(newTiles[2], row = 1, column = 0, width = 1),
                TileGridCell(newTiles[3], row = 1, column = 1, width = 1),
                TileGridCell(newTiles[4], row = 1, column = 2, width = 1),
                TileGridCell(newTiles[5], row = 1, column = 3, width = 1),
                TileGridCell(newTiles[6], row = 2, column = 0, width = 2),
                TileGridCell(newTiles[7], row = 2, column = 2, width = 2),
            )
    }

    private fun List<GridCell>.toStrings(): List<String> {
        return map {
            if (it is TileGridCell) {
                it.tile.tileSpec.spec
            } else {
                "spacer"
            }
        }
    }

    companion object {
        private fun createEditTile(tileSpec: String): EditTileViewModel {
            return EditTileViewModel(
                tileSpec = TileSpec.create(tileSpec),
                icon = Icon.Resource(0, null),
                label = AnnotatedString("unused"),
                inlinedLabel = null,
                appName = null,
                isCurrent = true,
                isDualTarget = false,
                availableEditActions = emptySet(),
                appIcon = null,
                category = TileCategory.UNKNOWN,
            )
        }

        // [ a ] [ b ] [ c ]
        // [ Large D ] [ e ] [ f ]
        private val TestEditTiles =
            listOf(
                createEditTile("a"),
                createEditTile("b"),
                createEditTile("c"),
                createEditTile("d"),
                createEditTile("e"),
            )
        private val TestLargeTiles = setOf(TileSpec.create("d"))
    }
}
