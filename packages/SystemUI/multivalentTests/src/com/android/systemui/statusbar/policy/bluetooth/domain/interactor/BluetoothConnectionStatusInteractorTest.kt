/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.policy.bluetooth.domain.interactor

import android.bluetooth.BluetoothProfile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.settingslib.bluetooth.CachedBluetoothDevice
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.statusbar.policy.bluetooth.data.repository.bluetoothRepository
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@ExperimentalCoroutinesApi
class BluetoothConnectionStatusInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val underTest = kosmos.bluetoothConnectionStatusInteractor

    @Test
    fun isBluetoothConnected_initialStateWithNoDevices_isFalse() =
        kosmos.runTest {
            val isConnected by collectLastValue(underTest.isBluetoothConnected)

            assertThat(isConnected).isFalse()
        }

    @Test
    fun isBluetoothConnected_whenDeviceConnects_isTrue() =
        kosmos.runTest {
            val isConnected by collectLastValue(underTest.isBluetoothConnected)

            // Simulate device connecting
            bluetoothRepository.setConnectedDevices(listOf(cachedDevice))

            assertThat(isConnected).isTrue()
        }

    @Test
    fun isBluetoothConnected_whenDeviceDisconnects_isFalse() =
        kosmos.runTest {
            val isConnected by collectLastValue(underTest.isBluetoothConnected)

            bluetoothRepository.setConnectedDevices(listOf(cachedDevice))
            assertThat(isConnected).isTrue()

            // Simulate device disconnecting
            bluetoothRepository.setConnectedDevices(emptyList())
            assertThat(isConnected).isFalse()
        }

    companion object {
        val cachedDevice =
            mock<CachedBluetoothDevice>().apply {
                whenever(this.isConnected).thenReturn(true)
                whenever(this.maxConnectionState).thenReturn(BluetoothProfile.STATE_CONNECTED)
            }
    }
}
