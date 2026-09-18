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

package com.android.systemui.activity.data.repository

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import java.util.concurrent.Executor

/**
 * Repository to fetch information about activity intents. A modernized replacement of
 * [com.android.systemui.ActivityIntentHelper] so flows and coroutines can be used.
 */
interface ActivityIntentRepository {
    /**
     * Determines if the given [intent] resolves to an Activity which is allowed to appear above the
     * lock screen. This is a modern replacement for
     * [com.android.systemui.ActivityIntentHelper.wouldShowOverLockscreen].
     *
     * Once the value is fetched, [callback] will be invoked on the given [executor]. The callback
     * will be provided with [callbackParameters] as well as a boolean. The boolean will be true if
     * the launched Activity would appear above the lock screen and false otherwise.
     */
    fun <T> wouldPendingIntentShowOverLockscreen(
        intent: PendingIntent,
        currentUserId: Int,
        executor: Executor,
        callbackParameters: T,
        callback: WouldPendingIntentShowOverLockscreenCallback<T>,
    )

    companion object {
        /** Returns true if the given activity info can show over lockscreen. */
        @JvmStatic
        fun ActivityInfo?.wouldActivityInfoShowOverLockscreen(): Boolean {
            return this != null &&
                (this.flags and
                    (ActivityInfo.FLAG_SHOW_WHEN_LOCKED or ActivityInfo.FLAG_SHOW_FOR_ALL_USERS)) >
                    0
        }

        /** Returns the flags that should be used to query activity intent information. */
        @JvmStatic
        fun getTargetActivityInfoFlags(onlyDirectBootAware: Boolean): Int {
            var flags = PackageManager.MATCH_DEFAULT_ONLY or PackageManager.GET_META_DATA
            if (!onlyDirectBootAware) {
                flags =
                    flags or
                        (PackageManager.MATCH_DIRECT_BOOT_AWARE or
                            PackageManager.MATCH_DIRECT_BOOT_UNAWARE)
            }
            return flags
        }

        /** Converts the given list of [ResolveInfo] to the single most relevant [ActivityInfo]. */
        @JvmStatic
        fun List<ResolveInfo>.toActivityInfo(
            intent: Intent,
            flags: Int,
            currentUserId: Int,
            packageManager: PackageManager,
        ): ActivityInfo? {
            if (this.isEmpty()) {
                return null
            }
            if (this.size == 1) {
                return this[0].activityInfo
            }
            val resolved: ResolveInfo? =
                packageManager.resolveActivityAsUser(intent, flags, currentUserId)
            return if (resolved == null || wouldLaunchResolverActivity(resolved, appList = this)) {
                null
            } else {
                resolved.activityInfo
            }
        }

        /**
         * Determines if sending the given intent would result in starting an Intent resolver
         * activity, instead of resolving to a specific component.
         *
         * @param resolved the resolveInfo for the intent as returned by resolveActivityAsUser
         * @param appList a list of resolveInfo as returned by queryIntentActivitiesAsUser
         * @return true if the intent would launch a resolver activity
         */
        private fun wouldLaunchResolverActivity(
            resolved: ResolveInfo,
            appList: List<ResolveInfo>,
        ): Boolean {
            // If the list contains the above resolved activity, then it can't be
            // ResolverActivity itself.
            for (i in appList.indices) {
                val tmp = appList[i]
                if (
                    tmp.activityInfo.name == resolved.activityInfo.name &&
                        tmp.activityInfo.packageName == resolved.activityInfo.packageName
                ) {
                    return false
                }
            }
            return true
        }
    }
}

/**
 * The callback that will be invoked when
 * [ActivityIntentRepository.wouldPendingIntentShowOverLockscreen] value is fetched.
 */
fun interface WouldPendingIntentShowOverLockscreenCallback<T> {
    fun onWouldPendingShowOverLockscreenFetched(
        showOverLockscreen: Boolean,
        additionalParameters: T,
    )
}
