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

package com.android.systemui.screenrecord.shared.model

import android.view.Display
import com.android.systemui.mediaprojection.MediaProjectionCaptureTarget
import com.android.systemui.screenrecord.ScreenRecordingAudioSource

/** Convenient factory to create [ScreenRecordingParameters] in the tests. */
object ScreenRecordingParametersFactory {

    fun screenRecordingParameters(
        captureTarget: MediaProjectionCaptureTarget? = null,
        audioSource: ScreenRecordingAudioSource = ScreenRecordingAudioSource.NONE,
        displayId: Int = Display.DEFAULT_DISPLAY,
        shouldShowTaps: Boolean = false,
    ): ScreenRecordingParameters =
        ScreenRecordingParameters(
            captureTarget = captureTarget,
            audioSource = audioSource,
            displayId = displayId,
            shouldShowTaps = shouldShowTaps,
        )
}
