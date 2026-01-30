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

package com.android.settingslib.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothLeAudio
import android.bluetooth.BluetoothLeAudioCodecStatus
import com.android.internal.util.ConcurrentUtils
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

/** [Flow] for [BluetoothLeAudio.Callback] fallback group change events */
val LeAudioProfile.onBroadcastToUnicastFallbackGroupChanged: Flow<Int>
    get() =
        callbackFlow {
            val listener =
                object : BluetoothLeAudio.Callback {
                    override fun onCodecConfigChanged(p0: Int, p1: BluetoothLeAudioCodecStatus) {
                    }

                    override fun onGroupNodeAdded(p0: BluetoothDevice, p1: Int) {
                    }

                    override fun onGroupNodeRemoved(p0: BluetoothDevice, p1: Int) {
                    }

                    override fun onGroupStatusChanged(p0: Int, p1: Int) {
                    }

                    override fun onBroadcastToUnicastFallbackGroupChanged(arg1: Int) {
                        trySend(arg1)
                    }
                }
            registerCallback(
                ConcurrentUtils.DIRECT_EXECUTOR,
                listener,
            )
            awaitClose { unregisterCallback(listener) }
        }
            .buffer(capacity = Channel.CONFLATED)