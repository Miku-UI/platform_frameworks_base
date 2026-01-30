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

import android.view.Display
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.app.tracing.coroutines.launchTraced
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ScreenCaptureUi
import com.android.systemui.screencapture.common.domain.interactor.ScreenCaptureLabelInteractor
import com.android.systemui.screencapture.common.domain.interactor.ScreenCaptureRecentTaskInteractor
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureRecentTask
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureTarget
import com.android.systemui.screenrecord.domain.interactor.ScreenRecordingServiceInteractor
import com.android.systemui.screenrecord.domain.interactor.Status
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

sealed interface RecordDetailsTargetItemViewModel {

    val labelRes: Int
    val isSelectable: Boolean
    val screenCaptureTarget: ScreenCaptureTarget?

    data class EntireScreen(override val screenCaptureTarget: ScreenCaptureTarget) :
        RecordDetailsTargetItemViewModel {

        constructor(display: Display) : this(ScreenCaptureTarget.Fullscreen(display.displayId))

        override val labelRes: Int = R.string.screen_record_entire_screen
        override val isSelectable: Boolean = true
    }

    data class SingleApp(val task: ScreenCaptureRecentTask) : RecordDetailsTargetItemViewModel {

        override val screenCaptureTarget: ScreenCaptureTarget =
            ScreenCaptureTarget.App(displayId = task.displayId, taskId = task.taskId)

        override val labelRes: Int = R.string.screen_record_single_app
        override val isSelectable: Boolean = true
    }

    data object SingleAppNoRecents : RecordDetailsTargetItemViewModel {

        override val labelRes: Int = R.string.screen_record_single_app_no_recents
        override val isSelectable: Boolean = false
        override val screenCaptureTarget: ScreenCaptureTarget? = null
    }
}

class RecordDetailsTargetViewModel
@AssistedInject
constructor(
    @ScreenCaptureUi private val display: Display,
    private val screenRecordingServiceInteractor: ScreenRecordingServiceInteractor,
    private val screenCaptureRecentTaskInteractor: ScreenCaptureRecentTaskInteractor,
    private val labelInteractor: ScreenCaptureLabelInteractor,
) : HydratedActivatable() {

    private val _selectedIndex = MutableStateFlow(0)
    private val _items: MutableStateFlow<List<RecordDetailsTargetItemViewModel>?> =
        MutableStateFlow(null)
    private val _currentTarget = MutableStateFlow<RecordDetailsTargetItemViewModel?>(null)

    val canChangeTarget: Boolean by
        screenRecordingServiceInteractor.status
            .map { it.canChangeTarget() }
            .hydratedStateOf(
                traceName = "RecordDetailsTargetViewModel#canChangeTarget",
                initialValue = screenRecordingServiceInteractor.status.value.canChangeTarget(),
            )
    val currentTarget: RecordDetailsTargetItemViewModel? by
        _currentTarget.hydratedStateOf(traceName = "RecordDetailsTargetViewModel#currentTarget")
    val items: List<RecordDetailsTargetItemViewModel>? by
        _items.hydratedStateOf(traceName = "RecordDetailsTargetViewModel#items")
    val selectedIndex: Int by
        combine(_selectedIndex, _items) { idx, targets ->
                if (targets.isNullOrEmpty()) {
                    idx
                } else {
                    val result = idx.coerceIn(targets.indices)
                    if (!targets[result].isSelectable) {
                        targets.indexOfFirst { it is RecordDetailsTargetItemViewModel.EntireScreen }
                    } else {
                        result
                    }
                }
            }
            .hydratedStateOf("RecordDetailsTargetViewModel#selectedIndex", 0)
    val shouldShowAppSelector: Boolean by derivedStateOf {
        items?.getOrNull(selectedIndex) is RecordDetailsTargetItemViewModel.SingleApp
    }
    var selectedAppName: Result<CharSequence>? by mutableStateOf(null)
        private set

    override suspend fun onActivated() {
        coroutineScope {
            launchTraced("RecordDetailsTargetViewModel#_currentTarget") {
                combine(_items, _selectedIndex) { targets, idx -> targets?.getOrNull(idx) }
                    .onEach {
                        val appViewModel = it as? RecordDetailsTargetItemViewModel.SingleApp
                        selectedAppName =
                            appViewModel?.task?.let { task -> labelInteractor.loadLabel(task) }
                    }
                    .collect(_currentTarget)
            }
            launchTraced("RecordDetailsTargetViewModel#_items") {
                screenCaptureRecentTaskInteractor.recentTasks
                    .map { tasks ->
                        buildList {
                            add(RecordDetailsTargetItemViewModel.EntireScreen(display))
                            add(
                                if (tasks.isNullOrEmpty()) {
                                    RecordDetailsTargetItemViewModel.SingleAppNoRecents
                                } else {
                                    RecordDetailsTargetItemViewModel.SingleApp(tasks.first())
                                }
                            )
                        }
                    }
                    .collect(_items)
            }
        }
    }

    fun select(index: Int) {
        _selectedIndex.value = index
    }

    fun selectTask(task: ScreenCaptureRecentTask) {
        _items.update { current ->
            current?.map {
                if (it is RecordDetailsTargetItemViewModel.SingleApp) {
                    RecordDetailsTargetItemViewModel.SingleApp(task)
                } else {
                    it
                }
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(): RecordDetailsTargetViewModel
    }
}

private fun Status.canChangeTarget(): Boolean = this is Status.Stopped || this is Status.Initial
