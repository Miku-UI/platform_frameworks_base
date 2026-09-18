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

package com.android.systemui.volume.panel.component.mediainput.ui.viewmodel

import androidx.compose.runtime.getValue
import com.android.internal.logging.UiEventLogger
import com.android.systemui.animation.Expandable
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.media.dialog.MediaSwitchingType
import com.android.systemui.res.R
import com.android.systemui.volume.panel.component.mediainput.domain.interactor.MediaInputComponentInteractor
import com.android.systemui.volume.panel.component.mediaoutput.domain.interactor.MediaOutputActionsInteractor
import com.android.systemui.volume.panel.ui.VolumePanelUiEvent
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.map

class MediaInputViewModel
@AssistedInject
constructor(
    mediaInputComponentInteractor: MediaInputComponentInteractor,
    private val actionsInteractor: MediaOutputActionsInteractor,
    private val uiEventLogger: UiEventLogger,
) : HydratedActivatable() {
    val hasInputDevice: Boolean by
        mediaInputComponentInteractor.currentInputDevice
            .map { mediaDevice -> mediaDevice != null }
            .hydratedStateOf(initialValue = false)

    val connectedDeviceName: String? by
        mediaInputComponentInteractor.currentInputDevice
            .map { mediaDevice -> mediaDevice?.name }
            .hydratedStateOf(initialValue = null)

    val connectedDeviceIcon: Icon by
        mediaInputComponentInteractor.currentInputDevice
            .map { mediaDevice ->
                mediaDevice?.icon?.let { Icon.Loaded(it, null) }
                    ?: Icon.Resource(R.drawable.ic_media_home_devices, null)
            }
            .hydratedStateOf(initialValue = Icon.Resource(R.drawable.ic_media_home_devices, null))

    fun onBarClick(expandable: Expandable?) {
        uiEventLogger.log(VolumePanelUiEvent.VOLUME_PANEL_MEDIA_INPUT_CLICKED)
        actionsInteractor.onBarClick(/* model= */ null, expandable, MediaSwitchingType.INPUT)
    }

    @AssistedFactory
    interface Factory {
        fun create(): MediaInputViewModel
    }
}
