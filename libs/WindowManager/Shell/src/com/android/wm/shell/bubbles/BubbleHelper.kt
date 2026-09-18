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
import android.window.TransitionInfo
import android.window.WindowContainerToken

/** Helper to query Bubble info from other components. */
interface BubbleHelper {
    /** Gets the [WindowContainerToken] of the Bubble root Task. */
    fun getAppBubbleRootTaskToken(): WindowContainerToken?

    /** Gets the [WindowContainerToken] of the Bubble visibility barrier. */
    fun getAppBubbleVisibilityBarrierToken(): WindowContainerToken?

    /** Gets the id for the Bubble root task. */
    fun getAppBubbleRootTaskId(): Int

    /** Whether the given Task id is the Bubble root Task. */
    fun isAppBubbleRootTask(taskId: Int): Boolean

    /** Whether the given Task info is the Bubble root Task. */
    fun isAppBubbleRootTask(taskInfo: ActivityManager.RunningTaskInfo?): Boolean

    /** Whether the given Task info is an App Bubble Task. */
    fun isAppBubbleTask(taskInfo: ActivityManager.RunningTaskInfo?): Boolean

    /** Whether the given task is any type of Bubble. */
    fun isBubbleTask(taskInfo: ActivityManager.RunningTaskInfo?): Boolean

    /**
     * Finds the Task that is entering Bubble. This can be either a Bubble Task that is becoming
     * visible, or a visible Task that is changing to Bubble from other windowing mode.
     */
    fun getEnterBubbleTask(info: TransitionInfo): TransitionInfo.Change?

    /** Finds the Bubble Task that is closing. */
    fun getClosingBubbleTask(info: TransitionInfo): TransitionInfo.Change?

    /** Whether the transition contains a Bubble switching. */
    fun containsBubbleSwitch(info: TransitionInfo): Boolean
}
