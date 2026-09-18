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

package com.android.wm.shell.bubbles.appinfo

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.util.Log
import com.android.wm.shell.Flags
import com.android.wm.shell.bubbles.Bubble
import com.android.wm.shell.bubbles.BubbleController
import javax.inject.Inject

/**
 * A concrete implementation of [BubbleAppInfoProvider] that uses [PackageManager] to resolve app
 * info.
 */
class PackageManagerBubbleAppInfoProvider @Inject constructor() : BubbleAppInfoProvider {

    private companion object {
        const val TAG = "PackageManagerBubbleAppInfoProvider"
    }

    override fun resolveAppInfo(context: Context, bubble: Bubble): BubbleAppInfo? {
        // App name & app icon
        val pm = BubbleController.getPackageManagerForUser(context, bubble.user.identifier)
        try {
            // Load the name and icon from application info
            val appInfo =
                pm.getApplicationInfo(
                    bubble.packageName,
                    (PackageManager.MATCH_UNINSTALLED_PACKAGES or
                        PackageManager.MATCH_DISABLED_COMPONENTS or
                        PackageManager.MATCH_DIRECT_BOOT_UNAWARE or
                        PackageManager.MATCH_DIRECT_BOOT_AWARE),
                )
            val appName = pm.getApplicationLabel(appInfo)?.toString()
            val appIcon = appInfo.loadUnbadgedIcon(pm)

            // Try to use the name and icon from the activity info if the component is available.
            // The badge icon should still be from application info.
            if (Flags.useBubbleIconFromActivityInfo()) {
                val intent = bubble.intent
                val component = intent?.component
                if (intent != null && component != null) {
                    val activityInfo = pm.getActivityInfo(component, /* flags= */ 0)
                    val activityName = activityInfo.loadLabel(pm)?.toString()
                    val icon = getActivityInfoIcon(activityInfo, bubble.packageName)
                    val activityInfoIcon = icon?.loadDrawable(context)
                    if (activityName != null && activityInfoIcon != null) {
                        return BubbleAppInfo(
                            appName = activityName,
                            appIcon = activityInfoIcon,
                            badgeIcon = appIcon,
                            user = bubble.user,
                        )
                    }
                }
            }
            return BubbleAppInfo(
                appName = appName,
                appIcon = appIcon,
                badgeIcon = appIcon,
                user = bubble.user,
            )
        } catch (exception: PackageManager.NameNotFoundException) {
            // If we can't find package... don't think we should show the bubble.
            Log.w(TAG, "Unable to find package: ${bubble.packageName}")
            return null
        }
    }

    /**
     * Retrieves the icon for the bubble from the activity info.
     *
     * @param pm the PackageManager to use
     * @param intent the Intent of the bubble
     * @return the activity icon or null if it cannot be loaded
     */
    override fun getActivityInfoIcon(pm: PackageManager, intent: Intent?): Icon? {
        val component = intent?.component ?: return null
        val packageName = component.packageName
        try {
            val activityInfo = pm.getActivityInfo(component, /* flags= */ 0)
            return getActivityInfoIcon(activityInfo, packageName)
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving getting icon for package: $packageName", e)
        }
        return null
    }

    /**
     * Retrieves the icon for the bubble from the activity info.
     *
     * @param activityInfo the ActivityInfo to get data from
     * @param packageName the package name of the bubble'd app
     * @return the activity icon or null if it cannot be loaded
     */
    override fun getActivityInfoIcon(activityInfo: ActivityInfo, packageName: String): Icon? {
        try {
            val iconRes = activityInfo.iconResource
            if (iconRes != 0) {
                return Icon.createWithResource(packageName, iconRes)
            } else {
                val logoRes = activityInfo.logoResource
                if (logoRes != 0) {
                    return Icon.createWithResource(packageName, logoRes)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error getting activity icon for package: $packageName", e)
        }
        return null
    }
}
