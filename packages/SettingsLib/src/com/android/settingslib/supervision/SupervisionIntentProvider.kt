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

import android.app.role.RoleManager
import android.app.supervision.SupervisionManager
import android.app.supervision.flags.Flags
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.annotation.RequiresPermission

/** Helper class meant to provide intent to launch supervision features. */
object SupervisionIntentProvider {
    private const val ACTION_SHOW_PARENTAL_CONTROLS = "android.settings.SHOW_PARENTAL_CONTROLS"
    private const val ACTION_SETUP_PIN_RECOVERY =
        "android.settings.supervision.action.SET_PIN_RECOVERY"
    private const val ACTION_SUPERVISION_SETTINGS = "android.settings.SUPERVISION_SETTINGS"
    private const val ACTION_VERIFY_PIN_RECOVERY =
        "android.settings.supervision.action.VERIFY_PIN_RECOVERY"
    private const val ACTION_UPDATE_PIN_RECOVERY =
        "android.settings.supervision.action.UPDATE_PIN_RECOVERY"
    private const val ACTION_SET_VERIFIED_PIN_RECOVERY =
        "android.settings.supervision.action.SET_VERIFIED_PIN_RECOVERY"
    private const val ACTION_POST_SETUP_VERIFY_PIN_RECOVERY =
        "android.settings.supervision.action.POST_SETUP_VERIFY_PIN_RECOVERY"
    private const val SETTINGS_PKG: String = "com.android.settings"
    private const val ACTION_CONFIRM_SUPERVISION_CREDENTIALS =
        "android.app.supervision.action.CONFIRM_SUPERVISION_CREDENTIALS"
    private const val EXTRA_FORCE_CONFIRMATION = "force_confirmation"

    enum class PinRecoveryAction(val action: String) {
        SET(ACTION_SETUP_PIN_RECOVERY),
        VERIFY(ACTION_VERIFY_PIN_RECOVERY),
        UPDATE(ACTION_UPDATE_PIN_RECOVERY),
        SET_VERIFIED(ACTION_SET_VERIFIED_PIN_RECOVERY),
        POST_SETUP_VERIFY(ACTION_POST_SETUP_VERIFY_PIN_RECOVERY),
    }

    /**
     * Returns an [Intent] to the supervision settings page or null if supervision is disabled or
     * the intent is not resolvable.
     */
    @JvmStatic
    fun getSettingsIntent(context: Context): Intent? {
        val supervisionManager =
            context.getSystemService(SupervisionManager::class.java) ?: return null
        val (intentAction, intentPackage) =
            if (Flags.enableSupervisionSettingsScreen()) {
                val settingsAppPackage =
                    if (supervisionManager.isSupervisionEnabled()) {
                        getSettingsAppPackage(context)
                    } else {
                        null
                    }
                ACTION_SUPERVISION_SETTINGS to settingsAppPackage
            } else {
                val supervisionAppPackage = supervisionManager.activeSupervisionAppPackage
                ACTION_SHOW_PARENTAL_CONTROLS to supervisionAppPackage
            }

        if (intentPackage == null) {
            return null
        }

        val intent =
            Intent(intentAction).setPackage(intentPackage).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val activities =
            context.packageManager.queryIntentActivitiesAsUser(intent, 0, context.userId)
        return if (activities.isNotEmpty()) intent else null
    }

    /**
     * Returns an [Intent] to the supervision pin recovery activity or null if there's no
     * [android.app.role.RoleManager.ROLE_SYSTEM_SUPERVISION] role holder or the intent is not
     * resolvable.
     */
    @RequiresPermission("android.permission.MANAGE_ROLE_HOLDERS")
    @JvmStatic
    fun getPinRecoveryIntent(context: Context, action: PinRecoveryAction): Intent? {
        val roleHolders =
            context
                .getSystemService(RoleManager::class.java)
                ?.getRoleHolders(RoleManager.ROLE_SYSTEM_SUPERVISION)
        // Supervision role is exclusive, only one app may hold this role per user.
        val supervisionAppPackage = roleHolders?.firstOrNull() ?: return null
        val intent = Intent(action.action).setPackage(supervisionAppPackage)
        val activities =
            context.packageManager.queryIntentActivitiesAsUser(intent, 0, context.userId)
        return if (activities.isNotEmpty()) intent else null
    }

    /**
     * Returns an [Intent] to confirm supervision credentials or null if the intent is not
     * resolvable.
     */
    @JvmStatic
    fun getConfirmSupervisionCredentialsIntent(context: Context): Intent? {
        val intent = Intent(ACTION_CONFIRM_SUPERVISION_CREDENTIALS).setPackage(SETTINGS_PKG)
        val activities =
            context.packageManager.queryIntentActivitiesAsUser(intent, 0, context.userId)
        return if (activities.isNotEmpty()) intent else null
    }

    /** Returns the System Settings application's package name */
    @JvmStatic
    private fun getSettingsAppPackage(context: Context): String {
        val packageManager = context.getPackageManager()
        val results =
            packageManager.queryIntentActivities(
                Intent(Settings.ACTION_SETTINGS),
                PackageManager.MATCH_SYSTEM_ONLY,
            )
        return results.firstOrNull()?.activityInfo?.packageName ?: SETTINGS_PKG
    }

    /**
     * Returns an [Intent] to confirm supervision credentials or null if the intent is not
     * resolvable.
     *
     * If [forceConfirm], user will be prompted to confirm supervision credentials regardless of
     * whether there is an active authentication session (i.e. if the supervision credentials have
     * been recently confirmed for some other purpose).
     */
    @JvmStatic
    fun getConfirmSupervisionCredentialsIntent(context: Context, forceConfirm: Boolean): Intent? {
        return getConfirmSupervisionCredentialsIntent(context)
            ?.putExtra(EXTRA_FORCE_CONFIRMATION, forceConfirm)
    }
}
