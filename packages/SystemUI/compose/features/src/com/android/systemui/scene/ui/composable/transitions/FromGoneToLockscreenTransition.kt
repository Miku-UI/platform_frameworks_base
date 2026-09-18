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
package com.android.systemui.scene.ui.composable.transitions

import androidx.compose.animation.core.tween
import com.android.compose.animation.Easings
import com.android.compose.animation.scene.TransitionBuilder
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys

fun TransitionBuilder.goneToAodEnterFromTop() {
    spec = tween(durationMillis = 1100, easing = Easings.Linear)

    // Translation, which accounts for both burn-in movement and the "enter from top" movement,
    // happens in both [GoneToAodTransitionViewModel] and [AodBurnInViewModel]. They should
    // eventually move here.

    fractionRange(start = 0.62f) { fade(LockscreenElementKeys.Root) }
}
