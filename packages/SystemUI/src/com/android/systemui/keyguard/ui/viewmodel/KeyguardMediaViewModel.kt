/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.keyguard.ui.viewmodel

import androidx.compose.runtime.getValue
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.media.controls.domain.pipeline.interactor.MediaCarouselInteractor
import com.android.systemui.media.remedia.ui.compose.MediaUiBehavior
import com.android.systemui.media.remedia.ui.viewmodel.MediaCarouselVisibility
import com.android.systemui.media.remedia.ui.viewmodel.MediaViewModel
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

class KeyguardMediaViewModel
@AssistedInject
constructor(
    val mediaViewModelFactory: MediaViewModel.Factory,
    private val mediaCarouselInteractor: MediaCarouselInteractor,
    private val keyguardInteractor: KeyguardInteractor,
) : ExclusiveActivatable() {

    private val hydrator = Hydrator("KeyguardMediaViewModel.hydrator")

    /** Whether the media notification is active */
    val isMediaActive: Boolean by
        hydrator.hydratedStateOf(
            traceName = "isMediaActive",
            source = mediaCarouselInteractor.hasActiveMedia,
        )

    val isDozing: Boolean by
        hydrator.hydratedStateOf(traceName = "isDozing", source = keyguardInteractor.isDozing)

    fun onSwipeToDismiss() = mediaCarouselInteractor.onSwipeToDismiss()

    val mediaUiBehavior =
        MediaUiBehavior(
            isCarouselDismissible = true,
            isCarouselScrollingEnabled = true,
            carouselVisibility = MediaCarouselVisibility.WhenAnyCardIsActive,
        )

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    @AssistedFactory
    interface Factory {
        fun create(): KeyguardMediaViewModel
    }
}
