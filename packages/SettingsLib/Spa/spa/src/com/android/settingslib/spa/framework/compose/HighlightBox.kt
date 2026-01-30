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

package com.android.settingslib.spa.framework.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.repeatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import com.android.settingslib.spa.framework.theme.isSpaExpressiveEnabled
import com.android.settingslib.spa.widget.ui.LocalIsInCategory
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

/**
 * [HighlightBox] wraps a composable item and highlights it with a background color animation.
 *
 * The highlight is typically used to indicate a search result within a list of items.
 */
@Composable
fun HighlightBox(highlightItemKey: String, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalHighlightItemKey provides highlightItemKey) {
        if (isSpaExpressiveEnabled && LocalIsInCategory.current) {
            // When in category, highlight is implemented in BaseLayout.
            content()
        } else {
            Box(modifier = Modifier.highlightBackground()) { content() }
        }
    }
}

internal val LocalHighlightItemKey = compositionLocalOf<String?> { null }

internal fun Modifier.highlightBackground(
    originalColor: Color = Color.Transparent,
    shape: Shape = RectangleShape,
): Modifier = composed {
    val highlightItemKey = LocalHighlightItemKey.current
    val navController = LocalNavController.current
    val isHighLight = rememberSaveable {
        highlightItemKey != null && navController.isHighLightItem(highlightItemKey)
    }

    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) { if (isHighLight) bringIntoViewRequester.bringIntoView() }

    val animateColor by animateBackgroundColor(isHighLight, originalColor)

    then(
        Modifier.background(color = animateColor, shape = shape)
            .bringIntoViewRequester(bringIntoViewRequester)
    )
}

/**
 * Animate background color to highlight an item.
 *
 * Same animation as com.android.settings.widget.HighlightablePreferenceGroupAdapter.
 */
@Composable
private fun animateBackgroundColor(isHighLight: Boolean, originalColor: Color): State<Color> {
    var currentHighlight by rememberSaveable { mutableStateOf(false) }
    var revertDueToTimeout by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Change the state to trigger the animation
        currentHighlight = isHighLight
        // Automatically revert to originalColor after 15 seconds
        delay(15.seconds)
        revertDueToTimeout = true
    }

    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.26f)
    return animateColorAsState(
        targetValue =
            if (currentHighlight && !revertDueToTimeout) highlightColor else originalColor,
        animationSpec =
            if (revertDueToTimeout) {
                snap()
            } else {
                repeatable(
                    iterations = 4,
                    animation = tween(durationMillis = 200),
                    repeatMode = RepeatMode.Reverse,
                )
            },
        label = "BackgroundColorAnimation",
    )
}
