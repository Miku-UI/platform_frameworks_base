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

import android.util.Log
import android.view.Choreographer
import androidx.annotation.VisibleForTesting
import com.android.systemui.coroutines.newTracingContext
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.screencapture.common.ScreenCaptureComponent
import com.android.systemui.screencapture.common.ScreenCaptureReleasable
import com.android.systemui.screencapture.common.ScreenCaptureStartable
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiState
import com.android.systemui.screencapture.data.repository.ScreenCaptureComponentRepository
import com.android.systemui.screenrecord.domain.interactor.ScreenRecordingServiceInteractor
import com.android.systemui.screenrecord.shared.model.ScreenRecordingStatus
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

private val startablesTimeout = 1.seconds
private val releasablesTimeout = 1.seconds
private const val TAG = "ScreenCaptureComponentInteractor"

/** Manages the lifecycle of the [ScreenCaptureComponent]. */
@SysUISingleton
class ScreenCaptureComponentInteractor
@VisibleForTesting
constructor(
    private val dispatcherContext: CoroutineContext,
    private val repository: ScreenCaptureComponentRepository,
    private val componentBuilder: ScreenCaptureComponent.Builder,
    private val screenCaptureUiInteractor: ScreenCaptureUiInteractor,
    private val screenRecordingServiceInteractor: ScreenRecordingServiceInteractor,
    private val getFrameDelay: () -> Long,
) {

    @Inject
    constructor(
        @Main dispatcherContext: CoroutineContext,
        repository: ScreenCaptureComponentRepository,
        componentBuilder: ScreenCaptureComponent.Builder,
        screenCaptureUiInteractor: ScreenCaptureUiInteractor,
        screenRecordingServiceInteractor: ScreenRecordingServiceInteractor,
    ) : this(
        dispatcherContext = dispatcherContext,
        repository = repository,
        componentBuilder = componentBuilder,
        screenCaptureUiInteractor = screenCaptureUiInteractor,
        screenRecordingServiceInteractor = screenRecordingServiceInteractor,
        getFrameDelay = { Choreographer.getFrameDelay() },
    )

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
                                ?: run {
                                    componentBuilder
                                        .setScope(
                                            CoroutineScope(
                                                dispatcherContext +
                                                    newTracingContext("ScreenCaptureScope_$type")
                                            )
                                        )
                                        .setParameters(it.parameters)
                                        .build()
                                        .apply { screenCaptureStartableSet().startAll() }
                                }
                        }
                    }
                },
                isCaptureInProgress(type),
            ) { uiState: ScreenCaptureUiState, isCapturing: Boolean ->
                CapturingContext(uiState, isCapturing)
            }
            .map { it.uiState is ScreenCaptureUiState.Visible || it.isCapturing }
            .distinctUntilChanged()
            .debounce(getFrameDelay())
            .collect { shouldHoldComponent ->
                if (shouldHoldComponent) return@collect
                repository.update(type) { component: ScreenCaptureComponent? ->
                    component?.run {
                        screenCaptureReleasableSet().releaseAll()
                        coroutineScope().cancel()
                    }
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
                screenRecordingServiceInteractor.status.map {
                    it is ScreenRecordingStatus.Started || it is ScreenRecordingStatus.Starting
                }
            ScreenCaptureType.SHARE_SCREEN -> flowOf(false)
            ScreenCaptureType.CAST -> flowOf(false)
        }
    }

    private data class CapturingContext(
        val uiState: ScreenCaptureUiState,
        val isCapturing: Boolean,
    )
}

private suspend fun Set<ScreenCaptureStartable>.startAll(): Unit =
    withLogOnTimeout(timeout = startablesTimeout, message = { "Startables take too long!" }) {
        map { launch { it.start() } }.joinAll()
    }

private suspend fun Set<ScreenCaptureReleasable>.releaseAll(): Unit =
    withLogOnTimeout(timeout = releasablesTimeout, message = { "Releasables take too long!" }) {
        map { launch { it.release() } }.joinAll()
    }

private suspend fun withLogOnTimeout(
    timeout: Duration,
    message: () -> String,
    block: suspend CoroutineScope.() -> Unit,
): Unit = coroutineScope {
    val loggingJob = launch {
        delay(timeout)
        Log.wtf(TAG, message())
    }
    try {
        block()
    } finally {
        loggingJob.cancel()
    }
}
