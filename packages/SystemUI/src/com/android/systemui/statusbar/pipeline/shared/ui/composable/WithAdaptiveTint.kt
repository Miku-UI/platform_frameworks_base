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

package com.android.systemui.statusbar.pipeline.shared.ui.composable

import android.graphics.Rect
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onLayoutRectChanged
import com.android.systemui.shade.ui.composable.ChipHighlightModel

/**
 * A helper composable that calculates the correct tint for UI elements.
 *
 * It manages its own bounds state and provides the calculated tint and a modifier to its content,
 * abstracting away the boilerplate of tint calculation.
 */
@Composable
fun WithAdaptiveTint(
    highlightModel: ChipHighlightModel? = null,
    isDarkProvider: (Rect) -> Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (tint: Color) -> Unit,
) {
    var bounds by remember { mutableStateOf(Rect()) }

    val tint =
        when (highlightModel) {
            null,
            ChipHighlightModel.Transparent ->
                if (isDarkProvider(bounds)) Color.White else Color.Black

            ChipHighlightModel.Strong -> highlightModel.foregroundColor

            ChipHighlightModel.Weak -> if (isDarkProvider(bounds)) Color.Black else Color.White
        }

    Box(
        propagateMinConstraints = true,
        modifier =
            modifier.onLayoutRectChanged { layoutCoordinates ->
                bounds = with(layoutCoordinates.boundsInScreen) { Rect(left, top, right, bottom) }
            },
    ) {
        content(tint)
    }
}
