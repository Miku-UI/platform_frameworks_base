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

package com.android.systemui.screencapture.record.camera.ui.viewmodel

import android.util.Size
import android.view.Display
import android.view.Surface
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import com.android.app.tracing.coroutines.flow.collectTraced
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.screencapture.record.camera.domain.interactor.ScreenRecordCameraInteractor
import com.android.systemui.screencapture.record.camera.domain.interactor.ScreenRecordCameraSurfaceInteractor
import com.android.systemui.screencapture.record.camera.shared.model.CameraState
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ScreenCaptureCameraViewModel
@AssistedInject
constructor(
    private val screenRecordCameraSurfaceInteractor: ScreenRecordCameraSurfaceInteractor,
    private val screenRecordCameraInteractor: ScreenRecordCameraInteractor,
) : HydratedActivatable() {

    private val taps = Channel<Unit>()

    val shouldShowCamera: Boolean? by
        screenRecordCameraInteractor.state.mapHydrate(
            "ScreenCaptureCameraViewModel#shouldShowCamera"
        ) {
            it == CameraState.Started
        }

    val surfaceSize: Size? by
        screenRecordCameraInteractor.streamConfiguration.mapHydrate(
            "ScreenCaptureCameraViewModel#surfaceSize"
        ) {
            it?.cameraStreamSize
        }

    val outputStreamSize: Size? by
        screenRecordCameraInteractor.streamConfiguration.mapHydrate(
            "ScreenCaptureCameraViewModel#outputStreamSize"
        ) {
            it?.outputStreamSize
        }

    val areTapsSupported: Boolean by
        screenRecordCameraInteractor.canTap.hydratedStateOf(
            "ScreenCaptureCameraViewModel#areTapsSupported"
        )

    override suspend fun onActivated() {
        super.onActivated()
        coroutineScope {
            launch {
                taps.consumeAsFlow().collectTraced("ScreenCaptureCameraViewModel#taps") {
                    screenRecordCameraInteractor.onTap()
                }
            }
        }
    }

    fun onSurfaceReady(surface: Surface, width: Int, height: Int) {
        screenRecordCameraSurfaceInteractor.startStream(surface, width, height)
    }

    fun onSurfaceDestroyed() {
        screenRecordCameraSurfaceInteractor.stopStream()
    }

    fun onSurfaceClicked() {
        taps.trySend(Unit)
    }

    fun onDisplayReady(display: Display) {
        screenRecordCameraInteractor.onDisplayReady(
            uniqueId = display.uniqueId,
            rotation = display.rotation,
        )
    }

    @AssistedFactory
    interface Factory {
        fun create(): ScreenCaptureCameraViewModel
    }

    private fun <T, R> StateFlow<T>.mapHydrate(traceName: String, mapping: (T) -> R): State<R> {
        return map(mapping).hydratedStateOf(traceName = traceName, initialValue = mapping(value))
    }
}
