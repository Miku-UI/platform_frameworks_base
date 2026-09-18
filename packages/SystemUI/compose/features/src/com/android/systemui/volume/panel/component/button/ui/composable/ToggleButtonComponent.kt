/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.android.systemui.volume.panel.component.button.ui.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.systemui.volume.panel.component.button.ui.viewmodel.ButtonViewModel
import com.android.systemui.volume.panel.ui.composable.ComposeVolumePanelUiComponent
import com.android.systemui.volume.panel.ui.composable.VolumePanelComposeScope
import kotlinx.coroutines.flow.StateFlow

/** [ComposeVolumePanelUiComponent] implementing a toggleable button from a bottom row. */
class ToggleButtonComponent(
    private val viewModelFlow: StateFlow<ButtonViewModel?>,
    private val onCheckedChange: (isChecked: Boolean) -> Unit,
) : ComposeVolumePanelUiComponent {

    @Composable
    override fun VolumePanelComposeScope.Content(modifier: Modifier) {
        val viewModelByState by viewModelFlow.collectAsStateWithLifecycle()
        val viewModel = viewModelByState ?: return

        VolumePanelButton(
            label = viewModel.label,
            icon = viewModel.icon,
            isActive = viewModel.isActive,
            onClick = { onCheckedChange(!viewModel.isActive) },
            semantics = {
                role = Role.Switch
                if (viewModel.stateDescription == null) {
                    toggleableState =
                        if (viewModel.isActive) {
                            ToggleableState.On
                        } else {
                            ToggleableState.Off
                        }
                } else {
                    stateDescription = viewModel.stateDescription
                }
                contentDescription = viewModel.label
            },
            isExpandedAudioTileDetailsView = isExpandedAudioTileDetailsView,
            modifier = modifier,
        )
    }
}
