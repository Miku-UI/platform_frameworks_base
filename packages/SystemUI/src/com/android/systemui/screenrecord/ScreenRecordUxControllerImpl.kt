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
import com.android.systemui.statusbar.policy.CallbackController

/** [ScreenRecordUxController] implementation of the new screen reactions UX. */
class ScreenRecordUxControllerImpl :
    ScreenRecordUxController, CallbackController<ScreenRecordUxController.StateChangeCallback> {
    override val isScreenCaptureDisabled: Boolean
        get() = TODO("Not yet implemented")

    override val isStarting: Boolean
        get() = TODO("Not yet implemented")

    override val isRecording: Boolean
        get() = TODO("Not yet implemented")

    override val stopReason: Int
        get() = TODO("Not yet implemented")

    override fun onScreenRecordQsTileClick() {
        TODO("Not yet implemented")
    }

    override fun createScreenRecordDialog(onStartRecordingClicked: Runnable?): Dialog {
        TODO("Not yet implemented")
    }

    override fun createScreenRecordPermissionContentManager(
        onStartRecordingClicked: Runnable?
    ): ScreenRecordPermissionContentManager {
        TODO("Not yet implemented")
    }

    override fun startCountdown(ms: Long, interval: Long, start: Runnable, stop: Runnable) {
        TODO("Not yet implemented")
    }

    override fun cancelCountdown() {
        TODO("Not yet implemented")
    }

    override fun stopRecording(stopReason: Int) {
        TODO("Not yet implemented")
    }

    override fun updateState(isRecording: Boolean) {
        TODO("Not yet implemented")
    }

    override fun addCallback(listener: ScreenRecordUxController.StateChangeCallback) {
        TODO("Not yet implemented")
    }

    override fun removeCallback(listener: ScreenRecordUxController.StateChangeCallback) {
        TODO("Not yet implemented")
    }
}
