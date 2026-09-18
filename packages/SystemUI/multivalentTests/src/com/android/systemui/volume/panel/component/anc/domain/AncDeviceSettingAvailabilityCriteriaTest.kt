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

package com.android.systemui.volume.panel.component.anc.domain

import android.content.res.Resources
import android.media.AudioManager
import android.testing.TestableLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.settingslib.bluetooth.devicesettings.DeviceSettingId
import com.android.settingslib.bluetooth.devicesettings.shared.model.DeviceSettingIcon
import com.android.settingslib.bluetooth.devicesettings.shared.model.DeviceSettingModel
import com.android.settingslib.bluetooth.devicesettings.shared.model.DeviceSettingStateModel
import com.android.settingslib.bluetooth.devicesettings.shared.model.ToggleModel
import com.android.systemui.SysuiTestCase
import com.android.systemui.bluetooth.devicesettings.data.repository.fakeDeviceSettingRepository
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmosNew
import com.android.systemui.volume.data.repository.audioRepository
import com.android.systemui.volume.domain.interactor.audioOutputInteractor
import com.android.systemui.volume.domain.model.AudioOutputDevice
import com.android.systemui.volume.localMediaController
import com.android.systemui.volume.localMediaRepository
import com.android.systemui.volume.mediaControllerRepository
import com.android.systemui.volume.panel.component.mediaoutput.domain.interactor.TestMediaDevicesFactory
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
class AncDeviceSettingAvailabilityCriteriaTest : SysuiTestCase() {

    private val kosmos =
        testKosmosNew().apply {
            audioRepository.setMode(AudioManager.MODE_NORMAL)
            mediaControllerRepository.setActiveSessions(listOf(localMediaController))
            localMediaRepository.updateCurrentConnectedDevice(
                TestMediaDevicesFactory.bluetoothMediaDevice()
            )
        }

    private val underTest: AncDeviceSettingAvailabilityCriteria by lazy {
        kosmos.ancDeviceSettingAvailabilityCriteria
    }

    @Test
    fun currentDeviceNotBluetooth_unavailable() =
        kosmos.runTest {
            val isAvailable by collectLastValue(underTest.isAvailable())

            localMediaRepository.updateCurrentConnectedDevice(
                TestMediaDevicesFactory.builtInMediaDevice()
            )

            assertThat(isAvailable).isFalse()
        }

    @Test
    fun hasSettings_available() =
        kosmos.runTest {
            val device by collectLastValue(audioOutputInteractor.currentAudioDevice)
            val isAvailable by collectLastValue(underTest.isAvailable())

            val cachedBtDevice = (device as AudioOutputDevice.Bluetooth).cachedBluetoothDevice
            fakeDeviceSettingRepository.updateDeviceSetting(
                cachedBtDevice,
                DeviceSettingId.DEVICE_SETTING_ID_ANC,
            ) {
                DeviceSettingModel.MultiTogglePreference(
                    cachedDevice = cachedBtDevice,
                    id = DeviceSettingId.DEVICE_SETTING_ID_ANC,
                    title = "Noise cancellation",
                    toggles =
                        listOf(
                            ToggleModel(
                                label = "Noise cancellation",
                                icon = DeviceSettingIcon.ResourceIcon(Resources.ID_NULL),
                            ),
                            ToggleModel(
                                label = "Off",
                                icon = DeviceSettingIcon.ResourceIcon(Resources.ID_NULL),
                            ),
                            ToggleModel(
                                label = "Transparency",
                                icon = DeviceSettingIcon.ResourceIcon(Resources.ID_NULL),
                            ),
                        ),
                    isActive = true,
                    state = DeviceSettingStateModel.MultiTogglePreferenceState(1),
                    isAllowedChangingState = true,
                    updateState = {},
                )
            }

            assertThat(isAvailable).isTrue()
        }

    @Test
    fun noSettings_unavailable() =
        kosmos.runTest {
            val device by collectLastValue(audioOutputInteractor.currentAudioDevice)
            val isAvailable by collectLastValue(underTest.isAvailable())

            fakeDeviceSettingRepository.updateDeviceSetting(
                (device as AudioOutputDevice.Bluetooth).cachedBluetoothDevice,
                DeviceSettingId.DEVICE_SETTING_ID_ANC,
            ) {
                null
            }

            assertThat(isAvailable).isFalse()
        }
}
