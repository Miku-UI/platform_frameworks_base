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

package com.android.wm.shell.apptoweb

import android.app.ActivityManager.RunningTaskInfo
import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.SurfaceControl
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import com.android.window.flags.Flags
import com.android.wm.shell.R
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.windowdecor.common.WindowDecorTaskResourceLoader
import java.util.function.Supplier
import kotlinx.coroutines.CoroutineScope

/** Window manager for the open by default first-run prompt */
internal class OpenByDefaultFirstRunPrompt(
    context: Context,
    userContext: Context,
    transitions: Transitions,
    taskInfo: RunningTaskInfo,
    taskSurface: SurfaceControl,
    displayController: DisplayController,
    taskResourceLoader: WindowDecorTaskResourceLoader,
    surfaceControlTransactionSupplier: Supplier<SurfaceControl.Transaction>,
    @ShellMainThread mainScope: CoroutineScope,
    listener: DialogLifecycleListener,
    private val onAckedCallback: () -> Unit,
    private val onSwitchToWeb: () -> Unit,
) :
    BaseOpenByDefaultDialog<OpenByDefaultFirstRunPromptView>(
        context,
        userContext,
        transitions,
        taskInfo,
        taskSurface,
        displayController,
        taskResourceLoader,
        surfaceControlTransactionSupplier,
        mainScope,
        listener,
    ) {

    override val dialogName = TAG

    override fun createDialog() {
        dialog =
            LayoutInflater.from(context)
                .inflate(R.layout.open_by_default_first_run_prompt, null /* root */)
                as OpenByDefaultFirstRunPromptView

        if (Flags.useInputReportedFocusForAccessibility()) {
            showDialogWindow()
        } else {
            transitions.runOnIdle(this::showDialogWindow)
        }

        dialog.setDismissOnClickListener { closeMenu() }
        dialog.setOpenInAppButtonClickListener {
            setDefaultLinkHandlingSetting(allowed = true)
            onAckedCallback()
            closeMenu()
        }
        dialog.setOpenInBrowserButtonClickListener {
            setDefaultLinkHandlingSetting(allowed = false)
            onAckedCallback()
            closeMenu()
            onSwitchToWeb()
        }
        dialog.setAskMeLaterButtonClickListener { closeMenu() }
    }

    override fun onAnimationEnded() {
        if (!Flags.useInputReportedFocusForAccessibility()) {
            dialog.post {
                dialog.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
                val subHeader: TextView = dialog.requireViewById(R.id.title)
                subHeader.requestFocus()
                subHeader.requestAccessibilityFocus()
            }
        }
    }

    override fun bindAppInfo(appIconBitmap: Bitmap, appName: CharSequence) {
        dialog.bindAppName(appName)
    }

    companion object {
        private const val TAG = "OpenByDefaultFirstRunPrompt"
    }
}
