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

import android.annotation.UserIdInt
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.serial.SerialManager
import android.hardware.serial.flags.Flags
import android.os.Process
import android.os.UserHandle
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import android.platform.test.flag.junit.SetFlagsRule
import android.util.ArrayMap
import android.util.SparseBooleanArray
import java.util.concurrent.FutureTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

/**
 * Tests for [SerialUserAccessManager].
 *
 * atest SerialUserAccessManagerTest
 * atest SerialTests - to test whole module
 */
@RunWith(ParameterizedAndroidJunit4::class)
class SerialUserAccessManagerTest(val flags: FlagsParameterization) {
    @get:Rule
    val mockitoRule = MockitoJUnit.rule()

    @get:Rule
    val setFlagsRule = SetFlagsRule(flags)

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockSerializer: PortAccessSerializerInterface

    private var persistedSerialAccess = ArrayMap<String, SparseBooleanArray>()

    @Mock
    private lateinit var mockLoadTask: FutureTask<ArrayMap<String, SparseBooleanArray>>

    @Mock
    private lateinit var mockSaveTask: FutureTask<Void>

    @Before
    fun setUp() {
        doReturn(PackageManager.PERMISSION_DENIED).whenever(mockContext).checkPermission(
            eq(android.Manifest.permission.SERIAL_PORT),
            anyInt(),
            anyInt())

        if (Flags.persistentAccess()) {
            doReturn(persistedSerialAccess).whenever(mockLoadTask).get()
            doReturn(mockLoadTask).whenever(mockSerializer).loadPortAccessForUser(TEST_USER_ID)
            doReturn(mockSaveTask).whenever(mockSerializer)
                .savePortAccessForUser(eq(TEST_USER_ID), any())
        }
    }

    @Test
    fun testGrantAccessAutomaticallyToPortsInConfigWithSerialPortPermission() {
        reset(mockContext)
        doReturn(PackageManager.PERMISSION_GRANTED).whenever(mockContext).checkPermission(
            android.Manifest.permission.SERIAL_PORT,
            Process.myPid(),
            TEST_UID)

        val portsInConfig = arrayOf(TEST_PORT)

        val accessManager =
            SerialUserAccessManager(mockContext, portsInConfig, "", mockSerializer, TEST_USER_ID)

        var callbackCalled = false
        accessManager.requestAccess(
            TEST_PORT,
            Process.myPid(),
            TEST_UID,
            "package_name"
        ) { resultPort, pid, uid, granted ->
            assertFalse(callbackCalled)
            callbackCalled = true
            assertEquals(TEST_PORT, resultPort)
            assertEquals(Process.myPid(), pid)
            assertEquals(TEST_UID, uid)
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
            TEST_UID)

        val portsInConfig = arrayOf("ttyS0")
        val port = "ttyS1"
        val accessManager =
            SerialUserAccessManager(mockContext, portsInConfig, "", mockSerializer, TEST_USER_ID)
        doAnswer { invocation ->
            val intent = invocation.getArgument<Intent>(0)
            val token = intent.extras?.getBinder(SerialManager.EXTRA_REQUEST_TOKEN)
            accessManager.revokeAccess(port, TEST_UID, false, token)
        }.whenever(mockContext).startActivityAsUser(any(), any())

        var callbackCalled = false
        accessManager.requestAccess(
            port,
            Process.myPid(),
            TEST_UID,
            "package_name"
        ) { resultPort, pid, uid, granted ->
            assertFalse(callbackCalled)
            callbackCalled = true
            assertEquals(port, resultPort)
            assertEquals(Process.myPid(), pid)
            assertEquals(TEST_UID, uid)
            assertFalse(granted)
        }
        assertTrue(callbackCalled)
    }

    @Test
    fun testDenyAccessToPortsInConfigWithoutSerialPortPermission() {
        val portsInConfig = arrayOf(TEST_PORT)
        val accessManager =
            SerialUserAccessManager(mockContext, portsInConfig, "", mockSerializer, TEST_USER_ID)
        doAnswer { invocation ->
            val intent = invocation.getArgument<Intent>(0)
            val token = intent.extras?.getBinder(SerialManager.EXTRA_REQUEST_TOKEN)
            accessManager.revokeAccess(TEST_PORT, TEST_UID, false, token)
        }.whenever(mockContext).startActivityAsUser(any(), any())

        var callbackCalled = false
        accessManager.requestAccess(
            TEST_PORT,
            Process.myPid(),
            TEST_UID,
            "package_name"
        ) { resultPort, pid, uid, granted ->
            assertFalse(callbackCalled)
            callbackCalled = true
            assertEquals(TEST_PORT, resultPort)
            assertEquals(Process.myPid(), pid)
            assertEquals(TEST_UID, uid)
            assertFalse(granted)
        }
        assertTrue(callbackCalled)
    }

    @Test
    fun testGrantAccessByUser() {
        val accessManager =
            SerialUserAccessManager(mockContext, arrayOf(), "", mockSerializer, TEST_USER_ID)
        doAnswer { invocation ->
            val intent = invocation.getArgument<Intent>(0)
            val token = intent.extras?.getBinder(SerialManager.EXTRA_REQUEST_TOKEN)
            accessManager.grantAccess(TEST_PORT, TEST_UID, false, token)
        }.whenever(mockContext).startActivityAsUser(any(), any())

        var callbackCalled = false
        accessManager.requestAccess(
            TEST_PORT,
            Process.myPid(),
            TEST_UID,
            "package_name"
        ) { resultPort, pid, uid, granted ->
            assertFalse(callbackCalled)
            callbackCalled = true
            assertEquals(TEST_PORT, resultPort)
            assertEquals(Process.myPid(), pid)
            assertEquals(TEST_UID, uid)
            assertTrue(granted)
        }
        assertTrue(callbackCalled)

        verify(mockSerializer, never()).savePortAccessForUser(any(), any())
    }

    @Test
    fun testAutomaticGrantAfterGrantedAccessByUser() {
        val accessManager =
            SerialUserAccessManager(mockContext, arrayOf(), "", mockSerializer, TEST_USER_ID)
        doAnswer { invocation ->
            val intent = invocation.getArgument<Intent>(0)
            val token = intent.extras?.getBinder(SerialManager.EXTRA_REQUEST_TOKEN)
            accessManager.grantAccess(TEST_PORT, TEST_UID, false, token)
        }.whenever(mockContext).startActivityAsUser(any(), any())

        accessManager.requestAccess(
            TEST_PORT,
            Process.myPid(),
            TEST_UID,
            "package_name"
        ) { resultPort, pid, uid, granted ->
            assertTrue(granted)
        }

        var callbackCalled = false
        accessManager.requestAccess(
            TEST_PORT,
            Process.myPid(),
            TEST_UID,
            "package_name"
        ) { resultPort, pid, uid, granted ->
            assertFalse(callbackCalled)
            callbackCalled = true
            assertEquals(TEST_PORT, resultPort)
            assertEquals(Process.myPid(), pid)
            assertEquals(TEST_UID, uid)
            assertTrue(granted)
        }
        assertTrue(callbackCalled)

        verify(mockContext).startActivityAsUser(any(), eq(UserHandle.of(TEST_USER_ID)))
    }

    @Test
    fun testGrantExpiresOnUnplug() {
        val accessManager =
            SerialUserAccessManager(mockContext, arrayOf(), "", mockSerializer, TEST_USER_ID)
        doAnswer { invocation ->
            val intent = invocation.getArgument<Intent>(0)
            val token = intent.extras?.getBinder(SerialManager.EXTRA_REQUEST_TOKEN)
            accessManager.grantAccess(TEST_PORT, TEST_UID, false, token)
        }.whenever(mockContext).startActivityAsUser(any(), any())

        accessManager.requestAccess(
            TEST_PORT,
            Process.myPid(),
            TEST_UID,
            "package_name"
        ) { resultPort, pid, uid, granted ->
            assertTrue(granted)
        }

        accessManager.onPortRemoved(TEST_PORT)

        var callbackCalled = false
        accessManager.requestAccess(
            TEST_PORT,
            Process.myPid(),
            TEST_UID,
            "package_name"
        ) { resultPort, pid, uid, granted ->
            assertFalse(callbackCalled)
            callbackCalled = true
            assertEquals(TEST_PORT, resultPort)
            assertEquals(Process.myPid(), pid)
            assertEquals(TEST_UID, uid)
            assertTrue(granted)
        }
        assertTrue(callbackCalled)

        verify(mockContext, times(2)).startActivityAsUser(any(), eq(UserHandle.of(TEST_USER_ID)))
    }

    @Test
    fun testAccessDialogFailure() {
        val accessManager =
            SerialUserAccessManager(mockContext, arrayOf(), "", mockSerializer, TEST_USER_ID)
        doThrow(RuntimeException()).whenever(mockContext).startActivityAsUser(any(), any())

        var callbackCalled = false
        accessManager.requestAccess(
            TEST_PORT,
            Process.myPid(),
            TEST_UID,
            "package_name"
        ) { resultPort, pid, uid, granted ->
            assertFalse(callbackCalled)
            callbackCalled = true
            assertEquals(TEST_PORT, resultPort)
            assertEquals(Process.myPid(), pid)
            assertEquals(TEST_UID, uid)
            assertFalse(granted)
        }
        assertTrue(callbackCalled)

        verify(mockSerializer, never()).savePortAccessForUser(any(), any())
    }

    @Test
    fun testDenyAccessByUser() {
        val accessManager =
            SerialUserAccessManager(mockContext, arrayOf(), "", mockSerializer, TEST_USER_ID)
        doAnswer { invocation ->
            val intent = invocation.getArgument<Intent>(0)
            val token = intent.extras?.getBinder(SerialManager.EXTRA_REQUEST_TOKEN)
            accessManager.revokeAccess(TEST_PORT, TEST_UID, false, token)
        }.whenever(mockContext).startActivityAsUser(any(), any())

        var callbackCalled = false
        accessManager.requestAccess(
            TEST_PORT,
            Process.myPid(),
            TEST_UID,
            "package_name"
        ) { resultPort, pid, uid, granted ->
            assertFalse(callbackCalled)
            callbackCalled = true
            assertEquals(TEST_PORT, resultPort)
            assertEquals(Process.myPid(), pid)
            assertEquals(TEST_UID, uid)
            assertFalse(granted)
        }
        assertTrue(callbackCalled)

        verify(mockSerializer, never()).savePortAccessForUser(any(), any())
    }

    @Test
    fun testRequestAgainAfterDeniedByUser() {
        val accessManager =
            SerialUserAccessManager(mockContext, arrayOf(), "", mockSerializer, TEST_USER_ID)
        doAnswer { invocation ->
            val intent = invocation.getArgument<Intent>(0)
            val token = intent.extras?.getBinder(SerialManager.EXTRA_REQUEST_TOKEN)
            accessManager.revokeAccess(TEST_PORT, TEST_UID, false, token)
        }.whenever(mockContext).startActivityAsUser(any(), any())

        accessManager.requestAccess(
            TEST_PORT,
            Process.myPid(),
            TEST_UID,
            "package_name"
        ) { resultPort, pid, uid, granted ->
            assertFalse(granted)
        }

        var callbackCalled = false
        accessManager.requestAccess(
            TEST_PORT,
            Process.myPid(),
            TEST_UID,
            "package_name"
        ) { resultPort, pid, uid, granted ->
            assertFalse(callbackCalled)
            callbackCalled = true
            assertEquals(TEST_PORT, resultPort)
            assertEquals(Process.myPid(), pid)
            assertEquals(TEST_UID, uid)
            assertFalse(granted)
        }
        assertTrue(callbackCalled)

        verify(mockContext, times(2)).startActivityAsUser(any(), eq(UserHandle.of(TEST_USER_ID)))

        verify(mockSerializer, never()).savePortAccessForUser(any(), any())
    }

    @EnableFlags(Flags.FLAG_PERSISTENT_ACCESS)
    @Test
    fun testDenyAccessAutomaticallyWithPersistentDenial() {
        persistedSerialAccess.put(TEST_PORT, SparseBooleanArray())
        persistedSerialAccess.get(TEST_PORT)!!.put(TEST_UID, false)

        val accessManager =
            SerialUserAccessManager(mockContext, arrayOf(), "", mockSerializer, TEST_USER_ID)
        doAnswer { invocation ->
            val intent = invocation.getArgument<Intent>(0)
            val token = intent.extras?.getBinder(SerialManager.EXTRA_REQUEST_TOKEN)
            accessManager.grantAccess(TEST_PORT, TEST_UID, false, token)
        }.whenever(mockContext).startActivityAsUser(any(), any())

        var callbackCalled = false
        accessManager.requestAccess(
            TEST_PORT,
            Process.myPid(),
            TEST_UID,
            "package_name"
        ) { resultPort, pid, uid, granted ->
            assertFalse(callbackCalled)
            callbackCalled = true
            assertEquals(TEST_PORT, resultPort)
            assertEquals(Process.myPid(), pid)
            assertEquals(TEST_UID, uid)
            assertFalse(granted)
        }
        assertTrue(callbackCalled)

        verify(mockSerializer, never()).savePortAccessForUser(any(), any())
    }

    @EnableFlags(Flags.FLAG_PERSISTENT_ACCESS)
    @Test
    fun testGrantAutomaticallyWithPersistentGrant() {
        persistedSerialAccess.put(TEST_PORT, SparseBooleanArray())
        persistedSerialAccess.get(TEST_PORT)!!.put(TEST_UID, true)

        val accessManager =
            SerialUserAccessManager(mockContext, arrayOf(), "", mockSerializer, TEST_USER_ID)
        doAnswer { invocation ->
            val intent = invocation.getArgument<Intent>(0)
            val token = intent.extras?.getBinder(SerialManager.EXTRA_REQUEST_TOKEN)
            accessManager.revokeAccess(TEST_PORT, TEST_UID, false, token)
        }.whenever(mockContext).startActivityAsUser(any(), any())

        var callbackCalled = false
        accessManager.requestAccess(
            TEST_PORT,
            Process.myPid(),
            TEST_UID,
            "package_name"
        ) { resultPort, pid, uid, granted ->
            assertFalse(callbackCalled)
            callbackCalled = true
            assertEquals(TEST_PORT, resultPort)
            assertEquals(Process.myPid(), pid)
            assertEquals(TEST_UID, uid)
            assertTrue(granted)
        }
        assertTrue(callbackCalled)

        verify(mockSerializer, never()).savePortAccessForUser(any(), any())
    }

    @EnableFlags(Flags.FLAG_PERSISTENT_ACCESS)
    @Test
    fun testDenyAccessPersistentlyByUser() {
        val accessManager =
            SerialUserAccessManager(mockContext, arrayOf(), "", mockSerializer, TEST_USER_ID)
        doAnswer { invocation ->
            val intent = invocation.getArgument<Intent>(0)
            val token = intent.extras?.getBinder(SerialManager.EXTRA_REQUEST_TOKEN)
            accessManager.revokeAccess(TEST_PORT, TEST_UID, true, token)
        }.whenever(mockContext).startActivityAsUser(any(), any())

        var callbackCalled = false
        accessManager.requestAccess(
            TEST_PORT,
            Process.myPid(),
            TEST_UID,
            "package_name"
        ) { resultPort, pid, uid, granted ->
            assertFalse(callbackCalled)
            callbackCalled = true
            assertEquals(TEST_PORT, resultPort)
            assertEquals(Process.myPid(), pid)
            assertEquals(TEST_UID, uid)
            assertFalse(granted)
        }
        assertTrue(callbackCalled)

        @Suppress("UNCHECKED_CAST")
        val accessMapCaptor = ArgumentCaptor.forClass(ArrayMap::class.java)
                as ArgumentCaptor<ArrayMap<String, SparseBooleanArray>>
        verify(mockSerializer).savePortAccessForUser(eq(TEST_USER_ID), accessMapCaptor.capture())
        assertTrue(accessMapCaptor.value.get(TEST_PORT)!!.indexOfKey(TEST_UID) >= 0)
        assertFalse(accessMapCaptor.value.get(TEST_PORT)!!.get(TEST_UID))
    }

    @EnableFlags(Flags.FLAG_PERSISTENT_ACCESS)
    @Test
    fun testDenyAutomaticallyAfterPersistentlyDeniedByUser() {
        val accessManager =
            SerialUserAccessManager(mockContext, arrayOf(), "", mockSerializer, TEST_USER_ID)
        doAnswer { invocation ->
            val intent = invocation.getArgument<Intent>(0)
            val token = intent.extras?.getBinder(SerialManager.EXTRA_REQUEST_TOKEN)
            accessManager.revokeAccess(TEST_PORT, TEST_UID, true, token)
        }.whenever(mockContext).startActivityAsUser(any(), any())

        accessManager.requestAccess(
            TEST_PORT,
            Process.myPid(),
            TEST_UID,
            "package_name"
        ) { resultPort, pid, uid, granted ->
            assertFalse(granted)
        }

        var callbackCalled = false
        accessManager.requestAccess(
            TEST_PORT,
            Process.myPid(),
            TEST_UID,
            "package_name"
        ) { resultPort, pid, uid, granted ->
            assertFalse(callbackCalled)
            callbackCalled = true
            assertEquals(TEST_PORT, resultPort)
            assertEquals(Process.myPid(), pid)
            assertEquals(TEST_UID, uid)
            assertFalse(granted)
        }
        assertTrue(callbackCalled)

        verify(mockContext).startActivityAsUser(any(), eq(UserHandle.of(TEST_USER_ID)))

        @Suppress("UNCHECKED_CAST")
        val accessMapCaptor = ArgumentCaptor.forClass(ArrayMap::class.java)
                as ArgumentCaptor<ArrayMap<String, SparseBooleanArray>>
        verify(mockSerializer).savePortAccessForUser(eq(TEST_USER_ID), accessMapCaptor.capture())
        assertTrue(accessMapCaptor.value.get(TEST_PORT)!!.indexOfKey(TEST_UID) >= 0)
        assertFalse(accessMapCaptor.value.get(TEST_PORT)!!.get(TEST_UID))
    }

    @EnableFlags(Flags.FLAG_PERSISTENT_ACCESS)
    @Test
    fun testGrantAccessPermanentlyByUser() {
        val accessManager =
            SerialUserAccessManager(mockContext, arrayOf(), "", mockSerializer, TEST_USER_ID)
        doAnswer { invocation ->
            val intent = invocation.getArgument<Intent>(0)
            val token = intent.extras?.getBinder(SerialManager.EXTRA_REQUEST_TOKEN)
            accessManager.grantAccess(TEST_PORT, TEST_UID, true, token)
        }.whenever(mockContext).startActivityAsUser(any(), any())

        var callbackCalled = false
        accessManager.requestAccess(
            TEST_PORT,
            Process.myPid(),
            TEST_UID,
            "package_name"
        ) { resultPort, pid, uid, granted ->
            assertFalse(callbackCalled)
            callbackCalled = true
            assertEquals(TEST_PORT, resultPort)
            assertEquals(Process.myPid(), pid)
            assertEquals(TEST_UID, uid)
            assertTrue(granted)
        }
        assertTrue(callbackCalled)

        @Suppress("UNCHECKED_CAST")
        val accessMapCaptor = ArgumentCaptor.forClass(ArrayMap::class.java)
                as ArgumentCaptor<ArrayMap<String, SparseBooleanArray>>
        verify(mockSerializer).savePortAccessForUser(eq(TEST_USER_ID), accessMapCaptor.capture())
        assertTrue(accessMapCaptor.value.get(TEST_PORT)!!.indexOfKey(TEST_UID) >= 0)
        assertTrue(accessMapCaptor.value.get(TEST_PORT)!!.get(TEST_UID))
    }

    @EnableFlags(Flags.FLAG_PERSISTENT_ACCESS)
    @Test
    fun testAutomaticGrantAfterPermanentlyGrantedAccessByUser() {
        val accessManager =
            SerialUserAccessManager(mockContext, arrayOf(), "", mockSerializer, TEST_USER_ID)
        doAnswer { invocation ->
            val intent = invocation.getArgument<Intent>(0)
            val token = intent.extras?.getBinder(SerialManager.EXTRA_REQUEST_TOKEN)
            accessManager.grantAccess(TEST_PORT, TEST_UID, true, token)
        }.whenever(mockContext).startActivityAsUser(any(), any())

        accessManager.requestAccess(
            TEST_PORT,
            Process.myPid(),
            TEST_UID,
            "package_name"
        ) { resultPort, pid, uid, granted ->
            assertTrue(granted)
        }

        var callbackCalled = false
        accessManager.requestAccess(
            TEST_PORT,
            Process.myPid(),
            TEST_UID,
            "package_name"
        ) { resultPort, pid, uid, granted ->
            assertFalse(callbackCalled)
            callbackCalled = true
            assertEquals(TEST_PORT, resultPort)
            assertEquals(Process.myPid(), pid)
            assertEquals(TEST_UID, uid)
            assertTrue(granted)
        }
        assertTrue(callbackCalled)

        verify(mockContext).startActivityAsUser(any(), eq(UserHandle.of(TEST_USER_ID)))

        @Suppress("UNCHECKED_CAST")
        val accessMapCaptor = ArgumentCaptor.forClass(ArrayMap::class.java)
                as ArgumentCaptor<ArrayMap<String, SparseBooleanArray>>
        verify(mockSerializer).savePortAccessForUser(eq(TEST_USER_ID), accessMapCaptor.capture())
        assertTrue(accessMapCaptor.value.get(TEST_PORT)!!.indexOfKey(TEST_UID) >= 0)
        assertTrue(accessMapCaptor.value.get(TEST_PORT)!!.get(TEST_UID))
    }

    @EnableFlags(Flags.FLAG_PERSISTENT_ACCESS)
    @Test
    fun testPermanentGrantNotExpiresOnUnplug() {
        val accessManager =
            SerialUserAccessManager(mockContext, arrayOf(), "", mockSerializer, TEST_USER_ID)
        doAnswer { invocation ->
            val intent = invocation.getArgument<Intent>(0)
            val token = intent.extras?.getBinder(SerialManager.EXTRA_REQUEST_TOKEN)
            accessManager.grantAccess(TEST_PORT, TEST_UID, true, token)
        }.whenever(mockContext).startActivityAsUser(any(), any())

        accessManager.requestAccess(
            TEST_PORT,
            Process.myPid(),
            TEST_UID,
            "package_name"
        ) { resultPort, pid, uid, granted ->
            assertTrue(granted)
        }

        accessManager.onPortRemoved(TEST_PORT)

        var callbackCalled = false
        accessManager.requestAccess(
            TEST_PORT,
            Process.myPid(),
            TEST_UID,
            "package_name"
        ) { resultPort, pid, uid, granted ->
            assertFalse(callbackCalled)
            callbackCalled = true
            assertEquals(TEST_PORT, resultPort)
            assertEquals(Process.myPid(), pid)
            assertEquals(TEST_UID, uid)
            assertTrue(granted)
        }
        assertTrue(callbackCalled)

        verify(mockContext).startActivityAsUser(any(), eq(UserHandle.of(TEST_USER_ID)))

        @Suppress("UNCHECKED_CAST")
        val accessMapCaptor = ArgumentCaptor.forClass(ArrayMap::class.java)
                as ArgumentCaptor<ArrayMap<String, SparseBooleanArray>>
        verify(mockSerializer).savePortAccessForUser(eq(TEST_USER_ID), accessMapCaptor.capture())
        assertTrue(accessMapCaptor.value.get(TEST_PORT)!!.indexOfKey(TEST_UID) >= 0)
        assertTrue(accessMapCaptor.value.get(TEST_PORT)!!.get(TEST_UID))
    }

    @EnableFlags(Flags.FLAG_PERSISTENT_ACCESS)
    @Test
    fun testPermanentDenialNotExpiresOnUnplug() {
        val accessManager =
            SerialUserAccessManager(mockContext, arrayOf(), "", mockSerializer, TEST_USER_ID)
        doAnswer { invocation ->
            val intent = invocation.getArgument<Intent>(0)
            val token = intent.extras?.getBinder(SerialManager.EXTRA_REQUEST_TOKEN)
            accessManager.revokeAccess(TEST_PORT, TEST_UID, true, token)
        }.whenever(mockContext).startActivityAsUser(any(), any())

        accessManager.requestAccess(
            TEST_PORT,
            Process.myPid(),
            TEST_UID,
            "package_name"
        ) { resultPort, pid, uid, granted ->
            assertFalse(granted)
        }

        accessManager.onPortRemoved(TEST_PORT)

        var callbackCalled = false
        accessManager.requestAccess(
            TEST_PORT,
            Process.myPid(),
            TEST_UID,
            "package_name"
        ) { resultPort, pid, uid, granted ->
            assertFalse(callbackCalled)
            callbackCalled = true
            assertEquals(TEST_PORT, resultPort)
            assertEquals(Process.myPid(), pid)
            assertEquals(TEST_UID, uid)
            assertFalse(granted)
        }
        assertTrue(callbackCalled)

        verify(mockContext).startActivityAsUser(any(), eq(UserHandle.of(TEST_USER_ID)))

        @Suppress("UNCHECKED_CAST")
        val accessMapCaptor2 = ArgumentCaptor.forClass(ArrayMap::class.java)
                as ArgumentCaptor<ArrayMap<String, SparseBooleanArray>>
        verify(mockSerializer).savePortAccessForUser(eq(TEST_USER_ID), accessMapCaptor2.capture())
        assertTrue(accessMapCaptor2.value.get(TEST_PORT)!!.indexOfKey(TEST_UID) >= 0)
        assertFalse(accessMapCaptor2.value.get(TEST_PORT)!!.get(TEST_UID))
    }

    @EnableFlags(Flags.FLAG_PERSISTENT_ACCESS)
    @Test
    fun testPermanentGrantAfterPermanentDenial() {
        val accessManager =
            SerialUserAccessManager(mockContext, arrayOf(), "", mockSerializer, TEST_USER_ID)
        doAnswer { invocation ->
            val intent = invocation.getArgument<Intent>(0)
            val token = intent.extras?.getBinder(SerialManager.EXTRA_REQUEST_TOKEN)
            accessManager.revokeAccess(TEST_PORT, TEST_UID, true, token)
        }.whenever(mockContext).startActivityAsUser(any(), any())

        accessManager.requestAccess(
            TEST_PORT,
            Process.myPid(),
            TEST_UID,
            "package_name"
        ) { resultPort, pid, uid, granted ->
            assertFalse(granted)
        }

        @Suppress("UNCHECKED_CAST")
        val accessMapCaptor = ArgumentCaptor.forClass(ArrayMap::class.java)
                as ArgumentCaptor<ArrayMap<String, SparseBooleanArray>>
        verify(mockSerializer).savePortAccessForUser(eq(TEST_USER_ID), accessMapCaptor.capture())
        assertTrue(accessMapCaptor.value.get(TEST_PORT)!!.indexOfKey(TEST_UID) >= 0)
        assertFalse(accessMapCaptor.value.get(TEST_PORT)!!.get(TEST_UID))

        reset(mockSerializer)

        accessManager.grantAccess(TEST_PORT, TEST_UID, true, null)

        accessManager.requestAccess(
            TEST_PORT,
            Process.myPid(),
            TEST_UID,
            "package_name"
        ) { resultPort, pid, uid, granted ->
            assertTrue(granted)
        }

        @Suppress("UNCHECKED_CAST")
        val accessMapCaptor2 = ArgumentCaptor.forClass(ArrayMap::class.java)
                as ArgumentCaptor<ArrayMap<String, SparseBooleanArray>>
        verify(mockSerializer).savePortAccessForUser(eq(TEST_USER_ID), accessMapCaptor2.capture())
        assertTrue(accessMapCaptor2.value.get(TEST_PORT)!!.indexOfKey(TEST_UID) >= 0)
        assertTrue(accessMapCaptor2.value.get(TEST_PORT)!!.get(TEST_UID))
    }

    @EnableFlags(Flags.FLAG_PERSISTENT_ACCESS)
    @Test
    fun testTemporaryGrantAfterPermanentDenial() {
        val accessManager =
            SerialUserAccessManager(mockContext, arrayOf(), "", mockSerializer, TEST_USER_ID)
        doAnswer { invocation ->
            val intent = invocation.getArgument<Intent>(0)
            val token = intent.extras?.getBinder(SerialManager.EXTRA_REQUEST_TOKEN)
            accessManager.revokeAccess(TEST_PORT, TEST_UID, true, token)
        }.whenever(mockContext).startActivityAsUser(any(), any())

        accessManager.requestAccess(
            TEST_PORT,
            Process.myPid(),
            TEST_UID,
            "package_name"
        ) { resultPort, pid, uid, granted ->
            assertFalse(granted)
        }

        @Suppress("UNCHECKED_CAST")
        val accessMapCaptor = ArgumentCaptor.forClass(ArrayMap::class.java)
                as ArgumentCaptor<ArrayMap<String, SparseBooleanArray>>
        verify(mockSerializer).savePortAccessForUser(eq(TEST_USER_ID), accessMapCaptor.capture())
        assertTrue(accessMapCaptor.value.get(TEST_PORT)!!.indexOfKey(TEST_UID) >= 0)
        assertFalse(accessMapCaptor.value.get(TEST_PORT)!!.get(TEST_UID))

        reset(mockSerializer)

        accessManager.grantAccess(TEST_PORT, TEST_UID, false, null)

        accessManager.requestAccess(
            TEST_PORT,
            Process.myPid(),
            TEST_UID,
            "package_name"
        ) { resultPort, pid, uid, granted ->
            assertTrue(granted)
        }

        @Suppress("UNCHECKED_CAST")
        val accessMapCaptor2 = ArgumentCaptor.forClass(ArrayMap::class.java)
                as ArgumentCaptor<ArrayMap<String, SparseBooleanArray>>
        verify(mockSerializer).savePortAccessForUser(eq(TEST_USER_ID), accessMapCaptor2.capture())
        assertTrue(accessMapCaptor2.value.get(TEST_PORT)!!.indexOfKey(TEST_UID) < 0)
    }

    @EnableFlags(Flags.FLAG_PERSISTENT_ACCESS)
    @Test
    fun testClearUserAccess() {
        persistedSerialAccess.put(TEST_PORT, SparseBooleanArray())
        persistedSerialAccess.get(TEST_PORT)!!.put(TEST_UID, true)

        val accessManager =
            SerialUserAccessManager(mockContext, arrayOf(), "", mockSerializer, TEST_USER_ID)
        doAnswer { invocation ->
            val intent = invocation.getArgument<Intent>(0)
            val token = intent.extras?.getBinder(SerialManager.EXTRA_REQUEST_TOKEN)
            accessManager.grantAccess(TEST_PORT, TEST_UID, false, token)
        }.whenever(mockContext).startActivityAsUser(any(), any())

        accessManager.clearUserAccess(TEST_USER_ID)
        @Suppress("UNCHECKED_CAST")
        val accessMapCaptor2 = ArgumentCaptor.forClass(ArrayMap::class.java)
                as ArgumentCaptor<ArrayMap<String, SparseBooleanArray>>
        verify(mockSerializer).savePortAccessForUser(eq(TEST_USER_ID), accessMapCaptor2.capture())
        assertNull(accessMapCaptor2.value.get(TEST_PORT))

        var callbackCalled = false
        accessManager.requestAccess(
            TEST_PORT,
            Process.myPid(),
            TEST_UID,
            "package_name"
        ) { resultPort, pid, uid, granted ->
            assertFalse(callbackCalled)
            callbackCalled = true
            assertEquals(TEST_PORT, resultPort)
            assertEquals(Process.myPid(), pid)
            assertEquals(TEST_UID, uid)
            assertTrue(granted)
        }
        assertTrue(callbackCalled)

        verify(mockContext).startActivityAsUser(any(), eq(UserHandle.of(TEST_USER_ID)))
    }

    companion object {
        const val TEST_PORT = "ttyS0"
        @UserIdInt
        const val TEST_USER_ID = 64
        val TEST_UID = UserHandle.getUid(TEST_USER_ID, UserHandle.getAppId(Process.myUid()))

        @get:JvmStatic
        @get:Parameters(name = "{0}")
        val params = FlagsParameterization.allCombinationsOf(Flags.FLAG_PERSISTENT_ACCESS)
    }
}