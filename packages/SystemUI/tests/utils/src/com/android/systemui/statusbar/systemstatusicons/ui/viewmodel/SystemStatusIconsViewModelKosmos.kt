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

import android.content.Context
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.statusbar.systemstatusicons.airplane.ui.viewmodel.airplaneModeIconViewModelFactory
import com.android.systemui.statusbar.systemstatusicons.alarm.ui.viewmodel.nextAlarmIconViewModelFactory
import com.android.systemui.statusbar.systemstatusicons.bluetooth.ui.viewmodel.bluetoothIconViewModelFactory
import com.android.systemui.statusbar.systemstatusicons.connecteddisplay.ui.viewmodel.connectedDisplayIconViewModelFactory
import com.android.systemui.statusbar.systemstatusicons.datasaver.ui.viewmodel.dataSaverIconViewModelFactory
import com.android.systemui.statusbar.systemstatusicons.ethernet.ui.viewmodel.ethernetIconViewModelFactory
import com.android.systemui.statusbar.systemstatusicons.hotspot.ui.viewmodel.hotspotIconViewModelFactory
import com.android.systemui.statusbar.systemstatusicons.mobile.ui.viewmodel.mobileSystemStatusIconsViewModelFactory
import com.android.systemui.statusbar.systemstatusicons.profile.ui.viewmodel.managedProfileIconViewModelFactory
import com.android.systemui.statusbar.systemstatusicons.ringer.ui.viewmodel.muteIconViewModelFactory
import com.android.systemui.statusbar.systemstatusicons.ringer.ui.viewmodel.vibrateIconViewModelFactory
import com.android.systemui.statusbar.systemstatusicons.vpn.ui.viewmodel.vpnIconViewModelFactory
import com.android.systemui.statusbar.systemstatusicons.wifi.ui.viewmodel.wifiIconViewModelFactory
import com.android.systemui.statusbar.systemstatusicons.zenmode.ui.viewmodel.zenModeIconViewModelFactory

val Kosmos.systemStatusIconsViewModelFactory by
    Kosmos.Fixture {
        object : SystemStatusIconsViewModel.Factory {
            override fun create(context: Context): SystemStatusIconsViewModel =
                SystemStatusIconsViewModel(
                    context = context,
                    orderedIconSlotNamesInteractor = orderedIconSlotNamesInteractor,
                    airplaneModeIconViewModelFactory = airplaneModeIconViewModelFactory,
                    bluetoothIconViewModelFactory = bluetoothIconViewModelFactory,
                    connectedDisplayIconViewModelFactory = connectedDisplayIconViewModelFactory,
                    dataSaverIconViewModelFactory = dataSaverIconViewModelFactory,
                    ethernetIconViewModelFactory = ethernetIconViewModelFactory,
                    hotspotIconViewModelFactory = hotspotIconViewModelFactory,
                    managedProfileIconViewModelFactory = managedProfileIconViewModelFactory,
                    mobileSystemStatusIconsViewModelFactory =
                        mobileSystemStatusIconsViewModelFactory,
                    muteIconViewModelFactory = muteIconViewModelFactory,
                    nextAlarmIconViewModelFactory = nextAlarmIconViewModelFactory,
                    vibrateIconViewModelFactory = vibrateIconViewModelFactory,
                    vpnIconViewModelFactory = vpnIconViewModelFactory,
                    wifiIconViewModelFactory = wifiIconViewModelFactory,
                    zenModeIconViewModelFactory = zenModeIconViewModelFactory,
                )
        }
    }
