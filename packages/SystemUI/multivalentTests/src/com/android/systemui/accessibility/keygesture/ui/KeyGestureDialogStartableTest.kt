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

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.hardware.input.KeyGestureEvent
import android.os.Build
import android.view.Display.DEFAULT_DISPLAY
import android.view.Display.INVALID_DISPLAY
import android.view.KeyEvent
import android.view.accessibility.accessibilityManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.accessibility.common.KeyGestureEventConstants
import com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType
import com.android.systemui.SysuiTestCase
import com.android.systemui.accessibility.data.repository.FakeAccessibilityShortcutsRepository
import com.android.systemui.accessibility.data.repository.accessibilityShortcutsRepository
import com.android.systemui.accessibility.data.repository.fakeAccessibilityShortcutsRepository
import com.android.systemui.accessibility.keygesture.domain.KeyGestureDialogInteractor
import com.android.systemui.accessibility.keygesture.domain.keyGestureDialogInteractor
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.display.data.repository.displayRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.runTest
import com.android.systemui.runOnMainThreadAndWaitForIdleSync
import com.android.systemui.statusbar.phone.systemUIDialogFactory
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class KeyGestureDialogStartableTest : SysuiTestCase() {
    private companion object {
        const val MAGNIFICATION_TARGET_NAME =
            FakeAccessibilityShortcutsRepository.FAKE_MAGNIFICATION_TARGET_NAME
        const val SCREEN_READER_TARGET_NAME =
            FakeAccessibilityShortcutsRepository.FAKE_TALKBACK_TARGET_NAME
    }

    private val kosmos = testKosmosNew()

    private val Kosmos.underTest by
        Kosmos.Fixture {
            KeyGestureDialogStartable(
                context.apply {
                    addMockSystemService(Context.ACCESSIBILITY_SERVICE, accessibilityManager)
                },
                displayRepository,
                keyGestureDialogInteractor,
                systemUIDialogFactory,
                backgroundScope,
            )
        }

    @Before
    fun setUp() {
        onTeardown {
            runOnMainThreadAndWaitForIdleSync {
                with(kosmos) { underTest.currentDialog?.dismiss() }
            }
        }
    }

    @Test
    fun start_doesNotShowDialogByDefault() =
        kosmos.runTest {
            underTest.start()

            assertThat(underTest.currentDialog).isNull()
        }

    @Test
    fun start_onMagnificationInfoFlowCollected_showDialog() =
        kosmos.runTest {
            underTest.start()

            sendIntentBroadcastForMagnificationInMainThread()

            assertThat(underTest.currentDialog!!.isShowing).isTrue()
        }

    @Test
    fun start_onMagnificationDialogCreatedAndDismiss_dialogDismissed() =
        kosmos.runTest() {
            underTest.start()

            sendIntentBroadcastForMagnificationInMainThread()
            assertThat(underTest.currentDialog!!.isShowing).isTrue()
            runOnMainThreadAndWaitForIdleSync { underTest.currentDialog!!.dismiss() }

            assertThat(underTest.currentDialog).isNull()
        }

    @Test
    fun start_onMagnificationInfoFlowCollected_dialogShowing_ignoreAdditionalFlows() =
        kosmos.runTest {
            underTest.start()
            // Assume that we already have a magnification dialog showing up.
            sendIntentBroadcastForMagnificationInMainThread()
            assertThat(underTest.currentDialog!!.isShowing).isTrue()

            // Then, we collect a flow for Screen reader.
            sendIntentBroadcastForScreenReaderInMainThread(
                KeyGestureDialogInteractor.LAUNCH_DIALOG_ACTION
            )

            // Still show the Magnification dialog.
            assertThat(underTest.currentDialog!!.isShowing).isTrue()
            assertThat(underTest.dialogType)
                .isEqualTo(KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION)
        }

    @Test
    fun start_onMagReceivedAndDismiss_thenShowScreenReaderAgain_showSecondDialog() =
        kosmos.runTest {
            underTest.start()
            sendIntentBroadcastForMagnificationInMainThread()
            assertThat(underTest.currentDialog!!.isShowing).isTrue()

            runOnMainThreadAndWaitForIdleSync { underTest.currentDialog!!.dismiss() }
            sendIntentBroadcastForScreenReaderInMainThread(
                KeyGestureDialogInteractor.LAUNCH_DIALOG_ACTION
            )

            assertThat(underTest.currentDialog!!.isShowing).isTrue()
            assertThat(underTest.dialogType)
                .isEqualTo(KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER)
        }

    @Test
    fun start_invalidKeyGestureType_onNullFlowCollected_noDialog() =
        kosmos.runTest {
            underTest.start()

            sendIntentBroadcastInMainThread(
                keyGestureType = 0,
                metaState = KeyEvent.META_META_ON or KeyEvent.META_ALT_ON,
                keyCode = KeyEvent.KEYCODE_M,
                targetName = MAGNIFICATION_TARGET_NAME,
                displayId = DEFAULT_DISPLAY,
            )

            assertThat(underTest.currentDialog).isNull()
        }

    @Test
    fun start_invalidMetaState_onNullFlowCollected_noDialog() =
        kosmos.runTest {
            underTest.start()

            sendIntentBroadcastInMainThread(
                keyGestureType = KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION,
                metaState = 0,
                keyCode = KeyEvent.KEYCODE_M,
                targetName = MAGNIFICATION_TARGET_NAME,
                displayId = DEFAULT_DISPLAY,
            )

            assertThat(underTest.currentDialog).isNull()
        }

    @Test
    fun start_invalidKeyCode_onNullFlowCollected_noDialog() =
        kosmos.runTest {
            underTest.start()

            sendIntentBroadcastInMainThread(
                keyGestureType = KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION,
                metaState = KeyEvent.META_META_ON or KeyEvent.META_ALT_ON,
                keyCode = 0,
                targetName = MAGNIFICATION_TARGET_NAME,
                displayId = DEFAULT_DISPLAY,
            )

            assertThat(underTest.currentDialog).isNull()
        }

    @Test
    fun start_invalidTargetName_onNullFlowCollected_noDialog() =
        kosmos.runTest {
            underTest.start()

            sendIntentBroadcastInMainThread(
                keyGestureType = KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION,
                metaState = KeyEvent.META_META_ON or KeyEvent.META_ALT_ON,
                keyCode = KeyEvent.KEYCODE_M,
                targetName = "",
                displayId = DEFAULT_DISPLAY,
            )

            assertThat(underTest.currentDialog).isNull()
        }

    @Test
    fun start_invalidDisplayId_onNullFlowCollected_noDialog() =
        kosmos.runTest {
            underTest.start()

            sendIntentBroadcastInMainThread(
                keyGestureType = KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION,
                metaState = KeyEvent.META_META_ON or KeyEvent.META_ALT_ON,
                keyCode = KeyEvent.KEYCODE_M,
                targetName = MAGNIFICATION_TARGET_NAME,
                displayId = INVALID_DISPLAY,
            )

            assertThat(underTest.currentDialog).isNull()
        }

    @Test
    fun start_onMagnificationDialog_enablesShortcutAndZoomsIn() =
        kosmos.runTest {
            underTest.start()
            assertThat(getAssignedTargetNames()).isEmpty()
            assertThat(getEnabledTargetNames()).isEmpty()

            sendIntentBroadcastForMagnificationInMainThread()

            assertThat(underTest.currentDialog!!.isShowing).isTrue()
            assertThat(getAssignedTargetNames()).contains(MAGNIFICATION_TARGET_NAME)
            assertThat(getEnabledTargetNames()).contains(MAGNIFICATION_TARGET_NAME)
        }

    @Test
    fun start_onMagnificationDialog_opensCorrectlyOnNonDefaultDisplay() =
        kosmos.runTest {
            val spyUnderTest = spy(underTest)

            doNothing().whenever(spyUnderTest).createDialog(anyOrNull())

            val externalDisplayId = 2
            spyUnderTest.start()

            sendIntentBroadcastForMagnificationInMainThread(displayId = externalDisplayId)

            verify(spyUnderTest)
                .createDialog(argThat { info -> info.displayId == externalDisplayId })
        }

    @Test
    fun start_TalkbackAlreadyOn_showTalkBackDialog_noTtsPrompt() =
        kosmos.runTest {
            val a11yServiceInfo = getMockAccessibilityServiceInfo(SCREEN_READER_TARGET_NAME)
            whenever(
                    accessibilityManager.getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_ALL_MASK
                    )
                )
                .thenReturn(listOf(a11yServiceInfo))
            underTest.start()

            sendIntentBroadcastForScreenReaderInMainThread(
                KeyGestureDialogInteractor.LAUNCH_DIALOG_ACTION
            )

            assertThat(underTest.currentDialog!!.isShowing).isTrue()
            assertThat(fakeAccessibilityShortcutsRepository.ttsPrompt).isNull()
        }

    @Test
    fun start_screenReaderDialog_performsTtsPrompt() =
        kosmos.runTest {
            underTest.start()

            sendIntentBroadcastForScreenReaderInMainThread(
                KeyGestureDialogInteractor.LAUNCH_DIALOG_ACTION
            )

            assertThat(underTest.currentDialog!!.isShowing).isTrue()
            // Screen Reader dialog will create a `TtsPrompt`, so it shouldn't be null.
            assertThat(fakeAccessibilityShortcutsRepository.ttsPrompt).isNotNull()
            // Verify the text used to create the `TtsPrompt` instance above is about Screen Reader
            // dialog.
            // TODO: b/432568819 - Update the expected string here after we get the new tts text
            // from UXW to create the `TtsPrompt` for Screen Reader dialog in production code.
            assertThat(fakeAccessibilityShortcutsRepository.ttsText)
                .isEqualTo("Press Action + Alt + T again to enable Screen Reader")
            assertThat(getAssignedTargetNames()).contains(SCREEN_READER_TARGET_NAME)
        }

    @Test
    fun start_screenReaderDialog_opensCorrectlyOnNonDefaultDisplay() =
        kosmos.runTest {
            val spyUnderTest = spy(underTest)

            doNothing().whenever(spyUnderTest).createDialog(anyOrNull())

            val externalDisplayId = 2
            spyUnderTest.start()

            sendIntentBroadcastForScreenReaderInMainThread(
                KeyGestureDialogInteractor.LAUNCH_DIALOG_ACTION,
                externalDisplayId,
            )

            verify(spyUnderTest)
                .createDialog(argThat { info -> info.displayId == externalDisplayId })
        }

    @Test
    fun start_screenReaderDialog_dismissDialog() =
        kosmos.runTest {
            underTest.start()
            sendIntentBroadcastForScreenReaderInMainThread(
                KeyGestureDialogInteractor.LAUNCH_DIALOG_ACTION
            )
            assertThat(underTest.currentDialog!!.isShowing).isTrue()
            assertThat(underTest.dialogType)
                .isEqualTo(KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER)

            // While the Screen Reader dialog is showing, we received a dismissal request to dismiss
            // it.
            sendIntentBroadcastForScreenReaderInMainThread(
                KeyGestureDialogInteractor.DISMISS_DIALOG_ACTION
            )

            assertThat(underTest.currentDialog).isNull()
        }

    @Test
    fun start_magnificationDialog_receivedDismissScreenReaderDialogRequest_doNothing() =
        kosmos.runTest {
            underTest.start()
            sendIntentBroadcastForMagnificationInMainThread()
            assertThat(underTest.currentDialog!!.isShowing).isTrue()
            assertThat(underTest.dialogType)
                .isEqualTo(KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION)

            // While the Magnification dialog is showing, we received a dismissal request to dismiss
            // it.
            sendIntentBroadcastForScreenReaderInMainThread(
                KeyGestureDialogInteractor.DISMISS_DIALOG_ACTION
            )

            // Because the current existing dialog type isn't Screen reader, so we will not dismiss
            // it.
            assertThat(underTest.currentDialog).isNotNull()
        }

    @Test
    fun start_noExistingDialog_receivedDismissScreenReaderDialogRequest_doNothing() =
        kosmos.runTest {
            underTest.start()

            // While there is no dialog, we received a dismissal request. It will do nothing.
            sendIntentBroadcastForScreenReaderInMainThread(
                KeyGestureDialogInteractor.DISMISS_DIALOG_ACTION
            )

            assertThat(underTest.currentDialog).isNull()
        }

    private fun Kosmos.sendIntentBroadcastInMainThread(
        keyGestureType: Int,
        metaState: Int,
        keyCode: Int,
        targetName: String,
        displayId: Int,
        intentAction: String = KeyGestureDialogInteractor.LAUNCH_DIALOG_ACTION,
    ) =
        // Sending broadcast to create SysUi dialog should be run in main thread.
        runOnMainThreadAndWaitForIdleSync {
            broadcastDispatcher.sendIntentToMatchingReceiversOnly(
                context,
                Intent().apply {
                    action = intentAction
                    putExtra(KeyGestureEventConstants.KEY_GESTURE_TYPE, keyGestureType)
                    putExtra(KeyGestureEventConstants.META_STATE, metaState)
                    putExtra(KeyGestureEventConstants.KEY_CODE, keyCode)
                    putExtra(KeyGestureEventConstants.TARGET_NAME, targetName)
                    putExtra(KeyGestureEventConstants.DISPLAY_ID, displayId)
                },
            )
        }

    private fun Kosmos.sendIntentBroadcastForMagnificationInMainThread(
        displayId: Int = DEFAULT_DISPLAY
    ) =
        sendIntentBroadcastInMainThread(
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION,
            KeyEvent.META_META_ON or KeyEvent.META_ALT_ON,
            KeyEvent.KEYCODE_M,
            MAGNIFICATION_TARGET_NAME,
            displayId,
        )

    private fun Kosmos.sendIntentBroadcastForScreenReaderInMainThread(
        intentAction: String,
        displayId: Int = DEFAULT_DISPLAY,
    ) =
        sendIntentBroadcastInMainThread(
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER,
            KeyEvent.META_META_ON or KeyEvent.META_ALT_ON,
            KeyEvent.KEYCODE_T,
            SCREEN_READER_TARGET_NAME,
            displayId,
            intentAction,
        )

    private fun Kosmos.getAssignedTargetNames(): Set<String> =
        accessibilityShortcutsRepository
            .getSelectedAccessibilityTargetsInfo(UserShortcutType.KEY_GESTURE)
            .map { it.targetName }
            .toSet()

    private fun Kosmos.getEnabledTargetNames(): Set<String> =
        accessibilityShortcutsRepository
            .getAllAccessibilityTargetsInfo(UserShortcutType.KEY_GESTURE)
            .filter { it.isStateOn }
            .map { it.targetName }
            .toSet()

    private fun getMockAccessibilityServiceInfo(targetName: String): AccessibilityServiceInfo {
        val componentName: ComponentName = ComponentName.unflattenFromString(targetName)!!
        val iconResId = 1

        return AccessibilityServiceInfo().apply {
            this.componentName = componentName
            this.resolveInfo =
                ResolveInfo().apply {
                    this.serviceInfo =
                        ServiceInfo().apply {
                            this.applicationInfo =
                                ApplicationInfo().apply {
                                    this.packageName = componentName.packageName
                                    this.icon = iconResId
                                    this.targetSdkVersion = Build.VERSION_CODES.BAKLAVA
                                }
                            this.name = componentName.className
                            this.packageName = componentName.packageName
                            this.icon = iconResId
                        }
                    this.nonLocalizedLabel = componentName.shortClassName
                    this.icon = iconResId
                    this.iconResourceId = iconResId
                }
        }
    }
}
