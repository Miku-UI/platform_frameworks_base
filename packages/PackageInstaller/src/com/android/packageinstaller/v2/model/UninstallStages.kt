/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.packageinstaller.v2.model

import android.app.Activity
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import com.android.packageinstaller.R

sealed class UninstallStage(val stageCode: Int) {

    companion object {
        const val STAGE_DEFAULT = -1
        const val STAGE_ABORTED = 0
        const val STAGE_READY = 1
        const val STAGE_USER_ACTION_REQUIRED = 2
        const val STAGE_SUCCESS = 3
        const val STAGE_FAILED = 4
    }
}

class UninstallReady : UninstallStage(STAGE_READY)

data class UninstallUserActionRequired(
    val titleResId: Int,
    val messageResId: Int? = null,
    val positiveButtonResId: Int,
    val appDataSize: Long = 0,
    val appSnippet: PackageUtil.AppSnippet? = null,
    val isClonedApp: Boolean = false,
    val isDifferentActivityName: Boolean = false,
    val isOtherUser: Boolean = false,
    val userName: String? = null,
    val targetAppInfo: ApplicationInfo,
    val targetActivityInfo: ActivityInfo? = null,
) : UninstallStage(STAGE_USER_ACTION_REQUIRED) {

    val appIcon: Drawable?
        get() = appSnippet?.icon

    fun getAppLabel(context: Context): String {
        val appLabel = targetAppInfo.loadSafeLabel(context.packageManager) as String
        return if (isClonedApp) {
            context.getString(R.string.string_cloned_app_label, appLabel)
        } else {
            appLabel
        }
    }

    fun getMessage(context: Context): String? {
        return messageResId?.let {
            if (isDifferentActivityName && targetActivityInfo != null) {
                val activityLabel = targetActivityInfo.loadSafeLabel(context.packageManager)
                val appLabel = targetAppInfo.loadSafeLabel(context.packageManager)
                context.getString(it, activityLabel, appLabel)
            } else if (isClonedApp) {
                val appLabel = targetAppInfo.loadSafeLabel(context.packageManager)
                context.getString(it, appLabel)
            } else {
                context.getString(it)
            }
        }
    }

    fun getTitle(context: Context): String {
        return titleResId.let {
            if (isOtherUser && userName != null) {
                context.getString(it, userName)
            } else {
                context.getString(it)
            }
        }
    }
}

data class UninstallSuccess(
    val appInfo: ApplicationInfo,
    val resultIntent: Intent? = null,
    val activityResultCode: Int = 0,
    val messageResId: Int? = null,
    val isCloneApp: Boolean = false,
) : UninstallStage(STAGE_SUCCESS) {

    fun getMessage(context: Context): String? {
        return messageResId?.let {
            val appLabel = appInfo.loadSafeLabel(context.packageManager)
            context.getString(it, appLabel)
        }
    }
}

data class UninstallFailed(
    val returnResult: Boolean,
    /**
     * If the caller wants the result back, the intent will hold the uninstall failure status code
     * and legacy code.
     */
    val resultIntent: Intent? = null,
    val activityResultCode: Int = Activity.RESULT_CANCELED,
    /**
     * ID used to show [uninstallNotification]
     */
    val uninstallNotificationId: Int? = null,
    /**
     * When the user does not request a result back, this notification will be shown indicating the
     * reason for uninstall failure.
     */
    val uninstallNotification: Notification? = null,
) : UninstallStage(STAGE_FAILED) {

    init {
        if (uninstallNotification != null && uninstallNotificationId == null) {
            throw IllegalArgumentException(
                "uninstallNotification cannot be set without uninstallNotificationId"
            )
        }
    }
}

data class UninstallAborted(
    val abortReason: Int,
    val appSnippet: PackageUtil.AppSnippet? = null
) : UninstallStage(STAGE_ABORTED) {
    var dialogTitleResource = 0
    var dialogTextResource = 0
    val activityResultCode = Activity.RESULT_FIRST_USER

    val appIcon: Drawable?
        get() = appSnippet?.icon

    val appLabel: String?
        get() = appSnippet?.let { appSnippet.label as String? }

    init {
        when (abortReason) {
            ABORT_REASON_APP_UNAVAILABLE -> {
                dialogTitleResource = R.string.title_uninstall_app_not_found
                dialogTextResource = R.string.message_uninstall_app_not_found
            }

            ABORT_REASON_UNKNOWN -> {
                dialogTitleResource = R.string.title_uninstall_failed
                dialogTextResource = R.string.message_uninstall_failed
            }

            ABORT_REASON_USER_NOT_ALLOWED -> {
                dialogTitleResource = R.string.title_uninstall_user_not_allowed
                dialogTextResource = R.string.message_uninstall_user_not_allowed
            }

            else -> {
                dialogTitleResource = 0
                dialogTextResource = 0
            }
        }
    }

    companion object {
        const val ABORT_REASON_UNKNOWN = -1
        const val ABORT_REASON_GENERIC_ERROR = 0
        const val ABORT_REASON_APP_UNAVAILABLE = 1
        const val ABORT_REASON_USER_NOT_ALLOWED = 2
        const val ABORT_REASON_UNINSTALL_DONE = 3
    }
}

