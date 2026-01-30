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
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import com.android.settingslib.bluetooth.devicesettings.shared.model.DeviceSettingModel
import com.android.settingslib.bluetooth.devicesettings.shared.model.DeviceSettingStateModel
import com.android.settingslib.bluetooth.devicesettings.shared.model.ToggleModel
import com.android.systemui.animation.Expandable
import com.android.systemui.bluetooth.devicesettings.shared.model.toSysUiIcon
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.volume.panel.component.devicesetting.ui.viewmodel.DeviceSettingComponentViewModel
import com.android.systemui.volume.panel.component.popup.ui.composable.VolumePanelPopup
import com.android.systemui.volume.panel.component.popup.ui.composable.VolumePanelPopupDefaults
import com.android.systemui.volume.panel.component.selector.ui.composable.VolumePanelRadioButtonBar
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

class DeviceSettingPopup
@AssistedInject
constructor(
    @Assisted private val viewModelFactory: () -> DeviceSettingComponentViewModel,
    private val volumePanelPopup: VolumePanelPopup,
) {

    /** Shows a popup with the [expandable] animation. */
    fun show(expandable: Expandable, horizontalGravity: Int) {
        val gravity = horizontalGravity or Gravity.BOTTOM
        volumePanelPopup.show(expandable, gravity, body = { Body(it) })
    }

    @Composable
    @VisibleForTesting
    fun Body(dialog: SystemUIDialog) {
        val viewModel = rememberViewModel("DeviceSettingPopup#viewModel") { viewModelFactory() }
        viewModel.setting?.let { setting ->
            Box(
                modifier = Modifier.padding(horizontal = 80.dp).fillMaxWidth().wrapContentHeight(),
                contentAlignment = Alignment.Center,
            ) {
                VolumePanelPopupDefaults.Title(setting.label)
            }

            Box(
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().wrapContentHeight(),
                contentAlignment = Alignment.Center,
            ) {
                Content(dialog, setting)
            }
        }
    }

    @Composable
    private fun Content(dialog: SystemUIDialog, setting: DeviceSettingModel) {
        val togglesSetting = setting.ensureToggle()
        val toggles = togglesSetting?.toggles
        if (toggles.isNullOrEmpty()) {
            SideEffect { dialog.dismiss() }
            return
        }

        VolumePanelRadioButtonBar {
            toggles.fastForEachIndexed { index, toggleModel ->
                item(
                    isSelected = index == togglesSetting.state.selectedIndex,
                    onItemSelected = {
                        togglesSetting.updateState(
                            DeviceSettingStateModel.MultiTogglePreferenceState(index)
                        )
                    },
                    contentDescription = toggleModel.label,
                    icon = {
                        Icon(icon = toggleModel.icon.toSysUiIcon(LocalContext.current, null))
                    },
                    label = {
                        Text(
                            modifier = Modifier.basicMarquee(),
                            text = toggleModel.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = LocalContentColor.current,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    },
                )
            }
        }
    }

    @AssistedFactory
    interface Factory {

        fun create(viewModelFactory: () -> DeviceSettingComponentViewModel): DeviceSettingPopup
    }
}

private val DeviceSettingModel.label
    get() =
        when (this) {
            is DeviceSettingModel.MultiTogglePreference -> title
            is DeviceSettingModel.ActionSwitchPreference -> title
            else -> error("Unsupported setting type: $this")
        }

/**
 * Ensures that the [DeviceSettingModel] is [DeviceSettingModel.MultiTogglePreference] by converting
 * [DeviceSettingModel.ActionSwitchPreference] to [DeviceSettingModel.MultiTogglePreference] when
 * needed. Returns null when the [DeviceSettingModel] is not supported.
 */
@Composable
private fun DeviceSettingModel.ensureToggle(): DeviceSettingModel.MultiTogglePreference? =
    remember(this) {
        when (this) {
            is DeviceSettingModel.MultiTogglePreference -> this
            is DeviceSettingModel.ActionSwitchPreference -> {
                val active = 1
                val inactive = 0
                val isActive = switchState?.checked == true
                DeviceSettingModel.MultiTogglePreference(
                    isAllowedChangingState = isAllowedChangingState,
                    isActive = isActive,
                    title = title,
                    state =
                        DeviceSettingStateModel.MultiTogglePreferenceState(
                            if (isActive) active else inactive
                        ),
                    id = id,
                    cachedDevice = cachedDevice,
                    updateState = {
                        updateState?.invoke(
                            DeviceSettingStateModel.ActionSwitchPreferenceState(
                                it.selectedIndex == active
                            )
                        )
                    },
                    toggles =
                        icon?.let { icon ->
                            listOf(
                                ToggleModel(icon = icon, label = label),
                                ToggleModel(icon = icon, label = label),
                            )
                        } ?: emptyList(),
                )
            }
            else -> null
        }
    }
