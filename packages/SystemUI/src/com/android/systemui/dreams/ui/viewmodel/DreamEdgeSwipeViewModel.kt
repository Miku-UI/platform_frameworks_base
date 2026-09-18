/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.dreams.ui.viewmodel

import android.content.ComponentName
import android.util.Log
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import com.android.app.tracing.coroutines.coroutineScopeTraced
import com.android.systemui.common.ui.domain.interactor.ConfigurationInteractor
import com.android.systemui.dreams.domain.interactor.DreamInteractor
import com.android.systemui.dreams.shared.model.DreamPlaylistModel
import com.android.systemui.dreams.touch.DreamSwipeDelegate
import com.android.systemui.dreams.ui.model.DreamItemUiModel
import com.android.systemui.dreams.ui.model.EdgeSwipeUiModel
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.settings.UserTracker
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class DreamEdgeSwipeViewModel
@AssistedInject
constructor(
    private val interactor: DreamInteractor,
    private val configurationInteractor: ConfigurationInteractor,
    private val userTracker: UserTracker,
) : HydratedActivatable(enableEnqueuedActivations = true), DreamSwipeDelegate {

    private companion object {
        const val TAG = "DreamEdgeSwipeViewModel"
        val SWITCH_TIMEOUT = 500.milliseconds
        val CANCEL_ANIMATION_DELAY = 250.milliseconds
    }

    private val _uiState = MutableStateFlow(EdgeSwipeUiModel())
    val uiState: EdgeSwipeUiModel by _uiState.hydratedStateOf(initialValue = EdgeSwipeUiModel())

    var swipeProgress by mutableFloatStateOf(0f)
        private set

    private var isLtr: Boolean = true
    private var dreamsState: DreamPlaylistModel = DreamPlaylistModel.EMPTY

    /** Tracks the current active swipe session to prevent race conditions during resets */
    private var swipeGeneration = 0

    override suspend fun onActivated() {
        coroutineScopeTraced("$TAG#onActivated") {
            launch {
                configurationInteractor.layoutDirection.collect { direction ->
                    isLtr = direction == View.LAYOUT_DIRECTION_LTR
                }
            }
            launch { interactor.dreamState.collect { playlist -> dreamsState = playlist } }
        }
    }

    override fun onSwipeStarted(isFromLeft: Boolean, startY: Float): Boolean {
        // In LTR, left swipe goes to previous. In RTL, right swipe goes to previous.
        val goPrevious = isLtr == isFromLeft

        val targetDream =
            if (goPrevious) {
                dreamsState.previousDream
            } else {
                dreamsState.nextDream
            }

        if (targetDream == null) {
            return false
        }

        swipeGeneration++
        swipeProgress = 0f

        _uiState.value =
            EdgeSwipeUiModel(
                isVisible = true,
                isFromLeft = isFromLeft,
                isReleasing = false,
                isCommitted = false,
                startY = startY,
                targetDream =
                    DreamItemUiModel(
                        componentName = targetDream.componentName,
                        title = targetDream.title,
                        description = targetDream.description,
                    ),
            )

        return true
    }

    override fun onSwipeProgress(dx: Float, swipeThreshold: Float) {
        val distance = if (_uiState.value.isFromLeft) dx else -dx
        swipeProgress = (distance.coerceAtLeast(0f) / swipeThreshold).coerceIn(0f, 1.5f)
    }

    override fun onSwipeEnded(committed: Boolean, velocityX: Float) {
        // Clamp extreme velocities so the spring animation doesn't overshoot wildly
        val clampedVelocity = velocityX.coerceIn(-4000f, 4000f)

        _uiState.value =
            _uiState.value.copy(
                isReleasing = true,
                isCommitted = committed,
                releaseVelocityX = clampedVelocity,
            )

        val targetDream = _uiState.value.targetDream
        if (targetDream == null) {
            Log.w(TAG, "No target dream on swipe end")
            return
        }

        val currentGeneration = swipeGeneration

        enqueueOnActivatedScope {
            if (committed) {
                interactor.setActiveDream(targetDream.componentName, userTracker.userHandle)
                waitForDreamSwitch(targetDream.componentName)
            } else {
                // If cancelled, wait just long enough for the snap-back animation
                // to finish before hiding the UI.
                delay(CANCEL_ANIMATION_DELAY)
            }

            // Only clear state if a new swipe hasn't already started
            if (swipeGeneration == currentGeneration) {
                _uiState.value = EdgeSwipeUiModel()
                swipeProgress = 0f
            }
        }
    }

    /**
     * Suspend robustly until the dream state confirms the switch. Uses a timeout as a safety valve
     * to prevent deadlocking the enqueueOnActivatedScope channel if the system fails to switch.
     */
    private suspend fun waitForDreamSwitch(targetComponentName: ComponentName) {
        withTimeoutOrNull(SWITCH_TIMEOUT) {
            interactor.dreamState.first { playlist ->
                playlist.activeDream?.componentName == targetComponentName
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(): DreamEdgeSwipeViewModel
    }
}
