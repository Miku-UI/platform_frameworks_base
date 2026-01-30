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

package com.android.systemui.volume.panel.component.devicesetting.ui.composable

import android.view.Gravity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.android.settingslib.bluetooth.devicesettings.shared.model.DeviceSettingModel
import com.android.settingslib.bluetooth.devicesettings.shared.model.DeviceSettingStateModel
import com.android.systemui.bluetooth.devicesettings.shared.model.toSysUiIcon
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.volume.panel.component.button.ui.composable.VolumePanelButton
import com.android.systemui.volume.panel.component.devicesetting.ui.viewmodel.DeviceSettingComponentViewModel
import com.android.systemui.volume.panel.component.popup.ui.composable.VolumePanelPopup.Companion.calculateGravity
import com.android.systemui.volume.panel.shared.VolumePanelLogger
import com.android.systemui.volume.panel.ui.composable.ComposeVolumePanelUiComponent
import com.android.systemui.volume.panel.ui.composable.VolumePanelComposeScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/** [ComposeVolumePanelUiComponent] that represents spatial audio button in the Volume Panel. */
class DeviceSettingComponent
@AssistedInject
constructor(
    @Assisted private val viewModelFactory: () -> DeviceSettingComponentViewModel,
    private val popupFactory: DeviceSettingPopup.Factory,
    private val volumePanelLogger: VolumePanelLogger,
) : ComposeVolumePanelUiComponent {

    @Composable
    override fun VolumePanelComposeScope.Content(modifier: Modifier) {
        val viewModel = rememberViewModel("DeviceSettingComponent#viewModel") { viewModelFactory() }
        when (val setting = viewModel.setting) {
            is DeviceSettingModel.ActionSwitchPreference -> {
                val isChecked = setting.switchState?.checked == true
                VolumePanelButton(
                    label = setting.title,
                    icon = setting.icon?.toSysUiIcon(LocalContext.current, null),
                    isActive = isChecked,
                    isEnabled = setting.isAllowedChangingState,
                    onClick = {
                        setting.updateState?.invoke(
                            DeviceSettingStateModel.ActionSwitchPreferenceState(!isChecked)
                        )
                    },
                    semanticsRole = Role.Switch,
                )
            }
            is DeviceSettingModel.MultiTogglePreference -> {
                val screenWidth: Float =
                    with(LocalDensity.current) {
                        LocalConfiguration.current.screenWidthDp.dp.toPx()
                    }
                var gravity by remember { mutableIntStateOf(Gravity.CENTER_HORIZONTAL) }
                val selectedToggle = setting.toggles[setting.state.selectedIndex]
                VolumePanelButton(
                    label = selectedToggle.label,
                    icon = selectedToggle.icon.toSysUiIcon(LocalContext.current, null),
                    isActive = setting.isActive,
                    isEnabled = setting.isAllowedChangingState,
                    onClick = { expandable ->
                        popupFactory
                            .create(viewModelFactory)
                            .show(expandable = expandable, horizontalGravity = gravity)
                    },
                    semanticsRole = Role.Button,
                    modifier =
                        modifier.onGloballyPositioned {
                            gravity = calculateGravity(it, screenWidth)
                        },
                )
            }
            else -> {
                setting?.let {
                    LaunchedEffect(it) {
                        volumePanelLogger.receivedUnsupportedDeviceSetting(it::class.simpleName)
                    }
                }
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(viewModelFactory: () -> DeviceSettingComponentViewModel): DeviceSettingComponent
    }
}
