/*
 * Copyright (C) 2024 The Android Open Source Project
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

import androidx.compose.runtime.getValue
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAware
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.res.R
import com.android.systemui.statusbar.quickactions.av.domain.interactor.AvControlsChipInteractor
import com.android.systemui.statusbar.quickactions.av.shared.model.AvControlsChipModel
import com.android.systemui.statusbar.quickactions.av.shared.model.SensorActivityModel
import com.android.systemui.statusbar.quickactions.popups.ui.viewmodel.StatusBarPopupChipViewModel
import com.android.systemui.statusbar.quickactions.shared.model.ChipIcon
import com.android.systemui.statusbar.quickactions.shared.model.QuickActionChipId
import com.android.systemui.statusbar.quickactions.shared.model.QuickActionChipModel
import com.android.systemui.statusbar.quickactions.ui.compose.ChipColors
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.map

/** ViewModel for the VC Privacy Chip */
class AvControlsChipViewModel
@AssistedInject
constructor(
    @DisplayAware avControlsChipInteractor: AvControlsChipInteractor,
    private val popupViewModelFactory: AvControlsPopupViewModel.Factory,
) : StatusBarPopupChipViewModel, HydratedActivatable() {
    companion object {
        val CAMERA_DRAWABLE: Int = R.drawable.av_controls_chip_camera
        val MICROPHONE_DRAWABLE: Int = R.drawable.av_controls_chip_mic
    }

    override val chip: QuickActionChipModel by
        avControlsChipInteractor.model
            .map { toPopupChipModel(it) }
            .hydratedStateOf(
                initialValue = QuickActionChipModel.Hidden(QuickActionChipId.AvControlsIndicator)
            )

    private fun toPopupChipModel(avControlsChipModel: AvControlsChipModel): QuickActionChipModel {
        val chipId = QuickActionChipId.AvControlsIndicator
        return when (val sensorActivityModel = avControlsChipModel.sensorActivityModel) {
            is SensorActivityModel.Inactive -> QuickActionChipModel.Hidden(chipId)
            is SensorActivityModel.Active ->
                QuickActionChipModel.PopupChip(
                    chipId = chipId,
                    icons = icons(sensorActivityModel = sensorActivityModel),
                    chipContent = null,
                    colors = ChipColors.AvControlsTheme,
                    contentDescription =
                        contentDescription(sensorActivityModel = sensorActivityModel),
                    popupViewModelFactory = popupViewModelFactory,
                )
        }
    }

    private fun contentDescription(
        sensorActivityModel: SensorActivityModel.Active
    ): ContentDescription =
        when (sensorActivityModel.sensors) {
            SensorActivityModel.Active.Sensors.CAMERA ->
                ContentDescription.Resource(R.string.accessibility_camera_in_use)

            SensorActivityModel.Active.Sensors.MICROPHONE ->
                ContentDescription.Resource(R.string.accessibility_microphone_in_use)

            SensorActivityModel.Active.Sensors.CAMERA_AND_MICROPHONE ->
                ContentDescription.Resource(R.string.accessibility_camera_and_microphone_in_use)
        }

    private fun icons(sensorActivityModel: SensorActivityModel.Active): List<ChipIcon> =
        when (sensorActivityModel.sensors) {
            SensorActivityModel.Active.Sensors.CAMERA -> listOf(CAMERA_DRAWABLE)
            SensorActivityModel.Active.Sensors.MICROPHONE -> listOf(MICROPHONE_DRAWABLE)
            SensorActivityModel.Active.Sensors.CAMERA_AND_MICROPHONE ->
                listOf(CAMERA_DRAWABLE, MICROPHONE_DRAWABLE)
        }.map {
            // TODO(b/414566470): Add content description for accessibility.
            ChipIcon(Icon.Resource(resId = it, contentDescription = null))
        }

    @AssistedFactory
    interface Factory {
        fun create(): AvControlsChipViewModel
    }
}
