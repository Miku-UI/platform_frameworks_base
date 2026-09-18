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

package com.android.systemui.screencapture.record.smallscreen.ui.compose

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ui.compose.LoadingIcon
import com.android.systemui.screencapture.common.ui.compose.loadIcon
import com.android.systemui.screencapture.record.smallscreen.ui.viewmodel.RecordDetailsColorPickerViewModel

@Composable
fun RecordDetailsColorPicker(
    viewModel: RecordDetailsColorPickerViewModel,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = modifier.horizontalScroll(rememberScrollState()).height(56.dp),
        ) {
            LoadingIcon(
                icon =
                    loadIcon(viewModel, R.drawable.ic_selfie_expressive, contentDescription = null)
                        .value,
                modifier = Modifier.size(48.dp).padding(14.dp),
            )
            for (color in viewModel.availableCameraColors) {
                RecordDetailsColorItem(
                    color = Color(color),
                    selected = color == viewModel.cameraColor,
                    modifier =
                        Modifier.size(48.dp).clip(CircleShape).clickable {
                            viewModel.onCameraColorClicked(color)
                        },
                )
            }
        }
    }
}

@Composable
fun RecordDetailsColorItem(
    color: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    rimColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val selectedOutlineStrokeWidth = 2.dp
    val deselectedOutlineStrokeWidth = 0.5.dp
    val selectedState = remember { MutableTransitionState(false) }
    selectedState.targetState = selected
    val selectedTransition = rememberTransition(selectedState)
    val colorCircleRadius: Dp by
        selectedTransition.animateDp {
            if (it) {
                13.dp
            } else {
                10.dp - deselectedOutlineStrokeWidth / 2
            }
        }
    val backgroundCircleRadius: Dp by
        selectedTransition.animateDp {
            if (it) {
                16.dp - selectedOutlineStrokeWidth / 2
            } else {
                10.dp
            }
        }
    Canvas(modifier = modifier) {
        drawCircle(
            color = rimColor,
            radius = backgroundCircleRadius.toPx(),
            // deselected background is overlapped by the color circle, so we want to draw
            // behind it to avoid border antialiasing artifacts
            style = Stroke(width = selectedOutlineStrokeWidth.toPx()),
        )
        drawCircle(color = color, radius = colorCircleRadius.toPx())
    }
}
