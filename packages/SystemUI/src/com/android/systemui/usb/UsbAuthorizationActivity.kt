/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.usb

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.hardware.usb.UsbAuthorizationStatus
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import com.android.internal.app.AlertActivity
import com.android.systemui.res.R
import javax.inject.Inject

interface AlertUpdater {
    // Updates the contents of the alert
    fun updateAlert()
}

/**
 * Dialog shown when the system requires authorization of a specific USB device.
 *
 * Class is kept open for ease of testing.
 */
open class UsbAuthorizationActivity
@Inject
constructor(private val factory: UsbAuthorizationHelper.Factory) :
    AlertActivity(), DialogInterface.OnClickListener, AlertUpdater {

    // Helper for this activity. Can be null until created with create Intent.
    private var helper: UsbAuthorizationHelper? = null

    private fun setupWindow() {
        window.addSystemFlags(
            WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS
        )

        // Needs to show up on lock screen (or login screen) too.
        setShowWhenLocked(true)

        // Don't allow dismissal when touched outside.
        this.setFinishOnTouchOutside(false)
    }

    override fun updateAlert() {
        helper?.setAlertParams(mAlertParams, this)
        setupAlert()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply SystemUI theme first.
        setTheme(R.style.Theme_SystemUI_Dialog)

        super.onCreate(savedInstanceState)
        setupWindow()

        try {
            helper = factory.create(this, this)
            helper?.newDeviceIntent(intent)
            updateAlert()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Failed to create activity with intent: ", e)
            helper = null
            finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // If we can't create a dialoghelper with new intent, finish early (which denies all pending
        // requests as well).
        try {
            helper?.newDeviceIntent(intent)
            updateAlert()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Failed to process new intent: ", e)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        updateAlert()
    }

    override fun onStop() {
        if (isFinishing) {
            helper?.complete()
        }

        super.onStop()
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        val authorize =
            if (which == AlertDialog.BUTTON_POSITIVE) UsbAuthorizationStatus.AUTHORIZED
            else UsbAuthorizationStatus.DENIED

        // Authorize all devices.
        helper?.sendResponse(authorize)

        // Finish the activity.
        finish()
    }

    companion object {
        private val TAG = UsbAuthorizationActivity::class.java.simpleName
    }
}
