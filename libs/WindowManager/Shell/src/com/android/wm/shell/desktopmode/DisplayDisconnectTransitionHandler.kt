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

import android.os.IBinder
import android.util.Log
import android.view.Display.INVALID_DISPLAY
import android.view.SurfaceControl
import android.window.DesktopExperienceFlags
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import com.android.internal.protolog.ProtoLog
import com.android.window.flags.Flags.enableDisplayDisconnectSplitscreen
import com.android.wm.shell.fullscreen.FullscreenDisconnectHandler
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerController
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.splitscreen.SplitScreenController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import java.util.Optional

/**
 * Handler to animate the transition from disconnecting a display.
 *
 * TODO: b/488139835 Consider moving this out of desktop package as it becomes less
 *   desktop-specific.
 */
class DisplayDisconnectTransitionHandler(
    val transitions: Transitions,
    shellInit: ShellInit,
    private val splitScreenController: Optional<SplitScreenController>,
    private val desktopTasksController: Optional<DesktopTasksController>,
    private val fullscreenDisconnectHandler: Optional<FullscreenDisconnectHandler>,
    private val pinnedLayerController: Optional<PinnedLayerController>,
    private val pipDisplayDisconnectHandler: Optional<PipDisplayDisconnectHandler>,
) : Transitions.TransitionHandler {

    private val pendingTransitions = mutableSetOf<IBinder>()

    init {
        shellInit.addInitCallback({ onInit() }, this)
    }

    private fun onInit() {
        transitions.addHandler(this)
    }

    fun addPendingTransition(transition: IBinder) = pendingTransitions.add(transition)

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
    ): Boolean {
        if (!pendingTransitions.contains(transition)) return false
        // TODO: b/391652399 - Animate transitions
        startTransaction.apply()
        finishCallback.onTransitionFinished(null)
        pendingTransitions.remove(transition)
        return true
    }

    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo,
    ): WindowContainerTransaction? {
        // TODO: b/448471638 - support multiple display changes
        val displayChange = request.displayChanges?.firstOrNull()
        if (
            !(DesktopExperienceFlags.ENABLE_DISPLAY_DISCONNECT_INTERACTION.isTrue &&
                displayChange != null)
        ) {
            Log.w(TAG, "No disconnect change found in the transition, not handling request.")
            return null
        }

        var reparentDisplay = displayChange.disconnectReparentDisplay
        if (reparentDisplay == INVALID_DISPLAY) {
            ProtoLog.v(WM_SHELL_DESKTOP_MODE, "$TAG: handleRequest: no reparent display; returning")
            return null
        }

        // This is a disconnect transition; flag it so we handle animation here later.
        // We need to do this here to prevent default handler from attempting to animate
        // disconnect transitions as this potentially crashes.
        addPendingTransition(transition)

        var handled = false
        val wct = WindowContainerTransaction()

        if (splitScreenController.isPresent && enableDisplayDisconnectSplitscreen()) {
            Log.w(
                TAG,
                "Handling disconnecting displayId=${displayChange.displayId}" +
                    " with splitScreen active",
            )
            splitScreenController
                .get()
                .multiDisplayProvider
                .addMoveSplitPairToDisplayChanges(
                    displayChange.displayId,
                    reparentDisplay,
                    wct,
                    false, /* toTop */
                )
            handled = !wct.isEmpty
        }
        if (desktopTasksController.isPresent) {
            val desktopWct =
                desktopTasksController
                    .get()
                    .onDisplayDisconnect(displayChange.displayId, reparentDisplay, transition)
            if (!desktopWct.isEmpty) {
                wct.merge(desktopWct, true)
                handled = true
            }
        }
        if (fullscreenDisconnectHandler.isPresent) {
            val fullscreenWct =
                fullscreenDisconnectHandler
                    .get()
                    .onDisplayDisconnect(displayChange.displayId, reparentDisplay)
            if (!fullscreenWct.isEmpty) {
                wct.merge(fullscreenWct, true)
                handled = true
            }
        }
        if (pinnedLayerController.isPresent) {
            pinnedLayerController
                .get()
                .getDisplayDisconnectChanges(transition, displayChange.displayId, reparentDisplay)
                ?.let { pinnedWct ->
                    wct.merge(pinnedWct, true)
                    handled = true
                }
        }
        if (pipDisplayDisconnectHandler.isPresent) {
            val pipWct =
                pipDisplayDisconnectHandler
                    .get()
                    .onDisplayDisconnect(displayChange.displayId, reparentDisplay)
            if (!pipWct.isEmpty) {
                wct.merge(pipWct, true)
                handled = true
            }
        }

        if (handled) {
            return wct
        }

        // Return null since another handler may want to make specific task changes.
        return null
    }

    companion object {
        private const val TAG = "DisconnectHandler"
    }
}
