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

@file:JvmName("WdLog")

package com.android.wm.shell.windowdecor

import android.os.SystemProperties
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_WINDOW_DECORATION

private val DEBUG_MOTION_EVENTS =
    SystemProperties.getBoolean("persist.wm.debug.window_decoration_motion_events_debug", false)

/** Logs a debug message with taskId. */
fun logD(tag: String, taskId: Int, msg: String, vararg args: Any?) {
    ProtoLog.d(
        WM_SHELL_WINDOW_DECORATION,
        "%s: (taskId=%d) %s",
        tag,
        taskId,
        String.format(msg, *args),
    )
}

/** Logs a debug message without taskId. */
fun logD(tag: String, msg: String, vararg args: Any?) {
    ProtoLog.d(
        WM_SHELL_WINDOW_DECORATION,
        "%s: %s",
        tag,
        if (args.isEmpty()) msg else String.format(msg, *args),
    )
}

/** Logs a debug message with taskId if [DEBUG_MOTION_EVENTS] is true. */
fun motionEventLogD(tag: String, taskId: Int, msg: String, vararg args: Any?) {
    if (DEBUG_MOTION_EVENTS) {
        logD(tag, taskId, msg, *args)
    }
}

/** Logs a debug message without taskId if [DEBUG_MOTION_EVENTS] is true. */
fun motionEventLogD(tag: String, msg: String, vararg args: Any?) {
    if (DEBUG_MOTION_EVENTS) {
        logD(tag, msg, *args)
    }
}
