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

package com.android.wm.shell.windowdecor

import android.app.ActivityManager.RunningTaskInfo
import android.content.Intent
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.InputMethod
import com.android.wm.shell.desktopmode.common.ToggleTaskSizeInteraction.AmbiguousSource
import com.android.wm.shell.shared.desktopmode.DesktopModeTransitionSource

/**
 * Holds references to the implementation of all possible actions to be implemented by the
 * [WindowDecoration].
 */
interface WindowDecorationActions {
    /** Maximizes a window or restores previous sized if already maximized. */
    fun onMaximizeOrRestore(taskId: Int, ambiguousSource: AmbiguousSource, inputMethod: InputMethod)

    /** Minimizes the task. */
    fun onMinimize(taskInfo: RunningTaskInfo)

    /** Close the task */
    fun onClose(taskInfo: RunningTaskInfo)

    /**
     * Closes the task.
     *
     * @param taskInfo the task requesting to close.
     * @param preventDesktopCleanup if true, the desktop cleanup will be skipped forcefully.
     */
    fun onClose(taskInfo: RunningTaskInfo, preventDesktopCleanup: Boolean)

    /**
     * Moves task to immersive mode or exits immersive and restores task to previous size if task is
     * already in immersive.
     */
    fun onImmersiveOrRestore(taskInfo: RunningTaskInfo)

    /**
     * On any caption views received user interactions. This likely brings the relevant task to
     * front.
     */
    fun onCaptionViewReceivedInteraction(taskInfo: RunningTaskInfo)

    /** Snaps task to left half of the screen. */
    fun onLeftSnap(taskId: Int, inputMethod: InputMethod)

    /** Snaps task to right half of the screen. */
    fun onRightSnap(taskId: Int, inputMethod: InputMethod)

    /** Moves task to fullscreen. */
    fun onToFullscreen(taskId: Int)

    /** Moves task to split screen. */
    fun onToSplitScreen(taskId: Int)

    /** Moves task to desktop windowing. */
    fun onToDesktop(taskId: Int, transitionSource: DesktopModeTransitionSource)

    /** Requests for task to open float. */
    fun onToFloat(taskId: Int)

    /**
     * Opens app content in browser.
     *
     * @param intent to be used to launch browser application.
     */
    fun onOpenInBrowser(taskId: Int, intent: Intent)

    /**
     * Opens app content in browser and close the task.
     *
     * @param intent to be used to launch browser application.
     */
    fun onSwitchToBrowser(taskInfo: RunningTaskInfo, intent: Intent)

    /**
     * Opens existing instance.
     *
     * @param taskInfo the task requesting to open the instance.
     * @param requestedTaskId the taskId of the task hosting the instance being opened.
     */
    fun onOpenInstance(taskInfo: RunningTaskInfo, requestedTaskId: Int)

    /** Opens manage windows menu. */
    fun onManageWindows(taskId: Int)

    /** Shows restart dialog. */
    fun onRestart(taskId: Int)

    /** Launches aspect ratio settings. */
    fun onChangeAspectRatio(taskInfo: RunningTaskInfo)

    /** Launches game controls. */
    fun onLaunchGameControls(taskInfo: RunningTaskInfo)

    /** Creates new instance of task. */
    fun onNewWindow(taskId: Int)

    /** Opens the handle menu. */
    fun onOpenHandleMenu(taskId: Int)

    /**
     * Opens an arbitrary Intent.
     *
     * @param intent to be launched
     */
    fun onOpenIntent(taskId: Int, intent: Intent)
}
