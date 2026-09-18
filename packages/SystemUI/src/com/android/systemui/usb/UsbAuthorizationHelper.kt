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

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.hardware.usb.IUsbManager
import android.hardware.usb.UsbAuthorizationStatus
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.RemoteException
import android.provider.Settings
import android.util.ArrayMap
import android.util.Log
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import com.android.internal.app.AlertController
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.res.R
import com.android.systemui.util.settings.GlobalSettings
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/** Helper class for {@link UsbAuthorizationActivity}. */
class UsbAuthorizationHelper
@AssistedInject
constructor(
    @Main private val resources: Resources,
    private val usbService: IUsbManager?,
    private val settings: GlobalSettings,
    private val broadcastDispatcher: BroadcastDispatcher,
    @Assisted private val updater: AlertUpdater,
    @Assisted private val parentActivity: Activity,
) {
    companion object {
        // Constant string for untrusted field in intent
        const val EXTRA_UNTRUSTED = "untrusted"
        private val TAG = UsbAuthorizationHelper::class.java.simpleName
    }

    // Devices that need to be authorized
    private val pendingDevices = ArrayMap<String, UsbDevice>()

    // Name of the current host (or model name as back-up)
    private val hostDeviceName: String

    // Not untrusted until we receive at least one untrusted intent
    private var untrusted = false

    // Listen to device detached broadcasts.
    private var detachReceiver: UsbDetachReceiver? = null

    // Last message we posted to the alert (used for testing).
    var lastMessageText: String? = null

    // Did we finish sending responses for all pending devices?
    private var responseSent = false

    @AssistedFactory
    interface Factory {
        fun create(updater: AlertUpdater, parentActivity: Activity): UsbAuthorizationHelper
    }

    init {
        hostDeviceName = settings.getString(Settings.Global.DEVICE_NAME) ?: android.os.Build.MODEL
    }

    // Broadcast receiver to listen for USB detach events.
    inner class UsbDetachReceiver : BroadcastReceiver() {
        fun register() {
            val filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)
            broadcastDispatcher.registerReceiver(this, filter)
        }

        override fun onReceive(context: Context, intent: Intent) {
            handleDetachBroadcasts(intent)
        }
    }

    fun handleDetachBroadcasts(intent: Intent) {
        // We only handle USB device detached intents.
        val action = intent.action
        if (UsbManager.ACTION_USB_DEVICE_DETACHED != action) {
            return
        }

        val device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        if (device == null) {
            return
        }

        val removed = pendingDevices.remove(device.deviceName)

        // If we removed anything, update the alert (if any items are remaining) or finish the
        // parent activity otherwise.
        if (removed != null) {
            Log.d(
                TAG,
                "Detach device: " + device.deviceName + ", remaining = " + pendingDevices.size,
            )
            if (pendingDevices.size > 0) {
                updater.updateAlert()
            } else {
                parentActivity.finish()
            }
        }
    }

    fun getProductNameString(device: UsbDevice): String {
        val name = device.productName
        if (name.isNullOrBlank()) {
            return resources.getString(
                R.string.usb_authorization_unknown_device,
                device.vendorId,
                device.productId,
            )
        }
        return name
    }

    // Set the height of the view containing the device list.
    // It should display at most 3 devices before it starts scrolling.
    fun setListViewHeight(listView: ListView) {
        val density = resources.displayMetrics.density
        val itemHeightPx = (48 * density).toInt()
        val listPaddingPx = listView.paddingTop + listView.paddingBottom
        val visibleItems =
            if (pendingDevices.size > 3) {
                3
            } else {
                pendingDevices.size
            }

        val layoutParams = listView.layoutParams
        layoutParams.height = (visibleItems * itemHeightPx) + listPaddingPx
        listView.layoutParams = layoutParams
    }

    // Set the necessary alert params and build the custom content view to display the message and
    // list of devices that connected.
    fun setAlertParams(
        params: AlertController.AlertParams,
        onClick: DialogInterface.OnClickListener,
    ) {
        val inflater = LayoutInflater.from(parentActivity)
        val view = inflater.inflate(R.layout.usb_auth_dialog_content, null)

        // Set message based on |untrusted| and number of devices.
        lastMessageText =
            if (untrusted) {
                if (pendingDevices.size > 1) {
                    resources.getString(
                        R.string.usb_authorization_message_multi_untrusted,
                        hostDeviceName,
                    )
                } else {
                    resources.getString(
                        R.string.usb_authorization_message_untrusted,
                        hostDeviceName,
                    )
                }
            } else {
                if (pendingDevices.size > 1) {
                    resources.getString(R.string.usb_authorization_message_multi, hostDeviceName)
                } else {
                    resources.getString(R.string.usb_authorization_message, hostDeviceName)
                }
            }
        val messageView = view.findViewById<TextView>(R.id.message)
        messageView.text = lastMessageText

        // Configure and assign the listview
        val listView = view.findViewById<ListView>(R.id.device_list)
        val adapter =
            ArrayAdapter<String>(
                parentActivity,
                android.R.layout.simple_list_item_1,
                pendingDevices.values.map { getProductNameString(it) }.toTypedArray(),
            )
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_NONE
        setListViewHeight(listView)
        params.mView = view

        // Assign alert params
        params.mIconId = com.android.internal.R.drawable.stat_sys_data_usb
        params.mTitle =
            resources.getString(
                if (pendingDevices.size > 1) R.string.usb_authorization_title_multi
                else R.string.usb_authorization_title
            )
        params.mPositiveButtonText = resources.getString(R.string.usb_authorization_allow)
        params.mNegativeButtonText = resources.getString(R.string.usb_authorization_deny)
        params.mPositiveButtonListener = onClick
        params.mNegativeButtonListener = onClick
    }

    // Add new device to pending list or throw an exception if the device is missing from the
    // intent.
    fun newDeviceIntent(intent: Intent) {
        val device: UsbDevice =
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                ?: throw IllegalStateException("Device missing from intent")

        // If we didn't get untrusted extra, assume it's false. The "untrusted" extra is used
        // to indicate that we are asking for a device to be allowed ahead of a user unlock or
        // login.
        //
        // If we have any untrusted intents, the entire dialog becomes untrusted.
        val extra_untrusted: Boolean = intent.getBooleanExtra(EXTRA_UNTRUSTED, false)
        untrusted = untrusted || extra_untrusted

        // Add new device to be authorized when we press allow.
        pendingDevices.put(device.deviceName, device)

        if (detachReceiver == null) {
            detachReceiver = UsbDetachReceiver()
            detachReceiver?.register()
        }

        Log.d(TAG, "New device added: " + device.deviceName + ", untrusted = " + extra_untrusted)
    }

    fun complete() {
        if (!responseSent) {
            sendResponse(UsbAuthorizationStatus.DENIED)
        }

        detachReceiver?.let {
            broadcastDispatcher.unregisterReceiver(it)
            detachReceiver = null
        }
    }

    fun sendResponse(status: Int) {
        pendingDevices.forEach { _, device -> setAuthorizationResponse(device, status) }
        responseSent = true
    }

    private fun setAuthorizationResponse(device: UsbDevice, authorizationStatus: Int) {
        Log.d(TAG, "Sending response for " + device.deviceName + " = " + authorizationStatus)
        try {
            usbService?.setAuthorizationResponse(
                device,
                authorizationStatus,
                /* isPersistent= */ true,
            )
        } catch (e: RemoteException) {
            Log.e(TAG, "IUsbService connection failed", e)
        }
    }
}
