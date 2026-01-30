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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.systemui.screencapture.common.ui.compose.RadioButtonGroup
import com.android.systemui.screencapture.common.ui.compose.RadioButtonGroupItem
import com.android.systemui.screencapture.common.ui.compose.Toolbar
import com.android.systemui.screencapture.record.largescreen.ui.viewmodel.PreCaptureViewModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PreCaptureToolbar(
    viewModel: PreCaptureViewModel,
    expanded: Boolean,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val captureTypeButtonItems =
        viewModel.captureTypeButtonViewModels.map {
            RadioButtonGroupItem(
                label = it.label,
                icon = it.icon,
                isSelected = it.isSelected,
                onClick = it.onClick,
                contentDescription = it.contentDescription,
            )
        }

    val captureRegionButtonItems =
        viewModel.captureRegionButtonViewModels.map {
            RadioButtonGroupItem(
                icon = it.icon,
                isSelected = it.isSelected,
                onClick = it.onClick,
                contentDescription = it.contentDescription,
            )
        }

    Toolbar(expanded = expanded, onCloseClick = onCloseClick, modifier = modifier) {
        Row {
            if (viewModel.screenRecordingSupported) {
                CaptureSettingsMenu(viewModel)
            }

            Spacer(Modifier.size(8.dp))

            RadioButtonGroup(items = captureRegionButtonItems)

            if (viewModel.screenRecordingSupported) {
                Spacer(Modifier.size(16.dp))

                RadioButtonGroup(items = captureTypeButtonItems)
            }
        }
    }
}
