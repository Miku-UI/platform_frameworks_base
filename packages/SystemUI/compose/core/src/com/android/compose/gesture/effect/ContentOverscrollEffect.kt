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

package com.android.compose.gesture.effect

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationConstants
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.copy
import androidx.compose.animation.core.spring
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import com.android.compose.ui.util.HorizontalSpaceVectorConverter
import com.android.compose.ui.util.SpaceVectorConverter
import com.android.compose.ui.util.VerticalSpaceVectorConverter
import com.android.systemui.Flags.stlFlingAnimationConsumeOvershoot
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * An [OverscrollEffect] that uses an [Animatable] to track and animate overscroll values along a
 * specific [Orientation].
 */
interface ContentOverscrollEffect : OverscrollEffect {
    /** The current overscroll value. */
    val overscrollDistance: Float
}

open class BaseContentOverscrollEffect(
    private val animationScope: CoroutineScope,
    private val animationSpec: AnimationSpec<Float>,
) : ContentOverscrollEffect {

    /**
     * The [Animatable] that holds the current overscroll value.
     *
     * NOTE: This is replaced by animationState with `stlFlingAnimationConsumeOvershoot`.
     */
    private val animatable = Animatable(initialValue = 0f)

    /**
     * The [AnimationState] that holds the current overscroll value.
     *
     * Using an [AnimationState] (instead of an [Animatable]) to support `sequentialAnimation` when
     * continuing the animation after performFling.
     *
     * Unlike the [Animatable], [AnimationState] does not ensures mutual exclusiveness on its
     * animations. Hence [AnimationState] has no `snapTo`, which is worked around by keeping the
     * animationState in a MutableState, and can thus be copied on each new animation start.
     */
    private var animationState by mutableStateOf(AnimationState(initialValue = 0f))
    private var lastConverter: SpaceVectorConverter? = null

    override val overscrollDistance: Float
        get() = if (stlFlingAnimationConsumeOvershoot()) animationState.value else animatable.value

    override val isInProgress: Boolean by derivedStateOf {
        // We need both checks, because [overscrollDistance] can be
        // - zero while it is already being animated, if the animation starts from 0
        // - greater than zero without an animation, if the content is still being dragged
        overscrollDistance != 0f ||
            if (stlFlingAnimationConsumeOvershoot()) animationState.isRunning
            else animatable.isRunning
    }

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        val converter = converterOrNull(delta.x, delta.y) ?: return performScroll(delta)
        return converter.applyToScroll(delta, source, performScroll)
    }

    private fun SpaceVectorConverter.applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        val deltaForAxis = delta.toFloat()

        // If we're currently overscrolled, and the user scrolls in the opposite direction, we need
        // to "relax" the overscroll by consuming some of the scroll delta to bring it back towards
        // zero.
        val currentOffset = overscrollDistance
        val sameDirection = deltaForAxis.sign == currentOffset.sign
        val consumedByPreScroll =
            if (abs(currentOffset) > 0f && !sameDirection) {
                    // The user has scrolled in the opposite direction.
                    val prevOverscrollValue = currentOffset
                    val newOverscrollValue = currentOffset + deltaForAxis
                    if (sign(prevOverscrollValue) != sign(newOverscrollValue)) {
                        // Enough to completely cancel the overscroll. We snap the overscroll value
                        // back to zero and consume the corresponding amount of the scroll delta.
                        if (stlFlingAnimationConsumeOvershoot()) {
                            animationState = AnimationState(0f)
                        } else {
                            animationScope.launch { animatable.snapTo(0f) }
                        }
                        -prevOverscrollValue
                    } else {
                        // Not enough to cancel the overscroll. We update the overscroll value
                        // accordingly and consume the entire scroll delta.
                        if (stlFlingAnimationConsumeOvershoot()) {
                            animationState = AnimationState(newOverscrollValue)
                        } else {
                            animationScope.launch { animatable.snapTo(newOverscrollValue) }
                        }
                        deltaForAxis
                    }
                } else {
                    0f
                }
                .toOffset()

        // After handling any overscroll relaxation, we pass the remaining scroll delta to the
        // standard scrolling logic.
        val leftForScroll = delta - consumedByPreScroll
        val consumedByScroll = performScroll(leftForScroll)
        val overscrollDelta = leftForScroll - consumedByScroll

        // If the user is dragging (not flinging), and there's any remaining scroll delta after the
        // standard scrolling logic has been applied, we add it to the overscroll.
        val overscroll = overscrollDelta.toFloat()
        val consumedByPostScroll =
            if (abs(overscroll) > 0f && source == NestedScrollSource.UserInput) {
                if (stlFlingAnimationConsumeOvershoot()) {
                    animationState = AnimationState(currentOffset + overscroll)
                } else {
                    animationScope.launch { animatable.snapTo(currentOffset + overscroll) }
                }
                overscroll.toOffset()
            } else {
                Offset.Zero
            }

        return consumedByPreScroll + consumedByScroll + consumedByPostScroll
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        val converter = converterOrNull(velocity.x, velocity.y) ?: return
        converter.applyToFling(velocity, performFling)
    }

    private suspend fun SpaceVectorConverter.applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        if (stlFlingAnimationConsumeOvershoot()) {
            coroutineScope {
                // To seamlessly continue the animation after `performFling()`, capture the current
                // frame's time, to launch the animation below with `sequentialAnimation = true`
                // Since animation frames are produces anyways within this coroutine scope, either
                // by `performFling` or the `animateTo`, requesting the frames will not have a
                // negative effect on battery.
                var lastFrameTime = AnimationConstants.UnspecifiedTime
                val frameTimeJob = launch { while (true) withFrameNanos { lastFrameTime = it } }

                val consumed =
                    try {
                        performFling(velocity)
                    } finally {
                        frameTimeJob.cancel()
                    }

                val remaining = velocity - consumed

                animationState =
                    animationState.copy(
                        lastFrameTimeNanos = lastFrameTime,
                        velocity = remaining.toFloat(),
                    )

                animationState.animateTo(
                    0f,
                    animationSpec.withVisibilityThreshold(1f),
                    sequentialAnimation = true,
                )
            }
        } else {
            // We launch a coroutine to ensure the fling animation starts after any pending [snapTo]
            // animations have finished.
            // This guarantees a smooth, sequential execution of animations on the overscroll value.
            coroutineScope {
                launch {
                    val consumed = performFling(velocity)
                    val remaining = velocity - consumed
                    animatable.animateTo(
                        0f,
                        animationSpec.withVisibilityThreshold(1f),
                        remaining.toFloat(),
                    )
                }
            }
        }
    }

    private fun <T> AnimationSpec<T>.withVisibilityThreshold(
        visibilityThreshold: T
    ): AnimationSpec<T> {
        return when (this) {
            is SpringSpec ->
                spring(
                    stiffness = stiffness,
                    dampingRatio = dampingRatio,
                    visibilityThreshold = visibilityThreshold,
                )
            else -> this
        }
    }

    protected fun requireConverter(): SpaceVectorConverter {
        return checkNotNull(lastConverter) {
            "lastConverter is null, make sure to call requireConverter() only when " +
                "overscrollDistance != 0f"
        }
    }

    private fun converterOrNull(x: Float, y: Float): SpaceVectorConverter? {
        val converter: SpaceVectorConverter =
            when {
                x != 0f && y != 0f ->
                    error(
                        "BaseContentOverscrollEffect only supports single orientation scrolls " +
                            "and velocities"
                    )
                x == 0f && y == 0f -> lastConverter ?: return null
                x != 0f -> HorizontalSpaceVectorConverter
                else -> VerticalSpaceVectorConverter
            }

        if (lastConverter != null) {
            check(lastConverter == converter) {
                "BaseContentOverscrollEffect should always be used in the same orientation"
            }
        } else {
            lastConverter = converter
        }

        return converter
    }
}
