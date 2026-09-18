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
import com.android.systemui.notifications.ui.composable.Notifications
import com.android.systemui.qs.shared.ui.QuickSettings
import com.android.systemui.shade.ui.composable.Shade
import com.android.systemui.shade.ui.composable.ShadeHeader
import kotlin.time.Duration.Companion.milliseconds

fun TransitionBuilder.quickSettingsToAlwaysOnDisplayTransition() {
    spec =
        tween(durationMillis = DefaultDuration.inWholeMilliseconds.toInt(), easing = Easings.Linear)

    sharedElement(Notifications.Elements.StackPlaceholder, enabled = false)

    // Fade out the Quick Settings elements first.
    fractionRange(end = 0.15f) {
        fade(ShadeHeader.Elements.ExpandedContent)
        fade(ShadeHeader.Elements.Clock)
        fade(ShadeHeader.Elements.ShadeCarrierGroup)
        fade(QuickSettings.Elements.BrightnessSlider)
        fade(QuickSettings.Elements.QuickSettingsContent)
        fade(QuickSettings.Elements.FooterActions)
        fade(Notifications.Elements.HeadsUpNotificationPlaceholder)
        fade(Shade.Elements.BackgroundScrim)
    }

    goneToAodEnterFromTop()
}

private val DefaultDuration = 1100.milliseconds
