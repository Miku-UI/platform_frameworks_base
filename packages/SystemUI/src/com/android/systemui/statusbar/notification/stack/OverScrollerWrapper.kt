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

import android.widget.OverScroller

class OverScrollerWrapper(private val delegate: OverScroller) : OverScrollerInterface {

    companion object {
        @JvmStatic
        fun wrap(delegate: OverScroller): OverScrollerWrapper = OverScrollerWrapper(delegate)
    }

    override fun abortAnimation() {
        delegate.abortAnimation()
    }

    override fun computeScrollOffset(): Boolean {
        return delegate.computeScrollOffset()
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
        delegate.fling(startX, startY, velocityX, velocityY, minX, maxX, minY, maxY)
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
        delegate.fling(startX, startY, velocityX, velocityY, minX, maxX, minY, maxY, overX, overY)
    }

    override fun forceFinished(finished: Boolean) {
        delegate.forceFinished(finished)
    }

    override fun getCurrVelocity(): Float {
        return delegate.currVelocity
    }

    override fun getCurrX(): Int {
        return delegate.currX
    }

    override fun getCurrY(): Int {
        return delegate.currY
    }

    override fun isFinished(): Boolean {
        return delegate.isFinished
    }

    override fun springBack(
        startX: Int,
        startY: Int,
        minX: Int,
        maxX: Int,
        minY: Int,
        maxY: Int,
    ): Boolean {
        return delegate.springBack(startX, startY, minX, maxX, minY, maxY)
    }

    override fun startScroll(startX: Int, startY: Int, dx: Int, dy: Int) {
        delegate.startScroll(startX, startY, dx, dy)
    }
}
