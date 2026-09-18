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

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.android.app.tracing.coroutines.launchTraced
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.screencapture.common.domain.interactor.ScreenCaptureThumbnailInteractor
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureDisplay
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.coroutineScope

/** Data for a UI to display a display. */
class DisplayViewModel
@AssistedInject
constructor(
    @Assisted override val model: ScreenCaptureDisplay,
    private val thumbnailInteractor: ScreenCaptureThumbnailInteractor,
) : TargetViewModel, HydratedActivatable() {

    override val icon: Result<Bitmap>? =
        Result.failure(IllegalArgumentException("Displays do not support icons"))

    override val label: Result<CharSequence>? = Result.success(model.label)

    override var thumbnail by mutableStateOf<Result<Bitmap>?>(null)
        private set

    override val backgroundColorOpaque: Color = Color.Black

    override suspend fun onActivated() {
        coroutineScope {
            launchTraced("DisplayViewModel#thumbnail") {
                thumbnail = thumbnailInteractor.loadThumbnail(model)
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(model: ScreenCaptureDisplay): DisplayViewModel
    }
}
