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

package com.android.systemui.unfold.domain.interactor

import com.android.systemui.display.data.repository.DeviceStateRepository.DeviceState
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

val Kosmos.fakeDisplaySwitchTrackingInteractor by Fixture { FakeDisplaySwitchTrackingInteractor() }

class FakeDisplaySwitchTrackingInteractor : DisplaySwitchTrackingInteractor {
    private val _displaySwitchState: MutableStateFlow<DisplaySwitchState> =
        MutableStateFlow(DisplaySwitchState.Idle(newDeviceState = DeviceState.FOLDED))

    override val displaySwitchState: StateFlow<DisplaySwitchState> = _displaySwitchState

    fun setDisplaySwitchState(state: DisplaySwitchState) {
        _displaySwitchState.value = state
    }
}
