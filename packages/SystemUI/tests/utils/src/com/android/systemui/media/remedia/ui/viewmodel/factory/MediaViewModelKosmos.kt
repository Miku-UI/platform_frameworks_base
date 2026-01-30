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

package com.android.systemui.media.remedia.ui.viewmodel.factory

import android.content.Context
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.media.remedia.domain.interactor.mediaInteractor
import com.android.systemui.media.remedia.ui.viewmodel.MediaCarouselVisibility
import com.android.systemui.media.remedia.ui.viewmodel.MediaViewModel
import com.android.systemui.media.remedia.ui.viewmodel.mediaFalsingSystem
import com.android.systemui.statusbar.notification.collection.provider.visualStabilityProvider

val Kosmos.mediaViewModelFactory by
    Kosmos.Fixture {
        object : MediaViewModel.Factory {
            override fun create(
                context: Context,
                carouselVisibility: MediaCarouselVisibility,
            ): MediaViewModel {
                return MediaViewModel(
                    interactor = mediaInteractor,
                    falsingSystem = mediaFalsingSystem,
                    visualStabilityProvider = visualStabilityProvider,
                    context = context,
                    carouselVisibility = carouselVisibility,
                )
            }
        }
    }
