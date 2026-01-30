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

/** A no-operation implementation of the [OverScrollerInterface] interface. */
class NoOpOverScroller : OverScrollerInterface {

    override fun abortAnimation() {
        // No-op
    }

    override fun computeScrollOffset(): Boolean {
        return false
    }

    override fun fling(
        startX: Int,
        startY: Int,
        velocityX: Int,
        velocityY: Int,
        minX: Int,
        maxX: Int,
        minY: Int,
        maxY: Int,
    ) {
        // No-op
    }

    override fun fling(
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
    ) {
        // No-op
    }

    override fun forceFinished(finished: Boolean) {
        // No-op
    }

    override fun getCurrVelocity(): Float {
        return 0.0f
    }

    override fun getCurrX(): Int {
        return 0
    }

    override fun getCurrY(): Int {
        return 0
    }

    override fun isFinished(): Boolean {
        return true
    }

    override fun springBack(
        startX: Int,
        startY: Int,
        minX: Int,
        maxX: Int,
        minY: Int,
        maxY: Int,
    ): Boolean {
        return false
    }

    override fun startScroll(startX: Int, startY: Int, dx: Int, dy: Int) {
        // No-op
    }
}
