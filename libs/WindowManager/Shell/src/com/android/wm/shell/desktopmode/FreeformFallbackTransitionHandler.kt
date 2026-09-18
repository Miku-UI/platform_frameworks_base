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

import android.os.IBinder
import android.view.SurfaceControl
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import com.android.wm.shell.transition.Transitions

/** Handles transitions related to recovering freeform tasks launched outside of a desk. */
class FreeformFallbackTransitionHandler(
    private val desktopUserRepositories: DesktopUserRepositories
) : Transitions.TransitionHandler {

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
    ): Boolean {
        for (change in info.changes) {
            val taskInfo = change.taskInfo ?: continue
            val repository = desktopUserRepositories.getProfile(taskInfo.userId)
            if (!repository.isActiveTask(taskInfo.taskId)) continue
            val leash = change.leash
            // Ensure leash remain visible throughout animation.
            startTransaction.setAlpha(leash, 1.0f)
            finishTransaction.setAlpha(leash, 1.0f)
        }

        startTransaction.apply()

        finishCallback.onTransitionFinished(null)

        return true
    }

    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo,
    ): WindowContainerTransaction? {
        return null
    }
}
