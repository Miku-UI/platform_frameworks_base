/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.settingslib.bluetooth.hearingdevices

import android.bluetooth.BluetoothCsipSetCoordinator
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHapClient
import android.bluetooth.BluetoothHapPresetInfo
import android.util.Log
import com.android.settingslib.bluetooth.HapClientProfile
import com.android.settingslib.bluetooth.LocalBluetoothProfileManager
import com.android.settingslib.utils.ThreadUtils
import java.util.concurrent.Executor

/**
 * This class serves as an abstraction layer for managing hearing device presets.
 *
 * It handles the communication with remote devices via the Bluetooth Hearing Access Profile
 * (HAP) and reports events back to a higher-level controller through a simplified callback
 * interface.
 */
class PresetController(
    private val profileManager: LocalBluetoothProfileManager,
    private val controlCallback: PresetControlCallback
) : LocalBluetoothProfileManager.ServiceListener {

    private val hapClientProfile: HapClientProfile? = profileManager.hapClientProfile
    private val remoteCallback: PresetCallback = PresetCallback(controlCallback)

    init {
        if (hapClientProfile?.isProfileReady == false) {
            profileManager.addServiceListener(this)
        }
    }

    override fun onServiceConnected() {
        if (hapClientProfile?.isProfileReady == true) {
            profileManager.removeServiceListener(this)
            registerCallback(ThreadUtils.getBackgroundExecutor())
            controlCallback.onHapClientServiceConnected()
        }
    }

    override fun onServiceDisconnected() {
        // Do nothing
    }

    /**
     * Registers the internal callback with the HAP client profile.
     *
     * @param executor The executor where the callback will be invoked.
     * @see unregisterCallback
     */
    fun registerCallback(executor: Executor) {
        try {
            hapClientProfile?.registerCallback(executor, remoteCallback)
        } catch (e: IllegalArgumentException) {
            // The callback was already registered
            Log.i(TAG, "Skip registering the callback, ${e.message}")
        }
    }

    /**
     * Unregisters the internal callback from the HAP client profile.
     */
    fun unregisterCallback() {
        try {
            hapClientProfile?.unregisterCallback(remoteCallback)
        } catch (e: IllegalArgumentException) {
            // The callback was never registered or was already unregistered
            Log.w(TAG, "Cannot unregister callback, ${e.message}")
        }
    }

    /**
     * Gets the active preset index for a specific remote device.
     *
     * @param device The Bluetooth device.
     * @return The active preset index, or [BluetoothHapClient.PRESET_INDEX_UNAVAILABLE]
     * if the profile is not ready.
     */
    fun getActivePreset(device: BluetoothDevice): Int {
        return hapClientProfile?.getActivePresetIndex(device)
            ?: BluetoothHapClient.PRESET_INDEX_UNAVAILABLE
    }

    /**
     * Gets a list of all available presets for a remote device.
     *
     * @param device The Bluetooth device.
     * @return A list of preset information. Returns an empty list if the profile is not ready
     * or the device is null.
     */
    fun getPresetInfos(device: BluetoothDevice?): List<BluetoothHapPresetInfo> {
        if (device == null) {
            return emptyList()
        }
        return hapClientProfile?.getAllPresetInfo(device)
            ?.filter { it.isAvailable }
            ?.sortedBy { it.index }
            .orEmpty()
    }

    /**
     * Sends a command to a remote device to select a specific preset.
     *
     * @param device The Bluetooth device to command.
     * @param presetIndex The index of the preset to select.
     */
    fun selectPreset(device: BluetoothDevice, presetIndex: Int) {
        if (DEBUG) {
            Log.d(TAG, "selectPreset, presetIndex=$presetIndex, device=$device")
        }
        if (!device.isConnected) {
            Log.w(TAG, "selectPreset ignored, device is not connected. device=$device")
            return
        }
        hapClientProfile?.selectPreset(device, presetIndex)
    }

    /**
     * Sends a command to a group of devices to select a specific preset.
     *
     * @param groupId The HAP group ID.
     * @param presetIndex The index of the preset to select.
     */
    fun selectPresetForGroup(groupId: Int, presetIndex: Int) {
        if (DEBUG) {
            Log.d(
                TAG,
                "selectPresetForGroup, presetIndex=$presetIndex, groupId=$groupId"
            )
        }
        hapClientProfile?.selectPresetForGroup(groupId, presetIndex)
    }

    /**
     * Gets the HAP group ID for a specific remote device.
     *
     * @param device The Bluetooth device.
     * @return The HAP group ID.
     */
    fun getHapGroupId(device: BluetoothDevice): Int {
        return hapClientProfile?.getHapGroup(device) ?: BluetoothCsipSetCoordinator.GROUP_ID_INVALID
    }

    /**
     * Checks if a remote device supports synchronized presets.
     *
     * @param device The Bluetooth device.
     * @return `true` if the device supports synchronized presets; `false` otherwise.
     */
    fun supportsSynchronizedPresets(device: BluetoothDevice): Boolean {
        return hapClientProfile?.supportsSynchronizedPresets(device) ?: false
    }

    /**
     * Callback providing information about the status and received events of
     * [PresetController].
     */
    interface PresetControlCallback {
        /**
         * Called when the HAP client service is connected and ready to receive commands.
         */
        fun onHapClientServiceConnected()

        /**
         * Called when the active preset on a remote device has been changed.
         *
         * @param device The remote device that was changed.
         * @param presetIndex The index of the new active preset.
         */
        fun onPresetChangedFromRemote(device: BluetoothDevice, presetIndex: Int)

        /**
         * Called when the list of available presets on a remote device has been updated.
         *
         * @param device The remote device with the updated preset list.
         * @param presetInfos The new, updated list of presets.
         */
        fun onPresetInfoChangedFromRemote(
            device: BluetoothDevice,
            presetInfos: List<BluetoothHapPresetInfo>
        )

        /**
         * Called when a command to select a preset for a group of devices has failed.
         *
         * @param hapGroupId The HAP group ID for which the command failed.
         */
        fun onPresetGroupSelectionFailedFromRemote(hapGroupId: Int)

        /**
         * Called when a generic command sent to a remote device has failed.
         */
        fun onCommandFailedFromRemote()
    }

    /**
     * A wrapper callback that will pass [BluetoothHapClient.Callback] to
     * [PresetControlCallback].
     */
    private class PresetCallback(private val callback: PresetControlCallback) :
        BluetoothHapClient.Callback {

        override fun onPresetInfoChanged(
            device: BluetoothDevice,
            presetInfos: List<BluetoothHapPresetInfo>,
            reason: Int
        ) {
            if (DEBUG) {
                Log.d(TAG, "onPresetInfoChanged, device=$device, reason=$reason")
                for (info in presetInfos) {
                    Log.d(TAG, "    preset ${info.index}=${info.name}")
                }
            }
            callback.onPresetInfoChangedFromRemote(device, presetInfos)
        }

        override fun onPresetSelected(device: BluetoothDevice, presetIndex: Int, reason: Int) {
            if (DEBUG) {
                Log.d(
                    TAG,
                    "onPresetSelected, device=$device, presetIndex=$presetIndex, reason=$reason"
                )
            }
            callback.onPresetChangedFromRemote(device, presetIndex)
        }

        override fun onPresetSelectionFailed(device: BluetoothDevice, reason: Int) {
            Log.w(TAG, "onPresetSelectionFailed, device=$device")
            callback.onCommandFailedFromRemote()
        }

        override fun onPresetSelectionForGroupFailed(hapGroupId: Int, reason: Int) {
            Log.w(TAG, "onPresetSelectionForGroupFailed, hapGroupId=$hapGroupId")
            callback.onPresetGroupSelectionFailedFromRemote(hapGroupId)
        }

        override fun onSetPresetNameFailed(device: BluetoothDevice, reason: Int) {
            Log.w(TAG, "onSetPresetNameFailed, device=$device")
            callback.onCommandFailedFromRemote()
        }

        override fun onSetPresetNameForGroupFailed(hapGroupId: Int, reason: Int) {
            Log.w(TAG, "onSetPresetNameForGroupFailed, hapGroupId=$hapGroupId")
            callback.onCommandFailedFromRemote()
        }
    }

    companion object {
        private const val DEBUG = true
        private const val TAG = "PresetController"
    }
}