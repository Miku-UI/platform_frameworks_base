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

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureRecentTask
import com.android.systemui.screencapture.record.smallscreen.domain.interactor.RecordDetailsTargetInteractor
import com.android.systemui.screencapture.record.smallscreen.shared.model.RecordDetailsTargetModel
import com.android.systemui.screencapture.record.smallscreen.shared.model.currentTargetModel
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

class RecordDetailsTargetViewModel
@AssistedInject
constructor(private val interactor: RecordDetailsTargetInteractor) : HydratedActivatable() {

    private val model by interactor.model.hydratedStateOf("RecordDetailsTargetViewModel#model")
    val items: List<RecordDetailsTargetModel> by derivedStateOf { model.items }
    val selectedIndex: Int by derivedStateOf { model.selectedIndex }
    val shouldShowAppSelector: Boolean by derivedStateOf {
        model.currentTargetModel.shouldShowAppSelector && canChangeTarget
    }
    val canShowTouches: Boolean by derivedStateOf { model.currentTargetModel.canShowTouches }
    val selectedAppLabel: CharSequence? by derivedStateOf {
        (model.currentTargetModel as? RecordDetailsTargetModel.SingleApp)?.appLabel
    }
    val warningMessageRes: Int by derivedStateOf { model.currentTargetModel.warningMessageRes }

    val canChangeTarget by
        interactor.canChangeTarget.hydratedStateOf("RecordDetailsTargetViewModel#canChangeTarget")

    override suspend fun onActivated() {}

    fun select(index: Int) {
        interactor.selectItem(index)
    }

    fun selectTask(task: ScreenCaptureRecentTask) {
        interactor.selectTask(task)
    }

    @AssistedFactory
    interface Factory {
        fun create(): RecordDetailsTargetViewModel
    }
}
