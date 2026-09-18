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

import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAware
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.statusbar.quickactions.av.domain.interactor.AvControlsChipInteractor
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.map

class MicrophoneGlobalSwitchViewModel
@AssistedInject
constructor(@DisplayAware private val avControlsChipInteractor: AvControlsChipInteractor) :
    ButtonViewModel, HydratedActivatable() {

    override val state by
        avControlsChipInteractor.microphoneBlocked
            .map {
                ButtonUiState(
                    isEnabled = !it,
                    subText = com.android.systemui.res.R.string.av_mic_label,
                    image =
                        if (it) com.android.systemui.res.R.drawable.gs_mic_off
                        else com.android.systemui.res.R.drawable.gs_mic,
                )
            }
            .hydratedStateOf(initialValue = ButtonUiState())

    override suspend fun onClick() {
        avControlsChipInteractor.setMicrophoneBlocked(state.isEnabled)
    }

    /** A factory to be used to create view model instances. */
    @AssistedFactory
    interface Factory {
        fun create(): MicrophoneGlobalSwitchViewModel
    }
}
