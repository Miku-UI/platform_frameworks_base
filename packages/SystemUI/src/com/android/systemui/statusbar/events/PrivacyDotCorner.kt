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

package com.android.systemui.statusbar.events

import android.view.DisplayCutout.BOUNDS_POSITION_BOTTOM
import android.view.DisplayCutout.BOUNDS_POSITION_LEFT
import android.view.DisplayCutout.BOUNDS_POSITION_RIGHT
import android.view.DisplayCutout.BOUNDS_POSITION_TOP
import android.view.Gravity
import android.view.Surface

/** Represents a corner on the display for the privacy dot. */
enum class PrivacyDotCorner(
    val index: Int,
    val gravity: Int,
    val innerGravity: Int,
    val title: String,
    val alignedBound1: Int,
    val alignedBound2: Int,
) {
    TopLeft(
        index = 0,
        gravity = Gravity.TOP or Gravity.LEFT,
        innerGravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT,
        title = "TopLeft",
        alignedBound1 = BOUNDS_POSITION_TOP,
        alignedBound2 = BOUNDS_POSITION_LEFT,
    ),
    TopRight(
        index = 1,
        gravity = Gravity.TOP or Gravity.RIGHT,
        innerGravity = Gravity.CENTER_VERTICAL or Gravity.LEFT,
        title = "TopRight",
        alignedBound1 = BOUNDS_POSITION_TOP,
        alignedBound2 = BOUNDS_POSITION_RIGHT,
    ),
    BottomRight(
        index = 2,
        gravity = Gravity.BOTTOM or Gravity.RIGHT,
        innerGravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT,
        title = "BottomRight",
        alignedBound1 = BOUNDS_POSITION_BOTTOM,
        alignedBound2 = BOUNDS_POSITION_RIGHT,
    ),
    BottomLeft(
        index = 3,
        gravity = Gravity.BOTTOM or Gravity.LEFT,
        innerGravity = Gravity.CENTER_VERTICAL or Gravity.LEFT,
        title = "BottomLeft",
        alignedBound1 = BOUNDS_POSITION_BOTTOM,
        alignedBound2 = BOUNDS_POSITION_LEFT,
    ),
}

fun PrivacyDotCorner.rotatedCorner(@Surface.Rotation rotation: Int): PrivacyDotCorner {
    var modded = index - rotation
    if (modded < 0) {
        modded += 4
    }
    return PrivacyDotCorner.entries[modded]
}
