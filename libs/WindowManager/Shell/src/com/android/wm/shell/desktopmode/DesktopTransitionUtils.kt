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

package com.android.wm.shell.desktopmode

import android.view.WindowManager.TRANSIT_NONE
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.window.RemoteTransition
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE

/** Encapsulate transition utility methods used by Desktop Windowing. */
object DesktopTransitionUtils {

    /** Returns the transition type for the given remote transition. */
    fun getToFrontTransitionTypeOrNone(remoteTransition: RemoteTransition?): Int {
        if (remoteTransition == null) {
            ProtoLog.v(WM_SHELL_DESKTOP_MODE, "DesktopTransitionUtils: RemoteTransition is null")
            return TRANSIT_NONE
        }
        return TRANSIT_TO_FRONT
    }
}
