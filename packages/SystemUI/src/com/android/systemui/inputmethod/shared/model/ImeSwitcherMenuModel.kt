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

package com.android.systemui.inputmethod.shared.model

import android.content.Intent
import android.view.inputmethod.InputMethodManager.IMPickerEntryPoint
import com.android.internal.inputmethod.IImeSwitcherMenu

/**
 * Model for the data sent from system_server to SystemUI, to be shown in the IME Switcher Menu.
 *
 * @param items the list of IME and subtype items.
 * @param selectedImeId the ID of the selected IME or `null` if no IME is selected.
 * @param selectedSubtypeIndex the index of the selected subtype in the IME's array of subtypes, or
 *   `-1` if no subtype is selected.
 * @param selectedImeSettingsIntent the intent for the settings activity of the selected IME, or
 *   `null` if no IME is selected, or the selected IME does not have a settings activity.
 * @param isScreenLocked whether the screen is currently locked.
 * @param entryPoint the entry point where the menu was requested from.
 * @param displayId the ID of the display where the menu was requested.
 */
data class ImeSwitcherMenuModel(
    val items: List<IImeSwitcherMenu.Item>,
    val selectedImeId: String?,
    val selectedSubtypeIndex: Int,
    val selectedImeSettingsIntent: Intent?,
    val isScreenLocked: Boolean,
    @param:IMPickerEntryPoint val entryPoint: Int,
    val displayId: Int,
) {

    companion object {

        /** The value to be used when no suitable subtype was found or selected. */
        const val NOT_A_SUBTYPE_INDEX = -1
    }
}
