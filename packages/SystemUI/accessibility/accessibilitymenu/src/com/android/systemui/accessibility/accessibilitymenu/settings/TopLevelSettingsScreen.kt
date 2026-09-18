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

import android.content.Context
import android.content.Intent
import androidx.fragment.app.Fragment
import com.android.settingslib.metadata.PreferenceHierarchy
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.metadata.preferenceHierarchy
import com.android.settingslib.preference.PreferenceScreenCreator
import com.android.systemui.accessibility.accessibilitymenu.Flags
import com.android.systemui.accessibility.accessibilitymenu.R
import com.android.systemui.accessibility.accessibilitymenu.activity.A11yMenuSettingsActivity
import com.android.systemui.accessibility.accessibilitymenu.activity.A11yMenuSettingsActivity.A11yMenuPreferenceFragment
import kotlinx.coroutines.CoroutineScope

/** Top-level settings screen for the Accessibility Menu. */
class TopLevelSettingsScreen : PreferenceScreenCreator {

    override val key: String
        get() = KEY

    override val title: Int
        get() = R.string.accessibility_menu_settings_name

    override val purpose: Int
        get() = R.string.accessibility_menu_top_level_settings_purpose

    override fun fragmentClass(): Class<out Fragment>? = A11yMenuPreferenceFragment::class.java

    override fun isFlagEnabled(context: Context): Boolean {
        return Flags.catalystA11yMenu()
    }

    override fun getLaunchIntent(context: Context, metadata: PreferenceMetadata?): Intent? {
        return Intent(context, A11yMenuSettingsActivity::class.java).apply {
            action = Intent.ACTION_MAIN
        }
    }

    override fun getPreferenceHierarchy(
        context: Context,
        coroutineScope: CoroutineScope,
    ): PreferenceHierarchy =
        preferenceHierarchy(context) {
            +LargeButtonPreference()
            +HelpPreference()
        }

    companion object {
        const val KEY = "top_level_settings_screen"
    }
}
