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

package com.android.systemui.statusbar.systemstatusicons.heaset.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.res.R
import com.android.systemui.statusbar.pipeline.audio.domain.interactor.WiredAudioDeviceInteractor
import com.android.systemui.statusbar.systemstatusicons.SystemStatusIconsInCompose
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/**
 * Viewmodel responsible for showing a headset icon on the status bar.
 *
 * The icon is updated from the [WiredAudioDeviceInteractor.wiredAudioDevice] StateFlow
 *
 * Will show [R.drawable.ic_headset_mic] for headsets with a microphone Will show
 * [R.drawable.ic_headset] for headphones or headsets without a mic
 */
class HeadsetIconViewModel
@AssistedInject
constructor(@Assisted context: Context, interactor: WiredAudioDeviceInteractor) :
    SystemStatusIconViewModel.Default, HydratedActivatable() {
    init {
        SystemStatusIconsInCompose.expectInNewMode()
    }

    override val slotName = context.getString(com.android.internal.R.string.status_bar_headset)

    private val wiredAudioDevice by interactor.wiredAudioDevice.hydratedStateOf(initialValue = null)

    override val visible: Boolean
        get() = wiredAudioDevice != null

    override val icon: Icon?
        get() =
            wiredAudioDevice?.let {
                if (it.hasMic) {
                    Icon.Resource(
                        resId = R.drawable.ic_headset_mic,
                        contentDescription =
                            ContentDescription.Resource(
                                R.string.accessibility_status_bar_headphones
                            ),
                    )
                } else {
                    Icon.Resource(
                        resId = R.drawable.ic_headset,
                        contentDescription =
                            ContentDescription.Resource(R.string.accessibility_status_bar_headset),
                    )
                }
            }

    @AssistedFactory
    interface Factory {
        fun create(context: Context): HeadsetIconViewModel
    }
}
