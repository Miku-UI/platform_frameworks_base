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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.android.systemui.qs.panels.ui.model.QsShadeComponent
import com.android.systemui.qs.panels.ui.model.QsShadeComponent.BRIGHTNESS
import com.android.systemui.qs.panels.ui.model.QsShadeComponent.MEDIA
import com.android.systemui.qs.panels.ui.model.QsShadeComponent.TILES_GRID
import com.android.systemui.qs.panels.ui.viewmodel.EditModeLayoutTabViewModel.DragState
import javax.inject.Inject

class EditModeLayoutTabViewModelImpl @Inject constructor() : EditModeLayoutTabViewModel {
    override val components: SnapshotStateList<QsShadeComponent> =
        mutableStateListOf(BRIGHTNESS, TILES_GRID, MEDIA)

    override var dragState: DragState? by mutableStateOf(null)

    override fun onHover(source: QsShadeComponent, target: QsShadeComponent?) {
        if (target == null || source == target) return

        components.apply {
            val targetIndex = components.indexOf(target)
            components.remove(source)
            components.add(targetIndex, source)
        }
    }

    override fun onDragStart(component: QsShadeComponent, offset: Int) {
        dragState = DragState(component, offset)
    }

    override fun onDrag(dragAmount: Int, idleOffset: Int) {
        dragState =
            dragState?.let { it.copy(offset = it.offset + dragAmount, idleOffset = idleOffset) }
    }

    override fun onDragEnd() {
        dragState = null
    }
}
