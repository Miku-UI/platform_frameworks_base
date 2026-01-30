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

import com.android.settingslib.spa.coroutines.SpaDispatchers
import com.android.settingslib.spa.widget.preference.SwitchPreferenceModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class RestrictedSwitchPreferencePresenter(
    private val model: SwitchPreferenceModel,
    private val ifBlockedOverrideCheckedTo: Boolean?,
) {
    fun restrictedModelFlow(restrictedMode: RestrictedMode?): Flow<SwitchPreferenceModel> =
        when (restrictedMode) {
                is NoRestricted -> flowOf(model)
                is Blocked -> restrictedModelFlow(restrictedMode)
                null -> flowOf(indeterminateModel)
            }
            .conflate()
            .flowOn(SpaDispatchers.Default)

    private fun restrictedModelFlow(restrictedMode: Blocked): Flow<SwitchPreferenceModel> =
        restrictedMode.switchPreferenceOverridesFlow.map { overrides ->
            object : SwitchPreferenceModel {
                override val title = model.title

                override val summary = run {
                    val overrideSummary =
                        when (ifBlockedOverrideCheckedTo) {
                            true -> overrides.summaryOn
                            false -> overrides.summaryOff
                            else -> null
                        }
                    if (overrideSummary != null) {
                        { overrideSummary }
                    } else {
                        model.summary
                    }
                }

                override val icon = model.icon

                override val checked =
                    if (ifBlockedOverrideCheckedTo != null) {
                        { ifBlockedOverrideCheckedTo }
                    } else {
                        model.checked
                    }

                override val changeable = { false }
                override val onCheckedChange = null
            }
        }

    /**
     * The switch preference model when the restricted mode is being calculated.
     *
     * In this state, the switch is indeterminate and not changeable.
     */
    val indeterminateModel =
        object : SwitchPreferenceModel {
            override val title = model.title
            override val summary = model.summary
            override val icon = model.icon
            override val checked = { null }
            override val changeable = { false }
            override val onCheckedChange = null
        }
}
