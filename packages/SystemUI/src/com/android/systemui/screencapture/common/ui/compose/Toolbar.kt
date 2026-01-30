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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.android.systemui.screencapture.common.ui.compose

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarColors
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.compose.PlatformIconButton
import com.android.systemui.res.R

/** Floating toolbar with a leading close icon to dismiss the toolbar. */
@Composable
fun Toolbar(
    expanded: Boolean,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: FloatingToolbarColors = defaultColors(),
    elevation: Dp = 2.dp,
    content: @Composable RowScope.() -> Unit,
) {
    HorizontalFloatingToolbar(
        expanded = expanded,
        modifier = modifier,
        colors = colors,
        shape = FloatingToolbarDefaults.ContainerShape,
        expandedShadowElevation = elevation,
        leadingContent = {
            PlatformIconButton(
                iconResource = R.drawable.ic_close,
                colors = IconButtonDefaults.iconButtonColors(),
                shape = IconButtonDefaults.smallSquareShape,
                contentDescription =
                    stringResource(id = R.string.underlay_close_button_content_description),
                onClick = onCloseClick,
            )
        },
        content = content,
    )
}

@Composable
private fun defaultColors(): FloatingToolbarColors {
    return FloatingToolbarDefaults.standardFloatingToolbarColors(
        toolbarContainerColor = MaterialTheme.colorScheme.surfaceBright
    )
}
