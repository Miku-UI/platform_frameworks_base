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

package com.android.systemui.keyguard.ui.composable.elements

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.VerticalAlignmentLine
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntRect
import com.android.compose.animation.scene.BaseContentScope
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementContentScope
import com.android.compose.modifiers.thenIf
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.ui.composable.LockscreenTouchHandling
import com.android.systemui.keyguard.ui.composable.modifier.burnInAware
import com.android.systemui.keyguard.ui.composable.trackBurnInParameters
import com.android.systemui.keyguard.ui.viewmodel.AodBurnInViewModel
import com.android.systemui.keyguard.ui.viewmodel.KeyguardClockViewModel
import com.android.systemui.keyguard.ui.viewmodel.LockscreenRootViewModel
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.plugins.keyguard.ui.composable.elements.BaseLockscreenElement.ElementSource
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElement
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys.AmbientIndicationArea
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys.Clock
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys.LockIcon
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys.MediaCarousel
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys.Region
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys.SettingsMenu
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys.Shortcuts
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys.Smartspace
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys.StatusBar
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementProvider
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenScope
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenScope.Companion.LockscreenElement
import com.android.systemui.shade.ShadeDisplayAware
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

@SysUISingleton
/** Provides an element representing all lockscreen ui */
class LockscreenRootElementProvider
@Inject
constructor(
    @ShadeDisplayAware private val context: Context,
    private val viewModelFactory: LockscreenRootViewModel.Factory,
    private val aodBurnInViewModel: AodBurnInViewModel,
    private val keyguardClockViewModel: KeyguardClockViewModel,
) : LockscreenElementProvider {
    override val elements: List<LockscreenElement> by lazy { listOf(RootElement()) }

    private inner class RootElement : LockscreenElement {
        override val key = LockscreenElementKeys.Root
        override val context = this@LockscreenRootElementProvider.context
        override val source = ElementSource.STANDARD

        @Composable
        override fun LockscreenScope<ElementContentScope>.LockscreenElement() {
            val viewModel =
                rememberViewModel("LockscreenRoot-viewModel") { viewModelFactory.create() }
            val burnInTracker =
                trackBurnInParameters(
                    aodBurnInViewModel,
                    { viewModel.burnIn },
                    keyguardClockViewModel,
                )
            LockscreenTouchHandling(viewModel.touchHandlingFactory) { onSettingsMenuPlaced ->
                val innerContext =
                    context.copy(
                        burnInAware = Modifier.burnInAware(viewModel.burnIn, isClock = false),
                        burnInAwareClock = Modifier.burnInAware(viewModel.burnIn, isClock = true),
                        onElementPositioned = { key, rect ->
                            when (key) {
                                Clock.Small -> {
                                    burnInTracker.onSmallClockTopChanged(rect.top)
                                    viewModel.setSmallClockBottom(rect.bottom)
                                }
                                Smartspace.Cards -> {
                                    burnInTracker.onSmartspaceTopChanged(rect.top)
                                    viewModel.setSmartspaceCardBottom(rect.bottom)
                                }
                                MediaCarousel -> viewModel.setMediaPlayerBottom(rect.bottom)
                                Shortcuts.Start -> viewModel.setShortcutTop(rect.top)
                                Shortcuts.End -> viewModel.setShortcutTop(rect.top)
                                SettingsMenu -> onSettingsMenuPlaced(rect)
                                else -> {}
                            }
                        },
                    )

                with(scopeFactory.create(contentScope, innerContext)) {
                    LockscreenSceneLayout(viewModel.isUdfpsSupported)
                }
            }
        }
    }
}

@Immutable
interface UnfoldTranslations {
    /**
     * Amount of horizontal translation to apply to elements that are aligned to the start side
     * (left in left-to-right layouts). Can also be used as horizontal padding for elements that
     * need horizontal padding on both side. In pixels.
     */
    val start: Float

    /**
     * Amount of horizontal translation to apply to elements that are aligned to the end side (right
     * in left-to-right layouts). In pixels.
     */
    val end: Float
}

/**
 * Encapsulates alignment lines produced by the lock icon element.
 *
 * Because the lock icon is also the same element as the under-display fingerprint sensor (UDFPS),
 * [LockscreenSceneLayout] uses the lock icon provided alignment lines to make sure that other
 * elements on screen do not overlap with the lock icon.
 */
object LockIconAlignmentLines {
    /** The left edge of the lock icon. */
    val Left =
        VerticalAlignmentLine(
            merger = { old, new ->
                // When two left alignment line values are provided, choose the leftmost one:
                min(old, new)
            }
        )

    /** The top edge of the lock icon. */
    val Top =
        HorizontalAlignmentLine(
            merger = { old, new ->
                // When two top alignment line values are provided, choose the topmost one:
                min(old, new)
            }
        )

    /** The right edge of the lock icon. */
    val Right =
        VerticalAlignmentLine(
            merger = { old, new ->
                // When two right alignment line values are provided, choose the rightmost one:
                max(old, new)
            }
        )

    /** The bottom edge of the lock icon. */
    val Bottom =
        HorizontalAlignmentLine(
            merger = { old, new ->
                // When two bottom alignment line values are provided, choose the bottommost one:
                max(old, new)
            }
        )
}

/**
 * Arranges the layout for the lockscreen scene.
 *
 * Takes care of figuring out the correct layout configuration based on the device form factor,
 * orientation, and the current UI state.
 *
 * Notes about some non-obvious behaviors:
 * - [LockIcon] is drawn according to the [LockIconAlignmentLines] that it must supply. The layout
 *   logic uses those alignment lines to make sure other elements don't overlap with the lock icon
 *   as it may be drawn on top of the UDFPS (under display fingerprint sensor).
 */
@Composable
private fun LockscreenScope<ContentScope>.LockscreenSceneLayout(
    isUdfpsSupported: Boolean,
    modifier: Modifier = Modifier,
) {
    Layout(
        content = {
            LockscreenElement(StatusBar)
            LockscreenElement(Region.Upper)
            LockscreenElement(LockIcon)
            LockscreenElement(AmbientIndicationArea)
            LockscreenElement(Region.Lower)
            LockscreenElement(SettingsMenu)
        },
        // Hide the lock screen elements when an overlay is shown above.
        modifier =
            modifier.thenIf(contentScope.isIdleWithOverlay()) {
                Modifier.graphicsLayer { alpha = 0f }
            },
    ) { measurables, constraints ->
        check(measurables.size == 6)
        val statusBarMeasurable = measurables[0]
        val upperRegionMeasurable = measurables[1]
        val lockIconMeasurable = measurables[2]
        val ambientIndicationMeasurable = measurables[3]
        val lowerRegionMeasurable = measurables[4]
        val settingsMenuMeasurable = measurables[5]

        val statusBarPlaceable =
            statusBarMeasurable.measure(constraints = Constraints.fixedWidth(constraints.maxWidth))

        val lockIconPlaceable =
            lockIconMeasurable.measure(constraints.copy(minWidth = 0, minHeight = 0))

        // Height available between the bottom of the status bar and either the top of the UDFPS
        // icon (if one is showing) or the bottom of the screen, if no UDFPS icon is showing.
        val lockIconBounds =
            IntRect(
                left = lockIconPlaceable[LockIconAlignmentLines.Left],
                top = lockIconPlaceable[LockIconAlignmentLines.Top],
                right = lockIconPlaceable[LockIconAlignmentLines.Right],
                bottom = lockIconPlaceable[LockIconAlignmentLines.Bottom],
            )

        val ambientIndicationPlaceable =
            ambientIndicationMeasurable.measure(
                constraints = Constraints.fixedWidth(constraints.maxWidth)
            )

        var upperRegionMaxHeight = lockIconBounds.top - statusBarPlaceable.measuredHeight
        var lowerRegionMaxHeight = constraints.maxHeight - lockIconBounds.bottom

        if (!isUdfpsSupported) {
            upperRegionMaxHeight -= ambientIndicationPlaceable.measuredHeight
        } else {
            lowerRegionMaxHeight -= ambientIndicationPlaceable.measuredHeight
        }

        val upperRegionPlaceable =
            upperRegionMeasurable.measure(
                Constraints(
                    minWidth = 0,
                    maxWidth = constraints.maxWidth.coerceAtLeast(0),
                    minHeight = 0,
                    maxHeight = upperRegionMaxHeight.coerceAtLeast(0),
                )
            )

        val lowerRegionPlaceable =
            lowerRegionMeasurable.measure(
                Constraints(
                    minWidth = 0,
                    maxWidth = constraints.maxWidth.coerceAtLeast(0),
                    minHeight = 0,
                    maxHeight = lowerRegionMaxHeight.coerceAtLeast(0),
                )
            )

        val settingsMenuPlaceable = settingsMenuMeasurable.measure(constraints)

        layout(constraints.maxWidth, constraints.maxHeight) {
            statusBarPlaceable.place(0, 0)
            upperRegionPlaceable.placeRelative(0, statusBarPlaceable.measuredHeight)
            lockIconPlaceable.place(lockIconBounds.left, lockIconBounds.top)

            if (isUdfpsSupported) {
                // Place below UDFPS icon.
                ambientIndicationPlaceable.placeRelative(
                    0,
                    lockIconBounds.top + lockIconPlaceable.measuredHeight,
                )
            } else {
                // Place above lock icon.
                ambientIndicationPlaceable.placeRelative(
                    0,
                    lockIconBounds.top - ambientIndicationPlaceable.measuredHeight,
                )
            }

            lowerRegionPlaceable.place(
                0,
                constraints.maxHeight - lowerRegionPlaceable.measuredHeight,
            )

            settingsMenuPlaceable.placeRelative(
                (constraints.maxWidth - settingsMenuPlaceable.measuredWidth) / 2,
                constraints.maxHeight - settingsMenuPlaceable.measuredHeight,
            )
        }
    }
}

private fun BaseContentScope.isIdleWithOverlay(): Boolean {
    return !layoutState.isTransitioning() && layoutState.currentOverlays.isNotEmpty()
}
