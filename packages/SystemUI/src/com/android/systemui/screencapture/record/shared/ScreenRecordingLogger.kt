/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.screencapture.record.shared

import android.util.Size
import android.view.Surface
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.LogLevel
import com.android.systemui.log.dagger.ScreenRecordingLog
import com.android.systemui.screencapture.record.camera.data.model.StreamConfiguration
import com.android.systemui.screencapture.record.camera.shared.model.CameraState
import com.android.systemui.screenrecord.shared.model.ScreenRecordingParameters
import javax.inject.Inject
import kotlin.time.Duration

private const val TAG = "ScreenRecording"

class ScreenRecordingLogger
@Inject
constructor(@ScreenRecordingLog private val logBuffer: LogBuffer) {

    fun startingScreenRecording(parameters: ScreenRecordingParameters, delay: Duration) {
        logBuffer.log(
            TAG,
            LogLevel.DEBUG,
            {
                int1 = parameters.captureTarget?.taskId ?: -1
                str1 = parameters.audioSource.name
                int2 = parameters.displayId
                bool1 = parameters.shouldShowTaps
                long1 = delay.inWholeMilliseconds
            },
            {
                "startingScreenRecording in ${long1}ms [task=$int1,audio=$str1,display=$int2,taps=$bool1]"
            },
        )
    }

    fun startScreenRecording(parameters: ScreenRecordingParameters) {
        logBuffer.log(
            TAG,
            LogLevel.DEBUG,
            {
                int1 = parameters.captureTarget?.taskId ?: -1
                str1 = parameters.audioSource.name
                int2 = parameters.displayId
                bool1 = parameters.shouldShowTaps
            },
            { "startScreenRecording [task=$int1,audio=$str1,display=$int2,taps=$bool1]" },
        )
    }

    fun stopScreenRecording(reason: Int) {
        logBuffer.log(
            TAG,
            LogLevel.DEBUG,
            { int1 = reason },
            { "stopScreenRecording [reason=$int1]" },
        )
    }

    fun cameraConnectedChanged(isConnected: Boolean, isSupported: Boolean) {
        logBuffer.log(
            TAG,
            LogLevel.DEBUG,
            {
                bool1 = isConnected
                bool2 = isSupported
            },
            { "cameraConnectedChanged [isConnected=$bool1,isSupported=$bool2]" },
        )
    }

    fun cameraSurfaceReady(surfaceHash: Int?, surfaceSize: Size) {
        logBuffer.log(
            TAG,
            LogLevel.DEBUG,
            {
                int1 = surfaceHash ?: -1
                str1 = surfaceSize.toString()
            },
            { "cameraSurfaceReady [surfaceHash=$int1,size=$str1]" },
        )
    }

    fun stopCameraStream() {
        logBuffer.log(TAG, LogLevel.DEBUG, {}, { "stopCameraStream" })
    }

    fun startCameraStream(surfaceHash: Int?, surfaceSize: Size) {
        logBuffer.log(
            TAG,
            LogLevel.DEBUG,
            {
                int1 = surfaceHash ?: -1
                str1 = surfaceSize.toString()
            },
            { "startCameraStream [surfaceHash=$int1,size=$str1]" },
        )
    }

    fun prepareCameraStream(
        displayUniqueId: String?,
        @Surface.Rotation displayRotation: Int,
        resultConfiguration: StreamConfiguration?,
    ) {
        logBuffer.log(
            TAG,
            LogLevel.DEBUG,
            {
                str1 = displayUniqueId ?: "null"
                int1 = displayRotation
                str2 = resultConfiguration?.toString() ?: "null"
            },
            { "prepareCameraStream [display=$str1,rotation=$int1,config=$str2]" },
        )
    }

    fun cameraTapped(canTap: Boolean) {
        logBuffer.log(TAG, LogLevel.DEBUG, { bool1 = canTap }, { "cameraTapped canTap=$bool1" })
    }

    fun cameraBackgroundChanged(color: Int) {
        logBuffer.log(
            TAG,
            LogLevel.DEBUG,
            { str1 = Integer.toHexString(color) },
            { "cameraBackgroundChanged [color=#$str1]" },
        )
    }

    fun cameraStateChanged(cameraState: CameraState) {
        logBuffer.log(
            TAG,
            LogLevel.DEBUG,
            { str1 = cameraState.name },
            { "cameraStateChanged [state=$str1]" },
        )
    }
}
