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

package com.android.systemui.kosmos.audio

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler

class FakeAudioManager : AudioManager() {
    private var devices: Array<AudioDeviceInfo> = emptyArray()
    private var callback: AudioDeviceCallback? = null

    fun setDevices(devices: Array<AudioDeviceInfo>) {
        this.devices = devices
        callback?.onAudioDevicesAdded(devices)
    }

    fun removeDevices(devices: Array<AudioDeviceInfo>) {
        this.devices = emptyArray()
        callback?.onAudioDevicesRemoved(devices)
    }

    override fun getDevices(flags: Int): Array<AudioDeviceInfo> {
        return devices
    }

    override fun registerAudioDeviceCallback(callback: AudioDeviceCallback, handler: Handler?) {
        this.callback = callback
    }

    override fun unregisterAudioDeviceCallback(callback: AudioDeviceCallback) {
        this.callback = null
    }
}
