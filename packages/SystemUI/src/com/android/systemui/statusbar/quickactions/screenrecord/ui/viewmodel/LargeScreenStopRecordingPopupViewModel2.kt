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

package com.android.systemui.statusbar.quickactions.screenrecord.ui.viewmodel

import android.media.projection.StopReason
import com.android.systemui.screencapture.common.ScreenCaptureUiScope
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.domain.interactor.ScreenCaptureUiInteractor
import com.android.systemui.screenrecord.domain.interactor.ScreenRecordingServiceInteractor
import com.android.systemui.statusbar.quickactions.popups.ui.viewmodel.StatusBarPopupViewModel
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/** ViewModel used to display screen recording popup in the status bar. */
class LargeScreenStopRecordingPopupViewModel2
@AssistedInject
constructor(
    private val screenRecordingServiceInteractor: ScreenRecordingServiceInteractor,
    private val screenCaptureUiInteractor: ScreenCaptureUiInteractor,
) : StatusBarPopupViewModel {

    fun dismiss() {
        screenCaptureUiInteractor.hide(ScreenCaptureType.RECORD)
    }

    fun onStopButtonTapped() {
        screenRecordingServiceInteractor.stopRecording(StopReason.STOP_HOST_APP)
        dismiss()
    }

    @AssistedFactory
    @ScreenCaptureUiScope
    interface Factory : StatusBarPopupViewModel.Factory.ScreenRecording {
        override fun create(): LargeScreenStopRecordingPopupViewModel2
    }
}
