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
package com.android.wm.shell.pinnedlayer.phone

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Rect
import android.view.SurfaceControl
import android.view.animation.Interpolator
import android.window.TransitionInfo
import com.android.wm.shell.shared.animation.Interpolators
import com.android.wm.shell.transition.Transitions
import kotlin.math.roundToInt

/** Animator for pinned layer transitions. */
class PinnedLayerAnimator {

    companion object {
        private const val PIN_ANIMATION_DURATION_MS = 300L
        private val PIN_ANIMATION_INTERPOLATOR: Interpolator = Interpolators.FAST_OUT_SLOW_IN

        /**
         * Creates an [Animator] that animates pinning operation according to the changed bounds.
         *
         * @param change a [TransitionInfo.Change] that caused pinning.
         * @param startTransaction see [Transitions.TransitionHandler.startAnimation]
         * @param finishTransaction see [Transitions.TransitionHandler.startAnimation]
         * @param sctFactory a [SurfaceControl.Transaction] factory used create a transaction for
         *   animation
         * @param onAnimationStart an animation listener that is called when the animation starts.
         *   This listener returns true if the transaction was applied.
         * @param onAnimationUpdate an animation listener that is called on animation updates. This
         *   listener returns true if the transaction was applied.
         * @param onAnimationEnd an animation listener that is called when the animation ends.
         */
        fun createPinAnimator(
            change: TransitionInfo.Change,
            startTransaction: SurfaceControl.Transaction,
            finishTransaction: SurfaceControl.Transaction,
            finishCallback: Transitions.TransitionFinishCallback,
            sctFactory: () -> SurfaceControl.Transaction = {
                SurfaceControl.Transaction()
            }, // for testing
            onAnimationStart: (Int, SurfaceControl.Transaction, Rect) -> Boolean = { _, t, _ ->
                t.apply()
                true
            },
            onAnimationUpdate: (Int, SurfaceControl.Transaction, Rect) -> Boolean = { _, t, _ ->
                t.apply()
                true
            },
            onAnimationEnd: (Int) -> Unit = {},
        ): Animator {
            val task =
                requireNotNull(change.taskInfo) {
                    "The change=$change doesn't contain a task to animate."
                }
            val animator =
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = PIN_ANIMATION_DURATION_MS
                    interpolator = PIN_ANIMATION_INTERPOLATOR
                }

            val startBounds = Rect(change.startAbsBounds)
            val endBounds = Rect(change.endAbsBounds)

            fun addTxWithBounds(
                tx: SurfaceControl.Transaction,
                left: Float,
                top: Float,
                width: Int,
                height: Int,
            ) {
                tx.setPosition(change.leash, left, top)
                tx.setWindowCrop(change.leash, width, height)
            }

            fun addTxWithBounds(tx: SurfaceControl.Transaction, bounds: Rect) {
                addTxWithBounds(
                    tx,
                    bounds.left.toFloat(),
                    bounds.top.toFloat(),
                    bounds.width(),
                    bounds.height(),
                )
            }

            animator.addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator) {
                        super.onAnimationStart(animation)
                        addTxWithBounds(startTransaction, startBounds)
                        val applied = onAnimationStart(task.taskId, startTransaction, startBounds)
                        if (!applied) {
                            startTransaction.apply()
                        }
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        addTxWithBounds(finishTransaction, endBounds)
                        onAnimationEnd(task.taskId)
                        finishCallback.onTransitionFinished(null)
                    }
                }
            )

            // TODO(b/399376001): Change to a RectEvaluator.
            val updateRect = Rect()
            animator.addUpdateListener { animation ->
                val transaction = sctFactory.invoke()
                val progress = animation.animatedFraction
                val s = startBounds
                val e = endBounds
                val left = s.left + (e.left - s.left) * progress
                val top = s.top + (e.top - s.top) * progress
                val right = s.right + (e.right - s.right) * progress
                val bottom = s.bottom + (e.bottom - s.bottom) * progress
                updateRect.set(
                    left.roundToInt(),
                    top.roundToInt(),
                    right.roundToInt(),
                    bottom.roundToInt(),
                )
                addTxWithBounds(transaction, updateRect)
                val applied = onAnimationUpdate(task.taskId, transaction, updateRect)
                if (!applied) {
                    transaction.apply()
                }
            }

            return animator
        }
    }
}
