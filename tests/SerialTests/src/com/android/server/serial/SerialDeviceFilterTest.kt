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
 * limitations under the License
 */

package com.android.server.serial

import android.app.admin.DevicePolicyManagerInternal
import android.content.Context
import android.hardware.serial.SerialPort
import android.hardware.serialservice.SerialPortInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.server.LocalServices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import android.hardware.serialservice.ISerialManager as NativeSerialManager

/**
 * Tests for [SerialDeviceFilter].
 *
 * atest SerialTests:SerialDeviceFilterTest
 */
@RunWith(AndroidJUnit4::class)
class SerialDeviceFilterTest {
    @get:Rule
    val mockitoRule = MockitoJUnit.rule()

    @Mock
    private lateinit var nativeService: NativeSerialManager

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var devicePolicyManager: DevicePolicyManagerInternal

    private val lock = Object()

    @Before
    fun setUp() {
        doReturn(true).whenever(devicePolicyManager).isUsbDataSignalingEnabled
        LocalServices.removeServiceForTest(DevicePolicyManagerInternal::class.java)
        LocalServices.addService(DevicePolicyManagerInternal::class.java, devicePolicyManager)
    }

    @Test
    fun testIsAvailable_conforms() {
        val filter = SerialDeviceFilter(context, arrayOf<String>(), nativeService, lock)
        val info = SerialPortInfo()
        with (info) {
            name = "test"
            vendorId = -1
            productId = -1
            subsystem = "usb"
            driverType = "serial"
        }

        val actual = filter.isAvailable(info)

        assertTrue(actual)
    }

    @Test
    fun testIsAvailable_wrongSubsystem() {
        val filter = SerialDeviceFilter(context, arrayOf<String>(), nativeService, lock)
        val info = SerialPortInfo()
        with (info) {
            name = "test"
            vendorId = -1
            productId = -1
            subsystem = "serial-base"
            driverType = "serial"
        }

        val actual = filter.isAvailable(info)

        assertFalse(actual)
    }

    @Test
    fun testIsAvailable_wrongDriverType() {
        val filter = SerialDeviceFilter(context, arrayOf<String>(), nativeService, lock)
        val info = SerialPortInfo()
        with (info) {
            name = "test"
            vendorId = -1
            productId = -1
            subsystem = "usb"
            driverType = "system"
        }

        val actual = filter.isAvailable(info)

        assertFalse(actual)
    }

    @Test
    fun testIsAvailable_exposePty() {
        val filter = SerialDeviceFilter(context, arrayOf<String>(), nativeService, lock)
        filter.setIsPtyExposed(true)
        val info = SerialPortInfo()
        with (info) {
            name = "pts/2"
            vendorId = -1
            productId = -1
            subsystem = "serial-base"
            driverType = "system"
        }

        val actual = filter.isAvailable(info)

        assertTrue(actual)
    }

    @Test
    fun testIsAvailable_hidePty() {
        val filter = SerialDeviceFilter(context, arrayOf<String>(), nativeService, lock)
        filter.setIsPtyExposed(false)
        val info = SerialPortInfo()
        with (info) {
            name = "pts/2"
            vendorId = -1
            productId = -1
            subsystem = "serial-base"
            driverType = "system"
        }

        val actual = filter.isAvailable(info)

        assertFalse(actual)
    }

    @Test
    fun testIsAvailable_blockPort() {
        val filter = SerialDeviceFilter(
            context, arrayOf("0012:0013", "03f0:0620"), nativeService, lock)
        val info = SerialPortInfo()
        with (info) {
            name = "test"
            vendorId = 0x3f0
            productId = 0x620
            subsystem = "usb"
            driverType = "serial"
        }

        val actual = filter.isAvailable(info)

        assertFalse(actual)
    }

    @Test
    fun testIsAvailable_notInBlockList() {
        val filter = SerialDeviceFilter(
            context, arrayOf("0012:0013", "03f0:0620"), nativeService, lock)
        val info = SerialPortInfo()
        with (info) {
            name = "test"
            vendorId = 0x0013
            productId = 0x0014
            subsystem = "usb"
            driverType = "serial"
        }

        val actual = filter.isAvailable(info)

        assertTrue(actual)
    }

    @Test
    fun testIsAvailable_notUsbPort() {
        val filter = SerialDeviceFilter(
            context, arrayOf("0012:0013", "03f0:0620"), nativeService, lock)
        val info = SerialPortInfo()
        with (info) {
            name = "test"
            vendorId = SerialPort.INVALID_ID
            productId = SerialPort.INVALID_ID
            subsystem = "tty"
            driverType = "serial"
        }

        val actual = filter.isAvailable(info)

        assertTrue(actual)
    }

    @Test
    fun testIsAvailable_devicePolicyKillSwitch() {
        doReturn(false).whenever(devicePolicyManager).isUsbDataSignalingEnabled
        val filter = SerialDeviceFilter(context, arrayOf<String>(), nativeService, lock)
        val info = SerialPortInfo()
        with (info) {
            name = "test"
            vendorId = -1
            productId = -1
            subsystem = "usb"
            driverType = "serial"
        }

        val actual = filter.isAvailable(info)

        assertFalse(actual)
    }

    @Test
    fun testGetAvailablePorts_success() {
        val info = SerialPortInfo()
        with (info) {
            name = "test"
            vendorId = -1
            productId = -1
            subsystem = "usb"
            driverType = "serial"
        }
        whenever(nativeService.serialPorts).thenReturn(listOf(info))
        val filter = SerialDeviceFilter(context, arrayOf<String>(), nativeService, lock)

        val serialPorts = filter.availablePorts

        verify(nativeService).serialPorts
        assertEquals(1, serialPorts.size)
        assertEquals("test", serialPorts["test"]?.name)
    }

    @Test
    fun testGetAvailablePorts_allPortsBlocked() {
        doReturn(false).whenever(devicePolicyManager).isUsbDataSignalingEnabled
        val info = SerialPortInfo()
        with (info) {
            name = "test"
            vendorId = -1
            productId = -1
            subsystem = "usb"
            driverType = "serial"
        }
        whenever(nativeService.serialPorts).thenReturn(listOf(info))
        val filter = SerialDeviceFilter(context, arrayOf<String>(), nativeService, lock)

        val serialPorts = filter.availablePorts

        verify(nativeService).serialPorts
        assertTrue(serialPorts.isEmpty())
    }
}