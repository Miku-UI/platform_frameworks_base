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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.theme.PlatformTheme
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.qs.panels.ui.compose.infinitegrid.DefaultEditTileGrid
import com.android.systemui.qs.panels.ui.compose.infinitegrid.EditAction
import com.android.systemui.qs.panels.ui.model.GridCell
import com.android.systemui.qs.panels.ui.model.TileGridCell
import com.android.systemui.qs.panels.ui.viewmodel.EditTileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.infiniteGridSnapshotViewModelFactory
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.shared.model.TileCategory
import com.android.systemui.res.R
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class ResizingTest : SysuiTestCase() {

    @get:Rule val composeRule = createComposeRule()

    private val kosmos = testKosmos()
    private val snapshotViewModelFactory = kosmos.infiniteGridSnapshotViewModelFactory

    @Composable
    private fun EditTileGridUnderTest(
        listState: EditTileListState,
        tiles: List<EditTileViewModel> = TestEditTiles,
        largeTiles: Set<TileSpec> = TestLargeTilesSpecs,
        onResize: (EditAction.ResizeTile) -> Unit = {},
    ) {
        val largeTilesSpecs = remember { largeTiles.toMutableSet() }
        PlatformTheme {
            DefaultEditTileGrid(
                listState = listState,
                allTiles = listState.tiles.filterIsInstance<TileGridCell>().map { it.tile },
                modifier = Modifier.fillMaxSize(),
                snapshotViewModel = remember { snapshotViewModelFactory.create() },
                topBarActions = remember { mutableStateListOf() },
                onStopEditing = {},
            ) { action ->
                when (action) {
                    is EditAction.ResizeTile -> {
                        onResize(action)

                        if (action.toIcon) largeTilesSpecs.remove(action.tileSpec)
                        else largeTilesSpecs.add(action.tileSpec)
                        listState.updateTiles(tiles, largeTilesSpecs)
                    }
                    else -> {}
                }
            }
        }
    }

    private fun ComposeContentTestRule.setEditContent(
        listState: EditTileListState,
        tiles: List<EditTileViewModel> = TestEditTiles,
        largeTiles: Set<TileSpec> = TestLargeTilesSpecs,
        onResize: (EditAction.ResizeTile) -> Unit = {},
    ) {
        setContent { EditTileGridUnderTest(listState, tiles, largeTiles, onResize) }
        waitForIdle()
    }

    @Test
    fun toggleIconTileWithA11yAction_shouldBeLarge() {
        val listState =
            EditTileListState(TestEditTiles, TestLargeTilesSpecs, columns = 4, largeTilesSpan = 2)
        var resizedAction: EditAction.ResizeTile? = null
        composeRule.setEditContent(listState) { resizedAction = it }

        composeRule
            .onNodeWithContentDescription("tileA")
            .performCustomAccessibilityActionWithLabel(
                context.getString(R.string.accessibility_qs_edit_toggle_tile_size_action)
            )

        assertTileHasWidth(listState.tiles, "tileA", 2)
        assertThat(resizedAction!!.tileSpec).isEqualTo(TestEditTiles[0].tileSpec)
        assertThat(resizedAction!!.toIcon).isFalse()
    }

    @Test
    fun toggleLargeTileWithA11yAction_shouldBeIcon() {
        val listState =
            EditTileListState(TestEditTiles, TestLargeTilesSpecs, columns = 4, largeTilesSpan = 2)
        var resizedAction: EditAction.ResizeTile? = null
        composeRule.setEditContent(listState) { resizedAction = it }

        composeRule
            .onNodeWithContentDescription("tileD_large")
            .performCustomAccessibilityActionWithLabel(
                context.getString(R.string.accessibility_qs_edit_toggle_tile_size_action)
            )

        assertTileHasWidth(listState.tiles, "tileD_large", 1)
        assertThat(resizedAction!!.tileSpec).isEqualTo(TestEditTiles[3].tileSpec)
        assertThat(resizedAction!!.toIcon).isTrue()
    }

    @Test
    fun tapOnIconResizingHandle_shouldBeLarge() {
        val listState =
            EditTileListState(TestEditTiles, TestLargeTilesSpecs, columns = 4, largeTilesSpan = 2)
        var resizedAction: EditAction.ResizeTile? = null
        composeRule.setEditContent(listState) { resizedAction = it }

        composeRule
            .onNodeWithContentDescription("tileA")
            .performClick() // Select
            .performTouchInput { // Tap on resizing handle
                click(centerRight)
            }
        composeRule.waitForIdle()

        assertTileHasWidth(listState.tiles, "tileA", 2)
        assertThat(resizedAction!!.tileSpec).isEqualTo(TestEditTiles[0].tileSpec)
        assertThat(resizedAction!!.toIcon).isFalse()
    }

    @Test
    fun tapOnLargeResizingHandle_shouldBeIcon() {
        val listState =
            EditTileListState(TestEditTiles, TestLargeTilesSpecs, columns = 4, largeTilesSpan = 2)
        var resizedAction: EditAction.ResizeTile? = null
        composeRule.setEditContent(listState) { resizedAction = it }

        composeRule
            .onNodeWithContentDescription("tileD_large")
            .performClick() // Select
            .performTouchInput { // Tap on resizing handle
                click(centerRight)
            }
        composeRule.waitForIdle()

        assertTileHasWidth(listState.tiles, "tileD_large", 1)
        assertThat(resizedAction!!.tileSpec).isEqualTo(TestEditTiles[3].tileSpec)
        assertThat(resizedAction!!.toIcon).isTrue()
    }

    @Test
    fun resizedIcon_shouldBeLarge() {
        val listState =
            EditTileListState(TestEditTiles, TestLargeTilesSpecs, columns = 4, largeTilesSpan = 2)
        var resizedAction: EditAction.ResizeTile? = null
        composeRule.setEditContent(listState) { resizedAction = it }

        composeRule
            .onNodeWithContentDescription("tileA")
            .performClick() // Select
            .performTouchInput { // Resize up
                swipeRight(startX = right, endX = right * 2)
            }
        composeRule.waitForIdle()

        assertTileHasWidth(listState.tiles, "tileA", 2)
        assertThat(resizedAction!!.tileSpec).isEqualTo(TestEditTiles[0].tileSpec)
        assertThat(resizedAction!!.toIcon).isFalse()
    }

    @Test
    fun resizedLarge_shouldBeIcon() {
        val listState =
            EditTileListState(TestEditTiles, TestLargeTilesSpecs, columns = 4, largeTilesSpan = 2)
        var resizedAction: EditAction.ResizeTile? = null
        composeRule.setEditContent(listState) { resizedAction = it }

        composeRule
            .onNodeWithContentDescription("tileD_large")
            .performClick() // Select
            .performTouchInput { // Resize down
                swipeLeft()
            }
        composeRule.waitForIdle()

        assertTileHasWidth(listState.tiles, "tileD_large", 1)
        assertThat(resizedAction!!.tileSpec).isEqualTo(TestEditTiles[3].tileSpec)
        assertThat(resizedAction!!.toIcon).isTrue()
    }

    @Test
    fun resizedIconFromEdge_shouldBeLarge() {
        val testTiles =
            listOf(
                createEditTile("tileA"),
                createEditTile("tileB"),
                createEditTile("tileC"),
                createEditTile("tileD"),
            )
        val listState = EditTileListState(testTiles, emptySet(), columns = 4, largeTilesSpan = 2)
        var resizedAction: EditAction.ResizeTile? = null

        composeRule.setEditContent(listState, testTiles) { resizedAction = it }

        composeRule
            .onNodeWithContentDescription("tileD")
            .performClick() // Select
            .performTouchInput { // Tap on resizing handle
                click(centerRight)
            }
        composeRule.waitForIdle()

        // Assert that tileD was resized to large
        assertTileHasWidth(listState.tiles, "tileD", 2)
        assertThat(resizedAction!!.tileSpec).isEqualTo(testTiles[3].tileSpec)
        assertThat(resizedAction!!.toIcon).isFalse()
    }

    private fun assertTileHasWidth(tiles: List<GridCell>, spec: String, expectedWidth: Int) {
        val tile =
            tiles.find { it is TileGridCell && it.tile.tileSpec.spec == spec } as TileGridCell
        assertThat(tile.width).isEqualTo(expectedWidth)
    }

    companion object {

        private fun createEditTile(tileSpec: String): EditTileViewModel {
            return EditTileViewModel(
                tileSpec = TileSpec.create(tileSpec),
                icon =
                    Icon.Resource(android.R.drawable.star_on, ContentDescription.Loaded(tileSpec)),
                label = AnnotatedString(tileSpec),
                inlinedLabel = null,
                appName = null,
                isCurrent = true,
                isDualTarget = false,
                availableEditActions = emptySet(),
                appIcon = null,
                category = TileCategory.UNKNOWN,
            )
        }

        private val TestEditTiles =
            listOf(
                createEditTile("tileA"),
                createEditTile("tileB"),
                createEditTile("tileC"),
                createEditTile("tileD_large"),
                createEditTile("tileE"),
            )
        private val TestLargeTilesSpecs =
            TestEditTiles.filter { it.tileSpec.spec.endsWith("large") }.map { it.tileSpec }.toSet()
    }
}
