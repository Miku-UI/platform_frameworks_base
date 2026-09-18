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

package com.android.compose.animation

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisibilityThresholdsTest {
    @Test
    fun withVisibilityThreshold_updatesSpringSpec() {
        val original = spring<Float>(stiffness = 500f)
        val threshold = 0.5f
        val updated = original.withVisibilityThreshold(threshold) as SpringSpec<Float>

        assertThat(updated.visibilityThreshold).isEqualTo(threshold)
        assertThat(updated.stiffness).isEqualTo(500f)
    }

    @Test
    fun withSpatialThreshold_offset_isCorrectAndNonZero() {
        val spec = spring<Offset>().withSpatialThreshold() as SpringSpec<Offset>
        val threshold = spec.visibilityThreshold

        assertThat(threshold?.x).isEqualTo(0.5f)
        assertThat(threshold?.y).isEqualTo(0.5f)
    }

    @Test
    fun withSpatialThreshold_intRect_usesOnePixel() {
        val spec = spring<IntRect>().withSpatialThreshold() as SpringSpec<IntRect>
        val threshold = spec.visibilityThreshold

        assertThat(threshold?.left).isEqualTo(1)
        assertThat(threshold?.top).isEqualTo(1)
        assertThat(threshold?.right).isEqualTo(1)
        assertThat(threshold?.bottom).isEqualTo(1)
    }

    @Test
    fun withSpatialThreshold_dp_scalesWithDensity() {
        val density = Density(density = 2.0f)
        val spec = spring<Dp>().withSpatialThreshold(density) as SpringSpec<Dp>

        // 0.5px / 2.0 density = 0.25dp
        assertThat(spec.visibilityThreshold?.value).isEqualTo(0.25f)
    }

    @Test
    fun withVisibilityThreshold_ignoresNonSpringSpecs() {
        val tweenSpec = tween<Float>(durationMillis = 300)
        val result = tweenSpec.withVisibilityThreshold(0.5f)

        assertThat(result).isSameInstanceAs(tweenSpec)
    }
}
