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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

sealed interface RestrictedMode

data object NoRestricted : RestrictedMode

sealed interface Blocked : RestrictedMode {
    /**
     * Represents the configuration for overriding a [RestrictedSwitchPreference] or
     * [RestrictedMainSwitchPreference] when it's in a blocked state.
     *
     * This class allows specifying whether the checked state of the preference should be overridden
     * and provides custom summaries for the 'on' and 'off' states when blocked.
     */
    data class SwitchPreferenceOverrides(
        /**
         * The summary to show when the preference is blocked and checked value is on.
         *
         * Note: Requires switch preference's param `ifBlockedOverrideCheckedTo` to be set to true.
         */
        val summaryOn: String? = null,

        /**
         * The summary to show when the preference is blocked and checked value is off.
         *
         * Note: Requires switch preference's param `ifBlockedOverrideCheckedTo` to be set to false.
         */
        val summaryOff: String? = null,
    )

    val switchPreferenceOverridesFlow: Flow<SwitchPreferenceOverrides>
        get() = flowOf(SwitchPreferenceOverrides())
}

interface BlockedWithDetails : Blocked {
    /** Show the details of this restriction, usually a dialog will be displayed. */
    fun showDetails()
}
