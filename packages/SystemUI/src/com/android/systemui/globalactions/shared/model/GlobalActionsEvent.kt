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

package com.android.systemui.globalactions.shared.model

import com.android.internal.logging.UiEvent
import com.android.internal.logging.UiEventLogger

/** UI Events for Global Actions. */
enum class GlobalActionsEvent(private val id: Int) : UiEventLogger.UiEventEnum {
    @UiEvent(doc = "The global actions / power menu surface became visible on the screen.")
    GA_POWER_MENU_OPEN(337),
    @UiEvent(doc = "The global actions / power menu surface was dismissed.")
    GA_POWER_MENU_CLOSE(471),
    @UiEvent(doc = "The global actions bugreport button was pressed.") GA_BUGREPORT_PRESS(344),
    @UiEvent(doc = "The global actions bugreport button was long pressed.")
    GA_BUGREPORT_LONG_PRESS(345),
    @UiEvent(doc = "The global actions emergency button was pressed.")
    GA_EMERGENCY_DIALER_PRESS(346),
    @UiEvent(doc = "The global actions screenshot button was pressed.") GA_SCREENSHOT_PRESS(347),
    @UiEvent(doc = "The global actions screenshot button was long pressed.")
    GA_SCREENSHOT_LONG_PRESS(348),
    @UiEvent(doc = "The global actions power off button was pressed.") GA_SHUTDOWN_PRESS(802),
    @UiEvent(doc = "The global actions power off button was long pressed.")
    GA_SHUTDOWN_LONG_PRESS(803),
    @UiEvent(doc = "The global actions reboot button was pressed.") GA_REBOOT_PRESS(349),
    @UiEvent(doc = "The global actions reboot button was long pressed.") GA_REBOOT_LONG_PRESS(804),
    @UiEvent(doc = "The global actions lockdown button was pressed.") GA_LOCKDOWN_PRESS(354),
    @UiEvent(doc = "Power menu was opened via quick settings button.") GA_OPEN_QS(805),
    @UiEvent(doc = "Power menu was opened via power + volume up.") GA_OPEN_POWER_VOLUP(806),
    @UiEvent(doc = "Power menu was opened via long press on power.") GA_OPEN_LONG_PRESS_POWER(807),
    @UiEvent(doc = "Power menu was closed via long press on power.") GA_CLOSE_LONG_PRESS_POWER(808),
    @UiEvent(doc = "Power menu was dismissed by back gesture.") GA_CLOSE_BACK(809),
    @UiEvent(doc = "Power menu was dismissed by tapping outside dialog.") GA_CLOSE_TAP_OUTSIDE(810),
    @UiEvent(doc = "Power menu was closed via power + volume up.") GA_CLOSE_POWER_VOLUP(811),
    @UiEvent(doc = "System Update button was pressed.") GA_SYSTEM_UPDATE_PRESS(1716),
    @UiEvent(doc = "Power menu was closed due to timeout.") GA_CLOSE_TIMEOUT(2148),
    @UiEvent(doc = "The global actions standby button was pressed.") GA_STANDBY_PRESS(2210),
    @UiEvent(doc = "The global actions lock button was pressed.") GA_LOCK_PRESS(2402),
    @UiEvent(doc = "The global actions feedback button was pressed.") GA_FEEDBACK_PRESS(2509);

    override fun getId() = id
}
