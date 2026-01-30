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

package com.android.systemui.screencapture.record.smallscreen.ui.viewmodel

import androidx.compose.runtime.getValue
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureRecentTask
import com.android.systemui.screencapture.common.ui.viewmodel.RecentTaskViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.RecentTasksViewModel
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

class RecordDetailsAppSelectorViewModel
@AssistedInject
constructor(
    recentTasksViewModel: RecentTasksViewModel,
    private val recentTaskViewModelFactory: RecentTaskViewModel.Factory,
) : HydratedActivatable() {

    val recentTasks: List<ScreenCaptureRecentTask>? by
        recentTasksViewModel.recentTasks.hydratedStateOf(
            "RecordDetailsAppSelectorViewModel#recentTasks",
            null,
        )

    fun createTaskViewModel(task: ScreenCaptureRecentTask): RecentTaskViewModel =
        recentTaskViewModelFactory.create(task)

    @AssistedFactory
    interface Factory {
        fun create(): RecordDetailsAppSelectorViewModel
    }
}
