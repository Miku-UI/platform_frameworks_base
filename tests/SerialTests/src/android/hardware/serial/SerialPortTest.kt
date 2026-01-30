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

import android.os.OutcomeReceiver
import android.os.ParcelFileDescriptor
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/**
 * Tests for SerialPort.
 *
 * atest SerialTests:SerialPortTest
 */
@RunWith(AndroidJUnit4::class)
@EnableFlags(android.hardware.serial.flags.Flags.FLAG_ENABLE_WIRED_SERIAL_API)
class SerialPortTest {
    @get:Rule
    val mockitoRule = MockitoJUnit.rule()

    @Mock
    private lateinit var backendService: ISerialManager

    @Captor
    private lateinit var responseCallback: ArgumentCaptor<ISerialPortResponseCallback.Stub>

    private val context = InstrumentationRegistry.getInstrumentation().context
    private val info = SerialPortInfo("ttyUSB0", 1234, 5678)

    @Test
    fun testAttributes() {
        val serialPort = SerialPort(context, info, backendService)

        assertEquals(serialPort.name, "ttyUSB0")
        assertEquals(serialPort.vendorId, 1234)
        assertEquals(serialPort.productId, 5678)
    }

    @Test
    fun testRequestOpen_success() {
        val serialPort = SerialPort(context, info, backendService)
        val flags = SerialPort.OPEN_FLAG_NONBLOCK
        val exclusive = true
        val executor = Executor { r -> r.run() }
        var outcomeResult: SerialPortResponse? = null
        var outcomeError: Exception? = null
        val outcomeReceiver = object : OutcomeReceiver<SerialPortResponse, Exception> {
            override fun onResult(result: SerialPortResponse) {
                outcomeResult = result
            }

            override fun onError(error: Exception) {
                outcomeError = error
            }
        }
        val pfd: ParcelFileDescriptor = mock()

        serialPort.requestOpen(flags, exclusive, executor, outcomeReceiver)
        verify(backendService).requestOpen(eq("ttyUSB0"), eq(flags), eq(exclusive),
            eq(context.packageName), responseCallback.capture())
        responseCallback.value.onResult(info, pfd)

        assertEquals(outcomeResult?.port, serialPort)
        assertEquals(outcomeResult?.fileDescriptor, pfd)
        assertNull(outcomeError)
    }

    @Test
    fun testRequestOpen_accessDenied() {
        val serialPort = SerialPort(context, info, backendService)
        val flags = SerialPort.OPEN_FLAG_READ_WRITE or SerialPort.OPEN_FLAG_NONBLOCK
        val exclusive = true
        val executor = Executor { r -> r.run() }
        var outcomeResult: SerialPortResponse? = null
        var outcomeError: Exception? = null
        val outcomeReceiver = object : OutcomeReceiver<SerialPortResponse, Exception> {
            override fun onResult(result: SerialPortResponse) {
                outcomeResult = result
            }

            override fun onError(error: Exception) {
                outcomeError = error
            }
        }

        serialPort.requestOpen(flags, exclusive, executor, outcomeReceiver)
        verify(backendService).requestOpen(eq("ttyUSB0"), eq(flags), eq(exclusive),
            eq(context.packageName), responseCallback.capture())
        responseCallback.value.onError(
            ISerialPortResponseCallback.ErrorCode.ERROR_ACCESS_DENIED, "Access denied"
        )

        assertNull(outcomeResult)
        assertEquals(outcomeError!!::class, SecurityException::class)
        assertEquals(outcomeError.message, "Access denied")
    }

    @Test
    fun testRequestOpen_error() {
        val serialPort = SerialPort(context, info, backendService)
        val flags = SerialPort.OPEN_FLAG_READ_WRITE or SerialPort.OPEN_FLAG_NONBLOCK
        val exclusive = false
        val executor = Executor { r -> r.run() }
        var outcomeResult: SerialPortResponse? = null
        var outcomeError: Exception? = null
        val outcomeReceiver = object : OutcomeReceiver<SerialPortResponse, Exception> {
            override fun onResult(result: SerialPortResponse) {
                outcomeResult = result
            }

            override fun onError(error: Exception) {
                outcomeError = error
            }
        }

        serialPort.requestOpen(flags, exclusive, executor, outcomeReceiver)
        verify(backendService).requestOpen(eq("ttyUSB0"), eq(flags), eq(exclusive),
            eq(context.packageName), responseCallback.capture())
        responseCallback.value.onError(
            ISerialPortResponseCallback.ErrorCode.ERROR_OPENING_PORT, "Test Error"
        )

        assertNull(outcomeResult)
        assertEquals(outcomeError!!::class, IOException::class)
        assertEquals(outcomeError.message, "Test Error")
    }
}