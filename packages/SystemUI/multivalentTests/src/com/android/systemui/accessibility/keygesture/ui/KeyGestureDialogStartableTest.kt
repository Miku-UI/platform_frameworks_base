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

package com.android.systemui.accessibility.keygesture.ui

import android.hardware.input.KeyGestureEvent
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.annotation.UiThreadTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.hardware.input.Flags
import com.android.internal.accessibility.util.TtsPrompt
import com.android.systemui.SysuiTestCase
import com.android.systemui.accessibility.keygesture.domain.KeyGestureDialogInteractor
import com.android.systemui.accessibility.keygesture.shared.model.KeyGestureConfirmInfo
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.testScope
import com.android.systemui.statusbar.phone.systemUIDialogFactory
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@UiThreadTest
@EnableFlags(Flags.FLAG_ENABLE_TALKBACK_AND_MAGNIFIER_KEY_GESTURES)
class KeyGestureDialogStartableTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope

    // Mocks
    private val mockInteractor = mock<KeyGestureDialogInteractor>()
    private val mockTtsPrompt = mock<TtsPrompt>()

    private val confirmInfoFlow = MutableStateFlow<KeyGestureConfirmInfo?>(null)
    private lateinit var underTest: KeyGestureDialogStartable

    @Before
    fun setUp() {
        whenever(mockInteractor.keyGestureConfirmDialogRequest).thenReturn(confirmInfoFlow)
        whenever(mockInteractor.performTtsPromptForText(any())).thenReturn(mockTtsPrompt)

        underTest =
            KeyGestureDialogStartable(
                mockInteractor,
                kosmos.systemUIDialogFactory,
                kosmos.applicationCoroutineScope,
            )
    }

    @After
    fun tearDown() {
        // If we show the dialog, we must dismiss the dialog at the end of the test on the main
        // thread.
        if (::underTest.isInitialized) {
            underTest.currentDialog?.dismiss()
        }
    }

    @Test
    fun start_doesNotShowDialogByDefault() =
        testScope.runTest {
            underTest.start()
            runCurrent()

            assertThat(underTest.currentDialog).isNull()
        }

    @Test
    @Ignore("b/425722546 - we have one in review CL ag/35510953 for fixing the crash")
    @DisableFlags(Flags.FLAG_ENABLE_MAGNIFY_MAGNIFICATION_KEY_GESTURE_DIALOG)
    fun start_onMagnificationInfoFlowCollected_showDialog() =
        testScope.runTest {
            underTest.start()
            runCurrent()

            val magnificationInfo =
                KeyGestureConfirmInfo(
                    keyGestureType = KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION,
                    title = "Magnification",
                    contentText = "Enable magnification?",
                    targetName = "targetNameForMagnification",
                    actionKeyIconResId = 0,
                    displayId = DEFAULT_DISPLAY,
                )
            confirmInfoFlow.value = magnificationInfo
            runCurrent()

            assertThat(underTest.currentDialog!!.isShowing).isTrue()
        }

    @Test
    @Ignore("b/425722546 - we have one in review CL ag/35510953 for fixing the crash")
    @DisableFlags(Flags.FLAG_ENABLE_MAGNIFY_MAGNIFICATION_KEY_GESTURE_DIALOG)
    fun start_onMagnificationInfoFlowCollected_dialogShowing_ignoreAdditionalFlows() =
        testScope.runTest {
            underTest.start()
            runCurrent()
            // Assume that we already have a magnification dialog showing up.
            val magnificationInfo =
                KeyGestureConfirmInfo(
                    keyGestureType = KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION,
                    title = "Magnification",
                    contentText = "Enable magnification?",
                    targetName = "targetNameForMagnification",
                    actionKeyIconResId = 0,
                    displayId = DEFAULT_DISPLAY,
                )
            confirmInfoFlow.value = magnificationInfo
            runCurrent()
            assertThat(underTest.currentDialog!!.isShowing).isTrue()

            // Then, we collect a flow for Screen reader.
            val screenReaderInfo =
                KeyGestureConfirmInfo(
                    keyGestureType = KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER,
                    title = "Screen Reader",
                    contentText = "Enable screen reader?",
                    targetName = "targetNameForScreenReader",
                    actionKeyIconResId = 0,
                    displayId = DEFAULT_DISPLAY,
                )
            confirmInfoFlow.value = screenReaderInfo
            runCurrent()

            // Still show the Magnification dialog.
            assertThat(underTest.currentDialog!!.isShowing).isTrue()
            assertThat(underTest.dialogType)
                .isEqualTo(KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION)
        }

    @Test
    fun start_onNullFlowCollected_noDialog() =
        testScope.runTest {
            underTest.start()
            runCurrent()

            confirmInfoFlow.value = null
            runCurrent()

            assertThat(underTest.currentDialog).isNull()
        }

    @Test
    @Ignore("b/425722546 - failed because of dismiss listener")
    @EnableFlags(Flags.FLAG_ENABLE_MAGNIFY_MAGNIFICATION_KEY_GESTURE_DIALOG)
    fun start_onMagnificationDialog_enablesShortcutAndZoomsIn() =
        testScope.runTest {
            underTest.start()
            runCurrent()

            val magnificationInfo =
                KeyGestureConfirmInfo(
                    keyGestureType = KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION,
                    title = "Magnification",
                    contentText = "Enable magnification?",
                    targetName = "targetNameForMagnification",
                    actionKeyIconResId = 0,
                    displayId = DEFAULT_DISPLAY,
                )
            confirmInfoFlow.value = magnificationInfo
            runCurrent()

            assertThat(underTest.currentDialog!!.isShowing).isTrue()
            verify(mockInteractor)
                .enableShortcutsForTargets(eq(true), eq(magnificationInfo.targetName))
            verify(mockInteractor).enableMagnificationAndZoomIn(eq(magnificationInfo.displayId))
        }
}
