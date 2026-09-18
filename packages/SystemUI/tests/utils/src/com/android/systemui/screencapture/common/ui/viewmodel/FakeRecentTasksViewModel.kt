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
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureRecentTask
import com.android.systemui.screencapture.common.domain.model.TargetModel
import kotlinx.coroutines.awaitCancellation

class FakeRecentTasksViewModel(
    var fakeViewModelFactory: RecentTaskViewModel.Factory,
    fakeDrawableLoaderViewModel: DrawableLoaderViewModel,
    fakeAudioSwitchViewModel: AudioSwitchViewModel = AudioSwitchViewModelImpl(),
) :
    RecentTasksViewModel,
    DrawableLoaderViewModel by fakeDrawableLoaderViewModel,
    AudioSwitchViewModel by fakeAudioSwitchViewModel {

    private val _targets = mutableStateOf<List<ScreenCaptureRecentTask>?>(null)

    override val targets: State<List<ScreenCaptureRecentTask>?> = _targets

    private val _selectedTarget = mutableStateOf<RecentTaskViewModel?>(null)
    override val selectedTarget: State<RecentTaskViewModel?> = _selectedTarget

    override fun setSelectedTarget(target: TargetViewModel?) {
        _selectedTarget.value = target as RecentTaskViewModel?
    }

    override fun createViewModelFor(target: TargetModel): RecentTaskViewModel =
        fakeViewModelFactory.create(target as ScreenCaptureRecentTask)

    fun setTargets(tasks: List<ScreenCaptureRecentTask>?) {
        _targets.value = tasks
    }

    fun setTargets(vararg tasks: ScreenCaptureRecentTask) {
        _targets.value = tasks.toList()
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
