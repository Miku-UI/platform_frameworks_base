/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.statusbar.notification.row.icon

import android.content.Context
import android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
import android.graphics.drawable.Drawable
import android.os.UserHandle
import com.android.launcher3.util.UserIconInfo
import com.android.settingslib.Utils
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.shade.ShadeDisplayAware
import javax.inject.Inject

/** Helper interface for [AppIconProviderImpl] that can be overridden in tests. */
interface AppIconHelper {
    /** Get the unstyled, unbadged icon corresponding to the given package and user. */
    fun getUnbadgedIcon(packageName: String, userHandle: UserHandle): Drawable?

    /** Look up the user to determine if it is a profile, and if so which badge to use. */
    fun getUserIconInfo(userHandle: UserHandle): UserIconInfo
}

@SysUISingleton
class AppIconHelperImpl @Inject constructor(@ShadeDisplayAware private val sysuiContext: Context) :
    AppIconHelper {

    override fun getUnbadgedIcon(packageName: String, userHandle: UserHandle): Drawable? {
        val pm = sysuiContext.packageManager
        val userId = userHandle.identifier
        return pm.getApplicationInfoAsUser(packageName, MATCH_UNINSTALLED_PACKAGES, userId)
            .loadUnbadgedIcon(pm)
    }

    override fun getUserIconInfo(userHandle: UserHandle): UserIconInfo =
        Utils.fetchUserIconInfo(sysuiContext, userHandle)
}
