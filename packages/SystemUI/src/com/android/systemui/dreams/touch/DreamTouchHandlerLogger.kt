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

import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.LogLevel
import com.android.systemui.log.dagger.CommunalTouchLog
import javax.inject.Inject
import kotlin.math.roundToInt

class DreamTouchHandlerLogger @Inject constructor(@CommunalTouchLog private val buffer: LogBuffer) {
    fun logEdgeSwipeDown(
        x: Float,
        y: Float,
        isLeftEdge: Boolean,
        isRightEdge: Boolean,
        swipeThreshold: Float,
    ) {
        buffer.log(
            TAG,
            LogLevel.DEBUG,
            {
                int1 = x.roundToInt()
                int2 = y.roundToInt()
                long1 = swipeThreshold.roundToInt().toLong()
                bool1 = isLeftEdge
                bool2 = isRightEdge
            },
            {
                "onDown consumed: x=$int1, y=$int2, " +
                    "isLeftEdge=$bool1, isRightEdge=$bool2, " +
                    "swipeThreshold=$long1"
            },
        )
    }

    fun logEdgeSwipeIgnoredNonEdge(
        x: Float,
        y: Float,
        edgeWidthLeft: Float,
        edgeWidthRight: Float,
        width: Int,
    ) {
        buffer.log(
            TAG,
            LogLevel.DEBUG,
            {
                int1 = x.roundToInt()
                int2 = y.roundToInt()
                long1 = edgeWidthLeft.roundToInt().toLong()
                long2 = edgeWidthRight.roundToInt().toLong()
                double1 = width.toDouble()
            },
            {
                "onDown: ignored non-edge swipe (x=$int1, y=$int2, " +
                    "edgeWidthLeft=$long1, " +
                    "edgeWidthRight=$long2, " +
                    "containerView.width=${double1.toInt()})"
            },
        )
    }

    fun logEdgeSwipeRefused(x: Float, y: Float) {
        buffer.log(
            TAG,
            LogLevel.DEBUG,
            {
                int1 = x.roundToInt()
                int2 = y.roundToInt()
            },
            { "onDown: swipeDelegate refused to claim swipe (x=$int1, y=$int2)" },
        )
    }

    fun logEdgeSwipeEnded(action: Int, dx: Float, committed: Boolean) {
        buffer.log(
            TAG,
            LogLevel.DEBUG,
            {
                int1 = action
                long1 = dx.roundToInt().toLong()
                bool1 = committed
            },
            { "onUp/Cancel: action=$int1, dx=$long1, committed=$bool1" },
        )
    }

    fun logEdgeSwipeHapticPerformed() {
        buffer.log(TAG, LogLevel.DEBUG, "edge swipe haptic performed")
    }

    fun logLongPressDetected() {
        buffer.log(TAG, LogLevel.DEBUG, "long press detected")
    }

    fun logLongPressIgnored() {
        buffer.log(TAG, LogLevel.DEBUG, "long press ignored")
    }

    private companion object {
        const val TAG = "DreamTouchHandler"
    }
}
