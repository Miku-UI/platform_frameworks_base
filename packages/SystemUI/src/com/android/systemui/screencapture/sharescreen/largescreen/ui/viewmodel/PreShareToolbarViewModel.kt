/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.screencapture.sharescreen.largescreen.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureTarget
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModelImpl
import com.android.systemui.screencapture.domain.interactor.ScreenCaptureUiInteractor
import com.android.systemui.statusbar.featurepods.sharescreen.domain.interactor.ShareScreenPrivacyIndicatorInteractor
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/** Models UI state for the Screen Share feature. */
class PreShareToolbarViewModel
@AssistedInject
constructor(
    private val drawableLoaderViewModelImpl: DrawableLoaderViewModelImpl,
    private val screenCaptureUiInteractor: ScreenCaptureUiInteractor,
    private val shareScreenPrivacyIndicatorInteractor: ShareScreenPrivacyIndicatorInteractor,
) : HydratedActivatable(), DrawableLoaderViewModel by drawableLoaderViewModelImpl {
    var selectedScreenCaptureTarget: ScreenCaptureTarget by
        mutableStateOf(ScreenCaptureTarget.AppContent(contentId = 0))

    fun onCloseClicked() {
        screenCaptureUiInteractor.hide(ScreenCaptureType.SHARE_SCREEN)
    }

    fun onShareClicked() {
        screenCaptureUiInteractor.hide(ScreenCaptureType.SHARE_SCREEN)
        shareScreenPrivacyIndicatorInteractor.showChip()
    }

    @AssistedFactory
    interface Factory {
        fun create(): PreShareToolbarViewModel
    }
}
