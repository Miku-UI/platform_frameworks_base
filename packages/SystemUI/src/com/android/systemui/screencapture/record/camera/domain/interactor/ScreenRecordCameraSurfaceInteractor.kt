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

package com.android.systemui.screencapture.record.camera.domain.interactor

import android.util.Size
import android.view.Surface
import com.android.systemui.screencapture.common.ScreenCapture
import com.android.systemui.screencapture.common.ScreenCaptureReleasable
import com.android.systemui.screencapture.common.ScreenCaptureScope
import com.android.systemui.util.kotlin.pairwiseBy
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.takeWhile

/**
 * Controls the [ScreenRecordCameraInteractor] camera stream. It makes sure to gracefully
 * [stopStream] when the [ScreenCaptureScope] is being released using the [ScreenCaptureReleasable].
 */
@ScreenCaptureScope
class ScreenRecordCameraSurfaceInteractor
@Inject
constructor(
    @ScreenCapture private val coroutineScope: CoroutineScope,
    private val cameraInteractor: ScreenRecordCameraInteractor,
) : ScreenCaptureReleasable {

    private val surfaceStatus = MutableStateFlow<Status>(Status.Inactive)
    private var consumingJob: Job? = null

    /** Releases the resources occupied by this interactor */
    override suspend fun release() {
        surfaceStatus.value = Status.Inactive
        consumingJob?.join()
        consumingJob = null
    }

    fun startStream(surface: Surface, width: Int, height: Int) {
        surfaceStatus.value = Status.Active.Started(surface, Size(width, height))
        if (consumingJob != null) {
            return
        }
        consumingJob =
            surfaceStatus
                .takeWhile { it is Status.Active }
                .map { it as Status.Active }
                .onStart { emit(Status.Active.Stopped) }
                .pairwiseBy { old: Status.Active, new: Status.Active ->
                    if (old is Status.Active.Started) {
                        cameraInteractor.stopStream()
                    }
                    if (new is Status.Active.Started) {
                        cameraInteractor.onSurfaceReady(new.surface, new.size)
                    }
                }
                .onCompletion { cameraInteractor.stopStream() }
                .launchIn(coroutineScope)
    }

    fun stopStream() {
        surfaceStatus.value = Status.Active.Stopped
    }

    private sealed interface Status {
        data object Inactive : Status

        sealed interface Active : Status {

            data class Started(val surface: Surface, val size: Size) : Active

            data object Stopped : Active
        }
    }
}
