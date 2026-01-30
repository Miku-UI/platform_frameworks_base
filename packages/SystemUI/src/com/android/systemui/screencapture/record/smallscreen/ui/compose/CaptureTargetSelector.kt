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

package com.android.systemui.screencapture.record.smallscreen.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import com.android.compose.modifiers.padding
import com.android.compose.modifiers.thenIf
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ui.compose.LoadingIcon
import com.android.systemui.screencapture.common.ui.compose.loadIcon
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModel

@Composable
fun <T> CaptureTargetSelector(
    items: List<T>?,
    selectedItemIndex: Int,
    onItemSelected: (Int) -> Unit,
    viewModel: DrawableLoaderViewModel,
    modifier: Modifier = Modifier,
    itemToString: @Composable (T) -> String = { it.toString() },
    isItemEnabled: @Composable (T) -> Boolean = { true },
) {
    val itemHeight = 56.dp
    val width = 272.dp
    val shape = RoundedCornerShape(itemHeight / 2)
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.width(width)) {
        TextButton(
            onClick = { expanded = true },
            shape = shape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors =
                ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            contentPadding = PaddingValues(16.dp),
        ) {
            if (!items.isNullOrEmpty()) {
                Text(
                    text = itemToString(items[selectedItemIndex]),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).basicMarquee(),
                )
                LoadingIcon(
                    loadIcon(
                            viewModel = viewModel,
                            resId = R.drawable.ic_arrow_down_24dp,
                            contentDescription = null,
                        )
                        .value
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            shape = shape,
            // -1.dp guarantees overlap with the TextButton border. Otherwise the dialog doesn't
            // fully cover its top
            offset = DpOffset(0.dp, -itemHeight - 1.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            onDismissRequest = { expanded = false },
            // DropdownMenu adds unavoidable vertical padding to the content. This offsets it
            modifier = Modifier.width(width).padding(vertical = { -8.dp.roundToPx() }),
        ) {
            items ?: return@DropdownMenu
            items.fastForEachIndexed { index, item ->
                Item(
                    label = itemToString(item),
                    selected = index == selectedItemIndex,
                    onSelected = {
                        expanded = false
                        onItemSelected(index)
                    },
                    enabled = isItemEnabled(item),
                    shape = shape,
                    viewModel = viewModel,
                    modifier = Modifier.height(itemHeight),
                )
            }
        }
    }
}

@Composable
private fun Item(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onSelected: () -> Unit,
    viewModel: DrawableLoaderViewModel,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val selectedBackgroundColor = MaterialTheme.colorScheme.secondaryContainer
    val contentColor =
        if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                modifier = Modifier.basicMarquee(),
            )
        },
        onClick = onSelected,
        enabled = enabled,
        trailingIcon =
            if (selected) {
                {
                    LoadingIcon(
                        loadIcon(
                                viewModel = viewModel,
                                resId = R.drawable.ic_check_expressive,
                                contentDescription = null,
                            )
                            .value
                    )
                }
            } else {
                null
            },
        colors =
            MenuDefaults.itemColors(
                textColor = contentColor,
                trailingIconColor = contentColor,
                leadingIconColor = contentColor,
            ),
        modifier =
            modifier.clip(shape).thenIf(selected) {
                Modifier.background(color = selectedBackgroundColor, shape = shape)
            },
    )
}
