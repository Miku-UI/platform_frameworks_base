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

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.IntRect
import com.android.compose.animation.SpringVisibilityThresholds.SpatialThreshold

/**
 * Returns a copy with the [visibilityThreshold] applied.
 *
 * Ensures spring animations end as soon as the remaining change is no longer visually perceivable.
 * Has no effect if this [AnimationSpec] is not a [SpringSpec].
 */
fun <T> AnimationSpec<T>.withVisibilityThreshold(visibilityThreshold: T): AnimationSpec<T> {
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

/**
 * Returns a copy with a visibility threshold suitable for animating spatial **pixel values**.
 *
 * Ensures spring animations end as soon as the remaining change is no longer visually perceivable.
 * Has no effect if this [AnimationSpec] is not a [SpringSpec].
 */
@JvmName("withSpatialThresholdFloat")
fun AnimationSpec<Float>.withSpatialThreshold() = withVisibilityThreshold(SpatialThreshold)

/**
 * Returns a copy with a visibility threshold suitable for animating spatial **[Dp] values**.
 *
 * Ensures spring animations end as soon as the remaining change is no longer visually perceivable.
 * Has no effect if this [AnimationSpec] is not a [SpringSpec].
 */
@JvmName("withSpatialThresholdDp")
fun AnimationSpec<Dp>.withSpatialThreshold(density: Density) =
    with(density) { SpatialThreshold.toDp() }.let { withVisibilityThreshold(it) }

/**
 * Returns a copy with a visibility threshold suitable for animating spatial **pixel values**.
 *
 * Ensures spring animations end as soon as the remaining change is no longer visually perceivable.
 * Has no effect if this [AnimationSpec] is not a [SpringSpec].
 */
@JvmName("withSpatialThresholdOffset")
fun AnimationSpec<Offset>.withSpatialThreshold() =
    SpatialThreshold.let { withVisibilityThreshold(Offset(it, it)) }

/**
 * Returns a copy with a visibility threshold suitable for animating spatial **[Dp] values**.
 *
 * Ensures spring animations end as soon as the remaining change is no longer visually perceivable.
 * Has no effect if this [AnimationSpec] is not a [SpringSpec].
 */
@JvmName("withSpatialThresholdDpOffset")
fun AnimationSpec<DpOffset>.withSpatialThreshold(density: Density) =
    with(density) { SpatialThreshold.toDp() }.let { withVisibilityThreshold(DpOffset(it, it)) }

/**
 * Returns a copy with a visibility threshold suitable for animating spatial **pixel values**.
 *
 * Ensures spring animations end as soon as the remaining change is no longer visually perceivable.
 * Has no effect if this [AnimationSpec] is not a [SpringSpec].
 */
@JvmName("withSpatialThresholdRect")
fun AnimationSpec<Rect>.withSpatialThreshold() =
    SpatialThreshold.let { withVisibilityThreshold(Rect(it, it, it, it)) }

/**
 * Returns a copy with a visibility threshold suitable for animating spatial **pixel values**.
 *
 * Ensures spring animations end as soon as the remaining change is no longer visually perceivable.
 * Has no effect if this [AnimationSpec] is not a [SpringSpec].
 */
@JvmName("withSpatialThresholdIntRect")
fun AnimationSpec<IntRect>.withSpatialThreshold() = withVisibilityThreshold(IntRect(1, 1, 1, 1))

/**
 * Returns a copy with a visibility threshold suitable for animating spatial **[Dp] values**.
 *
 * Ensures spring animations end as soon as the remaining change is no longer visually perceivable.
 * Has no effect if this [AnimationSpec] is not a [SpringSpec].
 */
@JvmName("withSpatialThresholdDpRect")
fun AnimationSpec<DpRect>.withSpatialThreshold(density: Density) =
    with(density) { SpatialThreshold.toDp() }
        .let { withVisibilityThreshold(DpRect(it, it, it, it)) }

object SpringVisibilityThresholds {
    /** Default threshold for effect springs. */
    const val EffectsThreshold = Spring.DefaultDisplacementThreshold
    /** Default threshold for spatial springs animating pixels. */
    const val SpatialThreshold = 0.5f
}
