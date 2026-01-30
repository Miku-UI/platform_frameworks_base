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

import com.android.systemui.screencapture.common.domain.model.ScreenCaptureRecentTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeRecentTasksViewModel : RecentTasksViewModel {

    private val _recentTasks = MutableStateFlow<List<ScreenCaptureRecentTask>?>(null)
    override val recentTasks: Flow<List<ScreenCaptureRecentTask>?> = _recentTasks.asStateFlow()

    fun setRecentTasks(tasks: List<ScreenCaptureRecentTask>?) {
        _recentTasks.value = tasks
    }

    fun setRecentTasks(vararg tasks: ScreenCaptureRecentTask) {
        _recentTasks.value = tasks.toList()
    }
}
