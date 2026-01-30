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
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_ALL_APPS
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_TAKE_PARTIAL_SCREENSHOT
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_NOTIFICATION_PANEL
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_QUICK_SETTINGS_PANEL
import android.util.Slog
import com.android.hardware.input.Flags.enablePartialScreenshotKeyboardShortcut
import com.android.hardware.input.Flags.enableQuickSettingsPanelShortcut
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.res.R
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.screencapture.domain.interactor.ScreenCaptureKeyboardShortcutInteractor
import com.android.systemui.shade.display.StatusBarTouchShadeDisplayPolicy
import com.android.systemui.statusbar.CommandQueue
import com.android.window.flags.Flags.enableKeyGestureHandlerForSysui
import java.util.concurrent.Executor
import javax.inject.Inject

/**
 * Registers system UI interested keyboard shortcut events and dispatches events to the correct
 * handlers.
 */
@SysUISingleton
class SysUIKeyGestureEventInitializer
@Inject
constructor(
    @Main private val mainExecutor: Executor,
    @Main private val resources: Resources,
    private val inputManager: InputManager,
    private val commandQueue: CommandQueue,
    private val shadeDisplayPolicy: StatusBarTouchShadeDisplayPolicy,
    private val screenCaptureKeyboardShortcutInteractor: ScreenCaptureKeyboardShortcutInteractor,
) : CoreStartable {
    override fun start() {
        registerKeyGestureEventHandlers()
        registerKeyGestureEventListeners()
    }

    private fun registerKeyGestureEventHandlers() {
        val supportedGestures = mutableListOf<Int>()
        if (enableKeyGestureHandlerForSysui()) {
            supportedGestures.add(KEY_GESTURE_TYPE_TOGGLE_NOTIFICATION_PANEL)
        }
        if (enableQuickSettingsPanelShortcut()) {
            supportedGestures.add(KEY_GESTURE_TYPE_TOGGLE_QUICK_SETTINGS_PANEL)
        }
        // TODO(b/420714826) Determine if this shortcut should be registered only for large screen
        // devices.
        if (enablePartialScreenshotKeyboardShortcut()) {
            supportedGestures.add(KEY_GESTURE_TYPE_TAKE_PARTIAL_SCREENSHOT)
        }
        if (supportedGestures.isEmpty()) {
            return
        }
        inputManager.registerKeyGestureEventHandler(supportedGestures) { event, _ ->
            when (event.keyGestureType) {
                KEY_GESTURE_TYPE_TAKE_PARTIAL_SCREENSHOT -> {
                    screenCaptureKeyboardShortcutInteractor.attemptPartialRegionScreenshot()
                }
                KEY_GESTURE_TYPE_TOGGLE_NOTIFICATION_PANEL -> {
                    shadeDisplayPolicy.onNotificationPanelKeyboardShortcut()
                    commandQueue.toggleNotificationsPanel()
                }
                KEY_GESTURE_TYPE_TOGGLE_QUICK_SETTINGS_PANEL -> {
                    shadeDisplayPolicy.onQSPanelKeyboardShortcut()
                    commandQueue.toggleQuickSettingsPanel()
                }
                else ->
                    Slog.w(TAG, "Unsupported key gesture event handler: ${event.keyGestureType}")
            }
        }
    }

    private fun registerKeyGestureEventListeners() {
        val enableHideNotificationsShade =
            resources.getBoolean(R.bool.config_enableHideNotificationsShadeOnAllAppsKey)
        if (!SceneContainerFlag.isEnabled || !enableHideNotificationsShade) {
            return
        }
        inputManager.registerKeyGestureEventListener(mainExecutor) { event ->
            if (event.keyGestureType == KEY_GESTURE_TYPE_ALL_APPS) {
                commandQueue.animateCollapsePanels()
            }
        }
    }

    private companion object {
        const val TAG = "KeyGestureEventInitializer"
    }
}
