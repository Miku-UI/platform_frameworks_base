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

package com.android.systemui.accessibility.hearingaid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.UserHandle
import android.util.Log
import androidx.annotation.MainThread
import com.android.settingslib.Utils
import com.android.settingslib.bluetooth.BluetoothUtils
import com.android.settingslib.bluetooth.CachedBluetoothDevice
import com.android.systemui.CoreStartable
import com.android.systemui.Flags
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.res.R
import com.android.systemui.statusbar.policy.bluetooth.data.repository.BluetoothRepository
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Monitor hearing devices status and show notifications. */
@SysUISingleton
class HearingDeviceNotification
@Inject
constructor(
    private val context: Context,
    private val bluetoothRepository: BluetoothRepository,
    private val broadcastDispatcher: BroadcastDispatcher,
    private val notificationManager: NotificationManager,
    private val dismissController: HearingDeviceNotificationDismissController,
    @Main private val mainExecutor: Executor,
    @Application private val applicationScope: CoroutineScope,
) : CoreStartable, CachedBluetoothDevice.Callback {

    private val trackedDevices = mutableSetOf<CachedBluetoothDevice>()

    override fun start() {
        if (!Flags.hearingDeviceStatusNotification()) {
            Log.d(TAG, "Hearing device status notification flag is disabled, not starting.")
            return
        }
        createNotificationChannel()

        applicationScope.launch {
            val dismissalFlow =
                broadcastDispatcher
                    .broadcastFlow(
                        IntentFilter(ACTION_DISMISS_NOTIFICATION),
                        UserHandle.ALL,
                        Context.RECEIVER_NOT_EXPORTED,
                    ) { intent, _ ->
                        intent
                    }
                    .onEach { intent ->
                        intent.getStringExtra(KEY_BLUETOOTH_ADDRESS)?.let {
                            dismissController.dismissNotification(it)
                        }
                    }

            merge(dismissalFlow, bluetoothRepository.connectedDevices).collect {
                checkConnectedDevices(bluetoothRepository.connectedDevices.value)
            }
        }
    }

    override fun onDeviceAttributesChanged() {
        checkConnectedDevices(bluetoothRepository.connectedDevices.value)
    }

    @MainThread
    private fun checkConnectedDevices(devices: List<CachedBluetoothDevice>) {
        val targetDevice = findTargetHearingDevice(devices)
        if (targetDevice != null) {
            updateTrackedDevices(targetDevice)
            updateNotification(targetDevice)
        } else {
            clearTrackedDevices()
            cancelNotification()
        }
    }

    private fun findTargetHearingDevice(
        devices: List<CachedBluetoothDevice>
    ): CachedBluetoothDevice? {
        val hearingDevices = devices.filter { it.isHearingDevice }
        // Prefer the active device, otherwise take the first connected hearing device.
        return hearingDevices.firstOrNull {
            it.isActiveDevice(BluetoothProfile.HEARING_AID) ||
                it.isActiveDevice(BluetoothProfile.LE_AUDIO)
        } ?: hearingDevices.firstOrNull()
    }

    private fun updateTrackedDevices(targetDevice: CachedBluetoothDevice) {
        val currentDevices = buildSet {
            add(targetDevice)
            targetDevice.subDevice?.let { add(it) }
            addAll(targetDevice.memberDevice)
        }
        val devicesToRemove = trackedDevices - currentDevices
        val devicesToAdd = currentDevices - trackedDevices
        devicesToRemove.forEach { device ->
            device.unregisterCallback(this)
            trackedDevices.remove(device)
            dismissController.removeDevice(device.address)
        }
        devicesToAdd.forEach { device ->
            device.registerCallback(mainExecutor, this)
            trackedDevices.add(device)
        }
    }

    private fun clearTrackedDevices() {
        trackedDevices.forEach { it.unregisterCallback(this) }
        trackedDevices.clear()
        dismissController.reset()
    }

    private fun updateNotification(device: CachedBluetoothDevice) {
        if (
            isValidHearingDevice(device) &&
                dismissController.updateAndCheckNotification(device.address, device.batteryLevel)
        ) {
            postNotification(device)
        } else {
            cancelNotification()
        }
    }

    private fun isValidHearingDevice(device: CachedBluetoothDevice): Boolean {
        return device.isConnected &&
            device.batteryLevel != BluetoothDevice.BATTERY_LEVEL_UNKNOWN &&
            !isFastPairDevice(device)
    }

    private fun postNotification(device: CachedBluetoothDevice) {
        val builder =
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(com.android.internal.R.drawable.ic_bt_hearing_aid)
                .setContentTitle(device.name)
                .setContentText(getBatteryMessage(device))
                .setSubText(getStatusMessage(device))
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setCategory(Notification.CATEGORY_SYSTEM)
                .setOnlyAlertOnce(true)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setOngoing(true)
                .setAutoCancel(false)
                .setDeleteIntent(getDeletePendingIntent(device))
                .addExtras(
                    Bundle().apply {
                        putString(
                            Notification.EXTRA_SUBSTITUTE_APP_NAME,
                            context.getString(com.android.internal.R.string.android_system_label),
                        )
                    }
                )

        getDeviceDetailsPendingIntent(device)?.let {
            builder.addAction(
                Notification.Action.Builder(
                        null,
                        context.getString(R.string.hearing_devices_settings_button),
                        it,
                    )
                    .build()
            )
        }

        val notification =
            builder.build().apply {
                flags = flags or (Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT)
            }
        notificationManager.notify(TAG, NOTIFICATION_ID, notification)
    }

    private fun cancelNotification() {
        notificationManager.cancel(TAG, NOTIFICATION_ID)
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                    CHANNEL_ID,
                    context.getString(
                        R.string.accessibility_hearing_device_notification_channel_name
                    ),
                    NotificationManager.IMPORTANCE_HIGH,
                )
                .apply { lockscreenVisibility = Notification.VISIBILITY_PRIVATE }
        // NotificationManager.createNotificationChannel will only create a new channel if it's not
        // already present. Repeated calls with the same CHANNEL_ID are ignored.
        notificationManager.createNotificationChannel(channel)
    }

    private fun getDeviceDetailsPendingIntent(device: CachedBluetoothDevice): PendingIntent? {
        val intent =
            Intent(ACTION_BLUETOOTH_DEVICE_DETAILS).apply {
                putExtra(
                    EXTRA_SHOW_FRAGMENT_ARGUMENTS,
                    Bundle().apply { putString(KEY_BLUETOOTH_ADDRESS, device.address) },
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

        return PendingIntent.getActivityAsUser(
            context,
            device.address.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            null,
            UserHandle.CURRENT,
        )
    }

    private fun getDeletePendingIntent(device: CachedBluetoothDevice): PendingIntent {
        val intent =
            Intent(ACTION_DISMISS_NOTIFICATION).apply {
                setPackage(context.packageName)
                putExtra(KEY_BLUETOOTH_ADDRESS, device.address)
            }
        return PendingIntent.getBroadcast(
            context,
            device.address.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun getBatteryMessage(device: CachedBluetoothDevice): String {
        device.batteryLevelsInfo?.let { info ->
            val leftLevel = info.leftBatteryLevel
            val rightLevel = info.rightBatteryLevel
            val isLeftValid = leftLevel != BluetoothDevice.BATTERY_LEVEL_UNKNOWN
            val isRightValid = rightLevel != BluetoothDevice.BATTERY_LEVEL_UNKNOWN

            when {
                isLeftValid && isRightValid ->
                    return context.getString(
                        R.string.accessibility_hearing_device_left_right_battery_level,
                        Utils.formatPercentage(leftLevel),
                        Utils.formatPercentage(rightLevel),
                    )
                isLeftValid ->
                    return context.getString(
                        R.string.accessibility_hearing_device_left_battery_level,
                        Utils.formatPercentage(leftLevel),
                    )
                isRightValid ->
                    return context.getString(
                        R.string.accessibility_hearing_device_right_battery_level,
                        Utils.formatPercentage(rightLevel),
                    )
            }
        }

        // Fallback to single battery level if no valid left/right levels
        return context.getString(
            R.string.accessibility_hearing_device_battery_level,
            Utils.formatPercentage(device.batteryLevel),
        )
    }

    private fun getStatusMessage(device: CachedBluetoothDevice): String {
        val isActive =
            device.isActiveDevice(BluetoothProfile.HEARING_AID) ||
                device.isActiveDevice(BluetoothProfile.LE_AUDIO)
        return if (isActive) {
            context.getString(R.string.quick_settings_hearing_devices_connected)
        } else {
            context.getString(R.string.quick_settings_bluetooth_device_connected)
        }
    }

    private fun isFastPairDevice(device: CachedBluetoothDevice): Boolean {
        return BluetoothUtils.getBooleanMetaData(
            device.device,
            BluetoothDevice.METADATA_IS_UNTETHERED_HEADSET,
        )
    }

    companion object {
        private const val TAG = "HearingDeviceNotification"
        private const val CHANNEL_ID = "hearing_device_status"
        private const val NOTIFICATION_ID = 101
        private const val EXTRA_SHOW_FRAGMENT_ARGUMENTS = ":settings:show_fragment_args"
        private const val ACTION_BLUETOOTH_DEVICE_DETAILS =
            "com.android.settings.BLUETOOTH_DEVICE_DETAIL_SETTINGS"
        private const val KEY_BLUETOOTH_ADDRESS = "device_address"
        private const val ACTION_DISMISS_NOTIFICATION =
            "com.android.systemui.accessibility.hearingaid.DISMISS_NOTIFICATION"
    }
}
