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

package com.android.wm.shell.bubbles

import android.app.ActivityManager
import android.app.ActivityTaskManager
import android.window.TransitionInfo
import android.window.WindowContainerToken

/** Fake implementation of [BubbleHelper] for testing. */
class FakeBubbleHelper : BubbleHelper {
    override fun getAppBubbleRootTaskToken(): WindowContainerToken? {
        return null
    }

    override fun getAppBubbleVisibilityBarrierToken(): WindowContainerToken? {
        return null
    }

    override fun getAppBubbleRootTaskId(): Int = ActivityTaskManager.INVALID_TASK_ID

    override fun isAppBubbleRootTask(taskId: Int): Boolean {
        return false
    }

    override fun isAppBubbleRootTask(taskInfo: ActivityManager.RunningTaskInfo?): Boolean {
        return false
    }

    override fun isAppBubbleTask(taskInfo: ActivityManager.RunningTaskInfo?): Boolean {
        return false
    }

    override fun isBubbleTask(taskInfo: ActivityManager.RunningTaskInfo?): Boolean {
        return false
    }

    override fun getEnterBubbleTask(info: TransitionInfo): TransitionInfo.Change? {
        return null
    }

    override fun getClosingBubbleTask(info: TransitionInfo): TransitionInfo.Change? {
        return null
    }

    override fun containsBubbleSwitch(info: TransitionInfo): Boolean {
        return false
    }
}
