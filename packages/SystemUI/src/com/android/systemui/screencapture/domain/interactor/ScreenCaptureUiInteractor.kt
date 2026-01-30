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

package com.android.systemui.screencapture.domain.interactor

import android.content.res.Resources
import com.android.dream.lowlight.dagger.qualifiers.Application
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiParameters
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiState
import com.android.systemui.screencapture.data.repository.ScreenCaptureUiRepository
import com.android.systemui.statusbar.policy.ConfigurationController
import com.android.systemui.statusbar.policy.onConfigChanged
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

@SysUISingleton
class ScreenCaptureUiInteractor
@Inject
constructor(
    @Main private val resources: Resources,
    @Application private val scope: CoroutineScope,
    configurationController: ConfigurationController,
    private val repository: ScreenCaptureUiRepository,
) {

    val isLargeScreen: Flow<Boolean?> =
        configurationController.onConfigChanged
            .onStart { emit(resources.configuration) }
            .map { resources.getBoolean(R.bool.config_enableLargeScreenScreencapture) }
            .stateIn(scope, SharingStarted.WhileSubscribed(), null)

    fun uiState(type: ScreenCaptureType): Flow<ScreenCaptureUiState> = repository.uiState(type)

    fun show(parameters: ScreenCaptureUiParameters) {
        repository.updateStateForType(type = parameters.screenCaptureType) {
            if (it is ScreenCaptureUiState.Visible) {
                return@updateStateForType it
            } else {
                return@updateStateForType ScreenCaptureUiState.Visible(parameters)
            }
        }
    }

    fun hide(type: ScreenCaptureType) {
        repository.updateStateForType(type) {
            if (it is ScreenCaptureUiState.Invisible) {
                return@updateStateForType it
            } else {
                return@updateStateForType ScreenCaptureUiState.Invisible
            }
        }
    }
}
