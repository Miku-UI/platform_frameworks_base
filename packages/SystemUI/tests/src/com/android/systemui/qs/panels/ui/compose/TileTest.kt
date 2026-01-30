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

import android.service.quicksettings.Tile.STATE_UNAVAILABLE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.SceneTransitionLayout
import com.android.compose.animation.scene.TestScenes.SceneA
import com.android.compose.animation.scene.rememberMutableSceneTransitionLayoutState
import com.android.compose.theme.PlatformTheme
import com.android.systemui.SysuiTestCase
import com.android.systemui.haptics.msdl.tileHapticsViewModelFactoryProvider
import com.android.systemui.plugins.qs.QSTile
import com.android.systemui.qs.FakeQSTile
import com.android.systemui.qs.panels.ui.compose.infinitegrid.Tile
import com.android.systemui.qs.panels.ui.viewmodel.BounceableTileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class TileTest : SysuiTestCase() {
    @get:Rule val composeRule = createComposeRule()
    private val kosmos = testKosmos()
    private val tileHapticsViewModelFactoryProvider = kosmos.tileHapticsViewModelFactoryProvider

    @Composable
    private fun TestTile(tile: TileViewModel, iconOnly: Boolean) {
        PlatformTheme {
            SceneTransitionLayout(rememberMutableSceneTransitionLayoutState(SceneA)) {
                scene(SceneA) {
                    Tile(
                        tile = tile,
                        iconOnly = iconOnly,
                        squishiness = { 1f },
                        coroutineScope = rememberCoroutineScope(),
                        bounceableInfo =
                            BounceableInfo(
                                BounceableTileViewModel(),
                                previousTile = null,
                                nextTile = null,
                                bounceEnd = true,
                            ),
                        tileHapticsViewModelFactoryProvider = tileHapticsViewModelFactoryProvider,
                        detailsViewModel = null,
                        interactionSource = null,
                    )
                }
            }
        }
    }

    @Test
    fun click_largeTile_shouldReceiveClick() {
        val tile = InteractableFakeQSTile()
        tile.fakeTile.changeState(QSTile.State().apply { label = "largeTile" })
        val viewModel = TileViewModel(tile.fakeTile, TileSpec.Companion.create("test"))

        composeRule.setContent { TestTile(viewModel, iconOnly = false) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("largeTile").performClick()

        assertThat(tile.interactions).containsExactly(InteractableFakeQSTile.Interaction.CLICK)
    }

    @Test
    fun click_largeDualTargetTile_shouldReceiveClick() {
        val tile = InteractableFakeQSTile()
        tile.fakeTile.changeState(
            QSTile.State().apply {
                label = "largeDualTargetTile"
                handlesSecondaryClick = true
            }
        )
        val viewModel = TileViewModel(tile.fakeTile, TileSpec.Companion.create("test"))

        composeRule.setContent { TestTile(viewModel, iconOnly = false) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("largeDualTargetTile").performClick()

        assertThat(tile.interactions).containsExactly(InteractableFakeQSTile.Interaction.CLICK)
    }

    @Test
    fun click_smallTile_shouldReceiveClick() {
        val tile = InteractableFakeQSTile()
        tile.fakeTile.changeState(QSTile.State().apply { contentDescription = "smallTile" })
        val viewModel = TileViewModel(tile.fakeTile, TileSpec.Companion.create("test"))

        composeRule.setContent { TestTile(viewModel, iconOnly = true) }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("smallTile").performClick()

        assertThat(tile.interactions).containsExactly(InteractableFakeQSTile.Interaction.CLICK)
    }

    @Test
    fun click_smallDualTargetTile_shouldReceiveSecondaryClick() {
        val tile = InteractableFakeQSTile()
        tile.fakeTile.changeState(
            QSTile.State().apply {
                contentDescription = "smallDualTargetTile"
                handlesSecondaryClick = true
            }
        )
        val viewModel = TileViewModel(tile.fakeTile, TileSpec.Companion.create("test"))

        composeRule.setContent { TestTile(viewModel, iconOnly = true) }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("smallDualTargetTile").performClick()

        assertThat(tile.interactions)
            .containsExactly(InteractableFakeQSTile.Interaction.SECONDARY_CLICK)
    }

    @Test
    fun longClick_largeTile_shouldReceiveLongClick() {
        val tile = InteractableFakeQSTile()
        tile.fakeTile.changeState(QSTile.State().apply { label = "largeTile" })
        val viewModel = TileViewModel(tile.fakeTile, TileSpec.Companion.create("test"))

        composeRule.setContent { TestTile(viewModel, iconOnly = false) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("largeTile").performTouchInput { longClick() }

        assertThat(tile.interactions).containsExactly(InteractableFakeQSTile.Interaction.LONG_CLICK)
    }

    @Test
    fun longClick_largeDualTargetTile_shouldReceiveLongClick() {
        val tile = InteractableFakeQSTile()
        tile.fakeTile.changeState(
            QSTile.State().apply {
                label = "largeDualTargetTile"
                handlesSecondaryClick = true
            }
        )
        val viewModel = TileViewModel(tile.fakeTile, TileSpec.Companion.create("test"))

        composeRule.setContent { TestTile(viewModel, iconOnly = false) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("largeDualTargetTile").performTouchInput { longClick() }

        assertThat(tile.interactions).containsExactly(InteractableFakeQSTile.Interaction.LONG_CLICK)
    }

    @Test
    fun longClick_smallTile_shouldReceiveLongClick() {
        val tile = InteractableFakeQSTile()
        tile.fakeTile.changeState(QSTile.State().apply { contentDescription = "smallTile" })
        val viewModel = TileViewModel(tile.fakeTile, TileSpec.Companion.create("test"))

        composeRule.setContent { TestTile(viewModel, iconOnly = true) }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("smallTile").performTouchInput { longClick() }

        assertThat(tile.interactions).containsExactly(InteractableFakeQSTile.Interaction.LONG_CLICK)
    }

    @Test
    fun longClick_smallDualTargetTile_shouldReceiveClick() {
        val tile = InteractableFakeQSTile()
        tile.fakeTile.changeState(
            QSTile.State().apply {
                contentDescription = "smallDualTargetTile"
                handlesSecondaryClick = true
            }
        )
        val viewModel = TileViewModel(tile.fakeTile, TileSpec.Companion.create("test"))

        composeRule.setContent { TestTile(viewModel, iconOnly = true) }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("smallDualTargetTile").performTouchInput {
            longClick()
        }

        assertThat(tile.interactions).containsExactly(InteractableFakeQSTile.Interaction.CLICK)
    }

    @Test
    fun longClick_smallDualTargetTile_doesNotHandleLongClick_shouldReceiveClick() {
        val tile = InteractableFakeQSTile()
        tile.fakeTile.changeState(
            QSTile.State().apply {
                contentDescription = "smallDualTargetTile"
                handlesSecondaryClick = true
                handlesLongClick = false
            }
        )
        val viewModel = TileViewModel(tile.fakeTile, TileSpec.Companion.create("test"))

        composeRule.setContent { TestTile(viewModel, iconOnly = true) }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("smallDualTargetTile").performTouchInput {
            longClick()
        }

        assertThat(tile.interactions).containsExactly(InteractableFakeQSTile.Interaction.CLICK)
    }

    @Test
    fun longClick_largeDualTargetTile_doesNotHandleLongClick_shouldNotReceiveLongClick() {
        val tile = InteractableFakeQSTile()
        tile.fakeTile.changeState(
            QSTile.State().apply {
                label = "largeDualTargetTile"
                handlesSecondaryClick = true
                handlesLongClick = false
            }
        )
        val viewModel = TileViewModel(tile.fakeTile, TileSpec.Companion.create("test"))

        composeRule.setContent { TestTile(viewModel, iconOnly = false) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("largeDualTargetTile").performTouchInput { longClick() }

        // Long clicks default to a normal click when a tile does not handle long clicks
        assertThat(tile.interactions).containsExactly(InteractableFakeQSTile.Interaction.CLICK)
    }

    @Test
    fun longClick_smallTile_doesNotHandleLongClick_shouldNotReceiveLongClick() {
        val tile = InteractableFakeQSTile()
        tile.fakeTile.changeState(
            QSTile.State().apply {
                contentDescription = "smallTile"
                handlesLongClick = false
            }
        )
        val viewModel = TileViewModel(tile.fakeTile, TileSpec.Companion.create("test"))

        composeRule.setContent { TestTile(viewModel, iconOnly = true) }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("smallTile").performTouchInput { longClick() }

        // Long clicks default to a normal click when a tile does not handle long clicks
        assertThat(tile.interactions).containsExactly(InteractableFakeQSTile.Interaction.CLICK)
    }

    @Test
    fun longClick_largeTile_doesNotHandleLongClick_shouldReceiveLongClick() {
        val tile = InteractableFakeQSTile()
        tile.fakeTile.changeState(
            QSTile.State().apply {
                label = "largeTile"
                handlesLongClick = false
            }
        )
        val viewModel = TileViewModel(tile.fakeTile, TileSpec.Companion.create("test"))

        composeRule.setContent { TestTile(viewModel, iconOnly = false) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("largeTile").performTouchInput { longClick() }

        // Long clicks default to a normal click when a tile does not handle long clicks
        assertThat(tile.interactions).containsExactly(InteractableFakeQSTile.Interaction.CLICK)
    }

    @Test
    fun longClick_smallDualTargetTile_isUnavailable_shouldReceiveLongClick() {
        val tile = InteractableFakeQSTile()
        tile.fakeTile.changeState(
            QSTile.State().apply {
                contentDescription = "smallDualTargetTile"
                handlesSecondaryClick = true
                state = STATE_UNAVAILABLE
            }
        )
        val viewModel = TileViewModel(tile.fakeTile, TileSpec.Companion.create("test"))

        composeRule.setContent { TestTile(viewModel, iconOnly = true) }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("smallDualTargetTile").performTouchInput {
            longClick()
        }

        assertThat(tile.interactions).containsExactly(InteractableFakeQSTile.Interaction.LONG_CLICK)
    }

    private class InteractableFakeQSTile {
        val interactions = mutableListOf<Interaction>()

        val fakeTile =
            FakeQSTile(
                user = 0,
                available = true,
                onClick = { interactions.add(Interaction.CLICK) },
                onLongClick = { interactions.add(Interaction.LONG_CLICK) },
                onSecondaryClick = { interactions.add(Interaction.SECONDARY_CLICK) },
            )

        enum class Interaction {
            CLICK,
            SECONDARY_CLICK,
            LONG_CLICK,
        }
    }
}
