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

package com.android.systemui.keyguard.data.model

import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration
import com.android.systemui.keyguard.shared.format

/**
 * Information about the SHOW_WHEN_LOCKED activity that is either newly on top of the task stack, or
 * newly not on top of the task stack.
 */
data class ShowWhenLockedActivityInfoModel(
    /** Whether the activity is on top. If not, we're unoccluding and will be animating it out. */
    val isOnTop: Boolean,

    /**
     * Information about the activity, which we use for transition internals and also to customize
     * animations.
     */
    val taskInfo: RunningTaskInfo? = null,
) {
    fun isDream(): Boolean {
        return taskInfo?.topActivityType == WindowConfiguration.ACTIVITY_TYPE_DREAM
    }

    override fun toString(): String {
        return "ShowWhenLockedActivityInfoModel(isOnTop=$isOnTop, taskInfo=${taskInfo.format()})"
    }
}
