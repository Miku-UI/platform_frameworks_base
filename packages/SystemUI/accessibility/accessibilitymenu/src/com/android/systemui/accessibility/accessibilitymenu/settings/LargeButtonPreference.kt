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

package com.android.systemui.accessibility.accessibilitymenu.settings

import com.android.settingslib.metadata.SwitchPreference
import com.android.systemui.accessibility.accessibilitymenu.R

/** Preference for enabling/disabling large buttons in the accessibility menu. */
class LargeButtonPreference :
    SwitchPreference(
        key = KEY,
        title = R.string.accessibility_menu_large_buttons_title,
        summary = R.string.accessibility_menu_large_buttons_summary,
        purpose = R.string.large_button_setting_purpose,
    ) {

    // TODO (b/426597986): Update permissions and permit and sensitivity level
    companion object {
        const val KEY = "pref_large_buttons"
    }
}
