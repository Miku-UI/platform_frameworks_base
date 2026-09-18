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

package com.android.wm.shell.bubbles.gesture

import android.content.Context
import android.view.ViewConfiguration
import androidx.annotation.VisibleForTesting
import com.android.wm.shell.bubbles.BubbleData
import com.android.wm.shell.bubbles.BubblePositioner
import com.android.wm.shell.bubbles.BubblesNavBarGestureTracker
import com.android.wm.shell.bubbles.BubblesNavBarMotionEventHandler
import com.android.wm.shell.bubbles.animation.OverScroll
import com.android.wm.shell.shared.bubbles.logging.BubbleLog
import kotlin.math.abs
import kotlin.math.min

/**
 * Controller that managers the interaction with bubbles when a user swipes on the gesture nav area.
 */
class BubbleBarGestureNavSwipeController(
    context: Context,
    private val bubbleData: BubbleData,
    private val positioner: BubblePositioner,
) {

    private val bubblesNavBarGestureTracker: BubblesNavBarGestureTracker =
        BubblesNavBarGestureTracker(context, positioner)
    private val minFlingVelocity: Int = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private var swipedAmount = 0

    @get:VisibleForTesting
    val swipeListener =
        object : BubblesNavBarMotionEventHandler.MotionEventListener {
            override fun onDown(x: Float, y: Float) {}

            override fun onMove(dx: Float, dy: Float) {
                if (!bubbleData.isExpanded) {
                    BubbleLog.e(
                        "BubbleBarGestureNavSwipeListener - gesture monitor (%s) is registered " +
                            "while we're collapsed",
                        this,
                    )
                    stopMonitoring()
                    return
                }
                // Only allow up, normalize for up direction
                val distance = -min(dy, 0f)
                // TODO(b/1525853): Animate while swipe is occurring
                updateSwipeAmount(distance.toInt())
            }

            override fun onUp(velX: Float, velY: Float) {
                if (shouldCollapse(velY)) {
                    // Update data first and start the animation when we are processing change
                    bubbleData.isExpanded = false
                }
                // Reset swipe distance when gesture is done.
                updateSwipeAmount(0)
            }

            override fun onCancel() {}
        }

    private fun shouldCollapse(velocity: Float): Boolean {
        val swipeVelocity = if (velocity < 0) abs(velocity) else 0f

        if (swipeVelocity > minFlingVelocity) {
            // Swiping up and over fling velocity, collapse the view.
            BubbleLog.d(
                "BubbleBarGestureNavSwipeListener.shouldCollapse() collapse, swipe up " +
                    "velocity=%f minV=%d",
                swipeVelocity,
                minFlingVelocity,
            )
            return true
        }

        if (isPastCollapseThreshold()) {
            BubbleLog.d(
                "BubbleBarGestureNavSwipeListener.shouldCollapse() collapse " +
                    " past threshold, swiped=%d",
                swipedAmount,
            )
            return true
        }

        BubbleLog.d("BubbleBarGestureNavSwipeListener.shouldCollapse() not collapsing")

        return false
    }

    private fun updateSwipeAmount(distance: Int) {
        if (bubbleData.isExpanded) {
            swipedAmount =
                OverScroll.dampedScroll(
                    distance.toFloat(),
                    positioner.getExpandedViewHeightForBubbleBar(false),
                )
        }
    }

    private fun isPastCollapseThreshold(): Boolean {
        if (bubbleData.isExpanded) {
            return swipedAmount >
                positioner.getExpandedViewHeightForBubbleBar(false) * COLLAPSE_THRESHOLD
        }
        return false
    }

    fun startMonitoring() {
        bubblesNavBarGestureTracker.start(swipeListener)
    }

    fun stopMonitoring() {
        bubblesNavBarGestureTracker.stop()
    }

    private companion object {
        private const val COLLAPSE_THRESHOLD: Float = 0.02f
    }
}
