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

package com.android.systemui.volume.panel.component.mediainput.data.repository

import android.content.Context
import android.media.AudioManager
import com.android.settingslib.media.InfoMediaManager
import com.android.settingslib.media.InputRouteManager
import com.android.settingslib.media.MediaDevice
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.kairos.awaitClose
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Stores data for the media input component. */
interface MediaInputComponentRepository {
    /** Flow that stores the current active input device. */
    val currentInputDevice: StateFlow<MediaDevice?>
}

@SysUISingleton
class MediaInputComponentRepositoryImpl
@Inject
constructor(
    context: Context,
    @Background private val coroutineScope: CoroutineScope,
    audioManager: AudioManager,
    infoMediaManager: InfoMediaManager,
) : MediaInputComponentRepository {
    private val inputRouteManager = InputRouteManager(context, audioManager, infoMediaManager)

    override val currentInputDevice: StateFlow<MediaDevice?> =
        conflatedCallbackFlow {
                val inputDeviceCallback =
                    InputRouteManager.InputDeviceCallback { devices ->
                        trySend(devices.firstOrNull { it?.isSelected == true })
                    }

                inputRouteManager.registerCallback(inputDeviceCallback)
                awaitClose { inputRouteManager.unregisterCallback(inputDeviceCallback) }
            }
            .stateIn(coroutineScope, SharingStarted.WhileSubscribed(), null)
}
