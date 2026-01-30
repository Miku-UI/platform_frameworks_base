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

package com.android.systemui.notifications.ui.composable.row

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.internal.R
import com.android.systemui.initOnBackPressedDispatcherOwner
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.statusbar.notification.row.ui.viewmodel.BundleHeaderGutsViewModel

fun createBundleHeaderGutsComposeView(context: Context): ComposeView {
    return ComposeView(context).apply {
        repeatWhenAttached {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                initOnBackPressedDispatcherOwner(this@repeatWhenAttached.lifecycle)
            }
        }
    }
}

@Composable
fun BundleHeaderGuts(viewModel: BundleHeaderGutsViewModel, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 16.dp)) {
        TopRow(viewModel)
        ContentRow(viewModel)
        BottomRow(viewModel)
    }
}

@Composable
private fun TopRow(viewModel: BundleHeaderGutsViewModel, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(vertical = 16.dp),
    ) {
        BundleIcon(viewModel.bundleIcon, large = true, modifier = Modifier.padding(end = 16.dp))
        Text(
            text = stringResource(viewModel.titleText),
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.primary,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )

        Image(
            imageVector = Icons.Default.Settings,
            contentDescription =
                stringResource(com.android.systemui.res.R.string.accessibility_long_click_tile),
            modifier =
                Modifier.size(24.dp)
                    .clickable(
                        onClick = viewModel.onSettingsClicked,
                        indication = null,
                        interactionSource = null,
                    ),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun ContentRow(viewModel: BundleHeaderGutsViewModel, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .background(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(size = 20.dp),
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .minimumInteractiveComponentSize()
                .clickable { viewModel.switchState = !viewModel.switchState }
                .semantics(mergeDescendants = true) {
                    role = Role.Switch
                    toggleableState =
                        if (viewModel.switchState) ToggleableState.On else ToggleableState.Off
                },
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text =
                    stringResource(
                        com.android.systemui.res.R.string.notification_guts_bundle_title
                    ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
            Text(
                text = stringResource(viewModel.summaryText),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
            )
        }

        Switch(
            checked = viewModel.switchState,
            onCheckedChange = null, // handled at the Row level above
            thumbContent =
                if (viewModel.switchState) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    }
                } else {
                    {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    }
                },
        )
    }
}

@Composable
private fun BottomRow(viewModel: BundleHeaderGutsViewModel, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(vertical = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.dismiss_action),
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier =
                modifier
                    .padding(vertical = 13.dp)
                    .clickable(
                        onClick = { viewModel.onDismissClicked() },
                        indication = null,
                        interactionSource = null,
                    ),
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = stringResource(viewModel.getDoneOrApplyButtonText()),
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier =
                modifier
                    .padding(vertical = 13.dp)
                    .clickable(
                        onClick = { viewModel.onDoneOrApplyClicked() },
                        indication = null,
                        interactionSource = null,
                    ),
        )
    }
}
