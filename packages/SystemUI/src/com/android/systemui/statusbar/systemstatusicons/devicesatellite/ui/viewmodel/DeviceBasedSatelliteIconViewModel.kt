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

package com.android.systemui.statusbar.systemstatusicons.devicesatellite.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.statusbar.pipeline.satellite.ui.viewmodel.DeviceBasedSatelliteViewModel
import com.android.systemui.statusbar.systemstatusicons.SystemStatusIconsInCompose.expectInNewMode
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/**
 * View model for the Device Based Satellite system status icon. Emits a Satellite icon when
 * satellite is available AND all other service states are considered OOS.
 */
class DeviceBasedSatelliteIconViewModel
@AssistedInject
constructor(
    @Assisted context: Context,
    deviceBasedSatelliteViewModel: DeviceBasedSatelliteViewModel,
) : SystemStatusIconViewModel.Default, HydratedActivatable() {

    init {
        /* check if */ expectInNewMode()
    }

    override val slotName =
        context.getString(com.android.internal.R.string.status_bar_oem_satellite)

    override val icon: Icon? by
        deviceBasedSatelliteViewModel.icon.hydratedStateOf(
            initialValue = null,
            traceName = "SystemStatus.deviceBasedSatelliteIcon",
        )

    override val visible: Boolean
        get() = icon != null

    @AssistedFactory
    interface Factory {
        fun create(context: Context): DeviceBasedSatelliteIconViewModel
    }
}
