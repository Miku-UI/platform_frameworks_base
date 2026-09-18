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

package com.android.systemui.statusbar.pipeline.satellite.ui.viewmodel

import android.content.testableContext
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.testScope
import com.android.systemui.log.logcatLogBuffer
import com.android.systemui.log.table.logcatTableLogBuffer
import com.android.systemui.statusbar.pipeline.airplane.domain.interactor.airplaneModeInteractor
import com.android.systemui.statusbar.pipeline.satellite.domain.interactor.deviceBasedSatelliteInteractor

val Kosmos.deviceBasedSatelliteViewModel: DeviceBasedSatelliteViewModel by
    Kosmos.Fixture {
        DeviceBasedSatelliteViewModelImpl(
            context = testableContext,
            interactor = deviceBasedSatelliteInteractor,
            scope = testScope.backgroundScope,
            airplaneModeInteractor = airplaneModeInteractor,
            logBuffer = logcatLogBuffer("deviceBasedSatelliteViewModelLogBuffer"),
            tableLog = logcatTableLogBuffer(this, "deviceBasedSatelliteViewModelTableLogBuffer"),
        )
    }
