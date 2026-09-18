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
package com.android.server.companion.datatransfer.crossdevicesync.feature.airplanemode.fake

/** Fake implementation of [AirplaneModeController] for testing. */
import com.android.server.companion.datatransfer.crossdevicesync.feature.airplanemode.AirplaneModeController
import java.util.concurrent.atomic.AtomicBoolean

/** A Fake implementation of [AirplaneModeController]. */
class FakeAirplaneModeController : AirplaneModeController {
    private val airplaneModeState = AtomicBoolean(false)
    private val airplaneModeSyncEnabledState = AtomicBoolean(true)
    private val listeners = mutableListOf<AirplaneModeController.Listener>()

    override fun isAirplaneModeEnabled(): Boolean {
        return airplaneModeState.get()
    }

    override fun isAirplaneModeSyncEnabled(): Boolean = airplaneModeSyncEnabledState.get()

    override fun updateAirplaneModeState(enabled: Boolean) {
        airplaneModeState.set(enabled)
        listeners.forEach { it.onAirplaneModeChanged(enabled) }
    }

    fun setAirplaneModeSyncEnabledState(enabled: Boolean) {
        airplaneModeSyncEnabledState.set(enabled)
        listeners.forEach { it.onAirplaneModeSyncEnabledStateChanged(enabled) }
    }

    override fun registerAirplaneModeChangedListener(listener: AirplaneModeController.Listener) {
        listeners.add(listener)
    }

    override fun unregisterAirplaneModeChangedListener(listener: AirplaneModeController.Listener) {
        listeners.remove(listener)
    }
}
