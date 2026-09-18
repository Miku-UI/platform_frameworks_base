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

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManagerInternal
import android.hardware.serial.ISerialPortListener
import android.hardware.serial.ISerialPortResponseCallback
import android.hardware.serial.ISerialPortResponseCallback.ErrorCode
import android.hardware.serial.SerialPort
import android.hardware.serial.SerialPortInfo
import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.os.UserHandle
import android.system.OsConstants.O_NOCTTY
import android.system.OsConstants.O_NONBLOCK
import android.system.OsConstants.O_RDWR
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.server.LocalServices
import java.util.function.Supplier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.stubbing.Answer
import android.hardware.serialservice.ISerialManager as NativeSerialManager
import android.hardware.serialservice.ISerialPortListener as NativeSerialPortListener
import android.hardware.serialservice.SerialPortInfo as NativeSerialPortInfo

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
    private lateinit var accessManager: SerialUserAccessManagerInterface

    @Mock
    private lateinit var pmInternal: PackageManagerInternal

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var serializer: PortAccessSerializerInterface

    private val nativeServiceSupplier = Supplier<NativeSerialManager> { nativeService }

    @Before
    fun setUp() {
        doReturn(true).whenever(pmInternal).isSameApp(any(), anyInt(), anyInt())
        LocalServices.removeServiceForTest(PackageManagerInternal::class.java)
        LocalServices.addService(PackageManagerInternal::class.java, pmInternal)

        whenever(context.checkPermission(eq(Manifest.permission.SERIAL_PORT), anyInt(), anyInt()))
            .thenReturn(PackageManager.PERMISSION_DENIED)
    }

    @Test
    fun testGetSerialPorts_success() {
        val service = SerialManagerService(
            context, arrayOf<String>(), arrayOf<String>(), "", nativeServiceSupplier,
            { _, _, _, _, _ -> accessManager }, serializer, true)
        whenever(nativeService.serialPorts).thenReturn(NATIVE_SERIAL_PORTS)

        val serialPorts = service.serialPorts

        verify(nativeService).serialPorts
        assertEquals(1, serialPorts.size)
        assertEquals(PORT_NAME, serialPorts[0].name)
    }

    @Test
    fun testGetSerialPorts_failure() {
        val service = SerialManagerService(
            context, arrayOf<String>(), arrayOf<String>(), "", nativeServiceSupplier,
            { _, _, _, _, _ -> accessManager }, serializer, true)
        whenever(nativeService.serialPorts).thenThrow(RemoteException())

        val serialPorts = service.serialPorts

        verify(nativeService).serialPorts
        assertTrue(serialPorts.isEmpty())
    }

    @Test(expected = SecurityException::class)
    fun testGetSerialPortsInConfig_WithoutPermission() {
        whenever(context.enforceCallingPermission(eq(Manifest.permission.SERIAL_PORT), any()))
            .thenThrow(SecurityException())
        val service = SerialManagerService(
            context, arrayOf(PORT_NAME), arrayOf<String>(), "", nativeServiceSupplier,
            { _, _, _, _, _ -> accessManager }, serializer, true)
        service.serialPortsInConfig
    }

    fun testGetSerialPortsInConfig_WithPermission() {
        val service = SerialManagerService(
            context, arrayOf(PORT_NAME), arrayOf<String>(), "", nativeServiceSupplier,
            { _, _, _, _, _ -> accessManager }, serializer, true)
        val ports = service.serialPortsInConfig
        assertEquals(1, ports.size)
        assertEquals(PORT_NAME, ports[0])
    }

    @Test
    fun testRegisterSerialPortListener_connected() {
        val service = SerialManagerService(
            context, arrayOf<String>(), arrayOf<String>(), "", nativeServiceSupplier,
            { _, _, _, _, _ -> accessManager }, serializer, true)
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
            context, arrayOf<String>(), arrayOf<String>(), "", nativeServiceSupplier,
            { _, _, _, _, _ -> accessManager }, serializer, true)
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
            context, arrayOf<String>(), arrayOf<String>(), "", nativeServiceSupplier,
            { _, _, _, _, _ -> accessManager }, serializer, true
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
            context, arrayOf(PORT_PATH), arrayOf<String>(), "", nativeServiceSupplier,
            { _, _, _, _, _ -> accessManager }, serializer, true)
        whenever(nativeService.serialPorts).thenReturn(NATIVE_SERIAL_PORTS)
        whenever(accessManager.requestAccess(any(), any(), any(), any(), any())).thenAnswer(
            answerAccessToPortGranted(true)
        )
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
    fun testRequestOpen_packageNameNotMatch() {
        val service = SerialManagerService(
            context, arrayOf(PORT_PATH), arrayOf<String>(), "", nativeServiceSupplier,
            { _, _, _, _, _ -> accessManager }, serializer, true)
        reset(pmInternal)
        val packageName = "package"
        val uid = Binder.getCallingUid()
        val userId = UserHandle.getUserId(uid)
        doReturn(false).whenever(pmInternal).isSameApp(packageName, uid, userId)

        val flags = SerialPort.OPEN_FLAG_READ_WRITE or SerialPort.OPEN_FLAG_NONBLOCK
        service.requestOpen(PORT_NAME, flags, /*exclusive=*/ false, packageName, callback)

        verify(pmInternal).isSameApp(packageName, uid, userId)

        verify(callback, never()).onResult(any(), any())
        verify(callback).onError(eq(ErrorCode.ERROR_ACCESS_DENIED), any())
    }

    @Test
    fun testRequestOpen_portInConfigWithSerialPortPermission() {
        val configPortName = "ttyS1"

        reset(context)
        whenever(context.checkPermission(eq(Manifest.permission.SERIAL_PORT), anyInt(), anyInt()))
            .thenReturn(PackageManager.PERMISSION_GRANTED)

        val service = SerialManagerService(
            context, arrayOf("/dev/$configPortName"), arrayOf<String>(), "", nativeServiceSupplier,
            { _, _, _, _, _ -> accessManager }, serializer, true)
        whenever(accessManager.requestAccess(any(), any(), any(), any(), any())).thenAnswer(
            answerAccessToPortGranted(true)
        )
        whenever(nativeService.serialPorts).thenReturn(NATIVE_SERIAL_PORTS)

        val pfd: ParcelFileDescriptor = mock()
        whenever(nativeService.requestOpen(any(), any(), any())).thenReturn(pfd)
        val flags = SerialPort.OPEN_FLAG_READ_WRITE

        service.requestOpen(configPortName, flags, /*exclusive=*/ false, "package", callback)

        verify(callback, never()).onError(any(), any())
        val nativePortCaptor = ArgumentCaptor.forClass(String::class.java)
        val nativeFlagsCaptor = ArgumentCaptor.forClass(Int::class.java)
        val nativeExclusiveCaptor = ArgumentCaptor.forClass(Boolean::class.java)
        verify(nativeService).requestOpen(
            nativePortCaptor.capture(),
            nativeFlagsCaptor.capture(),
            nativeExclusiveCaptor.capture()
        )
        assertEquals(configPortName, nativePortCaptor.value)
        assertEquals(O_RDWR or O_NOCTTY, nativeFlagsCaptor.value)
        assertFalse(nativeExclusiveCaptor.value)
        val serialPortInfoCaptor = ArgumentCaptor.forClass(SerialPortInfo::class.java)
        val pfdCaptor = ArgumentCaptor.forClass(ParcelFileDescriptor::class.java)
        verify(callback).onResult(serialPortInfoCaptor.capture(), pfdCaptor.capture())
        assertEquals(configPortName, serialPortInfoCaptor.value.name)
        assertEquals(pfd, pfdCaptor.value)
    }

    @Test
    fun testRequestOpen_portInConfigWithoutPrefix() {
        val service = SerialManagerService(
            context, arrayOf(PORT_NAME), arrayOf<String>(), "", nativeServiceSupplier,
            { _, _, _, _, _ -> accessManager }, serializer, true)
        whenever(accessManager.requestAccess(any(), any(), any(), any(), any())).thenAnswer(
            answerAccessToPortGranted(false)
        )
        whenever(nativeService.serialPorts).thenReturn(NATIVE_SERIAL_PORTS)
        val flags = SerialPort.OPEN_FLAG_READ_WRITE

        service.requestOpen(PORT_NAME, flags, /*exclusive=*/ false, "package", callback)

        val errorCodeCaptor = ArgumentCaptor.forClass(Int::class.java)
        verify(callback).onError(errorCodeCaptor.capture(), any())
        assertEquals(ErrorCode.ERROR_ACCESS_DENIED, errorCodeCaptor.value)
    }

    @Test
    fun testRequestOpen_portNotFound() {
        val service = SerialManagerService(
            context, arrayOf(PORT_PATH), arrayOf<String>(), "", nativeServiceSupplier,
            { _, _, _, _, _ -> accessManager }, serializer, true)
        whenever(nativeService.serialPorts).thenReturn(listOf())
        whenever(accessManager.requestAccess(any(), any(), any(), any(), any())).thenAnswer(
            answerAccessToPortGranted(true)
        )
        val flags = SerialPort.OPEN_FLAG_WRITE_ONLY

        service.requestOpen(PORT_NAME, flags, /*exclusive=*/ false, "package", callback)

        val errorCodeCaptor = ArgumentCaptor.forClass(Int::class.java)
        verify(callback).onError(errorCodeCaptor.capture(), any())
        assertEquals(ErrorCode.ERROR_PORT_NOT_FOUND, errorCodeCaptor.value)
    }

    @Test
    fun testRequestOpen_noPermission() {
        val service = SerialManagerService(
            context, arrayOf(PORT_PATH), arrayOf<String>(), "", nativeServiceSupplier,
            { _, _, _, _, _ -> accessManager }, serializer, true)
        whenever(accessManager.requestAccess(any(), any(), any(), any(), any())).thenAnswer {
            val port = it.getArgument<String>(0)
            val pid = it.getArgument<Int>(1)
            val uid = it.getArgument<Int>(2)
            val callback = it.getArgument<AccessToPortDecidedCallback>(4)
            callback.onAccessToPortDecided(port, pid, uid, /* granted */ false)
        }
        whenever(nativeService.serialPorts).thenReturn(NATIVE_SERIAL_PORTS)
        whenever(accessManager.requestAccess(any(), any(), any(), any(), any())).thenAnswer(
            answerAccessToPortGranted(false)
        )
        val flags = SerialPort.OPEN_FLAG_READ_ONLY

        service.requestOpen(PORT_NAME, flags, /*exclusive=*/ false, "package", callback)

        val errorCodeCaptor = ArgumentCaptor.forClass(Int::class.java)
        verify(callback).onError(errorCodeCaptor.capture(), any())
        assertEquals(ErrorCode.ERROR_ACCESS_DENIED, errorCodeCaptor.value)
    }

    @Test
    fun testRequestOpen_openFailed() {
        val service = SerialManagerService(
            context, arrayOf(PORT_PATH), arrayOf<String>(), "", nativeServiceSupplier,
            { _, _, _, _, _ -> accessManager }, serializer, true)
        whenever(nativeService.serialPorts).thenReturn(NATIVE_SERIAL_PORTS)
        whenever(accessManager.requestAccess(any(), any(), any(), any(), any())).thenAnswer(
            answerAccessToPortGranted(true)
        )
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
            context, arrayOf(PORT_PATH), arrayOf<String>(), "", nativeServiceSupplier,
            { _, _, _, _, _ -> accessManager }, serializer, true)
        whenever(accessManager.requestAccess(any(), any(), any(), any(), any())).thenAnswer(
            answerAccessToPortGranted(true)
        )
        whenever(nativeService.serialPorts).thenReturn(NATIVE_SERIAL_PORTS)
        val flags = SerialPort.OPEN_FLAG_WRITE_ONLY or SerialPort.OPEN_FLAG_READ_WRITE

        assertThrows(IllegalArgumentException::class.java) {
            service.requestOpen(PORT_NAME, flags, /*exclusive=*/ false, "package", callback)
        }
    }

    @Test
    fun testToOsConstants_wrongBitInFlags() {
        val service = SerialManagerService(
            context, arrayOf(PORT_PATH), arrayOf<String>(), "", nativeServiceSupplier,
            { _, _, _, _, _ -> accessManager }, serializer, true)
        whenever(accessManager.requestAccess(any(), any(), any(), any(), any())).thenAnswer(
            answerAccessToPortGranted(true)
        )
        whenever(nativeService.serialPorts).thenReturn(NATIVE_SERIAL_PORTS)
        val flags = 1 shl 31

        assertThrows(IllegalArgumentException::class.java) {
            service.requestOpen(PORT_NAME, flags, /*exclusive=*/ false, "package", callback)
        }
    }

    @Test
    fun testGrantSerialPortAccess_permissionDenied() {
        // Verifies that grantSerialPortAccess throws a SecurityException if the caller does not
        // have the MANAGE_SERIAL_PORTS permission.
        val service = SerialManagerService(
            context, arrayOf(PORT_PATH), arrayOf<String>(), "", nativeServiceSupplier,
            { _, _, _, _, _ -> accessManager }, serializer, true)
        whenever(context.enforceCallingPermission(
            Manifest.permission.MANAGE_SERIAL_PORTS,
            "The caller doesn't have MANAGE_SERIAL_PORTS permission."
        )).doThrow(SecurityException("Permission denied"))

        assertThrows(SecurityException::class.java) {
            service.grantSerialPortAccess(PORT_NAME, 123, false, null)
        }

        // Verify that the access manager is not called when permission is denied.
        verify(accessManager, never()).grantAccess(any(), any(), any(), any())
    }

    @Test
    fun testGrantSerialPortAccess_success() {
        // Verifies that grantSerialPortAccess calls the access manager when the caller has the
        // required permission.
        val service = SerialManagerService(
            context, arrayOf(PORT_PATH), arrayOf<String>(), "", nativeServiceSupplier,
            { _, _, _, _, _ -> accessManager }, serializer, true)
        whenever(nativeService.serialPorts).thenReturn(listOf(NATIVE_SERIAL_PORT_INFO))
        // This call is to ensure the access manager for the user is created.
        service.requestOpen(PORT_NAME, SerialPort.OPEN_FLAG_READ_WRITE, false, "pkg", callback)

        val token: IBinder = mock()
        val uid = 10123 // some app uid for user 0
        service.grantSerialPortAccess(PORT_NAME, uid, false, token)

        // Verify that the permission is checked.
        verify(context).enforceCallingPermission(
            Manifest.permission.MANAGE_SERIAL_PORTS,
            "The caller doesn't have MANAGE_SERIAL_PORTS permission."
        )
        // Verify that the access manager is called to grant access.
        verify(accessManager).grantAccess(PORT_NAME, uid, false, token)
    }

    @Test
    fun testRevokeSerialPortAccess_permissionDenied() {
        // Verifies that revokeSerialPortAccess throws a SecurityException if the caller does not
        // have the MANAGE_SERIAL_PORTS permission.
        val service = SerialManagerService(
            context, arrayOf(PORT_PATH), arrayOf<String>(), "", nativeServiceSupplier,
            { _, _, _, _, _ -> accessManager }, serializer, true)
        whenever(context.enforceCallingPermission(
            Manifest.permission.MANAGE_SERIAL_PORTS,
            "The caller doesn't have MANAGE_SERIAL_PORTS permission."
        )).doThrow(SecurityException("Permission denied"))

        assertThrows(SecurityException::class.java) {
            service.revokeSerialPortAccess(PORT_NAME, 123, false, null)
        }

        // Verify that the access manager is not called when permission is denied.
        verify(accessManager, never()).revokeAccess(any(), any(), any(), any())
    }

    @Test
    fun testRevokeSerialPortAccess_success() {
        // Verifies that revokeSerialPortAccess calls the access manager when the caller has the
        // required permission.
        val service = SerialManagerService(
            context, arrayOf(PORT_PATH), arrayOf<String>(), "", nativeServiceSupplier,
            { _, _, _, _, _ -> accessManager }, serializer, true)
        whenever(nativeService.serialPorts).thenReturn(listOf(NATIVE_SERIAL_PORT_INFO))
        // This call is to ensure the access manager for the user is created.
        service.requestOpen(PORT_NAME, SerialPort.OPEN_FLAG_READ_WRITE, false, "pkg", callback)

        val token: IBinder = mock()
        val uid = 10123 // some app uid for user 0
        service.revokeSerialPortAccess(PORT_NAME, uid, false, token)

        // Verify that the permission is checked.
        verify(context).enforceCallingPermission(
            Manifest.permission.MANAGE_SERIAL_PORTS,
            "The caller doesn't have MANAGE_SERIAL_PORTS permission."
        )
        // Verify that the access manager is called to revoke access.
        verify(accessManager).revokeAccess(PORT_NAME, uid, false, token)
    }

    private fun answerAccessToPortGranted(granted: Boolean): Answer<Unit> {
        return Answer<Unit> {
            val port = it.getArgument<String>(0)
            val pid = it.getArgument<Int>(1)
            val uid = it.getArgument<Int>(2)
            val callback = it.getArgument<AccessToPortDecidedCallback>(4)
            callback.onAccessToPortDecided(port, pid, uid, granted)
        }
    }

    companion object {
        private const val PORT_NAME = "ttyS0"
        private const val PORT_PATH = "/dev/$PORT_NAME"

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

        private val NATIVE_SERIAL_PORTS = listOf(NATIVE_SERIAL_PORT_INFO)
    }
}
