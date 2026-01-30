/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.server.input

import android.app.role.RoleManager
import android.content.Intent
import android.hardware.input.AppLaunchData
import android.hardware.input.KeyGestureEvent
import android.view.KeyEvent

/** Test data for Key gestures tests in {@link KeyGestureControllerTests} */
object KeyGestureTestData {

    val MULTI_KEY_SYSTEM_GESTURES =
        arrayOf(
            KeyGestureData(
                "VOLUME_DOWN + POWER -> Screenshot Chord",
                intArrayOf(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_POWER),
                KeyGestureEvent.KEY_GESTURE_TYPE_SCREENSHOT_CHORD,
                intArrayOf(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_POWER),
                0,
                intArrayOf(
                    KeyGestureEvent.ACTION_GESTURE_START,
                    KeyGestureEvent.ACTION_GESTURE_COMPLETE,
                ),
                isGestureHandlerRegistered = true,
            ),
            KeyGestureData(
                "POWER + STEM_PRIMARY -> Screenshot Chord",
                intArrayOf(KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_STEM_PRIMARY),
                KeyGestureEvent.KEY_GESTURE_TYPE_SCREENSHOT_CHORD,
                intArrayOf(KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_STEM_PRIMARY),
                0,
                intArrayOf(
                    KeyGestureEvent.ACTION_GESTURE_START,
                    KeyGestureEvent.ACTION_GESTURE_COMPLETE,
                ),
                isGestureHandlerRegistered = true,
            ),
            KeyGestureData(
                "VOLUME_DOWN + VOLUME_UP -> Accessibility Chord",
                intArrayOf(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP),
                KeyGestureEvent.KEY_GESTURE_TYPE_ACCESSIBILITY_SHORTCUT_CHORD,
                intArrayOf(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP),
                0,
                intArrayOf(
                    KeyGestureEvent.ACTION_GESTURE_START,
                    KeyGestureEvent.ACTION_GESTURE_COMPLETE,
                ),
                isGestureHandlerRegistered = true,
            ),
            KeyGestureData(
                "BACK + DPAD_DOWN -> TV Accessibility Chord",
                intArrayOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_DOWN),
                KeyGestureEvent.KEY_GESTURE_TYPE_ACCESSIBILITY_SHORTCUT_CHORD,
                intArrayOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_DOWN),
                0,
                intArrayOf(
                    KeyGestureEvent.ACTION_GESTURE_START,
                    KeyGestureEvent.ACTION_GESTURE_COMPLETE,
                ),
                isGestureHandlerRegistered = true,
            ),
            KeyGestureData(
                "BACK + DPAD_CENTER -> TV Trigger Bug Report",
                intArrayOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_CENTER),
                KeyGestureEvent.KEY_GESTURE_TYPE_TV_TRIGGER_BUG_REPORT,
                intArrayOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_CENTER),
                0,
                intArrayOf(
                    KeyGestureEvent.ACTION_GESTURE_START,
                    KeyGestureEvent.ACTION_GESTURE_COMPLETE,
                ),
            ),
        )

    // All Key gestures that should always happen regardless of whether focused window captures the
    // keys should go in this list.
    // (i.e. Shortcuts and keys handled in INTERCEPT_SHORTCUTS_BEFORE_KEY_CAPTURE stage)
    val NON_CAPTURABLE_SYSTEM_GESTURES =
        arrayOf(
            KeyGestureData(
                "META + H -> Go Home",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_H),
                KeyGestureEvent.KEY_GESTURE_TYPE_HOME,
                intArrayOf(KeyEvent.KEYCODE_H),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "META + ENTER -> Go Home",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_ENTER),
                KeyGestureEvent.KEY_GESTURE_TYPE_HOME,
                intArrayOf(KeyEvent.KEYCODE_ENTER),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "META + CTRL + DPAD_UP -> Multi Window Navigation",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_CTRL_LEFT,
                    KeyEvent.KEYCODE_DPAD_UP,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_MULTI_WINDOW_NAVIGATION,
                intArrayOf(KeyEvent.KEYCODE_DPAD_UP),
                KeyEvent.META_META_ON or KeyEvent.META_CTRL_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "META + CTRL + DPAD_DOWN -> Desktop Mode",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_CTRL_LEFT,
                    KeyEvent.KEYCODE_DPAD_DOWN,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_DESKTOP_MODE,
                intArrayOf(KeyEvent.KEYCODE_DPAD_DOWN),
                KeyEvent.META_META_ON or KeyEvent.META_CTRL_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "META + CTRL + DPAD_LEFT -> Splitscreen Navigation Left",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_CTRL_LEFT,
                    KeyEvent.KEYCODE_DPAD_LEFT,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_SPLIT_SCREEN_NAVIGATION_LEFT,
                intArrayOf(KeyEvent.KEYCODE_DPAD_LEFT),
                KeyEvent.META_META_ON or KeyEvent.META_CTRL_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "META + CTRL + DPAD_RIGHT -> Splitscreen Navigation Right",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_CTRL_LEFT,
                    KeyEvent.KEYCODE_DPAD_RIGHT,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_SPLIT_SCREEN_NAVIGATION_RIGHT,
                intArrayOf(KeyEvent.KEYCODE_DPAD_RIGHT),
                KeyEvent.META_META_ON or KeyEvent.META_CTRL_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "META + CTRL + D -> Move a task to next display",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_CTRL_LEFT,
                    KeyEvent.KEYCODE_D,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_MOVE_TO_NEXT_DISPLAY,
                intArrayOf(KeyEvent.KEYCODE_D),
                KeyEvent.META_META_ON or KeyEvent.META_CTRL_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "META + CTRL + W -> Quit focused desktop task",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_CTRL_LEFT,
                    KeyEvent.KEYCODE_W,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_QUIT_FOCUSED_DESKTOP_TASK,
                intArrayOf(KeyEvent.KEYCODE_W),
                KeyEvent.META_META_ON or KeyEvent.META_CTRL_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "META + [ -> Resizes a task to fit the left half of the screen",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_LEFT_BRACKET),
                KeyGestureEvent.KEY_GESTURE_TYPE_SNAP_LEFT_FREEFORM_WINDOW,
                intArrayOf(KeyEvent.KEYCODE_LEFT_BRACKET),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "META + ] -> Resizes a task to fit the right half of the screen",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_RIGHT_BRACKET),
                KeyGestureEvent.KEY_GESTURE_TYPE_SNAP_RIGHT_FREEFORM_WINDOW,
                intArrayOf(KeyEvent.KEYCODE_RIGHT_BRACKET),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "META + '=' -> Toggles maximization of a task to maximized and restore its bounds",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_EQUALS),
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAXIMIZE_FREEFORM_WINDOW,
                intArrayOf(KeyEvent.KEYCODE_EQUALS),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "META + '-' -> Minimizes a freeform task",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_MINUS),
                KeyGestureEvent.KEY_GESTURE_TYPE_MINIMIZE_FREEFORM_WINDOW,
                intArrayOf(KeyEvent.KEYCODE_MINUS),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "META + CTRL + '[' -> Switch to previous desk",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_CTRL_LEFT,
                    KeyEvent.KEYCODE_LEFT_BRACKET,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_SWITCH_TO_PREVIOUS_DESK,
                intArrayOf(KeyEvent.KEYCODE_LEFT_BRACKET),
                KeyEvent.META_META_ON or KeyEvent.META_CTRL_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "META + CTRL + ']' -> Switch to next desk",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_CTRL_LEFT,
                    KeyEvent.KEYCODE_RIGHT_BRACKET,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_SWITCH_TO_NEXT_DESK,
                intArrayOf(KeyEvent.KEYCODE_RIGHT_BRACKET),
                KeyEvent.META_META_ON or KeyEvent.META_CTRL_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "RECENT_APPS -> Show Overview",
                intArrayOf(KeyEvent.KEYCODE_RECENT_APPS),
                KeyGestureEvent.KEY_GESTURE_TYPE_RECENT_APPS,
                intArrayOf(KeyEvent.KEYCODE_RECENT_APPS),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "APP_SWITCH -> App Switch",
                intArrayOf(KeyEvent.KEYCODE_APP_SWITCH),
                KeyGestureEvent.KEY_GESTURE_TYPE_APP_SWITCH,
                intArrayOf(KeyEvent.KEYCODE_APP_SWITCH),
                0,
                intArrayOf(
                    KeyGestureEvent.ACTION_GESTURE_START,
                    KeyGestureEvent.ACTION_GESTURE_COMPLETE,
                ),
            ),
            KeyGestureData(
                "BRIGHTNESS_UP -> Brightness Up",
                intArrayOf(KeyEvent.KEYCODE_BRIGHTNESS_UP),
                KeyGestureEvent.KEY_GESTURE_TYPE_BRIGHTNESS_UP,
                intArrayOf(KeyEvent.KEYCODE_BRIGHTNESS_UP),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "BRIGHTNESS_DOWN -> Brightness Down",
                intArrayOf(KeyEvent.KEYCODE_BRIGHTNESS_DOWN),
                KeyGestureEvent.KEY_GESTURE_TYPE_BRIGHTNESS_DOWN,
                intArrayOf(KeyEvent.KEYCODE_BRIGHTNESS_DOWN),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "KEYBOARD_BACKLIGHT_UP -> Keyboard Backlight Up",
                intArrayOf(KeyEvent.KEYCODE_KEYBOARD_BACKLIGHT_UP),
                KeyGestureEvent.KEY_GESTURE_TYPE_KEYBOARD_BACKLIGHT_UP,
                intArrayOf(KeyEvent.KEYCODE_KEYBOARD_BACKLIGHT_UP),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "KEYBOARD_BACKLIGHT_DOWN -> Keyboard Backlight Down",
                intArrayOf(KeyEvent.KEYCODE_KEYBOARD_BACKLIGHT_DOWN),
                KeyGestureEvent.KEY_GESTURE_TYPE_KEYBOARD_BACKLIGHT_DOWN,
                intArrayOf(KeyEvent.KEYCODE_KEYBOARD_BACKLIGHT_DOWN),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "KEYBOARD_BACKLIGHT_TOGGLE -> Keyboard Backlight Toggle",
                intArrayOf(KeyEvent.KEYCODE_KEYBOARD_BACKLIGHT_TOGGLE),
                KeyGestureEvent.KEY_GESTURE_TYPE_KEYBOARD_BACKLIGHT_TOGGLE,
                intArrayOf(KeyEvent.KEYCODE_KEYBOARD_BACKLIGHT_TOGGLE),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "ALL_APPS -> Open App Drawer",
                intArrayOf(KeyEvent.KEYCODE_ALL_APPS),
                KeyGestureEvent.KEY_GESTURE_TYPE_ALL_APPS,
                intArrayOf(KeyEvent.KEYCODE_ALL_APPS),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "LOCK -> Lock Screen",
                intArrayOf(KeyEvent.KEYCODE_LOCK),
                KeyGestureEvent.KEY_GESTURE_TYPE_LOCK_SCREEN,
                intArrayOf(KeyEvent.KEYCODE_LOCK),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "FULLSCREEN -> Toggles the focused task's fullscreen state",
                intArrayOf(KeyEvent.KEYCODE_FULLSCREEN),
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_FULLSCREEN,
                intArrayOf(KeyEvent.KEYCODE_FULLSCREEN),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "SCREENSHOT -> Take Screenshot",
                intArrayOf(KeyEvent.KEYCODE_SCREENSHOT),
                KeyGestureEvent.KEY_GESTURE_TYPE_TAKE_SCREENSHOT,
                intArrayOf(KeyEvent.KEYCODE_SCREENSHOT),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                isGestureHandlerRegistered = true,
            ),
            KeyGestureData(
                "META + BRIGHTNESS_UP -> Keyboard Backlight Up",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_BRIGHTNESS_UP),
                KeyGestureEvent.KEY_GESTURE_TYPE_KEYBOARD_BACKLIGHT_UP,
                intArrayOf(KeyEvent.KEYCODE_BRIGHTNESS_UP),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "META + BRIGHTNESS_DOWN -> Keyboard Backlight Down",
                intArrayOf(KeyEvent.KEYCODE_META_RIGHT, KeyEvent.KEYCODE_BRIGHTNESS_DOWN),
                KeyGestureEvent.KEY_GESTURE_TYPE_KEYBOARD_BACKLIGHT_DOWN,
                intArrayOf(KeyEvent.KEYCODE_BRIGHTNESS_DOWN),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
        )

    // All Key gestures that can be captured by the focused window (and should not happen in
    // INTERCEPT_UNHANDLED_SHORTCUT stage), should go in this list.
    // (i.e. Shortcuts and keys exclusively handled in INTERCEPT_SHORTCUTS_AFTER_KEY_CAPTURE stage
    // like Meta, Meta+Alt, etc.)
    val CAPTURABLE_STATEFUL_SYSTEM_GESTURES =
        arrayOf(
            KeyGestureData(
                "META + ALT -> Toggle Caps Lock",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_ALT_LEFT),
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_CAPS_LOCK,
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_ALT_LEFT),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "ALT + META -> Toggle Caps Lock",
                intArrayOf(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_META_LEFT),
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_CAPS_LOCK,
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_ALT_LEFT),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "ALT + TAB -> Toggle Recent Apps Switcher",
                intArrayOf(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_TAB),
                KeyGestureEvent.KEY_GESTURE_TYPE_RECENT_APPS_SWITCHER,
                intArrayOf(KeyEvent.KEYCODE_TAB),
                KeyEvent.META_ALT_ON,
                intArrayOf(
                    KeyGestureEvent.ACTION_GESTURE_START,
                    KeyGestureEvent.ACTION_GESTURE_COMPLETE,
                ),
            ),
            KeyGestureData(
                "META -> Open Apps Drawer",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT),
                KeyGestureEvent.KEY_GESTURE_TYPE_ALL_APPS,
                intArrayOf(KeyEvent.KEYCODE_META_LEFT),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
        )

    // All Key gestures that can be captured by the focused window (and can also be handled in
    // INTERCEPT_UNHANDLED_SHORTCUT stage), should go in this list.
    // (i.e. Shortcuts and keys handled in INTERCEPT_SHORTCUTS_AFTER_KEY_CAPTURE stage or
    // INTERCEPT_UNHANDLED_SHORTCUT stage)
    val CAPTURABLE_SYSTEM_GESTURES =
        arrayOf(
            KeyGestureData(
                "META + Space -> Launch Assistant",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_SPACE),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_ASSISTANT,
                intArrayOf(KeyEvent.KEYCODE_SPACE),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "META + I -> Launch System Settings",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_I),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_SYSTEM_SETTINGS,
                intArrayOf(KeyEvent.KEYCODE_I),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "META + L -> Lock",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_L),
                KeyGestureEvent.KEY_GESTURE_TYPE_LOCK_SCREEN,
                intArrayOf(KeyEvent.KEYCODE_L),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "META + N -> Toggle Notification",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_N),
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_NOTIFICATION_PANEL,
                intArrayOf(KeyEvent.KEYCODE_N),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "META + Q -> Toggle Quick Settings",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_Q),
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_QUICK_SETTINGS_PANEL,
                intArrayOf(KeyEvent.KEYCODE_Q),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "META + ESC -> Back",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_ESCAPE),
                KeyGestureEvent.KEY_GESTURE_TYPE_BACK,
                intArrayOf(KeyEvent.KEYCODE_ESCAPE),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "META + DPAD_LEFT -> Back",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_DPAD_LEFT),
                KeyGestureEvent.KEY_GESTURE_TYPE_BACK,
                intArrayOf(KeyEvent.KEYCODE_DPAD_LEFT),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "META + / -> Open Shortcut Helper",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_SLASH),
                KeyGestureEvent.KEY_GESTURE_TYPE_OPEN_SHORTCUT_HELPER,
                intArrayOf(KeyEvent.KEYCODE_SLASH),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "META + TAB -> Open Overview",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_TAB),
                KeyGestureEvent.KEY_GESTURE_TYPE_RECENT_APPS,
                intArrayOf(KeyEvent.KEYCODE_TAB),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "CTRL + SPACE -> Switch Language Forward",
                intArrayOf(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_SPACE),
                KeyGestureEvent.KEY_GESTURE_TYPE_LANGUAGE_SWITCH,
                intArrayOf(KeyEvent.KEYCODE_SPACE),
                KeyEvent.META_CTRL_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "CTRL + SHIFT + SPACE -> Switch Language Backward",
                intArrayOf(
                    KeyEvent.KEYCODE_CTRL_LEFT,
                    KeyEvent.KEYCODE_SHIFT_LEFT,
                    KeyEvent.KEYCODE_SPACE,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_LANGUAGE_SWITCH,
                intArrayOf(KeyEvent.KEYCODE_SPACE),
                KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "META + B -> Launch Default Browser",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_B),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_B),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForRole(RoleManager.ROLE_BROWSER),
            ),
            KeyGestureData(
                "META + C -> Launch Default Contacts",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_P),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_P),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForCategory(Intent.CATEGORY_APP_CONTACTS),
            ),
            KeyGestureData(
                "META + E -> Launch Default Email",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_E),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_E),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForCategory(Intent.CATEGORY_APP_EMAIL),
            ),
            KeyGestureData(
                "META + K -> Launch Default Calendar",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_C),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_C),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForCategory(Intent.CATEGORY_APP_CALENDAR),
            ),
            KeyGestureData(
                "META + M -> Launch Default Maps",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_M),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_M),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForCategory(Intent.CATEGORY_APP_MAPS),
            ),
            KeyGestureData(
                "META + U -> Launch Default Calculator",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_U),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_U),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForCategory(Intent.CATEGORY_APP_CALCULATOR),
            ),
            KeyGestureData(
                "META + F -> Launch Default Files Browser",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_F),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_F),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForCategory(Intent.CATEGORY_APP_FILES),
            ),
            KeyGestureData(
                "META + CTRL + DEL -> Trigger Bug Report",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_CTRL_LEFT,
                    KeyEvent.KEYCODE_DEL,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_TRIGGER_BUG_REPORT,
                intArrayOf(KeyEvent.KEYCODE_DEL),
                KeyEvent.META_META_ON or KeyEvent.META_CTRL_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "Meta + Alt + 3 -> Toggle Bounce Keys",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_ALT_LEFT,
                    KeyEvent.KEYCODE_3,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_BOUNCE_KEYS,
                intArrayOf(KeyEvent.KEYCODE_3),
                KeyEvent.META_META_ON or KeyEvent.META_ALT_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "Meta + Alt + 4 -> Toggle Mouse Keys",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_ALT_LEFT,
                    KeyEvent.KEYCODE_4,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MOUSE_KEYS,
                intArrayOf(KeyEvent.KEYCODE_4),
                KeyEvent.META_META_ON or KeyEvent.META_ALT_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "Meta + Alt + 5 -> Toggle Sticky Keys",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_ALT_LEFT,
                    KeyEvent.KEYCODE_5,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_STICKY_KEYS,
                intArrayOf(KeyEvent.KEYCODE_5),
                KeyEvent.META_META_ON or KeyEvent.META_ALT_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "Meta + Alt + 6 -> Toggle Slow Keys",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_ALT_LEFT,
                    KeyEvent.KEYCODE_6,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SLOW_KEYS,
                intArrayOf(KeyEvent.KEYCODE_6),
                KeyEvent.META_META_ON or KeyEvent.META_ALT_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "META + ALT + M -> Toggle Magnification",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_ALT_LEFT,
                    KeyEvent.KEYCODE_M,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION,
                intArrayOf(KeyEvent.KEYCODE_M),
                KeyEvent.META_META_ON or KeyEvent.META_ALT_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "META + ALT + S -> Activate Select to Speak",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_ALT_LEFT,
                    KeyEvent.KEYCODE_S,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_ACTIVATE_SELECT_TO_SPEAK,
                intArrayOf(KeyEvent.KEYCODE_S),
                KeyEvent.META_META_ON or KeyEvent.META_ALT_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "META + ALT + 'V' -> Toggle Voice Access",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_ALT_LEFT,
                    KeyEvent.KEYCODE_V,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS,
                intArrayOf(KeyEvent.KEYCODE_V),
                KeyEvent.META_META_ON or KeyEvent.META_ALT_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "ESC -> Close All Dialogs",
                intArrayOf(KeyEvent.KEYCODE_ESCAPE),
                KeyGestureEvent.KEY_GESTURE_TYPE_CLOSE_ALL_DIALOGS,
                intArrayOf(KeyEvent.KEYCODE_ESCAPE),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "EXPLORER -> Launch Default Browser",
                intArrayOf(KeyEvent.KEYCODE_EXPLORER),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_EXPLORER),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForRole(RoleManager.ROLE_BROWSER),
            ),
            KeyGestureData(
                "ENVELOPE -> Launch Default Email",
                intArrayOf(KeyEvent.KEYCODE_ENVELOPE),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_ENVELOPE),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForCategory(Intent.CATEGORY_APP_EMAIL),
            ),
            KeyGestureData(
                "CONTACTS -> Launch Default Contacts",
                intArrayOf(KeyEvent.KEYCODE_CONTACTS),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_CONTACTS),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForCategory(Intent.CATEGORY_APP_CONTACTS),
            ),
            KeyGestureData(
                "CALENDAR -> Launch Default Calendar",
                intArrayOf(KeyEvent.KEYCODE_CALENDAR),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_CALENDAR),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForCategory(Intent.CATEGORY_APP_CALENDAR),
            ),
            KeyGestureData(
                "MUSIC -> Launch Default Music",
                intArrayOf(KeyEvent.KEYCODE_MUSIC),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_MUSIC),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForCategory(Intent.CATEGORY_APP_MUSIC),
            ),
            KeyGestureData(
                "CALCULATOR -> Launch Default Calculator",
                intArrayOf(KeyEvent.KEYCODE_CALCULATOR),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_CALCULATOR),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForCategory(Intent.CATEGORY_APP_CALCULATOR),
            ),
            KeyGestureData(
                "META + S -> Take Screenshot",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_S),
                KeyGestureEvent.KEY_GESTURE_TYPE_TAKE_SCREENSHOT,
                intArrayOf(KeyEvent.KEYCODE_S),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                isGestureHandlerRegistered = true,
            ),
            KeyGestureData(
                "META + CTRL + S -> Take Partial Screenshot",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_CTRL_LEFT,
                    KeyEvent.KEYCODE_S,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_TAKE_PARTIAL_SCREENSHOT,
                intArrayOf(KeyEvent.KEYCODE_S),
                KeyEvent.META_META_ON or KeyEvent.META_CTRL_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "SYSRQ -> Take Screenshot",
                intArrayOf(KeyEvent.KEYCODE_SYSRQ),
                KeyGestureEvent.KEY_GESTURE_TYPE_TAKE_SCREENSHOT,
                intArrayOf(KeyEvent.KEYCODE_SYSRQ),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                isGestureHandlerRegistered = true,
            ),
            KeyGestureData(
                "LANGUAGE_SWITCH -> Switch Language Forward",
                intArrayOf(KeyEvent.KEYCODE_LANGUAGE_SWITCH),
                KeyGestureEvent.KEY_GESTURE_TYPE_LANGUAGE_SWITCH,
                intArrayOf(KeyEvent.KEYCODE_LANGUAGE_SWITCH),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "NOTIFICATION -> Toggle Notification Panel",
                intArrayOf(KeyEvent.KEYCODE_NOTIFICATION),
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_NOTIFICATION_PANEL,
                intArrayOf(KeyEvent.KEYCODE_NOTIFICATION),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "SEARCH -> Launch Search Activity",
                intArrayOf(KeyEvent.KEYCODE_SEARCH),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_SEARCH,
                intArrayOf(KeyEvent.KEYCODE_SEARCH),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
            KeyGestureData(
                "SETTINGS -> Launch Settings Activity",
                intArrayOf(KeyEvent.KEYCODE_SETTINGS),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_SYSTEM_SETTINGS,
                intArrayOf(KeyEvent.KEYCODE_SETTINGS),
                0,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
            ),
        )

    // Key gesture data corresponding to gestures defined in com.android.test.input.R.xml.bookmarks
    // The default bookmarks data (added in frameworks/base/core/res/res/xml/bookmarks.xml) should
    // be added to the appropriate system gesture data list above.
    val TEST_BOOKMARKS_DATA =
        arrayOf(
            KeyGestureData(
                "META + B -> Launch Default Browser",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_B),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_B),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForRole(RoleManager.ROLE_BROWSER),
            ),
            KeyGestureData(
                "META + P -> Launch Default Contacts",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_P),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_P),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForCategory(Intent.CATEGORY_APP_CONTACTS),
            ),
            KeyGestureData(
                "META + E -> Launch Default Email",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_E),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_E),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForCategory(Intent.CATEGORY_APP_EMAIL),
            ),
            KeyGestureData(
                "META + C -> Launch Default Calendar",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_C),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_C),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForCategory(Intent.CATEGORY_APP_CALENDAR),
            ),
            KeyGestureData(
                "META + M -> Launch Default Maps",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_M),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_M),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForCategory(Intent.CATEGORY_APP_MAPS),
            ),
            KeyGestureData(
                "META + U -> Launch Default Calculator",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_U),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_U),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForCategory(Intent.CATEGORY_APP_CALCULATOR),
            ),
            KeyGestureData(
                "META + F -> Launch Default Files",
                intArrayOf(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_F),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_F),
                KeyEvent.META_META_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForCategory(Intent.CATEGORY_APP_FILES),
            ),
            KeyGestureData(
                "META + SHIFT + B -> Launch Default Browser",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_SHIFT_LEFT,
                    KeyEvent.KEYCODE_B,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_B),
                KeyEvent.META_META_ON or KeyEvent.META_SHIFT_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForRole(RoleManager.ROLE_BROWSER),
            ),
            KeyGestureData(
                "META + SHIFT + P -> Launch Default Contacts",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_SHIFT_LEFT,
                    KeyEvent.KEYCODE_P,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_P),
                KeyEvent.META_META_ON or KeyEvent.META_SHIFT_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForCategory(Intent.CATEGORY_APP_CONTACTS),
            ),
            KeyGestureData(
                "META + SHIFT + J -> Launch Target Activity",
                intArrayOf(
                    KeyEvent.KEYCODE_META_LEFT,
                    KeyEvent.KEYCODE_SHIFT_LEFT,
                    KeyEvent.KEYCODE_J,
                ),
                KeyGestureEvent.KEY_GESTURE_TYPE_LAUNCH_APPLICATION,
                intArrayOf(KeyEvent.KEYCODE_J),
                KeyEvent.META_META_ON or KeyEvent.META_SHIFT_ON,
                intArrayOf(KeyGestureEvent.ACTION_GESTURE_COMPLETE),
                AppLaunchData.createLaunchDataForComponent("com.test", "com.test.BookmarkTest"),
            ),
        )
}

class KeyGestureData(
    val name: String,
    val keys: IntArray,
    val expectedKeyGestureType: Int,
    val expectedKeys: IntArray = intArrayOf(),
    val expectedModifierState: Int = 0,
    val expectedActions: IntArray = intArrayOf(),
    val expectedAppLaunchData: AppLaunchData? = null,
    val isGestureHandlerRegistered: Boolean = false,
) {
    override fun toString(): String = name
}
