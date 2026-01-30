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

package com.android.systemui.scene.ui.composable.transitions

import androidx.compose.animation.core.tween
import com.android.compose.animation.scene.Edge
import com.android.compose.animation.scene.TransitionBuilder
import com.android.systemui.notifications.ui.composable.Notifications
import com.android.systemui.qs.shared.ui.QuickSettings.Elements
import com.android.systemui.qs.ui.composable.QuickSettingsScene
import com.android.systemui.shade.ui.composable.ShadeHeader
import kotlin.time.Duration.Companion.milliseconds

fun TransitionBuilder.toQuickSettingsSceneTransition(durationScale: Double = 1.0) {
    spec = tween(durationMillis = (DefaultDuration * durationScale).inWholeMilliseconds.toInt())

    val translationY = ShadeHeader.Dimensions.CollapsedHeightForTransitions
    translate(
        ShadeHeader.Elements.ExpandedContent,
        y = -(ShadeHeader.Dimensions.ExpandedHeight - translationY),
    )
    translate(ShadeHeader.Elements.Clock, y = -translationY)
    translate(ShadeHeader.Elements.ShadeCarrierGroup, y = -translationY)

    fractionRange(start = .58f) {
        fade(ShadeHeader.Elements.ExpandedContent)
        fade(ShadeHeader.Elements.Clock)
        fade(ShadeHeader.Elements.ShadeCarrierGroup)
    }

    fade(Notifications.Elements.HeadsUpNotificationPlaceholder)

    // New all compose element
    translate(Elements.QuickSettingsContent, y = -ShadeHeader.Dimensions.ExpandedHeight * .66f)
    translate(Notifications.Elements.NotificationScrim, Edge.Top, false)
    translate(QuickSettingsScene.Companion.InternalScenes.Edit.rootElementKey, Edge.Top, true)
}

private val DefaultDuration = 500.milliseconds
