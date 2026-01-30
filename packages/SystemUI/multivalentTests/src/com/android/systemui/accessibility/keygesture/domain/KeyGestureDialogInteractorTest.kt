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

package com.android.systemui.accessibility.keygesture.domain

import android.content.Intent
import android.content.applicationContext
import android.hardware.input.KeyGestureEvent
import android.os.fakeExecutorHandler
import android.view.Display.DEFAULT_DISPLAY
import android.view.Display.INVALID_DISPLAY
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.accessibility.common.KeyGestureEventConstants
import com.android.systemui.SysuiTestCase
import com.android.systemui.accessibility.data.repository.AccessibilityShortcutsRepository
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify

@SmallTest
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class KeyGestureDialogInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val broadcastDispatcher = kosmos.broadcastDispatcher
    private val testDispatcher = kosmos.testDispatcher
    private val testScope = kosmos.testScope

    // mocks
    private val mockRepository = mock(AccessibilityShortcutsRepository::class.java)

    private lateinit var underTest: KeyGestureDialogInteractor

    @Before
    fun setUp() {
        underTest =
            KeyGestureDialogInteractor(
                kosmos.applicationContext,
                mockRepository,
                broadcastDispatcher,
                testDispatcher,
                kosmos.fakeExecutorHandler,
            )
    }

    @Test
    fun enableShortcutsForTargets_enabledShortcutsForFakeTarget() {
        val enabledTargetName = "fakeTargetName"

        underTest.enableShortcutsForTargets(/* enable= */ true, enabledTargetName)

        verify(mockRepository).enableShortcutsForTargets(eq(true), eq(enabledTargetName))
    }

    @Test
    fun enableMagnificationAndZoomIn_validDisplayId_delegatesToRepository() {
        underTest.enableMagnificationAndZoomIn(DEFAULT_DISPLAY)

        verify(mockRepository).enableMagnificationAndZoomIn(eq(DEFAULT_DISPLAY))
    }

    @Test
    fun keyGestureConfirmDialogRequest_invalidKeyGestureTypeReceived_flowIsNull() {
        testScope.runTest {
            val keyGestureConfirmInfo by collectLastValue(underTest.keyGestureConfirmDialogRequest)
            runCurrent()

            sendIntentBroadcast(
                keyGestureType = 0,
                metaState = KeyEvent.META_META_ON or KeyEvent.META_ALT_ON,
                keyCode = KeyEvent.KEYCODE_M,
                targetName = "targetNameForMagnification",
                displayId = DEFAULT_DISPLAY,
            )
            runCurrent()

            assertThat(keyGestureConfirmInfo).isNull()
        }
    }

    @Test
    fun keyGestureConfirmDialogRequest_invalidMetaStateReceived_flowIsNull() {
        testScope.runTest {
            val keyGestureConfirmInfo by collectLastValue(underTest.keyGestureConfirmDialogRequest)
            runCurrent()

            sendIntentBroadcast(
                keyGestureType = KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION,
                metaState = 0,
                keyCode = KeyEvent.KEYCODE_M,
                targetName = "targetNameForMagnification",
                displayId = DEFAULT_DISPLAY,
            )
            runCurrent()

            assertThat(keyGestureConfirmInfo).isNull()
        }
    }

    @Test
    fun keyGestureConfirmDialogRequest_invalidKeyCodeReceived_flowIsNull() {
        testScope.runTest {
            val keyGestureConfirmInfo by collectLastValue(underTest.keyGestureConfirmDialogRequest)
            runCurrent()

            sendIntentBroadcast(
                keyGestureType = KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION,
                metaState = KeyEvent.META_META_ON or KeyEvent.META_ALT_ON,
                keyCode = 0,
                targetName = "targetNameForMagnification",
                displayId = DEFAULT_DISPLAY,
            )
            runCurrent()

            assertThat(keyGestureConfirmInfo).isNull()
        }
    }

    @Test
    fun keyGestureConfirmDialogRequest_invalidTargetNameReceived_flowIsNull() {
        testScope.runTest {
            val keyGestureConfirmInfo by collectLastValue(underTest.keyGestureConfirmDialogRequest)
            runCurrent()

            sendIntentBroadcast(
                keyGestureType = KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION,
                metaState = KeyEvent.META_META_ON or KeyEvent.META_ALT_ON,
                keyCode = KeyEvent.KEYCODE_M,
                targetName = "",
                displayId = DEFAULT_DISPLAY,
            )
            runCurrent()

            assertThat(keyGestureConfirmInfo).isNull()
        }
    }

    @Test
    fun keyGestureConfirmDialogRequest_invalidDisplayIdReceived_flowIsNull() {
        testScope.runTest {
            val keyGestureConfirmInfo by collectLastValue(underTest.keyGestureConfirmDialogRequest)
            runCurrent()

            sendIntentBroadcast(
                keyGestureType = KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION,
                metaState = KeyEvent.META_META_ON or KeyEvent.META_ALT_ON,
                keyCode = KeyEvent.KEYCODE_M,
                targetName = "targetNameForMagnification",
                displayId = INVALID_DISPLAY,
            )
            runCurrent()

            assertThat(keyGestureConfirmInfo).isNull()
        }
    }

    @Test
    fun keyGestureConfirmDialogRequest_getFlowFromIntentForMagnification() {
        testScope.runTest {
            val keyGestureType = KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION
            val metaState = KeyEvent.META_META_ON or KeyEvent.META_ALT_ON
            val keyCode = KeyEvent.KEYCODE_M
            val testTargetName = "targetNameForMagnification"
            collectLastValue(underTest.keyGestureConfirmDialogRequest)
            runCurrent()

            sendIntentBroadcast(keyGestureType, metaState, keyCode, testTargetName, DEFAULT_DISPLAY)
            runCurrent()

            verify(mockRepository)
                .getTitleToContentForKeyGestureDialog(
                    eq(keyGestureType),
                    eq(metaState),
                    eq(keyCode),
                    eq(testTargetName),
                )
        }
    }

    private fun sendIntentBroadcast(
        keyGestureType: Int,
        metaState: Int,
        keyCode: Int,
        targetName: String,
        displayId: Int,
    ) {
        val intent =
            Intent().apply {
                action = KeyGestureDialogInteractor.ACTION
                putExtra(KeyGestureEventConstants.KEY_GESTURE_TYPE, keyGestureType)
                putExtra(KeyGestureEventConstants.META_STATE, metaState)
                putExtra(KeyGestureEventConstants.KEY_CODE, keyCode)
                putExtra(KeyGestureEventConstants.TARGET_NAME, targetName)
                putExtra(KeyGestureEventConstants.DISPLAY_ID, displayId)
            }

        broadcastDispatcher.sendIntentToMatchingReceiversOnly(context, intent)
    }
}
