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

package com.android.settingslib.spa.gallery.restricted

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.settingslib.spa.framework.common.SettingsPageProvider
import com.android.settingslib.spa.framework.compose.navigator
import com.android.settingslib.spa.restricted.RestrictedMainSwitchPreference
import com.android.settingslib.spa.restricted.RestrictedSwitchPreference
import com.android.settingslib.spa.widget.editor.SettingsOutlinedTextField
import com.android.settingslib.spa.widget.preference.MainSwitchPreference
import com.android.settingslib.spa.widget.preference.Preference
import com.android.settingslib.spa.widget.preference.PreferenceModel
import com.android.settingslib.spa.widget.preference.SwitchPreferenceModel
import com.android.settingslib.spa.widget.scaffold.RegularScaffold
import com.android.settingslib.spa.widget.ui.Category

private const val TITLE = "Sample RestrictedSwitchPreference"

object RestrictedSwitchPreferencePageProvider : SettingsPageProvider {
    override val name = "RestrictedSwitchPreference"

    @Composable
    override fun Page(arguments: Bundle?) {
        RegularScaffold(TITLE) {
            Configs()

            SampleRestrictedMainSwitchPreference(ifBlockedOverrideCheckedTo = null)
            SampleRestrictedMainSwitchPreference(ifBlockedOverrideCheckedTo = true)
            SampleRestrictedMainSwitchPreference(ifBlockedOverrideCheckedTo = false)

            Category {
                SampleRestrictedSwitchPreference(ifBlockedOverrideCheckedTo = null)
                SampleRestrictedSwitchPreference(ifBlockedOverrideCheckedTo = true)
                SampleRestrictedSwitchPreference(ifBlockedOverrideCheckedTo = false)
            }
        }
    }

    @Composable
    fun Entry() {
        Preference(
            object : PreferenceModel {
                override val title = TITLE
                override val onClick = navigator(name)
            }
        )
    }
}

@Composable
private fun Configs() {
    val enableRestrictions by
        GalleryRestrictedRepository.enableRestrictionsFlow.collectAsStateWithLifecycle()
    val summaryOn by GalleryRestrictedRepository.summaryOnFlow.collectAsStateWithLifecycle()
    val summaryOff by GalleryRestrictedRepository.summaryOffFlow.collectAsStateWithLifecycle()
    MainSwitchPreference(
        model =
            object : SwitchPreferenceModel {
                override val title = "Enable restrictions"
                override val checked = { enableRestrictions }
                override val onCheckedChange = { newChecked: Boolean ->
                    GalleryRestrictedRepository.enableRestrictionsFlow.value = newChecked
                }
            }
    )
    SettingsOutlinedTextField(
        value = summaryOn,
        label = "Override summary on",
        onTextChange = { GalleryRestrictedRepository.summaryOnFlow.value = it },
    )
    SettingsOutlinedTextField(
        value = summaryOff,
        label = "Override summary off",
        onTextChange = { GalleryRestrictedRepository.summaryOffFlow.value = it },
    )
}

@Composable
private fun SampleRestrictedMainSwitchPreference(ifBlockedOverrideCheckedTo: Boolean?) {
    RestrictedMainSwitchPreference(
        model =
            createSwitchPreferenceModel(
                title = "RestrictedMainSwitchPreference",
                ifBlockedOverrideCheckedTo = ifBlockedOverrideCheckedTo,
            ),
        restrictions = GalleryRestrictions(isRestricted = true),
        ifBlockedOverrideCheckedTo = ifBlockedOverrideCheckedTo,
    )
}

@Composable
private fun SampleRestrictedSwitchPreference(ifBlockedOverrideCheckedTo: Boolean?) {
    RestrictedSwitchPreference(
        model =
            createSwitchPreferenceModel(
                title = "RestrictedSwitchPreference",
                ifBlockedOverrideCheckedTo = ifBlockedOverrideCheckedTo,
            ),
        restrictions = GalleryRestrictions(isRestricted = true),
        ifBlockedOverrideCheckedTo = ifBlockedOverrideCheckedTo,
    )
}

@Composable
private fun createSwitchPreferenceModel(
    title: String,
    ifBlockedOverrideCheckedTo: Boolean?,
): SwitchPreferenceModel {
    var checked by rememberSaveable { mutableStateOf(false) }
    return object : SwitchPreferenceModel {
        override val title = title
        override val summary = { "ifBlockedOverrideCheckedTo = $ifBlockedOverrideCheckedTo" }
        override val checked = { checked }
        override val onCheckedChange = { newChecked: Boolean -> checked = newChecked }
    }
}
