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

package com.android.systemui.accessibility.hearingaid

import android.app.Notification
import android.app.notificationManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.settingslib.bluetooth.BatteryLevelsInfo
import com.android.settingslib.bluetooth.CachedBluetoothDevice
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.concurrency.fakeExecutor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.res.R
import com.android.systemui.statusbar.policy.bluetooth.data.repository.bluetoothRepository
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@SmallTest
class HearingDeviceNotificationTest : SysuiTestCase() {

    private val kosmos = testKosmosNew()
    private val notificationManager by lazy { kosmos.notificationManager }
    private val dismissController by lazy { kosmos.hearingDeviceNotificationDismissController }
    private val bluetoothRepository by lazy { kosmos.bluetoothRepository }
    private val underTest by lazy { kosmos.hearingDeviceNotification }

    private val cachedDevice = mock<CachedBluetoothDevice>()
    private val device = mock<BluetoothDevice>()

    @Before
    fun setUp() {
        cachedDevice.stub {
            on { device } doReturn device
            on { address } doReturn TEST_DEVICE_ADDRESS
            on { name } doReturn TEST_DEVICE_NAME
            on { isConnected } doReturn true
            on { isHearingDevice } doReturn true
            on { batteryLevel } doReturn 50
        }
        bluetoothRepository.setConnectedDevices(listOf(cachedDevice))
        dismissController.stub {
            on { updateAndCheckNotification(anyString(), anyInt()) } doReturn true
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICE_STATUS_NOTIFICATION)
    fun updateNotification_fastPairDevice_cancelNotification() =
        kosmos.runTest {
            device.stub {
                on { getMetadata(BluetoothDevice.METADATA_IS_UNTETHERED_HEADSET) } doReturn
                    "true".toByteArray()
            }

            underTest.start()
            runAllReady()

            verify(notificationManager).cancel(anyString(), anyInt())
        }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICE_STATUS_NOTIFICATION)
    fun updateNotification_batteryInfoAvailable_showLeftRightBattery() =
        kosmos.runTest {
            val batteryInfo = BatteryLevelsInfo(50, 60, -1, -1)
            cachedDevice.stub { on { batteryLevelsInfo } doReturn batteryInfo }

            underTest.start()
            runAllReady()

            val notificationCaptor = argumentCaptor<Notification>()
            verify(notificationManager).notify(anyString(), anyInt(), notificationCaptor.capture())
            val notification = notificationCaptor.firstValue
            val expectedMessage =
                context.getString(
                    R.string.accessibility_hearing_device_left_right_battery_level,
                    "50%",
                    "60%",
                )
            assertThat(notification.extras.getString(Notification.EXTRA_TEXT))
                .isEqualTo(expectedMessage)
        }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICE_STATUS_NOTIFICATION)
    fun updateNotification_leftBatteryInfoAvailable_showLeftBattery() =
        kosmos.runTest {
            val batteryInfo = BatteryLevelsInfo(50, BluetoothDevice.BATTERY_LEVEL_UNKNOWN, -1, -1)
            cachedDevice.stub { on { batteryLevelsInfo } doReturn batteryInfo }

            underTest.start()
            runAllReady()

            val notificationCaptor = argumentCaptor<Notification>()
            verify(notificationManager).notify(anyString(), anyInt(), notificationCaptor.capture())
            val notification = notificationCaptor.firstValue
            val expectedMessage =
                context.getString(R.string.accessibility_hearing_device_left_battery_level, "50%")
            assertThat(notification.extras.getString(Notification.EXTRA_TEXT))
                .isEqualTo(expectedMessage)
        }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICE_STATUS_NOTIFICATION)
    fun updateNotification_rightBatteryInfoAvailable_showRightBattery() =
        kosmos.runTest {
            val batteryInfo = BatteryLevelsInfo(BluetoothDevice.BATTERY_LEVEL_UNKNOWN, 60, -1, -1)
            cachedDevice.stub { on { batteryLevelsInfo } doReturn batteryInfo }

            underTest.start()
            runAllReady()

            val notificationCaptor = argumentCaptor<Notification>()
            verify(notificationManager).notify(anyString(), anyInt(), notificationCaptor.capture())
            val notification = notificationCaptor.firstValue
            val expectedMessage =
                context.getString(R.string.accessibility_hearing_device_right_battery_level, "60%")
            assertThat(notification.extras.getString(Notification.EXTRA_TEXT))
                .isEqualTo(expectedMessage)
        }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICE_STATUS_NOTIFICATION)
    fun updateNotification_shouldNotShowNotification_noNotification() =
        kosmos.runTest {
            dismissController.stub {
                on { updateAndCheckNotification(anyString(), anyInt()) } doReturn false
            }

            underTest.start()
            runAllReady()

            verify(notificationManager, never()).notify(anyString(), anyInt(), any())
        }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICE_STATUS_NOTIFICATION)
    fun updateNotification_deviceActive_showActiveSubtext() =
        kosmos.runTest {
            cachedDevice.stub {
                on { isActiveDevice(BluetoothProfile.HEARING_AID) } doReturn true
                on { isActiveDevice(BluetoothProfile.LE_AUDIO) } doReturn false
            }

            underTest.start()
            runAllReady()

            val notificationCaptor = argumentCaptor<Notification>()
            verify(notificationManager).notify(anyString(), anyInt(), notificationCaptor.capture())
            val notification = notificationCaptor.firstValue
            assertThat(notification.extras.getString(Notification.EXTRA_SUB_TEXT))
                .isEqualTo(context.getString(R.string.quick_settings_hearing_devices_connected))
        }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICE_STATUS_NOTIFICATION)
    fun updateNotification_deviceConnectedNotActive_showConnectedSubtext() =
        kosmos.runTest {
            cachedDevice.stub {
                on { isActiveDevice(BluetoothProfile.HEARING_AID) } doReturn false
                on { isActiveDevice(BluetoothProfile.LE_AUDIO) } doReturn false
            }

            underTest.start()
            runAllReady()

            val notificationCaptor = argumentCaptor<Notification>()
            verify(notificationManager).notify(anyString(), anyInt(), notificationCaptor.capture())
            val notification = notificationCaptor.firstValue
            assertThat(notification.extras.getString(Notification.EXTRA_SUB_TEXT))
                .isEqualTo(context.getString(R.string.quick_settings_bluetooth_device_connected))
        }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICE_STATUS_NOTIFICATION)
    fun checkConnectedDevices_isActive_showActiveHearingDeviceStatus() =
        kosmos.runTest {
            val activeDevice =
                cachedDevice.stub {
                    on { isActiveDevice(BluetoothProfile.HEARING_AID) } doReturn true
                }
            val otherDevice = mock<CachedBluetoothDevice>()
            bluetoothRepository.setConnectedDevices(listOf(otherDevice, activeDevice))

            underTest.start()
            runAllReady()

            val notificationCaptor = argumentCaptor<Notification>()
            verify(notificationManager).notify(anyString(), anyInt(), notificationCaptor.capture())
            val notification = notificationCaptor.firstValue
            assertThat(notification.extras.getString(Notification.EXTRA_TITLE))
                .isEqualTo(activeDevice.name)
        }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICE_STATUS_NOTIFICATION)
    fun checkConnectedDevices_isNotActive_showConnectedHearingDeviceStatus() =
        kosmos.runTest {
            val connectedDevice =
                cachedDevice.stub {
                    on { isActiveDevice(BluetoothProfile.HEARING_AID) } doReturn false
                }
            val otherDevice = mock<CachedBluetoothDevice>()
            bluetoothRepository.setConnectedDevices(listOf(otherDevice, cachedDevice))

            underTest.start()
            runAllReady()

            val notificationCaptor = argumentCaptor<Notification>()
            verify(notificationManager).notify(anyString(), anyInt(), notificationCaptor.capture())
            val notification = notificationCaptor.firstValue
            assertThat(notification.extras.getString(Notification.EXTRA_TITLE))
                .isEqualTo(connectedDevice.name)
        }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICE_STATUS_NOTIFICATION)
    fun checkConnectedDevices_noHearingDevices_cancelNotification() =
        kosmos.runTest {
            bluetoothRepository.setConnectedDevices(emptyList())

            underTest.start()
            runAllReady()

            verify(notificationManager).cancel(anyString(), anyInt())
        }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICE_STATUS_NOTIFICATION)
    fun checkConnectedDevices_registerCallbackForMemberDevices() =
        kosmos.runTest {
            val mockMemberDevice = getMemberDevice()
            val mockMainDevice =
                cachedDevice.stub { on { memberDevice } doReturn setOf(mockMemberDevice) }

            underTest.start()
            runAllReady()

            verify(mockMainDevice).registerCallback(any(), any())
            verify(mockMemberDevice).registerCallback(any(), any())
        }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICE_STATUS_NOTIFICATION)
    fun checkConnectedDevices_registerCallbackForSubDevice() =
        kosmos.runTest {
            val mockSubDevice = getMemberDevice()
            val mockMainDevice = cachedDevice.stub { on { subDevice } doReturn mockSubDevice }

            underTest.start()
            runAllReady()

            verify(mockMainDevice).registerCallback(any(), any())
            verify(mockSubDevice).registerCallback(any(), any())
        }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICE_STATUS_NOTIFICATION)
    fun onDeviceAttributesChanged_updateNotification() =
        kosmos.runTest {
            underTest.start()
            runAllReady()
            underTest.onDeviceAttributesChanged()
            runAllReady()

            // notify called twice: once in start(), once in onDeviceAttributesChanged()
            verify(notificationManager, times(2)).notify(anyString(), anyInt(), any())
        }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICE_STATUS_NOTIFICATION)
    fun updateNotification_verifyVisibilityAndLabel() =
        kosmos.runTest {
            underTest.start()
            runAllReady()

            val notificationCaptor = argumentCaptor<Notification>()
            verify(notificationManager).notify(anyString(), anyInt(), notificationCaptor.capture())
            val notification = notificationCaptor.firstValue
            assertThat(notification.visibility).isEqualTo(Notification.VISIBILITY_PRIVATE)
            assertThat(notification.extras.getString(Notification.EXTRA_SUBSTITUTE_APP_NAME))
                .isEqualTo(context.getString(com.android.internal.R.string.android_system_label))
        }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICE_STATUS_NOTIFICATION)
    fun start_verifyChannelVisibility() =
        kosmos.runTest {
            underTest.start()
            runAllReady()

            val channelCaptor = argumentCaptor<android.app.NotificationChannel>()
            verify(notificationManager).createNotificationChannel(channelCaptor.capture())
            val channel = channelCaptor.firstValue
            assertThat(channel.lockscreenVisibility).isEqualTo(Notification.VISIBILITY_PRIVATE)
        }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICE_STATUS_NOTIFICATION)
    fun dismissalBroadcast_callDismissOnController() =
        kosmos.runTest {
            underTest.start()
            runAllReady()
            broadcastDispatcher.sendIntentToMatchingReceiversOnly(context, getDismissIntent())
            runAllReady()

            verify(dismissController).dismissNotification(TEST_DEVICE_ADDRESS)
        }

    fun connectedDevicesFlow_removeOneDevice_callRemoveDeviceOnController() =
        kosmos.runTest {
            val otherDevice = mock<CachedBluetoothDevice> { on { isHearingDevice } doReturn true }
            bluetoothRepository.setConnectedDevices(listOf(cachedDevice, otherDevice))

            underTest.start()
            runAllReady()
            bluetoothRepository.setConnectedDevices(listOf(otherDevice))
            runAllReady()

            verify(dismissController).removeDevice(TEST_DEVICE_ADDRESS)
        }

    @Test
    @EnableFlags(Flags.FLAG_HEARING_DEVICE_STATUS_NOTIFICATION)
    fun connectedDevicesFlow_disconnectAll_callResetOnController() =
        kosmos.runTest {
            underTest.start()
            runAllReady()
            bluetoothRepository.setConnectedDevices(emptyList())
            runAllReady()

            verify(dismissController).reset()
        }

    private fun getMemberDevice(): CachedBluetoothDevice {
        return mock<CachedBluetoothDevice> {
            on { name } doReturn TEST_MEMBER_DEVICE_NAME
            on { address } doReturn TEST_MEMBER_DEVICE_ADDRESS
            on { isConnected } doReturn true
            on { isHearingDevice } doReturn true
            on { batteryLevel } doReturn 60
        }
    }

    private fun getDismissIntent(): Intent {
        return Intent("com.android.systemui.accessibility.hearingaid.DISMISS_NOTIFICATION").apply {
            putExtra("device_address", TEST_DEVICE_ADDRESS)
        }
    }

    private fun Kosmos.runAllReady() {
        runCurrent()
        fakeExecutor.runAllReady()
    }

    companion object {
        private const val TEST_DEVICE_NAME = "test_device_name"
        private const val TEST_DEVICE_ADDRESS = "AA:BB:CC:DD:EE:FF"
        private const val TEST_MEMBER_DEVICE_NAME = "test_member_device_name"
        private const val TEST_MEMBER_DEVICE_ADDRESS = "AA:BB:CC:DD:EE:GG"
    }
}
