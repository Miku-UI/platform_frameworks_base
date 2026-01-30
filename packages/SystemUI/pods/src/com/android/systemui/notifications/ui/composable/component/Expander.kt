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

package com.android.systemui.notifications.ui.composable.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.compose.theme.LocalAndroidColorScheme

// TODO: b/432249649 - Once we move the compose code for bundles into a pod, we should consolidate
//  these elements with ExpansionControl used there.

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun Expander(expanded: Boolean, modifier: Modifier = Modifier, numberToShow: Int? = null) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val surfaceColor = LocalAndroidColorScheme.current.surfaceEffect3

    Box(modifier = modifier.background(surfaceColor, RoundedCornerShape(100.dp))) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 1.dp, horizontal = 5.dp),
        ) {
            val iconSizeDp = with(LocalDensity.current) { 16.sp.toDp() }

            if (numberToShow != null) {
                Text(
                    text = numberToShow.toString(),
                    style = MaterialTheme.typography.labelSmallEmphasized,
                    color = textColor,
                    modifier = Modifier.padding(end = 2.dp),
                )
            }
            Chevron(expanded, modifier = Modifier.size(iconSizeDp), color = textColor)
        }
    }
}

@Composable
private fun Chevron(expanded: Boolean, color: Color, modifier: Modifier = Modifier) {
    Icon(
        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
        contentDescription = null,
        tint = color,
        modifier = modifier,
    )
}
