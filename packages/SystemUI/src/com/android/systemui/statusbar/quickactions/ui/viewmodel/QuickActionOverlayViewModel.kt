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

package com.android.systemui.statusbar.quickactions.ui.viewmodel

import android.graphics.Rect
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.statusbar.quickactions.domain.interactor.QuickActionsInteractor
import com.android.systemui.statusbar.quickactions.shared.model.QuickActionPanelModel
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlin.math.roundToInt

/** ViewModel for the currently active QuickActionPanel */
class QuickActionOverlayViewModel
@AssistedInject
constructor(
    private val interactor: QuickActionsInteractor,
    private val shadeInteractor: ShadeInteractor,
) {
    val activePanel: QuickActionPanelModel?
        get() = interactor.activePanel

    fun onShadeOverlayBoundsChanged(bounds: androidx.compose.ui.geometry.Rect?) {
        val boundsRect =
            bounds?.let {
                Rect(
                    it.left.roundToInt(),
                    it.top.roundToInt(),
                    it.right.roundToInt(),
                    it.bottom.roundToInt(),
                )
            }
        shadeInteractor.setShadeOverlayBounds(boundsRect)
    }

    fun close() {
        interactor.close()
    }

    @AssistedFactory
    interface Factory {
        fun create(): QuickActionOverlayViewModel
    }
}
