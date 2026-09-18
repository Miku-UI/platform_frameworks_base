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

package com.android.systemui.volume.panel.component.mediainput.data.repository

import com.android.settingslib.media.MediaDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeMediaInputComponentRepository : MediaInputComponentRepository {
    private val _currentInputDevice: MutableStateFlow<MediaDevice?> = MutableStateFlow(null)

    override val currentInputDevice: StateFlow<MediaDevice?> = _currentInputDevice.asStateFlow()

    fun setCurrentInputDevice(device: MediaDevice?) {
        _currentInputDevice.value = device
    }
}
