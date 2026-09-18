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

package com.android.systemui.scene.ui.composable.transitions

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp
import com.android.compose.animation.scene.Edge
import com.android.compose.animation.scene.TransitionBuilder
import com.android.systemui.communal.ui.compose.Communal
import com.android.systemui.communal.ui.compose.TransitionDuration
import com.android.systemui.notifications.ui.composable.Notifications
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys
import com.android.systemui.scene.shared.model.Scenes

/** Scene transition from lockscreen to communal when triggered by a user interaction. */
fun TransitionBuilder.lockscreenToCommunalUserTransition() {
    lockscreenToCommunalTransition()

    // Translate the grid linearly so that it follows the finger movement
    translate(Communal.Elements.Grid, Edge.End)
}

/** Scene transition from lockscreen to communal when triggered by the system. */
fun TransitionBuilder.lockscreenToCommunalSystemTransition() {
    lockscreenToCommunalTransition()

    timestampRange(
        startMillis = (TransitionDuration.TO_GLANCEABLE_HUB_DURATION_MS * 0.2f).toInt(),
        easing = LinearOutSlowInEasing,
    ) {
        // Translate the grid with the same speed as the scrim fade.
        translate(Communal.Elements.Grid, Edge.End)
    }
}

private fun TransitionBuilder.lockscreenToCommunalTransition() {
    spec = tween(durationMillis = TransitionDuration.TO_GLANCEABLE_HUB_DURATION_MS)

    // Elevate the status bar so that it doesn't scale down during the transition.
    sharedElement(LockscreenElementKeys.StatusBar, elevateInContent = Scenes.Lockscreen)

    timestampRange(
        endMillis = (TransitionDuration.TO_GLANCEABLE_HUB_DURATION_MS * 0.8f).toInt(),
        easing = FastOutSlowInEasing,
    ) {
        // Lockscreen depth push
        scaleDraw(LockscreenElementKeys.Root, scaleX = 0.9f, scaleY = 0.9f)
        scaleDraw(Notifications.Elements.StackPlaceholder, scaleX = 0.9f, scaleY = 0.9f)

        // Lockscreen fade out
        fade(LockscreenElementKeys.Root)
        fade(LockscreenElementKeys.BehindScrim)

        // Status text position y translation
        translate(LockscreenElementKeys.IndicationArea, y = (-10).dp)
    }

    timestampRange(
        endMillis = (TransitionDuration.TO_GLANCEABLE_HUB_DURATION_MS * 0.2f).toInt(),
        easing = FastOutLinearInEasing,
    ) {
        // Fade out Notifications to avoid clashing with the Hub content
        fade(Notifications.Elements.StackPlaceholder)
    }
    timestampRange(
        startMillis = (TransitionDuration.TO_GLANCEABLE_HUB_DURATION_MS * 0.2f).toInt(),
        easing = LinearOutSlowInEasing,
    ) {
        // Hub background fade in
        fade(Communal.Elements.Scrim)
    }
}
