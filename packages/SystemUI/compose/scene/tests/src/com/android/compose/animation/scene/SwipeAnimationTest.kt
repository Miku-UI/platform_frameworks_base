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

package com.android.compose.animation.scene

import androidx.compose.animation.SplineBasedFloatDecayAnimationSpec
import androidx.compose.animation.core.generateDecayAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SwipeAnimationTest {
    @Test
    fun animationSlowerThanDecay() {
        assertThat(
                willDecayFasterThanAnimating(
                    // High animation duration.
                    animationSpec = tween(durationMillis = 1_000),
                    decayAnimationSpec =
                        SplineBasedFloatDecayAnimationSpec(density = Density(1f))
                            .generateDecayAnimationSpec(),
                    initialOffset = 0f,
                    targetOffset = 1_000f,
                    initialVelocity = 4_000f,
                )
            )
            .isTrue()
    }

    @Test
    fun animationFasterThanDecay() {
        assertThat(
                willDecayFasterThanAnimating(
                    // Low animation duration.
                    animationSpec = tween(durationMillis = 1),
                    decayAnimationSpec =
                        SplineBasedFloatDecayAnimationSpec(density = Density(1f))
                            .generateDecayAnimationSpec(),
                    initialOffset = 0f,
                    targetOffset = 1_000f,
                    initialVelocity = 4_000f,
                )
            )
            .isFalse()
    }

    @Test
    fun sameInitialAndTargetOffset() {
        assertThat(
                willDecayFasterThanAnimating(
                    // Low animation duration.
                    animationSpec = tween(durationMillis = 1),
                    decayAnimationSpec =
                        SplineBasedFloatDecayAnimationSpec(density = Density(1f))
                            .generateDecayAnimationSpec(),
                    initialOffset = 1_000f,
                    targetOffset = 1_000f,
                    initialVelocity = 4_000f,
                )
            )
            .isTrue()
    }
}
