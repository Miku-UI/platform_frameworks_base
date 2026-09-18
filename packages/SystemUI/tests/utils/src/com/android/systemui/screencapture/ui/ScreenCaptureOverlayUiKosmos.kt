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

package com.android.systemui.screencapture.ui

import android.content.applicationContext
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.screencapture.record.camera.ui.viewmodel.screenCaptureCameraTransformationViewModelFactory
import com.android.systemui.screencapture.record.camera.ui.viewmodel.screenCaptureCameraViewModelFactory
import com.android.systemui.screencapture.ui.viewmodel.screenCaptureOverlayUiDialogViewModel
import com.android.systemui.statusbar.phone.systemUIDialogFactory

val Kosmos.screenCaptureOverlayUi: ScreenCaptureOverlayUi by
    Kosmos.Fixture {
        ScreenCaptureOverlayUi(
            context = applicationContext,
            dialogFactory = systemUIDialogFactory,
            dialogViewModel = screenCaptureOverlayUiDialogViewModel,
            cameraViewModelFactory = screenCaptureCameraViewModelFactory,
            cameraTransformationViewModel = screenCaptureCameraTransformationViewModelFactory,
        )
    }
