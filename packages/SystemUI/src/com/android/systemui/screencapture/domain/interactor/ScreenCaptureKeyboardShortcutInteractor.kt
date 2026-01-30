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

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.screencapture.common.shared.model.LargeScreenCaptureUiParameters
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiParameters
import com.android.systemui.screencapture.record.domain.interactor.ScreenCaptureRecordFeaturesInteractor
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureRegion
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureType as LargeScreenCaptureType
import javax.inject.Inject

/** Handles the resulting actions of screen capture related keyboard shortcuts. */
@SysUISingleton
class ScreenCaptureKeyboardShortcutInteractor
@Inject
constructor(private val screenCaptureUiInteractor: ScreenCaptureUiInteractor) {
    fun attemptPartialRegionScreenshot() {
        // TODO(b/420714826) Check if the large-screen screen capture UI is supported on this device
        // device's display (i.e. the focused display or external display). If not supported,
        // default to taking a fullscreen screenshot.
        if (ScreenCaptureRecordFeaturesInteractor.isLargeScreenScreencaptureEnabled) {
            screenCaptureUiInteractor.show(
                ScreenCaptureUiParameters(
                    screenCaptureType = ScreenCaptureType.RECORD,
                    largeScreenParameters =
                        LargeScreenCaptureUiParameters(
                            defaultCaptureType = LargeScreenCaptureType.SCREENSHOT,
                            defaultCaptureRegion = ScreenCaptureRegion.PARTIAL,
                        ),
                )
            )
        }
    }
}
