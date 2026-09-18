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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.android.systemui.scene.ui.composable.transitions

import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.geometry.Offset
import com.android.compose.animation.Easings
import com.android.compose.animation.scene.Edge
import com.android.compose.animation.scene.TransitionBuilder
import com.android.compose.animation.scene.transformation.seekSharedElementToOffsetUntilRelease
import com.android.systemui.notifications.ui.composable.Notifications
import com.android.systemui.qs.shared.ui.QuickSettings
import com.android.systemui.shade.ui.composable.Shade
import com.android.systemui.shade.ui.composable.ShadeHeader
import kotlin.time.Duration.Companion.milliseconds

fun TransitionBuilder.toSingleShadeTransition(
    singleShadeMarginHorizontalPx: Float,
    durationScale: Double = 1.0,
    seekAnimation: Boolean = false,
) {
    spec = tween(durationMillis = (DefaultDuration * durationScale).inWholeMilliseconds.toInt())

    val isLargeScreenPortrait = singleShadeMarginHorizontalPx > 0

    fractionRange(end = .5f) {
        fade(ShadeHeader.Elements.Clock)
        fade(ShadeHeader.Elements.CollapsedContentStart)
        fade(ShadeHeader.Elements.CollapsedContentEnd)
        fade(ShadeHeader.Elements.PrivacyChip)
    }

    fractionRange(start = .58f) {
        fade(QuickSettings.Elements.SplitShadeQuickSettings)
        fade(QuickSettings.Elements.FooterActions)
        if (isLargeScreenPortrait) {
            fade(QuickSettings.Elements.QuickQuickSettingsAndMedia)
            fade(Notifications.Elements.NotificationScrim)
            fade(Notifications.Elements.StackPlaceholder)
        }
    }

    fade(Shade.Elements.BackgroundScrim)

    if (!isLargeScreenPortrait) {
        translate(Notifications.Elements.NotificationScrim, Edge.Top, false)
        fractionRange(start = .42f, easing = Easings.Legacy) {
            fade(Notifications.Elements.StackPlaceholder)
        }
    }

    if (seekAnimation) {
        seekSharedElementToOffsetUntilRelease(
            matcher = Notifications.Elements.StackPlaceholder,
            offset = { fromOffset, toOffset -> Offset(0f, maxOf(fromOffset.y, toOffset.y)) },
        )
    }
}

private val DefaultDuration = 500.milliseconds
