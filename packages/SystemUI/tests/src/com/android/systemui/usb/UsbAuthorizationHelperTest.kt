/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.usb

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.hardware.usb.IUsbManager
import android.hardware.usb.UsbAuthorizationStatus
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.provider.Settings
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper
import android.view.LayoutInflater
import androidx.test.filters.SmallTest
import com.android.internal.app.AlertController
import com.android.systemui.SysuiTestCase
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.res.R
import com.android.systemui.util.settings.FakeGlobalSettings
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.eq
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor

@RunWith(AndroidTestingRunner::class)
@TestableLooper.RunWithLooper
@SmallTest
class UsbAuthorizationHelperTest : SysuiTestCase() {
    companion object {
        const val MY_DEVICE_NAME = "foobar"
    }

    @Mock private lateinit var usbManager: IUsbManager
    @Mock private lateinit var broadcastDispatcher: BroadcastDispatcher
    @Mock private lateinit var alertUpdater: AlertUpdater
    @Mock private lateinit var parentActivity: Activity
    @Mock private lateinit var usbDevice1: UsbDevice
    @Mock private lateinit var usbDevice2: UsbDevice
    @Mock private lateinit var onClick: DialogInterface.OnClickListener

    private val globalSettings = FakeGlobalSettings(StandardTestDispatcher())
    private lateinit var helper: UsbAuthorizationHelper
    private lateinit var alertParams: AlertController.AlertParams

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        val layoutInflater = LayoutInflater.from(context)
        whenever(parentActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE))
            .thenReturn(layoutInflater)
        whenever(parentActivity.resources).thenReturn(context.resources)

        // Mock UsbDevices
        whenever(usbDevice1.deviceName).thenReturn("/dev/bus/usb/001/001")
        whenever(usbDevice1.productName).thenReturn("Mouse")
        whenever(usbDevice1.vendorId).thenReturn(0x1234)
        whenever(usbDevice1.productId).thenReturn(0x5678)

        whenever(usbDevice2.deviceName).thenReturn("/dev/bus/usb/001/002")
        whenever(usbDevice2.productName).thenReturn("Keyboard")

        globalSettings.putString(Settings.Global.DEVICE_NAME, MY_DEVICE_NAME)

        // Setup Helper
        helper =
            UsbAuthorizationHelper(
                context.resources,
                usbManager,
                globalSettings,
                broadcastDispatcher,
                alertUpdater,
                parentActivity,
            )

        alertParams = AlertController.AlertParams(context)
    }

    @Test
    fun testNewDeviceIntent_addsDevice() {
        val intent = createDeviceIntent(usbDevice1)
        helper.newDeviceIntent(intent)

        // Verify we registered receiver
        verify(broadcastDispatcher)
            .registerReceiver(
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyInt(),
                anyOrNull(),
            )

        // Set alert params and verify we use the singular messaging.
        helper.setAlertParams(alertParams, onClick)
        assertEquals(context.getString(R.string.usb_authorization_title), alertParams.mTitle)
        assertEquals(
            context.getString(R.string.usb_authorization_message, MY_DEVICE_NAME),
            helper.lastMessageText,
        )
    }

    @Test
    fun testNewDeviceIntent_multipleDevices() {
        helper.newDeviceIntent(createDeviceIntent(usbDevice1))
        helper.newDeviceIntent(createDeviceIntent(usbDevice2))

        helper.setAlertParams(alertParams, onClick)

        // Verify plural title and message
        assertEquals(context.getString(R.string.usb_authorization_title_multi), alertParams.mTitle)
        assertEquals(
            context.getString(R.string.usb_authorization_message_multi, MY_DEVICE_NAME),
            helper.lastMessageText,
        )
    }

    @Test
    fun testNewDeviceIntent_untrusted() {
        val intent = createDeviceIntent(usbDevice1)
        intent.putExtra(UsbAuthorizationHelper.EXTRA_UNTRUSTED, true)

        helper.newDeviceIntent(intent)
        helper.setAlertParams(alertParams, onClick)

        assertEquals(
            context.getString(R.string.usb_authorization_message_untrusted, MY_DEVICE_NAME),
            helper.lastMessageText,
        )
        // Assert View is created
        assertNotNull(alertParams.mView)

        // Add one more device and check strings.
        helper.newDeviceIntent(createDeviceIntent(usbDevice2))
        helper.setAlertParams(alertParams, onClick)
        assertEquals(
            context.getString(R.string.usb_authorization_message_multi_untrusted, MY_DEVICE_NAME),
            helper.lastMessageText,
        )
    }

    @Test
    fun testSendResponse_allow() {
        helper.newDeviceIntent(createDeviceIntent(usbDevice1))
        helper.sendResponse(UsbAuthorizationStatus.AUTHORIZED)

        verify(usbManager)
            .setAuthorizationResponse(
                eq(usbDevice1),
                eq(UsbAuthorizationStatus.AUTHORIZED),
                eq(true),
            )
    }

    @Test
    fun testSendResponse_deny() {
        helper.newDeviceIntent(createDeviceIntent(usbDevice1))
        helper.sendResponse(UsbAuthorizationStatus.DENIED)

        verify(usbManager)
            .setAuthorizationResponse(eq(usbDevice1), eq(UsbAuthorizationStatus.DENIED), eq(true))
    }

    @Test
    fun testComplete_implicitDeny() {
        helper.newDeviceIntent(createDeviceIntent(usbDevice1))

        // complete() called without sendResponse() previously called
        // Should default to DENY
        helper.complete()
        verify(usbManager)
            .setAuthorizationResponse(eq(usbDevice1), eq(UsbAuthorizationStatus.DENIED), eq(true))

        // We should unregister on complete
        verify(broadcastDispatcher).unregisterReceiver(anyOrNull())
    }

    @Test
    fun testComplete_alreadyResponded_doesNotRespondTwice() {
        helper.newDeviceIntent(createDeviceIntent(usbDevice1))

        // User explicitly allowed
        helper.sendResponse(UsbAuthorizationStatus.AUTHORIZED)

        // Activity stops
        helper.complete()

        // Verify we didn't call it again with DENIED
        verify(usbManager, never())
            .setAuthorizationResponse(anyOrNull(), eq(UsbAuthorizationStatus.DENIED), eq(true))
    }

    @Test
    fun testDetach_removesDeviceAndUpdates() {
        helper.newDeviceIntent(createDeviceIntent(usbDevice1))
        helper.newDeviceIntent(createDeviceIntent(usbDevice2))

        // Capture the receiver registered
        val captor: KArgumentCaptor<BroadcastReceiver> = argumentCaptor()
        verify(broadcastDispatcher)
            .registerReceiver(
                captor.capture(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyInt(),
                anyOrNull(),
            )
        val receiver = captor.lastValue
        assertNotNull(receiver)

        // Simulate Detach of Device 1
        val detachIntent = Intent(UsbManager.ACTION_USB_DEVICE_DETACHED)
        detachIntent.putExtra(UsbManager.EXTRA_DEVICE, usbDevice1)
        receiver?.onReceive(context, detachIntent)

        // Should update alert because Device 2 is still there
        verify(alertUpdater).updateAlert()
        verify(parentActivity, never()).finish()
    }

    @Test
    fun testDetach_allDevices_finishesActivity() {
        helper.newDeviceIntent(createDeviceIntent(usbDevice1))

        val captor: KArgumentCaptor<BroadcastReceiver> = argumentCaptor()
        verify(broadcastDispatcher)
            .registerReceiver(
                captor.capture(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyInt(),
                anyOrNull(),
            )

        val receiver = captor.lastValue
        assertNotNull(receiver)

        // Simulate Detach
        val detachIntent = Intent(UsbManager.ACTION_USB_DEVICE_DETACHED)
        detachIntent.putExtra(UsbManager.EXTRA_DEVICE, usbDevice1)
        receiver?.onReceive(context, detachIntent)

        // Should NOT update alert, but finish activity
        verify(alertUpdater, never()).updateAlert()
        verify(parentActivity).finish()
    }

    @Test
    fun testUnknownDeviceName_handlesGracefully() {
        whenever(usbDevice1.productName).thenReturn(null)

        helper.newDeviceIntent(createDeviceIntent(usbDevice1))

        val name = helper.getProductNameString(usbDevice1)

        // Should return formatted string with vendor/product ID
        val expected =
            context.getString(
                R.string.usb_authorization_unknown_device,
                usbDevice1.vendorId,
                usbDevice1.productId,
            )
        assertEquals(expected, name)
    }

    private fun createDeviceIntent(device: UsbDevice): Intent {
        val intent = Intent()
        intent.putExtra(UsbManager.EXTRA_DEVICE, device)
        return intent
    }
}
