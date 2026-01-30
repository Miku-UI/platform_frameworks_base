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

package com.android.systemui.statusbar.systemstatusicons.bluetooth.ui.viewmodel

import android.bluetooth.BluetoothProfile
import android.content.testableContext
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.settingslib.bluetooth.CachedBluetoothDevice
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.statusbar.policy.bluetooth.data.repository.bluetoothRepository
import com.android.systemui.statusbar.systemstatusicons.SystemStatusIconsInCompose
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(SystemStatusIconsInCompose.FLAG_NAME)
class BluetoothIconViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val cachedDevice =
        mock<CachedBluetoothDevice>().apply {
            whenever(isConnected).thenReturn(true)
            whenever(maxConnectionState).thenReturn(BluetoothProfile.STATE_CONNECTED)
        }
    private val underTest =
        kosmos.bluetoothIconViewModelFactory.create(kosmos.testableContext).apply {
            activateIn(kosmos.testScope)
        }

    @Test
    fun visible_isFalse_byDefault() = kosmos.runTest { assertThat(underTest.visible).isFalse() }

    @Test
    fun visible_deviceIsConnected_isTrue() =
        kosmos.runTest {
            kosmos.bluetoothRepository.setConnectedDevices(listOf(cachedDevice))

            assertThat(underTest.visible).isTrue()
        }

    @Test
    fun visible_connectionStateChanges_flips() =
        kosmos.runTest {
            assertThat(underTest.visible).isFalse()

            kosmos.bluetoothRepository.setConnectedDevices(listOf(cachedDevice))
            assertThat(underTest.visible).isTrue()

            kosmos.bluetoothRepository.setConnectedDevices(emptyList())
            assertThat(underTest.visible).isFalse()
        }

    @Test
    fun icon_visible_isCorrect() =
        kosmos.runTest {
            kosmos.bluetoothRepository.setConnectedDevices(listOf(cachedDevice))
            assertThat(underTest.icon).isEqualTo(expectedBluetoothIcon)
        }

    @Test fun icon_notVisible_isNull() = kosmos.runTest { assertThat(underTest.icon).isNull() }

    companion object {
        private val expectedBluetoothIcon =
            Icon.Resource(
                resId = R.drawable.ic_bluetooth_connected,
                contentDescription =
                    ContentDescription.Resource(R.string.accessibility_bluetooth_connected),
            )
    }
}
