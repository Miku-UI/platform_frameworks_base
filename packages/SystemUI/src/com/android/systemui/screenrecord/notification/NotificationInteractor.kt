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

package com.android.systemui.screenrecord.notification

import com.android.systemui.screenrecord.ScreenMediaRecorder.SavedRecording
import com.android.systemui.screenrecord.ScreenRecordingAudioSource

/** Notifies user about different stages of screen recording flow. */
interface NotificationInteractor {

    fun notifyRecording(notificationId: Int, audioSource: ScreenRecordingAudioSource)

    fun notifyProcessing(notificationId: Int, audioSource: ScreenRecordingAudioSource)

    fun notifySaved(
        notificationId: Int,
        audioSource: ScreenRecordingAudioSource,
        savedRecording: SavedRecording,
    )

    fun notifyErrorSaving(notificationId: Int)

    fun notifyErrorStarting(notificationId: Int)
}
