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

import android.view.Gravity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.systemui.animation.Expandable
import com.android.systemui.volume.panel.component.button.ui.viewmodel.ButtonViewModel
import com.android.systemui.volume.panel.component.popup.ui.composable.VolumePanelPopup.Companion.calculateGravity
import com.android.systemui.volume.panel.ui.composable.ComposeVolumePanelUiComponent
import com.android.systemui.volume.panel.ui.composable.VolumePanelComposeScope
import kotlinx.coroutines.flow.StateFlow

/** [ComposeVolumePanelUiComponent] implementing a clickable button from a bottom row. */
class ButtonComponent(
    private val viewModelFlow: StateFlow<ButtonViewModel?>,
    private val onClick: (expandable: Expandable, horizontalGravity: Int) -> Unit,
) : ComposeVolumePanelUiComponent {

    @Composable
    override fun VolumePanelComposeScope.Content(modifier: Modifier) {
        val viewModelByState by viewModelFlow.collectAsStateWithLifecycle()
        val viewModel = viewModelByState ?: return

        val screenWidth: Float =
            with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
        var gravity by remember { mutableIntStateOf(Gravity.CENTER_HORIZONTAL) }

        VolumePanelButton(
            label = viewModel.label,
            icon = viewModel.icon,
            isActive = viewModel.isActive,
            onClick = { expandable -> onClick(expandable, gravity) },
            semantics = {
                role = Role.Button
                contentDescription = viewModel.label
                viewModel.stateDescription?.let { stateDescription = it }
            },
            isExpandedAudioTileDetailsView = isExpandedAudioTileDetailsView,
            modifier = modifier.onGloballyPositioned { gravity = calculateGravity(it, screenWidth) },
        )
    }
}
