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

package com.android.systemui.statusbar.shared.ui.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.ui.compose.load
import com.android.systemui.res.R

/**
 * An [Image] wrapper for status bar icons. Scales the provided [icon] to a fixed height
 * ([iconHeightDp]) while maintaining its original aspect ratio. Expects icons without embedded
 * padding.
 */
@Composable
fun StatusBarIcon(
    icon: Icon,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    // TODO(414653733): The icon size should always be the same as the battery.
    iconHeightDp: Dp = dimensionResource(R.dimen.status_bar_composable_icon_height_sp),
) {
    val contentDescription = icon.contentDescription?.load()
    val painter =
        when (icon) {
            is Icon.Loaded -> rememberDrawablePainter(icon.drawable)
            is Icon.Resource -> painterResource(icon.resId)
        }

    val intrinsicSize = painter.intrinsicSize
    val aspectRatioModifier =
        if (intrinsicSize.width > 0 && intrinsicSize.height > 0) {
            Modifier.aspectRatio(intrinsicSize.width / intrinsicSize.height)
        } else {
            // Fallback just in case we get a malformed icon with 0 width or height.
            Modifier.wrapContentWidth()
        }

    val colorFilter = if (tint == Color.Unspecified) null else ColorFilter.tint(tint)

    Image(
        painter = painter,
        contentDescription = contentDescription,
        colorFilter = colorFilter,
        modifier = modifier.height(iconHeightDp).then(aspectRatioModifier),
    )
}
