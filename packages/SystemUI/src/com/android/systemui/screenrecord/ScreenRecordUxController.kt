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

package com.android.systemui.screenrecord

import android.app.Dialog
import android.media.projection.StopReason
import com.android.systemui.statusbar.policy.CallbackController

interface ScreenRecordUxController :
    CallbackController<ScreenRecordUxController.StateChangeCallback> {

    val isScreenCaptureDisabled: Boolean

    /** @see RecordingController.isStarting */
    val isStarting: Boolean

    /** @see RecordingController.isRecording */
    val isRecording: Boolean

    /** @see RecordingController.getStopReason */
    @get:StopReason val stopReason: Int

    // --- Consolidated functions that handles UX events ---

    fun onScreenRecordQsTileClick()

    // --- 1:1 wrapped RecordingController Public APIs ---
    // TODO(b/409330121): we will consolidate them into functions that handles UX events.

    /** @see RecordingController.createScreenRecordDialog */
    fun createScreenRecordDialog(onStartRecordingClicked: Runnable?): Dialog

    /** @see RecordingController.createScreenRecordPermissionContentManager */
    fun createScreenRecordPermissionContentManager(
        onStartRecordingClicked: Runnable?
    ): ScreenRecordPermissionContentManager

    /** @see RecordingController.startCountdown */
    fun startCountdown(ms: Long, interval: Long, start: Runnable, stop: Runnable)

    /** @see RecordingController.cancelCountdown */
    fun cancelCountdown()

    /** @see RecordingController.stopRecording */
    fun stopRecording(@StopReason stopReason: Int)

    /**
     * @see RecordingController.updateState Note: This is typically called internally in response to
     *   broadcasts, but wrapped here for 1:1 completeness.
     */
    fun updateState(isRecording: Boolean)

    /** A callback for changes in the screen recording state exposed by this controller. */
    interface StateChangeCallback {
        /**
         * Called when a countdown to recording has updated.
         *
         * @param millisUntilFinished Time in ms remaining in the countdown.
         */
        fun onCountdown(millisUntilFinished: Long) {}

        /**
         * Called when a countdown to recording has ended. This is called both when the countdown
         * finishes normally *and* when it's cancelled.
         */
        fun onCountdownEnd() {}

        /** Called when a screen recording has started. */
        fun onRecordingStart() {}

        /** Called when a screen recording has ended. */
        fun onRecordingEnd() {}
    }

    companion object {
        const val INTENT_UPDATE_STATE = "com.android.systemui.screenrecord.UPDATE_STATE"
        const val EXTRA_STATE = "extra_state"
    }
}
