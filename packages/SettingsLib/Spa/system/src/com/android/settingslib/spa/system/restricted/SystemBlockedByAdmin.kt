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

package com.android.settingslib.spa.system.restricted

import android.app.admin.DevicePolicyManager
import android.app.admin.DevicePolicyResourcesManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import com.android.settingslib.spa.flow.broadcastReceiverFlow
import com.android.settingslib.spa.restricted.Blocked.SwitchPreferenceOverrides
import com.android.settingslib.spa.restricted.BlockedWithDetails
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

internal class SystemBlockedByAdmin(private val context: Context, private val key: String) :
    BlockedWithDetails {

    private val devicePolicyResourcesManager: DevicePolicyResourcesManager? =
        context.getSystemService(DevicePolicyManager::class.java)?.resources

    override val switchPreferenceOverridesFlow: Flow<SwitchPreferenceOverrides> =
        context
            .broadcastReceiverFlow(
                IntentFilter(DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED)
            )
            .map {}
            .onStart { emit(Unit) }
            .map { switchPreferenceOverrides() }

    private fun switchPreferenceOverrides() =
        SwitchPreferenceOverrides(
            summaryOn =
                devicePolicyResourcesManager?.getString(ENABLED_BY_ADMIN_SWITCH_SUMMARY) { null },
            summaryOff =
                devicePolicyResourcesManager?.getString(DISABLED_BY_ADMIN_SWITCH_SUMMARY) { null },
        )

    override fun showDetails() {
        context.startActivity(
            Intent(Settings.ACTION_SHOW_ADMIN_SUPPORT_DETAILS).apply {
                putExtra(DevicePolicyManager.EXTRA_RESTRICTION, key)
            }
        )
    }

    companion object {
        const val ENABLED_BY_ADMIN_SWITCH_SUMMARY = "Settings.ENABLED_BY_ADMIN_SWITCH_SUMMARY"
        const val DISABLED_BY_ADMIN_SWITCH_SUMMARY = "Settings.DISABLED_BY_ADMIN_SWITCH_SUMMARY"
    }
}
