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

package com.android.systemui.scene.ui.composable.transitions

import android.os.Build
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import com.android.compose.animation.scene.ContentKey
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.TransitionBuilder
import com.android.compose.animation.scene.UserActionDistance
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.compose.animation.scene.transformation.CustomPropertyTransformation
import com.android.compose.animation.scene.transformation.PropertyTransformation
import com.android.compose.animation.scene.transformation.PropertyTransformationScope
import com.android.compose.animation.scene.transformation.Transformation
import com.android.systemui.bouncer.ui.composable.Bouncer
import com.android.systemui.scene.ui.viewmodel.ToBouncerTransitionViewModel
import kotlin.math.min
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun TransitionBuilder.toBouncerTransition(viewModel: ToBouncerTransitionViewModel) {
    spec = tween(durationMillis = BOUNCER_CONTENTS_ALPHA_IN_ANIMATION_DURATION)

    distance = UserActionDistance { fromContent, _, _ ->
        val fromContentSize = checkNotNull(fromContent.targetSize())
        fromContentSize.height * TO_BOUNCER_SWIPE_DISTANCE_FRACTION
    }

    fade(Bouncer.Elements.Background)
    val delayFractionOfTransition =
        viewModel.delayForPassiveAuth.inWholeMilliseconds.toFloat() /
            (BOUNCER_CONTENTS_ALPHA_IN_ANIMATION_DURATION +
                viewModel.delayForPassiveAuth.inWholeMilliseconds)
    if (viewModel.shouldDelayBouncerContent()) {
        transformation(
            Bouncer.Elements.Content,
            PassiveAuthDelayPropertyTransformationFactory(
                viewModel.delayForPassiveAuth,
                PropertyTransformation.Property.Alpha,
                initialValue = { 0f },
                targetValue = { 1f },
                twoWayConverter = Float.VectorConverter,
                transitionProgressToValue = { it.normalize(delayFractionOfTransition, 1f) },
            ),
        )

        transformation(
            Bouncer.Elements.Content,
            PassiveAuthDelayPropertyTransformationFactory(
                viewModel.delayForPassiveAuth,
                PropertyTransformation.Property.Offset,
                initialValue = { Offset(0f, y = BOUNCER_INITIAL_TRANSLATION.toPx()) },
                targetValue = { Offset(x = 0f, y = 0f) },
                YOffsetVectorConverter,
                transitionProgressToValue = {
                    Offset(
                        x = 0f,
                        y =
                            (1 -
                                FastOutSlowInEasing.transform(
                                    it.normalize(delayFractionOfTransition, 1f)
                                )) * BOUNCER_INITIAL_TRANSLATION.toPx(),
                    )
                },
            ),
        )
    } else {
        translate(Bouncer.Elements.Content, y = BOUNCER_INITIAL_TRANSLATION)
        fade(Bouncer.Elements.Content)
    }
}

const val TO_BOUNCER_FADE_FRACTION = 0.5f
const val TO_BOUNCER_SWIPE_DISTANCE_FRACTION = 0.5f
private const val BOUNCER_CONTENTS_ALPHA_IN_ANIMATION_DURATION = 250
private const val TAG = "ToBouncerTransition"
private val YOffsetVectorConverter: TwoWayConverter<Offset, AnimationVector1D> =
    TwoWayConverter(
        convertToVector = { AnimationVector1D(it.y) },
        convertFromVector = { Offset(0f, it.value) },
    )

/**
 * Creates a [CustomPropertyTransformation] that animates [transformedProperty] from [initialValue]
 * to [targetValue] after a delay of [ToBouncerTransitionViewModel.delayForPassiveAuth].
 *
 * When the transition is initiated by a swipe, the swipe could still be ongoing when the delay
 * finishes, in that case the property transformation will rely on the
 * [TransitionState.Transition.progress] with [transitionProgressToValue] and use the minimum of
 * this or the animated value.
 */
private class PassiveAuthDelayPropertyTransformationFactory<T>(
    private val delay: Duration,
    private val transformedProperty: PropertyTransformation.Property<T>,
    private val initialValue: PropertyTransformationScope.() -> T,
    private val targetValue: PropertyTransformationScope.() -> T,
    private val twoWayConverter: TwoWayConverter<T, AnimationVector1D>,
    private val transitionProgressToValue: PropertyTransformationScope.(Float) -> T,
) : Transformation.Factory {
    override fun create(): Transformation {
        return object : CustomPropertyTransformation<T> {
            var propertyAnimatable: Animatable<T, AnimationVector1D>? = null

            override fun PropertyTransformationScope.transform(
                content: ContentKey,
                element: ElementKey,
                transition: TransitionState.Transition,
                transitionScope: CoroutineScope,
            ): T {
                var animatable = propertyAnimatable
                if (animatable == null) {
                    animatable = Animatable(initialValue(), twoWayConverter)
                    propertyAnimatable = animatable
                    transitionScope.launch {
                        d { "starting passive auth delay for $property" }
                        delay(delay)
                        d { "passive auth delay ended, animating in $property bouncer contents" }
                        animatable.animateTo(
                            targetValue(),
                            tween(durationMillis = BOUNCER_CONTENTS_ALPHA_IN_ANIMATION_DURATION),
                        )
                    }
                }
                val basedOnTransitionProgress = transitionProgressToValue(transition.progress)
                val animatedValue = animatable.value
                val computedValue =
                    if (transition.isInitiatedByUserInput) {
                        min(
                            // rely on progress as well to handle slow swipes where the animatable
                            // ends and swipe is still happening
                            twoWayConverter.convertToVector(basedOnTransitionProgress).value,
                            twoWayConverter.convertToVector(animatedValue).value,
                        )
                    } else {
                        twoWayConverter.convertToVector(animatedValue).value
                    }
                val outputValue =
                    twoWayConverter.convertFromVector(AnimationVector1D(computedValue))
                d { "computedValue for $property = $computedValue, $outputValue" }
                return outputValue
            }

            override val property: PropertyTransformation.Property<T>
                get() = transformedProperty
        }
    }
}

private inline fun d(crossinline log: () -> String) {
    if (canLog) {
        Log.d(TAG, log())
    }
}

private val canLog
    get() = Log.isLoggable(TAG, Log.DEBUG) || Build.IS_ENG

private fun Float.normalize(min: Float, max: Float): Float {
    return if (max == min) {
        0f
    } else {
        ((this - min) / (max - min)).coerceIn(0f, 1f)
    }
}
