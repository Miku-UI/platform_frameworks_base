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

package com.android.compose.animation

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ApproachLayoutModifierNode
import androidx.compose.ui.layout.ApproachMeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** A component that can bounce in one dimension, for instance when it is tapped. */
@Stable
interface Bounceable {
    val bounce: Dp
}

/**
 * Bounce a composable in the given [orientation] when this [bounceable], the [previousBounceable]
 * or [nextBounceable] is bouncing.
 *
 * Important: This modifier should be used on composables that have a fixed size in [orientation],
 * i.e. they should be placed *after* modifiers like Modifier.fillMaxWidth() or Modifier.height().
 *
 * @param bounceable the [Bounceable] associated to the current composable that will make this
 *   composable size grow when bouncing.
 * @param previousBounceable the [Bounceable] associated to the previous composable in [orientation]
 *   that will make this composable shrink when bouncing.
 * @param nextBounceable the [Bounceable] associated to the next composable in [orientation] that
 *   will make this composable shrink when bouncing.
 * @param orientation the orientation in which this bounceable should grow/shrink.
 * @param bounceEnd whether this bounceable should bounce on the end (right in LTR layouts, left in
 *   RTL layouts) side. This can be used for grids for which the last item does not align perfectly
 *   with the end of the grid.
 * @param isWrappingContent whether this bounceable size is determined by the size of its children.
 *   This should preferably be `false` whenever possible, so that this modifier plays nicely with
 *   lookahead animations (e.g. SceneTransitionLayout animations), but is sometimes necessary when
 *   the size of a bounceable strictly depends on the size of its content.
 */
@Stable
fun Modifier.bounceable(
    bounceable: Bounceable,
    previousBounceable: Bounceable?,
    nextBounceable: Bounceable?,
    orientation: Orientation,
    bounceEnd: Boolean = nextBounceable != null,
    isWrappingContent: Boolean = false,
): Modifier {
    return if (isWrappingContent) {
        this then
            WrappingBounceableElement(
                bounceable,
                previousBounceable,
                nextBounceable,
                orientation,
                bounceEnd,
            )
    } else {
        this then
            BounceableElement(
                bounceable,
                previousBounceable,
                nextBounceable,
                orientation,
                bounceEnd,
            )
    }
}

private data class BounceableElement(
    private val bounceable: Bounceable,
    private val previousBounceable: Bounceable?,
    private val nextBounceable: Bounceable?,
    private val orientation: Orientation,
    private val bounceEnd: Boolean,
) : ModifierNodeElement<BounceableNode>() {
    override fun create(): BounceableNode {
        return BounceableNode(
            bounceable,
            previousBounceable,
            nextBounceable,
            orientation,
            bounceEnd,
        )
    }

    override fun update(node: BounceableNode) {
        node.bounceable = bounceable
        node.previousBounceable = previousBounceable
        node.nextBounceable = nextBounceable
        node.orientation = orientation
        node.bounceEnd = bounceEnd
    }
}

private class BounceableNode(
    var bounceable: Bounceable,
    var previousBounceable: Bounceable?,
    var nextBounceable: Bounceable?,
    var orientation: Orientation,
    var bounceEnd: Boolean = nextBounceable != null,
) : Modifier.Node(), LayoutModifierNode {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        // The constraints in the orientation should be fixed, otherwise there is no way to know
        // what the size of our child node will be without this animation code.
        checkFixedSize(constraints, orientation)
        return measure(
            constraints = constraints,
            measurable = measurable,
            bounceable = bounceable,
            previousBounceable = previousBounceable,
            nextBounceable = nextBounceable,
            orientation = orientation,
            bounceEnd = bounceEnd,
            idleSize = IntSize(constraints.maxWidth, constraints.maxHeight),
        )
    }
}

private fun MeasureScope.measure(
    constraints: Constraints,
    measurable: Measurable,
    bounceable: Bounceable,
    previousBounceable: Bounceable?,
    nextBounceable: Bounceable?,
    orientation: Orientation,
    bounceEnd: Boolean,
    idleSize: IntSize,
): MeasureResult {
    var sizePrevious = 0f
    var sizeNext = 0f

    if (previousBounceable != null) {
        sizePrevious += bounceable.bounce.toPx() - previousBounceable.bounce.toPx()
    }

    if (nextBounceable != null) {
        sizeNext += bounceable.bounce.toPx() - nextBounceable.bounce.toPx()
    } else if (bounceEnd) {
        sizeNext += bounceable.bounce.toPx()
    }

    when (orientation) {
        Orientation.Horizontal -> {
            val idleWidth = idleSize.width
            val animatedWidth = (idleWidth + sizePrevious + sizeNext).roundToInt()
            val animatedConstraints =
                constraints.copy(minWidth = animatedWidth, maxWidth = animatedWidth)

            val placeable = measurable.measure(animatedConstraints)

            // Important: we still place the element using the idle size coming from the
            // constraints, otherwise the parent will automatically center this node given the size
            // that it expects us to be. This allows us to then place the element where we want it
            // to be.
            return layout(idleWidth, placeable.height) {
                placeable.placeRelative(-sizePrevious.roundToInt(), 0)
            }
        }

        Orientation.Vertical -> {
            val idleHeight = idleSize.height
            val animatedHeight = (idleHeight + sizePrevious + sizeNext).roundToInt()
            val animatedConstraints =
                constraints.copy(minHeight = animatedHeight, maxHeight = animatedHeight)

            val placeable = measurable.measure(animatedConstraints)
            return layout(placeable.width, idleHeight) {
                placeable.placeRelative(0, -sizePrevious.roundToInt())
            }
        }
    }
}

private fun checkFixedSize(constraints: Constraints, orientation: Orientation) {
    when (orientation) {
        Orientation.Horizontal -> {
            check(constraints.hasFixedWidth) {
                "Modifier.bounceable() should receive a fixed width from its parent. Make sure " +
                    "that it is used *after* a fixed-width Modifier in the horizontal axis (like" +
                    " Modifier.fillMaxWidth() or Modifier.width()). If doing so is impossible" +
                    " and the bounceable has to wrap its content, set isWrappingContent to `true`."
            }
        }

        Orientation.Vertical -> {
            check(constraints.hasFixedHeight) {
                "Modifier.bounceable() should receive a fixed height from its parent. Make sure " +
                    "that it is used *after* a fixed-height Modifier in the vertical axis (like" +
                    " Modifier.fillMaxHeight() or Modifier.height()). If doing so is impossible " +
                    "and the bounceable has to wrap its content, set isWrappingContent to `true`."
            }
        }
    }
}

private data class WrappingBounceableElement(
    private val bounceable: Bounceable,
    private val previousBounceable: Bounceable?,
    private val nextBounceable: Bounceable?,
    private val orientation: Orientation,
    private val bounceEnd: Boolean,
) : ModifierNodeElement<WrappingBounceableNode>() {
    override fun create(): WrappingBounceableNode {
        return WrappingBounceableNode(
            bounceable,
            previousBounceable,
            nextBounceable,
            orientation,
            bounceEnd,
        )
    }

    override fun update(node: WrappingBounceableNode) {
        node.bounceable = bounceable
        node.previousBounceable = previousBounceable
        node.nextBounceable = nextBounceable
        node.orientation = orientation
        node.bounceEnd = bounceEnd
    }
}

private class WrappingBounceableNode(
    var bounceable: Bounceable,
    var previousBounceable: Bounceable?,
    var nextBounceable: Bounceable?,
    var orientation: Orientation,
    var bounceEnd: Boolean = nextBounceable != null,
) : Modifier.Node(), ApproachLayoutModifierNode {
    override fun isMeasurementApproachInProgress(lookaheadSize: IntSize): Boolean {
        fun Bounceable?.isBouncing() = this != null && this.bounce != 0.dp

        return bounceable.isBouncing() ||
            previousBounceable.isBouncing() ||
            nextBounceable.isBouncing()
    }

    override fun ApproachMeasureScope.approachMeasure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        return measure(
            constraints = constraints,
            measurable = measurable,
            bounceable = bounceable,
            previousBounceable = previousBounceable,
            nextBounceable = nextBounceable,
            orientation = orientation,
            bounceEnd = bounceEnd,
            idleSize = lookaheadSize,
        )
    }
}
