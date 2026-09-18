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

package com.android.systemui.screencapture.record.smallscreen.ui.viewmodel

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.compose.runtime.getValue
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.screencapture.common.domain.interactor.ScreenCaptureMarkupInteractor
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModel
import com.android.systemui.screencapture.record.camera.domain.interactor.ScreenRecordCameraInteractor
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

class RecordDetailsColorPickerViewModel
@AssistedInject
constructor(
    private val drawableLoaderViewModel: DrawableLoaderViewModel,
    private val markupInteractor: ScreenCaptureMarkupInteractor,
    private val cameraInteractor: ScreenRecordCameraInteractor,
) : HydratedActivatable(), DrawableLoaderViewModel by drawableLoaderViewModel {

    val availableMarkupColors: List<Int> = markupInteractor.availableColors
    val markupColor: Int by
        markupInteractor.color.hydratedStateOf(
            "RecordDetailsMarkupColorPickerViewModel#markupColor"
        )

    fun setMarkupColor(@ColorInt color: Int) {
        markupInteractor.setColor(color)
    }

    val availableCameraColors: List<Int> = cameraInteractor.cameraBackgroundColors
    val cameraColor: Int by
        cameraInteractor.cameraBackground.hydratedStateOf(
            "RecordDetailsMarkupColorPickerViewModel#cameraColor"
        )

    fun onCameraColorClicked(@ColorInt color: Int) {
        cameraInteractor.setBackgroundColor(
            if (color == cameraColor) {
                Color.TRANSPARENT
            } else {
                color
            }
        )
    }

    @AssistedFactory
    interface Factory {
        fun create(): RecordDetailsColorPickerViewModel
    }
}
