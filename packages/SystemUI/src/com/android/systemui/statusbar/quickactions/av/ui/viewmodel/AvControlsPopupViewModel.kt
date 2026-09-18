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

package com.android.systemui.statusbar.quickactions.av.ui.viewmodel

import com.android.systemui.statusbar.quickactions.popups.ui.viewmodel.StatusBarPopupViewModel
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/**
 * View model for the popup dialog that appears after clicking on the AV Chip (Privacy Indicator) in
 * the status Bar on Desktop.
 */
class AvControlsPopupViewModel
@AssistedInject
constructor(
    val avControlsPanelContentViewModelFactory: AvControlsPanelContentViewModel.Factory,
    val sensorActivityViewModelFactory: SensorActivityViewModel.Factory,
    val blurDrillInViewModelFactory: BlurDrillInViewModel.Factory,
    val studioLookDrillInViewModelFactory: StudioLookDrillInViewModel.Factory,
) : StatusBarPopupViewModel {
    /** A factory to be used to create view model instances. */
    @AssistedFactory
    interface Factory : StatusBarPopupViewModel.Factory.AvControls {
        override fun create(): AvControlsPopupViewModel
    }
}
