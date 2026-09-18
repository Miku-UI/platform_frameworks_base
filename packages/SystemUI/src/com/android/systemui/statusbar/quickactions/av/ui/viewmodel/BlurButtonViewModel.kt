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

package com.android.systemui.statusbar.quickactions.av.ui.viewmodel

import androidx.compose.runtime.getValue
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.statusbar.quickactions.av.domain.interactor.DesktopEffectInteractor
import com.android.systemui.statusbar.quickactions.av.shared.model.BlurLevel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.map

class BlurButtonViewModel
@AssistedInject
constructor(
    desktopEffectInteractor: DesktopEffectInteractor,
    @Assisted private val setCurrentPage: (PageType) -> Unit,
) : ButtonViewModel, HydratedActivatable() {
    override val state by
        desktopEffectInteractor.model
            .map {
                val blurLevel = it.blurLevel
                ButtonUiState(
                    isEnabled = blurLevel != BlurLevel.OFF,
                    mainTitle = com.android.systemui.res.R.string.av_blur,
                    image = icon(blurLevel),
                )
            }
            .hydratedStateOf(initialValue = ButtonUiState())

    override suspend fun onClick() {
        setCurrentPage(PageType.BLUR)
    }

    private fun icon(blurLevel: BlurLevel): Int =
        when (blurLevel) {
            BlurLevel.OFF -> com.android.systemui.res.R.drawable.gs_background_blur_full_off
            BlurLevel.LIGHT -> com.android.systemui.res.R.drawable.gs_background_blur_light
            BlurLevel.FULL -> com.android.systemui.res.R.drawable.gs_background_blur_full
        }

    /** A factory to be used to create view model instances. */
    @AssistedFactory
    interface Factory {
        fun create(setCurrentPage: (PageType) -> Unit): BlurButtonViewModel
    }
}
