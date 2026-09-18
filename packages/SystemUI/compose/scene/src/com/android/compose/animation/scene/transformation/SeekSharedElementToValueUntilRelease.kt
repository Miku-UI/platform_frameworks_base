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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.android.compose.animation.scene.transformation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorConverter
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.lerp
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.ElementMatcher
import com.android.compose.animation.scene.TransitionBuilder
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Seek the size of the element(s) matching [matcher] to [size] while the transition is driven by
 * user input.
 *
 * Upon release, the element animates to its final size in the destination scene.
 */
fun TransitionBuilder.seekSharedElementToSizeUntilRelease(
    matcher: ElementMatcher,
    animationSpec: MotionScheme.() -> AnimationSpec<IntSize> = { defaultSpatialSpec() },
    size: PropertyTransformationScope.(fromSize: IntSize, toSize: IntSize) -> IntSize,
) {
    seekSharedElementToValueUntilRelease(
        matcher = matcher,
        property = PropertyTransformation.Property.Size,
        undefinedValue = IntSize.Zero,
        typeConverter = IntSize.VectorConverter,
        lerp = ::lerp,
        value = size,
        animationSpec = animationSpec,
    )
}

/**
 * Seek the offset of the element(s) matching [matcher] to [offset] while the transition is driven
 * by user input.
 *
 * Upon release, the element animates to its final offset in the destination scene.
 */
fun TransitionBuilder.seekSharedElementToOffsetUntilRelease(
    matcher: ElementMatcher,
    animationSpec: MotionScheme.() -> AnimationSpec<Offset> = { defaultSpatialSpec() },
    offset: PropertyTransformationScope.(fromOffset: Offset, toOffset: Offset) -> Offset,
) {
    seekSharedElementToValueUntilRelease(
        matcher = matcher,
        property = PropertyTransformation.Property.Offset,
        undefinedValue = Offset.Unspecified,
        typeConverter = Offset.VectorConverter,
        lerp = ::lerp,
        value = offset,
        animationSpec = animationSpec,
    )
}

/**
 * Seek the alpha of the element(s) matching [matcher] to [alpha] while the transition is driven by
 * user input.
 *
 * Upon release, the element animates to its final alpha in the destination scene.
 */
fun TransitionBuilder.seekSharedElementToAlphaUntilRelease(
    matcher: ElementMatcher,
    animationSpec: MotionScheme.() -> AnimationSpec<Float> = { defaultEffectsSpec() },
    alpha: PropertyTransformationScope.(fromAlpha: Float, toAlpha: Float) -> Float,
) {
    seekSharedElementToValueUntilRelease(
        matcher = matcher,
        property = PropertyTransformation.Property.Alpha,
        undefinedValue = Float.MIN_VALUE,
        typeConverter = Float.VectorConverter,
        lerp = ::lerp,
        value = alpha,
        animationSpec = animationSpec,
    )
}

private fun <T, V : AnimationVector> TransitionBuilder.seekSharedElementToValueUntilRelease(
    matcher: ElementMatcher,
    property: PropertyTransformation.Property<T>,
    undefinedValue: T,
    typeConverter: TwoWayConverter<T, V>,
    lerp: (fromValue: T, toValue: T, fraction: Float) -> T,
    value: PropertyTransformationScope.(fromValue: T, toValue: T) -> T,
    animationSpec: MotionScheme.() -> AnimationSpec<T>,
) {
    transformation(matcher) {
        object : CustomSharedPropertyTransformation<T> {
            override val property = property

            private var seekValue = undefinedValue
            private var lastFromValue = undefinedValue
            private var lastToValue = undefinedValue
            private var lastValue = undefinedValue
            private var triggerAnimatable: Animatable<T, V>? = null

            override fun PropertyTransformationScope.transform(
                element: ElementKey,
                transition: TransitionState.Transition,
                transitionScope: CoroutineScope,
                fromValue: T,
                toValue: T,
            ): T {
                return if (transition.isUserInputOngoing) {
                        seek(transition, fromValue, toValue)
                    } else {
                        trigger(
                            transition = transition,
                            transitionScope = transitionScope,
                            animationSpec = animationSpec(motionScheme),
                            fromValue = fromValue,
                            toValue = toValue,
                        )
                    }
                    .also { lastValue = it }
            }

            private fun PropertyTransformationScope.seek(
                transition: TransitionState.Transition,
                fromValue: T,
                toValue: T,
            ): T {
                if (lastFromValue != fromValue || lastToValue != toValue) {
                    lastFromValue = fromValue
                    lastToValue = toValue
                    seekValue = value(fromValue, toValue)
                    check(seekValue != undefinedValue) {
                        "seekValue of $seekValue is not supported"
                    }
                }

                return lerp(fromValue, seekValue, transition.progress)
            }

            private fun trigger(
                transition: TransitionState.Transition,
                transitionScope: CoroutineScope,
                animationSpec: AnimationSpec<T>,
                fromValue: T,
                toValue: T,
            ): T {
                val targetValue = if (transition.isAnimatingToToContent()) toValue else fromValue

                val triggerAnimatable = triggerAnimatable
                return if (triggerAnimatable != null) {
                    if (triggerAnimatable.targetValue != targetValue) {
                        transitionScope.launch {
                            triggerAnimatable.animateTo(targetValue, animationSpec)
                        }
                    }
                    triggerAnimatable.value
                } else {
                    val animatable =
                        Animatable(
                            lastValue.takeIf { it != undefinedValue } ?: targetValue,
                            typeConverter,
                        )
                    transitionScope.launch { animatable.animateTo(targetValue, animationSpec) }
                    this.triggerAnimatable = animatable
                    animatable.value
                }
            }

            private fun TransitionState.Transition.isAnimatingToToContent(): Boolean {
                val changeScene =
                    this as? TransitionState.Transition.ChangeScene
                        ?: error("Overlays are not supported yet")
                return changeScene.currentScene == changeScene.toScene
            }
        }
    }
}
