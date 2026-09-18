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

package com.android.systemui.screencapture.record.largescreen.ui.viewmodel

import android.content.applicationContext
import android.hardware.display.displayManager
import com.android.internal.logging.uiEventLogger
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.screencapture.common.shared.model.screenCaptureUiParameters
import com.android.systemui.screencapture.common.ui.viewmodel.drawableLoaderViewModel
import com.android.systemui.screencapture.domain.interactor.screenCaptureUiInteractor
import com.android.systemui.screencapture.record.largescreen.domain.interactor.appWindowInteractor
import com.android.systemui.screencapture.record.largescreen.domain.interactor.largeScreenCaptureParametersInteractor
import com.android.systemui.screencapture.record.largescreen.domain.interactor.screenshotInteractor
import com.android.systemui.screenrecord.domain.interactor.screenRecordingServiceInteractor

val Kosmos.preCaptureViewModelFactory by Fixture {
    object : PreCaptureViewModel.Factory {
        override fun create(displayId: Int): PreCaptureViewModel {
            return PreCaptureViewModel(
                displayId = displayId,
                backgroundScope = backgroundScope,
                displayManager = displayManager,
                screenshotInteractor = screenshotInteractor,
                drawableLoaderViewModel = drawableLoaderViewModel,
                screenCaptureUiInteractor = screenCaptureUiInteractor,
                screenRecordingServiceInteractor = screenRecordingServiceInteractor,
                screenCaptureUiParams = screenCaptureUiParameters,
                largeScreenCaptureParametersInteractor = largeScreenCaptureParametersInteractor,
                uiEventLogger = uiEventLogger,
                toolbarViewModelFactory = preCaptureToolbarViewModelFactory,
                appWindowInteractor = appWindowInteractor,
                context = applicationContext,
            )
        }
    }
}

val Kosmos.preCaptureViewModel by Fixture { preCaptureViewModelFactory.create(123) }
