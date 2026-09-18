/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.screencapture.record.smallscreen.data.repository

import com.android.systemui.screencapture.common.ScreenCaptureScope
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureRecentTask
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@ScreenCaptureScope
class RecordDetailsTargetRepository @Inject constructor() {

    private val _currentlySelectedTask = MutableStateFlow<ScreenCaptureRecentTask?>(null)
    val currentlySelectedTask: StateFlow<ScreenCaptureRecentTask?> =
        _currentlySelectedTask.asStateFlow()
    private val _selectedIndex = MutableStateFlow(0)
    val selectedIndex: StateFlow<Int> = _selectedIndex.asStateFlow()

    fun selectIndex(index: Int) {
        _selectedIndex.value = index
    }

    fun selectTask(task: ScreenCaptureRecentTask?) {
        _currentlySelectedTask.value = task
    }
}
