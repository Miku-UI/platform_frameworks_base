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

package com.android.systemui.statusbar.pipeline.audio.domain.interactor

import android.media.AudioDeviceInfo
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.pipeline.audio.data.repository.WiredAudioDeviceRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.map

@SysUISingleton
class WiredAudioDeviceInteractor @Inject constructor(repo: WiredAudioDeviceRepository) {

    private fun List<AudioDeviceInfo>.getWiredHeadsetDevice(): WiredAudioDevice? {

        // Prioritize headset first
        val headset = firstNotNullOfOrNull { device ->
            when (device.type) {
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_USB_HEADSET -> WiredAudioDevice(hasMic = true)
                AudioDeviceInfo.TYPE_USB_DEVICE ->
                    if (device.isSource) WiredAudioDevice(hasMic = true) else null
                else -> null
            }
        }

        // If no headset, check for headphones
        return headset
            ?: firstNotNullOfOrNull { device ->
                when (device.type) {
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_LINE_ANALOG,
                    AudioDeviceInfo.TYPE_USB_DEVICE -> WiredAudioDevice(hasMic = false)
                    else -> null
                }
            }
    }

    /** The current [WiredAudioDevice], will be null if there is none */
    val wiredAudioDevice = repo.wiredAudioDevice.map { it.getWiredHeadsetDevice() }
}

data class WiredAudioDevice(val hasMic: Boolean)
