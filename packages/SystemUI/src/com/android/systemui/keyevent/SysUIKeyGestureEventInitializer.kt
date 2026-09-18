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

import android.app.contextualsearch.ContextualSearchManager
import android.content.res.Resources
import android.hardware.input.InputManager
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_ALL_APPS
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_CONTEXTUAL_CURSOR
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_CONTEXTUAL_SEARCH
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_TAKE_APP_WINDOW_SCREENSHOT
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_TAKE_PARTIAL_SCREENSHOT
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_NOTIFICATION_PANEL
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_QUICK_SETTINGS_PANEL
import android.util.Slog
import com.android.hardware.input.Flags.enableContextualCursorDesktopEntrypoints
import com.android.hardware.input.Flags.enableContextualSearchDesktopEntrypoints
import com.android.hardware.input.Flags.enablePartialScreenshotKeyboardShortcut
import com.android.hardware.input.Flags.enableQuickSettingsPanelShortcut
import com.android.systemui.CoreStartable
import com.android.systemui.LauncherProxyService
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.res.R
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.screencapture.domain.interactor.ScreenCaptureKeyboardShortcutInteractor
import com.android.systemui.shade.display.domain.interactor.ShadeExpansionTargetDisplayInteractor
import com.android.systemui.statusbar.CommandQueue
import com.android.wm.shell.shared.desktopmode.DesktopState
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
    private val desktopState: DesktopState,
    private val shadeExpansionTargetDisplayInteractor: ShadeExpansionTargetDisplayInteractor,
    private val screenCaptureKeyboardShortcutInteractor: ScreenCaptureKeyboardShortcutInteractor,
    private val launcherProxyService: LauncherProxyService,
) : CoreStartable {
    override fun start() {
        registerKeyGestureEventHandlers()
        registerKeyGestureEventListeners()
    }

    private fun registerKeyGestureEventHandlers() {
        val supportedGestures = mutableListOf<Int>()
        supportedGestures.add(KEY_GESTURE_TYPE_TOGGLE_NOTIFICATION_PANEL)
        if (enableQuickSettingsPanelShortcut()) {
            supportedGestures.add(KEY_GESTURE_TYPE_TOGGLE_QUICK_SETTINGS_PANEL)
        }
        // TODO(b/420714826) Determine if this shortcut should be registered only for large screen
        // devices.
        if (enablePartialScreenshotKeyboardShortcut()) {
            supportedGestures.add(KEY_GESTURE_TYPE_TAKE_PARTIAL_SCREENSHOT)
            supportedGestures.add(KEY_GESTURE_TYPE_TAKE_APP_WINDOW_SCREENSHOT)
        }
        if (enableContextualSearchDesktopEntrypoints()) {
            supportedGestures.add(KEY_GESTURE_TYPE_LAUNCH_CONTEXTUAL_SEARCH)
        }
        if (enableContextualCursorDesktopEntrypoints()) {
            supportedGestures.add(KEY_GESTURE_TYPE_LAUNCH_CONTEXTUAL_CURSOR)
        }
        if (supportedGestures.isEmpty()) {
            return
        }
        inputManager.registerKeyGestureEventHandler(supportedGestures) { event, _ ->
            when (event.keyGestureType) {
                KEY_GESTURE_TYPE_TAKE_PARTIAL_SCREENSHOT -> {
                    screenCaptureKeyboardShortcutInteractor.attemptPartialRegionScreenshot()
                }
                KEY_GESTURE_TYPE_TAKE_APP_WINDOW_SCREENSHOT -> {
                    screenCaptureKeyboardShortcutInteractor.attemptAppWindowScreenshot()
                }
                KEY_GESTURE_TYPE_TOGGLE_NOTIFICATION_PANEL -> {
                    if (desktopState.canEnterDesktopMode) {
                        // If device supports desktop windowing/connected displays, then
                        // reparent the shade to focused display, else open it where it is
                        shadeExpansionTargetDisplayInteractor.onNotificationPanelKeyboardShortcut()
                    }
                    commandQueue.toggleNotificationsPanel()
                }
                KEY_GESTURE_TYPE_TOGGLE_QUICK_SETTINGS_PANEL -> {
                    if (desktopState.canEnterDesktopMode) {
                        // If device supports desktop windowing/connected displays, then
                        // reparent the shade to focused display, else open it where it is
                        shadeExpansionTargetDisplayInteractor.onQSPanelKeyboardShortcut()
                    }
                    commandQueue.toggleQuickSettingsPanel()
                }
                // TODO: b/484184229 - Temporarily mapping the contextual cursor
                // shortcut to contextual search. A dedicated implementation for
                // the contextual cursor will follow in a later iteration.
                KEY_GESTURE_TYPE_LAUNCH_CONTEXTUAL_CURSOR,
                KEY_GESTURE_TYPE_LAUNCH_CONTEXTUAL_SEARCH -> {
                    launcherProxyService.proxy?.invokeContextualSearch(
                        ContextualSearchManager.ENTRYPOINT_KEYBOARD_SHORTCUT,
                        /* config= */ null,
                    )
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
