/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.screencapture.common.shared.model

import com.android.systemui.screencapture.ScreenCaptureEvent

/**
 * An enum describing the all the possible entry points (sources) which open the screen capture UI.
 * The source is mapped to the event for recording metrics.
 */
enum class ScreenCaptureUiSource(val event: ScreenCaptureEvent) {
    QUICK_SETTINGS_TILE(ScreenCaptureEvent.SCREEN_CAPTURE_UI_SOURCE_QUICK_SETTINGS),
    NOTIFICATION(ScreenCaptureEvent.SCREEN_CAPTURE_UI_SOURCE_NOTIFICATION),
    PARTIAL_SCREENSHOT_KEYBOARD_SHORTCUT(
        ScreenCaptureEvent.SCREEN_CAPTURE_LARGE_SCREEN_PARTIAL_SCREENSHOT_KEYBOARD_SHORTCUT
    ),
    APP_WINDOW_SCREENSHOT_KEYBOARD_SHORTCUT(
        ScreenCaptureEvent.SCREEN_CAPTURE_LARGE_SCREEN_APP_WINDOW_SCREENSHOT_KEYBOARD_SHORTCUT
    ),
}
