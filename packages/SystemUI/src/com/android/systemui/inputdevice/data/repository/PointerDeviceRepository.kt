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

package com.android.systemui.inputdevice.data.repository

import android.hardware.input.InputManager
import android.view.InputDevice.SOURCE_MOUSE
import android.view.InputDevice.SOURCE_TOUCHPAD
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

interface PointerDeviceRepository {
    /**
     * Emits true if any pointer device (e.g. mouse or touchpad) is connected to the device, false
     * otherwise.
     */
    val isAnyPointerDeviceConnected: Flow<Boolean>
}

@SysUISingleton
class PointerDeviceRepositoryImpl
@Inject
constructor(
    @Background private val backgroundDispatcher: CoroutineDispatcher,
    private val inputManager: InputManager,
    inputDeviceRepository: InputDeviceRepository,
) : PointerDeviceRepository {

    override val isAnyPointerDeviceConnected: Flow<Boolean> =
        inputDeviceRepository.deviceChange
            .map { (ids, _) -> ids.any { id -> isPointerDeviceEnabled(id) } }
            .distinctUntilChanged()
            .flowOn(backgroundDispatcher)

    private fun isPointerDeviceEnabled(deviceId: Int): Boolean {
        val device = inputManager.getInputDevice(deviceId) ?: return false
        return device.isEnabled &&
            (device.supportsSource(SOURCE_MOUSE) || device.supportsSource(SOURCE_TOUCHPAD))
    }

    companion object {
        const val TAG = "PointerDeviceRepositoryImpl"
    }
}
