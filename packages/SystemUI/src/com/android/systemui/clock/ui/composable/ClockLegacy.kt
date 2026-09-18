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

package com.android.systemui.clock.ui.composable

import android.graphics.Typeface
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.viewinterop.AndroidView
import com.android.compose.modifiers.thenIf
import com.android.systemui.res.R

/**
 * Composable wrapper around the legacy [com.android.systemui.statusbar.policy.Clock] view. Will be
 * replaced by the fully composable [Clock].
 */
@Composable
fun ClockLegacy(
    textColor: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    textStyle: TextStyle? = null,
) {
    val intColor = remember(textColor) { textColor.toArgb() }
    val density = LocalDensity.current
    val textPx =
        remember(textStyle, density) { textStyle?.let { with(density) { it.fontSize.toPx() } } }

    val resolver = LocalFontFamilyResolver.current
    val typefaceState =
        remember(textStyle, resolver) {
            textStyle?.let {
                resolver.resolve(
                    fontFamily = it.fontFamily,
                    fontWeight = it.fontWeight ?: FontWeight.Normal,
                    fontStyle = it.fontStyle ?: FontStyle.Normal,
                    fontSynthesis = it.fontSynthesis ?: FontSynthesis.All,
                )
            }
        }
    val letterSpacingEm =
        remember(textStyle) {
            val spacing = textStyle?.letterSpacing
            val size = textStyle?.fontSize

            if (spacing == null || !spacing.isSpecified) return@remember null

            when (spacing.type) {
                TextUnitType.Em -> spacing.value
                TextUnitType.Sp -> {
                    if (
                        size != null &&
                            size.isSpecified &&
                            size.type == TextUnitType.Sp &&
                            size.value > 0f
                    ) {
                        spacing.value / size.value
                    } else null
                }
                else -> null
            }
        }

    AndroidView(
        factory = { context ->
            val clock =
                com.android.systemui.statusbar.policy.Clock(
                    ContextThemeWrapper(context, R.style.Theme_SystemUI_DesktopStatusBar),
                    null,
                )
            // Desktop status bar handles its own padding.
            clock.setShouldApplyPadding(false)
            clock
        },
        update = { view ->
            view.setTextColor(intColor)
            textPx?.let { view.setTextSize(TypedValue.COMPLEX_UNIT_PX, it) }
            typefaceState?.value?.let { resolvedTypeface ->
                view.typeface = resolvedTypeface as? Typeface
            }
            letterSpacingEm?.let { view.letterSpacing = it }
        },
        modifier = modifier.thenIf(onClick != null) { Modifier.clickable { onClick?.invoke() } },
    )
}
