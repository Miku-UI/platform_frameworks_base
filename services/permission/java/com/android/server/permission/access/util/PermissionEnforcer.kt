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

package com.android.server.permission.access.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import com.android.server.LocalServices
import com.android.server.pm.UserManagerInternal

class PermissionEnforcer(private val context: Context) {
    private val userManagerInternal = LocalServices.getService(UserManagerInternal::class.java)!!

    /**
     * If neither you nor the calling process of an IPC you are handling has been granted the
     * permission for accessing a particular [userId], throw a [SecurityException].
     *
     * @see Context.enforceCallingOrSelfPermission
     * @see UserManager.DISALLOW_DEBUGGING_FEATURES
     */
    fun enforceCallingOrSelfCrossUserPermission(
        userId: Int,
        enforceFullPermission: Boolean,
        enforceShellRestriction: Boolean,
        message: String?,
    ) {
        require(userId >= 0) { "userId $userId is invalid" }
        val callingUid = Binder.getCallingUid()
        val callingUserId = UserHandle.getUserId(callingUid)
        if (userId != callingUserId) {
            val permissionName =
                if (enforceFullPermission) {
                    Manifest.permission.INTERACT_ACROSS_USERS_FULL
                } else {
                    Manifest.permission.INTERACT_ACROSS_USERS
                }
            if (
                context.checkCallingOrSelfPermission(permissionName) !=
                    PackageManager.PERMISSION_GRANTED
            ) {
                val exceptionMessage = buildString {
                    if (message != null) {
                        append(message)
                        append(": ")
                    }
                    append("Neither user ")
                    append(callingUid)
                    append(" nor current process has ")
                    append(permissionName)
                    append(" to access user ")
                    append(userId)
                }
                throw SecurityException(exceptionMessage)
            }
        }
        if (enforceShellRestriction && isShellUid(callingUid)) {
            val isShellRestricted =
                userManagerInternal.hasUserRestriction(
                    UserManager.DISALLOW_DEBUGGING_FEATURES,
                    userId,
                )
            if (isShellRestricted) {
                val exceptionMessage = buildString {
                    if (message != null) {
                        append(message)
                        append(": ")
                    }
                    append("Shell is disallowed to access user ")
                    append(userId)
                }
                throw SecurityException(exceptionMessage)
            }
        }
    }

    /** Check whether a UID is shell UID. */
    private fun isShellUid(uid: Int) = UserHandle.getAppId(uid) == Process.SHELL_UID

    /**
     * If neither you nor the calling process of an IPC you are handling has been granted any of the
     * permissions, throw a [SecurityException].
     *
     * @see Context.enforceCallingOrSelfPermission
     */
    fun enforceCallingOrSelfAnyPermission(message: String?, vararg permissionNames: String) {
        val hasAnyPermission =
            permissionNames.any { permissionName ->
                context.checkCallingOrSelfPermission(permissionName) ==
                    PackageManager.PERMISSION_GRANTED
            }
        if (!hasAnyPermission) {
            val exceptionMessage = buildString {
                if (message != null) {
                    append(message)
                    append(": ")
                }
                append("Neither user ")
                append(Binder.getCallingUid())
                append(" nor current process has any of ")
                permissionNames.joinTo(this, ", ")
            }
            throw SecurityException(exceptionMessage)
        }
    }
}
