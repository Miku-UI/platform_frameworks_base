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

package com.android.wm.shell.compatui.api

import android.app.TaskInfo
import android.graphics.Rect
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.dagger.WMSingleton
import java.util.function.Consumer
import javax.inject.Inject

/** [CompatUIHandler] implementation that handles the [CompatUISharedStateRepository]. */
@WMSingleton
class CompatUISharedStateHandler
@Inject
constructor(
    private val sharedStateRepository: CompatUISharedStateRepository,
    private val displayController: DisplayController,
) : CompatUIHandler {
    override fun onCompatInfoChanged(compatUIInfo: CompatUIInfo) {
        sharedStateRepository.insert(
            compatUIInfo.taskInfo.taskId,
            CompatUISharedState(
                taskId = compatUIInfo.taskInfo.taskId,
                taskConfiguration = compatUIInfo.taskInfo.configuration,
            ),
            overrideIfPresent = false,
        )
        updateDisplayLayout(compatUIInfo.taskInfo)
    }

    override fun sendCompatUIRequest(compatUIRequest: CompatUIRequest) {}

    override fun setCallback(compatUIEventSender: Consumer<CompatUIEvent>?) {}

    private fun updateDisplayLayout(taskInfo: TaskInfo) {
        val previousStableBounds = sharedStateRepository.find(taskInfo.taskId)?.stableBounds
        val displayLayout = displayController.getDisplayLayout(taskInfo.displayId)
        val newStableBounds = Rect()
        displayLayout?.getStableBounds(newStableBounds)
        sharedStateRepository.update(taskInfo.taskId) { item ->
            item.copy(
                stableBounds = newStableBounds,
                areParentBoundsChanged = newStableBounds != previousStableBounds,
                taskConfiguration = taskInfo.configuration,
            )
        }
    }
}
