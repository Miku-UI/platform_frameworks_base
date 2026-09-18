/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.shade.ui.composable

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import com.android.compose.modifiers.clickableWithoutFocus
import com.android.compose.modifiers.thenIf
import com.android.systemui.shade.ui.composable.ShadeHeader.Dimensions.ChipPaddingHorizontal
import com.android.systemui.shade.ui.composable.ShadeHeader.Dimensions.ChipPaddingVertical

/** Represents the colors used in a ShadeHighlightChip. */
sealed interface ChipHighlightModel {
    val backgroundColor: Color
        @Composable @ReadOnlyComposable get

    val foregroundColor: Color
        @Composable @ReadOnlyComposable get

    val hoverBackgroundColor: Color
        @Composable @ReadOnlyComposable get

    val rippleColor: Color
        @Composable @ReadOnlyComposable get

    companion object {
        /** Alpha values for the different chip states. */
        internal object Alpha {
            const val DEFAULT_HOVER = 0.89f
            const val DEFAULT_RIPPLE = 0.85f
            const val TRANSPARENT_HOVER = 0.22f
            const val TRANSPARENT_RIPPLE = 0.26f
        }
    }

    data object Weak : ChipHighlightModel {
        override val backgroundColor: Color
            @Composable get() = Color.White.copy(alpha = if (isSystemInDarkTheme()) 0.1f else 0.4f)

        override val foregroundColor: Color
            @Composable get() = if (isSystemInDarkTheme()) Color.White else Color.Black

        override val hoverBackgroundColor: Color
            @Composable get() = backgroundColor.copy(alpha = Alpha.DEFAULT_HOVER)

        override val rippleColor: Color
            @Composable get() = backgroundColor.copy(alpha = Alpha.DEFAULT_RIPPLE)
    }

    data object Strong : ChipHighlightModel {
        override val backgroundColor: Color
            @Composable get() = MaterialTheme.colorScheme.secondary

        override val foregroundColor: Color
            @Composable get() = MaterialTheme.colorScheme.onSecondary

        override val hoverBackgroundColor: Color
            @Composable get() = backgroundColor.copy(alpha = Alpha.DEFAULT_HOVER)

        override val rippleColor: Color
            @Composable get() = backgroundColor.copy(alpha = Alpha.DEFAULT_RIPPLE)
    }

    data object Transparent : ChipHighlightModel {
        override val backgroundColor: Color
            @Composable get() = Color.Transparent

        override val foregroundColor: Color
            @Composable get() = MaterialTheme.colorScheme.onSurface

        override val hoverBackgroundColor: Color
            @Composable get() = foregroundColor.copy(alpha = Alpha.TRANSPARENT_HOVER)

        override val rippleColor: Color
            @Composable get() = foregroundColor.copy(alpha = Alpha.TRANSPARENT_RIPPLE)
    }
}

/** A chip with a colored highlight. Used as an entry point for the shade. */
@Composable
fun ShadeHighlightChip(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Unspecified,
    hoverBackgroundColor: Color = Color.Unspecified,
    rippleColor: Color = Color.Unspecified,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    includePadding: Boolean = true,
    isClickable: Boolean = true,
    onClick: () -> Unit = {},
    clickTargetModifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    // Note: Intentionally assigning here instead of using `by`, which would unwrap the value and
    // cause recomposition.
    val animatedBackgroundColor =
        animateColorAsState(
            targetValue = if (isClickable && isHovered) hoverBackgroundColor else backgroundColor
        )

    // Wrapper for the rounded chip, intended to increase the click target.
    Box(
        modifier =
            if (isClickable)
                Modifier.clickableWithoutFocus(
                        interactionSource = interactionSource,
                        indication = null, // The indication is applied in the Row below instead.
                        onClick = onClick,
                    )
                    .then(clickTargetModifier)
            else clickTargetModifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = horizontalArrangement,
            modifier =
                modifier
                    .align(Alignment.CenterStart)
                    .clip(CircleShape)
                    .indication(
                        interactionSource = interactionSource,
                        indication = ripple(color = rippleColor),
                    )
                    .drawBehind {
                        val bgColor = animatedBackgroundColor.value
                        if (bgColor != Color.Unspecified) {
                            drawRoundRect(color = bgColor)
                        }
                    }
                    .thenIf(backgroundColor != Color.Unspecified && includePadding) {
                        Modifier.padding(
                            horizontal = ChipPaddingHorizontal,
                            vertical = ChipPaddingVertical,
                        )
                    }
                    .hoverable(interactionSource = interactionSource),
            content = content,
        )
    }
}
