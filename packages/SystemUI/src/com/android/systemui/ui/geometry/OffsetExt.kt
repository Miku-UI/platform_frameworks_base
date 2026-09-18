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

package com.android.systemui.ui.geometry

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Coerces the [Offset] to stay inside the given [Rect] */
fun Offset.coerceIn(rect: Rect): Offset {
    val xOffset =
        when {
            x < rect.left -> rect.left
            x > rect.right -> rect.right
            else -> x
        }
    val yOffset =
        when {
            y < rect.top -> rect.top
            y > rect.bottom -> rect.bottom
            else -> y
        }
    return Offset(xOffset, yOffset)
}

/**
 * Rotates the given [Offset] around the origin by the given angle in degrees.
 *
 * A positive angle indicates a counterclockwise rotation around the right-handed 2D Cartesian
 * coordinate system.
 *
 * See: [Rotation matrix](https://en.wikipedia.org/wiki/Rotation_matrix)
 */
fun Offset.rotateBy(angleInDegrees: Float): Offset {
    val angleInRadians: Float = angleInDegrees * (PI.toFloat() / 180)
    return Offset(
        x = x * cos(angleInRadians) - y * sin(angleInRadians),
        y = x * sin(angleInRadians) + y * cos(angleInRadians),
    )
}
