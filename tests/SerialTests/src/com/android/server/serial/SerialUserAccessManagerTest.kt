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
import android.content.pm.PackageManager
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever

/**
 * Tests for [SerialUserAccessManager].
 *
 * atest SerialUserAccessManagerTest
 * atest SerialTests - to test whole module
 */
@RunWith(AndroidJUnit4::class)
class SerialUserAccessManagerTest {
    @get:Rule
    val mockitoRule = MockitoJUnit.rule()

    @Mock
    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        doReturn(PackageManager.PERMISSION_DENIED).whenever(mockContext).checkPermission(
            eq(android.Manifest.permission.SERIAL_PORT),
            anyInt(),
            anyInt())
    }

    @Test
    fun testGrantAccessAutomaticallyToPortsInConfigWithSerialPortPermission() {
        reset(mockContext)
        doReturn(PackageManager.PERMISSION_GRANTED).whenever(mockContext).checkPermission(
            android.Manifest.permission.SERIAL_PORT,
            Process.myPid(),
            Process.FIRST_APPLICATION_UID)

        val port = "ttyS0"
        val portsInConfig = arrayOf(port)

        val accessManager = SerialUserAccessManager(mockContext, portsInConfig)

        var callbackCalled = false
        accessManager.requestAccess(port, Process.myPid(), Process.FIRST_APPLICATION_UID)
        { resultPort, pid, uid, granted ->
            assertFalse(callbackCalled)
            callbackCalled = true
            assertEquals(port, resultPort)
            assertEquals(Process.myPid(), pid)
            assertEquals(Process.FIRST_APPLICATION_UID, uid)
            assertTrue(granted)
        }
        assertTrue(callbackCalled)
    }

    @Test
    fun testDenyAccessToPortsNotInConfigWithSerialPortPermission() {
        reset(mockContext)
        doReturn(PackageManager.PERMISSION_GRANTED).whenever(mockContext).checkPermission(
            android.Manifest.permission.SERIAL_PORT,
            Process.myPid(),
            Process.FIRST_APPLICATION_UID)

        val portsInConfig = arrayOf("ttyS0")

        val accessManager = SerialUserAccessManager(mockContext, portsInConfig)

        val port = "ttyS1"
        var callbackCalled = false
        accessManager.requestAccess(port, Process.myPid(), Process.FIRST_APPLICATION_UID)
        { resultPort, pid, uid, granted ->
            assertFalse(callbackCalled)
            callbackCalled = true
            assertEquals(port, resultPort)
            assertEquals(Process.myPid(), pid)
            assertEquals(Process.FIRST_APPLICATION_UID, uid)
            assertFalse(granted)
        }
        assertTrue(callbackCalled)
    }

    @Test
    fun testDenyAccessToPortsInConfigWithoutSerialPortPermission() {
        val port = "ttyS0"
        val portsInConfig = arrayOf(port)

        val accessManager = SerialUserAccessManager(mockContext, portsInConfig)

        var callbackCalled = false
        accessManager.requestAccess(port, Process.myPid(), Process.FIRST_APPLICATION_UID)
        { resultPort, pid, uid, granted ->
            assertFalse(callbackCalled)
            callbackCalled = true
            assertEquals(port, resultPort)
            assertEquals(Process.myPid(), pid)
            assertEquals(Process.FIRST_APPLICATION_UID, uid)
            assertFalse(granted)
        }
        assertTrue(callbackCalled)
    }
}