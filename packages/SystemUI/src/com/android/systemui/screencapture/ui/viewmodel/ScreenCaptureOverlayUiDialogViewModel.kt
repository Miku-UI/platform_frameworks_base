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

package com.android.systemui.screencapture.ui.viewmodel

import android.view.MotionEvent
import androidx.compose.runtime.getValue
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.domain.interactor.ScreenCaptureUiInteractor
import com.android.systemui.screenrecord.domain.interactor.ScreenRecordingServiceInteractor
import com.android.systemui.screenrecord.shared.model.ScreenRecordingStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.map

class ScreenCaptureOverlayUiDialogViewModel
@Inject
constructor(
    private val screenCaptureUiInteractor: ScreenCaptureUiInteractor,
    recordingServiceInteractor: ScreenRecordingServiceInteractor,
) : HydratedActivatable() {

    private val shouldHideScreenCaptureUi: Boolean by
        recordingServiceInteractor.status
            .map { it !is ScreenRecordingStatus.Stopped }
            .hydratedStateOf(
                "ScreenCaptureOverlayUiDialogViewModel#shouldHideScreenCaptureUi",
                false,
            )

    fun onTouchEvent(motionEvent: MotionEvent): Boolean {
        if (motionEvent.action != MotionEvent.ACTION_OUTSIDE) return false
        if (!shouldHideScreenCaptureUi) return false
        screenCaptureUiInteractor.hide(ScreenCaptureType.RECORD)
        return true
    }
}
