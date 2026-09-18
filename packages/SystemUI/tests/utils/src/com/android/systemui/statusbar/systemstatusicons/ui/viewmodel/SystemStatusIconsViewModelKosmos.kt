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

package com.android.systemui.statusbar.systemstatusicons.ui.viewmodel

import android.app.AlarmManager
import android.app.AutomaticZenRule
import android.app.PendingIntent
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.view.Display.TYPE_EXTERNAL
import com.android.settingslib.bluetooth.CachedBluetoothDevice
import com.android.settingslib.notification.modes.TestModeBuilder
import com.android.settingslib.volume.shared.model.RingerMode
import com.android.systemui.display.data.repository.display
import com.android.systemui.display.data.repository.displayRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.audio.audioManager
import com.android.systemui.statusbar.systemstatusicons.domain.interactor.SystemStatusIconBlocklistInteractor
import com.android.systemui.statusbar.getCommandQueueCallback
import com.android.systemui.statusbar.pipeline.airplane.data.repository.airplaneModeRepository
import com.android.systemui.statusbar.pipeline.mobile.domain.interactor.fakeMobileIconsInteractor
import com.android.systemui.statusbar.pipeline.satellite.data.repository.deviceBasedSatelliteRepository
import com.android.systemui.statusbar.pipeline.satellite.shared.model.SatelliteConnectionState
import com.android.systemui.statusbar.pipeline.shared.data.repository.connectivityRepository
import com.android.systemui.statusbar.pipeline.shared.data.repository.fake
import com.android.systemui.statusbar.pipeline.wifi.data.repository.fakeWifiRepository
import com.android.systemui.statusbar.pipeline.wifi.shared.model.WifiNetworkModel
import com.android.systemui.statusbar.policy.bluetooth.data.repository.bluetoothRepository
import com.android.systemui.statusbar.policy.data.repository.fakeZenModeRepository
import com.android.systemui.statusbar.policy.domain.interactor.fakeTtyStatusInteractor
import com.android.systemui.statusbar.policy.fakeDataSaverController
import com.android.systemui.statusbar.policy.fakeHotspotController
import com.android.systemui.statusbar.policy.fakeNextAlarmController
import com.android.systemui.statusbar.policy.profile.data.repository.managedProfileRepository
import com.android.systemui.statusbar.policy.profile.shared.model.ProfileInfo
import com.android.systemui.statusbar.policy.vpn.data.repository.vpnRepository
import com.android.systemui.statusbar.policy.vpn.shared.model.VpnState
import com.android.systemui.statusbar.systemstatusicons.airplane.ui.viewmodel.airplaneModeIconViewModelFactory
import com.android.systemui.statusbar.systemstatusicons.alarm.ui.viewmodel.nextAlarmIconViewModelFactory
import com.android.systemui.statusbar.systemstatusicons.bluetooth.ui.viewmodel.bluetoothIconViewModelFactory
import com.android.systemui.statusbar.systemstatusicons.connecteddisplay.ui.viewmodel.connectedDisplayIconViewModelFactory
import com.android.systemui.statusbar.systemstatusicons.data.repository.ExternalSystemStatusIconRepositoryHelper.createStatusBarIcon
import com.android.systemui.statusbar.systemstatusicons.datasaver.ui.viewmodel.dataSaverIconViewModelFactory
import com.android.systemui.statusbar.systemstatusicons.devicesatellite.ui.viewmodel.deviceBasedSatelliteIconViewModelFactory
import com.android.systemui.statusbar.systemstatusicons.domain.interactor.externalSystemStatusIconInteractor
import com.android.systemui.statusbar.systemstatusicons.ethernet.ui.viewmodel.ethernetIconViewModelFactory
import com.android.systemui.statusbar.systemstatusicons.headset.ui.viewmodel.headsetIconViewModelFactory
import com.android.systemui.statusbar.systemstatusicons.hotspot.ui.viewmodel.hotspotIconViewModelFactory
import com.android.systemui.statusbar.systemstatusicons.mobile.ui.viewmodel.mobileSystemStatusIconsViewModelFactory
import com.android.systemui.statusbar.systemstatusicons.profile.ui.viewmodel.managedProfileIconViewModelFactory
import com.android.systemui.statusbar.systemstatusicons.ringer.ui.viewmodel.muteIconViewModelFactory
import com.android.systemui.statusbar.systemstatusicons.ringer.ui.viewmodel.vibrateIconViewModelFactory
import com.android.systemui.statusbar.systemstatusicons.tty.ui.viewmodel.ttyIconViewModelFactory
import com.android.systemui.statusbar.systemstatusicons.vpn.ui.viewmodel.vpnIconViewModelFactory
import com.android.systemui.statusbar.systemstatusicons.wifi.ui.viewmodel.wifiIconViewModelFactory
import com.android.systemui.statusbar.systemstatusicons.zenmode.ui.viewmodel.zenModeIconViewModelFactory
import com.android.systemui.volume.data.repository.fakeAudioRepository
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

val Kosmos.systemStatusIconsViewModelFactory by
    Kosmos.Fixture {
        object : SystemStatusIconsViewModelImpl.Factory {
            override fun create(
                context: Context,
                systemStatusIconBlocklistInteractor: SystemStatusIconBlocklistInteractor,
            ): SystemStatusIconsViewModelImpl =
                SystemStatusIconsViewModelImpl(
                    context = context,
                    systemStatusIconBlocklistInteractor = systemStatusIconBlocklistInteractor,
                    orderedIconSlotNamesInteractor = orderedIconSlotNamesInteractor,
                    externalSystemStatusIconInteractor = externalSystemStatusIconInteractor,
                    airplaneModeIconViewModelFactory = airplaneModeIconViewModelFactory,
                    bluetoothIconViewModelFactory = bluetoothIconViewModelFactory,
                    connectedDisplayIconViewModelFactory = connectedDisplayIconViewModelFactory,
                    dataSaverIconViewModelFactory = dataSaverIconViewModelFactory,
                    deviceBasedSatelliteIconViewModelFactory =
                        deviceBasedSatelliteIconViewModelFactory,
                    ethernetIconViewModelFactory = ethernetIconViewModelFactory,
                    headsetIconsViewModelFactory = headsetIconViewModelFactory,
                    hotspotIconViewModelFactory = hotspotIconViewModelFactory,
                    managedProfileIconViewModelFactory = managedProfileIconViewModelFactory,
                    mobileSystemStatusIconsViewModelFactory =
                        mobileSystemStatusIconsViewModelFactory,
                    muteIconViewModelFactory = muteIconViewModelFactory,
                    nextAlarmIconViewModelFactory = nextAlarmIconViewModelFactory,
                    ttyIconViewModelFactory = ttyIconViewModelFactory,
                    vibrateIconViewModelFactory = vibrateIconViewModelFactory,
                    vpnIconViewModelFactory = vpnIconViewModelFactory,
                    wifiIconViewModelFactory = wifiIconViewModelFactory,
                    zenModeIconViewModelFactory = zenModeIconViewModelFactory,
                )
        }
    }

object SystemStatusIconsViewModelHelper {
    suspend fun Kosmos.showAirplaneMode() {
        airplaneModeRepository.setIsAirplaneMode(true)
    }

    suspend fun Kosmos.hideAirplaneMode() {
        airplaneModeRepository.setIsAirplaneMode(false)
    }

    fun Kosmos.showBluetooth() {
        bluetoothRepository.setConnectedDevices(
            listOf(
                mock<CachedBluetoothDevice>().apply {
                    whenever(isConnected).thenReturn(true)
                    whenever(maxConnectionState).thenReturn(BluetoothProfile.STATE_CONNECTED)
                }
            )
        )
    }

    suspend fun Kosmos.showConnectedDisplay() {
        displayRepository.addDisplay(display(type = TYPE_EXTERNAL, id = 1))
    }

    fun Kosmos.showEthernet() {
        connectivityRepository.fake.setEthernetConnected(default = true, validated = true)
    }

    fun Kosmos.showMute() {
        fakeAudioRepository.setRingerMode(RingerMode(AudioManager.RINGER_MODE_SILENT))
    }

    fun Kosmos.showNextAlarm() {
        val alarmClockInfo = AlarmManager.AlarmClockInfo(1L, mock<PendingIntent>())
        fakeNextAlarmController.setNextAlarm(alarmClockInfo)
    }

    fun Kosmos.showVibrate() {
        fakeAudioRepository.setRingerMode(RingerMode(AudioManager.RINGER_MODE_VIBRATE))
    }

    fun Kosmos.showWifi() {
        fakeWifiRepository.setIsWifiEnabled(true)
        val testNetwork = WifiNetworkModel.Active.of(level = 4, ssid = "TestWifi")
        fakeWifiRepository.setWifiNetwork(testNetwork)
        connectivityRepository.fake.setWifiConnected()
    }

    fun Kosmos.showZenMode() {
        val modeId = "zenModeTestRule"
        val modeName = "Test Zen Mode"
        val mode =
            TestModeBuilder()
                .setId(modeId)
                .setName(modeName)
                .setType(AutomaticZenRule.TYPE_DRIVING)
                .setActive(true)
                .build()
        fakeZenModeRepository.clearModes()
        fakeZenModeRepository.addMode(mode)
    }

    fun Kosmos.showHotspot() {
        fakeHotspotController.isHotspotEnabled = true
    }

    fun Kosmos.showDataSaver() {
        fakeDataSaverController.setDataSaverEnabled(true)
    }

    fun Kosmos.showVpn() {
        vpnRepository.vpnState.value = VpnState(isEnabled = true)
    }

    fun Kosmos.showManagedProfile() {
        managedProfileRepository.currentProfileInfo.value =
            ProfileInfo(
                userId = 10,
                iconResId = com.android.internal.R.drawable.stat_sys_managed_profile_status,
                contentDescription = "Work profile",
            )
    }

    fun Kosmos.showTty() {
        fakeTtyStatusInteractor.isEnabled.value = true
    }

    suspend fun Kosmos.showSatelliteIcon() {
        hideAirplaneMode()
        fakeWifiRepository.setIsWifiEnabled(false)
        fakeWifiRepository.setWifiNetwork(WifiNetworkModel.Inactive())
        val mobileInteractor = fakeMobileIconsInteractor.getMobileConnectionInteractorForSubId(1)
        mobileInteractor.isInService.value = false
        mobileInteractor.isEmergencyOnly.value = false

        deviceBasedSatelliteRepository.isSatelliteProvisioned.value = true
        deviceBasedSatelliteRepository.isSatelliteAllowedForCurrentLocation.value = true
        deviceBasedSatelliteRepository.connectionState.value = SatelliteConnectionState.Connected
    }

    fun Kosmos.showHeadset() {
        val device =
            mock<AudioDeviceInfo>().also {
                whenever(it.type).thenReturn(AudioDeviceInfo.TYPE_WIRED_HEADSET)
            }
        audioManager.setDevices(arrayOf(device))
    }

    fun Kosmos.showExternalIcon(slotName: String) {
        val icon = createStatusBarIcon()
        getCommandQueueCallback().setIcon(slotName, icon)
    }
}
