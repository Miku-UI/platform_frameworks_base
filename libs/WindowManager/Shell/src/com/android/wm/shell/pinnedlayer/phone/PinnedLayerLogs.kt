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

import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_WINDOWING_LAYER

/**
 * A utility class that wraps logging into a convenient set of methods targeting pinned layer only.
 */
internal object PinnedLayerLogs {
    private const val TAG = "PinnedLayer"

    // TODO(b/478792808): Remove suppression
    @SuppressWarnings("ProtoLogNonConstantFormat")
    @JvmStatic
    internal fun logV(message: String, vararg args: Any?) {
        ProtoLog.v(WM_SHELL_WINDOWING_LAYER, "%s: $message", TAG, *args)
    }

    // TODO(b/478792808): Remove suppression
    @SuppressWarnings("ProtoLogNonConstantFormat")
    @JvmStatic
    internal fun logD(message: String, vararg args: Any?) {
        ProtoLog.d(WM_SHELL_WINDOWING_LAYER, "%s: $message", TAG, *args)
    }

    // TODO(b/478792808): Remove suppression
    @SuppressWarnings("ProtoLogNonConstantFormat")
    @JvmStatic
    internal fun logW(message: String, vararg args: Any?) {
        ProtoLog.w(WM_SHELL_WINDOWING_LAYER, "%s: $message", TAG, *args)
    }
}
