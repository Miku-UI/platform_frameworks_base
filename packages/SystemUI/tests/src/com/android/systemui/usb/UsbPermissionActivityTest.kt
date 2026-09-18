/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui.usb

import android.app.PendingIntent
import android.content.Intent
import android.hardware.usb.UsbManager
import android.hardware.usb.flags.Flags.FLAG_ENABLE_PERSISTENT_USB_DEVICE_PERMISSIONS
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper
import android.view.View
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.app.AlertController
import com.android.systemui.res.R
import com.google.common.truth.Truth.assertThat
import javax.inject.Inject
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

/** UsbPermissionActivityTest */
@RunWith(AndroidJUnit4::class)
@SmallTest
@TestableLooper.RunWithLooper
class UsbPermissionActivityTest :
    UsbDialogActivityTest<UsbPermissionActivityTest.UsbPermissionActivityTestable>() {
    companion object {
        private const val REQUEST_CODE: Int = 334
        private const val INTENT_ACTION: String = "NO_ACTION"
    }

    class UsbPermissionActivityTestable
    @Inject
    constructor(val message: UsbAudioWarningDialogMessage) :
        UsbPermissionActivity(message), UsbDialogActivityTestable {
        override fun getAlertParams(): AlertController.AlertParams {
            return mAlertParams
        }
    }

    override fun createActivity(): UsbPermissionActivityTestable {
        return UsbPermissionActivityTestable(mMessage)
    }

    override fun getActivityClass(): Class<UsbPermissionActivityTestable> {
        return UsbPermissionActivityTestable::class.java
    }

    override fun createIntent(canBeDefault: Boolean): Intent {
        return super.createIntent(canBeDefault)
            .putExtra(UsbManager.EXTRA_PACKAGE, EXTRA_PACKAGE)
            .putExtra(Intent.EXTRA_UID, EXTRA_UID)
            .putExtra(
                Intent.EXTRA_INTENT,
                PendingIntent.getBroadcast(
                    mContext,
                    REQUEST_CODE,
                    Intent(INTENT_ACTION).apply { setPackage(PACKAGE) },
                    PendingIntent.FLAG_MUTABLE,
                ),
            )
    }

    override fun getTitleResId(): Int {
        return R.string.usb_audio_device_permission_prompt_title
    }

    // Tests for old Usb Dialog UI

    @Test
    @DisableFlags(FLAG_ENABLE_PERSISTENT_USB_DEVICE_PERMISSIONS)
    fun testUsbAccessoryDialogTitle() {
        deviceConfiguration(isUsbDevice = false)

        checkNewUiIsNotInitialized()

        assertThat(mAlertParams.mTitle.toString()).isEqualTo(getTitle())

        // Usb Accessory shouldn't have a message.
        assertThat(mAlertParams.mMessage).isNull()

        // Dialog shouldn't have a checkbox, if it can't be default.
        assertThat(mAlertParams.mView).isNull()
    }

    @Test
    @DisableFlags(FLAG_ENABLE_PERSISTENT_USB_DEVICE_PERMISSIONS)
    fun testUsbAccessoryDialog() {
        deviceConfiguration(isUsbDevice = false, canBeDefault = true)

        checkNewUiIsNotInitialized()

        assertThat(mAlertParams.mTitle.toString()).isEqualTo(getTitle())

        // Usb Accessory shouldn't have a message.
        assertThat(mAlertParams.mMessage).isNull()

        val alwaysUseView: CheckBox? =
            mAlertParams.mView.findViewById(com.android.internal.R.id.alwaysUse)
        assertThat(alwaysUseView!!.text).isEqualTo(getAlwaysUseCheckboxMessage())
    }

    @Test
    @DisableFlags(FLAG_ENABLE_PERSISTENT_USB_DEVICE_PERMISSIONS)
    fun testUsbDeviceDialogTitle() {
        deviceConfiguration(isUsbDevice = true)

        checkNewUiIsNotInitialized()

        assertThat(mAlertParams.mTitle.toString()).isEqualTo(getTitle())

        // Dialog shouldn't have a message, if it is not an audio device.
        assertThat(mAlertParams.mMessage).isNull()
        // Dialog shouldn't have a checkbox, if it is not an audio device.
        assertThat(mAlertParams.mView).isNull()
    }

    @Test
    @DisableFlags(FLAG_ENABLE_PERSISTENT_USB_DEVICE_PERMISSIONS)
    fun testUsbDeviceDialogTitleAndMessage() {
        deviceConfiguration(isUsbDevice = true, hasAudioPlayback = true)

        checkNewUiIsNotInitialized()

        assertThat(mAlertParams.mTitle.toString()).isEqualTo(getTitle())
        assertThat(mAlertParams.mMessage).isEqualTo(getMessage(R.string.usb_audio_device_prompt))

        // Dialog shouldn't have a checkbox, if it can't be default.
        assertThat(mAlertParams.mView).isNull()
    }

    @Test
    @DisableFlags(FLAG_ENABLE_PERSISTENT_USB_DEVICE_PERMISSIONS)
    fun testUsbDeviceDialog() {
        deviceConfiguration(
            canBeDefault = true,
            isUsbDevice = true,
            hasAudioCapture = true,
            audioRecordingPermissionStatus = android.content.pm.PackageManager.PERMISSION_GRANTED,
        )

        checkNewUiIsNotInitialized()

        assertThat(mAlertParams.mTitle.toString()).isEqualTo(getTitle())
        assertThat(mAlertParams.mMessage).isEqualTo(getMessage(R.string.usb_audio_device_prompt))

        val alwaysUseView: CheckBox? =
            mAlertParams.mView.findViewById(com.android.internal.R.id.alwaysUse)
        assertThat(alwaysUseView!!.text).isEqualTo(getAlwaysUseCheckboxMessage())
    }

    // Tests for new Usb Dialog UI

    @Test
    @EnableFlags(FLAG_ENABLE_PERSISTENT_USB_DEVICE_PERMISSIONS)
    fun testUsbAccessoryNewDialogTitle() {
        deviceConfiguration(isUsbDevice = false)

        checkOldUiIsNotInitialized()

        val dialogView: View = mAlertParams.mView

        assertThat(dialogView.findViewById<TextView>(R.id.usb_device_dialog_title).text.toString())
            .isEqualTo(getTitle())

        // Usb Accessory shouldn't have a message.
        assertThat(dialogView.findViewById<TextView>(R.id.usb_device_dialog_message).visibility)
            .isEqualTo(View.GONE)

        // Dialog shouldn't have a checkbox, if it can't be default.
        assertThat(
                dialogView
                    .findViewById<FrameLayout>(R.id.usb_device_dialog_always_use_content)
                    .visibility
            )
            .isEqualTo(View.GONE)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_PERSISTENT_USB_DEVICE_PERMISSIONS)
    fun testUsbAccessoryNewDialog() {
        deviceConfiguration(isUsbDevice = false, canBeDefault = true)

        checkOldUiIsNotInitialized()

        val dialogView: View = mAlertParams.mView

        assertThat(dialogView.findViewById<TextView>(R.id.usb_device_dialog_title).text.toString())
            .isEqualTo(getTitle())

        // Usb Accessory shouldn't have a message.
        assertThat(dialogView.findViewById<TextView>(R.id.usb_device_dialog_message).visibility)
            .isEqualTo(View.GONE)

        assertThat(
                dialogView
                    .findViewById<FrameLayout>(R.id.usb_device_dialog_always_use_content)
                    .findViewById<CheckBox>(com.android.internal.R.id.alwaysUse)
                    .text
            )
            .isEqualTo(getAlwaysUseCheckboxMessage())
    }

    @Test
    @EnableFlags(FLAG_ENABLE_PERSISTENT_USB_DEVICE_PERMISSIONS)
    fun testUsbAccessoryNewDialogCancel() {
        prepareNewDialogCancel(isUsbDevice = false)

        verifyNoMoreInteractions(mUsbService)

        assertFalse(activityRule.activity.mPermissionGranted)
        assertFalse(activityRule.activity.isAlwaysUseChecked)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_PERSISTENT_USB_DEVICE_PERMISSIONS)
    fun testUsbAccessoryNewDialogAllowOnlyThisTime() {
        prepareNewDialogAllowOnlyThisTime(isUsbDevice = false)

        verify(mUsbService)
            .grantAccessoryPermission(eq(mUsbAccessory), eq(EXTRA_PACKAGE), eq(EXTRA_UID))
        verifyNoMoreInteractions(mUsbService)

        assertTrue(activityRule.activity.mPermissionGranted)
        assertFalse(activityRule.activity.isAlwaysUseChecked)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_PERSISTENT_USB_DEVICE_PERMISSIONS)
    fun testUsbAccessoryNewDialogAllowOnlyThisTimeWithAlwaysUse() {
        prepareNewDialogAllowOnlyThisTimeWithAlwaysUse(isUsbDevice = false)

        verify(mUsbService)
            .grantAccessoryPermission(eq(mUsbAccessory), eq(EXTRA_PACKAGE), eq(EXTRA_UID))
        verify(mUsbService).setAccessoryPackage(eq(mUsbAccessory), eq(EXTRA_PACKAGE), any())
        verifyNoMoreInteractions(mUsbService)

        assertTrue(activityRule.activity.mPermissionGranted)
        assertTrue(activityRule.activity.isAlwaysUseChecked)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_PERSISTENT_USB_DEVICE_PERMISSIONS)
    fun testUsbDeviceNewDialogTitle() {
        deviceConfiguration(isUsbDevice = true)

        checkOldUiIsNotInitialized()

        val dialogView: View = mAlertParams.mView

        assertThat(dialogView.findViewById<TextView>(R.id.usb_device_dialog_title).text.toString())
            .isEqualTo(getTitle())

        // Dialog shouldn't have a message, if it is not an audio device.
        assertThat(dialogView.findViewById<TextView>(R.id.usb_device_dialog_message).visibility)
            .isEqualTo(View.GONE)
        // Dialog shouldn't have a checkbox, if it is not an audio device.
        assertThat(
                dialogView
                    .findViewById<FrameLayout>(R.id.usb_device_dialog_always_use_content)
                    .visibility
            )
            .isEqualTo(View.GONE)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_PERSISTENT_USB_DEVICE_PERMISSIONS)
    fun testUsbDeviceNewDialogTitleAndMessage() {
        deviceConfiguration(isUsbDevice = true, hasAudioPlayback = true)

        checkOldUiIsNotInitialized()

        val dialogView: View = mAlertParams.mView

        assertThat(dialogView.findViewById<TextView>(R.id.usb_device_dialog_title).text.toString())
            .isEqualTo(getTitle())
        assertThat(dialogView.findViewById<TextView>(R.id.usb_device_dialog_message).text)
            .isEqualTo(getMessage(R.string.usb_audio_device_prompt))

        // Dialog shouldn't have a checkbox, if it can't be default.
        assertThat(
                dialogView
                    .findViewById<FrameLayout>(R.id.usb_device_dialog_always_use_content)
                    .visibility
            )
            .isEqualTo(View.GONE)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_PERSISTENT_USB_DEVICE_PERMISSIONS)
    fun testUsbDeviceNewDialog() {
        deviceConfiguration(
            canBeDefault = true,
            isUsbDevice = true,
            hasAudioCapture = true,
            audioRecordingPermissionStatus = android.content.pm.PackageManager.PERMISSION_GRANTED,
        )

        checkOldUiIsNotInitialized()

        val dialogView: View = mAlertParams.mView

        assertThat(dialogView.findViewById<TextView>(R.id.usb_device_dialog_title).text.toString())
            .isEqualTo(getTitle())
        assertThat(dialogView.findViewById<TextView>(R.id.usb_device_dialog_message).text)
            .isEqualTo(getMessage(R.string.usb_audio_device_prompt))

        assertThat(
                dialogView
                    .findViewById<FrameLayout>(R.id.usb_device_dialog_always_use_content)
                    .findViewById<CheckBox>(com.android.internal.R.id.alwaysUse)
                    .text
            )
            .isEqualTo(getAlwaysUseCheckboxMessage())
    }

    @Test
    @EnableFlags(FLAG_ENABLE_PERSISTENT_USB_DEVICE_PERMISSIONS)
    fun testUsbDeviceNewDialogCancel() {
        prepareNewDialogCancel(isUsbDevice = true)

        verifyNoMoreInteractions(mUsbService)

        assertFalse(activityRule.activity.mPermissionGranted)
        assertFalse(activityRule.activity.isAlwaysUseChecked)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_PERSISTENT_USB_DEVICE_PERMISSIONS)
    fun testUsbDeviceNewDialogAllowOnlyThisTime() {
        prepareNewDialogAllowOnlyThisTime(isUsbDevice = true)

        verify(mUsbService)
            .grantDevicePermission(eq(mUsbDevice), eq(EXTRA_PACKAGE), eq(EXTRA_UID), eq(false))
        verifyNoMoreInteractions(mUsbService)

        assertTrue(activityRule.activity.mPermissionGranted)
        assertFalse(activityRule.activity.isAlwaysUseChecked)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_PERSISTENT_USB_DEVICE_PERMISSIONS)
    fun testUsbDeviceNewDialogAllowOnlyThisTimeWithAlwaysUse() {
        prepareNewDialogAllowOnlyThisTimeWithAlwaysUse(isUsbDevice = true)

        verify(mUsbService)
            .grantDevicePermission(eq(mUsbDevice), eq(EXTRA_PACKAGE), eq(EXTRA_UID), eq(false))
        verify(mUsbService).setDevicePackage(eq(mUsbDevice), eq(EXTRA_PACKAGE), any())
        verifyNoMoreInteractions(mUsbService)

        assertTrue(activityRule.activity.mPermissionGranted)
        assertTrue(activityRule.activity.isAlwaysUseChecked)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_PERSISTENT_USB_DEVICE_PERMISSIONS)
    fun testUsbDeviceNewDialogAlwaysAllow() {
        prepareNewDialogAlwaysAllow()

        verify(mUsbService)
            .grantDevicePermission(eq(mUsbDevice), eq(EXTRA_PACKAGE), eq(EXTRA_UID), eq(true))
        verifyNoMoreInteractions(mUsbService)

        assertTrue(activityRule.activity.mPermissionGranted)
        assertFalse(activityRule.activity.isAlwaysUseChecked)
    }
}
