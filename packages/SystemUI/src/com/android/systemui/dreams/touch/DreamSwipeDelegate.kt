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

package com.android.systemui.dreams.touch

/**
 * Delegate for handling edge swipe gesture events. Decouples the raw touch detection from the
 * underlying UI state management.
 */
interface DreamSwipeDelegate {
    /**
     * Called when an edge swipe begins.
     *
     * @param isFromLeft Whether the swipe originated from the left edge.
     * @param startY The vertical position (in pixels) where the touch started.
     * @return true if the gesture is allowed and claimed, false otherwise.
     */
    fun onSwipeStarted(isFromLeft: Boolean, startY: Float): Boolean

    /** Called during the gesture to report the raw X translation and threshold. */
    fun onSwipeProgress(dx: Float, swipeThreshold: Float)

    /**
     * Called when the touch stream ends.
     *
     * @param committed Whether the swipe threshold was met.
     * @param velocityX The horizontal velocity of the swipe in pixels per second.
     */
    fun onSwipeEnded(committed: Boolean, velocityX: Float)
}
