/*
 * Copyright (C) 2025 The Android Open Source Project
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
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Drawable

sealed class UnarchiveStage(val stageCode: Int) {

    companion object {
        const val STAGE_DEFAULT = -1
        const val STAGE_ABORTED = 0
        const val STAGE_READY = 1
        const val STAGE_USER_ACTION_REQUIRED = 2
        const val STAGE_ERROR = 3
    }
}

data class UnarchiveAborted(
    val abortReason: Int,
    val activityResultCode: Int = Activity.RESULT_FIRST_USER,
) : UnarchiveStage(STAGE_ABORTED) {
    companion object {
        const val ABORT_REASON_GENERIC_ERROR = 0
        const val ABORT_REASON_UNARCHIVE_DONE = 1
    }
}

class UnarchiveReady() : UnarchiveStage(STAGE_READY)

data class UnarchiveUserActionRequired(
    val appSnippet: PackageUtil.AppSnippet? = null,
    val installerPackageName: String
) :
    UnarchiveStage(STAGE_USER_ACTION_REQUIRED) {
    val appIcon: Drawable?
        get() = appSnippet?.icon

    val appLabel: String?
        get() = appSnippet?.let { appSnippet.label as String? }

    fun getInstallerTitle(context: Context): String? {
        return installerPackageName.let {
            PackageUtil.getApplicationLabel(context, it) as String?
        }
    }
}

data class UnarchiveError(
    val unarchivalStatus: Int,
    val installerPackageName: String?,
    val installerAppTitle: String?,
    val requiredBytes: Long,
    val pendingIntent: PendingIntent?,
    val appSnippet: PackageUtil.AppSnippet? = null
) : UnarchiveStage(STAGE_ERROR) {
    val appIcon: Drawable?
        get() = appSnippet?.icon

    val appLabel: String?
        get() = appSnippet?.let { appSnippet.label as String? }
}
