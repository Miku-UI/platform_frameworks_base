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

package com.android.systemui.bluetooth.devicesettings.data.repository

import com.android.settingslib.bluetooth.CachedBluetoothDevice
import com.android.settingslib.bluetooth.devicesettings.data.repository.DeviceSettingRepository
import com.android.settingslib.bluetooth.devicesettings.shared.model.DeviceSettingConfigModel
import com.android.settingslib.bluetooth.devicesettings.shared.model.DeviceSettingModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FakeDeviceSettingRepository : DeviceSettingRepository {

    private val settingsConfigs = mutableMapOf<CachedBluetoothDevice, DeviceSettingConfigModel>()
    private val deviceSettings =
        mutableMapOf<DeviceSettingsKey, MutableStateFlow<DeviceSettingModel?>>()

    override suspend fun getDeviceSettingsConfig(
        cachedDevice: CachedBluetoothDevice
    ): DeviceSettingConfigModel? {
        return settingsConfigs[cachedDevice]
    }

    fun setDeviceSettingsConfig(
        cachedDevice: CachedBluetoothDevice,
        config: DeviceSettingConfigModel,
    ) {
        settingsConfigs[cachedDevice] = config
    }

    override fun getDeviceSetting(
        cachedDevice: CachedBluetoothDevice,
        settingId: Int,
    ): Flow<DeviceSettingModel?> =
        deviceSettings
            .getOrPut(DeviceSettingsKey(cachedDevice, settingId)) { MutableStateFlow(null) }
            .asStateFlow()

    fun updateDeviceSetting(
        cachedDevice: CachedBluetoothDevice,
        settingId: Int,
        update: (DeviceSettingModel?) -> DeviceSettingModel?,
    ) {
        deviceSettings
            .getOrPut(DeviceSettingsKey(cachedDevice, settingId)) { MutableStateFlow(null) }
            .update(update)
    }

    private data class DeviceSettingsKey(
        val cachedDevice: CachedBluetoothDevice,
        val settingId: Int,
    )
}
