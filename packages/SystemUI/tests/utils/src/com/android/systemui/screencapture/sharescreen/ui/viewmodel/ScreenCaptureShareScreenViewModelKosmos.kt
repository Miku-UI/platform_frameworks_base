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

package com.android.systemui.screencapture.sharescreen.ui.viewmodel

import android.content.applicationContext
import android.content.packageManager
import android.view.accessibility.accessibilityManager
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.mediaprojection.MediaProjectionMetricsLogger
import com.android.systemui.screencapture.common.ui.viewmodel.appContentsViewModelFactory
import com.android.systemui.screencapture.common.ui.viewmodel.displaysViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.drawableLoaderViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.recentTasksViewModel
import com.android.systemui.screencapture.sharescreen.domain.interactor.shareScreenUiInteractor
import com.android.systemui.statusbar.quickactions.sharescreen.domain.interactor.shareScreenPrivacyIndicatorInteractor
import com.android.systemui.util.mockito.mock

val Kosmos.screenCaptureShareScreenViewModelFactory by Fixture {
    object : ScreenCaptureShareScreenViewModel.Factory {
        override fun create(
            thumbnailWidthPx: Int,
            thumbnailHeightPx: Int,
            iconSizePx: Int,
        ): ScreenCaptureShareScreenViewModel {
            return ScreenCaptureShareScreenViewModel(
                packageManager,
                drawableLoaderViewModel,
                shareScreenUiInteractor,
                mock<MediaProjectionMetricsLogger>(),
                thumbnailWidthPx,
                thumbnailHeightPx,
                iconSizePx,
                appContentsViewModelFactory,
                recentTasksViewModel,
                displaysViewModel,
                accessibilityManager,
                applicationContext,
                shareScreenPrivacyIndicatorInteractor,
            )
        }
    }
}

val Kosmos.screenCaptureShareScreenViewModel by Fixture {
    screenCaptureShareScreenViewModelFactory.create(200, 100, 50)
}
