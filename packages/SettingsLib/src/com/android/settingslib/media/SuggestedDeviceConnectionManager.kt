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
package com.android.settingslib.media

import android.media.RoutingChangeInfo
import android.util.Log
import androidx.annotation.OpenForTesting
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

typealias ConnectionFinishedCallback = (SuggestedDeviceState, Boolean) -> Unit

/**
 * Controls the connection for a suggested device pill in Media Controls. Responsible to start the
 * route scan if the suggested device is not discovered yet.
 */
@OpenForTesting
open class SuggestedDeviceConnectionManager(
    private val localMediaManager: LocalMediaManager,
    private val coroutineScope: CoroutineScope,
) {
    private val isConnectInProgress = AtomicBoolean(false)
    private var activeJob: Job? = null

    /**
     * Connects to a suggested device. If the device is not already scanned, a scan will be started
     * to attempt to discover the device.
     *
     * @param suggestedDeviceState the suggested device to connect to.
     * @param routingChangeInfo the invocation details of the connect device request. See [ ]
     * @param callback the callback to be invoked when the connection attempt is complete.
     */
    @OpenForTesting
    @Throws(IllegalStateException::class)
    open fun connect(
        suggestedDeviceState: SuggestedDeviceState,
        routingChangeInfo: RoutingChangeInfo,
        callback: ConnectionFinishedCallback,
    ) {
        if (isConnectInProgress.compareAndSet(false, true)) {
            activeJob =
                coroutineScope.launch {
                    try {
                        val result = awaitConnect(suggestedDeviceState, routingChangeInfo)
                        callback(suggestedDeviceState, result)
                    } finally {
                        isConnectInProgress.set(false)
                    }
                }
        } else {
            throw IllegalStateException("Connection already in progress")
        }
    }

    @OpenForTesting
    open fun cancel() {
        activeJob?.cancel()
    }

    private suspend fun awaitConnect(
        suggestedDeviceState: SuggestedDeviceState,
        routingChangeInfo: RoutingChangeInfo,
    ): Boolean = coroutineScope {
        var scanStarted = false
        try {
            val suggestedRouteId = suggestedDeviceState.suggestedDeviceInfo.routeId
            // Start listening before starting scan to avoid missing events.
            val deviceDiscoveryResult = async { awaitForDevice(suggestedRouteId) }
            localMediaManager.startScan()
            scanStarted = true
            val deviceToConnect = deviceDiscoveryResult.await()

            if (deviceToConnect == null) {
                Log.w(TAG, "Failed to find a device to connect to. routeId = $suggestedRouteId")
                return@coroutineScope false
            }
            Log.i(TAG, "Connecting to device. id = ${deviceToConnect.id}")
            return@coroutineScope awaitConnectToDevice(deviceToConnect, routingChangeInfo)
        } finally {
            if (scanStarted) localMediaManager.stopScan()
        }
    }

    private suspend fun awaitForDevice(suggestedRouteId: String): MediaDevice? {
        val deviceFromCache =
            getDeviceByRouteId(localMediaManager.mediaDevices, suggestedRouteId)
        deviceFromCache?.let {
            Log.i(TAG, "Device from cache found.")
            return it
        }

        Log.i(TAG, "Scanning for device.")
        var callback: LocalMediaManager.DeviceCallback? = null
        try {
            return withTimeoutOrNull(SCAN_TIMEOUT) {
                suspendCancellableCoroutine { continuation ->
                    callback = object : LocalMediaManager.DeviceCallback {
                        override fun onDeviceListUpdate(newDevices: List<MediaDevice>?) {
                            val device = getDeviceByRouteId(newDevices, suggestedRouteId)
                            if (device != null) {
                                Log.i(
                                    TAG,
                                    "Scan found matched device. routeId = $suggestedRouteId",
                                )
                                if (continuation.isActive) continuation.resume(device)
                            }
                        }
                    }
                    localMediaManager.registerCallback(callback)
                }
            } ?: run {
                Log.w(TAG, "Scan timed out. routeId = $suggestedRouteId")
                null
            }
        } finally {
            localMediaManager.unregisterCallback(callback)
        }
    }

    private suspend fun awaitConnectToDevice(
        deviceToConnect: MediaDevice,
        routingChangeInfo: RoutingChangeInfo,
    ): Boolean {
        var callback: LocalMediaManager.DeviceCallback? = null
        val deviceId = deviceToConnect.id
        try {
            return withTimeoutOrNull(CONNECTION_TIMEOUT) {
                suspendCancellableCoroutine { continuation: CancellableContinuation<Boolean> ->
                    callback = object : LocalMediaManager.DeviceCallback {
                        override fun onDeviceListUpdate(newDevices: List<MediaDevice>?) =
                            checkConnectionStatus()

                        override fun onSelectedDeviceStateChanged(
                            device: MediaDevice,
                            @MediaDeviceState state: Int,
                        ) = checkConnectionStatus()

                        private fun checkConnectionStatus() {
                            if (localMediaManager.currentConnectedDevice?.id == deviceId) {
                                Log.i(TAG, "Successfully connected to device. id = $deviceId")
                                if (continuation.isActive) continuation.resume(true)
                            }
                        }
                    }
                    localMediaManager.registerCallback(callback)
                    localMediaManager.connectDevice(deviceToConnect, routingChangeInfo)
                }
            } ?: run {
                Log.w(TAG, "Connection timed out. id = $deviceId")
                false
            }
        } finally {
            localMediaManager.unregisterCallback(callback)
        }
    }

    private fun getDeviceByRouteId(
        mediaDevices: List<MediaDevice>?,
        routeId: String,
    ): MediaDevice? {
        return mediaDevices?.find { it.routeInfo?.id == routeId }
    }

    companion object {
        private const val TAG = "SuggestedDeviceConnectionManager"
        private val SCAN_TIMEOUT = 10.seconds
        private val CONNECTION_TIMEOUT = 20.seconds
    }
}
