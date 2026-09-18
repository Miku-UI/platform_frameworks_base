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

package com.android.compose.gesture

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationResult
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.calculateTargetValue

/**
 * Animates the [Animatable] to a specific [targetOffset], typically the boundary of a scrollable
 * container.
 *
 * This method attempts to use a decay animation to reach the target, as it provides a more natural
 * "flick" feel when navigating to the end of a list. However, if the [initialVelocity] is too low
 * to reach the target via decay, or if the decay animation would be significantly slower than a
 * spring-based animation, it falls back to using the [springAnimationSpec].
 *
 * The [targetOffset] must align with the [Animatable.lowerBound] or [Animatable.upperBound]. Both,
 * [Animatable.lowerBound] and [Animatable.upperBound] must be set before calling this method.
 *
 * The animation will stop at [targetOffset], even if it would overshoot. Clients need to check the
 * returned AnimationResult and run a follow-up animation in that case.
 *
 * @param targetOffset The destination offset, must be either the lower or upper bound.
 * @param initialVelocity The starting velocity of the animation.
 * @param decayAnimationSpec The preferred spec for natural feeling movement.
 * @param springAnimationSpec The fallback spec used when decay is insufficient or too slow.
 */
suspend fun Animatable<Float, AnimationVector1D>.animateToScrollOffsetTarget(
    targetOffset: Float,
    initialVelocity: Float,
    decayAnimationSpec: DecayAnimationSpec<Float>,
    springAnimationSpec: AnimationSpec<Float>,
): AnimationResult<Float, AnimationVector1D> {
    val lowerBound = checkNotNull(lowerBound) { "No lower bound" }
    val upperBound = checkNotNull(upperBound) { "No upper bound" }

    val initialOffset = value
    val decayOffset =
        decayAnimationSpec.calculateTargetValue(
            initialVelocity = initialVelocity,
            initialValue = initialOffset,
        )

    // The decay animation should only play if decayOffset exceeds targetOffset.
    val willDecayReachBounds =
        when (targetOffset) {
            lowerBound -> decayOffset <= lowerBound
            upperBound -> decayOffset >= upperBound
            else -> error("Target $targetOffset should be $lowerBound or $upperBound")
        }

    return if (
        willDecayReachBounds &&
            willDecayFasterThanAnimating(
                springAnimationSpec,
                decayAnimationSpec,
                initialOffset,
                targetOffset,
                initialVelocity,
            )
    ) {
        animateDecay(initialVelocity, decayAnimationSpec).also { result ->
            check(value == targetOffset) {
                buildString {
                    appendLine("animatable.value=$value != $targetOffset=targetOffset")
                    appendLine("  initialOffset=$initialOffset")
                    appendLine("  targetOffset=$targetOffset")
                    appendLine("  initialVelocity=$initialVelocity")
                    appendLine("  decayOffset=$decayOffset")
                    appendLine(
                        "  animateDecay result: reason=${result.endReason} " +
                            "value=${result.endState.value} " +
                            "velocity=${result.endState.velocity}"
                    )
                }
            }
        }
    } else {
        animateTo(
            targetValue = targetOffset,
            animationSpec = springAnimationSpec,
            initialVelocity = initialVelocity,
        )
    }
}

internal fun willDecayFasterThanAnimating(
    animationSpec: AnimationSpec<Float>,
    decayAnimationSpec: DecayAnimationSpec<Float>,
    initialOffset: Float,
    targetOffset: Float,
    initialVelocity: Float,
): Boolean {
    if (initialOffset == targetOffset) {
        return true
    }

    fun hasReachedTargetOffset(value: Float): Boolean {
        return when {
            initialOffset < targetOffset -> value >= targetOffset
            else -> value <= targetOffset
        }
    }

    val converter = Float.VectorConverter
    val decayAnimationSpecVector = decayAnimationSpec.vectorize(converter)
    val initialOffsetVector = converter.convertToVector(initialOffset)
    val initialVelocityVector = converter.convertToVector(initialVelocity)

    // Given that the Animatable that we are going to animate with animationSpec or
    // decayAnimationSpec has bounds and will stop as soon as the targetOffset is reached, we
    // can not use the getDurationNanos() API from VectorizedAnimationSpec and
    // VectorizedDecayAnimationSpec.
    //
    // For the decay, we can use a simple binary search given that once the decay has reached
    // the target value it will never change direction.
    val decayDuration =
        try {
            binarySearch { timeMs ->
                hasReachedTargetOffset(
                    converter.convertFromVector(
                        decayAnimationSpecVector.getValueFromNanos(
                            playTimeNanos = timeMs * MillisToNanos,
                            initialValue = initialOffsetVector,
                            initialVelocity = initialVelocityVector,
                        )
                    )
                )
            }
        } catch (e: Exception) {
            // TODO(b/431165757): Find the root cause of the crash and remove this log.
            throw IllegalStateException(
                buildString {
                    appendLine("binarySearch() threw an exception")
                    appendLine("  initialOffset=$initialOffset")
                    appendLine("  targetOffset=$targetOffset")
                    appendLine("  initialVelocity=$initialVelocity")
                    appendLine("  decayAnimationSpec=$decayAnimationSpec")
                    appendLine("  animationSpec=$animationSpec")
                },
                e,
            )
        }

    // For the animation we can't use binary search given that springs and eased interpolations
    // can oscillate around the target offset. Given that it's ok to estimate this duration, we
    // simply check whether we passed the threshold for each single frame step time (~8ms).
    val animationSpecVector = animationSpec.vectorize(converter)
    val targetOffsetVector = converter.convertToVector(targetOffset)
    val maxAnimationDurationMs =
        animationSpecVector.getDurationNanos(
            initialOffsetVector,
            targetOffsetVector,
            initialVelocityVector,
        ) / MillisToNanos
    var animationDurationMs = 0
    var hasReachedTarget = false
    while (!hasReachedTarget && animationDurationMs < maxAnimationDurationMs) {
        animationDurationMs += ApproximateFrameTime
        hasReachedTarget =
            hasReachedTargetOffset(
                converter.convertFromVector(
                    animationSpecVector.getValueFromNanos(
                        playTimeNanos = animationDurationMs * MillisToNanos,
                        initialValue = initialOffsetVector,
                        initialVelocity = initialVelocityVector,
                        targetValue = targetOffsetVector,
                    )
                )
            )
    }

    return decayDuration <= animationDurationMs
}

/** Returns the lowest timeMs >= 0 for which [f] is true. */
private fun binarySearch(f: (timeMs: Long) -> Boolean): Long {
    if (f(0)) {
        // If the target is reached at t = 0 (due to floating point inaccuracies), return 0.
        return 0L
    }

    var low = 0L
    var high = 128L // common duration that is also a power of 2.
    while (!f(high)) {
        if (high > Long.MAX_VALUE / 2) {
            error("overflow, f($high) returned false")
        }

        low = high
        high *= 2
    }

    var result = high
    while (low <= high) {
        val mid = low + (high - low) / 2
        if (f(mid)) {
            result = mid
            high = mid - 1
        } else {
            low = mid + 1
        }
    }
    return result
}

private const val MillisToNanos = 1_000_000L
private const val ApproximateFrameTime = 1_000 / 120
