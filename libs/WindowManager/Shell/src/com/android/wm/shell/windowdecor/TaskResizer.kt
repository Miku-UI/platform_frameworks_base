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

import android.window.WindowContainerTransaction

/** Interface for resizing a task via drag gestures. */
interface TaskResizer {
    /**
     * Called when a drag resize starts.
     *
     * @param session The drag session for the current drag operation.
     */
    fun onResizeStart(session: DragSession)

    /**
     * Called when a drag resize updates.
     *
     * @param session The drag session for the current drag operation.
     * @param x The new x coordinate of the drag point.
     * @param y The new y coordinate of the drag point.
     * @return whether the current resize operation is violating size constraints.
     */
    fun onResizeUpdate(session: DragSession, x: Float, y: Float): Boolean

    /**
     * Called when a drag resize ends.
     *
     * @param session The drag session for the current drag operation.
     * @param x The final x coordinate of the drag point.
     * @param y The final y coordinate of the drag point.
     * @return A [WindowContainerTransaction] to apply the final resize, or `null` if no change.
     */
    fun onResizeEnd(session: DragSession, x: Float, y: Float): WindowContainerTransaction?

    /**
     * Called by the orchestrator after the handler's animation logic has been applied or the
     * transition has been aborted, to clean up resources.
     *
     * @param session The drag session for the relevant drag operation.
     */
    fun cleanup(session: DragSession)
}
