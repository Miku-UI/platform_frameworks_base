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

package com.android.systemui.screencapture.common.ui.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureDisplay
import com.android.systemui.screencapture.common.domain.model.TargetModel
import kotlinx.coroutines.awaitCancellation

class FakeDisplaysViewModel(
    var fakeViewModelFactory: DisplayViewModel.Factory,
    fakeDrawableLoaderViewModel: DrawableLoaderViewModel,
    fakeAudioSwitchViewModel: AudioSwitchViewModel = AudioSwitchViewModelImpl(),
) :
    DisplaysViewModel,
    DrawableLoaderViewModel by fakeDrawableLoaderViewModel,
    AudioSwitchViewModel by fakeAudioSwitchViewModel {
    private val fakeDisplays = mutableStateOf<List<ScreenCaptureDisplay>?>(null)
    override val targets: State<List<ScreenCaptureDisplay>?> = fakeDisplays

    private val _selectedTarget = mutableStateOf<DisplayViewModel?>(null)
    override val selectedTarget: State<DisplayViewModel?> = _selectedTarget

    override fun setSelectedTarget(target: TargetViewModel?) {
        _selectedTarget.value = target as DisplayViewModel?
    }

    override fun createViewModelFor(target: TargetModel): DisplayViewModel =
        fakeViewModelFactory.create(target as ScreenCaptureDisplay)

    fun setDisplays(displays: List<ScreenCaptureDisplay>?) {
        fakeDisplays.value = displays
    }

    fun setDisplays(vararg displays: ScreenCaptureDisplay) {
        setDisplays(displays.toList())
    }

    var activateCallCount = 0
    var deactivateCallCount = 0

    override suspend fun activate(): Nothing {
        activateCallCount++
        try {
            awaitCancellation()
        } finally {
            deactivateCallCount++
        }
    }
}
