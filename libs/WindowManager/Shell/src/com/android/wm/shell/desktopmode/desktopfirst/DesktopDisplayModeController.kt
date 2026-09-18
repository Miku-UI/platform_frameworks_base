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

package com.android.wm.shell.desktopmode.desktopfirst

import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED
import android.app.WindowConfiguration.windowingModeToString
import android.content.Context
import android.hardware.devicestate.DeviceState
import android.hardware.devicestate.DeviceStateManager
import android.hardware.input.InputManager
import android.os.Handler
import android.os.SystemProperties
import android.provider.Settings
import android.provider.Settings.Global.DEVELOPMENT_FORCE_DESKTOP_MODE_ON_EXTERNAL_DISPLAYS
import android.util.IndentingPrintWriter
import android.view.Display.DEFAULT_DISPLAY
import android.view.IWindowManager
import android.view.InputDevice
import android.view.WindowManager.TRANSIT_CHANGE
import android.window.DesktopExperienceFlags
import android.window.WindowContainerTransaction
import com.android.internal.annotations.VisibleForTesting
import com.android.internal.protolog.ProtoLog
import com.android.window.flags.Flags
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.desktopmode.desktopwallpaperactivity.DesktopWallpaperActivityTokenProvider
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.sysui.ShellCommandHandler
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.sysui.UserChangeListener
import com.android.wm.shell.transition.Transitions
import java.io.PrintWriter

/** Controls the display windowing mode in desktop mode */
class DesktopDisplayModeController(
    private val context: Context,
    shellInit: ShellInit,
    private val shellCommandHandler: ShellCommandHandler,
    private val shellController: ShellController,
    private val transitions: Transitions,
    private val rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer,
    private val windowManager: IWindowManager,
    private val shellTaskOrganizer: ShellTaskOrganizer,
    private val desktopWallpaperActivityTokenProvider: DesktopWallpaperActivityTokenProvider,
    private val inputManager: InputManager,
    private val displayController: DisplayController,
    @ShellMainThread private val mainHandler: Handler,
    @ShellMainThread private val mainExecutor: ShellExecutor,
    private val desktopState: DesktopState,
    private val deviceStateManager: DeviceStateManager,
) {

    /**
     * Debug flag to indicate whether to force default display to be in desktop-first mode
     * regardless of required factors.
     */
    private val FORCE_DESKTOP_FIRST_ON_DEFAULT_DISPLAY =
        SystemProperties.getBoolean(
            "persist.wm.debug.force_desktop_first_on_default_display_for_testing",
            false,
        )

    private val inputDeviceListener =
        object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) {
                updateDefaultDisplayWindowingMode()
            }

            override fun onInputDeviceChanged(deviceId: Int) {
                updateDefaultDisplayWindowingMode()
            }

            override fun onInputDeviceRemoved(deviceId: Int) {
                updateDefaultDisplayWindowingMode()
            }
        }

    // True if the last notified device state is eligible for desktop-first.
    private var isDesktopFirstDeviceState = false

    private val deviceStateCallback =
        object : DeviceStateManager.DeviceStateCallback {
            override fun onDeviceStateChanged(state: DeviceState) {
                val newIsDesktopFirstDeviceState =
                    // When the lid is fully closed (i.e., LID_CLOSED or DOCKED), usually touchpad
                    // or keyboard are disabled but we want to keep desktop-first mode.
                    state.hasProperty(
                        DeviceState.PROPERTY_LAPTOP_HARDWARE_CONFIGURATION_LID_CLOSED
                    ) ||
                        state.hasProperty(
                            DeviceState.PROPERTY_LAPTOP_HARDWARE_CONFIGURATION_DOCKED
                        ) ||
                        // When the lid is opened, the keyboard and touchpad get activated so in
                        // theory we don't need to override the desktop-first state by this OPEN
                        // state. But because the activation of input devices may have a slight
                        // delay which causes glitches, we here include this OPEN state in the list.
                        state.hasProperty(
                            DeviceState.PROPERTY_LAPTOP_HARDWARE_CONFIGURATION_LID_OPEN
                        )
                if (newIsDesktopFirstDeviceState == isDesktopFirstDeviceState) {
                    return
                }
                isDesktopFirstDeviceState = newIsDesktopFirstDeviceState
                updateDefaultDisplayWindowingMode()
            }
        }

    init {
        shellInit.addInitCallback({ onInit() }, this)
        inputManager.registerInputDeviceListener(inputDeviceListener, mainHandler)
        if (Flags.enableDesktopFirstLaptopStateBugfix()) {
            deviceStateManager.registerCallback(mainExecutor, deviceStateCallback)
        }
    }

    private fun onInit() {
        shellCommandHandler.addDumpCallback(this::dump, this)
        if (Flags.enableDesktopFirstUserChangeBugfix()) {
            shellController.addUserChangeListener(
                object : UserChangeListener {
                    override fun onUserChanged(newUserId: Int, userContext: Context) {
                        val displayIds = rootTaskDisplayAreaOrganizer.displayIds.toSet()
                        logV("onUserChanged newUserId=%d displays=%s", newUserId, displayIds)
                        // Changing a user results in reconfiguring a display so we here ensure the
                        // windowing mode.
                        displayIds.forEach { displayId ->
                            if (displayId == DEFAULT_DISPLAY) {
                                updateDefaultDisplayWindowingMode()
                            } else {
                                updateExternalDisplayWindowingMode(displayId)
                            }
                        }
                    }
                }
            )
        }
    }

    fun updateExternalDisplayWindowingMode(displayId: Int) {
        if (!DesktopExperienceFlags.ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT.isTrue) return

        val desktopModeSupported = desktopState.isDesktopModeSupportedOnDisplay(displayId)
        if (!desktopModeSupported) return

        // An external display should always be a freeform display when desktop mode is enabled.
        updateDisplayWindowingMode(displayId, DESKTOP_FIRST_DISPLAY_WINDOWING_MODE)
    }

    fun updateDefaultDisplayWindowingMode() {
        if (!DesktopExperienceFlags.ENABLE_DISPLAY_WINDOWING_MODE_SWITCHING.isTrue) return

        updateDisplayWindowingMode(DEFAULT_DISPLAY, getTargetWindowingModeForDefaultDisplay())
    }

    private fun updateDisplayWindowingMode(displayId: Int, targetDisplayWindowingMode: Int) {
        val tdaInfo = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(displayId)
        // A non-organized display (e.g., non-trusted virtual displays used in CTS) doesn't have
        // TDA.
        if (tdaInfo == null) {
            logW(
                "updateDisplayWindowingMode cannot find DisplayAreaInfo for displayId=%d. This " +
                    " could happen when the display is a non-trusted virtual display.",
                displayId,
            )
            return
        }
        val taskMoveAllowed = targetDisplayWindowingMode == DESKTOP_FIRST_DISPLAY_WINDOWING_MODE
        val currentDisplayWindowingMode = tdaInfo.configuration.windowConfiguration.windowingMode
        if (currentDisplayWindowingMode == targetDisplayWindowingMode) {
            // If the windowing mode is already as needed, just make sure that the task move allowed
            // bit is set correctly.
            // LINT.IfChange(setIsTaskMoveAllowed)
            val wct = WindowContainerTransaction()
            wct.setIsTaskMoveAllowed(tdaInfo.token, taskMoveAllowed)
            transitions.startTransition(TRANSIT_CHANGE, wct, /* handler= */ null)
            // LINT.ThenChange(/libs/WindowManager/Shell/src/com/android/wm/shell/desktopmode/multidesks/RootTaskDesksOrganizer.kt:updateTaskMoveAllowed)

            // Already in the target mode.
            return
        }

        logV(
            "Changing display#%d's windowing mode from %s to %s",
            displayId,
            windowingModeToString(currentDisplayWindowingMode),
            windowingModeToString(targetDisplayWindowingMode),
        )

        val wct = WindowContainerTransaction()
        wct.setWindowingMode(tdaInfo.token, targetDisplayWindowingMode)
        wct.setIsTaskMoveAllowed(tdaInfo.token, taskMoveAllowed)
        shellTaskOrganizer
            .getRunningTasks(displayId)
            .filter { it.activityType == ACTIVITY_TYPE_STANDARD }
            .forEach {
                // With multi-desks, display windowing mode doesn't affect the windowing
                // mode of freeform tasks but fullscreen tasks which are the direct children
                // of TDA.
                if (it.windowingMode == WINDOWING_MODE_FULLSCREEN) {
                    if (targetDisplayWindowingMode == DESKTOP_FIRST_DISPLAY_WINDOWING_MODE) {
                        wct.setWindowingMode(it.token, WINDOWING_MODE_FULLSCREEN)
                    } else {
                        wct.setWindowingMode(it.token, WINDOWING_MODE_UNDEFINED)
                    }
                }
            }
        // The override windowing mode of DesktopWallpaper can be UNDEFINED on fullscreen-display
        // right after the first launch while its resolved windowing mode is FULLSCREEN. We here
        // it has the FULLSCREEN override windowing mode.
        desktopWallpaperActivityTokenProvider.getToken(displayId)?.let { token ->
            wct.setWindowingMode(token, WINDOWING_MODE_FULLSCREEN)
        }
        transitions.startTransition(TRANSIT_CHANGE, wct, /* handler= */ null)
    }

    // Do not directly use this method to check the state of desktop-first mode. Use
    // [isDisplayDesktopFirst] instead.
    private fun canDesktopFirstModeBeEnabledOnDefaultDisplay(): Boolean {
        if (FORCE_DESKTOP_FIRST_ON_DEFAULT_DISPLAY) {
            logW(
                "FORCE_DESKTOP_FIRST_ON_DEFAULT_DISPLAY is enabled. Forcing desktop-first for " +
                    " testing purposes."
            )
            return true
        }

        val isDefaultDisplayDesktopEligible = isDefaultDisplayDesktopEligible()
        logV(
            "canDesktopFirstModeBeEnabledOnDefaultDisplay: isDefaultDisplayDesktopEligible=%b",
            isDefaultDisplayDesktopEligible,
        )
        if (isDefaultDisplayDesktopEligible) {
            val isExtendedDisplayEnabled = isExtendedDisplayEnabled()
            val hasExternalDisplay = hasExternalDisplay()
            logV(
                "canDesktopFirstModeBeEnabledOnDefaultDisplay: isExtendedDisplayEnabled=%b" +
                    " hasExternalDisplay=%b",
                isExtendedDisplayEnabled,
                hasExternalDisplay,
            )
            if (isExtendedDisplayEnabled && hasExternalDisplay) {
                return true
            }
            val hasAnyTouchpadDevice = hasAnyTouchpadDevice()
            val hasAnyPhysicalKeyboardDevice = hasAnyPhysicalKeyboardDevice()
            logV(
                "canDesktopFirstModeBeEnabledOnDefaultDisplay: hasAnyTouchpadDevice=%b" +
                    " hasAnyPhysicalKeyboardDevice=%b",
                hasAnyTouchpadDevice,
                hasAnyPhysicalKeyboardDevice,
            )
            if (hasAnyTouchpadDevice && hasAnyPhysicalKeyboardDevice) {
                return true
            }
            if (Flags.enableDesktopFirstLaptopStateBugfix()) {
                logV(
                    "canDesktopFirstModeBeEnabledOnDefaultDisplay: isDesktopFirstDeviceState=%b",
                    isDesktopFirstDeviceState,
                )
                if (isDesktopFirstDeviceState) {
                    return true
                }
            }
        }
        return false
    }

    // Do not directly use this method to check the state of desktop-first mode. Use
    // [isDisplayDesktopFirst] instead.
    @VisibleForTesting
    fun getTargetWindowingModeForDefaultDisplay(): Int {
        if (canDesktopFirstModeBeEnabledOnDefaultDisplay()) {
            return DESKTOP_FIRST_DISPLAY_WINDOWING_MODE
        }

        return TOUCH_FIRST_DISPLAY_WINDOWING_MODE
    }

    private fun isExtendedDisplayEnabled(): Boolean {
        if (DesktopExperienceFlags.ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT.isTrue) {
            return rootTaskDisplayAreaOrganizer
                .getDisplayIds()
                .filter { it != DEFAULT_DISPLAY }
                .any { displayId -> desktopState.isDesktopModeSupportedOnDisplay(displayId) }
        }

        return 0 !=
            Settings.Global.getInt(
                context.contentResolver,
                DEVELOPMENT_FORCE_DESKTOP_MODE_ON_EXTERNAL_DISPLAYS,
                0,
            )
    }

    private fun hasExternalDisplay() =
        rootTaskDisplayAreaOrganizer.getDisplayIds().any { it != DEFAULT_DISPLAY }

    private fun hasAnyTouchpadDevice() =
        inputManager.inputDeviceIds.any { deviceId ->
            inputManager.getInputDevice(deviceId)?.let { device ->
                device.supportsSource(InputDevice.SOURCE_TOUCHPAD) && device.isEnabled()
            } ?: false
        }

    private fun hasAnyPhysicalKeyboardDevice() =
        inputManager.inputDeviceIds.any { deviceId ->
            inputManager.getInputDevice(deviceId)?.let { device ->
                !device.isVirtual() && device.isFullKeyboard() && device.isEnabled()
            } ?: false
        }

    private fun isDefaultDisplayDesktopEligible(): Boolean {
        return desktopState.isDesktopModeSupportedOnDisplay(DEFAULT_DISPLAY)
    }

    // TODO(b/478792808): Remove suppression
    @SuppressWarnings("ProtoLogNonConstantFormat")
    private fun logV(msg: String, vararg arguments: Any?) {
        ProtoLog.v(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    // TODO(b/478792808): Remove suppression
    @SuppressWarnings("ProtoLogNonConstantFormat")
    private fun logW(msg: String, vararg arguments: Any?) {
        ProtoLog.w(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private fun dump(originalWriter: PrintWriter, prefix: String) {
        if (!DesktopExperienceFlags.ENABLE_DISPLAY_WINDOWING_MODE_SWITCHING.isTrue) return

        val pw = IndentingPrintWriter(originalWriter, "  ", prefix)

        pw.println(TAG)
        pw.increaseIndent()
        pw.println(
            "targetWindowingModeForDefaultDisplay=" + getTargetWindowingModeForDefaultDisplay()
        )
        pw.println(
            "canDesktopFirstModeBeEnabledOnDefaultDisplay=" +
                canDesktopFirstModeBeEnabledOnDefaultDisplay()
        )
        pw.println("isDefaultDisplayDesktopEligible=" + isDefaultDisplayDesktopEligible())
        pw.println("isExtendedDisplayEnabled=" + isExtendedDisplayEnabled())
        pw.println("hasExternalDisplay=" + hasExternalDisplay())
        pw.println(
            "FORCE_DESKTOP_FIRST_ON_DEFAULT_DISPLAY=" + FORCE_DESKTOP_FIRST_ON_DEFAULT_DISPLAY
        )
        pw.println("hasAnyTouchpadDevice=" + hasAnyTouchpadDevice())
        pw.println("hasAnyPhysicalKeyboardDevice=" + hasAnyPhysicalKeyboardDevice())
        if (Flags.enableDesktopFirstLaptopStateBugfix()) {
            pw.println("isDesktopFirstDeviceState=" + isDesktopFirstDeviceState)
        }

        pw.println("Current Desktop Display Modes:")
        pw.increaseIndent()
        rootTaskDisplayAreaOrganizer.displayIds.forEach { displayId ->
            val isDesktopFirst = rootTaskDisplayAreaOrganizer.isDisplayDesktopFirst(displayId)
            pw.println("Display#$displayId isDesktopFirst=$isDesktopFirst")
        }
    }

    companion object {
        private const val TAG = "DesktopDisplayModeController"
    }
}
