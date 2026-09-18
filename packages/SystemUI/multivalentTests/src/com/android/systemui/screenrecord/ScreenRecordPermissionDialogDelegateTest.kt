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

package com.android.systemui.screenrecord

import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.hardware.display.VirtualDisplayConfig
import android.os.UserHandle
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.testing.TestableLooper
import android.view.Display
import android.view.View
import android.view.WindowManager.LayoutParams.TYPE_STATUS_BAR
import android.widget.Spinner
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.media.projection.flags.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.display.data.repository.FakeDisplayWindowPropertiesRepository
import com.android.systemui.mediaprojection.MediaProjectionMetricsLogger
import com.android.systemui.mediaprojection.appselector.MediaProjectionAppSelectorActivity
import com.android.systemui.mediaprojection.permission.ENTIRE_SCREEN
import com.android.systemui.mediaprojection.permission.SINGLE_APP
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.res.R
import com.android.systemui.screenrecord.data.repository.screenRecordingStartStopRepository
import com.android.systemui.shade.data.repository.shadeDialogContextInteractor
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.statusbar.phone.systemUIDialogDotFactory
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.eq
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
class ScreenRecordPermissionDialogDelegateTest : SysuiTestCase() {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Mock private lateinit var starter: ActivityStarter

    @Mock private lateinit var controller: ScreenRecordUxController

    @Mock private lateinit var onStartRecordingClicked: Runnable

    @Mock private lateinit var mediaProjectionMetricsLogger: MediaProjectionMetricsLogger
    private val fakeDisplayWindowPropertiesRepository =
        FakeDisplayWindowPropertiesRepository(context)

    private val kosmos = testKosmos()

    private lateinit var displayManager: DisplayManager
    private lateinit var dialog: SystemUIDialog
    private lateinit var underTest: ScreenRecordPermissionDialogDelegate

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        displayManager = Mockito.spy(context.getSystemService(DisplayManager::class.java)!!)

        underTest =
            ScreenRecordPermissionDialogDelegate(
                UserHandle.of(0),
                TEST_HOST_UID,
                controller,
                starter,
                onStartRecordingClicked,
                mediaProjectionMetricsLogger,
                kosmos.systemUIDialogDotFactory,
                context,
                displayManager,
                kosmos.screenRecordingStartStopRepository,
                kosmos.shadeDialogContextInteractor,
            )
        dialog = underTest.createDialog()
    }

    @After
    fun teardown() {
        if (::dialog.isInitialized) {
            dismissDialog()
        }
    }

    @Test
    fun testShowDialog_partialScreenSharingEnabled_optionsSpinnerIsVisible() {
        showDialog()

        val visibility = dialog.requireViewById<Spinner>(R.id.screen_share_mode_options).visibility
        assertThat(visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun testShowDialog_singleAppSelected_showTapsIsGone() {
        showDialog()
        onSpinnerItemSelected(SINGLE_APP)

        val visibility = dialog.requireViewById<View>(R.id.show_taps).visibility
        assertThat(visibility).isEqualTo(View.GONE)
    }

    @Test
    fun testShowDialog_entireScreenSelected_showTapsIsVisible() {
        showDialog()
        onSpinnerItemSelected(ENTIRE_SCREEN)

        val visibility = dialog.requireViewById<View>(R.id.show_taps).visibility
        assertThat(visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun startButtonText_entireScreenSelected() {
        showDialog()

        onSpinnerItemSelected(ENTIRE_SCREEN)

        assertThat(getStartButton().text)
            .isEqualTo(
                context.getString(R.string.screenrecord_permission_dialog_continue_entire_screen)
            )
    }

    @Test
    fun startButtonText_singleAppSelected() {
        showDialog()

        onSpinnerItemSelected(SINGLE_APP)

        assertThat(getStartButton().text)
            .isEqualTo(
                context.getString(
                    R.string.media_projection_entry_generic_permission_dialog_continue_single_app
                )
            )
    }

    @Test
    fun startClicked_singleAppSelected_passesHostUidToAppSelector() {
        showDialog()
        onSpinnerItemSelected(SINGLE_APP)

        clickOnStart()

        assertExtraPassedToAppSelector(
            extraKey = MediaProjectionAppSelectorActivity.EXTRA_HOST_APP_UID,
            value = TEST_HOST_UID,
        )
    }

    @Test
    fun showDialog_dialogIsShowing() {
        showDialog()

        assertThat(dialog.isShowing).isTrue()
    }

    @Test
    fun showDialog_singleAppIsDefault() {
        showDialog()

        val spinner = dialog.requireViewById<Spinner>(R.id.screen_share_mode_options)
        val singleApp =
            context.getString(R.string.screenrecord_permission_dialog_option_text_single_app)
        assertThat(spinner.adapter.getItem(0)).isEqualTo(singleApp)
    }

    @Test
    fun showDialog_cancelClicked_dialogIsDismissed() {
        showDialog()

        clickOnCancel()

        assertThat(dialog.isShowing).isFalse()
    }

    @Test
    fun showDialog_cancelClickedMultipleTimes_projectionRequestCancelledIsLoggedOnce() {
        showDialog()

        clickOnCancel()
        clickOnCancel()

        verify(mediaProjectionMetricsLogger).notifyProjectionRequestCancelled(TEST_HOST_UID)
    }

    @Test
    fun dismissDialog_dismissCalledMultipleTimes_projectionRequestCancelledIsLoggedOnce() {
        showDialog()

        dismissDialog()
        dismissDialog()

        verify(mediaProjectionMetricsLogger).notifyProjectionRequestCancelled(TEST_HOST_UID)
    }

    @Test
    fun showDialog_singleAppSelected_clickOnStart_projectionRequestCancelledIsNotLoggedOnce() {
        showDialog()
        onSpinnerItemSelected(SINGLE_APP)

        clickOnStart()

        verify(mediaProjectionMetricsLogger, never())
            .notifyProjectionRequestCancelled(TEST_HOST_UID)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_PROJECTION_CONNECTED_DISPLAY_NO_VIRTUAL_DEVICE)
    fun doNotShowVirtualDisplayInDialog() {
        val displayManager = context.getSystemService(DisplayManager::class.java)!!
        var virtualDisplay: VirtualDisplay? = null
        try {
            virtualDisplay =
                displayManager.createVirtualDisplay(
                    VirtualDisplayConfig.Builder("virtual display", 1, 1, 160).build()
                )
            showDialog()
            val spinner = dialog.requireViewById<Spinner>(R.id.screen_share_mode_options)
            val adapter = spinner.adapter
            val virtualDisplayAvailable =
                (0 until adapter.count)
                    .mapNotNull { adapter.getItem(it) as? String }
                    .any { it.contains("virtual display", ignoreCase = true) }
            assertWithMessage("A Virtual Display was shown in the list of display to record")
                .that(virtualDisplayAvailable)
                .isFalse()
        } finally {
            virtualDisplay?.release()
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MEDIA_PROJECTION_CONNECTED_DISPLAY)
    fun connectedDisplayShown() {
        val mainDisplay =
            mock<Display> {
                on { displayId } doReturn (Display.DEFAULT_DISPLAY)
                on { name } doReturn ("Default Display")
                on { type } doReturn (Display.TYPE_INTERNAL)
            }

        val connectedDisplay =
            mock<Display> {
                on { displayId } doReturn (1000)
                on { name } doReturn ("Connected Display")
                on { type } doReturn (Display.TYPE_EXTERNAL)
            }
        whenever(displayManager.displays).thenReturn(arrayOf(mainDisplay, connectedDisplay))

        dialog = underTest.createDialog()
        showDialog()

        val spinner = dialog.requireViewById<Spinner>(R.id.screen_share_mode_options)
        val optionsText =
            (0 until spinner.adapter.count)
                .map { spinner.adapter.getDropDownView(it, null, spinner) }
                .mapNotNull { it.findViewById<TextView>(android.R.id.text1)?.text }

        // check that the list contains the connected display (type EXTERNAL)
        assertThat(optionsText)
            .contains(
                context.getString(
                    R.string.screenrecord_permission_dialog_option_text_entire_screen_for_display,
                    "Connected Display",
                )
            )
    }

    @Test
    fun createDialog_usesDisplayContextInsteadOfDefaultOne() {
        val shadeContext = context
        val displayManager = context.getSystemService(DisplayManager::class.java)!!
        var virtualDisplay: VirtualDisplay? = null
        try {
            virtualDisplay =
                displayManager.createVirtualDisplay(
                    VirtualDisplayConfig.Builder("virtual display", 1, 1, 160).build()
                )!!
            val displayContext = context.createDisplayContext(virtualDisplay.display)
            fakeDisplayWindowPropertiesRepository.insertForContext(
                displayId = virtualDisplay.display.displayId,
                TYPE_STATUS_BAR,
                displayContext,
            )
            shadeContext.display = virtualDisplay.display

            dialog = underTest.createDialog()

            assertThat(dialog.context.displayId).isEqualTo(virtualDisplay.display.displayId)
        } finally {
            virtualDisplay?.release()
        }
    }

    private fun showDialog() {
        dialog.show()
    }

    private fun dismissDialog() {
        dialog.dismiss()
    }

    private fun clickOnCancel() {
        dialog.requireViewById<View>(android.R.id.button2).performClick()
    }

    private fun getStartButton() = dialog.requireViewById<TextView>(android.R.id.button1)

    private fun clickOnStart() {
        getStartButton().performClick()
    }

    private fun onSpinnerItemSelected(position: Int) {
        val spinner = dialog.requireViewById<Spinner>(R.id.screen_share_mode_options)
        checkNotNull(spinner.onItemSelectedListener)
            .onItemSelected(spinner, mock(), position, /* id= */ 0)
    }

    private fun assertExtraPassedToAppSelector(extraKey: String, value: Int) {
        val intentCaptor = argumentCaptor<Intent>()
        verify(starter).startActivity(intentCaptor.capture(), /* dismissShade= */ eq(true))

        val intent = intentCaptor.firstValue
        assertThat(intent.extras!!.getInt(extraKey)).isEqualTo(value)
    }

    companion object {
        private const val TEST_HOST_UID = 12345
    }
}
