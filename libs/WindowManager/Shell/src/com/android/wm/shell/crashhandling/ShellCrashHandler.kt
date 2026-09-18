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

package com.android.wm.shell.crashhandling

import android.app.WindowConfiguration
import android.app.WindowConfiguration.WINDOWING_MODE_PINNED
import android.view.Display.DEFAULT_DISPLAY
import android.view.WindowManager
import android.window.DesktopExperienceFlags
import android.window.WindowContainerTransaction
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.bubbles.BubbleHelper
import com.android.wm.shell.bubbles.util.BubbleUtils
import com.android.wm.shell.common.HomeIntentProvider
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.NoOpTransitionHandler
import com.android.wm.shell.transition.Transitions
import java.util.Optional

/**
 * [ShellCrashHandler] for shell to use when it's being initialized. Currently it only restores the
 * home task to top.
 */
class ShellCrashHandler(
    private val shellTaskOrganizer: ShellTaskOrganizer,
    private val transitions: Transitions,
    private val homeIntentProvider: HomeIntentProvider,
    private val desktopState: DesktopState,
    private val bubbleHelper: Optional<BubbleHelper>,
    shellInit: ShellInit,
) {
    init {
        shellInit.addInitCallback(::onInit, this)
    }

    private fun onInit() {
        handleCrashIfNeeded()
    }

    private fun handleCrashIfNeeded() {
        bubbleHelper.ifPresent { handleBubbleTaskCleanup(it) }
        handlePipTaskCleanup()
    }

    private fun addLaunchHomePendingIntent(
        wct: WindowContainerTransaction,
        displayId: Int,
    ): WindowContainerTransaction {
        // TODO: b/400462917 - Check that crashes are also handled correctly on HSUM devices. We
        // might need to pass the [userId] here to launch the correct home.
        homeIntentProvider.addLaunchHomePendingIntent(wct, displayId)
        return wct
    }

    /**
     * Cleans up any existing bubble tasks by removing bubble specific overrides. After cleanup, the
     * device will be transitioned to the home screen.
     */
    private fun handleBubbleTaskCleanup(bubbleHelper: BubbleHelper) {
        val wct = WindowContainerTransaction()
        for (task in shellTaskOrganizer.getRunningTasks()) {
            if (bubbleHelper.isAppBubbleTask(task)) {
                val exitWct =
                    BubbleUtils.getExitBubbleTransaction(
                        bubbleHelper,
                        task.token,
                        /* captionInsetsOwner= */ null,
                    )
                wct.merge(exitWct, /* transfer= */ true)
            }
        }
        if (!wct.isEmpty) {
            // Make sure we end up on the home screen
            addLaunchHomePendingIntent(wct, DEFAULT_DISPLAY)
            transitions.startTransition(WindowManager.TRANSIT_CHANGE, wct, NoOpTransitionHandler())
        }
    }

    private fun handlePipTaskCleanup() {
        for (task in shellTaskOrganizer.getRunningTasks()) {
            if (task.windowingMode == WINDOWING_MODE_PINNED) {
                // Any PiP task should be removed as previous session state is cleared in Shell.
                val wct = WindowContainerTransaction()
                wct.removeTask(task.token)
                transitions.startTransition(WindowManager.TRANSIT_CLOSE, wct, null)
                return
            }
        }
    }
}
