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

package com.android.packageinstaller.v2.ui

import android.app.ActivityOptions
import android.app.BroadcastOptions
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import com.android.packageinstaller.R
import com.android.packageinstaller.v2.model.PackageUtil
import com.android.packageinstaller.v2.model.UnarchiveAborted
import com.android.packageinstaller.v2.model.UnarchiveRepository
import com.android.packageinstaller.v2.model.UnarchiveStage
import com.android.packageinstaller.v2.ui.fragments.UnarchiveFragment
import com.android.packageinstaller.v2.viewmodel.UnarchiveViewModel
import com.android.packageinstaller.v2.viewmodel.UnarchiveViewModelFactory

class UnarchiveLaunch : FragmentActivity(), UnarchiveActionListener {

    companion object {
        @JvmField val EXTRA_CALLING_PKG_UID: String =
            UnarchiveLaunch::class.java.packageName + ".callingPkgUid"
        @JvmField val EXTRA_CALLING_PKG_NAME: String =
            UnarchiveLaunch::class.java.packageName + ".callingPkgName"
        val TAG: String = UnarchiveLaunch::class.java.simpleName

        private val LOG_TAG = UnarchiveLaunch::class.java.simpleName

        private const val TAG_DIALOG = "dialog"
        private const val TAG_UNARCHIVE_DIALOG = "unarchive-dialog"

        private const val ACTION_UNARCHIVE_DIALOG: String =
            "com.android.intent.action.UNARCHIVE_DIALOG"
        private const val ACTION_UNARCHIVE_ERROR_DIALOG: String =
            "com.android.intent.action.UNARCHIVE_ERROR_DIALOG"
    }

    private var unarchiveViewModel: UnarchiveViewModel? = null
    private var unarchiveRepository: UnarchiveRepository? = null
    private var fragmentManager: FragmentManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        window.addSystemFlags(
            WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS
        )
        super.onCreate(savedInstanceState)

        // The base theme inherits a deviceDefault theme. Applying a material style on the base
        // theme to support the material design.
        if (PackageUtil.isMaterialDesignEnabled(this)) {
            Log.d(LOG_TAG, "Apply material design")
            theme.applyStyle(R.style.Theme_AlertDialogActivity_Material, /* force= */ true)
        }

        fragmentManager = supportFragmentManager

        unarchiveRepository = UnarchiveRepository(applicationContext)
        unarchiveViewModel = ViewModelProvider(
            this, UnarchiveViewModelFactory(application, unarchiveRepository!!)
        )[UnarchiveViewModel::class.java]

        val info = UnarchiveRepository.CallerInfo(
            intent.getStringExtra(EXTRA_CALLING_PKG_NAME),
            intent.getIntExtra(EXTRA_CALLING_PKG_UID, Process.INVALID_UID)
        )
        when (intent.action) {
            ACTION_UNARCHIVE_DIALOG -> unarchiveViewModel!!.preprocessIntent(intent, info)
            ACTION_UNARCHIVE_ERROR_DIALOG -> unarchiveViewModel!!.showUnarchiveError(intent)
        }

        unarchiveViewModel!!.currentUnarchiveStage.observe(this) { stage: UnarchiveStage ->
            onUnarchiveStageChange(stage)
        }
    }

    private fun onUnarchiveStageChange(stage: UnarchiveStage) {
        when (stage.stageCode) {
            UnarchiveStage.STAGE_ABORTED -> {
                val aborted = stage as UnarchiveAborted
                setResult(aborted.activityResultCode)
                finish()
            }

            UnarchiveStage.STAGE_USER_ACTION_REQUIRED -> {
                showUnarchiveDialog()
            }

            UnarchiveStage.STAGE_ERROR -> {
                showUnarchiveDialog()
            }
        }
    }

    override fun beginUnarchive() {
        unarchiveViewModel!!.beginUnarchive()
    }

    override fun handleUnarchiveErrorAction(
        unarchiveStatus: Int,
        installerPkg: String?,
        pi: PendingIntent?
    ) {
        // Allow the error handling activities to start in the background.
        val options = BroadcastOptions.makeBasic()
        options.setPendingIntentBackgroundActivityStartMode(
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        )

        when (unarchiveStatus) {
            PackageInstaller.UNARCHIVAL_ERROR_USER_ACTION_NEEDED -> {
                baseContext.startIntentSender(
                    pi!!.intentSender,
                    /* fillInIntent= */ null,
                    /* flagsMask= */ 0,
                    Intent.FLAG_ACTIVITY_NEW_TASK,
                    /* extraFlags= */ 0,
                    options.toBundle()
                )
            }

            PackageInstaller.UNARCHIVAL_ERROR_INSUFFICIENT_STORAGE -> {
                if (pi != null) {
                    baseContext.startIntentSender(
                        pi.intentSender,
                        /* fillInIntent= */ null,
                        /* flagsMask= */ 0,
                        Intent.FLAG_ACTIVITY_NEW_TASK,
                        /* extraFlags= */ 0,
                        options.toBundle()
                    )
                } else {
                    val intent = Intent("android.intent.action.MANAGE_PACKAGE_STORAGE")
                    startActivity(intent, options.toBundle())
                }
            }

            PackageInstaller.UNARCHIVAL_ERROR_INSTALLER_DISABLED -> {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", installerPkg!!, null)
                intent.setData(uri)
                startActivity(intent, options.toBundle())
            }

            else -> {
                // Do nothing. The rest of the dialogs are purely informational.
            }
        }
        finish()
    }

    private fun showUnarchiveDialog() {
        val fragment = getUnarchiveFragment() ?: UnarchiveFragment()
        fragment.updateUI()
        showDialogInner(fragment, TAG_UNARCHIVE_DIALOG)
    }

    /**
     * Replace any visible dialog by the dialog returned by UnarchiveRepository
     *
     * @param newDialog The new dialog to display
     */
    private fun showDialogInner(newDialog: DialogFragment?) {
        showDialogInner(newDialog, TAG_DIALOG)
    }

    private fun showDialogInner(newDialog: DialogFragment?, tag: String) {
        var currentTag: String? = null
        if (tag == TAG_UNARCHIVE_DIALOG) {
            if (getUnarchiveFragment() != null) {
                return
            }
            currentTag = TAG_DIALOG
        } else {
            currentTag = TAG_UNARCHIVE_DIALOG
        }

        val currentDialog = fragmentManager!!.findFragmentByTag(currentTag)
        if (currentDialog is DialogFragment) {
            currentDialog.dismissAllowingStateLoss()
        }
        newDialog?.show(fragmentManager!!, tag)
    }

    private fun getUnarchiveFragment(): UnarchiveFragment? {
        return (fragmentManager!!.findFragmentByTag(TAG_UNARCHIVE_DIALOG)
            ?: return null) as UnarchiveFragment?
    }
}
