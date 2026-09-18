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
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.UserHandle
import com.android.wm.shell.bubbles.Bubble

/** Resolves app info for bubbles. */
interface BubbleAppInfoProvider {
    /** Resolves app info for the bubble. Returns `null` if the app could not be resolved. */
    fun resolveAppInfo(context: Context, bubble: Bubble): BubbleAppInfo?

    /**
     * Retrieves the icon for the bubble from the activity info.
     *
     * @param pm the PackageManager to use
     * @param intent the Intent of the bubble
     * @return the activity icon or null if it cannot be loaded
     */
    fun getActivityInfoIcon(pm: PackageManager, intent: Intent?): Icon?

    /**
     * Retrieves the icon for the bubble from the activity info.
     *
     * @param activityInfo the ActivityInfo to get data from
     * @param packageName the package name of the bubble'd app
     * @return the activity icon or null if it cannot be loaded
     */
    fun getActivityInfoIcon(activityInfo: ActivityInfo, packageName: String): Icon?
}

/** Data object for the resolved app info. */
data class BubbleAppInfo(
    val appName: String?,
    val appIcon: Drawable,
    val badgeIcon: Drawable,
    val user: UserHandle,
)
