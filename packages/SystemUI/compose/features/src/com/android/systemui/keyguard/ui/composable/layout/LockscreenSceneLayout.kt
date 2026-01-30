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

package com.android.systemui.keyguard.ui.composable.layout

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
import com.android.compose.modifiers.thenIf
import com.android.systemui.keyguard.ui.viewmodel.LockscreenContentViewModel
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenScope
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenScope.Companion.LockscreenElement
import kotlin.math.max
import kotlin.math.min

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
                // When two bottom alignment line values are provided, choose the bottommost
                // one:
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
 *   as it may be drawn on top of the UDFPS (under display fingerprint sensor)
 */
@Composable
fun LockscreenScope<ContentScope>.LockscreenSceneLayout(
    viewModel: LockscreenContentViewModel,
    modifier: Modifier = Modifier,
) {
    Layout(
        content = {
            LockscreenElement(LockscreenElementKeys.StatusBar)
            LockscreenElement(LockscreenElementKeys.Region.Upper)
            LockscreenElement(LockscreenElementKeys.LockIcon)
            LockscreenElement(LockscreenElementKeys.AmbientIndicationArea)
            LockscreenElement(LockscreenElementKeys.Region.Lower)
            LockscreenElement(LockscreenElementKeys.SettingsMenu)
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

        if (!viewModel.isUdfpsSupported) {
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

        val settingsMenuPleaceable = settingsMenuMeasurable.measure(constraints)

        layout(constraints.maxWidth, constraints.maxHeight) {
            statusBarPlaceable.place(0, 0)
            upperRegionPlaceable.placeRelative(0, statusBarPlaceable.measuredHeight)
            lockIconPlaceable.place(lockIconBounds.left, lockIconBounds.top)

            ambientIndicationPlaceable.place(
                0,
                if (viewModel.isUdfpsSupported) lockIconBounds.bottom
                else lockIconBounds.top - ambientIndicationPlaceable.measuredHeight,
            )

            lowerRegionPlaceable.place(
                0,
                constraints.maxHeight - lowerRegionPlaceable.measuredHeight,
            )

            settingsMenuPleaceable.placeRelative(
                (constraints.maxWidth - settingsMenuPleaceable.measuredWidth) / 2,
                constraints.maxHeight - settingsMenuPleaceable.measuredHeight,
            )
        }
    }
}

private fun BaseContentScope.isIdleWithOverlay(): Boolean {
    return !layoutState.isTransitioning() && layoutState.currentOverlays.isNotEmpty()
}
