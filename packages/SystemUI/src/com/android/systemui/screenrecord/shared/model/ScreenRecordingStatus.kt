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

import kotlin.time.Duration

/**
 * Current status of the recording service: [Starting] (optional for when the start is delayed) ->
 * [Started] -> [Stopped].
 */
sealed interface ScreenRecordingStatus {

    val isRecording: Boolean

    /** @see ScreenRecordingStatus */
    data class Starting(val untilStarted: Duration, val parameters: ScreenRecordingParameters) :
        ScreenRecordingStatus {

        override val isRecording: Boolean = true
    }

    /** @see ScreenRecordingStatus */
    data class Started(val parameters: ScreenRecordingParameters) : ScreenRecordingStatus {

        override val isRecording: Boolean = true
    }

    /** @see ScreenRecordingStatus */
    data class Stopped(val reason: Int) : ScreenRecordingStatus {

        override val isRecording: Boolean = false

        companion object {
            const val STOP_REASON_NOT_STARTED = -1
        }
    }
}
