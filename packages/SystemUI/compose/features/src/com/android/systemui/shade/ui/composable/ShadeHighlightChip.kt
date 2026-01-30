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

package com.android.systemui.shade.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.android.compose.modifiers.clickableWithoutFocus
import com.android.compose.modifiers.thenIf
import com.android.systemui.shade.ui.composable.ShadeHeader.Dimensions.ChipPaddingHorizontal
import com.android.systemui.shade.ui.composable.ShadeHeader.Dimensions.ChipPaddingVertical

/** Represents the background and foreground colors of a ShadeHighlightChip. */
sealed interface ChipHighlightModel {
    val backgroundColor: Color
        @Composable @ReadOnlyComposable get

    val foregroundColor: Color
        @Composable @ReadOnlyComposable get

    val onHoveredBackgroundColor: Color
        @Composable @ReadOnlyComposable get

    data object Weak : ChipHighlightModel {
        override val backgroundColor: Color
            @Composable get() = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)

        override val foregroundColor: Color
            @Composable get() = MaterialTheme.colorScheme.onSurface

        override val onHoveredBackgroundColor: Color
            @Composable get() = backgroundColor
    }

    data object Strong : ChipHighlightModel {
        override val backgroundColor: Color
            @Composable get() = MaterialTheme.colorScheme.secondary

        override val foregroundColor: Color
            @Composable get() = MaterialTheme.colorScheme.onSecondary

        override val onHoveredBackgroundColor: Color
            @Composable get() = backgroundColor
    }

    data object Transparent : ChipHighlightModel {
        override val backgroundColor: Color
            @Composable get() = Color.Transparent

        override val foregroundColor: Color
            @Composable get() = MaterialTheme.colorScheme.onSurface

        override val onHoveredBackgroundColor: Color
            @Composable get() = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
    }
}

/** A chip with a colored highlight. Used as an entry point for the shade. */
@Composable
fun ShadeHighlightChip(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Unspecified,
    onHoveredBackgroundColor: Color = Color.Unspecified,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    onClick: () -> Unit = {},
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = horizontalArrangement,
        modifier =
            modifier
                .clip(RoundedCornerShape(25.dp))
                .clickableWithoutFocus(onClick)
                .thenIf(backgroundColor != Color.Unspecified) {
                    Modifier.background(
                            if (isHovered) {
                                onHoveredBackgroundColor
                            } else {
                                backgroundColor
                            }
                        )
                        .padding(horizontal = ChipPaddingHorizontal, vertical = ChipPaddingVertical)
                },
        content = content,
    )
}
