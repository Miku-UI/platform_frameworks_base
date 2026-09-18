/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.systemui.display.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.systemui.res.R

/**
 * The dialog content for notifying the user about an external display connection and offering the
 * choice to start mirroring, start desktop mode, or cancel.
 *
 * This implementation replaces the legacy XML-based dialog when the
 * [com.android.systemui.Flags.FLAG_ENABLE_COMPOSE_EXTERNAL_DISPLAY_DIALOG] flag is enabled.
 */
@Composable
fun ExternalDisplayConnectionContent(
    showConcurrentDisplayInfo: Boolean,
    isDesktopModeSupported: Boolean,
    isInKioskMode: Boolean,
    onSaveChoiceChanged: (Boolean) -> Unit,
    onStartDesktopMode: () -> Unit,
    onStartMirroring: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var rememberChoice by remember { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimensionResource(R.dimen.dialog_side_padding))
                .padding(top = dimensionResource(R.dimen.dialog_top_padding)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ExternalDisplayConnectionIcon()

        ExternalDisplayConnectionTitle()

        if (!isInKioskMode) {
            RememberChoiceCheckbox(
                rememberChoice = rememberChoice,
                onRememberChoiceChanged = {
                    rememberChoice = it
                    onSaveChoiceChanged(it)
                },
            )
        }

        if (showConcurrentDisplayInfo) {
            DualDisplayWarning()
        }

        ActionButtons(
            isInKioskMode = isInKioskMode,
            isDesktopModeSupported = isDesktopModeSupported,
            onStartDesktopMode = onStartDesktopMode,
            onStartMirroring = onStartMirroring,
            onCancel = onCancel,
        )
    }
}

@Composable
private fun ExternalDisplayConnectionIcon() {
    Box(
        modifier =
            Modifier.size(dimensionResource(R.dimen.connected_display_dialog_logo_size))
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.stat_sys_connected_display),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondary,
            modifier = Modifier.fillMaxSize().padding(6.dp),
        )
    }
}

@Composable
private fun ExternalDisplayConnectionTitle() {
    Text(
        text = stringResource(R.string.connected_display_dialog_title),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun RememberChoiceCheckbox(
    rememberChoice: Boolean,
    onRememberChoiceChanged: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(vertical = 8.dp)
                .toggleable(
                    value = rememberChoice,
                    role = Role.Checkbox,
                    onValueChange = onRememberChoiceChanged,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = rememberChoice,
            onCheckedChange = null, // Handled by Row's toggleable
            modifier = Modifier.padding(start = 8.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.remember_choice),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DualDisplayWarning() {
    Text(
        text = stringResource(R.string.connected_display_dialog_dual_display_stop_warning),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun ActionButtons(
    isInKioskMode: Boolean,
    isDesktopModeSupported: Boolean,
    onStartDesktopMode: () -> Unit,
    onStartMirroring: () -> Unit,
    onCancel: () -> Unit,
) {
    val enableDesktopModeButtonVisible = !isInKioskMode && isDesktopModeSupported
    Column(modifier = Modifier.padding(top = 8.dp)) {
        if (enableDesktopModeButtonVisible) {
            Button(
                onClick = onStartDesktopMode,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
                Text(stringResource(R.string.start_desktop_mode))
            }
        }

        MirroringButton(
            firstButtonInTheList = !enableDesktopModeButtonVisible,
            onClick = onStartMirroring,
        )

        OutlinedButton(
            onClick = onCancel,
            modifier =
                Modifier.fillMaxWidth()
                    .padding(bottom = dimensionResource(R.dimen.dialog_bottom_padding)),
        ) {
            Text(stringResource(R.string.cancel))
        }
    }
}

@Composable
private fun MirroringButton(firstButtonInTheList: Boolean, onClick: () -> Unit) {
    if (firstButtonInTheList) {
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Text(stringResource(R.string.start_mirroring))
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        ) {
            Text(stringResource(R.string.start_mirroring))
        }
    }
}
