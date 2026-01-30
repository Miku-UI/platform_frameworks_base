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

package com.android.systemui.communal.posturing.domain.model

import android.annotation.FloatRange
import com.android.systemui.communal.posturing.shared.model.ConfidenceLevel

data class AggregatedConfidenceState(
    /** The raw confidence levels in the window. */
    val rawWindow: List<ConfidenceLevel>
) {
    /**
     * The average confidence level over the entire sliding window. If the window contains any
     * [ConfidenceLevel.Negative] instances, this will be forced to 0.
     */
    @FloatRange(from = 0.0, to = 1.0) val avgConfidence: Double = calculateAverageConfidence()

    /**
     * The latest confidence level in the window. If the latest value is [ConfidenceLevel.Negative],
     * then this will be 0.
     */
    @FloatRange(from = 0.0, to = 1.0)
    val latestConfidence: Float =
        rawWindow.lastOrNull()?.let {
            if (it is ConfidenceLevel.Negative) {
                0f
            } else {
                it.confidence
            }
        } ?: 0f

    private fun calculateAverageConfidence(): Double {
        if (rawWindow.isEmpty()) return 0.0

        var positiveConfidenceSum = 0.0
        for (confidenceLevel in rawWindow) {
            when (confidenceLevel) {
                // Short-circuit: Negative found, return immediately
                is ConfidenceLevel.Negative -> return 0.0
                else -> positiveConfidenceSum += confidenceLevel.confidence
            }
        }

        return (positiveConfidenceSum / rawWindow.size).coerceIn(0.0, 1.0)
    }
}
