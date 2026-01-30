/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.settingslib.spa.widget.dialog

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.DialogProperties
import com.android.settingslib.spa.framework.theme.SettingsSize

@Composable
fun SettingsAlertDialogWithIcon(
    onDismissRequest: () -> Unit,
    confirmButton: AlertDialogButton?,
    dismissButton: AlertDialogButton?,
    title: String?,
    icon: ImageVector = Icons.Default.WarningAmber,
    text: @Composable (() -> Unit)?,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(SettingsSize.medium1),
            )
        },
        modifier = Modifier.width(getDialogWidth()),
        confirmButton = { confirmButton?.let { Button(it) } },
        dismissButton = dismissButton?.let { { OutlinedButton(it) } },
        title = title?.let { { CenterRow { SettingsAlertDialogTitle(it) } } },
        text = text?.let { { SettingsAlertDialogText(text) } },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    )
}

@Composable
private fun Button(button: AlertDialogButton) {
    Button(onClick = { button.onClick() }) { Text(button.text) }
}

@Composable
private fun OutlinedButton(button: AlertDialogButton) {
    OutlinedButton(onClick = { button.onClick() }) { Text(button.text) }
}
