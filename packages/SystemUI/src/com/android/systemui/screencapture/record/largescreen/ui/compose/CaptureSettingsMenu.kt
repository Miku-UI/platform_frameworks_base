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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ui.compose.LoadingIcon
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.record.largescreen.ui.viewmodel.PreCaptureViewModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CaptureSettingsMenu(viewModel: PreCaptureViewModel) {
    val recordParameters = viewModel.screenCaptureRecordParametersViewModel
    val isScreenRecording = viewModel.captureType == ScreenCaptureType.RECORDING
    val icons = viewModel.icons

    val settingsButtonContentDescription =
        stringResource(R.string.screen_capture_toolbar_settings_button_a11y)

    Box {
        var showMenu by remember { mutableStateOf(false) }
        IconToggleButton(
            checked = showMenu,
            onCheckedChange = { showMenu = it },
            shape = IconButtonDefaults.smallSquareShape,
            modifier =
                Modifier.semantics { this.contentDescription = settingsButtonContentDescription },
        ) {
            LoadingIcon(viewModel.icons?.moreOptions)
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            offset = DpOffset(x = 0.dp, y = 28.dp),
            shape = RoundedCornerShape(28.dp),
        ) {
            SettingsMenuItem(
                text = stringResource(R.string.screen_capture_show_clicks_and_keys),
                leadingIcon = { icons?.showClicks?.let { Icon(icon = it) } },
                checked = recordParameters.shouldShowTaps ?: false,
                onCheckedChange = { recordParameters.setShouldShowTaps(it) },
                enabled = isScreenRecording,
            )
            SettingsMenuItem(
                text = stringResource(R.string.screen_capture_device_audio),
                leadingIcon = { icons?.deviceAudio?.let { Icon(icon = it) } },
                checked = recordParameters.shouldRecordDevice,
                onCheckedChange = { recordParameters.shouldRecordDevice = it },
                enabled = isScreenRecording,
            )
            SettingsMenuItem(
                text = stringResource(R.string.screen_capture_microphone_audio),
                leadingIcon = { icons?.microphone?.let { Icon(icon = it) } },
                checked = recordParameters.shouldRecordMicrophone,
                onCheckedChange = { recordParameters.shouldRecordMicrophone = it },
                enabled = isScreenRecording,
            )
            SettingsMenuItem(
                text = stringResource(R.string.screen_capture_front_camera),
                leadingIcon = { icons?.frontCamera?.let { Icon(icon = it) } },
                checked = recordParameters.shouldShowFrontCamera ?: false,
                onCheckedChange = { recordParameters.setShouldShowFrontCamera(it) },
                enabled = true,
            )
        }
    }
}

@Composable
private fun SettingsMenuItem(
    text: String,
    leadingIcon: @Composable (() -> Unit)?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
) {
    DropdownMenuItem(
        text = { Text(text) },
        onClick = { onCheckedChange(!checked) },
        leadingIcon =
            leadingIcon?.let {
                {
                    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        it()
                    }
                }
            },
        trailingIcon = {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        },
        enabled = enabled,
    )
}
