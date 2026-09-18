/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.settingslib.bluetooth.devicesettings.shared.model

import com.android.settingslib.bluetooth.devicesettings.DeviceSettingId

/** Models a device setting config. */
data class DeviceSettingConfigModel(
    /** Items need to be shown in device details main page. */
    val mainItems: DeviceSettingLayout,
    /** Items need to be shown in device details more settings page. */
    val moreSettingsItems: DeviceSettingLayout,
    /**
     * Help button which need to be shown on the top right corner of device details more settings
     * page.
     */
    val moreSettingsHelpItem: DeviceSettingConfigNodeModel.Item?,
)

data class DeviceSettingLayout(val nodes: List<DeviceSettingConfigNodeModel> = emptyList())

sealed interface DeviceSettingConfigNodeModel {
    /** Models a device setting group in config. */
    data class Group(
        val key: String,
        val preferenceCategoryTitle: String?,
        val children: List<Item>,
    ) : DeviceSettingConfigNodeModel

    /** Models a device setting item in config. */
    sealed interface Item : DeviceSettingConfigNodeModel {
        @DeviceSettingId
        val settingId: Int
        val highlighted: Boolean

        /** A built-in item in Settings. */
        sealed interface BuiltinItem : Item {
            @DeviceSettingId
            override val settingId: Int
            val preferenceKey: String

            /** A general built-in item in Settings. */
            data class CommonBuiltinItem(
                @param:DeviceSettingId override val settingId: Int,
                override val highlighted: Boolean,
                override val preferenceKey: String,
            ) : BuiltinItem

            /** A bluetooth profiles in Settings. */
            data class BluetoothProfilesItem(
                @param:DeviceSettingId override val settingId: Int,
                override val highlighted: Boolean,
                override val preferenceKey: String,
                val invisibleProfiles: List<String>,
            ) : BuiltinItem
        }

        /** A remote item provided by other apps. */
        data class AppProvidedItem(
            @param:DeviceSettingId override val settingId: Int,
            override val highlighted: Boolean,
        ) : Item
    }
}
