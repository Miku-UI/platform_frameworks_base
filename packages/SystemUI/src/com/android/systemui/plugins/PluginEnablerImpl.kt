/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.systemui.plugins

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.edit
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.shared.plugins.PluginEnabler
import com.android.systemui.shared.plugins.PluginEnabler.DisableReason
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PluginEnablerImpl
@Inject
constructor(
    @Application private val hostContext: Context,
    private val packageManager: PackageManager = hostContext.packageManager,
) : PluginEnabler {
    private val autoDisabledPrefs: SharedPreferences =
        hostContext.getSharedPreferences(CRASH_DISABLED_PLUGINS_PREF_FILE, Context.MODE_PRIVATE)

    override fun setEnabled(component: ComponentName) {
        setDisabled(component, DisableReason.ENABLED)
    }

    override fun setDisabled(component: ComponentName, reason: DisableReason) {
        val enabled = reason == DisableReason.ENABLED
        val desiredState =
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED

        packageManager.setComponentEnabledSetting(
            component,
            desiredState,
            PackageManager.DONT_KILL_APP,
        )
        autoDisabledPrefs.edit {
            val key = component.flattenToString()
            if (enabled) remove(key) else putInt(key, reason.value)
        }
    }

    override fun isEnabled(component: ComponentName): Boolean {
        try {
            return (packageManager.getComponentEnabledSetting(component) !=
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        } catch (ex: IllegalArgumentException) {
            Log.e(TAG, "Package Manager Exception", ex)
            return false
        }
    }

    override fun getDisableReason(componentName: ComponentName): DisableReason {
        if (isEnabled(componentName)) return DisableReason.ENABLED
        return DisableReason.fromValue(
            autoDisabledPrefs.getInt(
                componentName.flattenToString(),
                DisableReason.DISABLED_UNKNOWN.value,
            )
        )
    }

    companion object {
        private const val TAG = "PluginEnablerImpl"
        private const val CRASH_DISABLED_PLUGINS_PREF_FILE = "auto_disabled_plugins_prefs"
    }
}
