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

package com.android.systemui.mediaprojection.appselector.data

import android.content.ComponentName
import android.os.UserHandle
import android.view.Display
import com.android.users.UserType

object TestRecentTaskFactory {

    fun createRecentTask(
        taskId: Int = 1,
        displayId: Int = Display.DEFAULT_DISPLAY,
        userId: Int = UserHandle.USER_CURRENT,
        colorBackground: Int = 0x99a83ba5.toInt(),
    ): RecentTask {
        return RecentTask(
            taskId = taskId,
            displayId = displayId,
            userId = userId,
            topActivityComponent = ComponentName("FakeTopPackage_$taskId", "FakeTopClass_$taskId"),
            baseIntentComponent = ComponentName("FakeBasePackage_$taskId", "FakeBaseClass_$taskId"),
            baseIntent = null,
            colorBackground = colorBackground,
            isForegroundTask = false,
            userType = UserType.MAIN,
            splitBounds = null,
        )
    }

    fun createRecentTasks(): List<RecentTask> {
        return listOf(
            createRecentTask(taskId = 1, colorBackground = 0x99a83ba5.toInt()),
            createRecentTask(taskId = 2, colorBackground = 0x99123456.toInt()),
        )
    }
}
