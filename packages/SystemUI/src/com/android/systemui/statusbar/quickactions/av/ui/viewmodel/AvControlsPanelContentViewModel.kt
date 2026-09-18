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
import com.android.systemui.statusbar.quickactions.av.domain.interactor.DesktopEffectInteractor
import com.android.systemui.statusbar.quickactions.av.shared.model.DesktopEffectModel
import com.android.systemui.statusbar.quickactions.av.shared.model.SensorActivityModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class AvControlsPanelContentViewModel
@AssistedInject
constructor(
    @DisplayAware val avControlsChipInteractor: AvControlsChipInteractor,
    val desktopEffectInteractor: DesktopEffectInteractor,
    val sensorActivityViewModelFactory: SensorActivityViewModel.Factory,
    val cameraGlobalSwitchViewModelFactory: CameraGlobalSwitchViewModel.Factory,
    val microphoneGlobalSwitchViewModelFactory: MicrophoneGlobalSwitchViewModel.Factory,
    val studioLookButtonViewModelFactory: StudioLookButtonViewModel.Factory,
    val blurButtonViewModelFactory: BlurButtonViewModel.Factory,
    val studioMicViewModelFactory: StudioMicViewModel.Factory,
    val liveCaptionsViewModelFactory: LiveCaptionsViewModel.Factory,
    val cameraFramingViewModelFactory: CameraFramingViewModel.Factory,
    @Assisted val setCurrentPage: (PageType) -> Unit,
) : HydratedActivatable() {

    private enum class Sensor {
        CAMERA,
        MICROPHONE,
    }

    private fun showControl(sensor: Sensor, isEffectSupported: (DesktopEffectModel) -> Boolean) =
        // TODO(469060705): Optimize using flatMapLatest
        combine(
            avControlsChipInteractor.model.map { it.sensorActivityModel },
            desktopEffectInteractor.model.map { isEffectSupported(it) },
        ) { sensorActivityModel, supported ->
            supported &&
                when (sensorActivityModel) {
                    is SensorActivityModel.Active ->
                        when (sensorActivityModel.sensors) {
                            SensorActivityModel.Active.Sensors.CAMERA -> sensor == Sensor.CAMERA
                            SensorActivityModel.Active.Sensors.MICROPHONE ->
                                sensor == Sensor.MICROPHONE
                            SensorActivityModel.Active.Sensors.CAMERA_AND_MICROPHONE -> true
                        }
                    is SensorActivityModel.Inactive -> false
                }
        }

    /** True if studio look controls should be displayed */
    val showStudioLookControls: Boolean by
        showControl(Sensor.CAMERA) { it.faceRetouchSupported || it.portraitRelightSupported }
            .hydratedStateOf(initialValue = false)

    /** True if blur controls should be displayed */
    val showBlurControls: Boolean by
        showControl(Sensor.CAMERA) { it.blurSupported }.hydratedStateOf(initialValue = false)

    /** True if camera framing button should be displayed */
    val showCameraFramingButton: Boolean by
        showControl(Sensor.CAMERA) { it.cameraFramingSupported }
            .hydratedStateOf(initialValue = false)

    /** True if studio mic button should be displayed */
    val showStudioMicButton: Boolean by
        showControl(Sensor.MICROPHONE) { it.studioMicSupported }
            .hydratedStateOf(initialValue = false)

    /** True if live captions button should be displayed */
    val showLiveCaptionsButton: Boolean by
        desktopEffectInteractor.model
            .map { it.liveCaptionsSupported }
            .hydratedStateOf(initialValue = false)

    /** A factory to be used to create view model instances. */
    @AssistedFactory
    interface Factory {
        fun create(setCurrentPage: (PageType) -> Unit): AvControlsPanelContentViewModel
    }
}
