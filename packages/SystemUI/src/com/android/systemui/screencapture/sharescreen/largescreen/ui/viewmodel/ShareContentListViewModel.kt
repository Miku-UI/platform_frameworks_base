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

package com.android.systemui.screencapture.sharescreen.largescreen.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.screencapture.common.ui.viewmodel.RecentTaskViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.RecentTasksViewModel
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/**
 * ViewModel for the Share Content list UI.
 *
 * This class follows a composition pattern. It delegates the responsibility of providing the recent
 * tasks list via the [RecentTasksViewModel] interface, and adds its own UI-specific state
 * management, such as tracking the selected item.
 */
class ShareContentListViewModel
@AssistedInject
constructor(private val recentTasksViewModel: RecentTasksViewModel) :
    HydratedActivatable(), RecentTasksViewModel by recentTasksViewModel {
    var selectedRecentTaskViewModel by mutableStateOf<RecentTaskViewModel?>(null)

    @AssistedFactory
    interface Factory {
        fun create(): ShareContentListViewModel
    }
}
