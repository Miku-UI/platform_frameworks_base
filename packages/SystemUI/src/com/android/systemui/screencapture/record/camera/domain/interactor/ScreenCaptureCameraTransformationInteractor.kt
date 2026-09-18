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

import com.android.systemui.screencapture.common.ScreenCaptureScope
import com.android.systemui.screencapture.record.camera.data.repository.ScreenCaptureCameraTransformationRepository
import javax.inject.Inject

private const val MIN_SCALE = 0.3f
private const val MAX_SCALE = 3f

@ScreenCaptureScope
class ScreenCaptureCameraTransformationInteractor
@Inject
constructor(private val repository: ScreenCaptureCameraTransformationRepository) {

    var isTransforming: Boolean by repository::isTransforming
    var offsetX: Float by repository::offsetX
    var offsetY: Float by repository::offsetY
    var scale: Float
        get() = repository.scale
        set(value) {
            repository.scale = value.coerceIn(MIN_SCALE, MAX_SCALE)
        }

    var rotation: Float by repository::rotation
}
