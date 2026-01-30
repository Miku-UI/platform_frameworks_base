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
package com.android.systemui.shade

import android.util.Log
import com.android.internal.jank.Cuj.CUJ_DESKTOP_MODE_SHADE_WINDOW_DISPLAY_CHANGE
import com.android.internal.jank.InteractionJankMonitor
import com.android.internal.util.LatencyTracker
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.scene.ui.view.WindowRootView
import com.android.systemui.shade.data.repository.ShadeDisplaysRepository
import com.android.systemui.shade.domain.interactor.ShadeDisplaysWaitInteractor
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Records the performance of moving the shade from one display to another - tracking both the
 * latency and jank involved in the process.
 * - Recording starts when [ShadeDisplaysRepository] propagates the new display ID.
 * - Latency tracking ends one frame after the shade configuration controller receives a new
 *   configuration change. (Note: even if the configuration of the new display is the same,
 *   onConfigurationChange is called anyway as it is triggered by
 *   [NotificationShadeWindowView.onMovedToDisplay].
 * - Jank tracking ends after the shade window is fully expanded.
 */
@SysUISingleton
class ShadeDisplayChangePerformanceTracker
@Inject
constructor(
    private val latencyTracker: LatencyTracker,
    private val shadeRootView: WindowRootView,
    @Background private val bgScope: CoroutineScope,
    private val jankMonitor: InteractionJankMonitor,
    private val waitInteractor: ShadeDisplaysWaitInteractor,
) {

    private var previousJob: Job? = null

    /**
     * Called before the display change begins.
     *
     * It is guaranteed that context and resources are still associated to the "old" display id, and
     * that onMovedToDisplay has not been received yet on the notification shade window root view.
     *
     * IMPORTANT: this shouldn't be refactored to use [ShadePositionRepository], otherwise there is
     * no guarantees of event order (as the shade could be reparented before the event is propagated
     * to this class, breaking the assumption that [onMovedToDisplayFlow] didn't emit with the new
     * display id yet.
     */
    @Synchronized
    fun onShadeDisplayChanging(displayId: Int) {
        previousJob?.cancel(CancellationException("New shade move in progress to $displayId"))
        previousJob = bgScope.launch { onShadeDisplayChangingAsync(displayId) }
    }

    private suspend fun onShadeDisplayChangingAsync(displayId: Int) {
        try {
            jankMonitor.begin(shadeRootView, CUJ_DESKTOP_MODE_SHADE_WINDOW_DISPLAY_CHANGE)
            latencyTracker.onActionStart(SHADE_MOVE_ACTION)
            waitForOnMovedToDisplayDispatchedToView(displayId)
            waitUntilNextDoFrameDone(displayId)
            latencyTracker.onActionEnd(SHADE_MOVE_ACTION)
            waitForShadeExpanded()
            jankMonitor.end(CUJ_DESKTOP_MODE_SHADE_WINDOW_DISPLAY_CHANGE)
        } catch (e: Exception) {
            val reason =
                when (e) {
                    is CancellationException ->
                        "Shade move to $displayId cancelled as a new move is being done " +
                            "before the previous one finished. Message: ${e.message}"

                    else -> "Shade move cancelled."
                }
            Log.e(TAG, reason, e)
            latencyTracker.onActionCancel(SHADE_MOVE_ACTION)
            jankMonitor.cancel(CUJ_DESKTOP_MODE_SHADE_WINDOW_DISPLAY_CHANGE)
        }
    }

    private suspend fun waitForOnMovedToDisplayDispatchedToView(newDisplayId: Int) {
        withTimeout(TIMEOUT) {
            waitInteractor.waitForOnMovedToDisplayDispatchedToView(newDisplayId, TAG)
        }
    }

    private suspend fun waitUntilNextDoFrameDone(newDisplayId: Int) {
        withTimeout(TIMEOUT) { waitInteractor.waitForNextDoFrameDone(newDisplayId, TAG) }
    }

    private suspend fun waitForShadeExpanded() {
        withTimeout(TIMEOUT) { waitInteractor.waitForShadeExpanded(TAG) }
    }

    private companion object {
        const val TAG = "ShadeDisplayLatency"
        val TIMEOUT = 3.seconds
        const val SHADE_MOVE_ACTION = LatencyTracker.ACTION_SHADE_WINDOW_DISPLAY_CHANGE
    }
}
