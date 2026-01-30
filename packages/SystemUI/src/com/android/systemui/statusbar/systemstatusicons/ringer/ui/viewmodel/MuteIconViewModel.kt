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

package com.android.systemui.statusbar.systemstatusicons.ringer.ui.viewmodel

import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.getValue
import com.android.settingslib.volume.domain.interactor.AudioVolumeInteractor
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.res.R
import com.android.systemui.statusbar.systemstatusicons.SystemStatusIconsInCompose
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.map

/**
 * View model for the "ringer silent" system status icon. Emits an icon when the ringer is silenced.
 * Null icon otherwise.
 */
class MuteIconViewModel
@AssistedInject
constructor(@Assisted context: Context, interactor: AudioVolumeInteractor) :
    SystemStatusIconViewModel.Default, ExclusiveActivatable() {

    init {
        SystemStatusIconsInCompose.expectInNewMode()
    }

    private val hydrator = Hydrator("MuteIconViewModel.hydrator")

    override val slotName = context.getString(com.android.internal.R.string.status_bar_mute)

    override val visible: Boolean by
        hydrator.hydratedStateOf(
            traceName = "SystemStatus.muteVisible",
            initialValue = false,
            source = interactor.ringerMode.map { it.value == AudioManager.RINGER_MODE_SILENT },
        )

    override val icon: Icon?
        get() = visible.toUiState()

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    private fun Boolean.toUiState(): Icon? =
        if (this) {
            Icon.Resource(
                resId = R.drawable.ic_speaker_mute,
                contentDescription =
                    ContentDescription.Resource(R.string.accessibility_ringer_silent),
            )
        } else {
            null
        }

    @AssistedFactory
    interface Factory {
        fun create(context: Context): MuteIconViewModel
    }
}
