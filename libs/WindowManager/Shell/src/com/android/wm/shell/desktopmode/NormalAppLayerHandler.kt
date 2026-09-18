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

package com.android.wm.shell.desktopmode

import android.app.ActivityManager.AppTask.WINDOWING_LAYER_NORMAL_APP
import android.os.IBinder
import android.view.SurfaceControl
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.TransitionRequestInfo.WindowingLayerChange
import android.window.WindowContainerTransaction
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_WINDOWING_LAYER
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions

/** A class responsible for handling [WINDOWING_LAYER_NORMAL_APP] requests. */
class NormalAppLayerHandler(
    shellInit: ShellInit,
    private val transitions: Transitions,
    private val normalAppLayerController: NormalAppLayerController,
) : Transitions.TransitionHandler {

    init {
        shellInit.addInitCallback(this::onInit, this)
    }

    private fun onInit() {
        transitions.addHandler(this)
    }

    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo,
    ): WindowContainerTransaction? {
        val triggerTask = request.triggerTask ?: return null
        val windowingLayerChange = request.windowingLayerChange ?: return null

        if (!windowingLayerChange.isNormalLayerRequest()) {
            return null
        }

        return normalAppLayerController.moveTaskToNormalLayer(
            transition,
            triggerTask,
            windowingLayerChange.remoteCallback,
        )
    }

    private fun WindowingLayerChange?.isNormalLayerRequest(): Boolean {
        return this != null && windowingLayer == WINDOWING_LAYER_NORMAL_APP
    }

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
    ): Boolean = false

    internal companion object {
        private const val TAG = "NormalAppLayer"

        // TODO(b/478792808): Remove suppression
        @SuppressWarnings("ProtoLogNonConstantFormat")
        @JvmStatic
        internal fun logV(message: String, vararg args: Any?) {
            ProtoLog.v(WM_SHELL_WINDOWING_LAYER, "%s: $message", TAG, *args)
        }

        // TODO(b/478792808): Remove suppression
        @SuppressWarnings("ProtoLogNonConstantFormat")
        @JvmStatic
        internal fun logW(message: String, vararg args: Any?) {
            ProtoLog.w(WM_SHELL_WINDOWING_LAYER, "%s: $message", TAG, *args)
        }

        // TODO(b/478792808): Remove suppression
        @SuppressWarnings("ProtoLogNonConstantFormat")
        @JvmStatic
        internal fun logE(message: String, vararg args: Any?) {
            ProtoLog.e(WM_SHELL_WINDOWING_LAYER, "%s: $message", TAG, *args)
        }
    }
}
