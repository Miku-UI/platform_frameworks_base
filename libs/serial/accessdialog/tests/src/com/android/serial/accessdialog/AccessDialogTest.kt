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

package com.android.serial.accessdialog

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.serial.ISerialManager
import android.hardware.serial.SerialManager
import android.hardware.serial.SerialPort
import android.hardware.serial.SerialPortInfo
import android.hardware.serial.SerialPortListener
import android.os.Binder
import android.os.Bundle
import android.os.Process
import androidx.lifecycle.Lifecycle.State
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for access request dialog for serial ports.
 *
 * atest SerialPortAccessDialogTests
 */
@RunWith(AndroidJUnit4::class)
class AccessDialogTest {
    @get:Rule val mockitoRule = MockitoJUnit.rule()

    private lateinit var mDevice: UiDevice

    @Mock
    val mApplicationInfo: ApplicationInfo? = null

    val mToken = Binder()

    class InstrumentedAccessDialogActivity : AccessDialogActivity(sSerialAccessManager) {
        override fun getPackageManager(): PackageManager {
            return sPackageManager!!
        }

        override fun getCallingPackage(): String {
            return APP_PACKAGE_NAME
        }
    }

    @Before
    fun setUp() {
        mDevice = UiDevice.getInstance(getInstrumentation())
        doReturn(null).whenever(sPackageManager)!!.queryIntentServices(any(), any<Int>())
        doReturn(mApplicationInfo).whenever(sPackageManager)!!
            .getApplicationInfo(any<String>(), any<Int>())
    }

    @Test
    fun testTitle() {
        doReturn(APP_NAME).whenever(mApplicationInfo)!!.loadLabel(sPackageManager!!)

        launchActivity().use { scenario ->
            scenario.onActivity { activity ->
                assertEquals("Allow SerialApp to access ttyS0?", activity!!.getAlertTitle())
            }
        }
    }

    @Test
    fun testClickAllow() {
        launchActivity().use {
            val latch = CountDownLatch(1)
            doAnswer { latch.countDown() }.whenever(sSerialAccessManager!!)
                .grantSerialPortAccess(PORT_NAME, Process.myUid(), false, mToken)

            val label = Pattern.compile("Allow", Pattern.CASE_INSENSITIVE)
            val button = mDevice.wait(
                Until.findObject(By.text(label).clickable(true)),
                Duration.ofSeconds(TIMEOUT_SECONDS).toMillis()
            )
            button.click()

            assertTrue(latch.await(TIMEOUT_SECONDS * 2, TimeUnit.SECONDS))
            verify(sSerialAccessManager!!, never())
                .revokeSerialPortAccess(any(), any(), any(), any())
        }
    }

    @Test
    fun testClickDontAllow() {
        launchActivity().use {
            val latch = CountDownLatch(1)
            doAnswer { latch.countDown() }.whenever(sSerialAccessManager!!)
                .revokeSerialPortAccess(PORT_NAME, Process.myUid(), false, mToken)

            val label = Pattern.compile("Don't allow", Pattern.CASE_INSENSITIVE)
            val button = mDevice.wait(
                Until.findObject(By.text(label).clickable(true)),
                Duration.ofSeconds(TIMEOUT_SECONDS).toMillis()
            )
            button.click()

            assertTrue(latch.await(TIMEOUT_SECONDS * 2, TimeUnit.SECONDS))
            verify(sSerialAccessManager!!, never())
                .grantSerialPortAccess(any(), any(), any(), any())
        }
    }

    @Test
    fun testDismissDialog() {
        launchActivity().use { scenario ->
            val latch = CountDownLatch(1)
            doAnswer { latch.countDown() }.whenever(sSerialAccessManager!!)
                .revokeSerialPortAccess(PORT_NAME, Process.myUid(), false, mToken)

            scenario.moveToState(State.DESTROYED)

            assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            verify(sSerialAccessManager!!, never())
                .grantSerialPortAccess(any(), any(), any(), any())
        }
    }

    @Test
    fun testDisconnectPort() {
        val context = getInstrumentation().getContext()
        val service: ISerialManager = mock()
        val port = SerialPort(context, SerialPortInfo(PORT_NAME, -1, -1), service)
        var listener: SerialPortListener? = null
        whenever(sSerialAccessManager!!.registerSerialPortListener(any(), any())).thenAnswer {
            listener = it.getArgument<SerialPortListener>(1)
        }

        launchActivity().use {
            val latch = CountDownLatch(1)
            doAnswer { latch.countDown() }.whenever(sSerialAccessManager!!)
                .revokeSerialPortAccess(PORT_NAME, Process.myUid(), false, mToken)

            listener!!.onSerialPortDisconnected(port)

            assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            verify(sSerialAccessManager!!, never())
                .grantSerialPortAccess(any(), any(), any(), any())
        }
    }

    private fun launchActivity(): ActivityScenario<AccessDialogActivity> {
        val context = getInstrumentation().getContext()
        val intent = Intent(context, InstrumentedAccessDialogActivity::class.java)
        val binderExtras = Bundle()
        binderExtras.putBinder(SerialManager.EXTRA_REQUEST_TOKEN, mToken)
        with (intent) {
            putExtras(binderExtras)
            putExtra(SerialManager.EXTRA_PORT, PORT_NAME)
            putExtra(SerialManager.EXTRA_PACKAGE_NAME, APP_PACKAGE_NAME)
            putExtra(SerialManager.EXTRA_UID, Process.myUid())
        }
        return ActivityScenario.launch(intent)
    }

    companion object {
        @Mock
        var sPackageManager: PackageManager? = null

        @Mock
        var sSerialAccessManager: SerialAccessManager? = null

        private const val APP_NAME = "SerialApp"
        private const val APP_PACKAGE_NAME = "com.android.serial.accessdialog.AccessDialogTest"
        private const val PORT_NAME = "ttyS0"
        private const val TIMEOUT_SECONDS = 5L
    }
}
