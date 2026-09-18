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

package com.android.wm.shell.desktopmode.api.impl

import android.graphics.Region
import android.view.Display.DEFAULT_DISPLAY
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.desktopmode.DesktopTasksController
import com.android.wm.shell.desktopmode.api.DesktopMode
import com.android.wm.shell.desktopmode.data.DesktopRepository.DeskChangeListener
import com.android.wm.shell.desktopmode.data.DesktopRepository.VisibleTasksListener
import com.android.wm.shell.desktopmode.desktopfirst.DesktopFirstListenerManager
import com.android.wm.shell.desktopmode.homescreenpeeking.DesktopHomeScreenPeekController
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.annotations.ExternalThread
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.shared.desktopmode.DesktopFirstListener
import com.android.wm.shell.shared.desktopmode.DesktopModeTransitionSource
import com.android.wm.shell.shared.desktopmode.DesktopScrimListener
import java.util.Optional
import java.util.concurrent.Executor
import java.util.function.Consumer
import kotlin.jvm.optionals.getOrNull

/** The interface for calls to DesktopMode from outside the shell, within the host process. */
@ExternalThread
class DesktopModeImpl(
    private val desktopTasksController: Optional<DesktopTasksController>,
    private val desktopFirstListenerManager: Optional<DesktopFirstListenerManager>,
    private val desktopHomeScreenPeekController: DesktopHomeScreenPeekController,
    @ShellMainThread private val mainExecutor: ShellExecutor,
) : DesktopMode {
    override fun addVisibleTasksListener(
        listener: VisibleTasksListener,
        callbackExecutor: Executor,
    ) {
        mainExecutor.execute {
            desktopTasksController.getOrNull()?.addVisibleTasksListener(listener, callbackExecutor)
        }
    }

    override fun addDeskChangeListener(listener: DeskChangeListener, callbackExecutor: Executor) {
        mainExecutor.execute {
            desktopTasksController.getOrNull()?.addDeskChangeListener(listener, callbackExecutor)
        }
    }

    override fun removeDeskChangeListener(listener: DeskChangeListener) {
        mainExecutor.execute {
            desktopTasksController.getOrNull()?.removeDeskChangeListener(listener)
        }
    }

    override fun addDesktopGestureExclusionRegionListener(
        listener: Consumer<Region>,
        callbackExecutor: Executor,
    ) {
        mainExecutor.execute {
            desktopTasksController.getOrNull()?.setTaskRegionListener(listener, callbackExecutor)
        }
    }

    override fun addDesktopScrimListener(
        listener: DesktopScrimListener,
        callbackExecutor: Executor,
    ) {
        mainExecutor.execute {
            desktopTasksController
                .getOrNull()
                ?.getDesktopScrimController()
                ?.addDesktopScrimListener(listener, callbackExecutor)
        }
    }

    override fun removeDesktopScrimListener(listener: DesktopScrimListener) {
        mainExecutor.execute {
            desktopTasksController
                .getOrNull()
                ?.getDesktopScrimController()
                ?.removeDesktopScrimListener(listener)
        }
    }

    override fun moveFocusedTaskToDesktop(
        displayId: Int,
        transitionSource: DesktopModeTransitionSource,
    ) {
        ProtoLog.v(WM_SHELL_DESKTOP_MODE, "$TAG: moveFocusedTaskToDesktop")
        mainExecutor.execute {
            desktopTasksController
                .getOrNull()
                ?.moveFocusedTaskToDesktop(
                    displayId = displayId,
                    transitionSource = transitionSource,
                )
        }
    }

    override fun moveFocusedTaskToFullscreen(
        displayId: Int,
        transitionSource: DesktopModeTransitionSource,
    ) {
        ProtoLog.v(WM_SHELL_DESKTOP_MODE, "$TAG: moveFocusedTaskToFullscreen")
        mainExecutor.execute {
            desktopTasksController
                .getOrNull()
                ?.enterFullscreen(displayId = displayId, transitionSource = transitionSource)
        }
    }

    override fun moveFocusedTaskToStageSplit(displayId: Int, leftOrTop: Boolean) {
        ProtoLog.v(WM_SHELL_DESKTOP_MODE, "$TAG: moveFocusedTaskToStageSplit")
        mainExecutor.execute {
            desktopTasksController
                .getOrNull()
                ?.enterSplit(displayId = displayId, leftOrTop = leftOrTop)
        }
    }

    override fun toggleFocusedTaskFullscreenState(
        displayId: Int,
        transitionSource: DesktopModeTransitionSource,
    ) {
        ProtoLog.v(WM_SHELL_DESKTOP_MODE, "$TAG: toggleFocusedTaskFullscreenState")
        mainExecutor.execute {
            desktopTasksController
                .getOrNull()
                ?.toggleFocusedTaskFullscreenState(
                    displayId = displayId,
                    transitionSource = transitionSource,
                )
        }
    }

    override fun registerDesktopFirstListener(listener: DesktopFirstListener) {
        ProtoLog.v(WM_SHELL_DESKTOP_MODE, "$TAG: registerDesktopFirstListener")
        if (desktopFirstListenerManager.isEmpty) {
            throw UnsupportedOperationException(
                "DesktopFirstListenerManager is not available on this device"
            )
        }
        mainExecutor.execute { desktopFirstListenerManager.get().registerListener(listener) }
    }

    override fun unregisterDesktopFirstListener(listener: DesktopFirstListener) {
        ProtoLog.v(WM_SHELL_DESKTOP_MODE, "$TAG: unregisterDesktopFirstListener")
        if (desktopFirstListenerManager.isEmpty) {
            throw UnsupportedOperationException(
                "DesktopFirstListenerManager is not available on this device"
            )
        }
        mainExecutor.execute { desktopFirstListenerManager.get().unregisterListener(listener) }
    }

    override fun isDisplayInDesktopMode(displayId: Int) =
        mainExecutor.executeBlockingForResult(
            { desktopTasksController.getOrNull()?.isDisplayInDesktopMode(displayId) ?: false },
            Boolean::class.javaObjectType,
        ) ?: false

    override fun togglePeek(displayId: Int) {
        if (displayId != DEFAULT_DISPLAY) {
            ProtoLog.w(
                WM_SHELL_DESKTOP_MODE,
                "$TAG: togglePeek currently only supported on default display",
            )
            return
        }
        ProtoLog.v(WM_SHELL_DESKTOP_MODE, "$TAG: togglePeek")
        mainExecutor.execute {
            if (desktopHomeScreenPeekController.isPeeking) {
                desktopHomeScreenPeekController.unpeek()
            } else {
                desktopHomeScreenPeekController.peek()
            }
        }
    }

    companion object {
        private const val TAG = "DesktopModeImpl"
    }
}
