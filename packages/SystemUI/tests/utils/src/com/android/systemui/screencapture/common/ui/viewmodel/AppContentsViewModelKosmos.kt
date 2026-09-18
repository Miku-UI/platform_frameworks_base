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

package com.android.systemui.screencapture.common.ui.viewmodel

import com.android.systemui.kosmos.Kosmos
import com.android.systemui.screencapture.common.domain.interactor.screenCaptureAppContentInteractor

var Kosmos.appContentsViewModel: AppContentsViewModel by
    Kosmos.Fixture { appContentsViewModelFactory.create("fake.host.package", 200, 100, 50) }

val Kosmos.fakeAppContentsViewModel by
    Kosmos.Fixture { FakeAppContentsViewModel(appContentViewModelFactory, drawableLoaderViewModel) }

var Kosmos.appContentsViewModelFactory: AppContentsViewModel.Factory by
    Kosmos.Fixture {
        object : AppContentsViewModel.Factory {
            override fun create(
                hostPackageName: String,
                thumbnailWidthPx: Int,
                thumbnailHeightPx: Int,
                iconSizePx: Int,
            ): AppContentsViewModel =
                AppContentsViewModelImpl(
                    appContentInteractor = screenCaptureAppContentInteractor,
                    appContentViewModelFactory = appContentViewModelFactory,
                    drawableLoaderViewModel = drawableLoaderViewModel,
                    audioSwitchViewModel = AudioSwitchViewModelImpl(),
                    hostPackageName = hostPackageName,
                    thumbnailWidthPx = thumbnailWidthPx,
                    thumbnailHeightPx = thumbnailHeightPx,
                    iconSizePx = iconSizePx,
                )
        }
    }

val Kosmos.fakeAppContentsViewModelFactory: AppContentsViewModel.Factory by
    Kosmos.Fixture {
        object : AppContentsViewModel.Factory {
            override fun create(
                hostPackageName: String,
                thumbnailWidthPx: Int,
                thumbnailHeightPx: Int,
                iconSizePx: Int,
            ): AppContentsViewModel = fakeAppContentsViewModel
        }
    }
