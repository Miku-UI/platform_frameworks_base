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

import android.graphics.PointF
import android.graphics.Rect
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.SurfaceControl
import android.view.WindowManager
import android.window.DesktopExperienceFlags
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import com.android.internal.jank.Cuj
import com.android.internal.jank.InteractionJankMonitor
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.ResizeTrigger
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.getInputMethodType
import com.android.wm.shell.desktopmode.DesktopTasksController
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_BOTTOM
import com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_LEFT
import com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_RIGHT
import com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_TOP
import java.util.concurrent.TimeUnit

/**
 * Orchestrator class for handling task drag-resizing and drag-moving operations.
 *
 * This class acts as a controller that delegates the actual work to either a [TaskMover] or a
 * [TaskResizer] implementation based on the user's input (ctrlType).
 *
 * @param windowDecoration The [WindowDecorationWrapper] associated with the task being positioned.
 * @param displayController A [DisplayController] to query display information.
 * @param taskMover The [TaskMover] implementation to use for drag-move operations.
 * @param taskResizer The [TaskResizer] implementation to use for drag-resize operations.
 * @param transitions The [Transitions] controller for handling window transitions.
 * @param handler The handler for the main shell thread.
 * @param desktopTasksController The controller for desktop mode windowing logic and transitions.
 * @param desktopUserRepositories The repository for desktop mode per-user data.
 */
class ResizeTaskPositioner(
    private val windowDecoration: WindowDecorationWrapper,
    private val displayController: DisplayController,
    private val taskMover: TaskMover,
    private val taskResizer: TaskResizer,
    private val transitions: Transitions,
    private val handler: Handler,
    private val desktopTasksController: DesktopTasksController,
    private val desktopUserRepositories: DesktopUserRepositories,
    private val interactionJankMonitor: InteractionJankMonitor,
) : TaskPositioner, Transitions.TransitionHandler {
    // The TaskPositioner interface requires the add and remove DragEventListener methods,
    // making this the central manager of the listener list.
    private val resizeEventListeners =
        mutableListOf<DragPositioningCallbackUtility.DragEventListener>()
    private var dragSession: DragSession? = null
    private val isResizing: Boolean
        get() =
            dragSession?.let {
                (it.ctrlType and CTRL_TYPE_TOP) != 0 ||
                    (it.ctrlType and CTRL_TYPE_BOTTOM) != 0 ||
                    (it.ctrlType and CTRL_TYPE_LEFT) != 0 ||
                    (it.ctrlType and CTRL_TYPE_RIGHT) != 0
            } ?: false

    override fun onDragPositioningStart(
        ctrlType: Int,
        displayId: Int,
        x: Float,
        y: Float,
        inputMethodType: Int,
    ): Rect {
        val taskBounds = Rect(windowDecoration.taskInfo.configuration.windowConfiguration.bounds)
        logD(
            TAG,
            windowDecoration.taskInfo.taskId,
            "onDragPositioningStart: taskId=%d, ctrlType=%d, displayId=%d, x=%f, y=%f, " +
                "taskBounds=%s",
            windowDecoration.taskInfo.taskId,
            ctrlType,
            displayId,
            x,
            y,
            taskBounds,
        )
        val rotation = windowDecoration.taskInfo.configuration.windowConfiguration.displayRotation
        val resizeTrigger =
            if (
                ctrlType == CTRL_TYPE_BOTTOM ||
                    ctrlType == CTRL_TYPE_TOP ||
                    ctrlType == CTRL_TYPE_RIGHT ||
                    ctrlType == CTRL_TYPE_LEFT
            ) {
                ResizeTrigger.EDGE
            } else {
                ResizeTrigger.CORNER
            }
        val inputMethod = getInputMethodType(inputMethodType)

        val newDragSession =
            DragSession(
                    windowDecoration,
                    ctrlType = ctrlType,
                    taskBoundsAtDragStart = Rect(taskBounds),
                    repositionTaskBounds = Rect(taskBounds),
                    repositionStartPoint = PointF(x, y),
                    rotation = rotation,
                    hasFirstMoveEventConsumed = false,
                    resizeTrigger = resizeTrigger,
                    inputMethod = inputMethod,
                    resizeEventListeners = resizeEventListeners,
                )
                .also {
                    dragSession = it
                    if (DesktopExperienceFlags.ENABLE_BOUNDS_RESTORING_ON_DRAG_EXIT.isTrue) {
                        val profile =
                            desktopUserRepositories.getProfile(windowDecoration.taskInfo.userId)
                        it.desktopRepository = profile
                        it.shouldRestoreBoundsOnMove =
                            profile.hasBoundsBeforeSnapOrMaximize(windowDecoration.taskInfo)
                        logD(
                            TAG,
                            windowDecoration.taskInfo.taskId,
                            "onDragPositioningStart: shouldRestoreBoundsOnMove=%b",
                            it.shouldRestoreBoundsOnMove,
                        )
                    }
                }

        displayController
            .getDisplayLayout(windowDecoration.taskInfo.displayId)
            ?.getStableBounds(newDragSession.stableBounds)

        if (isResizing) {
            taskResizer.onResizeStart(newDragSession)
            // Capture CUJ for re-sizing window in DW mode.
            interactionJankMonitor.begin(
                createLongTimeoutJankConfigBuilder(Cuj.CUJ_DESKTOP_MODE_RESIZE_WINDOW)
            )
        }

        return Rect(newDragSession.repositionTaskBounds)
    }

    override fun onDragPositioningMove(displayId: Int, x: Float, y: Float): Rect {
        check(Looper.myLooper() == handler.looper) {
            "This method must run on the shell main thread."
        }
        motionEventLogD(
            TAG,
            windowDecoration.taskInfo.taskId,
            "onDragPositioningMove: displayId=%d, x=%f, y=%f",
            displayId,
            x,
            y,
        )
        val session = checkNotNull(dragSession) { "DragSession must not be null during a move." }

        if (isResizing) {
            taskResizer.onResizeUpdate(session, x, y)
        } else {
            taskMover.onMoveUpdate(session, displayId, x, y)?.let {
                session.pendingBoundsRestoreTransition =
                    transitions.startTransition(WindowManager.TRANSIT_CHANGE, it, this)
            }
        }

        if (!session.hasFirstMoveEventConsumed) {
            // Update taskbar rounding once the drag/resize has registered a move event - in case
            // the moved task is no longer maximized. Only call this once per resize/drag so we
            // don't call into Launcher with each drag/resize frame to try to update the taskbar.
            desktopTasksController
                .getDesktopScrimController()
                .updateDesktopScrimOnResize(
                    displayId,
                    windowDecoration.taskInfo.taskId,
                    Rect(session.repositionTaskBounds),
                )
            session.hasFirstMoveEventConsumed = true

            if (!isResizing) {
                // Begin window drag CUJ instrumentation only when drag position moves.
                interactionJankMonitor.begin(
                    createLongTimeoutJankConfigBuilder(Cuj.CUJ_DESKTOP_MODE_DRAG_WINDOW)
                )
            }
        }

        return Rect(session.repositionTaskBounds)
    }

    override fun onDragPositioningEnd(displayId: Int, x: Float, y: Float): Rect {
        logD(
            TAG,
            windowDecoration.taskInfo.taskId,
            "onDragPositioningEnd: taskId=%d, displayId=%d, x=%f, y=%f",
            windowDecoration.taskInfo.taskId,
            displayId,
            x,
            y,
        )
        val session =
            checkNotNull(dragSession) { "DragSession must not be null when ending a drag." }
        if (isResizing) {
            val wct = taskResizer.onResizeEnd(session, x, y)
            wct?.let {
                session.dragResizeEndTransition =
                    transitions.startTransition(WindowManager.TRANSIT_CHANGE, it, this).also { t ->
                        desktopTasksController.onDragResizeTransitionStarted(t)
                    }
            }
                ?: run {
                    // If no transition is started, clear the session now.
                    logD(
                        TAG,
                        windowDecoration.taskInfo.taskId,
                        "onDragPositioningEnd: no transition started, clearing session",
                    )
                    dragSession = null
                }
        } else {
            taskMover.onMoveEnd(session, displayId, x, y)
            dragSession = null
        }

        return Rect(session.repositionTaskBounds)
    }

    override fun close() {
        // TODO(b/465979009): Remove this API after TaskPositioner refactor.
        // Its original purpose was to unregister DisplayWindowListener for topology changes, which
        // is now handled by TaskMover.
    }

    override fun isResizingOrAnimating(): Boolean {
        return dragSession?.isResizingOrAnimatingResize ?: false
    }

    override fun addDragEventListener(
        dragEventListener: DragPositioningCallbackUtility.DragEventListener
    ) {
        resizeEventListeners.add(dragEventListener)
    }

    override fun removeDragEventListener(
        dragEventListener: DragPositioningCallbackUtility.DragEventListener
    ) {
        resizeEventListeners.remove(dragEventListener)
    }

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
    ): Boolean {
        for (change in info.changes) {
            if (change.taskInfo == null) {
                // Ignore non-task (e.g., display, activity) changes.
                continue
            }
            val sc = change.leash
            val endBounds = change.endAbsBounds
            val endPosition = change.endRelOffset
            startTransaction
                .setWindowCrop(sc, endBounds.width(), endBounds.height())
                .setPosition(sc, endPosition.x.toFloat(), endPosition.y.toFloat())
            finishTransaction
                .setWindowCrop(sc, endBounds.width(), endBounds.height())
                .setPosition(sc, endPosition.x.toFloat(), endPosition.y.toFloat())
        }
        startTransaction.apply()

        dragSession?.let {
            if (it.pendingBoundsRestoreTransition == transition) {
                it.pendingBoundsRestoreTransition = null
            } else if (it.dragResizeEndTransition == transition) {
                taskResizer.cleanup(it)
                dragSession = null
            }
        }

        finishCallback.onTransitionFinished(null)
        interactionJankMonitor.end(Cuj.CUJ_DESKTOP_MODE_RESIZE_WINDOW)
        return true
    }

    /**
     * We should never reach this as this handler's transitions are only started from shell
     * explicitly.
     */
    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo,
    ): WindowContainerTransaction? {
        return null
    }

    override fun onTransitionConsumed(
        transition: IBinder,
        aborted: Boolean,
        finishTransaction: SurfaceControl.Transaction?,
    ) {
        dragSession?.let {
            if (it.dragResizeEndTransition == transition) {
                taskResizer.cleanup(it)
                dragSession = null
            }
        }
    }

    private fun createLongTimeoutJankConfigBuilder(
        @com.android.internal.jank.Cuj.CujType cujType: Int
    ) =
        InteractionJankMonitor.Configuration.Builder.withSurface(
                cujType,
                windowDecoration.decorWindowContext,
                windowDecoration.taskSurface,
                handler,
            )
            .setTimeout(LONG_CUJ_TIMEOUT_MS)

    companion object {
        private const val TAG = "ResizeTaskPositioner"

        // Timeout used for resize and drag CUJs, this is longer than the default timeout to avoid
        // timing out in the middle of a resize or drag action.
        private val LONG_CUJ_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(/* duration= */ 10L)
    }
}
