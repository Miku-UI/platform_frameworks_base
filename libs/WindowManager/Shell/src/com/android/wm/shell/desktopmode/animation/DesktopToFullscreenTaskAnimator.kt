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
package com.android.wm.shell.desktopmode.animation

import android.animation.RectEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.view.Choreographer
import android.view.SurfaceControl
import android.window.TransitionInfo
import androidx.core.animation.addListener
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.windowdecor.OnTaskResizeAnimationListener

/**
 * A animator util to animate a task moving from desktop that interpolates between the start and end
 * bounds.
 */
class DesktopToFullscreenTaskAnimator(
    private val context: Context,
    private val transactionSupplier: () -> SurfaceControl.Transaction,
    private val displayController: DisplayController,
    private val onTaskResizeAnimationListener: OnTaskResizeAnimationListener? = null,
) {
    private val rectEvaluator = RectEvaluator()

    /** Starts the animation of [change]. */
    fun animate(
        change: TransitionInfo.Change,
        startTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
        onAnimationEnd: (() -> Unit)? = null,
        overrideStartPosition: Point? = null,
    ) {
        val task = change.taskInfo ?: error("Expected non-null task info")
        val context = displayController.getDisplayContext(task.displayId) ?: this.context
        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels
        val leash = change.leash
        val startAbsBounds = change.startAbsBounds
        val endBounds = change.endAbsBounds
        val startPosition = overrideStartPosition ?: Point(startAbsBounds.left, startAbsBounds.top)
        val startBounds =
            Rect(change.startAbsBounds).apply { offsetTo(startPosition.x, startPosition.y) }
        val scaleX = startBounds.width().toFloat() / screenWidth
        val scaleY = startBounds.height().toFloat() / screenHeight

        startTransaction
            .setPosition(leash, startBounds.left.toFloat(), startBounds.top.toFloat())
            .setWindowCrop(leash, startBounds.width(), startBounds.height())
        val startTransactionApplied =
            onTaskResizeAnimationListener?.onAnimationStart(
                taskId = task.taskId,
                t = startTransaction,
                bounds = startBounds,
            ) ?: false
        if (!startTransactionApplied) {
            startTransaction.apply()
        }

        val updateTransaction = transactionSupplier()
        ValueAnimator.ofObject(rectEvaluator, startBounds, endBounds).apply {
            duration = ANIMATION_DURATION_MS
            addUpdateListener { animation ->
                val rect = animation.animatedValue as Rect
                updateTransaction
                    .setPosition(leash, rect.left.toFloat(), rect.top.toFloat())
                    .setWindowCrop(leash, rect.width(), rect.height())
                    .show(leash)
                    .setFrameTimeline(Choreographer.getInstance().getVsyncId())
                val updateTransactionApplied =
                    onTaskResizeAnimationListener?.onBoundsChange(
                        taskId = task.taskId,
                        t = updateTransaction,
                        bounds = rect,
                    ) ?: false
                if (!updateTransactionApplied) {
                    updateTransaction.apply()
                }
            }
            addListener(
                onEnd = {
                    onTaskResizeAnimationListener?.onAnimationEnd(task.taskId)
                    onAnimationEnd?.invoke()
                    finishCallback.onTransitionFinished(/* wct= */ null)
                }
            )
            start()
        }
    }

    companion object {
        private const val ANIMATION_DURATION_MS = 336L
    }
}
