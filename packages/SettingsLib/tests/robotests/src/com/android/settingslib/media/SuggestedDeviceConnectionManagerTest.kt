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

import android.content.Context
import android.media.MediaRoute2Info
import android.media.MediaRoute2Info.TYPE_REMOTE_SPEAKER
import android.media.RoutingChangeInfo
import android.media.SuggestedDeviceInfo
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_CONNECTED
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SuggestedDeviceConnectionManagerTest {
    private val callback = mock<ConnectionFinishedCallback>()
    private var localMediaManager: LocalMediaManager = mock<LocalMediaManager>()
    private val routeInfo1 =
        mock<MediaRoute2Info> {
            on { name } doReturn TEST_DEVICE_NAME_1
            on { id } doReturn TEST_DEVICE_ID_1
        }
    private val routeInfo2 =
        mock<MediaRoute2Info> {
            on { name } doReturn TEST_DEVICE_NAME_2
            on { id } doReturn TEST_DEVICE_ID_2
        }
    private val suggestedDeviceInfo1 =
        SuggestedDeviceInfo.Builder(TEST_DEVICE_NAME_1, TEST_DEVICE_ID_1, TYPE_REMOTE_SPEAKER)
            .build()
    private val suggestedDeviceInfo2 =
        SuggestedDeviceInfo.Builder(TEST_DEVICE_NAME_2, TEST_DEVICE_ID_2, TYPE_REMOTE_SPEAKER)
            .build()
    private lateinit var mediaDevice1: MediaDevice
    private lateinit var mediaDevice2: MediaDevice
    private lateinit var suggestedDeviceConnectionManager: SuggestedDeviceConnectionManager
    private val testScope = TestScope()
    private val deviceCallbacks = CopyOnWriteArrayList<LocalMediaManager.DeviceCallback>()

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()

        mediaDevice1 =
            InfoMediaDevice(
                context,
                routeInfo1,
                /* dynamicRouteAttributes= */ null,
                /* rlpItem= */ null,
            )

        mediaDevice2 =
            InfoMediaDevice(
                context,
                routeInfo2,
                /* dynamicRouteAttributes= */ null,
                /* rlpItem= */ null,
            )

        suggestedDeviceConnectionManager =
            SuggestedDeviceConnectionManager(localMediaManager, testScope)

        deviceCallbacks.clear()
        localMediaManager.stub {
            on { registerCallback(any()) } doAnswer { invocation ->
                val callback = invocation.getArgument<LocalMediaManager.DeviceCallback>(0)
                deviceCallbacks.add(callback)
                null
            }
            on { unregisterCallback(any()) } doAnswer { invocation ->
                val callback = invocation.getArgument<LocalMediaManager.DeviceCallback>(0)
                deviceCallbacks.remove(callback)
                null
            }
        }
    }

    @Test
    fun connect_deviceIsDiscovered_immediatelyConnects() = testScope.runTest {
        val suggestedDeviceState = SuggestedDeviceState(suggestedDeviceInfo1)
        val mediaDevices = listOf(mediaDevice1)
        localMediaManager.stub { on { getMediaDevices() } doReturn mediaDevices }
        suggestedDeviceConnectionManager.connect(
            suggestedDeviceState,
            ROUTING_CHANGE_INFO,
            callback,
        )
        runCurrent()

        // Scan should be started anyway to ensure the device is discoverable during connection.
        verify(localMediaManager).startScan()
        verify(localMediaManager).connectDevice(mediaDevice1, ROUTING_CHANGE_INFO)
        assertThat(deviceCallbacks).hasSize(1) // Callback for connection state change
    }

    @Test
    fun connect_deviceIsNotDiscovered_startsScan() = testScope.runTest {
        val suggestedDeviceState = SuggestedDeviceState(suggestedDeviceInfo2)
        val mediaDevices = listOf(mediaDevice1)
        localMediaManager.stub { on { getMediaDevices() } doReturn mediaDevices }
        suggestedDeviceConnectionManager.connect(
            suggestedDeviceState,
            ROUTING_CHANGE_INFO,
            callback,
        )
        runCurrent()

        verify(localMediaManager).startScan()
        assertThat(deviceCallbacks).hasSize(1) // Callback for scan update
        verify(localMediaManager, never()).connectDevice(mediaDevice1, ROUTING_CHANGE_INFO)
    }

    @Test
    fun connect_scanSucceeds_connects() = testScope.runTest {
        val suggestedDeviceState = SuggestedDeviceState(suggestedDeviceInfo1)
        val mediaDevices = mutableListOf(mediaDevice2)
        localMediaManager.stub { on { getMediaDevices() } doReturn mediaDevices }
        suggestedDeviceConnectionManager.connect(
            suggestedDeviceState,
            ROUTING_CHANGE_INFO,
            callback
        )
        runCurrent()

        verify(localMediaManager).startScan()

        // Device found 5 seconds before timeout
        advanceTimeBy(SCAN_TIMEOUT - 5.seconds)
        mediaDevices.add(mediaDevice1)
        emulateOnDeviceListUpdate(mediaDevices)
        runCurrent()

        verify(localMediaManager).connectDevice(mediaDevice1, ROUTING_CHANGE_INFO)
        assertThat(deviceCallbacks).hasSize(1) // Callback for connection state change
    }

    @Test
    fun connect_scanTimesOut_returnsFailure() = testScope.runTest {
        val suggestedDeviceState = SuggestedDeviceState(suggestedDeviceInfo1)
        val mediaDevices = mutableListOf(mediaDevice2)
        localMediaManager.stub { on { getMediaDevices() } doReturn mediaDevices }
        suggestedDeviceConnectionManager.connect(
            suggestedDeviceState,
            ROUTING_CHANGE_INFO,
            callback,
        )

        advanceTimeBy(SCAN_TIMEOUT - 1.seconds)
        verify(localMediaManager).startScan()
        verify(callback, never()).invoke(any(), any())

        advanceTimeBy(2.seconds)

        verify(localMediaManager).stopScan()
        verify(callback).invoke(suggestedDeviceState, false)
        assertThat(deviceCallbacks).hasSize(0)
    }


    @Test
    fun connect_deviceIsDiscoveredConnectionTimesOut_returnsFailure() = testScope.runTest {
        val suggestedDeviceState = SuggestedDeviceState(suggestedDeviceInfo1)
        val mediaDevices = listOf(mediaDevice1)
        localMediaManager.stub { on { getMediaDevices() } doReturn mediaDevices }
        suggestedDeviceConnectionManager.connect(
            suggestedDeviceState,
            ROUTING_CHANGE_INFO,
            callback
        )
        runCurrent()

        verify(localMediaManager).connectDevice(mediaDevice1, ROUTING_CHANGE_INFO)

        advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

        verify(callback, never()).invoke(any(), any())

        advanceTimeBy(2.seconds)

        verify(callback).invoke(suggestedDeviceState, false)
        assertThat(deviceCallbacks).hasSize(0)
    }

    @Test
    fun connect_scanThenConnectionSuccess_callbackReturnsTrue() = testScope.runTest {
        val suggestedDeviceState = SuggestedDeviceState(suggestedDeviceInfo1)
        val mediaDevices = mutableListOf(mediaDevice2)
        localMediaManager.stub { on { getMediaDevices() } doReturn mediaDevices }
        suggestedDeviceConnectionManager.connect(
            suggestedDeviceState,
            ROUTING_CHANGE_INFO,
            callback,
        )
        runCurrent()
        mediaDevices.add(mediaDevice1)
        emulateOnDeviceListUpdate(mediaDevices)
        runCurrent()

        verify(localMediaManager).connectDevice(mediaDevice1, ROUTING_CHANGE_INFO)
        verify(localMediaManager, never()).stopScan() // don't stop scan until connected.
        localMediaManager.stub { on { currentConnectedDevice } doReturn mediaDevice1 }
        emulateOnSelectedDeviceStateChanged(mediaDevice1, STATE_CONNECTED)
        runCurrent()

        verify(callback).invoke(suggestedDeviceState, true)
        verify(localMediaManager).stopScan()
        assertThat(deviceCallbacks).hasSize(0)
    }

    @Test
    fun connect_cancelledDuringConnectingDevice_clearsCallbacks() = testScope.runTest {
        val suggestedDeviceState = SuggestedDeviceState(suggestedDeviceInfo1)
        val mediaDevices = mutableListOf(mediaDevice1)
        localMediaManager.stub { on { getMediaDevices() } doReturn mediaDevices }
        suggestedDeviceConnectionManager.connect(
            suggestedDeviceState,
            ROUTING_CHANGE_INFO,
            callback,
        )
        runCurrent()

        verify(localMediaManager).connectDevice(mediaDevice1, ROUTING_CHANGE_INFO)

        advanceTimeBy(CONNECTION_TIMEOUT - 5.seconds)

        assertThat(deviceCallbacks).hasSize(1)

        suggestedDeviceConnectionManager.cancel()
        runCurrent()

        verify(callback, never()).invoke(any(), any())
        assertThat(deviceCallbacks).hasSize(0)
    }

    @Test
    fun connect_cancelledDuringScan_clearsCallbacks() = testScope.runTest {
        val suggestedDeviceState = SuggestedDeviceState(suggestedDeviceInfo1)
        val mediaDevices = mutableListOf(mediaDevice2)
        localMediaManager.stub { on { getMediaDevices() } doReturn mediaDevices }
        suggestedDeviceConnectionManager.connect(
            suggestedDeviceState,
            ROUTING_CHANGE_INFO,
            callback,
        )
        runCurrent()

        verify(localMediaManager).startScan()

        advanceTimeBy(SCAN_TIMEOUT - 5.seconds)

        assertThat(deviceCallbacks).hasSize(1)

        suggestedDeviceConnectionManager.cancel()
        runCurrent()

        verify(callback, never()).invoke(any(), any())
        verify(localMediaManager).stopScan()
        assertThat(deviceCallbacks).hasSize(0)
    }

    @Test
    fun connect_whileConnectionInProgress_throwsException() = testScope.runTest {
        val suggestedDeviceState = SuggestedDeviceState(suggestedDeviceInfo1)
        val mediaDevices = mutableListOf(mediaDevice1)
        localMediaManager.stub { on { getMediaDevices() } doReturn mediaDevices }
        suggestedDeviceConnectionManager.connect(
            suggestedDeviceState,
            ROUTING_CHANGE_INFO,
            callback,
        )
        runCurrent()

        assertThrows(IllegalStateException::class.java) {
            suggestedDeviceConnectionManager.connect(
                suggestedDeviceState,
                ROUTING_CHANGE_INFO,
                callback,
            )
        }
    }

    @Test
    fun connect_afterCancel_proceedsNormally() = testScope.runTest {
        val suggestedDeviceState = SuggestedDeviceState(suggestedDeviceInfo1)
        val mediaDevices = mutableListOf(mediaDevice1)
        localMediaManager.stub { on { getMediaDevices() } doReturn mediaDevices }
        suggestedDeviceConnectionManager.connect(
            suggestedDeviceState,
            ROUTING_CHANGE_INFO,
            callback,
        )
        runCurrent()

        assertThat(deviceCallbacks).hasSize(1)

        suggestedDeviceConnectionManager.cancel()
        runCurrent()

        assertThat(deviceCallbacks).hasSize(0)

        suggestedDeviceConnectionManager.connect(
            suggestedDeviceState,
            ROUTING_CHANGE_INFO,
            callback,
        )
        runCurrent()

        assertThat(deviceCallbacks).hasSize(1)
    }

    private fun emulateOnDeviceListUpdate(mediaDevices: List<MediaDevice>) {
        deviceCallbacks.forEach { it.onDeviceListUpdate(mediaDevices) }
    }

    private fun emulateOnSelectedDeviceStateChanged(mediaDevice: MediaDevice, connectionState: Int) {
        deviceCallbacks.forEach { it.onSelectedDeviceStateChanged(mediaDevice, connectionState) }
    }

    companion object {
        private const val TEST_DEVICE_NAME_1 = "device_name_1"
        private const val TEST_DEVICE_NAME_2 = "device_name_2"
        private const val TEST_DEVICE_ID_1 = "device_id_1"
        private const val TEST_DEVICE_ID_2 = "device_id_2"
        private val SCAN_TIMEOUT = 10.seconds
        private val CONNECTION_TIMEOUT = 20.seconds
        private val ROUTING_CHANGE_INFO =
            RoutingChangeInfo(
                RoutingChangeInfo.ENTRY_POINT_SYSTEM_MEDIA_CONTROLS,
                /* isSuggested= */ true,
            )
    }
}
