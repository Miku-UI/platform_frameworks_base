/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.statusbar.policy.bluetooth

import android.bluetooth.BluetoothProfile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.settingslib.bluetooth.BluetoothCallback
import com.android.settingslib.bluetooth.BluetoothEventManager
import com.android.settingslib.bluetooth.CachedBluetoothDevice
import com.android.settingslib.bluetooth.LocalBluetoothAdapter
import com.android.systemui.SysuiTestCase
import com.android.systemui.bluetooth.cachedBluetoothDeviceManager
import com.android.systemui.bluetooth.localBluetoothManager
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.statusbar.policy.bluetooth.data.repository.BluetoothRepositoryImpl
import com.android.systemui.statusbar.policy.bluetooth.data.repository.ConnectionStatusModel
import com.android.systemui.statusbar.policy.bluetooth.data.repository.realBluetoothRepository
import com.android.systemui.testKosmos
import com.android.systemui.util.mockito.mock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class BluetoothRepositoryImplTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val underTest: BluetoothRepositoryImpl =
        kosmos.realBluetoothRepository as BluetoothRepositoryImpl

    private val bluetoothManager = kosmos.localBluetoothManager!!
    @Mock private lateinit var eventManager: BluetoothEventManager
    @Mock private lateinit var cachedDevice: CachedBluetoothDevice
    @Mock private lateinit var cachedDevice2: CachedBluetoothDevice
    @Captor private lateinit var callbackCaptor: ArgumentCaptor<BluetoothCallback>

    private lateinit var scheduler: TestCoroutineScheduler
    private lateinit var dispatcher: TestDispatcher
    private lateinit var testScope: TestScope

    @Mock private lateinit var bluetoothAdapter: LocalBluetoothAdapter

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        scheduler = TestCoroutineScheduler()
        dispatcher = StandardTestDispatcher(scheduler)
        testScope = TestScope(dispatcher)

        whenever(bluetoothManager.eventManager).thenReturn(eventManager)
        whenever(bluetoothManager.bluetoothAdapter).thenReturn(bluetoothAdapter)
    }

    @Test
    fun connectedDevices_initialStateWithNoDevices_isEmpty() =
        kosmos.runTest {
            val connectedDevices by collectLastValue(underTest.connectedDevices)

            bluetoothManager.eventManager.let {
                verify(it).registerCallback(callbackCaptor.capture())
            }

            assertThat(connectedDevices).isEmpty()
        }

    @Test
    fun connectedDevices_whenDeviceConnects_emitsDevice() =
        kosmos.runTest {
            val connectedDevices by collectLastValue(underTest.connectedDevices)
            bluetoothManager.eventManager.let {
                verify(it).registerCallback(callbackCaptor.capture())
            }

            val callback = callbackCaptor.value
            assertThat(connectedDevices).isEmpty()

            // Simulate device connecting
            whenever(cachedDevice.isConnected).thenReturn(true)
            whenever(cachedDevice.maxConnectionState).thenReturn(BluetoothProfile.STATE_CONNECTED)
            whenever(cachedBluetoothDeviceManager.cachedDevicesCopy)
                .thenReturn(listOf(cachedDevice))

            // Trigger a callback
            callback.onConnectionStateChanged(cachedDevice, BluetoothProfile.STATE_CONNECTED)
            assertThat(connectedDevices).isEqualTo(listOf(cachedDevice))
        }

    @Test
    fun connectedDevices_whenDeviceDisconnects_isEmpty() =
        kosmos.runTest {
            val connectedDevices by collectLastValue(underTest.connectedDevices)
            bluetoothManager.eventManager.let {
                verify(it).registerCallback(callbackCaptor.capture())
            }
            val callback = callbackCaptor.value

            // Start with a connected device
            whenever(cachedDevice.isConnected).thenReturn(true)
            whenever(cachedDevice.maxConnectionState).thenReturn(BluetoothProfile.STATE_CONNECTED)
            whenever(cachedBluetoothDeviceManager.cachedDevicesCopy)
                .thenReturn(listOf(cachedDevice))
            callback.onConnectionStateChanged(cachedDevice, BluetoothProfile.STATE_CONNECTED)
            assertThat(connectedDevices).isNotEmpty()

            // Simulate device disconnecting
            whenever(cachedDevice.isConnected).thenReturn(false)
            whenever(cachedDevice.maxConnectionState)
                .thenReturn(BluetoothProfile.STATE_DISCONNECTED)
            whenever(cachedBluetoothDeviceManager.cachedDevicesCopy).thenReturn(emptyList())

            // Trigger a callback reflecting the disconnection
            callback.onConnectionStateChanged(cachedDevice, BluetoothProfile.STATE_DISCONNECTED)

            assertThat(connectedDevices).isEmpty()
        }

    @Test
    fun connectedDevices_whenMultipleDevicesConnects_emitsAllDevices() =
        kosmos.runTest {
            val connectedDevices by collectLastValue(underTest.connectedDevices)
            bluetoothManager.eventManager.let {
                verify(it).registerCallback(callbackCaptor.capture())
            }

            val callback = callbackCaptor.value
            assertThat(connectedDevices).isEmpty()

            whenever(cachedDevice.isConnected).thenReturn(true)
            whenever(cachedDevice.maxConnectionState).thenReturn(BluetoothProfile.STATE_CONNECTED)
            whenever(cachedDevice2.isConnected).thenReturn(true)
            whenever(cachedDevice2.maxConnectionState).thenReturn(BluetoothProfile.STATE_CONNECTED)

            whenever(cachedBluetoothDeviceManager.cachedDevicesCopy)
                .thenReturn(listOf(cachedDevice, cachedDevice2))

            callback.onConnectionStateChanged(cachedDevice, BluetoothProfile.STATE_CONNECTED)

            assertThat(connectedDevices).isEqualTo(listOf(cachedDevice, cachedDevice2))
        }

    @Test
    fun fetchConnectionStatusInBackground_currentDevicesEmpty_maxStateIsManagerState() =
        kosmos.runTest {
            whenever(bluetoothAdapter.connectionState).thenReturn(BluetoothProfile.STATE_CONNECTING)

            val status = fetchConnectionStatus(currentDevices = emptyList())

            assertThat(status.maxConnectionState).isEqualTo(BluetoothProfile.STATE_CONNECTING)
        }

    @Test
    fun fetchConnectionStatusInBackground_currentDevicesEmpty_nullManager_maxStateIsDisconnected() =
        kosmos.runTest {
            // This CONNECTING state should be unused because localBluetoothManager is null
            whenever(bluetoothAdapter.connectionState).thenReturn(BluetoothProfile.STATE_CONNECTING)
            val repository =
                BluetoothRepositoryImpl(
                    testScope.backgroundScope,
                    dispatcher,
                    localBluetoothManager = null,
                )

            val status =
                fetchConnectionStatus(repository = repository, currentDevices = emptyList())

            assertThat(status.maxConnectionState).isEqualTo(BluetoothProfile.STATE_DISCONNECTED)
        }

    @Test
    fun fetchConnectionStatusInBackground_managerStateLargerThanDeviceStates_maxStateIsManager() =
        kosmos.runTest {
            whenever(bluetoothAdapter.connectionState).thenReturn(BluetoothProfile.STATE_CONNECTING)
            val device1 =
                mock<CachedBluetoothDevice>().also {
                    whenever(it.maxConnectionState).thenReturn(BluetoothProfile.STATE_DISCONNECTED)
                }
            val cachedDevice2 =
                mock<CachedBluetoothDevice>().also {
                    whenever(it.maxConnectionState).thenReturn(BluetoothProfile.STATE_DISCONNECTED)
                }

            val status = fetchConnectionStatus(currentDevices = listOf(device1, cachedDevice2))

            assertThat(status.maxConnectionState).isEqualTo(BluetoothProfile.STATE_CONNECTING)
        }

    @Test
    fun fetchConnectionStatusInBackground_oneCurrentDevice_maxStateIsDeviceState() =
        kosmos.runTest {
            whenever(bluetoothAdapter.connectionState)
                .thenReturn(BluetoothProfile.STATE_DISCONNECTED)
            val device =
                mock<CachedBluetoothDevice>().also {
                    whenever(it.maxConnectionState).thenReturn(BluetoothProfile.STATE_CONNECTING)
                }

            val status = fetchConnectionStatus(currentDevices = listOf(device))

            assertThat(status.maxConnectionState).isEqualTo(BluetoothProfile.STATE_CONNECTING)
        }

    @Test
    fun fetchConnectionStatusInBackground_multipleDevices_maxStateIsHighestState() =
        kosmos.runTest {
            whenever(bluetoothAdapter.connectionState)
                .thenReturn(BluetoothProfile.STATE_DISCONNECTED)

            val device1 =
                mock<CachedBluetoothDevice>().also {
                    whenever(it.maxConnectionState).thenReturn(BluetoothProfile.STATE_CONNECTING)
                    whenever(it.isConnected).thenReturn(false)
                }
            val cachedDevice2 =
                mock<CachedBluetoothDevice>().also {
                    whenever(it.maxConnectionState).thenReturn(BluetoothProfile.STATE_CONNECTED)
                    whenever(it.isConnected).thenReturn(true)
                }

            val status = fetchConnectionStatus(currentDevices = listOf(device1, cachedDevice2))

            assertThat(status.maxConnectionState).isEqualTo(BluetoothProfile.STATE_CONNECTED)
        }

    @Test
    fun fetchConnectionStatusInBackground_devicesNotConnected_maxStateIsDisconnected() =
        kosmos.runTest {
            whenever(bluetoothAdapter.connectionState).thenReturn(BluetoothProfile.STATE_CONNECTING)

            // WHEN the devices say their state is CONNECTED but [isConnected] is false
            val device1 =
                mock<CachedBluetoothDevice>().also {
                    whenever(it.maxConnectionState).thenReturn(BluetoothProfile.STATE_CONNECTED)
                    whenever(it.isConnected).thenReturn(false)
                }
            val cachedDevice2 =
                mock<CachedBluetoothDevice>().also {
                    whenever(it.maxConnectionState).thenReturn(BluetoothProfile.STATE_CONNECTED)
                    whenever(it.isConnected).thenReturn(false)
                }

            val status = fetchConnectionStatus(currentDevices = listOf(device1, cachedDevice2))

            // THEN the max state is DISCONNECTED
            assertThat(status.maxConnectionState).isEqualTo(BluetoothProfile.STATE_DISCONNECTED)
        }

    @Test
    fun fetchConnectionStatusInBackground_currentDevicesEmpty_connectedDevicesEmpty() =
        kosmos.runTest {
            val status = fetchConnectionStatus(currentDevices = emptyList())

            assertThat(status.connectedDevices).isEmpty()
        }

    @Test
    fun fetchConnectionStatusInBackground_oneCurrentDeviceDisconnected_connectedDevicesEmpty() =
        kosmos.runTest {
            val device =
                mock<CachedBluetoothDevice>().also { whenever(it.isConnected).thenReturn(false) }

            val status = fetchConnectionStatus(currentDevices = listOf(device))

            assertThat(status.connectedDevices).isEmpty()
        }

    @Test
    fun fetchConnectionStatusInBackground_oneCurrentDeviceConnected_connectedDevicesHasDevice() =
        kosmos.runTest {
            val device =
                mock<CachedBluetoothDevice>().also { whenever(it.isConnected).thenReturn(true) }

            val status = fetchConnectionStatus(currentDevices = listOf(device))

            assertThat(status.connectedDevices).isEqualTo(listOf(device))
        }

    @Test
    fun fetchConnectionStatusInBackground_multipleDevices_connectedDevicesHasOnlyConnected() =
        kosmos.runTest {
            val device1Connected =
                mock<CachedBluetoothDevice>().also { whenever(it.isConnected).thenReturn(true) }
            val device2Disconnected =
                mock<CachedBluetoothDevice>().also { whenever(it.isConnected).thenReturn(false) }
            val device3Connected =
                mock<CachedBluetoothDevice>().also { whenever(it.isConnected).thenReturn(true) }

            val status =
                fetchConnectionStatus(
                    currentDevices = listOf(device1Connected, device2Disconnected, device3Connected)
                )

            assertThat(status.connectedDevices)
                .isEqualTo(listOf(device1Connected, device3Connected))
        }

    private fun fetchConnectionStatus(
        repository: BluetoothRepositoryImpl = underTest,
        currentDevices: Collection<CachedBluetoothDevice>,
    ): ConnectionStatusModel {
        var receivedStatus: ConnectionStatusModel? = null
        repository.fetchConnectionStatusInBackground(currentDevices) { status ->
            receivedStatus = status
        }
        scheduler.runCurrent()
        return receivedStatus!!
    }
}
