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

package com.android.systemui.statusbar.featurepods.av.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.ContentDescription.Companion.loadContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.res.R
import com.android.systemui.statusbar.featurepods.av.domain.interactor.AvControlsChipInteractor
import com.android.systemui.statusbar.featurepods.av.shared.model.AvControlsChipModel
import com.android.systemui.statusbar.featurepods.av.shared.model.SensorActivityModel
import com.android.systemui.statusbar.featurepods.popups.ui.model.ChipIcon
import com.android.systemui.statusbar.featurepods.popups.ui.model.ColorsModel
import com.android.systemui.statusbar.featurepods.popups.ui.model.HoverBehavior
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupChipId
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupChipModel
import com.android.systemui.statusbar.featurepods.popups.ui.viewmodel.StatusBarPopupChipViewModel
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.map

/** ViewModel for the VC Privacy Chip */
class AvControlsChipViewModel
@AssistedInject
constructor(
    @Application val applicationContext: Context,
    avControlsChipInteractor: AvControlsChipInteractor,
) : StatusBarPopupChipViewModel, ExclusiveActivatable() {
    companion object {
        val CAMERA_DRAWABLE: Int = com.android.internal.R.drawable.perm_group_camera
        val MICROPHONE_DRAWABLE: Int = com.android.internal.R.drawable.perm_group_microphone
    }

    private val hydrator: Hydrator = Hydrator("AvControlsChipViewModel.hydrator")

    override val chip: PopupChipModel by
        hydrator.hydratedStateOf(
            traceName = "chip",
            initialValue = PopupChipModel.Hidden(PopupChipId.AvControlsIndicator),
            source = avControlsChipInteractor.model.map { toPopupChipModel(it) },
        )

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    private fun toPopupChipModel(avControlsChipModel: AvControlsChipModel): PopupChipModel {
        val chipId = PopupChipId.AvControlsIndicator
        val sensorActivityModel = avControlsChipModel.sensorActivityModel
        return when (sensorActivityModel) {
            is SensorActivityModel.Inactive -> PopupChipModel.Hidden(chipId)
            is SensorActivityModel.Active ->
                PopupChipModel.Shown(
                    chipId = chipId,
                    icons = icons(sensorActivityModel = sensorActivityModel),
                    chipText = null,
                    colors = ColorsModel.AvControlsTheme,
                    hoverBehavior = HoverBehavior.None,
                    contentDescription =
                        contentDescription(sensorActivityModel = sensorActivityModel),
                )
        }
    }

    private fun contentDescription(sensorActivityModel: SensorActivityModel.Active): String? =
        when (sensorActivityModel.sensors) {
            SensorActivityModel.Active.Sensors.CAMERA -> R.string.accessibility_camera_in_use
            SensorActivityModel.Active.Sensors.MICROPHONE ->
                R.string.accessibility_microphone_in_use
            SensorActivityModel.Active.Sensors.CAMERA_AND_MICROPHONE ->
                R.string.accessibility_camera_and_microphone_in_use
        }.let { ContentDescription.Resource(it).loadContentDescription(applicationContext) }

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
