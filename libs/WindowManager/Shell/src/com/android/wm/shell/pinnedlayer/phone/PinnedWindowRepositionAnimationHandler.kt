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

import android.app.TaskInfo
import android.graphics.Rect
import android.os.IBinder
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CHANGE
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.windowdecor.OnTaskRepositionAnimationListener

/**
 * A pinned layer handler that animates pinned windows reposition bounds changes.
 *
 * This handler is not registered in the handler chain and is supposed to be passed as a parameter
 * of [Transitions.startTransition].
 *
 * The contract of the handler is to mandatory set [onTaskRepositionAnimationListener] to non-null
 * value to dispatch updates to caption bar.
 */
class PinnedWindowRepositionAnimationHandler(
    private val transitions: Transitions,
    private val windowRepositionAnimator: PinnedWindowRepositionAnimator,
) : Transitions.TransitionHandler {

    private var startBounds: Rect? = null
    private var onAnimationStartCallback: (SurfaceControl.Transaction) -> Unit = {}
    private var onAnimationCanceled: (SurfaceControl.Transaction) -> Unit = {}

    private var onTaskRepositionAnimationListener: OnTaskRepositionAnimationListener? = null

    /** Sets a [OnTaskRepositionAnimationListener] to listen to all task animation. */
    fun setOnTaskRepositionAnimationListener(
        onTaskRepositionAnimationListener: OnTaskRepositionAnimationListener?
    ) {
        this.onTaskRepositionAnimationListener = onTaskRepositionAnimationListener
    }

    /**
     * Starts a bounds change transition providing
     * [com.android.wm.shell.pinnedlayer.phone.PinnedWindowRepositionAnimationHandler] as the main
     * handler.
     *
     * @param taskInfo a [TaskInfo] of the task whose bounds has changed.
     * @param startBounds a [Rect] containing the leash start position. This do not match
     *   [TransitionInfo.Change.getStartAbsBounds] because contains actual task bounds while
     *   [startBounds] contain mirrored leash start bounds.
     * @param endBounds a [Rect] with the final bounds where the task should be positioned.
     * @param onAnimationStart a callback that's invoked on the animation start, it provides an
     *   access to the startTransaction. Do not [SurfaceControl.Transaction.apply] since it's called
     *   by the handler itself.
     * @param onAnimationCanceled a callback that's invoked when animation is canceled when
     *   transition is consumed. The callback accepts finishTransition, so do not call
     *   [SurfaceControl.Transaction.apply].
     */
    fun startTransition(
        taskInfo: TaskInfo,
        startBounds: Rect? = null,
        endBounds: Rect,
        onAnimationStart: (SurfaceControl.Transaction) -> Unit = {},
        onAnimationCanceled: (SurfaceControl.Transaction) -> Unit = {},
    ) {
        // TODO: Consider waiting for the transition to end before starting a new one.
        this.startBounds = startBounds
        onAnimationStartCallback = onAnimationStart
        this@PinnedWindowRepositionAnimationHandler.onAnimationCanceled = onAnimationCanceled

        val wct = WindowContainerTransaction()
        wct.setBounds(taskInfo.token, endBounds)
        transitions.startTransition(TRANSIT_CHANGE, wct, this)
    }

    // This handler doesn't handle requests, it's passed manually as a handler during transition
    // start.
    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo,
    ): WindowContainerTransaction? = null

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
    ): Boolean {
        val change = info.changes.find { it.mode == TRANSIT_CHANGE } ?: return false
        val leash = change.leash
        val taskId = requireNotNull(change.taskInfo).taskId
        val startBounds = startBounds ?: change.startAbsBounds
        val endBounds = change.endAbsBounds

        finishTransaction.setPosition(leash, endBounds.left.toFloat(), endBounds.top.toFloat())

        windowRepositionAnimator.start(
            leash,
            startBounds,
            endBounds,
            startTransaction,
            onStart = { tx ->
                onAnimationStartCallback(tx)
                onTaskRepositionAnimationListener?.onAnimationStart(taskId)
            },
            onEnd = {
                onTaskRepositionAnimationListener?.onAnimationEnd(taskId)
                this@PinnedWindowRepositionAnimationHandler.startBounds = null
                finishCallback.onTransitionFinished(null)
            },
        )
        return true
    }

    override fun onTransitionConsumed(
        transition: IBinder,
        aborted: Boolean,
        finishTransaction: SurfaceControl.Transaction?,
    ) {
        finishTransaction?.run { onAnimationCanceled(this) }
    }
}
