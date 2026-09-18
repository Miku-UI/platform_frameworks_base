/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.bouncer.ui.composable

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowSizeClass
import com.android.compose.layout.ContainerLayout
import com.android.compose.windowsizeclass.AdditionalHeightBreakpoints
import com.android.compose.windowsizeclass.LocalWindowSizeClass
import com.android.systemui.Flags

/**
 * Returns the [BouncerOverlayLayout] that should be used by the bouncer scene. If
 * [isOneHandedModeSupported] is `false`, then [BouncerOverlayLayout.BESIDE_USER_SWITCHER] is
 * replaced by [BouncerOverlayLayout.STANDARD_BOUNCER].
 */
@Composable
fun calculateLayout(isOneHandedModeSupported: Boolean): BouncerOverlayLayout {
    return calculateLayoutInternal(LocalWindowSizeClass.current, isOneHandedModeSupported)
}

/**
 * Returns the [ContainerLayout] that should be used by Bouncer's `containerize` modifier or null if
 * the modifier should not be applied.
 */
@Composable
fun calculateContainerLayout(): ContainerLayout? {
    if (!Flags.containerizeBouncerOnLargeScreens() && !Flags.containerizeBouncerOnLargeScreens2()) {
        return null
    }
    return calculateContainerLayoutInternal(LocalWindowSizeClass.current)
}

/** Enumerates all known adaptive layout configurations. */
enum class BouncerOverlayLayout {
    /** The default UI with the bouncer laid out normally. */
    STANDARD_BOUNCER,
    /** The bouncer is displayed vertically stacked with the user switcher. */
    BELOW_USER_SWITCHER,
    /** The bouncer is displayed side-by-side with the user switcher or an empty space. */
    BESIDE_USER_SWITCHER,
    /** The bouncer is split in two with both sides shown side-by-side. */
    SPLIT_BOUNCER,
}

/**
 * Internal version of `calculateLayout` in the System UI Compose library, extracted here to allow
 * for testing that's not dependent on Compose.
 *
 * Layout calculation is visualized by the table below. The matching order is indicated by the
 * numbers in parentheses. `isAtLeastBreakpoint` matches all equal or larger size classes (those
 * below and to the right of the breakpoint in the table). Consequently, size classes must be
 * matched starting from the largest and moving toward the smallest.
 *
 * If a container layout is applied, it's marked as HORIZONTAL or VERTICAL.
 *
 * ```
 * +-------------+----------+----------+------------+------------+--------------+
 * |             |                        Width Size Class                       |
 * +-------------+----------+----------+------------+------------+--------------+
 * |   Height    | Compact  | Medium   | Expanded   | Large      | Extra-large  |
 * | Size Class  |          | (600 dp) | (840 dp)   | (1200 dp)  | (1600 dp)    |
 * +------------(6)---------+----------+------------+------------+--------------+
 * | Compact     | SPLIT    | SPLIT    | SPLIT      | SPLIT      | SPLIT        |
 * +------------(5)---------+---------(3)-----------+------------+--------------+
 * | Medium      | STANDARD | STANDARD | BESIDE     | BESIDE     | BESIDE       |
 * | (480 dp)    |          |          |            |            |              |
 * +-------------+---------(4)---------+------------+------------+--------------+
 * | Expanded    | STANDARD | BELOW    | BESIDE     | BESIDE     | BESIDE       |
 * | (900 dp)    |          |          |            |            | (HORIZONTAL) |
 * +-------------+----------+----------+------------+------------+--------------+
 * | Large       | STANDARD | BELOW    | BESIDE     | BESIDE     | BESIDE       |
 * | (1200 dp)   |          |          |            |            | (HORIZONTAL) |
 * +-------------+----------+---------(2)-----------+-----------(1)-------------+
 * | Extra-large | STANDARD | BELOW    | BELOW      | BELOW      | BESIDE       |
 * | (1600 dp)   |          |          | (VERTICAL) | (VERTICAL) | (HORIZONTAL) |
 * +-------------+----------+----------+------------+------------+--------------+
 * ```
 */
@VisibleForTesting
fun calculateLayoutInternal(
    windowSizeClass: WindowSizeClass,
    isOneHandedModeSupported: Boolean,
): BouncerOverlayLayout {
    with(windowSizeClass) {
        return when {
            isAtLeastBreakpoint(
                WindowSizeClass.WIDTH_DP_EXTRA_LARGE_LOWER_BOUND,
                AdditionalHeightBreakpoints.HEIGHT_DP_EXTRA_LARGE_LOWER_BOUND,
            ) -> BouncerOverlayLayout.BESIDE_USER_SWITCHER
            isAtLeastBreakpoint(
                WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND,
                AdditionalHeightBreakpoints.HEIGHT_DP_EXTRA_LARGE_LOWER_BOUND,
            ) -> BouncerOverlayLayout.BELOW_USER_SWITCHER
            isAtLeastBreakpoint(
                WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND,
                WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND,
            ) -> BouncerOverlayLayout.BESIDE_USER_SWITCHER
            isAtLeastBreakpoint(
                WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
                WindowSizeClass.HEIGHT_DP_EXPANDED_LOWER_BOUND,
            ) -> BouncerOverlayLayout.BELOW_USER_SWITCHER
            isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND) ->
                BouncerOverlayLayout.STANDARD_BOUNCER
            else -> BouncerOverlayLayout.SPLIT_BOUNCER
        }.takeIf { it != BouncerOverlayLayout.BESIDE_USER_SWITCHER || isOneHandedModeSupported }
            ?: BouncerOverlayLayout.STANDARD_BOUNCER
    }
}

/**
 * Internal version of `calculateContainerLayout` in the System UI Compose library, extracted here
 * to allow for testing that's not dependent on Compose.
 *
 * See [calculateLayoutInternal] for the layout table.
 */
@VisibleForTesting
fun calculateContainerLayoutInternal(windowSizeClass: WindowSizeClass): ContainerLayout? {
    return when {
        windowSizeClass.isAtLeastBreakpoint(
            WindowSizeClass.WIDTH_DP_EXTRA_LARGE_LOWER_BOUND,
            WindowSizeClass.HEIGHT_DP_EXPANDED_LOWER_BOUND,
        ) -> ContainerLayout.HORIZONTAL
        windowSizeClass.isAtLeastBreakpoint(
            WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND,
            AdditionalHeightBreakpoints.HEIGHT_DP_EXTRA_LARGE_LOWER_BOUND,
        ) -> ContainerLayout.VERTICAL
        else -> null
    }
}
