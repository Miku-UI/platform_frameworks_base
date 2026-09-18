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

package com.android.systemui.statusbar.pipeline.audio.data.repository

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.kairos.awaitClose
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.statusbar.pipeline.dagger.WiredAudioDeviceRepositoryLog
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/** Serves as source of truth for wired audio device plugged into the device */
interface WiredAudioDeviceRepository {

    /**
     * A flow of a list of [AudioDeviceInfo] filtered for just wired audio devices emits null if no
     * wired audio device is plugged in.
     *
     * This flow updates when [AudioDeviceCallback] callbacks are triggered.
     */
    val wiredAudioDevice: StateFlow<List<AudioDeviceInfo>>
}

@SysUISingleton
class WiredAudioDeviceRepositoryImpl
@Inject
constructor(
    private val audioManager: AudioManager,
    @Background private val handler: Handler,
    @Background private val scope: CoroutineScope,
    @WiredAudioDeviceRepositoryLog logBuffer: LogBuffer,
) : WiredAudioDeviceRepository {

    private val logger = Logger(logBuffer, TAG)

    private suspend fun getAudioDevices(): List<AudioDeviceInfo> =
        withContext(scope.coroutineContext) {
            return@withContext audioManager.getDevices(AudioManager.GET_DEVICES_ALL).filter {
                when (it.type) {
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_LINE_ANALOG,
                    AudioDeviceInfo.TYPE_USB_DEVICE -> true

                    else -> false
                }
            }
        }

    override val wiredAudioDevice: StateFlow<List<AudioDeviceInfo>> =
        conflatedCallbackFlow {
                val callback =
                    object : AudioDeviceCallback() {
                        override fun onAudioDevicesAdded(devices: Array<out AudioDeviceInfo>?) {
                            logger.i({ "onAudioDevicesChanged: $str1" }) {
                                str1 = devices.toString()
                            }
                            trySend(Unit)
                        }

                        override fun onAudioDevicesRemoved(devices: Array<out AudioDeviceInfo>?) {
                            logger.i({ "onAudioDevicesRemoved: $str1" }) {
                                str1 = devices.toString()
                            }
                            trySend(Unit)
                        }
                    }

                audioManager.registerAudioDeviceCallback(callback, handler)

                awaitClose { audioManager.unregisterAudioDeviceCallback(callback) }
            }
            .map { getAudioDevices() }
            .stateIn(scope, SharingStarted.WhileSubscribed(), emptyList())

    companion object {
        private const val TAG = "WiredAudioDeviceRepository"
    }
}
