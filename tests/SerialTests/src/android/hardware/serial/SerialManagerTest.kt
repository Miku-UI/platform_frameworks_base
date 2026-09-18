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

package android.hardware.serial

import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for SerialManager.
 *
 * atest SerialTests:SerialManagerTest
 */
@RunWith(AndroidJUnit4::class)
@EnableFlags(android.hardware.serial.flags.Flags.FLAG_ENABLE_WIRED_SERIAL_API)
class SerialManagerTest {
    @get:Rule
    val mockitoRule = MockitoJUnit.rule()

    @Mock
    private lateinit var backendService: ISerialManager

    @Captor
    private lateinit var backendSerialPortListener: ArgumentCaptor<ISerialPortListener>

    @Captor
    private lateinit var serialPort: ArgumentCaptor<SerialPort>

    private val context = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun testSerialPorts() {
        val serialManager = SerialManager(context, backendService)
        val infos = listOf(
            SerialPortInfo("test", -1, -1),
            SerialPortInfo("ttyUSB0", 1234, 5678),
        )
        whenever(backendService.serialPorts).thenReturn(infos)

        val ports = serialManager.ports

        assertEquals(ports.size, 2)
        assertSerialPortAttributes(ports[0], "test", -1, -1)
        assertSerialPortAttributes(ports[1], "ttyUSB0", 1234, 5678)
    }

    @Test
    fun testRegisterSerialPortListener() {
        val serialManager = SerialManager(context, backendService)
        val listener1: SerialPortListener = mock()
        val listener2: SerialPortListener = mock()
        val executor = Executor { r -> r.run() }
        val info1 = SerialPortInfo("ttyUSB0", 1234, 5678)
        val info2 = SerialPortInfo("test", -1, -1)

        serialManager.registerSerialPortListener(executor, listener1)
        serialManager.registerSerialPortListener(executor, listener2)
        verify(backendService).registerSerialPortListener(backendSerialPortListener.capture())
        backendSerialPortListener.value.onSerialPortConnected(info1)
        backendSerialPortListener.value.onSerialPortDisconnected(info2)

        verify(listener1).onSerialPortConnected(serialPort.capture())
        assertSerialPortAttributes(serialPort.value, "ttyUSB0", 1234, 5678)
        verify(listener1).onSerialPortDisconnected(serialPort.capture())
        assertSerialPortAttributes(serialPort.value, "test", -1, -1)
        verify(listener2).onSerialPortConnected(any())
        verify(listener2).onSerialPortDisconnected(any())
    }

    @Test
    fun testUnregisterSerialPortListener() {
        val serialManager = SerialManager(context, backendService)
        val listener1: SerialPortListener = mock()
        val listener2: SerialPortListener = mock()
        val executor = Executor { r -> r.run() }
        val info1 = SerialPortInfo("ttyUSB0", 1234, 5678)
        val info2 = SerialPortInfo("test", -1, -1)
        serialManager.registerSerialPortListener(executor, listener1)
        serialManager.registerSerialPortListener(executor, listener2)
        verify(backendService).registerSerialPortListener(backendSerialPortListener.capture())

        serialManager.unregisterSerialPortListener(listener2)
        backendSerialPortListener.value.onSerialPortConnected(info1)
        backendSerialPortListener.value.onSerialPortDisconnected(info2)

        verify(listener1).onSerialPortConnected(any())
        verify(listener1).onSerialPortDisconnected(any())
        verify(listener2, never()).onSerialPortConnected(any())
        verify(listener2, never()).onSerialPortDisconnected(any())
    }

    @Test
    fun testCompatibleSerialPorts() {
        val portInConfig = "ttyS0"
        whenever(backendService.serialPortsInConfig).thenReturn(arrayOf(portInConfig))

        val serialManager = SerialManager(context, backendService)
        val paths = serialManager.serialPorts

        assertEquals(1, paths.size)
        assertEquals("/dev/$portInConfig", paths[0])
    }

    @Test(expected = SecurityException::class)
    fun testCompatibleOpenSerialPort_NoPermission() {
        val mockContext: Context = mock()
        whenever(mockContext.checkSelfPermission(android.Manifest.permission.SERIAL_PORT))
            .thenReturn(PackageManager.PERMISSION_DENIED)

        val portInConfig = "ttyS0"
        val speed = 9600

        whenever(backendService.serialPortsInConfig).thenReturn(arrayOf(portInConfig))

        val serialManager = SerialManager(mockContext, backendService)
        serialManager.openSerialPort("/dev/$portInConfig", speed)
    }

    @Test(expected = IOException::class)
    fun testCompatibleOpenSerialPort_PathOutOfDev() {
        val mockContext: Context = mock()
        whenever(mockContext.checkSelfPermission(android.Manifest.permission.SERIAL_PORT))
            .thenReturn(PackageManager.PERMISSION_GRANTED)

        val portInConfig = "ttyS0"
        val speed = 9600

        whenever(backendService.serialPortsInConfig).thenReturn(arrayOf(portInConfig))

        val serialManager = SerialManager(mockContext, backendService)
        serialManager.openSerialPort(portInConfig, speed)
    }

    @Test(expected = IOException::class)
    fun testCompatibleOpenSerialPort_PortNotInConfig() {
        val mockContext: Context = mock()
        whenever(mockContext.checkSelfPermission(android.Manifest.permission.SERIAL_PORT))
            .thenReturn(PackageManager.PERMISSION_GRANTED)

        val portInConfig = "ttyS0"
        val speed = 9600

        whenever(backendService.serialPortsInConfig).thenReturn(arrayOf<String>())

        val serialManager = SerialManager(mockContext, backendService)
        serialManager.openSerialPort("/dev/$portInConfig", speed)
    }

    @Test(expected = IOException::class)
    fun testCompatibleOpenSerialPort_RemoteIoException() {
        val mockContext: Context = mock()
        whenever(mockContext.checkSelfPermission(android.Manifest.permission.SERIAL_PORT))
            .thenReturn(PackageManager.PERMISSION_GRANTED)

        val packageName = context.packageName
        whenever(mockContext.packageName).thenReturn(packageName)

        val portInConfig = "ttyS0"
        val speed = 9600

        whenever(backendService.serialPortsInConfig).thenReturn(arrayOf(portInConfig))

        doAnswer { invocation ->
            val callback = invocation.getArgument<ISerialPortResponseCallback>(4)
            callback.onError(
                ISerialPortResponseCallback.ErrorCode.ERROR_OPENING_PORT, "message")
        }.whenever(backendService).requestOpen(
            any(),
            anyInt(),
            anyBoolean(),
            any(),
            any()
        )

        val serialManager = SerialManager(mockContext, backendService)
        serialManager.openSerialPort("/dev/$portInConfig", speed)
    }

    @Test(expected = IOException::class)
    fun testCompatibleOpenSerialPort_RemoteNonIoException() {
        val mockContext: Context = mock()
        whenever(mockContext.checkSelfPermission(android.Manifest.permission.SERIAL_PORT))
            .thenReturn(PackageManager.PERMISSION_GRANTED)

        val packageName = context.packageName
        whenever(mockContext.packageName).thenReturn(packageName)

        val portInConfig = "ttyS0"
        val speed = 9600

        whenever(backendService.serialPortsInConfig).thenReturn(arrayOf(portInConfig))

        doAnswer { invocation ->
            val callback = invocation.getArgument<ISerialPortResponseCallback>(4)
            callback.onError(
                ISerialPortResponseCallback.ErrorCode.ERROR_PORT_NOT_FOUND, "message")
        }.whenever(backendService).requestOpen(
            any(),
            anyInt(),
            anyBoolean(),
            any(),
            any()
        )

        val serialManager = SerialManager(mockContext, backendService)
        serialManager.openSerialPort("/dev/$portInConfig", speed)
    }

    @Test(expected = SecurityException::class)
    fun testCompatibleOpenSerialPort_RemoteSecurityException() {
        val mockContext: Context = mock()
        whenever(mockContext.checkSelfPermission(android.Manifest.permission.SERIAL_PORT))
            .thenReturn(PackageManager.PERMISSION_GRANTED)

        val packageName = context.packageName
        whenever(mockContext.packageName).thenReturn(packageName)

        val portInConfig = "ttyS0"
        val speed = 9600

        whenever(backendService.serialPortsInConfig).thenReturn(arrayOf(portInConfig))

        doAnswer { invocation ->
            val callback = invocation.getArgument<ISerialPortResponseCallback>(4)
            callback.onError(
                ISerialPortResponseCallback.ErrorCode.ERROR_ACCESS_DENIED, "message")
        }.whenever(backendService).requestOpen(
            any(),
            anyInt(),
            anyBoolean(),
            any(),
            any()
        )

        val serialManager = SerialManager(mockContext, backendService)
        serialManager.openSerialPort("/dev/$portInConfig", speed)
    }


    @Test
    fun testCompatibleOpenSerialPort_Success() {
        val mockContext: Context = mock()
        whenever(mockContext.checkSelfPermission(android.Manifest.permission.SERIAL_PORT))
            .thenReturn(PackageManager.PERMISSION_GRANTED)

        val packageName = context.packageName
        whenever(mockContext.packageName).thenReturn(packageName)

        val portInConfig = "ttyS0"
        val speed = 9600

        whenever(backendService.serialPortsInConfig).thenReturn(arrayOf(portInConfig))

        // These aren't real serial ports, but the legacy native_open() doesn't give us troubles if
        // any serial port specific operations fail.
        val pfds = ParcelFileDescriptor.createPipe()
        try {
            doAnswer { invocation ->
                val callback = invocation.getArgument<ISerialPortResponseCallback>(4)
                callback.onResult(
                    SerialPortInfo(
                        invocation.getArgument(0),
                        SerialPort.INVALID_ID,
                        SerialPort.INVALID_ID
                    ),
                    pfds[1]
                )
            }.whenever(backendService).requestOpen(
                    any(),
                    anyInt(),
                    anyBoolean(),
                    any(),
                    any()
                )

            val serialManager = SerialManager(mockContext, backendService)
            val port = serialManager.openSerialPort("/dev/$portInConfig", speed)

            verify(backendService).requestOpen(
                eq(portInConfig),
                eq(SerialPort.OPEN_FLAG_READ_WRITE),
                eq(false),
                eq(packageName),
                any()
            )

            val data = 9064
            val writeBuffer = ByteBuffer.allocate(4)
            writeBuffer.putInt(data)
            port.write(writeBuffer, 4)

            val readBuffer = ByteBuffer.allocate(4)
            ParcelFileDescriptor.AutoCloseInputStream(pfds[0]).use {
                stream -> stream.read(readBuffer.array())
            }
            assertEquals(data, readBuffer.getInt())
        } finally {
            pfds[0].close()
            pfds[1].close()
        }
    }

    private fun assertSerialPortAttributes(
        serialPort: SerialPort, name: String, vendorId: Int, productId: Int
    ) {
        assertEquals(serialPort.name, name)
        assertEquals(serialPort.vendorId, vendorId)
        assertEquals(serialPort.productId, productId)
    }
}