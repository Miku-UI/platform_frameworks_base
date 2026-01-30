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

package com.android.systemui.notifications.ui.composable.component

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.android.compose.ui.graphics.painter.rememberDrawablePainter

@Composable
internal fun AppIcon(drawable: Drawable, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(40.dp).clip(CircleShape).background(Color.White)) {
        Image(
            painter = rememberDrawablePainter(drawable),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
internal fun LargeIcon(
    drawable: Drawable,
    modifier: Modifier = Modifier,
    maxAspectRatio: Float = 1f,
) {
    val desiredAspectRatio =
        remember(drawable, maxAspectRatio) {
            val drawableAspectRatio =
                if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                    drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight.toFloat()
                } else {
                    1f
                }
            drawableAspectRatio.coerceIn(1f, maxAspectRatio)
        }

    Image(
        painter = rememberDrawablePainter(drawable),
        contentDescription = null,
        modifier =
            modifier
                .height(48.dp)
                .width((desiredAspectRatio * 48).dp)
                .clip(RoundedCornerShape(5.dp)),
        contentScale = ContentScale.Crop,
    )
}
