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

import com.android.systemui.mediaprojection.MediaProjectionCaptureTarget
import com.android.systemui.screenrecord.ScreenRecordingAudioSource
import com.android.systemui.screenrecord.domain.ScreenRecordingParameters
import com.android.systemui.screenrecord.domain.interactor.Status
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest

class FakeScreenRecordingService : IScreenRecordingService.Stub() {

    private val _callback = MutableStateFlow<FakeScreenRecordingServiceCallbackWrapper?>(null)
    val callback: Flow<FakeScreenRecordingServiceCallbackWrapper?> = _callback.asStateFlow()
    val currentCallback: FakeScreenRecordingServiceCallbackWrapper?
        get() = _callback.value

    private val _status = MutableStateFlow<Status>(Status.Initial)
    val status: Flow<Status> = _status.asStateFlow()

    override fun setCallback(callback: IScreenRecordingServiceCallback?) {
        _callback.value = callback?.let(::FakeScreenRecordingServiceCallbackWrapper)
    }

    override fun stopRecording(reason: Int) {
        _status.value = Status.Stopped(reason)
        _callback.value?.onRecordingInterrupted(0, reason)
    }

    override fun startRecording(
        captureTarget: MediaProjectionCaptureTarget?,
        audioSource: Int,
        displayId: Int,
        shouldShowTaps: Boolean,
    ) {
        _status.value =
            Status.Started(
                ScreenRecordingParameters(
                    captureTarget = captureTarget,
                    audioSource = ScreenRecordingAudioSource.entries[audioSource],
                    displayId = displayId,
                    shouldShowTaps = shouldShowTaps,
                )
            )
        _callback.value?.onRecordingStarted()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
val FakeScreenRecordingService.callbackStatus:
    Flow<FakeScreenRecordingServiceCallbackWrapper.RecordingStatus?>
    get() = callback.filterNotNull().flatMapLatest { it.status }
