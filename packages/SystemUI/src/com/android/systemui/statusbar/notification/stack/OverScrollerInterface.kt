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

package com.android.systemui.statusbar.notification.stack

/**
 * Interface defining the contract for an OverScroller. This handles scrolling and flinging with
 * overscroll support.
 */
interface OverScrollerInterface {
    /**
     * Stops the animation. Contrary to {@link #forceFinished(boolean)}, aborting the animation
     * causes the scroller to move to the final x and y positions.
     */
    fun abortAnimation()

    /**
     * Call this when you want to know the new location. If it returns true, the animation is not
     * yet finished.
     *
     * @return True if the animation is not yet finished.
     */
    fun computeScrollOffset(): Boolean

    /**
     * Start scrolling based on a fling gesture. The distance travelled will depend on the initial
     * velocity of the fling.
     *
     * @param startX Starting point of the scroll (X)
     * @param startY Starting point of the scroll (Y)
     * @param velocityX Initial velocity of the fling (X) measured in pixels per second.
     * @param velocityY Initial velocity of the fling (Y) measured in pixels per second
     * @param minX Minimum X value. The scroller will not scroll beyond this point.
     * @param maxX Maximum X value. The scroller will not scroll beyond this point.
     * @param minY Minimum Y value. The scroller will not scroll beyond this point.
     * @param maxY Maximum Y value. The scroller will not scroll beyond this point.
     */
    fun fling(
        startX: Int,
        startY: Int,
        velocityX: Int,
        velocityY: Int,
        minX: Int,
        maxX: Int,
        minY: Int,
        maxY: Int,
    )

    /**
     * Start scrolling based on a fling gesture. The distance travelled will depend on the initial
     * velocity of the fling.
     *
     * @param startX Starting point of the scroll (X)
     * @param startY Starting point of the scroll (Y)
     * @param velocityX Initial velocity of the fling (X) measured in pixels per second.
     * @param velocityY Initial velocity of the fling (Y) measured in pixels per second
     * @param minX Minimum X value. The scroller will not scroll beyond this point.
     * @param maxX Maximum X value. The scroller will not scroll beyond this point.
     * @param minY Minimum Y value. The scroller will not scroll beyond this point.
     * @param maxY Maximum Y value. The scroller will not scroll beyond this point.
     * @param overX Overfling range. If > 0, horizontal overfling in either direction will be
     *   possible.
     * @param overY Overfling range. If > 0, vertical overfling in either direction will be
     *   possible.
     */
    fun fling(
        startX: Int,
        startY: Int,
        velocityX: Int,
        velocityY: Int,
        minX: Int,
        maxX: Int,
        minY: Int,
        maxY: Int,
        overX: Int,
        overY: Int,
    )

    /**
     * Force the finished field to a particular value.
     *
     * @param finished The new finished value.
     */
    fun forceFinished(finished: Boolean)

    /**
     * Returns the current velocity.
     *
     * @return The original velocity less the deceleration. Result may be negative.
     */
    fun getCurrVelocity(): Float

    /**
     * Returns the current X offset in the scroll.
     *
     * @return The new X offset as an absolute distance from the origin.
     */
    fun getCurrX(): Int

    /**
     * Returns the current Y offset in the scroll.
     *
     * @return The new Y offset as an absolute distance from the origin.
     */
    fun getCurrY(): Int

    // getSplineFlingDistance was package-private, typically not in public interface
    // fun getSplineFlingDistance(velocity: Int): Double

    /**
     * Returns whether the scroller has finished scrolling.
     *
     * @return True if the scroller has finished scrolling, false otherwise.
     */
    fun isFinished(): Boolean

    // setInterpolator was package-private
    // fun setInterpolator(interpolator: Interpolator?)

    /**
     * Call this when you want to 'spring back' into a valid range.
     *
     * @param startX Starting X coordinate
     * @param startY Starting Y coordinate
     * @param minX Minimum X value
     * @param maxX Maximum X value
     * @param minY Minimum Y value
     * @param maxY Maximum Y value
     * @return true if a springback animation is started, false if already in range.
     */
    fun springBack(startX: Int, startY: Int, minX: Int, maxX: Int, minY: Int, maxY: Int): Boolean

    /**
     * Start scrolling by providing a starting point and the distance to travel. The scroll will use
     * the default value of 250 milliseconds for the duration.
     *
     * @param startX Starting horizontal scroll offset in pixels. Positive numbers will scroll the
     *   content to the left.
     * @param startY Starting vertical scroll offset in pixels. Positive numbers will scroll the
     *   content up.
     * @param dx Horizontal distance to travel. Positive numbers will scroll the content to the
     *   left.
     * @param dy Vertical distance to travel. Positive numbers will scroll the content up.
     */
    fun startScroll(startX: Int, startY: Int, dx: Int, dy: Int)
}
