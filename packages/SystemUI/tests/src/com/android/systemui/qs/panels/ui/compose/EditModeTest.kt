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

package com.android.systemui.qs.panels.ui.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.theme.PlatformTheme
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.compose.modifiers.resIdToTestTag
import com.android.systemui.qs.panels.ui.compose.infinitegrid.DefaultEditTileGrid
import com.android.systemui.qs.panels.ui.compose.infinitegrid.EditAction
import com.android.systemui.qs.panels.ui.viewmodel.AvailableEditActions
import com.android.systemui.qs.panels.ui.viewmodel.EditTileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.infiniteGridSnapshotViewModelFactory
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.shared.model.TileCategory
import com.android.systemui.res.R
import com.android.systemui.testKosmos
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class EditModeTest : SysuiTestCase() {
    @get:Rule val composeRule = createComposeRule()

    private val kosmos = testKosmos()
    private val snapshotViewModelFactory = kosmos.infiniteGridSnapshotViewModelFactory

    @Composable
    private fun EditTileGridUnderTest() {
        val allTiles = remember { TestEditTiles.toMutableStateList() }
        val largeTiles = remember { TestLargeTilesSpecs.toMutableStateList() }
        val currentTiles = allTiles.filter { it.isCurrent }
        val listState = remember {
            EditTileListState(currentTiles, TestLargeTilesSpecs, columns = 4, largeTilesSpan = 2)
        }
        LaunchedEffect(currentTiles, largeTiles) {
            listState.updateTiles(currentTiles, largeTiles.toSet())
        }

        val snapshotViewModel = remember { snapshotViewModelFactory.create() }

        PlatformTheme {
            DefaultEditTileGrid(
                listState = listState,
                allTiles = allTiles,
                modifier = Modifier.fillMaxSize(),
                snapshotViewModel = snapshotViewModel,
                topBarActions = remember { mutableStateListOf() },
                onStopEditing = {},
            ) { action ->
                snapshotViewModel.takeSnapshot(
                    currentTiles.map { it.tileSpec },
                    TestLargeTilesSpecs,
                )

                when (action) {
                    is EditAction.AddTile -> {
                        val index = allTiles.indexOfFirst { it.tileSpec == action.tileSpec }
                        allTiles[index] = allTiles[index].copy(isCurrent = true)
                    }
                    is EditAction.InsertTile -> {
                        val index = allTiles.indexOfFirst { it.tileSpec == action.tileSpec }
                        allTiles[index] = allTiles[index].copy(isCurrent = true)
                    }
                    is EditAction.RemoveTile -> {
                        val index = allTiles.indexOfFirst { it.tileSpec == action.tileSpec }
                        allTiles[index] = allTiles[index].copy(isCurrent = false)
                    }
                    is EditAction.ResizeTile -> {
                        if (action.toIcon) {
                            largeTiles.remove(action.tileSpec)
                        } else {
                            largeTiles.add(action.tileSpec)
                        }
                    }
                    else -> error("Not expecting action $action from test")
                }
            }
        }
    }

    @Test
    fun clickAvailableTile_shouldAdd() {
        composeRule.setContent { EditTileGridUnderTest() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("tileF").performClick() // Tap to add

        composeRule.assertCurrentTilesGridContainsExactly(
            listOf("tileA", "tileB", "tileC", "tileD_large", "tileE", "tileF")
        )
        composeRule.assertAvailableTilesGridContainsExactly(TestEditTiles.map { it.tileSpec.spec })
    }

    @Test
    fun clickCurrentTile_shouldRemove() {
        composeRule.setContent { EditTileGridUnderTest() }
        composeRule.waitForIdle()

        // Tap to remove
        composeRule
            .onAllNodesWithContentDescription(
                context.getString(R.string.accessibility_qs_edit_remove_tile_action)
            )
            .onFirst()
            .performClick()

        composeRule.assertCurrentTilesGridContainsExactly(
            listOf("tileB", "tileC", "tileD_large", "tileE")
        )
        composeRule.assertAvailableTilesGridContainsExactly(TestEditTiles.map { it.tileSpec.spec })
    }

    @Test
    fun placementMode_shouldRepositionTile() {
        composeRule.setContent { EditTileGridUnderTest() }
        composeRule.waitForIdle()

        // Double tap first "tileA", i.e. the one in the current grid
        composeRule.onNodeWithContentDescription("tileA").performTouchInput { doubleClick() }

        // Tap on tileE to position tileA in its spot
        composeRule.onNodeWithContentDescription("tileE").performClick()

        // Assert tileA moved to tileE's position
        composeRule.assertCurrentTilesGridContainsExactly(
            listOf("tileB", "tileC", "tileD_large", "tileE", "tileA")
        )
    }

    @Test
    fun resizingAction_dependsOnPlacementMode() {
        composeRule.setContent { EditTileGridUnderTest() }
        composeRule.waitForIdle()

        // Use the toggle size action
        composeRule
            .onNodeWithContentDescription("tileE")
            .performCustomAccessibilityActionWithLabel(
                context.getString(R.string.accessibility_qs_edit_toggle_tile_size_action)
            )

        // Double tap "tileA" to enable placement mode
        composeRule.onNodeWithContentDescription("tileA").performTouchInput { doubleClick() }

        // Assert the toggle size action is missing
        assertThrows(AssertionError::class.java) {
            composeRule
                .onNodeWithContentDescription("tileE")
                .performCustomAccessibilityActionWithLabel(
                    context.getString(R.string.accessibility_qs_edit_toggle_tile_size_action)
                )
        }
    }

    @Test
    fun placementAction_dependsOnPlacementMode() {
        composeRule.setContent { EditTileGridUnderTest() }
        composeRule.waitForIdle()

        // Assert the placement action is missing
        assertThrows(AssertionError::class.java) {
            composeRule
                .onNodeWithContentDescription("tileE")
                .performCustomAccessibilityActionWithLabel(
                    context.getString(R.string.accessibility_qs_edit_place_tile_action)
                )
        }

        // Double tap "tileA" to enable placement mode
        composeRule.onNodeWithContentDescription("tileA").performTouchInput { doubleClick() }

        // Use the placement action
        composeRule
            .onNodeWithContentDescription("tileE")
            .performCustomAccessibilityActionWithLabel(
                context.getString(R.string.accessibility_qs_edit_place_tile_action)
            )
    }

    @Test
    fun performAction_undoAppears() {
        composeRule.setContent { EditTileGridUnderTest() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("tileF").performClick() // Tap to add
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Undo").assertExists()

        composeRule.onNodeWithContentDescription("Undo").performClick() // Undo addition
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Undo").assertDoesNotExist()
    }

    @Test
    fun gridHeader_dependsOnPlacementMode() {
        composeRule.setContent { EditTileGridUnderTest() }
        composeRule.waitForIdle()

        // Assert the idle string is showing
        composeRule
            .onNodeWithText(context.getString(R.string.select_to_rearrange_tiles))
            .assertExists()
        composeRule
            .onNodeWithText(context.getString(R.string.tap_to_position_tile))
            .assertDoesNotExist()

        // Double tap "tileA" to enable placement mode
        composeRule.onNodeWithContentDescription("tileA").performTouchInput { doubleClick() }

        // Assert the "Tap to position" string is showing
        composeRule
            .onNodeWithText(context.getString(R.string.select_to_rearrange_tiles))
            .assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.tap_to_position_tile)).assertExists()
    }

    @Test
    fun visibleAvailableTiles_dependsOnPlacementMode() {
        composeRule.setContent { EditTileGridUnderTest() }
        composeRule.waitForIdle()

        // Assert the available tiles are visible
        composeRule.onNodeWithText("tileF").assertExists()

        // Double tap "tileA" to enable placement mode
        composeRule.onNodeWithContentDescription("tileA").performTouchInput { doubleClick() }

        // Assert the available tiles are not visible
        composeRule.onNodeWithText("tileF").assertDoesNotExist()
    }

    private fun ComposeContentTestRule.assertCurrentTilesGridContainsExactly(specs: List<String>) =
        assertGridContainsExactly(CURRENT_TILES_GRID_TEST_TAG, specs)

    private fun ComposeContentTestRule.assertAvailableTilesGridContainsExactly(
        specs: List<String>
    ) = assertGridContainsExactly(AVAILABLE_TILES_GRID_TEST_TAG, specs)

    companion object {
        private val CURRENT_TILES_GRID_TEST_TAG = resIdToTestTag("CurrentTilesGrid")
        private val AVAILABLE_TILES_GRID_TEST_TAG = resIdToTestTag("AvailableTilesGrid")

        private fun createEditTile(
            tileSpec: String,
            isCurrent: Boolean = true,
            isRemovable: Boolean = true,
        ): EditTileViewModel {
            return EditTileViewModel(
                tileSpec = TileSpec.create(tileSpec),
                icon =
                    Icon.Resource(android.R.drawable.star_on, ContentDescription.Loaded(tileSpec)),
                label = AnnotatedString(tileSpec),
                inlinedLabel = null,
                appName = null,
                isCurrent = isCurrent,
                isDualTarget = false,
                availableEditActions =
                    if (isRemovable) setOf(AvailableEditActions.REMOVE) else emptySet(),
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
                createEditTile("tileF", isCurrent = false),
                createEditTile("tileG_large", isCurrent = false),
            )
        private val TestLargeTilesSpecs =
            TestEditTiles.filter { it.tileSpec.spec.endsWith("large") }.map { it.tileSpec }.toSet()
    }
}
