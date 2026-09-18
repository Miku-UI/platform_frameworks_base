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
import androidx.compose.ui.unit.dp
import com.android.compose.animation.Easings
import com.android.compose.animation.scene.TransitionBuilder
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys

fun TransitionBuilder.lockscreenToGoneWithAnimationOverLockscreenTransition() {
    spec = tween(durationMillis = 500)

    fractionRange(end = 0.3f, easing = Easings.PredictiveBack) {
        // Fade out all lock screen elements, except the status bar.
        fade(LockscreenElementKeys.Region.Upper)
        fade(LockscreenElementKeys.LockIcon)
        fade(LockscreenElementKeys.AmbientIndicationArea)
        fade(LockscreenElementKeys.Region.Lower)
        fade(LockscreenElementKeys.SettingsMenu)

        fade(LockscreenElementKeys.BehindScrim)
    }
}

fun TransitionBuilder.lockscreenToGoneTransition() {
    lockscreenToGoneWithAnimationOverLockscreenTransition()

    fractionRange(end = 0.3f, easing = Easings.PredictiveBack) {
        translate(LockscreenElementKeys.Region.Upper, y = (-48).dp)
        translate(LockscreenElementKeys.Notifications.Stack, y = (-72).dp)
    }
}

fun TransitionBuilder.aodToGoneTransition() {
    spec = tween(durationMillis = 500)

    fractionRange(end = 0.2f) { fade(LockscreenElementKeys.Root) }
}
