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

package com.android.systemui.volume.panel.component.mediaoutput.domain.interactor

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.graphics.drawable.Drawable
import android.graphics.drawable.TestStubDrawable
import com.android.settingslib.bluetooth.CachedBluetoothDevice
import com.android.settingslib.bluetooth.LeAudioProfile
import com.android.settingslib.media.BluetoothMediaDevice
import com.android.settingslib.media.MediaDevice
import com.android.settingslib.media.PhoneMediaDevice
import com.android.systemui.util.mockito.mock
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.stub

@SuppressLint("StaticFieldLeak") // These are mocks
object TestMediaDevicesFactory {

    fun builtInMediaDevice(
        deviceName: String = "built_in_media",
        deviceIcon: Drawable? = TestStubDrawable(),
    ) =
        mock<MediaDevice>().stub {
            on { name } doReturn deviceName
            on { icon } doReturn deviceIcon
            on { deviceType } doReturn MediaDevice.MediaDeviceType.TYPE_PHONE_DEVICE
        }

    fun wiredMediaDevice(
        deviceName: String = "wired_media",
        deviceIcon: Drawable? = TestStubDrawable(),
    ) =
        mock<PhoneMediaDevice>().stub {
            on { name } doReturn deviceName
            on { icon } doReturn deviceIcon
            on { deviceType } doReturn MediaDevice.MediaDeviceType.TYPE_3POINT5_MM_AUDIO_DEVICE
        }

    fun bluetoothMediaDevice(
        deviceName: String = "bt_media",
        deviceIcon: Drawable? = TestStubDrawable(),
        deviceAddress: String = "bt_media_device",
    ): BluetoothMediaDevice {
        val bluetoothDevice =
            mock<BluetoothDevice>().stub {
                on { name } doReturn deviceName
                on { address } doReturn deviceAddress
            }
        val leAudioProfile =
            mock<LeAudioProfile>().stub {
                on { profileId } doReturn BluetoothProfile.LE_AUDIO
                on { isEnabled(bluetoothDevice) } doReturn true
            }
        val cachedBluetoothDevice =
            mock<CachedBluetoothDevice>().stub {
                on { name } doReturn deviceName
                on { address } doReturn deviceAddress
                on { device } doReturn bluetoothDevice
                on { isHearingDevice } doReturn true
                on { profiles } doReturn listOf(leAudioProfile)
            }
        return mock<BluetoothMediaDevice>().stub {
            on { name } doReturn deviceName
            on { icon } doReturn deviceIcon
            on { cachedDevice } doReturn cachedBluetoothDevice
            on { deviceType } doReturn MediaDevice.MediaDeviceType.TYPE_BLUETOOTH_DEVICE
        }
    }

    fun remoteMediaDevice(
        deviceName: String = "remote_media",
        deviceIcon: Drawable? = TestStubDrawable(),
    ) =
        mock<MediaDevice>().stub {
            on { name } doReturn deviceName
            on { icon } doReturn deviceIcon
            on { deviceType } doReturn MediaDevice.MediaDeviceType.TYPE_CAST_DEVICE
        }
}
