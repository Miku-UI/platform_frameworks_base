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

import android.window.DesktopExperienceFlags
import android.window.WindowContainerTransaction
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.desktopmode.freeformCaptionInsets
import com.android.wm.shell.shared.desktopmode.DesktopState

/** Implementation of [TaskResizer] for veiled resizing of tasks. */
class VeiledTaskResizer(
    private val displayController: DisplayController,
    private val desktopState: DesktopState,
) : TaskResizer {
    private val changeBoundsResult = DragPositioningCallbackUtility.ChangeBoundsResult()

    override fun onResizeStart(session: DragSession) {
        for (listener in session.resizeEventListeners) {
            listener.onDragResizeStarted(
                session.windowDecoration.taskInfo.taskId,
                session.resizeTrigger,
                session.inputMethod,
                session.taskBoundsAtDragStart,
            )
        }
    }

    override fun onResizeUpdate(session: DragSession, x: Float, y: Float): Boolean {
        DragPositioningCallbackUtility.changeBounds(
            session.ctrlType,
            session.repositionTaskBounds,
            session.taskBoundsAtDragStart,
            session.stableBounds,
            DragPositioningCallbackUtility.calculateDelta(x, y, session.repositionStartPoint),
            displayController,
            session.windowDecoration,
            desktopState.canEnterDesktopMode,
            changeBoundsResult,
        )
        if (changeBoundsResult.boundsChanged) {
            if (!session.isResizingOrAnimatingResize) {
                for (listener in session.resizeEventListeners) {
                    listener.onDragMove(session.windowDecoration.taskInfo.taskId)
                }
                session.windowDecoration.showResizeVeil(session.repositionTaskBounds)
                // Clear the stored pre-snapped/maximized bounds to prevent unintended restoration
                // if the user manually resizes the window.
                if (DesktopExperienceFlags.ENABLE_BOUNDS_RESTORING_ON_DRAG_EXIT.isTrue) {
                    session.desktopRepository?.removeBoundsBeforeSnapOrMaximize(
                        session.windowDecoration.taskInfo.taskId
                    )
                }
                session.isResizingOrAnimatingResize = true
            } else {
                session.windowDecoration.updateResizeVeil(session.repositionTaskBounds)
            }
        }
        return changeBoundsResult.violatingSizeConstraints
    }

    override fun onResizeEnd(
        session: DragSession,
        x: Float,
        y: Float,
    ): WindowContainerTransaction? {
        for (listener in session.resizeEventListeners) {
            listener.onDragResizeEnded(
                session.windowDecoration.taskInfo.taskId,
                session.resizeTrigger,
                session.inputMethod,
                session.repositionTaskBounds,
            )
        }
        DragPositioningCallbackUtility.changeBounds(
            session.ctrlType,
            session.repositionTaskBounds,
            session.taskBoundsAtDragStart,
            session.stableBounds,
            DragPositioningCallbackUtility.calculateDelta(x, y, session.repositionStartPoint),
            displayController,
            session.windowDecoration,
            desktopState.canEnterDesktopMode,
            changeBoundsResult,
        )

        session.windowDecoration.updateResizeVeil(session.repositionTaskBounds)
        val boundsChanged = session.taskBoundsAtDragStart != session.repositionTaskBounds
        logD(
            TAG,
            session.windowDecoration.taskInfo.taskId,
            "onResizeEnd: boundsChanged=%b, bounds=%s",
            boundsChanged,
            session.repositionTaskBounds,
        )
        if (boundsChanged) {
            val wct = WindowContainerTransaction()
            wct.setBounds(session.windowDecoration.taskInfo.token, session.repositionTaskBounds)
            val captionInsets = session.windowDecoration.taskInfo.freeformCaptionInsets
            if (captionInsets != 0) {
                logD(
                    TAG,
                    session.windowDecoration.taskInfo.taskId,
                    "onResizeEnd: resetting app bounds for caption insets=%d",
                    captionInsets,
                )
                // Reset app bounds if app bounds were overridden.
                wct.setAppBounds(session.windowDecoration.taskInfo.token, null)
            }
            return wct
        } else {
            endDragResizeSession(session)
            return null
        }
    }

    override fun cleanup(session: DragSession) {
        logD(TAG, session.windowDecoration.taskInfo.taskId, "cleanup")
        endDragResizeSession(session)
    }

    private fun endDragResizeSession(session: DragSession) {
        if (session.isResizingOrAnimatingResize) {
            session.windowDecoration.hideResizeVeil()
            session.isResizingOrAnimatingResize = false
        }
    }

    companion object {
        private const val TAG = "VeiledTaskResizer"
    }
}
