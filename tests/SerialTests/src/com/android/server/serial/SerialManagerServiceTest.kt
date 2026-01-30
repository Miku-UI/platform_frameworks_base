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

import android.content.Context
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.hardware.serial.ISerialPortListener
import android.hardware.serial.ISerialPortResponseCallback
import android.hardware.serial.ISerialPortResponseCallback.ErrorCode
import android.hardware.serial.SerialPort
import android.hardware.serial.SerialPortInfo
import android.hardware.serialservice.ISerialPortListener as NativeSerialPortListener
import android.hardware.serialservice.ISerialManager as NativeSerialManager
import android.hardware.serialservice.SerialPortInfo as NativeSerialPortInfo
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.system.OsConstants.O_NOCTTY
import android.system.OsConstants.O_NONBLOCK
import android.system.OsConstants.O_RDWR
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.function.Predicate
import java.util.function.Supplier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for serial manager service.
 *
 * atest SerialTests:SerialManagerServiceTest
 */
@RunWith(AndroidJUnit4::class)
class SerialManagerServiceTest {
    @get:Rule
    val mockitoRule = MockitoJUnit.rule()

    @Mock
    private lateinit var nativeService: NativeSerialManager

    @Mock
    private lateinit var callback: ISerialPortResponseCallback

    @Mock
    private lateinit var context: Context

    private val serialDeviceFilter = Predicate<NativeSerialPortInfo> { _ -> true }

    private val nativeServiceSupplier = Supplier<NativeSerialManager> { nativeService }

    private val PORT_NAME = "ttyS0"
    private val PORT_PATH = "/dev/" + PORT_NAME

    private val NATIVE_SERIAL_PORT_INFO = {
        val info = NativeSerialPortInfo()
        with (info) {
            name = PORT_NAME
            vendorId = -1
            productId = -1
            subsystem = "usb"
            driverType = "serial"
        }
        info
    }()

    @Test
    fun testGetSerialPorts_success() {
        val service = SerialManagerService(
            context, arrayOf<String>(), serialDeviceFilter, nativeServiceSupplier
        )
        whenever(nativeService.serialPorts).thenReturn(listOf(NATIVE_SERIAL_PORT_INFO))

        val serialPorts = service.serialPorts

        verify(nativeService).serialPorts
        assertEquals(1, serialPorts.size)
        assertEquals(PORT_NAME, serialPorts[0].name)
    }

    @Test
    fun testGetSerialPorts_failure() {
        val service = SerialManagerService(
            context, arrayOf<String>(), serialDeviceFilter, nativeServiceSupplier
        )
        whenever(nativeService.serialPorts).thenThrow(RemoteException())

        val serialPorts = service.serialPorts

        verify(nativeService).serialPorts
        assertTrue(serialPorts.isEmpty())
    }

    @Test
    fun testRegisterSerialPortListener_connected() {
        val service = SerialManagerService(
            context, arrayOf<String>(), serialDeviceFilter, nativeServiceSupplier
        )
        val binder: IBinder = mock()
        val listener: ISerialPortListener = mock()
        whenever(listener.asBinder()).thenReturn(binder)

        service.registerSerialPortListener(listener)
        val nativeSerialPortListenerCaptor = ArgumentCaptor.forClass(NativeSerialPortListener::class.java)
        verify(nativeService).registerSerialPortListener(nativeSerialPortListenerCaptor.capture())
        nativeSerialPortListenerCaptor.value.onSerialPortConnected(NATIVE_SERIAL_PORT_INFO)

        val serialPortInfoCaptor = ArgumentCaptor.forClass(SerialPortInfo::class.java)
        verify(listener).onSerialPortConnected(serialPortInfoCaptor.capture())
        assertEquals(PORT_NAME, serialPortInfoCaptor.value.name)
        verify(listener, never()).onSerialPortDisconnected(any())
    }

    @Test
    fun testRegisterSerialPortListener_disconnected() {
        val service = SerialManagerService(
            context, arrayOf<String>(), serialDeviceFilter, nativeServiceSupplier
        )
        val binder: IBinder = mock()
        val listener: ISerialPortListener = mock()
        whenever(listener.asBinder()).thenReturn(binder)

        service.registerSerialPortListener(listener)
        val nativeSerialPortListenerCaptor = ArgumentCaptor.forClass(NativeSerialPortListener::class.java)
        verify(nativeService).registerSerialPortListener(nativeSerialPortListenerCaptor.capture())
        nativeSerialPortListenerCaptor.value.onSerialPortConnected(NATIVE_SERIAL_PORT_INFO)
        nativeSerialPortListenerCaptor.value.onSerialPortDisconnected(NATIVE_SERIAL_PORT_INFO)

        verify(listener).onSerialPortConnected(any())
        val serialPortInfoCaptor = ArgumentCaptor.forClass(SerialPortInfo::class.java)
        verify(listener).onSerialPortDisconnected(serialPortInfoCaptor.capture())
        assertEquals(PORT_NAME, serialPortInfoCaptor.value.name)
    }

    @Test
    fun testUnregisterSerialPortListener() {
        val service = SerialManagerService(
            context, arrayOf<String>(), serialDeviceFilter, nativeServiceSupplier
        )
        val binder: IBinder = mock()
        val listener: ISerialPortListener = mock()
        whenever(listener.asBinder()).thenReturn(binder)

        service.registerSerialPortListener(listener)
        service.unregisterSerialPortListener(listener)
        val nativeSerialPortListenerCaptor = ArgumentCaptor.forClass(NativeSerialPortListener::class.java)
        verify(nativeService).registerSerialPortListener(nativeSerialPortListenerCaptor.capture())
        nativeSerialPortListenerCaptor.value.onSerialPortConnected(NATIVE_SERIAL_PORT_INFO)

        verify(listener, never()).onSerialPortConnected(any())
        verify(listener, never()).onSerialPortDisconnected(any())
    }

    @Test
    fun testRequestOpen_success() {
        val service = SerialManagerService(
            context, arrayOf(PORT_PATH), serialDeviceFilter, nativeServiceSupplier
        )
        whenever(nativeService.serialPorts).thenReturn(listOf(NATIVE_SERIAL_PORT_INFO))
        whenever(context.checkPermission(any(), any(), any())).thenReturn(PERMISSION_GRANTED)
        val pfd: ParcelFileDescriptor = mock()
        whenever(nativeService.requestOpen(any(), any(), any())).thenReturn(pfd)
        val flags = SerialPort.OPEN_FLAG_READ_WRITE or SerialPort.OPEN_FLAG_NONBLOCK

        service.requestOpen(PORT_NAME, flags, /*exclusive=*/ false, "package", callback)

        verify(callback, never()).onError(any(), any())
        val nativePortCaptor = ArgumentCaptor.forClass(String::class.java)
        val nativeFlagsCaptor = ArgumentCaptor.forClass(Int::class.java)
        val nativeExclusiveCaptor = ArgumentCaptor.forClass(Boolean::class.java)
        verify(nativeService).requestOpen(
            nativePortCaptor.capture(),
            nativeFlagsCaptor.capture(),
            nativeExclusiveCaptor.capture()
        )
        assertEquals(PORT_NAME, nativePortCaptor.value)
        assertEquals(O_RDWR or O_NONBLOCK or O_NOCTTY, nativeFlagsCaptor.value)
        assertFalse(nativeExclusiveCaptor.value)
        val serialPortInfoCaptor = ArgumentCaptor.forClass(SerialPortInfo::class.java)
        val pfdCaptor = ArgumentCaptor.forClass(ParcelFileDescriptor::class.java)
        verify(callback).onResult(serialPortInfoCaptor.capture(), pfdCaptor.capture())
        assertEquals(PORT_NAME, serialPortInfoCaptor.value.name)
        assertEquals(pfd, pfdCaptor.value)
    }

    @Test
    fun testRequestOpen_portInConfigWithoutPrefix() {
        val service = SerialManagerService(
            context, arrayOf(PORT_NAME), serialDeviceFilter, nativeServiceSupplier
        )
        whenever(nativeService.serialPorts).thenReturn(listOf(NATIVE_SERIAL_PORT_INFO))
        whenever(context.checkPermission(any(), any(), any())).thenReturn(PERMISSION_GRANTED)
        val flags = SerialPort.OPEN_FLAG_READ_WRITE

        service.requestOpen(PORT_NAME, flags, /*exclusive=*/ false, "package", callback)

        val errorCodeCaptor = ArgumentCaptor.forClass(Int::class.java)
        verify(callback).onError(errorCodeCaptor.capture(), any())
        assertEquals(ErrorCode.ERROR_ACCESS_DENIED, errorCodeCaptor.value)
    }

    @Test
    fun testRequestOpen_portNotFound() {
        val service = SerialManagerService(
            context, arrayOf(PORT_PATH), serialDeviceFilter, nativeServiceSupplier
        )
        whenever(nativeService.serialPorts).thenReturn(listOf())
        whenever(context.checkPermission(any(), any(), any())).thenReturn(PERMISSION_GRANTED)
        val flags = SerialPort.OPEN_FLAG_WRITE_ONLY

        service.requestOpen(PORT_NAME, flags, /*exclusive=*/ false, "package", callback)

        val errorCodeCaptor = ArgumentCaptor.forClass(Int::class.java)
        verify(callback).onError(errorCodeCaptor.capture(), any())
        assertEquals(ErrorCode.ERROR_PORT_NOT_FOUND, errorCodeCaptor.value)
    }

    @Test
    fun testRequestOpen_noPermission() {
        val service = SerialManagerService(
            context, arrayOf(PORT_PATH), serialDeviceFilter, nativeServiceSupplier
        )
        whenever(nativeService.serialPorts).thenReturn(listOf(NATIVE_SERIAL_PORT_INFO))
        whenever(context.checkPermission(any(), any(), any())).thenReturn(PERMISSION_DENIED)
        val flags = SerialPort.OPEN_FLAG_READ_ONLY

        service.requestOpen(PORT_NAME, flags, /*exclusive=*/ false, "package", callback)

        val errorCodeCaptor = ArgumentCaptor.forClass(Int::class.java)
        verify(callback).onError(errorCodeCaptor.capture(), any())
        assertEquals(ErrorCode.ERROR_ACCESS_DENIED, errorCodeCaptor.value)
    }

    @Test
    fun testRequestOpen_openFailed() {
        val service = SerialManagerService(
            context, arrayOf(PORT_PATH), serialDeviceFilter, nativeServiceSupplier
        )
        whenever(nativeService.serialPorts).thenReturn(listOf(NATIVE_SERIAL_PORT_INFO))
        whenever(context.checkPermission(any(), any(), any())).thenReturn(PERMISSION_GRANTED)
        whenever(nativeService.requestOpen(any(), any(), any())).thenThrow(RemoteException())
        val flags = SerialPort.OPEN_FLAG_READ_WRITE or SerialPort.OPEN_FLAG_DATA_SYNC

        service.requestOpen(PORT_NAME, flags, /*exclusive=*/ false, "package", callback)

        val errorCodeCaptor = ArgumentCaptor.forClass(Int::class.java)
        verify(callback).onError(errorCodeCaptor.capture(), any())
        assertEquals(ErrorCode.ERROR_OPENING_PORT, errorCodeCaptor.value)
    }

    @Test
    fun testToOsConstants_twoOpenModes() {
        val service = SerialManagerService(
            context, arrayOf(PORT_PATH), serialDeviceFilter, nativeServiceSupplier
        )
        whenever(context.checkPermission(any(), any(), any())).thenReturn(PERMISSION_GRANTED)
        whenever(nativeService.serialPorts).thenReturn(listOf(NATIVE_SERIAL_PORT_INFO))
        val flags = SerialPort.OPEN_FLAG_WRITE_ONLY or SerialPort.OPEN_FLAG_READ_WRITE

        assertThrows(IllegalArgumentException::class.java) {
            service.requestOpen(PORT_NAME, flags, /*exclusive=*/ false, "package", callback)
        }
    }

    @Test
    fun testToOsConstants_wrongBitInFlags() {
        val service = SerialManagerService(
            context, arrayOf(PORT_PATH), serialDeviceFilter, nativeServiceSupplier
        )
        whenever(context.checkPermission(any(), any(), any())).thenReturn(PERMISSION_GRANTED)
        whenever(nativeService.serialPorts).thenReturn(listOf(NATIVE_SERIAL_PORT_INFO))
        val flags = 1 shl 31

        assertThrows(IllegalArgumentException::class.java) {
            service.requestOpen(PORT_NAME, flags, /*exclusive=*/ false, "package", callback)
        }
    }
}