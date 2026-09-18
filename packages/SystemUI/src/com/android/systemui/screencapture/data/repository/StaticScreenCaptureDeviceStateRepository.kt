/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may
 * obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.screencapture.data.repository

import android.content.res.Resources
import com.android.systemui.res.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A [ScreenCaptureDeviceStateRepository] that reads the device state from static resources once and
 * does not update. This is a lightweight implementation suitable for services or other components
 * that do not need to react to configuration changes.
 */
class StaticScreenCaptureDeviceStateRepository(resources: Resources) :
    ScreenCaptureDeviceStateRepository {
    override val isLargeScreen: StateFlow<Boolean> =
        MutableStateFlow(resources.getBoolean(R.bool.config_enableLargeScreenScreencapture))
            .asStateFlow()
}
