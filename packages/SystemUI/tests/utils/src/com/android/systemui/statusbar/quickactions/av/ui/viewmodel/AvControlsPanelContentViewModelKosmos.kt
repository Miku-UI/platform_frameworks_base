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

import com.android.systemui.kosmos.Kosmos
import com.android.systemui.statusbar.quickactions.av.domain.interactor.avControlsChipInteractor
import com.android.systemui.statusbar.quickactions.av.domain.interactor.desktopEffectInteractor

val Kosmos.avControlsPanelContentViewModelFactory: AvControlsPanelContentViewModel.Factory by
    Kosmos.Fixture {
        object : AvControlsPanelContentViewModel.Factory {
            override fun create(
                setCurrentPage: (PageType) -> Unit
            ): AvControlsPanelContentViewModel {
                return AvControlsPanelContentViewModel(
                    avControlsChipInteractor = avControlsChipInteractor,
                    desktopEffectInteractor = desktopEffectInteractor,
                    sensorActivityViewModelFactory = sensorActivityViewModelFactory,
                    cameraGlobalSwitchViewModelFactory = cameraGlobalSwitchViewModelFactory,
                    microphoneGlobalSwitchViewModelFactory = microphoneGlobalSwitchViewModelFactory,
                    studioLookButtonViewModelFactory = studioLookButtonViewModelFactory,
                    blurButtonViewModelFactory = blurButtonViewModelFactory,
                    studioMicViewModelFactory = studioMicViewModelFactory,
                    liveCaptionsViewModelFactory = liveCaptionsViewModelFactory,
                    cameraFramingViewModelFactory = cameraFramingViewModelFactory,
                    setCurrentPage = setCurrentPage,
                )
            }
        }
    }
