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

import android.content.Context
import android.os.UserHandle
import android.widget.Toast
import com.android.internal.logging.UiEventLogger
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.mediaprojection.devicepolicy.ScreenCaptureDevicePolicyResolver
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiParameters
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiSource
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiState
import com.android.systemui.screencapture.data.repository.ScreenCaptureDeviceStateRepository
import com.android.systemui.screencapture.data.repository.ScreenCaptureUiRepository
import com.android.systemui.user.data.repository.UserRepository
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@SysUISingleton
class ScreenCaptureUiInteractor
@Inject
constructor(
    @Application private val context: Context,
    @Application private val applicationScope: CoroutineScope,
    deviceStateRepository: ScreenCaptureDeviceStateRepository,
    private val repository: ScreenCaptureUiRepository,
    private val userRepository: UserRepository,
    private val devicePolicyResolver: Lazy<ScreenCaptureDevicePolicyResolver>,
    private val uiEventLogger: UiEventLogger,
) {

    val isLargeScreen: StateFlow<Boolean> = deviceStateRepository.isLargeScreen

    fun uiState(type: ScreenCaptureType): StateFlow<ScreenCaptureUiState> = repository.uiState(type)

    fun isVisible(type: ScreenCaptureType): Boolean {
        return uiState(type).value is ScreenCaptureUiState.Visible
    }

    // TODO(b/475561417) Remove nullable parameter if/when all invocations define a source.
    fun show(parameters: ScreenCaptureUiParameters, source: ScreenCaptureUiSource? = null) {
        if (
            devicePolicyResolver
                .get()
                .isScreenCaptureCompletelyDisabled(
                    UserHandle.of(userRepository.getSelectedUserInfo().id)
                )
        ) {
            // Launch on the main thread
            applicationScope.launch {
                Toast.makeText(
                        context,
                        R.string.screen_capture_blocked_by_admin,
                        Toast.LENGTH_SHORT,
                    )
                    .show()
            }
            return
        }

        repository.updateStateForType(type = parameters.screenCaptureType) {
            if (it is ScreenCaptureUiState.Visible) {
                return@updateStateForType it
            } else {
                return@updateStateForType ScreenCaptureUiState.Visible(parameters)
            }
        }

        if (source != null) {
            uiEventLogger.log(source.event)
        }
    }

    fun hide(type: ScreenCaptureType) {
        repository.updateStateForType(type) {
            if (it is ScreenCaptureUiState.Invisible) {
                return@updateStateForType it
            } else {
                return@updateStateForType ScreenCaptureUiState.Invisible()
            }
        }
    }
}
