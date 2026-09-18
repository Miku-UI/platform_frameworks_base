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

package com.android.systemui.screencapture.common.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.systemui.common.shared.model.Icon as IconModel
import com.android.systemui.common.ui.compose.Icon

/** Data class to represent a single item in the ActionButtonGroup. */
data class ActionButtonGroupItem(val icon: IconModel?, val onClick: () -> Unit)

/** Component for a toast bar containing a list of action buttons. */
@Composable
fun PostCaptureToastBar(
    actionButtonGroup: List<ActionButtonGroupItem>,
    modifier: Modifier = Modifier,
    colors: CardColors = defaultCardColors(),
    elevation: CardElevation = defaultElevation(),
    shape: RoundedCornerShape = RoundedCornerShape(percent = 50),
) {
    Card(shape = shape, elevation = elevation, colors = colors, modifier = modifier) {
        Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            actionButtonGroup.forEach { item ->
                FilledTonalIconButton(
                    onClick = item.onClick,
                    enabled = item.icon != null,
                    colors = defaultColors(),
                    modifier = Modifier.size(48.dp),
                ) {
                    if (item.icon != null) {
                        Icon(icon = item.icon, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun defaultCardColors(): CardColors {
    return CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceBright)
}

@Composable
private fun defaultElevation(): CardElevation {
    return CardDefaults.cardElevation(defaultElevation = 8.dp)
}

@Composable
private fun defaultColors(): IconButtonColors {
    return IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondary,
    )
}
