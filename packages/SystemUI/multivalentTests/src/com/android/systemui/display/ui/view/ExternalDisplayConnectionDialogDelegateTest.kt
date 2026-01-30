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

package com.android.systemui.display.ui.view

import android.app.Dialog
import android.graphics.Insets
import android.graphics.Rect
import android.platform.test.annotations.RequiresFlagsEnabled
import android.testing.TestableLooper
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsAnimation
import android.widget.CheckBox
import android.widget.CompoundButton
import androidx.core.view.marginBottom
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.app.animation.Interpolators
import com.android.server.display.feature.flags.Flags.FLAG_ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT
import com.android.systemui.SysuiTestCase
import com.android.systemui.res.R
import com.android.window.flags.Flags.FLAG_ENABLE_UPDATED_DISPLAY_CONNECTION_DIALOG
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.capture
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RequiresFlagsEnabled(
    FLAG_ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT,
    FLAG_ENABLE_UPDATED_DISPLAY_CONNECTION_DIALOG,
)
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
class ExternalDisplayConnectionDialogDelegateTest : SysuiTestCase() {

    private val rememberChoiceCallback = mock<CompoundButton.OnCheckedChangeListener>()
    private val onStartDesktopCallback = mock<View.OnClickListener>()
    private val onStartMirroringCallback = mock<View.OnClickListener>()
    private val onCancelCallback = mock<View.OnClickListener>()
    private val windowInsetsAnimationCallbackCaptor =
        ArgumentCaptor.forClass(WindowInsetsAnimation.Callback::class.java)
    private val dialog: Dialog = mock()
    private val window: Window = mock()
    private val windowDecorView: View = mock()

    private lateinit var inflatedView: View
    private lateinit var underTest: ExternalDisplayConnectionDialogDelegate

    @Before
    fun setUp() {
        inflatedView =
            LayoutInflater.from(context).inflate(R.layout.connected_display_dialog_2, null, false)

        whenever(dialog.window).thenReturn(window)
        whenever(window.decorView).thenReturn(windowDecorView)
        whenever(dialog.requireViewById<View>(any())).thenAnswer { invocation ->
            val viewId = invocation.getArgument<Int>(0)
            inflatedView.requireViewById(viewId)
        }

        underTest =
            ExternalDisplayConnectionDialogDelegate(
                context = context,
                showConcurrentDisplayInfo = false,
                isInKioskMode = false,
                rememberChoiceCheckBoxListener = rememberChoiceCallback,
                onStartDesktopClickListener = onStartDesktopCallback,
                onStartMirroringClickListener = onStartMirroringCallback,
                onCancelClickListener = onCancelCallback,
                insetsProvider = { Insets.of(Rect()) },
            )
    }

    @Test
    fun startDesktopButton_clicked_callsCorrectCallback() {
        underTest.onCreate(dialog, null)

        dialog.requireViewById<View>(R.id.start_desktop_mode).callOnClick()

        verify(onStartDesktopCallback).onClick(any())
        verify(onCancelCallback, never()).onClick(any())
    }

    @Test
    fun startMirroringButton_clicked_callsCorrectCallback() {
        underTest.onCreate(dialog, null)

        dialog.requireViewById<View>(R.id.start_mirroring).callOnClick()

        verify(onStartMirroringCallback).onClick(any())
        verify(onCancelCallback, never()).onClick(any())
    }

    @Test
    fun cancelButton_clicked_callsCorrectCallback() {
        underTest.onCreate(dialog, null)

        dialog.requireViewById<View>(R.id.cancel).callOnClick()

        verify(onCancelCallback).onClick(any())
        verify(onStartMirroringCallback, never()).onClick(any())
        verify(onStartDesktopCallback, never()).onClick(any())
    }

    @Test
    fun onCancel_afterEnablingMirroring_cancelCallbackNotCalled() {
        underTest.onCreate(dialog, null)
        dialog.requireViewById<View>(R.id.start_mirroring).callOnClick()

        underTest.onStop(dialog)

        verify(onCancelCallback, never()).onClick(any())
        verify(onStartMirroringCallback).onClick(any())
    }

    @Test
    fun onCancel_afterEnablingDesktopMode_cancelCallbackNotCalled() {
        underTest.onCreate(dialog, null)
        dialog.requireViewById<View>(R.id.start_desktop_mode).callOnClick()

        underTest.onStop(dialog)

        verify(onCancelCallback, never()).onClick(any())
        verify(onStartDesktopCallback).onClick(any())
    }

    @Test
    fun startDesktopButton_inKioskMode_isNotVisible() {
        val kioskModeDialogDelegate =
            ExternalDisplayConnectionDialogDelegate(
                context = context,
                showConcurrentDisplayInfo = false,
                isInKioskMode = true,
                rememberChoiceCheckBoxListener = rememberChoiceCallback,
                onStartDesktopClickListener = onStartDesktopCallback,
                onStartMirroringClickListener = onStartMirroringCallback,
                onCancelClickListener = onCancelCallback,
                insetsProvider = { Insets.of(Rect()) },
            )
        kioskModeDialogDelegate.onCreate(dialog, null)

        val desktopModeButton = dialog.requireViewById<View>(R.id.start_desktop_mode)
        assertThat(desktopModeButton.visibility).isEqualTo(View.GONE)
    }

    @Test
    fun saveChoiceCheckbox_inKioskMode_isNotVisible() {
        val kioskModeDialogDelegate =
            ExternalDisplayConnectionDialogDelegate(
                context = context,
                showConcurrentDisplayInfo = false,
                isInKioskMode = true,
                rememberChoiceCheckBoxListener = rememberChoiceCallback,
                onStartDesktopClickListener = onStartDesktopCallback,
                onStartMirroringClickListener = onStartMirroringCallback,
                onCancelClickListener = onCancelCallback,
                insetsProvider = { Insets.of(Rect()) },
            )
        kioskModeDialogDelegate.onCreate(dialog, null)

        val saveChoiceCheckbox = dialog.requireViewById<CheckBox>(R.id.save_connection_preference)
        assertThat(saveChoiceCheckbox.visibility).isEqualTo(View.GONE)
    }

    @Test
    fun onInsetsChanged_navBarInsets_updatesBottomMargin() {
        underTest.onCreate(dialog, null)
        underTest.onStart(dialog)

        val insets = buildInsets(WindowInsets.Type.navigationBars(), TEST_BOTTOM_INSETS)

        triggerInsetsChanged(WindowInsets.Type.navigationBars(), insets)

        assertThat(dialog.requireViewById<View>(R.id.cancel).marginBottom)
            .isEqualTo(TEST_BOTTOM_INSETS)
    }

    @Test
    fun onInsetsChanged_otherType_doesNotUpdateBottomPadding() {
        underTest.onCreate(dialog, null)
        underTest.onStart(dialog)

        val insets = buildInsets(WindowInsets.Type.ime(), TEST_BOTTOM_INSETS)
        triggerInsetsChanged(WindowInsets.Type.ime(), insets)

        assertThat(dialog.requireViewById<View>(R.id.cancel).marginBottom)
            .isNotEqualTo(TEST_BOTTOM_INSETS)
    }

    private fun buildInsets(@WindowInsets.Type.InsetsType type: Int, bottom: Int): WindowInsets {
        return WindowInsets.Builder().setInsets(type, Insets.of(0, 0, 0, bottom)).build()
    }

    private fun triggerInsetsChanged(type: Int, insets: WindowInsets) {
        verify(windowDecorView)
            .setWindowInsetsAnimationCallback(capture(windowInsetsAnimationCallbackCaptor))
        windowInsetsAnimationCallbackCaptor.value.onProgress(
            insets,
            listOf(WindowInsetsAnimation(type, Interpolators.INSTANT, 0)),
        )
    }

    private companion object {
        const val TEST_BOTTOM_INSETS = 1000 // arbitrarily high number
    }
}
