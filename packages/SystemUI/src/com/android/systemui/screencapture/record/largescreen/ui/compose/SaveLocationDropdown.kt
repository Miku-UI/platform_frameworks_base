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

import android.os.Environment
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.android.compose.modifiers.padding
import com.android.systemui.common.shared.model.Icon as IconModel
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ui.compose.loadIcon
import com.android.systemui.screencapture.record.largescreen.ui.viewmodel.PreCaptureToolbarViewModel

/**
 * A dropdown for selecting the save location of screen recordings.
 *
 * @param viewModel The [PreCaptureToolbarViewModel] to handle save location logic.
 * @param onClose Callback to be invoked when the dropdown is closed.
 * @param modifier The modifier for the dropdown.
 */
@Composable
fun SaveLocationDropdown(
    viewModel: PreCaptureToolbarViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemHeight = 48.dp
    val shape = RoundedCornerShape(itemHeight / 2)
    var expanded by remember { mutableStateOf(false) }
    var buttonWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.onSizeChanged { buttonWidth = with(density) { it.width.toDp() } },
            shape = shape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors =
                ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val folderIcon by
                    loadIcon(
                        viewModel = viewModel,
                        resId = R.drawable.ic_screen_capture_folder,
                        contentDescription = null,
                    )
                folderIcon?.let { Icon(icon = it, modifier = Modifier.size(32.dp).padding(4.dp)) }

                Column(modifier = Modifier.weight(1f)) {
                    ProvideTextStyle(value = MaterialTheme.typography.labelMedium) {
                        Text(text = stringResource(R.string.screenshot_save_to))
                        Text(
                            text = viewModel.currentSaveLocationString,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(),
                        )
                    }
                }

                val downArrowIcon by
                    loadIcon(
                        viewModel = viewModel,
                        resId = R.drawable.ic_arrow_down_24dp,
                        contentDescription = null,
                    )
                downArrowIcon?.let { Icon(icon = it) }
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
            modifier = Modifier.width(buttonWidth).padding(vertical = { -8.dp.roundToPx() }),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val folderIcon by
                    loadIcon(
                        viewModel = viewModel,
                        resId = R.drawable.ic_arrow_down_24dp,
                        contentDescription = null,
                    )
                val checkmarkIcon by
                    loadIcon(
                        viewModel = viewModel,
                        resId = R.drawable.ic_check_expressive,
                        contentDescription = null,
                    )
                Item(
                    label = Environment.DIRECTORY_SCREENSHOTS,
                    selected = !viewModel.isCustomSaveLocationActive,
                    onSelected = {
                        viewModel.setCustomSaveLocationActiveStatus(false)
                        expanded = false
                    },
                    leadingIcon = folderIcon,
                    selectedIcon = checkmarkIcon,
                    shape = shape,
                    modifier = Modifier.height(itemHeight),
                )

                viewModel.customSaveLocationDisplayName?.let { displayName ->
                    Item(
                        label = displayName,
                        selected = viewModel.isCustomSaveLocationActive,
                        onSelected = {
                            viewModel.setCustomSaveLocationActiveStatus(true)
                            expanded = false
                        },
                        leadingIcon = folderIcon,
                        selectedIcon = checkmarkIcon,
                        shape = shape,
                        modifier = Modifier.height(itemHeight),
                    )
                }

                val newFolderIcon by
                    loadIcon(
                        viewModel = viewModel,
                        resId = R.drawable.ic_screen_capture_create_new_folder,
                        contentDescription = null,
                    )
                Item(
                    label = stringResource(R.string.screenshot_select_folder),
                    selected = false,
                    onSelected = {
                        expanded = false
                        onClose()
                        viewModel.requestLaunchDirectoryPicker()
                    },
                    leadingIcon = newFolderIcon,
                    selectedIcon = checkmarkIcon,
                    shape = shape,
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
    onSelected: () -> Unit,
    leadingIcon: IconModel.Loaded?,
    selectedIcon: IconModel.Loaded?,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                modifier = Modifier.basicMarquee(),
            )
        },
        onClick = onSelected,
        enabled = true,
        leadingIcon = {
            leadingIcon?.let {
                Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    Icon(icon = it)
                }
            }
        },
        trailingIcon = {
            selectedIcon?.let {
                if (selected) {
                    Icon(icon = it)
                }
            }
        },
        contentPadding = PaddingValues(12.dp, 12.dp, 16.dp, 12.dp),
        modifier = modifier.clip(shape),
    )
}
