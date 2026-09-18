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
package com.android.systemui.locationbutton.ui.viewmodel

import androidx.compose.ui.unit.dp
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.locationbutton.domain.interactor.LocationButtonInteractor
import com.android.systemui.locationbutton.shared.model.ButtonModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

class LocationButtonViewModel
@AssistedInject
constructor(
    private val interactor: LocationButtonInteractor,
    @Assisted private val sessionId: Int,
) : HydratedActivatable() {

    fun getButtonViewModel(): ButtonViewModel? = interactor.getButtonState(sessionId)?.toViewModel()

    @AssistedFactory
    interface Factory {
        fun create(sessionId: Int): LocationButtonViewModel
    }

    private fun ButtonModel.toViewModel(): ButtonViewModel {
        return ButtonViewModel(
            width = (width / density).dp,
            height = (height / density).dp,
            paddingLeft = (paddingLeft / density).dp,
            paddingTop = (paddingTop / density).dp,
            paddingRight = (paddingRight / density).dp,
            paddingBottom = (paddingBottom / density).dp,
            backgroundColor = backgroundColor,
            strokeColor = strokeColor,
            strokeWidth = (strokeWidth / density).dp,
            cornerRadius = cornerRadius?.let { (it / density).dp },
            pressedCornerRadius = pressedCornerRadius?.let { (it / density).dp },
            iconTint = iconTint,
            textResId = textResId,
            textColor = textColor,
            configuration = configuration,
            validationFlags = validationFlags,
        )
    }
}
