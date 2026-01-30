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

package com.android.settingslib.spa.restricted

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import com.android.settingslib.spa.widget.preference.MainSwitchPreference
import com.android.settingslib.spa.widget.preference.SwitchPreferenceModel

/**
 * Restricted version [MainSwitchPreference].
 *
 * @param ifBlockedOverrideCheckedTo if this is not null and the current [RestrictedMode] is
 *   [Blocked], the switch's checked status will be overridden to this value.
 */
@Composable
fun RestrictedMainSwitchPreference(
    model: SwitchPreferenceModel,
    restrictions: Restrictions,
    ifBlockedOverrideCheckedTo: Boolean? = null,
) {
    val repository = rememberRestrictedRepository()
    RestrictedMainSwitchPreference(model, restrictions, repository, ifBlockedOverrideCheckedTo)
}

@VisibleForTesting
@Composable
internal fun RestrictedMainSwitchPreference(
    model: SwitchPreferenceModel,
    restrictions: Restrictions,
    repository: RestrictedRepository,
    ifBlockedOverrideCheckedTo: Boolean? = null,
) {
    RestrictedBaseSwitchPreference(
        model = model,
        restrictions = restrictions,
        repository = repository,
        ifBlockedOverrideCheckedTo = ifBlockedOverrideCheckedTo,
    ) { model, modifier ->
        MainSwitchPreference(model, modifier)
    }
}
