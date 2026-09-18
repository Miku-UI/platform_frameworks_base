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

package com.android.settingslib.bluetooth.devicesettings

import android.os.Bundle
import android.os.Parcel
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeviceSettingGroupTest {

    @Test
    fun parcelOperation() {
        val group =
            DeviceSettingGroup(
                preferenceCategoryTitle = "group title",
                extras = Bundle().apply { putString("key1", "value1") },
            )

        val fromParcel = writeAndRead(group)

        assertThat(fromParcel.preferenceCategoryTitle).isEqualTo(group.preferenceCategoryTitle)
        assertThat(fromParcel.extras.getString("key1")).isEqualTo(group.extras.getString("key1"))
    }

    private fun writeAndRead(group: DeviceSettingGroup): DeviceSettingGroup {
        val parcel = Parcel.obtain()
        group.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        return DeviceSettingGroup.CREATOR.createFromParcel(parcel)
    }
}
