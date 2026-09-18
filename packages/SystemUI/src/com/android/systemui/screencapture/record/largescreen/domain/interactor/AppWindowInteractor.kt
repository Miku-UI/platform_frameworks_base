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

package com.android.systemui.screencapture.record.largescreen.domain.interactor

import android.app.ActivityManager
import android.app.ActivityTaskManager
import android.app.WindowConfiguration
import com.android.systemui.screencapture.common.ScreenCaptureUiScope
import javax.inject.Inject

/** Interactor for discovering and filtering app windows for screen capture. */
@ScreenCaptureUiScope
class AppWindowInteractor
@Inject
constructor(private val activityTaskManager: ActivityTaskManager) {

    /**
     * Returns a Z-ordered list of visible, standard app window tasks on a specific display.
     *
     * @param displayId The ID of the display to query for tasks.
     * @return A list of [ActivityManager.RunningTaskInfo] objects, ordered from top to bottom.
     */
    fun getAppWindowTasks(displayId: Int): List<ActivityManager.RunningTaskInfo> {
        return activityTaskManager.getTasks(Integer.MAX_VALUE).filter {
            it.topActivity != null &&
                it.isVisible &&
                it.displayId == displayId &&
                it.configuration.windowConfiguration.activityType ==
                    WindowConfiguration.ACTIVITY_TYPE_STANDARD &&
                it.topActivity?.packageName != "com.android.systemui" &&
                it.numActivities > 0
        }
    }
}
