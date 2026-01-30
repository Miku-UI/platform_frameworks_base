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

package com.android.systemui.statusbar.policy.bluetooth.domain.interactor

import com.android.settingslib.bluetooth.CachedBluetoothDevice
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.policy.bluetooth.data.repository.BluetoothRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.map

/** Interactor to manage and provide an observable state of Bluetooth connectivity. */
@SysUISingleton
class BluetoothConnectionStatusInteractor @Inject constructor(repository: BluetoothRepository) {
    private val maxConnectionState =
        repository.connectedDevices.map { devices -> calculateMaxConnectionState(devices) }

    val isBluetoothConnected =
        maxConnectionState.map { it == android.bluetooth.BluetoothProfile.STATE_CONNECTED }

    private fun calculateMaxConnectionState(devices: List<CachedBluetoothDevice>): Int {
        if (devices.isEmpty()) {
            return android.bluetooth.BluetoothProfile.STATE_DISCONNECTED
        }
        return devices.maxOfOrNull { it.maxConnectionState }
            ?: android.bluetooth.BluetoothProfile.STATE_DISCONNECTED
    }
}
