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

package com.android.wm.shell.pinnedlayer.phone

import android.graphics.Rect
import android.view.SurfaceControl
import com.android.wm.shell.windowdecor.OnTaskResizeAnimationListener

/**
 * A class that reflects a UI state of the pinned layer windows, for example, what
 * [PinnedLayerHandler] plans to do with a [android.app.TaskInfo] after handling the
 * [com.android.wm.shell.transition.Transitions], e.g. animating, resizing, etc.
 */
class PinnedLayerUiState {

    private var resizeAnimationListener: OnTaskResizeAnimationListener? = null

    /**
     * Sets a [OnTaskResizeAnimationListener] as a main listener for resize animations.
     *
     * @param listener a listener to be invoked.
     * @see OnTaskResizeAnimationListener
     */
    fun setOnTaskResizeAnimationListener(listener: OnTaskResizeAnimationListener?) {
        resizeAnimationListener = listener
    }

    /**
     * Dispatches an update that window move/resize has started.
     *
     * @param taskId a task id that represents a window that being moved/resized.
     * @param t a start transaction to be applied on the animation start.
     * @param bounds a [Rect] that represent window bound on the animation start.
     * @return `true` if the transaction was applied, `false` otherwise.
     * @see OnTaskResizeAnimationListener.onAnimationStart
     */
    fun onResizeMoveStarted(taskId: Int, t: SurfaceControl.Transaction, bounds: Rect): Boolean {
        return resizeAnimationListener?.onAnimationStart(taskId, t, bounds) ?: false
    }

    /**
     * Dispatches an update that window move/resize has been updated.
     *
     * @param taskId a task id that represents a window that being moved/resized.
     * @param t a start transaction to be applied on the animation update.
     * @param bounds a [Rect] that represent window bound on the animation update.
     * @return `true` if the transaction was applied, `false` otherwise.
     * @see OnTaskResizeAnimationListener.onBoundsChange
     */
    fun onResizeMoveUpdated(taskId: Int, t: SurfaceControl.Transaction, bounds: Rect): Boolean {
        return resizeAnimationListener?.onBoundsChange(taskId, t, bounds) ?: false
    }

    /**
     * Dispatched an update the window move/resize has ended.
     *
     * @param taskId a task id that represents a window that being moved/resized.
     * @see OnTaskResizeAnimationListener.onAnimationEnd
     */
    fun onResizeMoveEnded(taskId: Int) {
        resizeAnimationListener?.onAnimationEnd(taskId)
    }
}
