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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Stable
import androidx.compose.runtime.withFrameMillis
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * A component that can bounce in one dimension, for instance when it is tapped.
 *
 * Both [animateExpansion] amd [animateToRest] will be called when the optional [InteractionSource]
 * from [bounceable] receives press and release events.
 */
@Stable
interface Bounceable {
    val bounce: Dp

    suspend fun animateExpansion() {}

    suspend fun animateToRest() {}
}

/**
 * The default implementation of [Bounceable].
 *
 * @param bounceSize the size in [Dp] of the bounce when this component is pressed.
 * @param animationSpec the [AnimationSpec] to be used when animating the bounce.
 */
class BounceableImpl(
    private val bounceSize: Dp,
    private val animationSpec: AnimationSpec<Dp> = spring(),
) : Bounceable {
    private val bounceAnimatable = Animatable(0.dp, Dp.VectorConverter)

    override val bounce: Dp
        get() = bounceAnimatable.value

    override suspend fun animateExpansion() {
        bounceAnimatable.animateTo(bounceSize, animationSpec)
    }

    override suspend fun animateToRest() {
        waitUntil { bounceAnimatable.value > bounceSize * MINIMUM_BOUNCE_RATIO }
        bounceAnimatable.animateTo(0.dp, animationSpec)
    }

    private suspend fun waitUntil(condition: () -> Boolean) {
        // This is taken from the Material Expressive implementation of ButtonGroup, and is needed
        // to ensure a minimum bounce is shown for quick taps.
        val initialTimeMillis = withFrameMillis { it }
        while (!condition()) {
            val timeMillis = withFrameMillis { it }
            if (timeMillis - initialTimeMillis > MAX_WAIT_TIME_MILLIS) return
        }
        return
    }

    private companion object {
        // Minimum ratio of the bounce size the animation should reach before going back to rest on
        // fast clicks
        const val MINIMUM_BOUNCE_RATIO = .75f
        const val MAX_WAIT_TIME_MILLIS = 1_000L
    }
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
 * @param interactionSource an optional [InteractionSource] that will be used to drive the bounce
 *   animation on presses. If null, the animation will have to be driven manually.
 */
@Stable
fun Modifier.bounceable(
    bounceable: Bounceable,
    previousBounceable: Bounceable?,
    nextBounceable: Bounceable?,
    orientation: Orientation,
    bounceEnd: Boolean = nextBounceable != null,
    isWrappingContent: Boolean = false,
    interactionSource: InteractionSource? = null,
): Modifier {
    val pressModifier =
        interactionSource?.let { BounceOnPressElement(bounceable, interactionSource) } ?: Modifier

    val bounceModifier =
        if (isWrappingContent) {
            WrappingBounceableElement(
                bounceable,
                previousBounceable,
                nextBounceable,
                orientation,
                bounceEnd,
            )
        } else {
            BounceableElement(
                bounceable,
                previousBounceable,
                nextBounceable,
                orientation,
                bounceEnd,
            )
        }

    return this then pressModifier then bounceModifier
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

private data class BounceOnPressElement(
    val bounceable: Bounceable,
    val interactionSource: InteractionSource,
) : ModifierNodeElement<BounceOnPressNode>() {
    override fun create(): BounceOnPressNode {
        return BounceOnPressNode(bounceable, interactionSource)
    }

    override fun update(node: BounceOnPressNode) {
        node.bounceable = bounceable
        if (node.interactionSource != interactionSource) {
            node.interactionSource = interactionSource
            node.launchCollectionJob()
        }
    }
}

private class BounceOnPressNode(
    var bounceable: Bounceable,
    var interactionSource: InteractionSource,
) : Modifier.Node() {
    private var collectionJob: Job? = null

    override fun onAttach() {
        super.onAttach()
        launchCollectionJob()
    }

    override fun onDetach() {
        super.onDetach()
        collectionJob?.cancel()
        collectionJob = null
    }

    fun launchCollectionJob() {
        collectionJob?.cancel()
        collectionJob =
            coroutineScope.launch {
                val pressInteractions = mutableListOf<PressInteraction.Press>()
                launch {
                    interactionSource.interactions
                        .map { interaction ->
                            when (interaction) {
                                is PressInteraction.Press -> pressInteractions.add(interaction)
                                is PressInteraction.Release ->
                                    pressInteractions.remove(interaction.press)
                                is PressInteraction.Cancel ->
                                    pressInteractions.remove(interaction.press)
                            }
                            pressInteractions.isNotEmpty()
                        }
                        .distinctUntilChanged()
                        .collectLatest { pressed ->
                            if (pressed) {
                                launch { bounceable.animateExpansion() }
                            } else {
                                bounceable.animateToRest()
                            }
                        }
                }
            }
    }
}
