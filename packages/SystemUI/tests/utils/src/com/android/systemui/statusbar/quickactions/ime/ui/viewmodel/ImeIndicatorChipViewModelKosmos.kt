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

package com.android.systemui.statusbar.quickactions.ime.ui.viewmodel

import android.content.applicationContext
import android.view.Display
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.statusbar.quickactions.domain.interactor.quickActionsInteractor
import com.android.systemui.statusbar.quickactions.ime.domain.interactor.imeIndicatorChipInteractor

val Kosmos.imeIndicatorChipViewModel by Fixture {
    ImeIndicatorChipViewModel(
        context = applicationContext,
        displayId = Display.DEFAULT_DISPLAY,
        sceneInteractor = sceneInteractor,
        quickActionsInteractor = quickActionsInteractor,
        imeIndicatorChipInteractor = imeIndicatorChipInteractor,
    )
}

val Kosmos.imeIndicatorChipViewModelFactory: ImeIndicatorChipViewModel.Factory by Fixture {
    object : ImeIndicatorChipViewModel.Factory {
        override fun create(displayId: Int): ImeIndicatorChipViewModel =
            ImeIndicatorChipViewModel(
                context = applicationContext,
                displayId = displayId,
                sceneInteractor = sceneInteractor,
                quickActionsInteractor = quickActionsInteractor,
                imeIndicatorChipInteractor = imeIndicatorChipInteractor,
            )
    }
}
