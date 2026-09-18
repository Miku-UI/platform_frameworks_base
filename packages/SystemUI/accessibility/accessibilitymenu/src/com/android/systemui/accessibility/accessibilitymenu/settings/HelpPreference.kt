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
import android.provider.Browser
import android.provider.Settings
import androidx.core.net.toUri
import com.android.settingslib.metadata.PreferenceAvailabilityProvider
import com.android.settingslib.metadata.preferencesapi.preconditions.PreconditionStability
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.metadata.UI_ONLY_PREFERENCE
import com.android.systemui.accessibility.accessibilitymenu.R

/** Preference for displaying help information. */
class HelpPreference : PreferenceMetadata, PreferenceAvailabilityProvider {
    override val key: String
        get() = KEY

    override val title: Int
        get() = R.string.pref_help_title

    override val purpose: Int
        get() = R.string.pref_help_purpose

    override fun tags(context: Context) = arrayOf(UI_ONLY_PREFERENCE)

    override fun intent(context: Context): Intent? {
        // Configure preference to open the help page in the default web browser.
        // If the system has no browser, hide the preference.
        val uri = context.getString(R.string.help_url).toUri()
        return Intent(Intent.ACTION_VIEW, uri).apply {
            putExtra(Browser.EXTRA_APPLICATION_ID, context.packageName)
        }
    }

    override val availabilityDescription = "Must not be during setup. The intent must resolve to an activity."

    override fun getAvailabilityStability() = PreconditionStability.UNSTABLE

    override fun isAvailable(context: Context): Boolean {
        // Do not allow access to web during setup.
        val inSetupWizard =
            Settings.Secure.getInt(
                context.getContentResolver(),
                Settings.Secure.USER_SETUP_COMPLETE,
                0,
            ) != 1
        if (inSetupWizard) {
            return false
        }

        val intent = intent(context) ?: return false
        // Verify that the intent can be resolved to an activity.
        return context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()
    }

    override val indexable: Boolean
        get() = false

    companion object {
        const val KEY = "pref_help"
    }
}
