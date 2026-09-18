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

package com.android.systemui.qs.ui.composable

import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper
import android.view.Display
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onParent
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.TestContentScope
import com.android.compose.theme.PlatformTheme
import com.android.systemui.Flags.FLAG_DUAL_SHADE
import com.android.systemui.Flags.FLAG_EXPANDED_AUDIO_DETAILED_VIEW
import com.android.systemui.Flags.FLAG_QS_TILE_DETAILED_VIEW
import com.android.systemui.SysuiTestCase
import com.android.systemui.compose.modifiers.resIdToTestTag
import com.android.systemui.display.data.repository.setDisplayType
import com.android.systemui.flags.DesktopSizing
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.flags.Flags.FILTER_PROVISIONING_NETWORK_SUBSCRIPTIONS
import com.android.systemui.flags.fake
import com.android.systemui.flags.featureFlagsClassic
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.qs.panels.data.repository.defaultLargeTilesRepository
import com.android.systemui.qs.panels.domain.interactor.iconTilesInteractor
import com.android.systemui.qs.panels.ui.viewmodel.detailsViewModel
import com.android.systemui.qs.panels.ui.viewmodel.editModeViewModel
import com.android.systemui.qs.pipeline.domain.interactor.currentTilesInteractor
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.res.R
import com.android.systemui.scene.ui.composable.WithSceneContainerPreloadedResources
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.ui.composable.WithStatusIconContext
import com.android.systemui.statusbar.phone.ui.tintedIconManagerFactory
import com.android.systemui.testKosmos
import com.android.systemui.util.FixedActivitySizeComposeTestRule
import com.google.common.truth.Truth.assertThat
import junit.framework.TestCase.assertEquals
import kotlin.test.Test
import org.junit.Rule
import org.junit.runner.RunWith
import platform.test.screenshot.DeviceEmulationSpec
import platform.test.screenshot.Displays

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper
@EnableSceneContainer
class QuickSettingsShadeOverlayTest : SysuiTestCase() {
    @get:Rule
    val rule =
        FixedActivitySizeComposeTestRule(
            DeviceEmulationSpec(
                // Use a large display size intentionally to verify the dimens for large screens.
                // Also, use 160dpi as the display density to avoid the rounding errors triggered by
                // the pixel-DP conversion.
                Displays.Desktop160dpi,
                isLandscape = true,
            )
        )

    val composeTestRule = rule.composeTestRule

    private val kosmos =
        testKosmos().apply {
            useUnconfinedTestDispatcher()
            featureFlagsClassic.fake.apply { setDefault(FILTER_PROVISIONING_NETWORK_SUBSCRIPTIONS) }
            setDisplayType(Display.DEFAULT_DISPLAY, Display.TYPE_INTERNAL)
        }

    private fun ComposeContentTestRule.setQSShadeOverlay() {
        setContent {
            PlatformTheme {
                WithStatusIconContext(kosmos.tintedIconManagerFactory) {
                    WithSceneContainerPreloadedResources {
                        with(kosmos.quickSettingsShadeOverlay) {
                            TestContentScope { Content(Modifier) }
                        }
                    }
                }
            }
        }
    }

    @Test
    // TODO(b/485387343): Re-enable this test on desktop once the
    // SystemUITests_desktop is fixed.
    fun testBrightnessSlider() =
        kosmos.runTest {
            composeTestRule.setQSShadeOverlay()
            composeTestRule.waitForIdle()

            composeTestRule.apply {
                // Verify the brightness slider's height.
                onNodeWithTag(resIdToTestTag("slider"))
                    .assertHeightIsEqualTo(if (DesktopSizing.isEnabled) 48.dp else 52.dp)

                // Verify the brightness slider's vertical padding.
                val brightnessSliderNode = onNodeWithTag(resIdToTestTag("brightness_slider"))
                val sliderBoundsInRoot = brightnessSliderNode.getBoundsInRoot()
                val sliderContainerBoundsInRoot = brightnessSliderNode.onParent().getBoundsInRoot()
                val expectValue = if (DesktopSizing.isEnabled) 0.dp else 6.dp
                assertEquals(expectValue, sliderBoundsInRoot.top - sliderContainerBoundsInRoot.top)
            }
        }

    @Test
    // TODO(b/485387343): Re-enable this test on desktop once the
    // SystemUITests_desktop is fixed.
    fun testSmallTileSize() =
        kosmos.runTest {
            currentTilesInteractor.setTiles(listOf(TileSpec.create("airplane")))

            composeTestRule.setQSShadeOverlay()
            composeTestRule.waitForIdle()

            composeTestRule
                .onNodeWithTag("element:airplane")
                .assertHeightIsEqualTo(if (DesktopSizing.isEnabled) 56.dp else 72.dp)

            composeTestRule
                .onNodeWithTag(resIdToTestTag("qs_tile_icon"), useUnmergedTree = true)
                .assertHeightIsEqualTo(if (DesktopSizing.isEnabled) 24.dp else 32.dp)

            // Verify the QS shade overlay's width.
            composeTestRule
                .onNodeWithTag(resIdToTestTag("quick_settings_panel"))
                .assertWidthIsEqualTo(if (DesktopSizing.isEnabled) 376.dp else 474.dp)
        }

    @Test
    // TODO(b/485387343): Re-enable this test on desktop once the
    // SystemUITests_desktop is fixed.
    fun testLargeTileSize() =
        kosmos.runTest {
            iconTilesInteractor.setLargeTiles(defaultLargeTilesRepository.defaultLargeTiles)
            currentTilesInteractor.setTiles(listOf(TileSpec.create("dnd")))

            composeTestRule.setQSShadeOverlay()
            composeTestRule.waitForIdle()

            composeTestRule
                .onNodeWithTag("element:dnd")
                .assertHeightIsEqualTo(if (DesktopSizing.isEnabled) 56.dp else 72.dp)

            composeTestRule
                .onNodeWithTag(resIdToTestTag("qs_tile_icon"), useUnmergedTree = true)
                .assertHeightIsEqualTo(if (DesktopSizing.isEnabled) 20.dp else 28.dp)
        }

    @Test
    // TODO(b/485387343): Re-enable this test on desktop once the
    // SystemUITests_desktop is fixed.
    fun testToolbar() =
        kosmos.runTest {
            composeTestRule.setQSShadeOverlay()
            composeTestRule.waitForIdle()

            // Verify the toolbar's height.
            composeTestRule
                .onNodeWithTag(resIdToTestTag("quick_settings_toolbar"))
                .assertHeightIsEqualTo(if (DesktopSizing.isEnabled) 36.dp else 48.dp)

            // Verify the toolbar button's height.
            composeTestRule
                .onNodeWithTag(resIdToTestTag("settings_button_container"))
                .assertHeightIsEqualTo(if (DesktopSizing.isEnabled) 36.dp else 40.dp)
        }

    @Test
    // TODO(b/485387343): Re-enable this test on desktop once the
    // SystemUITests_desktop is fixed.
    @EnableFlags(FLAG_QS_TILE_DETAILED_VIEW, FLAG_EXPANDED_AUDIO_DETAILED_VIEW)
    fun testVolumeSlider() =
        kosmos.runTest {
            composeTestRule.setQSShadeOverlay()
            composeTestRule.waitForIdle()

            // Verify the slider's height. "Media" is the tag of the volume slider.
            composeTestRule
                .onNodeWithTag(resIdToTestTag("Media"))
                .assertHeightIsEqualTo(if (DesktopSizing.isEnabled) 48.dp else 52.dp)
        }

    @Test
    fun testAccessibilityPaneTitle() =
        kosmos.runTest {
            composeTestRule.setQSShadeOverlay()
            composeTestRule.waitForIdle()

            val expectedTitle = mContext.getString(R.string.accessibility_desc_quick_settings)
            composeTestRule
                .onNodeWithTag(resIdToTestTag("quick_settings_container"))
                .assert(hasPaneTitle(expectedTitle))
        }

    @Test
    fun testAccessibilityPaneTitle_editing() =
        kosmos.runTest {
            kosmos.editModeViewModel.startEditing()
            composeTestRule.setQSShadeOverlay()
            composeTestRule.waitForIdle()

            val expectedTitle = mContext.getString(R.string.accessibility_desc_quick_settings_edit)
            composeTestRule
                .onNodeWithTag(resIdToTestTag("quick_settings_container"))
                .assert(hasPaneTitle(expectedTitle))
        }

    @Test
    @EnableFlags(FLAG_QS_TILE_DETAILED_VIEW, FLAG_DUAL_SHADE)
    fun testAccessibilityPaneTitle_details() =
        kosmos.runTest {
            enableDualShade()
            val fakeTitle = "Fake title"
            val fakeSpec = TileSpec.create(fakeTitle)
            currentTilesInteractor.setTiles(listOf(fakeSpec))
            assertThat(currentTilesInteractor.currentTilesSpecs).hasSize(1)

            composeTestRule.setQSShadeOverlay()
            composeTestRule.waitForIdle()

            kosmos.detailsViewModel.onTileClicked(fakeSpec)
            assertThat(kosmos.detailsViewModel.activeTileDetails).isNotNull()
            composeTestRule.waitForIdle()

            composeTestRule
                .onNodeWithTag(resIdToTestTag("quick_settings_container"))
                .assert(hasPaneTitle(fakeTitle))
        }

    private fun hasPaneTitle(title: String): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title)
}
