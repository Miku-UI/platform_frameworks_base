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
 * A fake implementation of [DreamSwipeDelegate] optimized for testing. It records all interactions
 * and allows configuring the claim behavior.
 */
class FakeDreamSwipeDelegate : DreamSwipeDelegate {

    /** Whether [DreamSwipeDelegate.onSwipeStarted] should return true. */
    var shouldClaimSwipe: Boolean = true

    val swipeStartedCalls = mutableListOf<SwipeStartedEvent>()
    val swipeProgressCalls = mutableListOf<ProgressEvent>()
    val swipeEndedCalls = mutableListOf<SwipeEndedEvent>()

    override fun onSwipeStarted(isFromLeft: Boolean, startY: Float): Boolean {
        swipeStartedCalls.add(SwipeStartedEvent(isFromLeft, startY))
        return shouldClaimSwipe
    }

    override fun onSwipeProgress(dx: Float, swipeThreshold: Float) {
        swipeProgressCalls.add(ProgressEvent(dx, swipeThreshold))
    }

    override fun onSwipeEnded(committed: Boolean, velocityX: Float) {
        swipeEndedCalls.add(SwipeEndedEvent(committed, velocityX))
    }

    /** Clears all recorded history. */
    fun reset() {
        swipeStartedCalls.clear()
        swipeProgressCalls.clear()
        swipeEndedCalls.clear()
    }

    data class ProgressEvent(val dx: Float, val swipeThreshold: Float)

    data class SwipeStartedEvent(val isFromLeft: Boolean, val startY: Float)

    data class SwipeEndedEvent(val committed: Boolean, val velocityX: Float)
}

val DreamSwipeDelegate.fake: FakeDreamSwipeDelegate
    get() = this as FakeDreamSwipeDelegate
