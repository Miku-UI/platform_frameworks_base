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

package com.android.systemui.communal.posturing.shared.model

import androidx.annotation.FloatRange

sealed interface ConfidenceLevel {
    /** The confidence level of this state, ranging from 0.0 to 1.0 (inclusive). */
    @get:FloatRange(from = 0.0, to = 1.0) val confidence: Float

    /**
     * Represents an unknown state where confidence cannot be determined. Confidence is always 0.
     */
    data object Unknown : ConfidenceLevel {
        override val confidence: Float = 0f
    }

    /**
     * Represents a positive state (e.g., Stationary, Postured).
     *
     * @param confidence The confidence level (0.0 to 1.0) that the state is positive.
     */
    @JvmInline
    value class Positive(@FloatRange(from = 0.0, to = 1.0) override val confidence: Float) :
        ConfidenceLevel

    /**
     * Represents a negative state (e.g., Not Stationary, Not Postured).
     *
     * @param confidence The confidence level (0.0 to 1.0) that the state is negative.
     */
    @JvmInline
    value class Negative(@FloatRange(from = 0.0, to = 1.0) override val confidence: Float) :
        ConfidenceLevel
}
