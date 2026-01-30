/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.mediaprojection.permission

import android.app.AlertDialog
import android.hardware.display.DisplayManager
import android.hardware.display.displayManager
import android.media.projection.MediaProjectionConfig
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.testing.TestableLooper
import android.view.Display
import android.view.WindowManager
import android.widget.Spinner
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.media.projection.flags.Flags.FLAG_MEDIA_PROJECTION_CONNECTED_DISPLAY
import com.android.media.projection.flags.Flags.FLAG_MEDIA_PROJECTION_CONNECTED_DISPLAY_SCREEN_SHARING
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.mediaprojection.MediaProjectionMetricsLogger
import com.android.systemui.res.R
import com.android.systemui.statusbar.phone.AlertDialogWithDelegate
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * atest
 * SystemUITests:com.android.systemui.mediaprojection.permission.ShareToAppPermissionDialogDelegateTest
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
class ShareToAppPermissionDialogDelegateTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val testScope = kosmos.testScope
    private val displayManager = kosmos.displayManager

    @get:Rule val checkFlagRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private lateinit var dialog: AlertDialog

    private val appName = "Test App"

    private val resIdSingleApp =
        R.string.media_projection_entry_app_permission_dialog_option_text_single_app
    private val resIdFullScreen =
        R.string.media_projection_entry_app_permission_dialog_option_text_entire_screen
    private val resIdDisplay =
        R.string.screen_share_permission_dialog_option_text_entire_screen_for_display
    private val resIdSingleAppDisabled =
        R.string.media_projection_entry_app_permission_dialog_single_app_disabled
    private val resIdSingleAppNotSupported =
        R.string.media_projection_entry_app_permission_dialog_single_app_not_supported

    @After
    fun teardown() {
        if (::dialog.isInitialized) {
            dialog.dismiss()
        }
    }

    @Test
    fun showDefaultDialog() {
        setUpAndShowDialog()

        val spinner = dialog.requireViewById<Spinner>(R.id.screen_share_mode_options)
        val secondOptionText =
            spinner.adapter
                .getDropDownView(1, null, spinner)
                .findViewById<TextView>(android.R.id.text1)
                ?.text

        // check that the first option is single app and enabled
        assertEquals(context.getString(resIdSingleApp), spinner.selectedItem)

        // check that the second option is full screen and enabled
        assertEquals(context.getString(resIdFullScreen), secondOptionText)
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_MEDIA_PROJECTION_GREY_ERROR_TEXT)
    fun showDialog_disableSingleApp() {
        setUpAndShowDialog(
            mediaProjectionConfig = MediaProjectionConfig.createConfigForDefaultDisplay()
        )

        val spinner = dialog.requireViewById<Spinner>(R.id.screen_share_mode_options)
        val secondOptionWarningText =
            spinner.adapter
                .getDropDownView(1, null, spinner)
                .findViewById<TextView>(android.R.id.text2)
                ?.text

        // check that the first option is full screen and enabled
        assertEquals(context.getString(resIdFullScreen), spinner.selectedItem)

        // check that the second option is single app and disabled
        assertEquals(context.getString(resIdSingleAppDisabled, appName), secondOptionWarningText)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_PROJECTION_GREY_ERROR_TEXT)
    fun showDialog_disableSingleApp_appNotSupported() {
        setUpAndShowDialog(
            mediaProjectionConfig = MediaProjectionConfig.createConfigForDefaultDisplay()
        )

        val spinner = dialog.requireViewById<Spinner>(R.id.screen_share_mode_options)
        val secondOptionWarningText =
            spinner.adapter
                .getDropDownView(1, null, spinner)
                .findViewById<TextView>(android.R.id.text2)
                ?.text

        // check that the first option is full screen and enabled
        assertEquals(context.getString(resIdFullScreen), spinner.selectedItem)

        // check that the second option is single app and disabled
        assertEquals(
            context.getString(resIdSingleAppNotSupported, appName),
            secondOptionWarningText,
        )
    }

    @Test
    fun showDialog_disableSingleApp_forceShowPartialScreenShareTrue() {
        setUpAndShowDialog(
            mediaProjectionConfig = MediaProjectionConfig.createConfigForDefaultDisplay(),
            overrideDisableSingleAppOption = true,
        )

        val spinner = dialog.requireViewById<Spinner>(R.id.screen_share_mode_options)
        val secondOptionText =
            spinner.adapter
                .getDropDownView(1, null, spinner)
                .findViewById<TextView>(android.R.id.text1)
                ?.text

        // check that the first option is single app and enabled
        assertEquals(context.getString(resIdSingleApp), spinner.selectedItem)

        // check that the second option is full screen and enabled
        assertEquals(context.getString(resIdFullScreen), secondOptionText)
    }

    @Test
    fun startButtonText_entireScreenSelected() {
        setUpAndShowDialog()
        onSpinnerItemSelected(ENTIRE_SCREEN)

        val startButtonText = dialog.requireViewById<TextView>(android.R.id.button1).text

        assertThat(startButtonText)
            .isEqualTo(
                context.getString(
                    R.string.media_projection_entry_app_permission_dialog_continue_entire_screen
                )
            )
    }

    @Test
    fun startButtonText_singleAppSelected() {
        setUpAndShowDialog()
        onSpinnerItemSelected(SINGLE_APP)

        val startButtonText = dialog.requireViewById<TextView>(android.R.id.button1).text

        assertThat(startButtonText)
            .isEqualTo(
                context.getString(
                    R.string.media_projection_entry_generic_permission_dialog_continue_single_app
                )
            )
    }

    @Test
    @RequiresFlagsEnabled(
        FLAG_MEDIA_PROJECTION_CONNECTED_DISPLAY,
        FLAG_MEDIA_PROJECTION_CONNECTED_DISPLAY_SCREEN_SHARING,
    )
    fun connectedDisplayShown() {
        testScope.runTest {
            context.addMockSystemService(DisplayManager::class.java, displayManager)
            val mainDisplay =
                mock<Display>().apply {
                    whenever(displayId).thenReturn(Display.DEFAULT_DISPLAY)
                    whenever(name).thenReturn("Default Display")
                    whenever(type).thenReturn(Display.TYPE_INTERNAL)
                }

            val connectedDisplay =
                mock<Display>().apply {
                    whenever(displayId).thenReturn(1000)
                    whenever(name).thenReturn("Connected Display")
                    whenever(type).thenReturn(Display.TYPE_EXTERNAL)
                }
            whenever(displayManager.displays).thenReturn(arrayOf(mainDisplay, connectedDisplay))
            setUpAndShowDialog()

            val spinner = dialog.requireViewById<Spinner>(R.id.screen_share_mode_options)
            val optionsText =
                (0 until spinner.adapter.count)
                    .map { spinner.adapter.getDropDownView(it, null, spinner) }
                    .mapNotNull { it.findViewById<TextView>(android.R.id.text1)?.text }

            // check that the first option is single app and enabled
            assertThat(optionsText).contains(context.getString(resIdDisplay, "Connected Display"))
        }
    }

    private fun setUpAndShowDialog(
        mediaProjectionConfig: MediaProjectionConfig? = null,
        overrideDisableSingleAppOption: Boolean = false,
    ) {
        val delegate =
            ShareToAppPermissionDialogDelegate(
                context,
                mediaProjectionConfig,
                onStartRecordingClicked = {},
                onCancelClicked = {},
                appName,
                overrideDisableSingleAppOption,
                hostUid = 12345,
                mediaProjectionMetricsLogger = mock<MediaProjectionMetricsLogger>(),
            )

        dialog = AlertDialogWithDelegate(context, R.style.Theme_SystemUI_Dialog, delegate)
        SystemUIDialog.applyFlags(dialog)
        SystemUIDialog.setDialogSize(dialog)

        dialog.window?.addSystemFlags(
            WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS
        )

        delegate.onCreate(dialog, savedInstanceState = null)
        dialog.show()
    }

    private fun onSpinnerItemSelected(position: Int) {
        val spinner = dialog.requireViewById<Spinner>(R.id.screen_share_mode_options)
        checkNotNull(spinner.onItemSelectedListener)
            .onItemSelected(spinner, mock(), position, /* id= */ 0)
    }
}
