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

package com.android.settingslib.bluetooth.hearingdevices.ui

import android.bluetooth.BluetoothCsipSetCoordinator
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.BOND_BONDED
import android.bluetooth.BluetoothHapPresetInfo
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.android.settingslib.R
import com.android.settingslib.bluetooth.BluetoothCallback
import com.android.settingslib.bluetooth.BluetoothEventManager
import com.android.settingslib.bluetooth.CachedBluetoothDevice
import com.android.settingslib.bluetooth.HapClientProfile
import com.android.settingslib.bluetooth.HearingAidInfo.DeviceSide.SIDE_INVALID
import com.android.settingslib.bluetooth.HearingAidInfo.DeviceSide.SIDE_LEFT
import com.android.settingslib.bluetooth.HearingAidInfo.DeviceSide.SIDE_RIGHT
import com.android.settingslib.bluetooth.LocalBluetoothManager
import com.android.settingslib.bluetooth.hearingdevices.PresetController
import com.android.settingslib.bluetooth.hearingdevices.ui.ExpandableControlUi.Companion.SIDE_UNIFIED
import com.android.settingslib.bluetooth.hearingdevices.ui.ExpandableControlUi.Companion.VALID_SIDES
import com.android.settingslib.utils.ThreadUtils
import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap

/** This class controls the hearing device preset UI with remote preset data.
 *
 * It acts as a bridge between the UI [PresetUi] and the remote data retrieved from
 * [PresetController], managing the state and ensuring the UI reflects the current
 * remote device settings.
 */
class PresetUiController(
    private val context: Context,
    bluetoothManager: LocalBluetoothManager,
    private val presetLayout: PresetUi
) : PresetController.PresetControlCallback, PresetUi.PresetUiListener {

    private val eventManager: BluetoothEventManager = bluetoothManager.eventManager
    private val sideToDeviceMap: BiMap<Int, BluetoothDevice> = HashBiMap.create()
    private val cachedDevices: MutableSet<CachedBluetoothDevice> = mutableSetOf()
    private val presetController: PresetController =
        PresetController(bluetoothManager.profileManager, this)

    private var cachedDevice: CachedBluetoothDevice? = null
    private var started = false
    private var toast: Toast? = null

    private val bluetoothCallback: BluetoothCallback = object : BluetoothCallback {
        override fun onProfileConnectionStateChanged(
            cachedDevice: CachedBluetoothDevice,
            state: Int,
            bluetoothProfile: Int
        ) {
            if (bluetoothProfile == BluetoothProfile.HAP_CLIENT &&
                state == BluetoothProfile.STATE_CONNECTED &&
                cachedDevices.contains(cachedDevice)
            ) {
                context.mainExecutor.execute { refresh() }
            }
        }
    }

    private val deviceCallback: CachedBluetoothDevice.Callback =
        object : CachedBluetoothDevice.Callback {
            override fun onDeviceAttributesChanged() {
                cachedDevices.forEach { device -> device.unregisterCallback(this) }
                loadDevice(cachedDevice)
                cachedDevices.forEach { device ->
                    device.registerCallback(
                        ThreadUtils.getBackgroundExecutor(),
                        this
                    )
                }
            }
        }

    init {
        presetLayout.setListener(this)
    }

    /**
     * Loads all devices in the same set as [cachedDevice] and prepares the UI.
     *
     * This method ensures the UI is visible only if the device supports HAP, identifies all
     * valid devices in the same set and populates the internal device map.
     *
     * @param cachedDevice The remote device.
     */
    fun loadDevice(cachedDevice: CachedBluetoothDevice?) {
        if (DEBUG) {
            Log.d(TAG, "loadDevice, device=$cachedDevice")
        }
        this.cachedDevice = cachedDevice
        sideToDeviceMap.clear()
        cachedDevices.clear()
        if (cachedDevice == null
            || cachedDevice.profiles.stream().noneMatch { p -> p is HapClientProfile }
        ) {
            context.mainExecutor.execute { presetLayout.setVisible(false) }
            return
        }

        // load devices in the same set
        if (VALID_SIDES.contains(cachedDevice.deviceSide) && cachedDevice.bondState == BOND_BONDED) {
            sideToDeviceMap[cachedDevice.deviceSide] = cachedDevice.device
            cachedDevices.add(cachedDevice)
        }
        for (memberDevice in cachedDevice.memberDevice) {
            if (VALID_SIDES.contains(memberDevice.deviceSide) &&
                memberDevice.bondState == BOND_BONDED
            ) {
                sideToDeviceMap[memberDevice.deviceSide] = memberDevice.device
                cachedDevices.add(memberDevice)
            }
        }

        context.mainExecutor.execute {
            presetLayout.setupControls(sideToDeviceMap.keys)
            if (started) {
                refresh()
            }
        }
    }

    /**
     * Starts the controller.
     *
     * This method registers the necessary callbacks to begin listening for remote events.
     * It should be called when the UI is active and ready to process events.
     */
    fun start() {
        if (started) {
            return
        }
        started = true
        eventManager.registerCallback(bluetoothCallback)
        cachedDevices.forEach { device: CachedBluetoothDevice ->
            device.registerCallback(ThreadUtils.getBackgroundExecutor(), deviceCallback)
        }
        presetController.registerCallback(ThreadUtils.getBackgroundExecutor())
        refresh()
    }

    /**
     * Stops the controller.
     *
     * This method unregisters the callbacks and should be called when the UI is no longer
     * active or does not need to listen for events.
     */
    fun stop() {
        if (!started) {
            return
        }
        started = false
        eventManager.unregisterCallback(bluetoothCallback)
        cachedDevices.forEach { device: CachedBluetoothDevice ->
            device.unregisterCallback(deviceCallback)
        }
        presetController.unregisterCallback()
    }

    /**
     * Refreshes the preset UI with the latest data from the remote devices.
     *
     * This method queries the preset lists from remote and updates the UI accordingly.
     * The UI will display controls for each side if the preset lists differ, and a unified
     * control otherwise.
     */
    fun refresh() {
        val leftDevice: BluetoothDevice? = sideToDeviceMap[SIDE_LEFT]
        val rightDevice: BluetoothDevice? = sideToDeviceMap[SIDE_RIGHT]
        val leftList: List<BluetoothHapPresetInfo> = presetController.getPresetInfos(leftDevice)
        val rightList: List<BluetoothHapPresetInfo> = presetController.getPresetInfos(rightDevice)

        context.mainExecutor.execute {
            if (leftList.isEmpty() && rightList.isEmpty()) {
                presetLayout.setVisible(false)
                return@execute
            }
            presetLayout.setVisible(true)
            if (leftDevice != null && rightDevice != null && isDifferentPresetList(
                    leftList,
                    rightList
                )
            ) {
                loadDataToControl(SIDE_LEFT, leftDevice, leftList)
                loadDataToControl(SIDE_RIGHT, rightDevice, rightList)
            } else {
                val device = cachedDevice?.device
                if (device != null) {
                    loadDataToControl(SIDE_UNIFIED, device, null)
                }
            }
        }
    }

    private fun loadDataToControl(
        side: Int,
        device: BluetoothDevice,
        presetInfos: List<BluetoothHapPresetInfo>?
    ) {
        presetLayout.setControlExpanded(side != SIDE_UNIFIED)
        presetLayout.setControlEnabled(side, device.isConnected)
        val finalPresetInfos = presetInfos ?: presetController.getPresetInfos(device)
        val activePresetIndex = presetController.getActivePreset(device)
        presetLayout.setControlList(side, finalPresetInfos)
        presetLayout.setControlValue(side, activePresetIndex)
    }

    private fun isDifferentPresetList(
        list1: List<BluetoothHapPresetInfo>,
        list2: List<BluetoothHapPresetInfo>
    ): Boolean {
        if (list1.size != list2.size) {
            return true
        }
        return list1.zip(list2).any { (preset1, preset2) ->
            preset1.name != preset2.name || preset1.index != preset2.index
        }
    }

    override fun onHapClientServiceConnected() {
        refresh()
    }

    override fun onPresetChangedFromRemote(device: BluetoothDevice, presetIndex: Int) {
        if (sideToDeviceMap.containsValue(device)) {
            val side = if (presetLayout.isControlExpanded()) {
                sideToDeviceMap.inverse().getOrDefault(device, SIDE_INVALID)
            } else {
                SIDE_UNIFIED
            }
            context.mainExecutor.execute { presetLayout.setControlValue(side, presetIndex) }
        }
    }

    override fun onPresetInfoChangedFromRemote(
        device: BluetoothDevice,
        presetInfos: List<BluetoothHapPresetInfo>
    ) {
        if (sideToDeviceMap.containsValue(device)) {
            val side = if (presetLayout.isControlExpanded()) {
                sideToDeviceMap.inverse().getOrDefault(device, SIDE_INVALID)
            } else {
                SIDE_UNIFIED
            }
            context.mainExecutor.execute { presetLayout.setControlList(side, presetInfos) }
        }
    }

    override fun onPresetGroupSelectionFailedFromRemote(hapGroupId: Int) {
        val device = cachedDevice?.device
        if (device != null) {
            if (hapGroupId == presetController.getHapGroupId(device)) {
                // Try to set the preset independently if group operation failed
                val selectedIndex = presetLayout.getControlValue(SIDE_UNIFIED)
                for (memberDevice in sideToDeviceMap.values) {
                    presetController.selectPreset(memberDevice, selectedIndex)
                }
            }
        }
    }

    override fun onCommandFailedFromRemote() {
        refresh()
        showErrorToast()
    }

    override fun onPresetChangedFromUi(side: Int, value: Int) {
        if (side == SIDE_UNIFIED) {
            val device = cachedDevice?.device
            if (device != null) {
                val hapGroupId = presetController.getHapGroupId(device)
                if (presetController.supportsSynchronizedPresets(device) &&
                    hapGroupId != BluetoothCsipSetCoordinator.GROUP_ID_INVALID
                ) {
                    presetController.selectPresetForGroup(hapGroupId, value)
                } else {
                    for (memberDevice in sideToDeviceMap.values) {
                        presetController.selectPreset(memberDevice, value)
                    }
                }
            }
        } else {
            val device: BluetoothDevice? = sideToDeviceMap[side]
            if (device != null) {
                presetController.selectPreset(device, value)
            }
        }
    }

    private fun showErrorToast() {
        context.mainExecutor.execute {
            toast?.cancel()
            toast = Toast.makeText(
                context,
                R.string.bluetooth_hearing_aids_presets_error,
                Toast.LENGTH_SHORT
            )
            toast?.show()
        }
    }

    companion object {
        private const val DEBUG = true
        private const val TAG = "PresetUiController"
    }
}