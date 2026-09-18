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

package com.android.serial.accessdialog

import android.app.AlertDialog
import android.content.DialogInterface
import android.hardware.serial.SerialManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS
import com.android.internal.annotations.VisibleForTesting
import com.android.internal.app.AlertActivity

open class AccessDialogActivity @VisibleForTesting constructor(
    private var serialAccessManager: SerialAccessManager?
) : AlertActivity(), DialogInterface.OnClickListener {

    constructor() : this(null)

    private lateinit var helper: AccessDialogHelper

    private val onDisconnectedCallback = {
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (serialAccessManager == null) {
            val serialManager = requireNotNull(getSystemService(SerialManager::class.java)) {
                "Serial manager is null"
            }
            serialAccessManager = SerialAccessManagerImpl(serialManager)
        }
        try {
            helper = AccessDialogHelper(this, getIntent(), serialAccessManager!!)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Failed to initialize.", e)
            finish()
            return
        }
        with (mAlertParams) {
            mTitle = getString(R.string.dialog_title, helper.appLabel, helper.requestedPort)
            mPositiveButtonText = getString(R.string.dialog_positive_button)
            mPositiveButtonListener = this@AccessDialogActivity
            mNegativeButtonText = getString(R.string.dialog_negative_button)
            mNegativeButtonListener = this@AccessDialogActivity
        }
        setupAlert()

        // Tapjacking protection
        window.addSystemFlags(SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS)
        mAlert.getButton(DialogInterface.BUTTON_POSITIVE).setFilterTouchesWhenObscured(true)
    }

    override fun onResume() {
        super.onResume()
        helper.registerSerialPortDisconnectedCallback(onDisconnectedCallback)
    }

    override fun onPause() {
        helper.unregisterSerialPortDisconnectedCallback()
        super.onPause()
    }

    override fun onDestroy() {
        helper.sendResult()
        super.onDestroy()
    }

    override fun onClick(dialog: DialogInterface?, which: Int) {
        when (which) {
            AlertDialog.BUTTON_POSITIVE -> helper.granted = true
            else -> helper.granted = false
        }
    }

    @VisibleForTesting
    fun getAlertTitle(): CharSequence {
        return mAlertParams.mTitle
    }

    companion object {
        const val TAG = "SerialAccessDialog"
    }
}
