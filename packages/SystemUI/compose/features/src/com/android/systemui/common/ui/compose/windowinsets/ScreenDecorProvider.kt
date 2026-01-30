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

package com.android.systemui.common.ui.compose.windowinsets

import android.content.Context
import android.graphics.Point
import android.view.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.internal.policy.ScreenDecorationsUtils

/**
 * The bounds and [CutoutLocation] of the current display.
 *
 * This is provided as a [State] and not as a simple [DisplayCutout] as the cutout is calculated
 * from insets and can change after recomposition but before layout. If a plain DisplayCutout was
 * provided and the value was read during recomposition, it would result in a frame using the wrong
 * value after new insets are received.
 */
val LocalDisplayCutout: ProvidableCompositionLocal<() -> DisplayCutout> = staticCompositionLocalOf {
    { DisplayCutout() }
}

/** The corner radius in px of the current display. */
val LocalScreenCornerRadius = staticCompositionLocalOf { 0.dp }

@Composable
fun ScreenDecorProvider(windowInsets: () -> WindowInsets?, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalScreenCornerRadius provides rememberScreenCornerRadius(),
        LocalDisplayCutout provides rememberDisplayCutout(windowInsets),
    ) {
        content()
    }
}

@Composable
fun rememberScreenCornerRadius(): Dp {
    val context = LocalContext.current
    val screenCornerRadiusPx =
        remember(context.display.uniqueId) { ScreenDecorationsUtils.getWindowCornerRadius(context) }
    return with(LocalDensity.current) { screenCornerRadiusPx.toDp() }
}

@Composable
fun rememberDisplayCutout(windowInsets: () -> WindowInsets?): () -> DisplayCutout {
    val context = LocalContext.current
    return remember(windowInsets, context) {
        val cutoutState = derivedStateOf { windowInsets().toCutout(context) }
        ({ cutoutState.value })
    }
}

private fun WindowInsets?.toCutout(context: Context): DisplayCutout {
    val boundingRect = this?.displayCutout?.boundingRectTop
    val width = boundingRect?.let { boundingRect.right - boundingRect.left } ?: 0
    val left = boundingRect?.left ?: 0
    val top = boundingRect?.top ?: 0
    val right = boundingRect?.right ?: 0
    val bottom = boundingRect?.bottom ?: 0
    val location =
        when {
            width <= 0 -> CutoutLocation.NONE
            left <= 0 -> CutoutLocation.LEFT
            right >= getDisplayWidth(context) -> CutoutLocation.RIGHT
            else -> CutoutLocation.CENTER
        }
    val viewDisplayCutout = this?.displayCutout
    return DisplayCutout(left, top, right, bottom, location, viewDisplayCutout)
}

// TODO(b/298525212): remove once Compose exposes window inset bounds.
private fun getDisplayWidth(context: Context): Int {
    val point = Point()
    checkNotNull(context.display).getRealSize(point)
    return point.x
}
