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

package com.android.systemui.keyevent

import android.content.res.Resources
import android.hardware.input.InputManager
import android.hardware.input.InputManager.KeyGestureEventHandler
import android.hardware.input.InputManager.KeyGestureEventListener
import android.hardware.input.KeyGestureEvent
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_ALL_APPS
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_TAKE_PARTIAL_SCREENSHOT
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_NOTIFICATION_PANEL
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_QUICK_SETTINGS_PANEL
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_TASKBAR
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags.FLAG_SCENE_CONTAINER
import com.android.systemui.SysuiTestCase
import com.android.systemui.res.R
import com.android.systemui.screencapture.domain.interactor.ScreenCaptureKeyboardShortcutInteractor
import com.android.systemui.shade.display.StatusBarTouchShadeDisplayPolicy
import com.android.systemui.statusbar.CommandQueue
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class SysUIKeyGestureEventInitializerTest : SysuiTestCase() {
    @JvmField @Rule var mockitoRule = MockitoJUnit.rule()
    @Mock private lateinit var inputManager: InputManager
    @Mock private lateinit var commandQueue: CommandQueue
    @Mock private lateinit var shadeDisplayPolicy: StatusBarTouchShadeDisplayPolicy
    @Mock
    private lateinit var screenCaptureKeyboardShortcutInteractor:
        ScreenCaptureKeyboardShortcutInteractor
    @Mock private lateinit var resources: Resources
    @Captor private lateinit var keyGestureEventsCaptor: ArgumentCaptor<List<Int>>
    @Captor
    private lateinit var keyGestureEventHandlerCaptor: ArgumentCaptor<KeyGestureEventHandler>
    @Captor
    private lateinit var keyGestureEventListenerCaptor: ArgumentCaptor<KeyGestureEventListener>

    private lateinit var underTest: SysUIKeyGestureEventInitializer

    @Before
    fun setup() {
        underTest =
            SysUIKeyGestureEventInitializer(
                context.mainExecutor,
                resources,
                inputManager,
                commandQueue,
                shadeDisplayPolicy,
                screenCaptureKeyboardShortcutInteractor,
            )
    }

    @Test
    @EnableFlags(
        com.android.window.flags.Flags.FLAG_ENABLE_KEY_GESTURE_HANDLER_FOR_SYSUI,
        com.android.hardware.input.Flags.FLAG_ENABLE_QUICK_SETTINGS_PANEL_SHORTCUT,
        com.android.hardware.input.Flags.FLAG_ENABLE_PARTIAL_SCREENSHOT_KEYBOARD_SHORTCUT,
    )
    fun start_flagEnabled_registerKeyGestureEvents() {
        underTest.start()

        verify(inputManager).registerKeyGestureEventHandler(keyGestureEventsCaptor.capture(), any())
        keyGestureEventsCaptor.value.let { keyGestureEvents ->
            assertThat(keyGestureEvents)
                .containsExactly(
                    KEY_GESTURE_TYPE_TOGGLE_NOTIFICATION_PANEL,
                    KEY_GESTURE_TYPE_TOGGLE_QUICK_SETTINGS_PANEL,
                    KEY_GESTURE_TYPE_TAKE_PARTIAL_SCREENSHOT,
                )
        }
    }

    @Test
    @DisableFlags(
        com.android.window.flags.Flags.FLAG_ENABLE_KEY_GESTURE_HANDLER_FOR_SYSUI,
        com.android.hardware.input.Flags.FLAG_ENABLE_QUICK_SETTINGS_PANEL_SHORTCUT,
        com.android.hardware.input.Flags.FLAG_ENABLE_PARTIAL_SCREENSHOT_KEYBOARD_SHORTCUT,
    )
    fun start_flagDisabled_noRegisterKeyGestureEvents() {
        underTest.start()

        verifyNoInteractions(inputManager)
    }

    @Test
    @EnableFlags(com.android.window.flags.Flags.FLAG_ENABLE_KEY_GESTURE_HANDLER_FOR_SYSUI)
    fun handleKeyGestureEvent_eventTypeToggleNotificationPanel_toggleNotificationPanel() {
        underTest.start()
        verify(inputManager)
            .registerKeyGestureEventHandler(any(), keyGestureEventHandlerCaptor.capture())

        keyGestureEventHandlerCaptor.value.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setKeyGestureType(KEY_GESTURE_TYPE_TOGGLE_NOTIFICATION_PANEL)
                .build(),
            /* focusedToken= */ null,
        )

        verify(shadeDisplayPolicy).onNotificationPanelKeyboardShortcut()
        verify(commandQueue).toggleNotificationsPanel()
    }

    @Test
    @EnableFlags(com.android.hardware.input.Flags.FLAG_ENABLE_QUICK_SETTINGS_PANEL_SHORTCUT)
    fun handleKeyGestureEvent_eventTypeToggleQuickSettingsPanel_toggleQuickSettingsPanel() {
        underTest.start()
        verify(inputManager)
            .registerKeyGestureEventHandler(any(), keyGestureEventHandlerCaptor.capture())

        keyGestureEventHandlerCaptor.value.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setKeyGestureType(KEY_GESTURE_TYPE_TOGGLE_QUICK_SETTINGS_PANEL)
                .build(),
            /* focusedToken= */ null,
        )

        verify(shadeDisplayPolicy).onQSPanelKeyboardShortcut()
        verify(commandQueue).toggleQuickSettingsPanel()
    }

    @Test
    @EnableFlags(com.android.hardware.input.Flags.FLAG_ENABLE_PARTIAL_SCREENSHOT_KEYBOARD_SHORTCUT)
    fun handleKeyGestureEvent_eventTypeTakePartialScreenshot_callsScreenCaptureInteractor() {
        underTest.start()
        verify(inputManager)
            .registerKeyGestureEventHandler(any(), keyGestureEventHandlerCaptor.capture())

        keyGestureEventHandlerCaptor.value.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setKeyGestureType(KEY_GESTURE_TYPE_TAKE_PARTIAL_SCREENSHOT)
                .build(),
            /* focusedToken= */ null,
        )

        verify(screenCaptureKeyboardShortcutInteractor).attemptPartialRegionScreenshot()
    }

    @Test
    @EnableFlags(com.android.window.flags.Flags.FLAG_ENABLE_KEY_GESTURE_HANDLER_FOR_SYSUI)
    fun handleKeyGestureEvent_otherEventTypeToggleNotificationPanel_noInteraction() {
        underTest.start()
        verify(inputManager)
            .registerKeyGestureEventHandler(any(), keyGestureEventHandlerCaptor.capture())

        keyGestureEventHandlerCaptor.value.handleKeyGestureEvent(
            KeyGestureEvent.Builder().setKeyGestureType(KEY_GESTURE_TYPE_TOGGLE_TASKBAR).build(),
            /* focusedToken= */ null,
        )

        verifyNoInteractions(commandQueue)
    }

    @Test
    @EnableFlags(FLAG_SCENE_CONTAINER)
    fun observeKeyGestureEvent_configEnableHideNotificationsShadeOff_noInteraction() {
        whenever(resources.getBoolean(R.bool.config_enableHideNotificationsShadeOnAllAppsKey))
            .thenReturn(false)
        underTest.start()
        verify(inputManager, never()).registerKeyGestureEventListener(any(), any())
        verifyNoInteractions(commandQueue)
    }

    @Test
    @EnableFlags(FLAG_SCENE_CONTAINER)
    fun observeKeyGestureEvent_configEnableHideNotificationsShadeOn_animateCollapsePanels() {
        whenever(resources.getBoolean(R.bool.config_enableHideNotificationsShadeOnAllAppsKey))
            .thenReturn(true)
        underTest.start()
        verify(inputManager)
            .registerKeyGestureEventListener(any(), keyGestureEventListenerCaptor.capture())

        keyGestureEventListenerCaptor.value.onKeyGestureEvent(
            KeyGestureEvent.Builder().setKeyGestureType(KEY_GESTURE_TYPE_ALL_APPS).build()
        )

        verify(commandQueue).animateCollapsePanels()
    }
}
