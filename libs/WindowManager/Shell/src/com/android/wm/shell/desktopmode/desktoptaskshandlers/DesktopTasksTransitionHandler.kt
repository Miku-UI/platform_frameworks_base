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

package com.android.wm.shell.desktopmode.desktoptaskshandlers

import android.os.IBinder
import android.view.SurfaceControl.Transaction
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import com.android.wm.shell.desktopmode.DesktopTasksController
import com.android.wm.shell.desktopmode.ShellDesktopState
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.transition.Transitions.TransitionFinishCallback
import com.android.wm.shell.transition.Transitions.TransitionHandler
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

/** Handles transition requests related to Desktop tasks. */
class DesktopTasksTransitionHandler(
    private val transitions: Transitions,
    private val shellInit: ShellInit,
    private val desktopState: ShellDesktopState,
    private val desktopTasksController: Optional<DesktopTasksController>,
) : TransitionHandler {

    init {
        if (desktopState.canEnterDesktopMode) {
            shellInit.addInitCallback({ transitions.addHandler(this) }, this)
        }
    }

    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo,
    ): WindowContainerTransaction? =
        desktopTasksController.getOrNull()?.handleRequest(transition, request)

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: Transaction,
        finishTransaction: Transaction,
        finishCallback: TransitionFinishCallback,
    ): Boolean {
        // This handler never animates anything - instead it delegates to other handlers.
        return false
    }
}
