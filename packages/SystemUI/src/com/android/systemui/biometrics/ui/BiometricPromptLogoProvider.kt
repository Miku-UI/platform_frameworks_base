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

package com.android.systemui.biometrics.ui

import android.app.ActivityTaskManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.util.Log
import com.android.launcher3.icons.IconProvider
import com.android.systemui.biometrics.Utils
import com.android.systemui.biometrics.Utils.isSystem
import com.android.systemui.biometrics.domain.model.BiometricPromptRequest
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.res.R
import javax.inject.Inject

private const val TAG = "BiometricLogoProvider"

/**
 * Encapsulates the logic for resolving the correct Logo and Description for a Biometric Prompt.
 * Handles fallbacks between the specific request logo, Activity icon, and App icon.
 */
class BiometricPromptLogoProvider
@Inject
constructor(
    @Application private val context: Context,
    private val iconProvider: IconProvider,
    private val activityTaskManager: ActivityTaskManager,
    private val packageManager: PackageManager,
) {
    /** Resolves the correct lock icon (Personal, Work, or Supervised). */
    fun getLockIcon(userId: Int): Drawable {
        val id =
            if (Utils.isManagedProfile(context, userId)) {
                R.drawable.auth_dialog_enterprise
            } else if (Utils.isSupervisingProfile(context, userId)) {
                com.android.internal.R.drawable.ic_account_child_invert
            } else {
                R.drawable.auth_dialog_lock
            }

        return context.resources.getDrawable(id, context.theme)
    }

    /**
     * The order of getting logo icon/description is:
     * 1. If the app sets customized icon/description, use the passed-in value
     * 2. If shouldUseActivityLogo(), use activityInfo to get icon/description
     * 3. Otherwise, use applicationInfo to get icon/description
     */
    fun getLogoInfo(request: BiometricPromptRequest): Pair<Drawable?, String> {
        // If the app sets customized icon/description, use the passed-in value directly
        val customizedIcon: Drawable? =
            request.logoBitmap?.let { BitmapDrawable(context.resources, request.logoBitmap) }
        var icon = customizedIcon
        var label = request.logoDescription ?: ""
        if (icon != null && label.isNotEmpty()) {
            return Pair(icon, label)
        }

        // Use activityInfo if shouldUseActivityLogo() is true
        val componentName = request.getComponentNameForLogo()
        if (componentName != null && context.shouldUseActivityLogo(componentName)) {
            val activityInfo = getActivityInfo(componentName)
            if (activityInfo != null) {
                icon = icon ?: iconProvider.getIcon(activityInfo)
                label = label.ifEmpty { activityInfo.loadLabel(packageManager).toString() }
            }
        }
        // Use applicationInfo for other cases
        if (icon == null || label.isEmpty()) {
            val appInfo = request.getApplicationInfo(componentName)
            if (appInfo != null) {
                icon = icon ?: packageManager.getApplicationIcon(appInfo)
                label = label.ifEmpty { packageManager.getApplicationLabel(appInfo).toString() }
            } else {
                Log.w(TAG, "Cannot find app logo for package ${request.opPackageName}")
            }
        }

        // Add user badge for non-customized logo icon
        val userHandle = UserHandle.of(request.userInfo.userId)
        if (icon != null && icon != customizedIcon) {
            icon = packageManager.getUserBadgedIcon(icon, userHandle)
        }

        return Pair(icon, label)
    }

    private fun BiometricPromptRequest.getComponentNameForLogo(): ComponentName? {
        val topActivity: ComponentName? = activityTaskManager.getTasks(1).firstOrNull()?.topActivity
        return when {
            componentNameForConfirmDeviceCredentialActivity != null ->
                componentNameForConfirmDeviceCredentialActivity
            topActivity?.packageName.contentEquals(opPackageName) -> topActivity
            else -> {
                Log.w(TAG, "Top activity $topActivity is not the client $opPackageName")
                null
            }
        }
    }

    private fun BiometricPromptRequest.getApplicationInfo(
        componentNameForLogo: ComponentName?
    ): ApplicationInfo? {
        val packageName =
            when {
                componentNameForLogo != null -> componentNameForLogo.packageName
                // TODO(b/353597496): We should check whether |allowBackgroundAuthentication| should
                // be
                // removed.
                // This is being consistent with the check in [AuthController.showDialog()].
                allowBackgroundAuthentication || isSystem(context, opPackageName) -> opPackageName
                else -> null
            }
        return if (packageName == null) {
            Log.w(TAG, "Cannot find application info for $opPackageName")
            null
        } else {
            try {
                context.packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_ANY_USER,
                )
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(TAG, "Cannot find application info for $opPackageName", e)
                null
            }
        }
    }

    private fun Context.shouldUseActivityLogo(componentName: ComponentName): Boolean {
        return resources.getStringArray(R.array.config_useActivityLogoForBiometricPrompt).find {
            componentName.packageName.contentEquals(it)
        } != null
    }

    private fun getActivityInfo(componentName: ComponentName): ActivityInfo? =
        try {
            packageManager.getActivityInfo(componentName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Cannot find activity info", e)
            null
        }
}
