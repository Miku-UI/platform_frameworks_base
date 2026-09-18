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

package com.android.systemui.screencapture.record.smallscreen.domain.interactor

import android.hardware.display.defaultDisplay
import com.android.systemui.display.data.repository.displayRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.screencapture.common.domain.interactor.screenCaptureLabelInteractor
import com.android.systemui.screencapture.common.domain.interactor.screenCaptureMarkupInteractor
import com.android.systemui.screencapture.common.domain.interactor.screenCaptureRecentTaskInteractor
import com.android.systemui.screencapture.record.camera.domain.interactor.screenRecordCameraSurfaceInteractor
import com.android.systemui.screencapture.record.domain.interactor.screenCaptureRecordParametersInteractor
import com.android.systemui.screencapture.record.smallscreen.data.repository.recordDetailsTargetRepository
import com.android.systemui.screenrecord.domain.interactor.screenRecordingServiceInteractor

val Kosmos.recordDetailsTargetInteractor: RecordDetailsTargetInteractor by
    Kosmos.Fixture {
        RecordDetailsTargetInteractor(
            display = defaultDisplay,
            coroutineScope = applicationCoroutineScope,
            recordDetailsTargetRepository = recordDetailsTargetRepository,
            recordingServiceInteractor = screenRecordingServiceInteractor,
            recentTaskInteractor = screenCaptureRecentTaskInteractor,
            screenCaptureLabelInteractor = screenCaptureLabelInteractor,
            parametersInteractor = screenCaptureRecordParametersInteractor,
            cameraInteractor = screenRecordCameraSurfaceInteractor,
            markupInteractor = screenCaptureMarkupInteractor,
            displayRepository = displayRepository,
        )
    }
