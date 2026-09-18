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

package com.android.systemui.statusbar.icons.ui.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ParentDataModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastMap
import kotlin.math.max
import kotlinx.coroutines.launch

/**
 * A row that lays out icons horizontally, prioritizing those at the end of the list (right side).
 * If the content exceeds the available width, it replaces the first non-fitting icon with an
 * animated overflow indicator dot on the far left.
 */
@Composable
fun EllipsizingRow(
    spacing: Dp,
    modifier: Modifier = Modifier,
    overflowColor: Color = LocalContentColor.current,
    overflowDotSize: TextUnit = 4.sp,
    overflowDotLayoutSpace: TextUnit = 12.sp,
    content: EllipsizingRowScope.() -> Unit,
) {
    val layoutState = remember { IconLayoutState() }

    Layout(
        modifier = modifier,
        content = {
            val scopeImpl =
                object : EllipsizingRowScope {
                    val items = mutableListOf<ItemData>()

                    override fun item(key: Any?, content: @Composable () -> Unit) {
                        items.add(ItemData(key, content))
                    }
                }

            scopeImpl.content()
            scopeImpl.items.fastForEach { itemData ->
                key(itemData.key) {
                    Box(Modifier.animateOverflow(overflowColor, overflowDotSize)) {
                        itemData.content()
                    }
                }
            }
        },
    ) { measurables, constraints ->
        if (measurables.isEmpty()) {
            return@Layout layout(0, 0) {}
        }

        val spacingPx = spacing.roundToPx()
        val dotLayoutSpacePx = overflowDotLayoutSpace.roundToPx()

        // Measure all children with loose constraints to get their preferred sizes.
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val allPlaceables = measurables.fastMap { it.measure(childConstraints) }

        // Determine visibility and overflow based on available space.
        val measureResult: MeasureResult =
            measureIcons(
                iconPlaceables = allPlaceables,
                constraints = constraints,
                spacingPx = spacingPx,
                dotLayoutSpacePx = dotLayoutSpacePx,
            )

        // Define the layout boundaries.
        val layoutWidth = constraints.constrainWidth(measureResult.totalWidth)
        val layoutHeight = constraints.constrainHeight(measureResult.totalHeight)

        layout(layoutWidth, layoutHeight) {
            // Calculate overflow count and detect changes for animation triggering.
            val hiddenIconCount = measurables.size - measureResult.visibleIconIndices.size
            val currentOverflowCount =
                if (measureResult.overflowTargetIndex != null) {
                    hiddenIconCount
                } else {
                    0
                }

            // Pulse if the number of overflowed icons has increased.
            val shouldPulse = currentOverflowCount > layoutState.previousOverflowCount
            layoutState.previousOverflowCount = currentOverflowCount

            // Update the animation state of each child via their Modifier.Node.
            measurables.fastForEachIndexed { index, measurable ->
                val animationNode = measurable.parentData as? OverflowAnimationNode
                checkNotNull(animationNode) {
                    "Item $index in EllipsizingIconsRow is missing the required Modifier.animateOverflow()."
                }

                val isOverflowTarget = index == measureResult.overflowTargetIndex
                val pulseThisItem = isOverflowTarget && shouldPulse

                // Check if the item is transforming into the dot for the first time.
                val isNewlyOverflowing = !animationNode.isOverflowing && isOverflowTarget

                animationNode.updateOverflowState(
                    isOverflowing = isOverflowTarget,
                    shouldPulse = pulseThisItem && !isNewlyOverflowing,
                )
            }

            var xPosition = 0

            // Place the overflow dot on the far left.
            measureResult.overflowTargetIndex?.let { index ->
                val placeable = allPlaceables[index]
                val y = Alignment.CenterVertically.align(placeable.height, layoutHeight)

                val xOffset = xPosition + (dotLayoutSpacePx - placeable.width) / 2
                placeable.placeRelative(x = xOffset, y = y)

                xPosition += dotLayoutSpacePx

                if (measureResult.visibleIconIndices.isNotEmpty()) {
                    xPosition += spacingPx
                }
            }

            // Place the visible icons sequentially.
            measureResult.visibleIconIndices.forEachIndexed { listIndex, childIndex ->
                val placeable = allPlaceables[childIndex]
                val y = Alignment.CenterVertically.align(placeable.height, layoutHeight)
                placeable.placeRelative(x = xPosition, y = y)
                xPosition += placeable.width

                if (listIndex < measureResult.visibleIconIndices.size - 1) {
                    xPosition += spacingPx
                }
            }
        }
    }
}

/**
 * Determines which icons fit within the constraints, prioritizing icons from the end of the list
 * (right-to-left priority). Also accounts for the width required by the overflow indicator.
 */
private fun measureIcons(
    iconPlaceables: List<Placeable>,
    constraints: Constraints,
    spacingPx: Int,
    dotLayoutSpacePx: Int,
): MeasureResult {
    var remainingWidth = constraints.maxWidth
    // Stores indices of icons that fit, sorted from left to right.
    val visibleIconIndices = mutableListOf<Int>()
    var overflowTargetIndex: Int? = null

    // Greedy fitting: Iterate from the end (right) to the start (left).
    for (index in iconPlaceables.indices.reversed()) {
        val placeable = iconPlaceables[index]
        // Calculate width needed, including spacing if it's not the very first icon (rightmost).
        val requiredWidth =
            if (visibleIconIndices.isEmpty()) {
                placeable.width
            } else {
                spacingPx + placeable.width
            }

        if (remainingWidth >= requiredWidth) {
            remainingWidth -= requiredWidth
            // Add to the start of the list to maintain correct LTR order.
            visibleIconIndices.add(0, index)
        } else {
            // This is the first icon (from the right) that doesn't fit.
            overflowTargetIndex = index
            break
        }
    }

    if (overflowTargetIndex != null) {
        var spaceRequiredForOverflow =
            if (visibleIconIndices.isNotEmpty()) {
                dotLayoutSpacePx + spacingPx
            } else {
                dotLayoutSpacePx
            }

        // If there isn't enough leftover space for the dot slot, greedily drop visible icons from
        // the left.
        while (remainingWidth < spaceRequiredForOverflow && visibleIconIndices.isNotEmpty()) {
            val droppedIndex = visibleIconIndices.removeAt(0)
            overflowTargetIndex = droppedIndex
            val droppedPlaceable = iconPlaceables[droppedIndex]

            remainingWidth += droppedPlaceable.width

            if (visibleIconIndices.isNotEmpty()) {
                remainingWidth += spacingPx
            } else {
                spaceRequiredForOverflow = dotLayoutSpacePx
            }
        }
    }

    // Calculate final dimensions based on what is actually visible.
    val (finalWidth, finalHeight) =
        calculateDimensions(
            iconPlaceables = iconPlaceables,
            visibleIconIndices = visibleIconIndices,
            overflowTargetIndex = overflowTargetIndex,
            spacingPx = spacingPx,
            dotLayoutSpacePx = dotLayoutSpacePx,
        )

    return MeasureResult(
        visibleIconIndices = visibleIconIndices,
        overflowTargetIndex = overflowTargetIndex,
        totalWidth = finalWidth,
        totalHeight = finalHeight,
    )
}

/**
 * Calculates the total width and height required for the visible elements (overflow dot + icons)
 * and the spacing between them.
 */
private fun calculateDimensions(
    iconPlaceables: List<Placeable>,
    visibleIconIndices: List<Int>,
    overflowTargetIndex: Int?,
    spacingPx: Int,
    dotLayoutSpacePx: Int,
): Pair<Int, Int> {
    var totalWidth = 0
    var maxHeight = 0

    // Account for the overflow dot slot, if present.
    if (overflowTargetIndex != null) {
        val overflowTarget = iconPlaceables[overflowTargetIndex]
        totalWidth += dotLayoutSpacePx
        maxHeight = max(maxHeight, overflowTarget.height)
    }

    // Account for visible icons.
    if (visibleIconIndices.isNotEmpty()) {
        val visibleIconsWidth = visibleIconIndices.sumOf { iconPlaceables[it].width }
        // We know this list is not empty, so maxOf can be used safely.
        val maxVisibleIconHeight = visibleIconIndices.maxOf { iconPlaceables[it].height }

        totalWidth += visibleIconsWidth
        maxHeight = max(maxHeight, maxVisibleIconHeight)
    }

    // Account for spacing.
    val numberOfIconGaps = (visibleIconIndices.size - 1).coerceAtLeast(0)
    val gapAfterOverflow =
        if (overflowTargetIndex != null && visibleIconIndices.isNotEmpty()) 1 else 0

    totalWidth += (numberOfIconGaps + gapAfterOverflow) * spacingPx

    return Pair(totalWidth, maxHeight)
}

/** Represents the state where the original content is fully visible. */
private const val VISIBILITY_TARGET_CONTENT = 1f
/** Represents the state where the overflow indicator (dot) is fully visible. */
private const val VISIBILITY_TARGET_OVERFLOW = 0f

/**
 * The point in the visibility animation progress (0.0 to 1.0) where the fade-through animation
 * switches from fading out the icon (1.0 -> THRESHOLD) to fading in the indicator (THRESHOLD ->
 * 0.0).
 */
private const val FADE_THROUGH_THRESHOLD = 0.5f

/** The maximum scale factor applied during the pulse animation. */
private const val PULSE_SCALE_TARGET = 1.5f
/** The base scale factor when the pulse animation is idle. */
private const val PULSE_SCALE_BASE = 1f

interface EllipsizingRowScope {
    fun item(key: Any? = null, content: @Composable () -> Unit)
}

internal data class ItemData(val key: Any?, val content: @Composable () -> Unit)

/**
 * A modifier that enables an item to participate in the overflow animation of an [EllipsizingRow].
 *
 * When the parent layout determines this item should be the overflow target, this modifier handles
 * the "fade-through" animation: the item's content fades and scales out, while a circular indicator
 * dot fades and scales in.
 *
 * This modifier **must** be applied to every direct child of [EllipsizingRow].
 */
private fun Modifier.animateOverflow(
    overflowIndicatorColor: Color,
    overflowDotSize: TextUnit,
): Modifier =
    this.then(
        OverflowAnimationElement(
            overflowIndicatorColor = overflowIndicatorColor,
            overflowDotSize = overflowDotSize,
        )
    )

private data class OverflowAnimationElement(
    val overflowIndicatorColor: Color,
    val overflowDotSize: TextUnit,
) : ModifierNodeElement<OverflowAnimationNode>() {

    override fun create() = OverflowAnimationNode(overflowIndicatorColor, overflowDotSize)

    override fun update(node: OverflowAnimationNode) {
        node.overflowIndicatorColor = overflowIndicatorColor
        node.overflowDotSize = overflowDotSize
    }

    /** For better tooling support in the Layout Inspector. */
    override fun InspectorInfo.inspectableProperties() {
        name = "animateOverflow"
        properties["overflowIndicatorColor"] = overflowIndicatorColor
        properties["overflowDotSize"] = overflowDotSize
    }
}

/**
 * The Modifier.Node responsible for executing the overflow animations and communicating layout
 * intent back to the parent [EllipsizingRow].
 *
 * It implements [DrawModifierNode] to control rendering and [ParentDataModifierNode] so the parent
 * layout can locate and update this specific node instance during the layout phase.
 */
private class OverflowAnimationNode(
    var overflowIndicatorColor: Color,
    var overflowDotSize: TextUnit,
) : Modifier.Node(), DrawModifierNode, ParentDataModifierNode {

    /** Tracks whether this item is currently designated as the overflow indicator. */
    var isOverflowing = false
        private set

    /** Drives the fade-through animation (1f = content visible, 0f = dot visible). */
    private val visibilityAnimation = Animatable(VISIBILITY_TARGET_CONTENT)

    /** Drives the pulsing animation when new items are added to the overflow. */
    private val pulseAnimation = Animatable(PULSE_SCALE_BASE)

    /** Paint used for applying alpha during the fade-out of the original content. */
    private val contentPaint = Paint()

    /**
     * Updates the overflow state based on the parent layout's decision. This triggers the
     * appropriate animations.
     */
    fun updateOverflowState(isOverflowing: Boolean, shouldPulse: Boolean) {
        if (shouldPulse) {
            triggerPulse()
        }

        if (this.isOverflowing == isOverflowing) return

        this.isOverflowing = isOverflowing
        val targetValue =
            if (isOverflowing) VISIBILITY_TARGET_OVERFLOW else VISIBILITY_TARGET_CONTENT

        coroutineScope.launch {
            visibilityAnimation.animateTo(targetValue = targetValue, animationSpec = spring())
        }
    }

    private fun triggerPulse() {
        // Avoid restarting the pulse if it's already running.
        if (pulseAnimation.isRunning) return

        coroutineScope.launch {
            // Pulse outwards
            pulseAnimation.animateTo(targetValue = PULSE_SCALE_TARGET, animationSpec = spring())
            // Settle back to the base scale
            pulseAnimation.animateTo(targetValue = PULSE_SCALE_BASE, animationSpec = spring())
        }
    }

    override fun ContentDrawScope.draw() {
        val visibilityProgress = this@OverflowAnimationNode.visibilityAnimation.value

        // If the item is fully visible, draw its content directly.
        if (visibilityProgress == VISIBILITY_TARGET_CONTENT) {
            drawContent()
            return
        }

        // The animation is a fade-through, composed of two parts:
        // 1. Content fades/scales out (progress 1.0 down to FADE_THROUGH_THRESHOLD).
        // 2. Indicator fades/scales in (progress FADE_THROUGH_THRESHOLD down to 0.0).

        // Calculate progress for the content fade-out.
        val contentFadeProgress =
            ((visibilityProgress - FADE_THROUGH_THRESHOLD) / (1f - FADE_THROUGH_THRESHOLD))
                .fastCoerceIn(0f, 1f)

        // Calculate progress for the indicator fade-in.
        val indicatorFadeProgress =
            (1f - (visibilityProgress / FADE_THROUGH_THRESHOLD)).fastCoerceIn(0f, 1f)

        drawContentWithTransform(contentFadeProgress)
        drawIndicator(indicatorFadeProgress)
    }

    /**
     * Draws the original composable content, applying scale and alpha transformations based on the
     * animation [progress] (1.0 = fully visible, 0.0 = hidden).
     */
    private fun ContentDrawScope.drawContentWithTransform(progress: Float) {
        if (progress <= 0f) return

        contentPaint.alpha = progress

        withTransform({ scale(scaleX = progress, scaleY = progress, pivot = center) }) {
            this@drawContentWithTransform.drawContent()
        }
    }

    /**
     * Draws the circular overflow indicator, fading and scaling it in as the [progress] goes from
     * 0.0 to 1.0, and applying the pulse animation.
     */
    private fun ContentDrawScope.drawIndicator(progress: Float) {
        if (progress <= 0f) return

        val pulseScale = this@OverflowAnimationNode.pulseAnimation.value
        val baseRadius = overflowDotSize.toPx() / 2f

        // Radius scales with both the fade-in progress and the pulse animation.
        val animatedRadius = baseRadius * progress * pulseScale

        drawCircle(
            color = overflowIndicatorColor,
            radius = animatedRadius,
            center = center,
            alpha = progress,
        )
    }

    /**
     * Implements ParentDataModifierNode. This allows the parent Layout ([EllipsizingRow]) to access
     * this specific Node instance during the layout phase via measurable.parentData.
     */
    override fun Density.modifyParentData(parentData: Any?) = this@OverflowAnimationNode
}

/** Holds the result of the measurement pass in [EllipsizingRow]. */
private data class MeasureResult(
    /** The indices of the icons that should be displayed as-is. */
    val visibleIconIndices: List<Int>,
    /**
     * The index of the icon that should be transformed into the overflow dot. Null if no overflow.
     */
    val overflowTargetIndex: Int?,

    /** The final size of the row. Accounts for items being removed due to lack of space. */
    val totalWidth: Int,
    val totalHeight: Int,
)

/**
 * Stable state holder for tracking layout information across compositions, used for detecting when
 * to pulse the overflow indicator.
 */
@Stable
private class IconLayoutState {
    var previousOverflowCount = 0
}
