/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.settingslib.spa.widget.preference

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.settingslib.spa.framework.theme.SettingsShape
import com.android.settingslib.spa.framework.theme.SettingsSpace
import com.android.settingslib.spa.framework.theme.SettingsTheme
import com.android.settingslib.spa.framework.theme.isSpaExpressiveEnabled

@Composable
fun MainSwitchPreference(model: SwitchPreferenceModel) {
    MainSwitchPreference(model = model, modifier = Modifier)
}

@Composable
internal fun MainSwitchPreference(model: SwitchPreferenceModel, modifier: Modifier) {
    Surface(
        modifier = Modifier.padding(SettingsSpace.small1),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape =
            if (isSpaExpressiveEnabled) SettingsShape.CornerFull
            else SettingsShape.CornerExtraLarge1,
    ) {
        InternalSwitchPreference(
            title = model.title,
            modifier = modifier,
            checked = model.checked(),
            changeable = model.changeable(),
            onCheckedChange = model.onCheckedChange,
            paddingStart = if (isSpaExpressiveEnabled) SettingsSpace.medium1 else 20.dp,
            paddingEnd = SettingsSpace.small3,
            paddingVertical = if (isSpaExpressiveEnabled) SettingsSpace.small1 else 24.dp,
        )
    }
}

@Preview
@Composable
private fun MainSwitchPreferencePreview() {
    SettingsTheme {
        Column {
            MainSwitchPreference(
                object : SwitchPreferenceModel {
                    override val title = "Use Dark theme"
                    override val checked = { true }
                    override val onCheckedChange: (Boolean) -> Unit = {}
                }
            )
            MainSwitchPreference(
                object : SwitchPreferenceModel {
                    override val title = "Use Dark theme"
                    override val checked = { false }
                    override val onCheckedChange: (Boolean) -> Unit = {}
                }
            )
        }
    }
}
