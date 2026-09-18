/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.contextualcursor.domain.interactor

import android.util.Log
import com.android.hardware.input.Flags.enableContextualCursorDesktopEntrypoints
import com.android.systemui.cursorposition.data.model.CursorPosition
import com.android.systemui.cursorposition.domain.interactor.MultiDisplayCursorPositionInteractor
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.display.domain.interactor.DisplayWindowPropertiesInteractor
import com.android.systemui.util.time.SystemClock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.math.sign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.launch

class GestureDetectionInteractor
@Inject
constructor(
    private val cursorInteractor: MultiDisplayCursorPositionInteractor,
    private val displayWindowPropertiesInteractor: DisplayWindowPropertiesInteractor,
    private val systemClock: SystemClock,
    @param:Background private val backgroundScope: CoroutineScope,
) {
    interface OnShakeGestureListener {
        /** Notifies that a shake gesture has been detected. */
        fun onShakeGestureDetected()
    }

    private val listeners = ConcurrentHashMap.newKeySet<OnShakeGestureListener>()
    private var detectionJob: Job? = null

    fun addShakeGestureListener(listener: OnShakeGestureListener) {
        val wasEmpty = listeners.isEmpty()
        listeners.add(listener)
        if (wasEmpty) {
            startDetection()
        }
    }

    fun removeShakeGestureListener(listener: OnShakeGestureListener) {
        listeners.remove(listener)
        if (listeners.isEmpty()) stopDetection()
    }

    private fun startDetection() {
        if (!enableContextualCursorDesktopEntrypoints()) {
            return
        }

        if (detectionJob != null) {
            Log.w(TAG, "startDetection is called when the detection is running.")
            return
        }

        detectionJob =
            backgroundScope.launch {
                cursorInteractor.cursorPositions
                    .filterNotNull()
                    .map { pos: CursorPosition ->
                        TimedPosition(pos, systemClock.currentTimeMillis())
                    }
                    .scan(emptyList<TimedPosition>()) { recentPositions, newTimedPosition ->
                        val currentTime = newTimedPosition.timestamp
                        (recentPositions.filter {
                            currentTime - it.timestamp <= MAX_SHAKE_DURATION_MS
                        } + newTimedPosition)
                    }
                    .map(::isCursorShaking)
                    .distinctUntilChanged()
                    .scan(CooldownState(0L, false)) { state, startToShake ->
                        val currentTime = systemClock.currentTimeMillis()
                        if (startToShake && currentTime - state.lastShakeTime >= COOLDOWN_MS) {
                            CooldownState(currentTime, true)
                        } else {
                            CooldownState(state.lastShakeTime, false)
                        }
                    }
                    .filter { it.isShaking }
                    .collect { notifyListeners() }
            }
    }

    private fun stopDetection() {
        detectionJob?.cancel()
        detectionJob = null
    }

    private fun notifyListeners() {
        listeners.forEach { it.onShakeGestureDetected() }
    }

    // TODO: b/484184229 - This is an early version of mouse shaking detection algorithm and is
    //  expected to evolve.
    private fun isCursorShaking(positions: List<TimedPosition>): Boolean {
        if (positions.isEmpty()) return false

        val minX = positions.minOf { it.pos.x }
        val maxX = positions.maxOf { it.pos.x }
        val minY = positions.minOf { it.pos.y }
        val maxY = positions.maxOf { it.pos.y }

        val windowProperties =
            displayWindowPropertiesInteractor.getForBaseApplication(positions.last().pos.displayId)
                ?: return false
        val windowDensity = windowProperties.windowManager.currentWindowMetrics.density

        val rangeThresholdPx = SHAKE_RANGE_THRESHOLD_DP * windowDensity
        val inRange = (maxX - minX) < rangeThresholdPx && (maxY - minY) < rangeThresholdPx
        if (!inRange) return false

        val directionChanges =
            positions
                .zipWithNext { current, next -> next.pos.x - current.pos.x }
                .filter { deltaX -> deltaX != 0f }
                .zipWithNext()
                .count { (currentDelta, nextDelta) -> sign(currentDelta) != sign(nextDelta) }

        return directionChanges >= SHAKE_COUNT_THRESHOLD
    }

    private data class TimedPosition(val pos: CursorPosition, val timestamp: Long)

    private data class CooldownState(val lastShakeTime: Long, val isShaking: Boolean)

    companion object {
        private const val MAX_SHAKE_DURATION_MS = 500L

        private const val SHAKE_RANGE_THRESHOLD_DP = 100f
        private const val SHAKE_COUNT_THRESHOLD = 3

        private const val COOLDOWN_MS = 3000L

        private const val TAG = "GestureDetectInteractor"
    }
}
