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

package com.android.systemui.qs.panels.ui.viewmodel

import com.android.systemui.qs.panels.ui.model.QsShadeComponent

/** Viewmodel for the Edit mode Layout tab. */
interface EditModeLayoutTabViewModel {

    /** List of [QsShadeComponent] to display. */
    val components: List<QsShadeComponent>

    /** Current state of a drag movement. */
    val dragState: DragState?

    /** Callback when [source] is dragged over [target]. */
    fun onHover(source: QsShadeComponent, target: QsShadeComponent?)

    /**
     * Callback when a drag is started on [component].
     *
     * @param component The [QsShadeComponent] being dragged
     * @param offset The offset of the y-axis, in pixels, of the component at the start of the drag
     *   relative to the column.
     */
    fun onDragStart(component: QsShadeComponent, offset: Int)

    /**
     * Callback on drag movement
     *
     * @param dragAmount Delta of the drag
     * @param idleOffset Idle position for the dragged component.
     */
    fun onDrag(dragAmount: Int, idleOffset: Int)

    /** Callback when the drag is stopped. */
    fun onDragEnd()

    /** Holds the current state of a drag gesture. */
    data class DragState(
        /** [QsShadeComponent] being dragged. */
        val component: QsShadeComponent,

        /** Vertical offset in pixels of the drag relative to the layout composable. */
        val offset: Int,

        /**
         * Vertical offset for the idle position of the item.
         *
         * Used to animate back into position after the drag is released
         */
        val idleOffset: Int = offset,
    )

    interface Factory {
        fun create(): EditModeLayoutTabViewModel
    }
}
