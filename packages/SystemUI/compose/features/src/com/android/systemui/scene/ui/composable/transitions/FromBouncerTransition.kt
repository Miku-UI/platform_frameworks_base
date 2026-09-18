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

package com.android.systemui.scene.ui.composable.transitions

import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.android.compose.animation.Easings
import com.android.compose.animation.scene.TransitionBuilder
import com.android.compose.animation.scene.UserActionDistance
import com.android.systemui.bouncer.ui.composable.Bouncer

val BOUNCER_INITIAL_TRANSLATION = 48.dp

fun TransitionBuilder.fromBouncerTransition(translateUpwards: Boolean = false) {
    spec = tween(durationMillis = 500)

    distance = UserActionDistance { fromContent, _, _ ->
        val fromContentSize = checkNotNull(fromContent.targetSize())
        fromContentSize.height * TO_BOUNCER_SWIPE_DISTANCE_FRACTION
    }

    val translateDirection = if (translateUpwards) -1 else 1
    translate(Bouncer.Elements.Content, y = translateDirection * BOUNCER_INITIAL_TRANSLATION)
    fractionRange(end = TO_BOUNCER_FADE_FRACTION) { fade(Bouncer.Elements.Content) }
    fractionRange(start = TO_BOUNCER_FADE_FRACTION) { fade(Bouncer.Elements.Background) }
}

fun TransitionBuilder.fromBouncerPreview() {
    fractionRange(easing = Easings.PredictiveBack) {
        scaleDraw(Bouncer.Elements.Content, scaleY = 0.8f, scaleX = 0.8f)
    }
}
