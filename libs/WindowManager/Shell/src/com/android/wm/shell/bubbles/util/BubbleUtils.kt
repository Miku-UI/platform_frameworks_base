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

package com.android.wm.shell.bubbles.util

import android.app.ActivityManager
import android.app.WindowConfiguration
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.graphics.Rect
import android.os.Binder
import android.view.WindowInsets
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import com.android.wm.shell.Flags
import com.android.wm.shell.bubbles.Bubble
import com.android.wm.shell.bubbles.BubbleHelper
import com.android.wm.shell.shared.bubbles.BubbleFlagHelper

object BubbleUtils {

    /**
     * Returns a [WindowContainerTransaction] that includes the necessary operations of entering or
     * exiting Bubble.
     */
    private fun getBubbleTransaction(
        bubbleHelper: BubbleHelper,
        token: WindowContainerToken,
        bounds: Rect?,
        toBubble: Boolean,
        isAppBubble: Boolean,
        reparentToTda: Boolean,
        captionInsetsOwner: Binder?,
    ): WindowContainerTransaction {
        val wct = WindowContainerTransaction()
        if (BubbleFlagHelper.enableRootTaskForBubble() && isAppBubble) {
            val rootToken = bubbleHelper.getAppBubbleRootTaskToken()
            if (toBubble && rootToken != null) {
                if (Flags.fixPipToBubbleRelaunch()) {
                    wct.setWindowingMode(token, WindowConfiguration.WINDOWING_MODE_UNDEFINED)
                }
                wct.reparent(token, rootToken, true /* onTop */)
                if (bounds != null) {
                    wct.setBounds(rootToken, bounds)
                }
                wct.setBounds(token, Rect())
                wct.setAlwaysOnTop(rootToken, true /* alwaysOnTop */)
            } else if (reparentToTda) {
                wct.reparent(token, null, true /* onTop */)
            }
        } else {
            if (reparentToTda) {
                // Reparenting must happen before setAlwaysOnTop() below since WCT operations are
                // applied in order and always-on-top for nested tasks is not supported
                wct.reparent(token, null, true)
            }
            wct.setWindowingMode(
                token,
                if (toBubble) WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW
                else WindowConfiguration.WINDOWING_MODE_UNDEFINED,
            )
            wct.setInterceptBackPressedOnTaskRoot(token, toBubble)
            wct.setTaskForceExcludedFromRecents(token, toBubble /* forceExcluded */)
            wct.setDisablePip(token, toBubble /* disablePip */)
            if (!isAppBubble || !BubbleFlagHelper.enableRootTaskForBubble()) {
                wct.setAlwaysOnTop(token, toBubble /* alwaysOnTop */)
            }
            if (!toBubble || isAppBubble) {
                // We only set launch next to Bubble for App Bubble, since new Task opened from Chat
                // Bubble should be launched in fullscreen.
                // Always reset everything when exit bubble.
                wct.setLaunchNextToBubble(token, toBubble /* launchNextToBubble */)
            }
            if (BubbleFlagHelper.enableCreateAnyBubble()) {
                wct.setDisableLaunchAdjacent(token, toBubble /* disableLaunchAdjacent */)
            }
        }
        if (BubbleFlagHelper.enableCreateAnyBubble()) {
            if (!toBubble && bounds != null) {
                // Clear bounds if moving out of Bubble.
                wct.setBounds(token, bounds)
            }
        }
        if (BubbleFlagHelper.enableCreateAnyBubble()) {
            if (!toBubble && captionInsetsOwner != null) {
                wct.removeInsetsSource(
                    token,
                    captionInsetsOwner,
                    0 /* index */,
                    WindowInsets.Type.captionBar(),
                )
            }
        }
        return wct
    }

    /**
     * Returns a [WindowContainerTransaction] that includes the necessary operations of entering
     * Bubble.
     *
     * @param isAppBubble App Bubble has some different UX from Chat Bubble.
     * @param reparentToTda Whether to reparent the task to the ancestor TaskDisplayArea (for if
     *   this task is a child of another root task)
     */
    @JvmOverloads
    @JvmStatic
    fun getEnterBubbleTransaction(
        bubbleHelper: BubbleHelper,
        token: WindowContainerToken,
        bounds: Rect,
        isAppBubble: Boolean,
        reparentToTda: Boolean = false,
    ): WindowContainerTransaction {
        return getBubbleTransaction(
            bubbleHelper,
            token,
            bounds,
            toBubble = true,
            isAppBubble,
            reparentToTda,
            captionInsetsOwner = null,
        )
    }

    /**
     * Returns a [WindowContainerTransaction] that includes the necessary operations of exiting
     * Bubble.
     */
    @JvmOverloads
    @JvmStatic
    fun getExitBubbleTransaction(
        bubbleHelper: BubbleHelper,
        token: WindowContainerToken,
        captionInsetsOwner: Binder?,
        resetBounds: Boolean = true,
        reparentToTda: Boolean = BubbleFlagHelper.enableRootTaskForBubble(),
    ): WindowContainerTransaction {
        return getBubbleTransaction(
            bubbleHelper,
            token,
            bounds = if (resetBounds) Rect() else null,
            toBubble = false,
            // Everything will be reset, so doesn't matter for exit.
            isAppBubble = true,
            reparentToTda,
            captionInsetsOwner,
        )
    }

    /** Determines if a bubble task is moving to fullscreen based on its windowing mode. */
    @JvmStatic
    fun ActivityManager.RunningTaskInfo?.isBubbleToFullscreen(): Boolean {
        return BubbleFlagHelper.enableCreateAnyBubble() &&
            this?.windowingMode == WINDOWING_MODE_FULLSCREEN
    }

    /** Determines if a bubble task is moving to another organized parent task. */
    @JvmStatic
    fun ActivityManager.RunningTaskInfo?.isBubbleMovedToAnotherRootTask(
        bubbleHelper: BubbleHelper
    ): Boolean {
        return this?.hasParentTask() == true && !bubbleHelper.isAppBubbleTask(this)
    }

    /** The task token that should be used for the WCT when updating bounds. */
    @JvmStatic
    fun Bubble.getTaskTokenForBoundsUpdate(bubbleHelper: BubbleHelper): WindowContainerToken? {
        val taskInfo = taskView?.taskInfo ?: return null
        return if (bubbleHelper.isAppBubbleTask(taskInfo)) {
            bubbleHelper.getAppBubbleRootTaskToken() ?: taskInfo.token
        } else {
            taskInfo.token
        }
    }
}
