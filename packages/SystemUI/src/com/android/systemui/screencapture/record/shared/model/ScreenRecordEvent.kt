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

package com.android.systemui.screencapture.record.shared.model

import com.android.internal.logging.UiEvent
import com.android.internal.logging.UiEventLogger

enum class ScreenRecordEvent(private val id: Int) : UiEventLogger.UiEventEnum {
    @UiEvent(doc = "User starts the recording") SCREEN_RECORD_RECORDING_STARTED(2636),
    @UiEvent(doc = "User stops the recording from the notification")
    SCREEN_RECORD_RECORDING_STOPPED_NOTIFICATION(2637),
    @UiEvent(doc = "User stops the recording from the qs tile")
    SCREEN_RECORD_RECORDING_STOPPED_QS_TILE(2638),
    @UiEvent(doc = "User stops the recording from the toolbar")
    SCREEN_RECORD_RECORDING_STOPPED_TOOLBAR(2639),
    @UiEvent(doc = "User enables mic audio recording") SCREEN_RECORD_AUDIO_MIC_ENABLED(2640),
    @UiEvent(doc = "User disables mic audio recording") SCREEN_RECORD_AUDIO_MIC_DISABLED(2641),
    @UiEvent(doc = "User enables device audio recording") SCREEN_RECORD_AUDIO_DEVICE_ENABLED(2642),
    @UiEvent(doc = "User disables device audio recording")
    SCREEN_RECORD_AUDIO_DEVICE_DISABLED(2643),
    @UiEvent(doc = "User enables the “Show touches” setting before the recording")
    SCREEN_RECORD_SHOW_TOUCHES_ENABLED_PRE_RECORDING(2644),
    @UiEvent(doc = "User disables the “Show touches” setting before the recording")
    SCREEN_RECORD_SHOW_TOUCHES_DISABLED_PRE_RECORDING(2645),
    @UiEvent(doc = "User enables the “Show touches” setting during the recording")
    SCREEN_RECORD_SHOW_TOUCHES_ENABLED_MID_RECORDING(2646),
    @UiEvent(doc = "User disables the “Show touches” setting during the recording")
    SCREEN_RECORD_SHOW_TOUCHES_DISABLED_MID_RECORDING(2647),
    @UiEvent(doc = "User selects a capture target (entire screen)")
    SCREEN_RECORD_TARGET_ENTIRE_SCREEN(2648),
    @UiEvent(doc = "User selects a capture target (single app)")
    SCREEN_RECORD_TARGET_SINGLE_APP(2649),
    @UiEvent(doc = "User enables camera output before the recording")
    SCREEN_RECORD_CAMERA_ENABLED_PRE_RECORDING(2650),
    @UiEvent(doc = "User disables camera output before the recording")
    SCREEN_RECORD_CAMERA_DISABLED_PRE_RECORDING(2651),
    @UiEvent(doc = "User enables camera output during the recording")
    SCREEN_RECORD_CAMERA_ENABLED_MID_RECORDING(2652),
    @UiEvent(doc = "User disables camera output during the recording")
    SCREEN_RECORD_CAMERA_DISABLED_MID_RECORDING(2653),
    @UiEvent(doc = "User interacts with the surface before the recording")
    SCREEN_RECORD_SURFACE_ADJUSTED_PRE_RECORDING(2654),
    @UiEvent(doc = "User interacts with the surface during the recording")
    SCREEN_RECORD_SURFACE_ADJUSTED_MID_RECORDING(2655),
    @UiEvent(doc = "User shared the recording") SCREEN_RECORD_POST_RECORDING_SHARE(2656),
    @UiEvent(doc = "User opens the recording in the editor")
    SCREEN_RECORD_POST_RECORDING_EDIT(2657),
    @UiEvent(doc = "User deletes the recording") SCREEN_RECORD_POST_RECORDING_DELETE(2658),
    @UiEvent(doc = "User selects to retake the recording") SCREEN_RECORD_POST_RECORDING_NEW(2659);

    override fun getId(): Int = id
}
