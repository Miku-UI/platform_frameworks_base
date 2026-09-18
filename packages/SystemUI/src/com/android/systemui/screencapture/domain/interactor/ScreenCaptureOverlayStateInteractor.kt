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

package com.android.systemui.screencapture.domain.interactor

import androidx.compose.runtime.snapshotFlow
import com.android.app.tracing.coroutines.flow.stateInTraced
import com.android.systemui.screencapture.common.ScreenCapture
import com.android.systemui.screencapture.common.ScreenCaptureScope
import com.android.systemui.screencapture.common.domain.interactor.ScreenCaptureMarkupInteractor
import com.android.systemui.screencapture.record.camera.domain.interactor.ScreenRecordCameraInteractor
import com.android.systemui.screencapture.record.domain.interactor.ScreenCaptureRecordParametersInteractor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

@ScreenCaptureScope
class ScreenCaptureOverlayStateInteractor
@Inject
constructor(
    @ScreenCapture private val scope: CoroutineScope,
    markupInteractor: ScreenCaptureMarkupInteractor,
    parametersInteractor: ScreenCaptureRecordParametersInteractor,
    cameraInteractor: ScreenRecordCameraInteractor,
) {

    val isMarkupInUse: Flow<Boolean> = markupInteractor.enabled
    val isCameraInUse: Flow<Boolean> =
        combine(
                cameraInteractor.isCameraSupported,
                snapshotFlow { parametersInteractor.shouldShowFrontCamera },
            ) { isCameraSupported, shouldShowFrontCamera ->
                isCameraSupported && shouldShowFrontCamera
            }
            .distinctUntilChanged()

    val isVisible: Flow<Boolean> =
        combine(isMarkupInUse, isCameraInUse) { markupEnabled, cameraEnabled ->
                markupEnabled || cameraEnabled
            }
            .stateInTraced(
                "ScreenCaptureOverlayStateInteractor#isVisible",
                scope,
                SharingStarted.Eagerly,
                false,
            )
}
