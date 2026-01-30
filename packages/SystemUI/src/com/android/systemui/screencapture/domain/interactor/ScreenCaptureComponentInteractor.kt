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

import com.android.systemui.coroutines.newTracingContext
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.screencapture.common.ScreenCaptureComponent
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiState
import com.android.systemui.screencapture.data.repository.ScreenCaptureComponentRepository
import com.android.systemui.screenrecord.domain.interactor.ScreenRecordingServiceInteractor
import com.android.systemui.screenrecord.domain.interactor.Status
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Manages the lifecycle of the [ScreenCaptureComponent]. */
@SysUISingleton
class ScreenCaptureComponentInteractor
@Inject
constructor(
    @Main private val dispatcherContext: CoroutineContext,
    private val repository: ScreenCaptureComponentRepository,
    private val componentBuilder: ScreenCaptureComponent.Builder,
    private val screenCaptureUiInteractor: ScreenCaptureUiInteractor,
    private val screenRecordingServiceInteractor: ScreenRecordingServiceInteractor,
) {

    suspend fun initialize() {
        coroutineScope {
            for (type in ScreenCaptureType.entries) {
                launch { initializeForType(type) }
            }
        }
    }

    private suspend fun initializeForType(type: ScreenCaptureType) {
        combine(
                screenCaptureUiInteractor.uiState(type).onEach {
                    if (it is ScreenCaptureUiState.Visible) {
                        repository.update(type) { component ->
                            component
                                ?: componentBuilder
                                    .setScope(
                                        CoroutineScope(
                                            dispatcherContext +
                                                newTracingContext("ScreenCaptureScope_$type")
                                        )
                                    )
                                    .setParameters(it.parameters)
                                    .build()
                        }
                    }
                },
                isCaptureInProgress(type),
            ) { uiState: ScreenCaptureUiState, isCapturing: Boolean ->
                CapturingContext(uiState, isCapturing)
            }
            .map { it.uiState is ScreenCaptureUiState.Visible || it.isCapturing }
            .distinctUntilChanged()
            .collect { shouldHoldComponent ->
                if (shouldHoldComponent) return@collect
                repository.update(type) { component: ScreenCaptureComponent? ->
                    component?.coroutineScope()?.cancel()
                    null
                }
            }
    }

    /**
     * Returns current [ScreenCaptureComponent]. Consumer should neither keep any reference of the
     * [ScreenCaptureComponent] nor manage its state. [ScreenCaptureComponentInteractor] deals with
     * this.
     */
    fun screenCaptureComponent(type: ScreenCaptureType): StateFlow<ScreenCaptureComponent?> =
        repository.screenCaptureComponent(type)

    private fun isCaptureInProgress(type: ScreenCaptureType): Flow<Boolean> {
        return when (type) {
            ScreenCaptureType.RECORD ->
                screenRecordingServiceInteractor.status.map { it is Status.Started }
            ScreenCaptureType.SHARE_SCREEN -> flowOf(false)
            ScreenCaptureType.CAST -> flowOf(false)
        }
    }

    private data class CapturingContext(
        val uiState: ScreenCaptureUiState,
        val isCapturing: Boolean,
    )
}
