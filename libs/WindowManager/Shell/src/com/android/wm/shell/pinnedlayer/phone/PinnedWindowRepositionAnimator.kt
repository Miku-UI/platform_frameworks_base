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

package com.android.wm.shell.pinnedlayer.phone

import android.animation.RectEvaluator
import android.animation.ValueAnimator
import android.graphics.Rect
import android.view.Choreographer
import android.view.SurfaceControl
import androidx.core.animation.addListener

/** A shared animator that performs pinned windows reposition bounds animations. */
class PinnedWindowRepositionAnimator(
    private val transactionFactory: () -> SurfaceControl.Transaction
) {
    private var animator: ValueAnimator? = null
    private val rectEvaluator = RectEvaluator(Rect())

    /**
     * Creates a [ValueAnimator] that animates [leash] from [startBounds] to [endBounds].
     *
     * @param onStart a callback invoked when the animation starts. It provides a
     *   [SurfaceControl.Transaction] that can be used to set the initial state of the leash.
     * @param onEnd a callback invoked when the animation ends.
     */
    fun start(
        leash: SurfaceControl,
        startBounds: Rect,
        endBounds: Rect,
        startTransaction: SurfaceControl.Transaction? = null,
        finishTransaction: SurfaceControl.Transaction? = null,
        onStart: (SurfaceControl.Transaction) -> Unit = {},
        onEnd: () -> Unit = {},
    ) {
        if (startBounds == endBounds) return

        val tx = transactionFactory()
        animator?.cancel()
        animator =
            ValueAnimator.ofObject(rectEvaluator, startBounds, endBounds)
                .setDuration(RESIZE_DURATION_MS)
                .apply {
                    addListener(
                        onStart = {
                            val startTx = startTransaction ?: transactionFactory()
                            onStart(startTx)
                            startTx
                                .setPosition(
                                    leash,
                                    startBounds.left.toFloat(),
                                    startBounds.top.toFloat(),
                                )
                                .show(leash)
                                .apply()
                        },
                        onEnd = {
                            val endTx = finishTransaction ?: transactionFactory()
                            endTx
                                .setPosition(
                                    leash,
                                    endBounds.left.toFloat(),
                                    endBounds.top.toFloat(),
                                )
                                .show(leash)
                                .apply()
                            onEnd()
                        },
                    )
                    addUpdateListener { anim ->
                        val rect = anim.animatedValue as Rect
                        tx.setPosition(leash, rect.left.toFloat(), rect.top.toFloat())
                            .setFrameTimeline(Choreographer.getInstance().getVsyncId())
                            .apply()
                    }
                }
                .also(ValueAnimator::start)
    }

    private companion object {
        const val RESIZE_DURATION_MS = 300L
    }
}
