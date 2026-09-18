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

package com.android.systemui.statusbar.pipeline.shared.ui.composable

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.sp
import com.android.systemui.res.R

/** Composable for activity indicators (data in/out arrows) for a mobile or wifi icon. */
@Composable
fun ActivityIndicators(
    isActivityInVisible: Boolean,
    isActivityOutVisible: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val indicatorsHeight =
        with(LocalDensity.current) { ActivityIndicatorDimensions.IndicatorsHeightSp.toDp() }
    val endPaddingDp =
        with(LocalDensity.current) { ActivityIndicatorDimensions.EndPaddingSp.toDp() }
    Box(contentAlignment = Alignment.TopCenter, modifier = modifier.padding(end = endPaddingDp)) {
        Image(
            painter = painterResource(id = R.drawable.ic_activity_up),
            contentDescription = null,
            colorFilter = ColorFilter.tint(color, BlendMode.SrcIn),
            // The drawable embeds bottom empty space for the down arrow, so just have it fill size.
            contentScale = ContentScale.FillHeight,
            alpha = if (isActivityInVisible) 1f else ACTIVITY_OFF_ALPHA,
            modifier = Modifier.height(indicatorsHeight),
        )
        Image(
            painter = painterResource(id = R.drawable.ic_activity_down),
            contentDescription = null,
            colorFilter = ColorFilter.tint(color, BlendMode.SrcIn),
            // The drawable embeds top empty space for the up arrow, so just have it fill size.
            contentScale = ContentScale.FillHeight,
            alpha = if (isActivityOutVisible) 1f else ACTIVITY_OFF_ALPHA,
            modifier = Modifier.height(indicatorsHeight),
        )
    }
}

private object ActivityIndicatorDimensions {
    val IndicatorsHeightSp = 12.sp
    val EndPaddingSp = 2.sp
}

private const val ACTIVITY_OFF_ALPHA = 0.3f
