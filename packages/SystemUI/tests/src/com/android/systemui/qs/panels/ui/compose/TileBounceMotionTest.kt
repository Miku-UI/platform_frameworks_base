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

import android.widget.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.SceneTransitionLayout
import com.android.compose.animation.scene.TestScenes.SceneA
import com.android.compose.animation.scene.rememberMutableSceneTransitionLayoutState
import com.android.compose.theme.PlatformTheme
import com.android.systemui.SysuiTestCase
import com.android.systemui.grid.ui.compose.VerticalSpannedGrid
import com.android.systemui.haptics.msdl.tileHapticsViewModelFactoryProvider
import com.android.systemui.motion.createSysUiComposeMotionTestRule
import com.android.systemui.plugins.qs.QSTile
import com.android.systemui.qs.FakeQSTile
import com.android.systemui.qs.panels.shared.model.SizedTileImpl
import com.android.systemui.qs.panels.ui.compose.infinitegrid.Tile
import com.android.systemui.qs.panels.ui.compose.infinitegrid.TileBounceMotionTestKeys
import com.android.systemui.qs.panels.ui.viewmodel.BounceableTileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.shared.ui.QuickSettings.Elements.toElementKey
import com.android.systemui.res.R
import com.android.systemui.testKosmos
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.motion.MotionTestRule
import platform.test.motion.compose.ComposeFeatureCaptures
import platform.test.motion.compose.ComposeRecordingSpec
import platform.test.motion.compose.ComposeToolkit
import platform.test.motion.compose.MotionControl
import platform.test.motion.compose.feature
import platform.test.motion.compose.recordMotion
import platform.test.motion.compose.runTest
import platform.test.motion.golden.FeatureCapture
import platform.test.motion.golden.asDataPoint
import platform.test.screenshot.DeviceEmulationSpec
import platform.test.screenshot.Displays.Phone

@SmallTest
@RunWith(AndroidJUnit4::class)
class TileBounceMotionTest : SysuiTestCase() {
    private val deviceSpec = DeviceEmulationSpec(Phone)
    private val kosmos = testKosmos()
    private val tileHapticsViewModelFactoryProvider = kosmos.tileHapticsViewModelFactoryProvider
    @get:Rule val motionTestRule = createSysUiComposeMotionTestRule(kosmos, deviceSpec)

    @Composable
    private fun TestTileGrid(
        tiles: List<TestTile>,
        tappedIndex: Int,
        bounceType: BounceType,
        play: Boolean,
        done: () -> Unit,
    ) {
        val bounceables = remember { List(tiles.size) { BounceableTileViewModel() } }
        val spans = remember { tiles.map { it.span } }
        PlatformTheme {
            SceneTransitionLayout(rememberMutableSceneTransitionLayoutState(SceneA)) {
                scene(SceneA) {
                    VerticalSpannedGrid(
                        columns = 4,
                        columnSpacing = 8.dp,
                        rowSpacing = 8.dp,
                        spans = spans,
                    ) { index, _, _, _ ->
                        Tile(
                            tile = tiles[index].viewModel,
                            iconOnly = tiles[index].iconOnly,
                            squishiness = { 1f },
                            coroutineScope = rememberCoroutineScope(),
                            bounceableInfo =
                                BounceableInfo(
                                    bounceables[index],
                                    previousTile = bounceables.getOrNull(index - 1),
                                    nextTile = bounceables.getOrNull(index + 1),
                                    bounceEnd = index != tiles.size - 1,
                                ),
                            tileHapticsViewModelFactoryProvider =
                                tileHapticsViewModelFactoryProvider,
                            detailsViewModel = null,
                            interactionSource = null,
                        )
                    }
                }
            }
        }

        LaunchedEffect(play) {
            if (play) {
                when (bounceType) {
                    BounceType.Container -> bounceables[tappedIndex].animateContainerBounce()
                    BounceType.Content ->
                        bounceables[tappedIndex].animateContentBounce(tiles[tappedIndex].iconOnly)
                }
                done()
            }
        }
    }

    @Composable
    private fun TestMaterialExpressiveTileGrid(tiles: List<TestTile>) {
        val sizedTiles = tiles.map { SizedTileImpl(it.viewModel, it.span) }
        PlatformTheme {
            SceneTransitionLayout(rememberMutableSceneTransitionLayoutState(SceneA)) {
                scene(SceneA) {
                    ButtonGroupGrid(
                        sizedTiles,
                        keys = { it.spec },
                        elementKey = { it.spec.toElementKey() },
                        horizontalPadding = dimensionResource(R.dimen.qs_tile_margin_horizontal),
                        columns = 4,
                    ) { tile, interactionSource ->
                        Tile(
                            tile = tile.tile,
                            iconOnly = tile.isIcon,
                            squishiness = { 1f },
                            coroutineScope = rememberCoroutineScope(),
                            bounceableInfo = null,
                            tileHapticsViewModelFactoryProvider =
                                tileHapticsViewModelFactoryProvider,
                            detailsViewModel = null,
                            interactionSource = interactionSource,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun containerBounce_iconTile() {
        val tiles =
            listOf(TestTile("small_previous"), TestTile("small_clicked"), TestTile("small_next"))
        motionTestRule.runTest { containerBounceTest(tiles) }
    }

    @Test
    fun containerBounce_withMaterialExpressive_iconTile() {
        val tiles =
            listOf(
                TestTile("small_previous"),
                TestTile("small_clicked", toggleable = true),
                TestTile("small_next"),
            )
        motionTestRule.runTest {
            materialExpressiveContainerBounceTest(tiles) {
                onNode(hasContentDescription("_clicked", substring = true))
            }
        }
    }

    @Test
    fun containerBounce_largeTile() {
        val tiles =
            listOf(
                TestTile("small_previous"),
                TestTile("large_clicked", iconOnly = false),
                TestTile("small_next"),
            )
        motionTestRule.runTest { containerBounceTest(tiles) }
    }

    @Test
    fun containerBounce_withMaterialExpressive_largeTile() {
        val tiles =
            listOf(
                TestTile("small_previous"),
                TestTile("large_clicked", iconOnly = false, toggleable = true),
                TestTile("small_next"),
            )
        motionTestRule.runTest {
            materialExpressiveContainerBounceTest(tiles) {
                onNode(hasText("_clicked", substring = true))
            }
        }
    }

    @Test
    fun containerBounce_bounceEndDisabled() {
        // Click on the last tile of the row to verify it bounces on one side only
        val tiles = listOf(TestTile("small_previous"), TestTile("small_clicked"))
        motionTestRule.runTest { containerBounceTest(tiles) }
    }

    @Test
    fun containerBounce_withMaterialExpressive_bounceEndDisabled() {
        // Click on the last tile of the row to verify it bounces on one side only
        val tiles = listOf(TestTile("small_previous"), TestTile("small_clicked", toggleable = true))
        motionTestRule.runTest {
            materialExpressiveContainerBounceTest(tiles) {
                onNode(hasContentDescription("_clicked", substring = true))
            }
        }
    }

    @Test
    fun iconBounce() {
        motionTestRule.runTest { contentBounceTest(TestTile("small")) }
    }

    @Test
    fun iconBounce_withMaterialExpressive() {
        motionTestRule.runTest {
            materialExpressiveContentBounceTest(TestTile("small_clicked")) {
                onNode(hasContentDescription("_clicked", substring = true))
            }
        }
    }

    @Test
    fun textBounce() {
        motionTestRule.runTest { contentBounceTest(TestTile("large", iconOnly = false)) }
    }

    @Test
    fun textBounce_withMaterialExpressive() {
        motionTestRule.runTest {
            materialExpressiveContentBounceTest(TestTile("large_clicked", iconOnly = false)) {
                onNode(hasText("_clicked", substring = true))
            }
        }
    }

    private fun MotionTestRule<ComposeToolkit>.containerBounceTest(tiles: List<TestTile>) {
        var done = false
        val motion =
            recordMotion(
                { play ->
                    TestTileGrid(
                        tiles,
                        tappedIndex = 1,
                        bounceType = BounceType.Container,
                        play = play,
                    ) {
                        done = true
                    }
                },
                ComposeRecordingSpec(MotionControl { awaitCondition { done } }) {
                    for (tile in tiles) {
                        val matcher =
                            if (tile.iconOnly) {
                                hasContentDescription(tile.spec)
                            } else {
                                hasText(tile.spec)
                            }
                        feature(matcher, ComposeFeatureCaptures.dpSize, name = "tile-${tile.spec}")
                    }
                },
            )
        assertThat(motion).timeSeriesMatchesGolden()
    }

    private fun MotionTestRule<ComposeToolkit>.materialExpressiveContainerBounceTest(
        tiles: List<TestTile>,
        onNode: SemanticsNodeInteractionsProvider.() -> SemanticsNodeInteraction,
    ) {
        val motion =
            recordMotion(
                content = { TestMaterialExpressiveTileGrid(tiles) },
                ComposeRecordingSpec(
                    MotionControl {
                        performTouchInputAsync(onNode()) { click() }
                        awaitDelay(500.milliseconds)
                    }
                ) {
                    for (tile in tiles) {
                        val matcher =
                            if (tile.iconOnly) {
                                hasContentDescription(tile.spec)
                            } else {
                                hasText(tile.spec)
                            }
                        feature(matcher, ComposeFeatureCaptures.dpSize, name = "tile-${tile.spec}")
                    }
                },
            )
        assertThat(motion).timeSeriesMatchesGolden()
    }

    private fun MotionTestRule<ComposeToolkit>.contentBounceTest(tile: TestTile) {
        var done = false
        val motion =
            recordMotion(
                { play ->
                    TestTileGrid(
                        listOf(tile),
                        tappedIndex = 0,
                        bounceType = BounceType.Content,
                        play = play,
                    ) {
                        done = true
                    }
                },
                ComposeRecordingSpec(MotionControl { awaitCondition { done } }) {
                    feature(
                        motionTestValueKey = TileBounceMotionTestKeys.BounceScale,
                        capture =
                            FeatureCapture(
                                TileBounceMotionTestKeys.BounceScale.semanticsPropertyKey.name
                            ) {
                                it.asDataPoint()
                            },
                    )
                },
            )
        assertThat(motion).timeSeriesMatchesGolden()
    }

    private fun MotionTestRule<ComposeToolkit>.materialExpressiveContentBounceTest(
        tile: TestTile,
        onNode: SemanticsNodeInteractionsProvider.() -> SemanticsNodeInteraction,
    ) {
        val motion =
            recordMotion(
                content = { TestMaterialExpressiveTileGrid(listOf(tile)) },
                ComposeRecordingSpec(
                    MotionControl {
                        performTouchInputAsync(onNode()) { click() }
                        awaitDelay(500.milliseconds)
                    }
                ) {
                    feature(
                        motionTestValueKey = TileBounceMotionTestKeys.BounceScale,
                        capture =
                            FeatureCapture(
                                TileBounceMotionTestKeys.BounceScale.semanticsPropertyKey.name
                            ) {
                                it.asDataPoint()
                            },
                    )
                },
            )
        assertThat(motion).timeSeriesMatchesGolden()
    }

    private enum class BounceType {
        Container,
        Content,
    }

    private class TestTile(
        val spec: String,
        val iconOnly: Boolean = true,
        toggleable: Boolean = false,
    ) {
        val viewModel: TileViewModel = createTile(spec, toggleable)
        val span: Int
            get() = if (iconOnly) 1 else 2
    }

    private companion object {
        fun createTile(spec: String, toggleable: Boolean): TileViewModel {
            return FakeQSTile(user = 0, available = true)
                .apply {
                    changeState(
                        QSTile.State().apply {
                            label = spec
                            contentDescription = spec
                            if (toggleable) {
                                expandedAccessibilityClassName = Switch::class.java.name
                            }
                        }
                    )
                }
                .let { TileViewModel(it, TileSpec.create(spec)) }
        }
    }
}
