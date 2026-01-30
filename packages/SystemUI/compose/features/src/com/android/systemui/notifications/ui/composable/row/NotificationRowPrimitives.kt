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

package com.android.systemui.notifications.ui.composable.row

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.LowestZIndexContentPicker
import com.android.compose.animation.scene.ValueKey
import com.android.compose.animation.scene.animateElementFloatAsState
import com.android.compose.theme.LocalAndroidColorScheme

object NotificationRowPrimitives {
    object Elements {
        val PillBackground = ElementKey("PillBackground", contentPicker = LowestZIndexContentPicker)
        val NotificationIconBackground = ElementKey("NotificationIconBackground")
        val Chevron = ElementKey("Chevron")
        val ExpandedNumber = ElementKey("ExpandedNumber")
    }

    object Values {
        val ChevronRotation = ValueKey("NotificationChevronRotation")
    }
}

/** The Icon displayed at the start of any notification row. */
@Composable
fun BundleIcon(@DrawableRes drawable: Int?, large: Boolean, modifier: Modifier = Modifier) {
    val iconBackground = LocalAndroidColorScheme.current.surfaceEffect2
    Box(
        modifier =
            if (large) {
                modifier.size(40.dp).background(color = iconBackground, shape = CircleShape)
            } else {
                modifier
                    .size(24.dp)
                    .background(
                        color = iconBackground,
                        shape = RoundedCornerShape(24.dp, 24.dp, 24.dp, 24.dp),
                    )
            }
    ) {
        if (drawable == null) return@Box
        Image(
            painter = painterResource(drawable),
            contentDescription = null,
            modifier =
                if (large) {
                    Modifier.fillMaxSize(.5f).align(Alignment.Center)
                } else {
                    Modifier.padding(2.dp).align(Alignment.Center)
                },
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
        )
    }
}

/** The ExpansionControl of any expandable notification row, containing a Chevron. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ContentScope.ExpansionControl(
    collapsed: Boolean,
    numberToShow: Int?,
    modifier: Modifier = Modifier,
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val shouldShowNumber = numberToShow != null
    Box(modifier = modifier) {
        // The background is a shared Element and therefore can't be the parent of a different
        // shared Element (the chevron), otherwise the child can't be animated.
        PillBackground(modifier = Modifier.matchParentSize())
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier.padding(
                    top = 1.dp,
                    bottom = 1.dp,
                    start = 5.dp,
                    end = if (shouldShowNumber) 3.dp else 5.dp,
                ),
        ) {
            val iconSizeDp = with(LocalDensity.current) { 16.sp.toDp() }

            if (shouldShowNumber) {
                Text(
                    text = numberToShow.toString(),
                    style = MaterialTheme.typography.labelSmallEmphasized,
                    color = textColor,
                    modifier = Modifier.element(NotificationRowPrimitives.Elements.ExpandedNumber),
                )
            }
            Chevron(collapsed = collapsed, modifier = Modifier.size(iconSizeDp), color = textColor)
        }
    }
}

@Composable
private fun ContentScope.PillBackground(modifier: Modifier = Modifier) {
    val surfaceColor = LocalAndroidColorScheme.current.surfaceEffect3
    // Needs to be a shared element so it does not overlap while animating
    ElementWithValues(NotificationRowPrimitives.Elements.PillBackground, modifier) {
        Box(
            modifier =
                Modifier.drawBehind {
                    drawRoundRect(
                        color = surfaceColor,
                        cornerRadius = CornerRadius(100.dp.toPx(), 100.dp.toPx()),
                    )
                }
        )
    }
}

@Composable
private fun ContentScope.Chevron(collapsed: Boolean, color: Color, modifier: Modifier = Modifier) {
    val key = NotificationRowPrimitives.Elements.Chevron
    ElementWithValues(key, modifier) {
        val rotation by
            animateElementFloatAsState(
                if (collapsed) 0f else 180f,
                NotificationRowPrimitives.Values.ChevronRotation,
            )
        content {
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.graphicsLayer { rotationZ = rotation },
                tint = color,
            )
        }
    }
}
