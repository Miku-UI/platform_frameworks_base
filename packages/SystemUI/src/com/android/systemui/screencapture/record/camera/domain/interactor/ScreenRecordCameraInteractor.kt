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

import android.content.res.Resources
import android.graphics.Color
import android.graphics.Region
import android.util.Size
import android.view.Surface
import androidx.annotation.ColorInt
import androidx.core.content.res.use
import com.android.app.tracing.coroutines.flow.stateInTraced
import com.android.systemui.content.res.map
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ScreenCapture
import com.android.systemui.screencapture.common.ScreenCaptureScope
import com.android.systemui.screencapture.common.ScreenCaptureStartable
import com.android.systemui.screencapture.record.camera.data.model.StreamConfiguration
import com.android.systemui.screencapture.record.camera.data.model.isValid
import com.android.systemui.screencapture.record.camera.data.repository.ScreenRecordCameraRepository
import com.android.systemui.screencapture.record.camera.shared.model.CameraState
import com.android.systemui.screencapture.record.shared.ScreenRecordingLogger
import com.android.systemui.screenrecord.domain.interactor.ScreenRecordingServiceInteractor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

@ScreenCaptureScope
class ScreenRecordCameraInteractor
@Inject
constructor(
    @Main resources: Resources,
    @ScreenCapture private val coroutineScope: CoroutineScope,
    private val repository: ScreenRecordCameraRepository,
    private val screenRecordingServiceInteractor: ScreenRecordingServiceInteractor,
    private val logger: ScreenRecordingLogger,
) : ScreenCaptureStartable {

    val cameraBackgroundColors: List<Int> =
        resources.obtainTypedArray(R.array.screen_record_color_palette).use { array ->
            array.map { index -> getColor(index, Color.TRANSPARENT) }
        }
    val errors: Flow<Int> = repository.errors
    val state: StateFlow<CameraState> = repository.state
    val isConnected: Flow<Boolean> = repository.isConnected
    val cameraSubjectBounds: StateFlow<Region?> = repository.cameraSubjectBounds

    private val _cameraBackground = MutableStateFlow(Color.TRANSPARENT)
    val cameraBackground: StateFlow<Int> = _cameraBackground

    private val surfaceParameters = MutableStateFlow<CameraSurfaceParameters?>(null)
    private val displayParameters = MutableStateFlow<CameraDisplayParameters?>(null)

    val isCameraSupported: Flow<Boolean> =
        repository.isConnected
            .map { isConnected ->
                val isCameraSupported = repository.isCameraSupported()
                logger.cameraConnectedChanged(
                    isConnected = isConnected,
                    isSupported = isCameraSupported,
                )
                isConnected && isCameraSupported
            }
            .stateInTraced(
                "ScreenRecordCameraInteractor#isCameraSupported",
                coroutineScope,
                SharingStarted.Eagerly,
                null,
            )
            .filterNotNull()
    val canTap: StateFlow<Boolean> =
        combine(isCameraSupported, screenRecordingServiceInteractor.status) {
                isCameraSupported,
                recordingStatus ->
                !recordingStatus.isRecording && isCameraSupported && repository.isOnTapSupported()
            }
            .stateInTraced(
                "ScreenRecordCameraInteractor#isOnTapSupported",
                coroutineScope,
                SharingStarted.Eagerly,
                false,
            )
    val canChangeBackgroundColor: StateFlow<Boolean> =
        isCameraSupported
            .flatMapLatest { if (it) repository.isBackgroundColorAvailable else flowOf(false) }
            .map { isAvailable -> isAvailable && repository.isBackgroundColorSupported() }
            .stateInTraced(
                "ScreenRecordCameraInteractor#isBackgroundColorSupported",
                coroutineScope,
                SharingStarted.Eagerly,
                false,
            )
    private val statelessStreamConfiguration: Flow<StreamConfiguration?> =
        combine(repository.isConnected.filter { it }, displayParameters.filterNotNull()) {
                _,
                displayParameters ->
                repository
                    .prepareStream(
                        displayUniqueId = displayParameters.uniqueId,
                        displayRotation = displayParameters.rotation,
                    )
                    .also { config ->
                        logger.prepareCameraStream(
                            displayUniqueId = displayParameters.uniqueId,
                            displayRotation = displayParameters.rotation,
                            resultConfiguration = config,
                        )
                    }
            }
            .filter {
                // null is a valid value here indicating that something went wrong
                it == null || it.isValid()
            }

    val streamConfiguration: StateFlow<StreamConfiguration?> =
        statelessStreamConfiguration.stateInTraced(
            "ScreenRecordCameraInteractor#optimalCameraStreamSize",
            coroutineScope,
            SharingStarted.Eagerly,
            null,
        )

    override suspend fun start() {
        // Keep the service connected throughout the recording for faster camera on/off
        coroutineScope.launch { connect() }

        // Populate current background color when camera is connected
        cameraBackground
            .onEach { color ->
                repository.setBackgroundColor(color)
                logger.cameraBackgroundChanged(color)
            }
            .launchIn(coroutineScope)

        repository.isConnected
            .onEach {
                if (it) {
                    repository.setBackgroundColor(cameraBackground.value)
                    logger.cameraBackgroundChanged(cameraBackground.value)
                }
            }
            .launchIn(coroutineScope)

        state.onEach { logger.cameraStateChanged(it) }.launchIn(coroutineScope)

        combine(
                surfaceParameters.filterNotNull(),
                // Observe stateless configuration here to startStream after any prepareStream call
                statelessStreamConfiguration.filterNotNull(),
            ) { params, optimalCameraStreamSize ->
                if (
                    params.surface == null ||
                        params.size != optimalCameraStreamSize.cameraStreamSize
                ) {
                    return@combine
                }
                if (state.value == CameraState.Started || state.value == CameraState.Starting) {
                    stopStream()
                }
                repository.startStream(
                    surface = params.surface,
                    size = optimalCameraStreamSize.cameraStreamSize,
                )
                logger.startCameraStream(
                    surfaceHash = params.surface.hashCode(),
                    surfaceSize = optimalCameraStreamSize.cameraStreamSize,
                )
            }
            .launchIn(coroutineScope)
    }

    private suspend fun connect(): Nothing = suspendCancellableCoroutine { continuation ->
        repository.connect()

        continuation.invokeOnCancellation { repository.disconnect() }
    }

    fun onSurfaceReady(surface: Surface, size: Size) {
        logger.cameraSurfaceReady(surface.hashCode(), size)
        surfaceParameters.value = CameraSurfaceParameters(surface = surface, size = size)
    }

    fun onDisplayReady(uniqueId: String?, @Surface.Rotation rotation: Int) {
        displayParameters.value = CameraDisplayParameters(uniqueId = uniqueId, rotation = rotation)
    }

    suspend fun stopStream() {
        repository.stopStream()
        logger.stopCameraStream()
    }

    fun setBackgroundColor(@ColorInt color: Int) {
        require(color in cameraBackgroundColors || color == Color.TRANSPARENT) {
            "color should be one of cameraBackgroundColors or transparent"
        }
        _cameraBackground.value = color
    }

    suspend fun onTap() {
        if (canTap.value) {
            repository.onTap()
        }
        logger.cameraTapped(canTap.value)
    }
}

private data class CameraDisplayParameters(
    val uniqueId: String?,
    @Surface.Rotation val rotation: Int,
)

private data class CameraSurfaceParameters(val surface: Surface?, val size: Size?)
