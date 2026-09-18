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

package com.android.systemui.statusbar.quickactions.screenrecord.ui.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ui.compose.PrimaryButton
import com.android.systemui.statusbar.quickactions.screenrecord.ui.viewmodel.LargeScreenStopRecordingPopupViewModel2
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Displays a popup for screen recording configurations. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LargeScreenStopRecordingPopup(
    viewModel: LargeScreenStopRecordingPopupViewModel2,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = FloatingToolbarDefaults.ContainerShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 24.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.height(64.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val coroutineScope = rememberCoroutineScope()
            var buttonJob: Job? by remember { mutableStateOf(null) }
            StopRecordingButton(
                onClick = {
                    if (buttonJob == null) {
                        buttonJob =
                            coroutineScope.launch {
                                viewModel.onStopButtonTapped()
                                buttonJob = null
                            }
                    }
                },
                modifier = Modifier.height(40.dp),
            )
        }
    }
}

@Composable
private fun StopRecordingButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    PrimaryButton(
        onClick = onClick,
        text = stringResource(R.string.screenrecord_stop_label),
        icon = Icon.Resource(resId = R.drawable.ic_stop, contentDescription = null),
        contentPadding = PaddingValues(horizontal = 14.dp),
        iconPadding = 4.dp,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        modifier = modifier,
    )
}
