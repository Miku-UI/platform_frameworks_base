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

package com.android.settingslib.supervision

import android.app.admin.DeviceAdminReceiver
import android.app.admin.EnforcingAdmin
import android.app.admin.RoleAuthority
import android.app.role.RoleManager
import android.app.supervision.SupervisionAppService
import android.app.supervision.SupervisionManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.UserHandle
import android.util.Log
import com.android.settingslib.RestrictedLockUtils.EnforcedAdmin

/** Helper class for supervision-enforced restrictions. */
object SupervisionRestrictionsHelper {

    /**
     * Creates an instance of [EnforcedAdmin] that uses the correct supervision component or returns
     * null if supervision is not enabled.
     */
    @JvmStatic
    fun createEnforcedAdmin(
        context: Context,
        restriction: String,
        user: UserHandle,
    ): EnforcedAdmin? {
        if (!supervisionEnabled(context, user.identifier)) {
            return null
        }
        return EnforcedAdmin(getSupervisionComponent(context, user.identifier), restriction, user)
    }

    /**
     * Creates an instance of [EnforcingAdmin] that uses the correct supervision component or
     * returns null if supervision is not enabled.
     */
    @JvmStatic
    fun createEnforcingAdmin(context: Context, user: UserHandle): EnforcingAdmin? {
        if (!supervisionEnabled(context, user.identifier)) {
            return null
        }
        val componentName = getSupervisionComponent(context, user.identifier)
        val packageName = componentName?.packageName ?: ""
        return EnforcingAdmin(
            packageName,
            RoleAuthority(setOf(RoleManager.ROLE_SYSTEM_SUPERVISION)),
            user,
            componentName,
        )
    }

    private fun supervisionEnabled(context: Context, userId: Int): Boolean {
        val supervisionManager = context.getSystemService(SupervisionManager::class.java)
        return supervisionManager?.isSupervisionEnabledForUser(userId) == true
    }

    private fun getSupervisionComponent(context: Context, userId: Int): ComponentName? {
        val supervisionManager = context.getSystemService(SupervisionManager::class.java)
        val supervisionAppPackage = supervisionManager?.activeSupervisionAppPackage ?: return null
        var supervisionComponent: ComponentName? = null

        // Try to find the service whose package matches the active supervision app.
        val resolveSupervisionApps =
            context.packageManager.queryIntentServicesAsUser(
                Intent(SupervisionAppService.ACTION_SUPERVISION_APP_SERVICE),
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS,
                userId,
            )
        resolveSupervisionApps
            .mapNotNull { it.serviceInfo?.componentName }
            .find { it.packageName == supervisionAppPackage }
            ?.let { supervisionComponent = it }

        if (supervisionComponent == null) {
            // Try to find the PO receiver whose package matches the active supervision app, for
            // backwards compatibility.
            val resolveDeviceAdmins =
                context.packageManager.queryBroadcastReceiversAsUser(
                    Intent(DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED),
                    PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS,
                    userId,
                )
            resolveDeviceAdmins
                .mapNotNull { it.activityInfo?.componentName }
                .find { it.packageName == supervisionAppPackage }
                ?.let { supervisionComponent = it }
        }

        if (supervisionComponent == null) {
            Log.d(SupervisionLog.TAG, "Could not find the supervision component.")
        }
        return supervisionComponent
    }
}
