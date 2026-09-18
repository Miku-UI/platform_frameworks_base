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

package com.android.wm.shell.pinnedlayer.phone

import android.app.ActivityManager.AppTask.WINDOWING_LAYER_PINNED
import android.app.TaskInfo
import android.os.IRemoteCallback

/** A sealed class that represents an active transition in the pinned layer. */
sealed class ActiveTransition {
    abstract val taskInfo: TaskInfo

    /**
     * A transition that represents pin operation on a specific task. Pinning can happen via direct
     * [android.app.ActivityManager.AppTask.requestWindowingLayer] call or when the window becomes
     * visible again.
     *
     * @property taskInfo a [TaskInfo] that current transition going to pin.
     * @property resultCallback a [IRemoteCallback] that should be invoked to notify the client
     *   about pin request result. Can be `null` during re-pining.
     * @see WINDOWING_LAYER_PINNED
     * @see [android.app.ActivityManager.AppTask.requestWindowingLayer]
     */
    data class Pin(override val taskInfo: TaskInfo, val resultCallback: IRemoteCallback?) :
        ActiveTransition()

    /**
     * A transition that represents unpin operation on a specific task. Unpinning can happen while
     * switching to another layer or a window is simply closed or minimized.
     *
     * @property taskInfo a [TaskInfo] that current transition going to pin.
     */
    data class Unpin(override val taskInfo: TaskInfo) : ActiveTransition()
}
