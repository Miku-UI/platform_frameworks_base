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

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.os.Process
import android.util.Log
import com.android.packageinstaller.v2.model.PackageUtil.getAppSnippet
import com.android.packageinstaller.v2.model.PackageUtil.getPackageNameForUid
import com.android.packageinstaller.v2.model.PackageUtil.isPermissionGranted
import com.android.packageinstaller.v2.model.PackageUtil.isUidRequestingPermission
import java.io.IOException

class UnarchiveRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val packageInstaller: PackageInstaller = packageManager.packageInstaller

    private lateinit var intent: Intent
    private lateinit var targetPackageName: String
    private lateinit var intentSender: IntentSender

    fun performPreUnarchivalChecks(intent: Intent, callerInfo: CallerInfo): UnarchiveStage {
        this.intent = intent

        val callingUid = callerInfo.uid
        if (callingUid == Process.INVALID_UID) {
            Log.e(LOG_TAG, "Could not determine the launching uid.")
            return UnarchiveAborted(UnarchiveAborted.ABORT_REASON_GENERIC_ERROR)
        }

        val callingPackage = getPackageNameForUid(context, callingUid, null)
        if (callingPackage == null) {
            Log.e(LOG_TAG, "Package not found for originating uid $callingUid")
            return UnarchiveAborted(UnarchiveAborted.ABORT_REASON_GENERIC_ERROR)
        }


        // We don't check the AppOpsManager here for REQUEST_INSTALL_PACKAGES because the requester
        // is not the source of the installation.
        val hasRequestInstallPermission = isUidRequestingPermission(
            packageManager, callingUid, Manifest.permission.REQUEST_INSTALL_PACKAGES
        )
        val hasInstallPermission =
            isPermissionGranted(context, Manifest.permission.INSTALL_PACKAGES, callingUid)

        if (!hasRequestInstallPermission && !hasInstallPermission) {
            Log.e(
                LOG_TAG, ("Uid " + callingUid + " does not have "
                    + Manifest.permission.REQUEST_INSTALL_PACKAGES + " or "
                    + Manifest.permission.INSTALL_PACKAGES)
            )
            return UnarchiveAborted(UnarchiveAborted.ABORT_REASON_GENERIC_ERROR)
        }

        targetPackageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)!!
        intentSender =
            intent.getParcelableExtra(EXTRA_UNARCHIVE_INTENT_SENDER, IntentSender::class.java)!!

        return UnarchiveReady()
    }

    fun showUnarchivalConfirmation(): UnarchiveStage {
        var appSnippet: PackageUtil.AppSnippet
        try {
            val applicationInfo = packageManager.getApplicationInfo(targetPackageName,
                PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_ARCHIVED_PACKAGES))
            appSnippet = getAppSnippet(context, applicationInfo)
        } catch (e: NameNotFoundException) {
            Log.e(LOG_TAG, "Invalid packageName $targetPackageName: ", e)
            return UnarchiveAborted(UnarchiveAborted.ABORT_REASON_GENERIC_ERROR)
        }

        val installSource = packageManager.getInstallSourceInfo(targetPackageName)
        val installingPackageName =
            installSource.updateOwnerPackageName ?: installSource.installingPackageName
        if (installingPackageName == null) {
            return UnarchiveAborted(UnarchiveAborted.ABORT_REASON_GENERIC_ERROR)
        }

        val label = PackageUtil.getApplicationLabel(context, installingPackageName)
        if (label == null) {
            Log.e(LOG_TAG, "Could not find installer")
            return UnarchiveAborted(UnarchiveAborted.ABORT_REASON_GENERIC_ERROR)
        }

        return UnarchiveUserActionRequired(appSnippet, installingPackageName)
    }

    @SuppressLint("MissingPermission")
    fun beginUnarchive(): UnarchiveStage {
        try {
            packageInstaller.requestUnarchive(targetPackageName, intentSender)
        } catch (e: Exception) {
            when (e) {
                is IOException, is NameNotFoundException ->
                    Log.e(LOG_TAG, "RequestUnarchive failed with %s." + e.message)
            }
            return UnarchiveAborted(UnarchiveAborted.ABORT_REASON_GENERIC_ERROR)
        }
        return UnarchiveAborted(UnarchiveAborted.ABORT_REASON_UNARCHIVE_DONE, Activity.RESULT_OK)
    }

    fun showUnarchiveError(intent: Intent): UnarchiveStage {
        val unarchivalStatus = intent.getIntExtra(PackageInstaller.EXTRA_UNARCHIVE_STATUS, -1)
        val requiredBytes = intent.getLongExtra(EXTRA_REQUIRED_BYTES, 0L)
        val installerPackageName = intent.getStringExtra(EXTRA_INSTALLER_PACKAGE_NAME)
        // We cannot derive installerAppTitle from the package name because the installer might
        // not be installed anymore.
        val installerAppTitle = intent.getStringExtra(EXTRA_INSTALLER_TITLE)
        val pendingIntent = intent.getParcelableExtra<PendingIntent?>(
            Intent.EXTRA_INTENT,
            PendingIntent::class.java
        )

        val appPackageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
        var appSnippet: PackageUtil.AppSnippet? = null
        if (appPackageName != null) {
            try {
                val applicationInfo = packageManager.getApplicationInfo(
                    appPackageName,
                    PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_ARCHIVED_PACKAGES)
                )
                appSnippet = getAppSnippet(context, applicationInfo)
            } catch (e: NameNotFoundException) {
                Log.e(LOG_TAG, "Invalid packageName $appPackageName: ", e)
            }
        }

        when (unarchivalStatus) {
            PackageInstaller.UNARCHIVAL_ERROR_USER_ACTION_NEEDED -> {
                if (pendingIntent == null) {
                    Log.e(
                        LOG_TAG,
                        "A PendingIntent is required for unarchive error code " +
                                "${PackageInstaller.UNARCHIVAL_ERROR_USER_ACTION_NEEDED}"
                    )
                    return UnarchiveAborted(UnarchiveAborted.ABORT_REASON_GENERIC_ERROR)
                }

                if (installerAppTitle == null) {
                    Log.e(
                        LOG_TAG,
                        "Installer app title is required for unarchive error code " +
                                "${PackageInstaller.UNARCHIVAL_ERROR_USER_ACTION_NEEDED}"
                    )
                    return UnarchiveAborted(UnarchiveAborted.ABORT_REASON_GENERIC_ERROR)
                }
            }


            PackageInstaller.UNARCHIVAL_ERROR_INSUFFICIENT_STORAGE -> {
                if (requiredBytes == 0L) {
                    Log.w(
                        LOG_TAG,
                        "Required bytes not specified for unarchive error code " +
                                "${PackageInstaller.UNARCHIVAL_ERROR_INSUFFICIENT_STORAGE}"
                    )
                    // It's ok to still show the error dialog even though requiredBytes == 0
                }
            }

            PackageInstaller.UNARCHIVAL_ERROR_INSTALLER_DISABLED -> {
                if (installerPackageName == null) {
                    Log.e(
                        LOG_TAG,
                        "installerPackageName not specified for unarchive error code " +
                                "${PackageInstaller.UNARCHIVAL_ERROR_INSTALLER_DISABLED}"
                    )
                    return UnarchiveAborted(UnarchiveAborted.ABORT_REASON_GENERIC_ERROR)
                }
            }
        }

        return UnarchiveError(
            unarchivalStatus,
            installerPackageName,
            installerAppTitle,
            requiredBytes,
            pendingIntent,
            appSnippet
        )
    }

    companion object {
        private val LOG_TAG = UnarchiveRepository::class.java.simpleName
        private const val EXTRA_UNARCHIVE_INTENT_SENDER =
            "android.content.pm.extra.UNARCHIVE_INTENT_SENDER"
        private const val EXTRA_REQUIRED_BYTES: String =
            "com.android.content.pm.extra.UNARCHIVE_EXTRA_REQUIRED_BYTES"
        private const val EXTRA_INSTALLER_PACKAGE_NAME: String =
            "com.android.content.pm.extra.UNARCHIVE_INSTALLER_PACKAGE_NAME"
        private const val EXTRA_INSTALLER_TITLE: String =
            "com.android.content.pm.extra.UNARCHIVE_INSTALLER_TITLE"
    }

    data class CallerInfo(val packageName: String?, val uid: Int)
}
