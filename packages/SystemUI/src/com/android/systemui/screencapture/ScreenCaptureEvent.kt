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

package com.android.systemui.screencapture

import com.android.internal.logging.UiEvent
import com.android.internal.logging.UiEventLogger.UiEventEnum
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureRegion
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureType

/** Enum of available screen capture events. */
enum class ScreenCaptureEvent(private val id: Int) : UiEventEnum {

    @UiEvent(doc = "Closed the large-screen pre-capture UI without any capture")
    SCREEN_CAPTURE_LARGE_SCREEN_CLOSE_UI_WITHOUT_CAPTURE(2486),
    @UiEvent(doc = "Requested a fullscreen screenshot from the large-screen pre-capture UI")
    SCREEN_CAPTURE_LARGE_SCREEN_FULLSCREEN_SCREENSHOT_REQUESTED(2490),
    @UiEvent(doc = "Requested a partial screenshot from the large-screen pre-capture UI")
    SCREEN_CAPTURE_LARGE_SCREEN_PARTIAL_SCREENSHOT_REQUESTED(2491),
    @UiEvent(doc = "Invoked partial screenshot using the keyboard shortcut")
    SCREEN_CAPTURE_LARGE_SCREEN_PARTIAL_SCREENSHOT_KEYBOARD_SHORTCUT(2495),
    @UiEvent(doc = "Selected fullscreen screenshot in the large-screen pre-capture UI")
    SCREEN_CAPTURE_LARGE_SCREEN_SELECTED_FULLSCREEN_SCREENSHOT(2501),
    @UiEvent(doc = "Selected fullscreen recording in the large-screen pre-capture UI")
    SCREEN_CAPTURE_LARGE_SCREEN_SELECTED_FULLSCREEN_RECORDING(2502),
    @UiEvent(doc = "Selected partial screenshot in the large-screen pre-capture UI")
    SCREEN_CAPTURE_LARGE_SCREEN_SELECTED_PARTIAL_SCREENSHOT(2503),
    @UiEvent(doc = "Selected partial recording in the large-screen pre-capture UI")
    SCREEN_CAPTURE_LARGE_SCREEN_SELECTED_PARTIAL_RECORDING(2504),
    @UiEvent(doc = "Selected app window screenshot in the large-screen pre-capture UI")
    SCREEN_CAPTURE_LARGE_SCREEN_SELECTED_APP_WINDOW_SCREENSHOT(2505),
    @UiEvent(doc = "Selected app window recording in the large-screen pre-capture UI")
    SCREEN_CAPTURE_LARGE_SCREEN_SELECTED_APP_WINDOW_RECORDING(2506),
    @UiEvent(doc = "Invoked app window screenshot using the keyboard shortcut")
    SCREEN_CAPTURE_LARGE_SCREEN_APP_WINDOW_SCREENSHOT_KEYBOARD_SHORTCUT(2529),
    @UiEvent(doc = "Requested an app window screenshot from the large-screen pre-capture UI")
    SCREEN_CAPTURE_LARGE_SCREEN_APP_WINDOW_SCREENSHOT_REQUESTED(2569),
    @UiEvent(doc = "Opened the screen capture UI from Quick Settings")
    SCREEN_CAPTURE_UI_SOURCE_QUICK_SETTINGS(2593),
    @UiEvent(doc = "Opened the screen capture UI from recording notification")
    SCREEN_CAPTURE_UI_SOURCE_NOTIFICATION(2662),
    @UiEvent(doc = "Took fullscreen recording in the large-screen pre-capture UI")
    SCREEN_CAPTURE_LARGE_SCREEN_TOOK_FULLSCREEN_RECORDING(2624),
    @UiEvent(doc = "Took app window recording in the large-screen pre-capture UI")
    SCREEN_CAPTURE_LARGE_SCREEN_TOOK_APP_WINDOW_RECORDING(2625);

    override fun getId(): Int = id

    companion object {
        /**
         * Returns the corresponding [ScreenCaptureEvent] for the given [region] and [type]. The
         * event name follows the convention SCREEN_CAPTURE_LARGE_SCREEN_SELECTED_[region]_[type].
         */
        fun fromRegionAndType(
            region: ScreenCaptureRegion,
            type: ScreenCaptureType,
        ): ScreenCaptureEvent {
            return when (region) {
                ScreenCaptureRegion.FULLSCREEN -> {
                    when (type) {
                        ScreenCaptureType.SCREENSHOT ->
                            SCREEN_CAPTURE_LARGE_SCREEN_SELECTED_FULLSCREEN_SCREENSHOT
                        ScreenCaptureType.RECORDING ->
                            SCREEN_CAPTURE_LARGE_SCREEN_SELECTED_FULLSCREEN_RECORDING
                    }
                }
                ScreenCaptureRegion.PARTIAL -> {
                    when (type) {
                        ScreenCaptureType.SCREENSHOT ->
                            SCREEN_CAPTURE_LARGE_SCREEN_SELECTED_PARTIAL_SCREENSHOT
                        ScreenCaptureType.RECORDING ->
                            SCREEN_CAPTURE_LARGE_SCREEN_SELECTED_PARTIAL_RECORDING
                    }
                }
                ScreenCaptureRegion.APP_WINDOW -> {
                    when (type) {
                        ScreenCaptureType.SCREENSHOT ->
                            SCREEN_CAPTURE_LARGE_SCREEN_SELECTED_APP_WINDOW_SCREENSHOT
                        ScreenCaptureType.RECORDING ->
                            SCREEN_CAPTURE_LARGE_SCREEN_SELECTED_APP_WINDOW_RECORDING
                    }
                }
            }
        }
    }
}
