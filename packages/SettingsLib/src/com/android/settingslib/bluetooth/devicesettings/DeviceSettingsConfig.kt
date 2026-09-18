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

package com.android.settingslib.bluetooth.devicesettings

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable

/**
 * A data class representing a bluetooth device details config.
 *
 * @property mainContentItems The setting items to be shown in main page.
 * @property moreSettingsItems The setting items to be shown in more settings page.
 * @property moreSettingsHelpItem The help item displayed on the top right corner of the page.
 * @property extras Extra bundle
 */
data class DeviceSettingsConfig(
    val mainContentItems: List<DeviceSettingItem>,
    val moreSettingsItems: List<DeviceSettingItem>,
    val moreSettingsHelpItem: DeviceSettingItem? = null,
    val settingGroups: List<DeviceSettingGroup>? = null,
    val extras: Bundle = Bundle.EMPTY,
) : Parcelable {
    private val processedExtras: Bundle = Bundle(extras).apply {
        if (settingGroups != null) {
            putParcelableArrayList(SETTING_GROUPS_KEY, ArrayList(settingGroups))
        }
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.run {
            writeTypedList(mainContentItems)
            writeTypedList(moreSettingsItems)
            writeParcelable(moreSettingsHelpItem, flags)
            writeBundle(processedExtras)
        }
    }

    companion object {
        private const val SETTING_GROUPS_KEY = "settingGroups"

        @JvmField
        val CREATOR: Parcelable.Creator<DeviceSettingsConfig> =
            object : Parcelable.Creator<DeviceSettingsConfig> {
                override fun createFromParcel(parcel: Parcel): DeviceSettingsConfig = parcel.run {
                    val mainContentItems = arrayListOf<DeviceSettingItem>().also {
                        readTypedList(
                            it, DeviceSettingItem.CREATOR
                        )
                    }
                    val moreSettingsItems = arrayListOf<DeviceSettingItem>().also {
                        readTypedList(
                            it, DeviceSettingItem.CREATOR
                        )
                    }
                    val moreSettingsHelpItem: DeviceSettingItem? =
                        readParcelable(DeviceSettingItem::class.java.classLoader)
                    val extras = readBundle((Bundle::class.java.classLoader)) ?: Bundle.EMPTY
                    extras.classLoader = DeviceSettingGroup::class.java.classLoader
                    val settingGroups: List<DeviceSettingGroup>? = extras.getParcelableArrayList(
                        SETTING_GROUPS_KEY, DeviceSettingGroup::class.java
                    )
                    return DeviceSettingsConfig(
                        mainContentItems,
                        moreSettingsItems,
                        moreSettingsHelpItem,
                        settingGroups,
                        extras,
                    )
                }

                override fun newArray(size: Int): Array<DeviceSettingsConfig?> {
                    return arrayOfNulls(size)
                }
            }
    }
}
