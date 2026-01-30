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
package com.android.server.accessibility.integration

import android.content.Context
import android.view.Display.DEFAULT_DISPLAY
import android.view.WindowManager
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.android.compatibility.common.util.PollingCheck
import com.android.compatibility.common.util.PollingCheck.waitFor
import kotlin.time.Duration.Companion.seconds
import platform.test.desktop.DesktopMouseTestRule

private val FIND_OBJECT_TIMEOUT = 10.seconds

// Autoclick panel resource ids.
val LEFT_CLICK_BUTTON_LAYOUT_ID =
    "android:id/accessibility_autoclick_left_click_button"
val LONG_PRESS_BUTTON_LAYOUT_ID =
    "android:id/accessibility_autoclick_long_press_button"
val RIGHT_CLICK_BUTTON_LAYOUT_ID =
    "android:id/accessibility_autoclick_right_click_button"
val DOUBLE_CLICK_BUTTON_LAYOUT_ID =
    "android:id/accessibility_autoclick_double_click_button"
val DRAG_CLICK_BUTTON_LAYOUT_ID = "android:id/accessibility_autoclick_drag_button"
val SCROLL_BUTTON_LAYOUT_ID = "android:id/accessibility_autoclick_scroll_button"
val SCROLL_EXIT_BUTTON_LAYOUT_ID = "android:id/scroll_exit"
val SCROLL_UP_BUTTON_LAYOUT_ID = "android:id/scroll_up"
val SCROLL_DOWN_BUTTON_LAYOUT_ID = "android:id/scroll_down"
val SCROLL_LEFT_BUTTON_LAYOUT_ID = "android:id/scroll_left"
val SCROLL_RIGHT_BUTTON_LAYOUT_ID = "android:id/scroll_right"
val CLICK_TYPE_BUTTON_GROUP_ID =
    "android:id/accessibility_autoclick_click_type_button_group_container"
val PAUSE_BUTTON_LAYOUT_ID = "android:id/accessibility_autoclick_pause_button"
val POSITION_BUTTON_LAYOUT_ID = "android:id/accessibility_autoclick_position_button"
val AUTOCLICK_PANEL_ID = "android:id/accessibility_autoclick_type_panel"

fun findObject(uiDevice: UiDevice, selector: BySelector): UiObject2 {
    return uiDevice.wait(Until.findObject(selector), FIND_OBJECT_TIMEOUT.inWholeMilliseconds)
}

fun waitAndAssert(condition: PollingCheck.PollingCheckCondition) {
    waitFor(FIND_OBJECT_TIMEOUT.inWholeMilliseconds, condition)
}

fun initiateAutoclickPanel(
    context: Context,
    uiDevice: UiDevice,
    desktopMouseTestRule: DesktopMouseTestRule
) {
    // Move the mouse across the display to create the panel.
    desktopMouseTestRule.move(DEFAULT_DISPLAY, 0, 0)
    val wm = context.getSystemService(WindowManager::class.java)
    desktopMouseTestRule.move(
        DEFAULT_DISPLAY,
        wm.currentWindowMetrics.bounds.width() / 2,
        wm.currentWindowMetrics.bounds.height() / 2
    )

    // Wait for the panel to close before beginning the tests.
    waitAndAssert {
        !isAutoclickPanelOpen(uiDevice)
    }
}

// The panel is considered open when more than one click type button is visible.
fun isAutoclickPanelOpen(uiDevice: UiDevice): Boolean {
    val clickTypeButtonGroupContainer = findObject(
        uiDevice, By.res(CLICK_TYPE_BUTTON_GROUP_ID)
    )
    return clickTypeButtonGroupContainer.childCount > 1
}

fun changeClickType(
    uiDevice: UiDevice,
    desktopMouseTestRule: DesktopMouseTestRule,
    clickTypeResourceId: String
) {
    // Requested click type is already selected.
    if (uiDevice.findObject(By.res(clickTypeResourceId)) != null) {
        return
    }

    // The click type button group starts closed so click it to open the panel.
    val clickTypeButtonGroup = findObject(
        uiDevice, By.res(CLICK_TYPE_BUTTON_GROUP_ID)
    )
    desktopMouseTestRule.move(
        DEFAULT_DISPLAY,
        clickTypeButtonGroup.visibleCenter.x,
        clickTypeButtonGroup.visibleCenter.y
    )

    // Wait for the panel to fully open before attempting to select a click type.
    waitAndAssert {
        isAutoclickPanelOpen(uiDevice)
    }

    val targetClickTypeButton = findObject(uiDevice, By.res(clickTypeResourceId))
    desktopMouseTestRule.move(
        DEFAULT_DISPLAY,
        targetClickTypeButton.visibleCenter.x,
        targetClickTypeButton.visibleCenter.y
    )

    // Wait for the panel to close as the signal that the click type was selected.
    waitAndAssert {
        !isAutoclickPanelOpen(uiDevice)
    }
}
