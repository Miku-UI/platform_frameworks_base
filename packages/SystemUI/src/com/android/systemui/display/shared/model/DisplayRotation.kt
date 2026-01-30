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

package com.android.systemui.display.shared.model

import android.view.Surface

/** Shadows [Surface.Rotation] for kotlin use within SysUI. */
enum class DisplayRotation {
    ROTATION_0,
    ROTATION_90,
    ROTATION_180,
    ROTATION_270,
}

fun DisplayRotation.isDefaultOrientation() =
    this == DisplayRotation.ROTATION_0 || this == DisplayRotation.ROTATION_180

/** Converts [Surface.Rotation] to corresponding [DisplayRotation] */
fun Int.toDisplayRotation(): DisplayRotation =
    when (this) {
        Surface.ROTATION_0 -> DisplayRotation.ROTATION_0
        Surface.ROTATION_90 -> DisplayRotation.ROTATION_90
        Surface.ROTATION_180 -> DisplayRotation.ROTATION_180
        Surface.ROTATION_270 -> DisplayRotation.ROTATION_270
        else -> throw IllegalArgumentException("Invalid DisplayRotation value: $this")
    }

/** Converts [DisplayRotation] to corresponding [Surface.Rotation] */
fun DisplayRotation.toRotation(): Int =
    when (this) {
        DisplayRotation.ROTATION_0 -> Surface.ROTATION_0
        DisplayRotation.ROTATION_90 -> Surface.ROTATION_90
        DisplayRotation.ROTATION_180 -> Surface.ROTATION_180
        DisplayRotation.ROTATION_270 -> Surface.ROTATION_270
    }
