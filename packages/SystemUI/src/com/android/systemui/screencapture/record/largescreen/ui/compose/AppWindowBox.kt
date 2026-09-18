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

package com.android.systemui.screencapture.record.largescreen.ui.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.android.systemui.screencapture.common.ui.compose.ScreenCaptureColors
import com.android.systemui.screencapture.record.largescreen.shared.model.AppWindowModel

@Composable
fun AppWindowBox(appWindowModel: AppWindowModel?, modifier: Modifier = Modifier) {
    val scrimColor = ScreenCaptureColors.scrimColor
    Box(modifier = modifier.fillMaxSize().background(color = scrimColor)) {
        appWindowModel?.let { model ->
            val maskColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
            val cornerRadius = 16.dp
            val density = LocalDensity.current
            val cornerRadiusPx = with(density) { cornerRadius.toPx() }

            Canvas(modifier = Modifier.fillMaxSize()) {
                // Draw the main rounded rect for the selected window
                val taskBounds = model.taskBounds
                drawRoundRect(
                    color = maskColor,
                    topLeft = Offset(taskBounds.left.toFloat(), taskBounds.top.toFloat()),
                    size = Size(taskBounds.width().toFloat(), taskBounds.height().toFloat()),
                    cornerRadius = CornerRadius(cornerRadiusPx),
                )

                // Cut out the overlapping rectangles
                model.overlappingBounds.forEach { overlappingRect ->
                    drawRoundRect(
                        color = scrimColor,
                        topLeft =
                            Offset(overlappingRect.left.toFloat(), overlappingRect.top.toFloat()),
                        size =
                            Size(
                                overlappingRect.width().toFloat(),
                                overlappingRect.height().toFloat(),
                            ),
                        cornerRadius = CornerRadius(cornerRadiusPx),
                        blendMode = BlendMode.Src,
                    )
                }
            }
        }
    }
}
