/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.notifications.ui.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import com.android.compose.gesture.effect.OffsetOverscrollEffect

/**
 * A container that layers a background and content for the NotificationScrim.
 *
 * It provides two sizing strategies for the background:
 * 1. If [shouldBackgroundFillMaxHeight] is true, the background is measured with a fixed height
 *    provided by [backgroundHeightPx], while the content fills the available space.
 * 2. If [shouldBackgroundFillMaxHeight] is false, the background is measured to match the exact
 *    size of the content. Used in DualShade.
 */
@Composable
fun ScrimContainer(
    modifier: Modifier = Modifier,
    shouldBackgroundFillMaxHeight: Boolean,
    content: @Composable () -> Unit,
    background: @Composable () -> Unit,
) {
    // Make the bg tall enough to prevent any possible background gaps during overscroll.
    val backgroundHeightDp =
        LocalWindowInfo.current.containerDpSize.height + OffsetOverscrollEffect.DefaultMaxDistance
    val density = LocalDensity.current

    Layout(
        contents = listOf(background, content),
        modifier = modifier,
        measurePolicy = { measurables, constraints ->
            val backgroundMeasurables = measurables[0]
            val contentMeasurables = measurables[1]

            check(backgroundMeasurables.size == 1) { "background slot must have exactly one child" }
            check(contentMeasurables.size == 1) { "content slot must have exactly one child" }

            val backgroundMeasurable = backgroundMeasurables[0]
            val contentMeasurable = contentMeasurables[0]

            val backgroundPlaceable: Placeable
            val contentPlaceable: Placeable

            if (shouldBackgroundFillMaxHeight) {
                // Fill the entire available space with the content, and force the background to
                // match the provided backgroundHeightPx to ensure it covers the full display area
                // (including overscroll).
                contentPlaceable =
                    contentMeasurable.measure(
                        Constraints.fixed(
                            width = constraints.maxWidth,
                            height = constraints.maxHeight,
                        )
                    )

                backgroundPlaceable =
                    backgroundMeasurable.measure(
                        Constraints.fixed(
                            width = constraints.maxWidth,
                            height = with(density) { backgroundHeightDp.roundToPx() },
                        )
                    )
            } else {
                // Make the background size match the content size.
                // The component should be only as large as its content requires. We measure the
                // content first, then force the background to be the *exact* same size. The final
                // layout size is determined by the content.
                contentPlaceable = contentMeasurable.measure(constraints)
                val backgroundConstraints =
                    Constraints.fixed(contentPlaceable.width, contentPlaceable.height)

                backgroundPlaceable = backgroundMeasurable.measure(backgroundConstraints)
            }

            // Content draws over the Background
            layout(width = contentPlaceable.width, height = contentPlaceable.height) {
                backgroundPlaceable.place(IntOffset.Zero)
                contentPlaceable.place(IntOffset.Zero)
            }
        },
    )
}
