/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.keyguard.ui.composable.modifier

import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.android.systemui.keyguard.shared.model.BurnInModel.Companion.MAX_LARGE_CLOCK_SCALE
import com.android.systemui.keyguard.ui.viewmodel.BurnInMovementState

/**
 * Modifies the composable to account for anti-burn in translation, alpha, and scaling.
 *
 * Please override [isClock] as `true` if the composable is an element that's part of a clock.
 */
fun Modifier.burnInAware(movement: BurnInMovementState, isClock: Boolean): Modifier {
    return this.graphicsLayer {
        this.translationX = if (isClock) 0f else movement.translation.x
        this.translationY = movement.translation.y

        val scale =
            when {
                !isClock -> 1f
                movement.scale.scaleClockOnly -> movement.scale.scale
                else -> MAX_LARGE_CLOCK_SCALE
            }
        this.scaleX = scale
        this.scaleY = scale
    }
}
