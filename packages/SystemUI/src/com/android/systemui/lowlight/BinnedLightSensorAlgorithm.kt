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

package com.android.systemui.lowlight

import javax.inject.Inject

class BinnedLightSensorAlgorithm @Inject constructor() : AmbientLightModeMonitor.DebounceAlgorithm {
    private var callback: AmbientLightModeMonitor.Callback? = null

    override fun start(callback: AmbientLightModeMonitor.Callback?) {
        this.callback = callback
    }

    override fun stop() {
        this.callback = null
    }

    override fun onUpdateLightSensorEvent(value: Float) {
        callback?.apply {
            this.onChange(
                if (value > BINNED_THRESHOLD) {
                    AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_LIGHT
                } else {
                    AmbientLightModeMonitor.AMBIENT_LIGHT_MODE_DARK
                }
            )
        }
    }

    companion object {
        /**
         * Binned light sensors modulate between 1 (representing dark environment) and 2
         * (representing a light environment). The value below defines the threshold for determining
         * which state we're in. Note that sensor value is always an integer despite being reported
         * as a float.
         */
        const val BINNED_THRESHOLD = 1.5f
    }
}
