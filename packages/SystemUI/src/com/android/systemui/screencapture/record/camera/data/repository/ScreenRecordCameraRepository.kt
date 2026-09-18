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

package com.android.systemui.screencapture.record.camera.data.repository

import android.graphics.Region
import android.util.Size
import android.view.Surface
import androidx.annotation.ColorInt
import com.android.systemui.screencapture.record.camera.data.model.StreamConfiguration
import com.android.systemui.screencapture.record.camera.shared.model.CameraState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ScreenRecordCameraRepository {

    val errors: Flow<Int>
    val state: StateFlow<CameraState>
    val isConnected: StateFlow<Boolean>
    val cameraSubjectBounds: StateFlow<Region?>
    val isBackgroundColorAvailable: StateFlow<Boolean>

    fun connect()

    fun disconnect()

    suspend fun startStream(surface: Surface, size: Size)

    suspend fun stopStream()

    suspend fun isCameraSupported(): Boolean

    suspend fun isOnTapSupported(): Boolean

    suspend fun isBackgroundColorSupported(): Boolean

    suspend fun prepareStream(
        displayUniqueId: String?,
        @Surface.Rotation displayRotation: Int,
    ): StreamConfiguration?

    suspend fun setBackgroundColor(@ColorInt color: Int)

    suspend fun onTap()
}
