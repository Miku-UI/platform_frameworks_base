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

package com.android.systemui.lowlightclock

import android.content.ComponentName
import android.content.pm.PackageManager
import com.android.dream.lowlight.LowLightDreamManager
import com.android.systemui.dreams.dagger.DreamModule
import com.android.systemui.lifecycle.ExclusiveActivatable
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.awaitCancellation

class LowLightClockDreamAction
@Inject
constructor(
    private val packageManager: PackageManager,
    private val lowLightDreamManager: Lazy<LowLightDreamManager>,
    @param:Named(DreamModule.LOW_LIGHT_DREAM_SERVICE)
    private val lowLightDreamService: ComponentName?,
) : ExclusiveActivatable() {
    fun setEnabled(enabled: Boolean) {
        if (lowLightDreamService == null) {
            return
        }

        if (!componentEnabled) {
            lowLightDreamService.let { service ->
                packageManager.setComponentEnabledSetting(
                    service,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
            componentEnabled = true
        }

        lowLightDreamManager
            .get()
            .setAmbientLightMode(
                if (enabled) LowLightDreamManager.AMBIENT_LIGHT_MODE_LOW_LIGHT
                else LowLightDreamManager.AMBIENT_LIGHT_MODE_REGULAR
            )
    }

    override suspend fun onActivated(): Nothing {
        try {
            setEnabled(true)
            awaitCancellation()
        } finally {
            setEnabled(false)
        }
    }

    companion object {
        private var componentEnabled: Boolean = false
    }
}
