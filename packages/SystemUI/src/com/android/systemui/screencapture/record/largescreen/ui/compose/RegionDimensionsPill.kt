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

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.systemui.res.R

/**
 * A composable that displays width and height dimensions in a styled "pill" shape.
 *
 * @param widthPx The width in pixels to display.
 * @param heightPx The height in pixels to display.
 * @param modifier The modifier to be applied to the composable.
 */
@Composable
fun RegionDimensionsPill(widthPx: Int, heightPx: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.wrapContentWidth(),
        shadowElevation = 4.dp,
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Text(
            text = stringResource(R.string.screen_capture_region_dimensions, widthPx, heightPx),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = MaterialTheme.typography.titleMedium.fontSize,
            fontWeight = FontWeight.Medium,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}
