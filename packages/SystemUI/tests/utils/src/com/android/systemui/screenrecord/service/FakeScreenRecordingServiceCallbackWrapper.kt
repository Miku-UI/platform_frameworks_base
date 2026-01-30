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

package com.android.systemui.screenrecord.service

import android.graphics.drawable.Icon
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeScreenRecordingServiceCallbackWrapper(private val real: IScreenRecordingServiceCallback) :
    IScreenRecordingServiceCallback.Stub() {

    private val _status = MutableStateFlow<RecordingStatus>(RecordingStatus.Initial)
    val status: Flow<RecordingStatus?> = _status.asStateFlow()

    override fun onRecordingStarted() {
        _status.value = RecordingStatus.Started
        real.onRecordingStarted()
    }

    override fun onRecordingInterrupted(userId: Int, reason: Int) {
        _status.value = RecordingStatus.Interrupted(userId = userId, reason = reason)
        real.onRecordingInterrupted(userId, reason)
    }

    override fun onRecordingSaved(recordingUri: Uri, thumbnail: Icon) {
        _status.value = RecordingStatus.Saved(recordingUri = recordingUri, thumbnail = thumbnail)
        real.onRecordingSaved(recordingUri, thumbnail)
    }

    sealed interface RecordingStatus {

        data object Initial : RecordingStatus

        data object Started : RecordingStatus

        data class Saved(val recordingUri: Uri, val thumbnail: Icon) : RecordingStatus

        data class Interrupted(val userId: Int, val reason: Int) : RecordingStatus
    }
}
