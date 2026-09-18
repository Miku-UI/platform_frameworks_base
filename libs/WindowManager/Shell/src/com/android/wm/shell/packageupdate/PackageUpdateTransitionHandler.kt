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

package com.android.wm.shell.packageupdate

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.os.Handler
import android.os.IBinder
import android.view.Choreographer
import android.view.SurfaceControl
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import androidx.core.animation.addListener
import com.android.internal.jank.Cuj.CUJ_APP_UPDATE
import com.android.internal.jank.InteractionJankMonitor
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.common.suppliers.TransactionSupplier
import com.android.wm.shell.shared.R
import com.android.wm.shell.shared.TransitionUtil.isClosingType
import com.android.wm.shell.shared.TransitionUtil.isOpeningType
import com.android.wm.shell.shared.animation.Interpolators
import com.android.wm.shell.transition.Transitions

/** [Transitions.TransitionHandler] responsible for animating package update transitions. */
class PackageUpdateTransitionHandler(
    private val transactionProvider: TransactionSupplier,
    private val context: Context,
    private val animExecutor: ShellExecutor,
    private val mainExecutor: ShellExecutor,
    private val shellMainHandler: Handler,
    private val interactionJankMonitor: InteractionJankMonitor,
) : Transitions.TransitionHandler {

    private val cornerRadius =
        context.resources
            .getDimensionPixelSize(R.dimen.desktop_windowing_freeform_rounded_corner_radius)
            .toFloat()

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
    ): Boolean {
        val changes = getUpdateChanges(info)

        // Nothing to animate
        if (changes.totalTasks == 0) {
            startTransaction.apply()
            finishCallback.onTransitionFinished(/* wct */ null)
            return true
        }

        startJankInstrumentation(changes)

        setUpInitialSurfaces(changes, startTransaction)

        val fadeInAnimator = createFadeInAnimator(changes)
        val fadeOutAnimator = createFadeOutAnimator(changes)

        val onAnimationEnd: (Animator) -> Unit = {
            finishTransaction.apply {
                changes.openingChanges.forEach { c -> setAlpha(c.leash, 1f) }
                changes.closingChanges.forEach { c -> setAlpha(c.leash, 0f) }
            }
            mainExecutor.execute {
                finishCallback.onTransitionFinished(/* wct */ null)
                interactionJankMonitor.end(CUJ_APP_UPDATE)
            }
        }

        animExecutor.execute {
            AnimatorSet()
                .apply {
                    playTogether(fadeInAnimator, fadeOutAnimator)
                    addListener(onEnd = onAnimationEnd)
                }
                .start()
        }
        return true
    }

    private fun startJankInstrumentation(changes: UpdateChanges) {
        var change = changes.openingChanges[0]
        var surface = change.leash
        var tag = "Opening"

        // If the opening activity is not the PackageUpdateActivity then track the closing activity.
        if (!PackageUpdateActivity.isPackageUpdateActivityComponent(change.activityComponent)) {
            change = changes.closingChanges.firstOrNull() ?: return
            surface = change.leash
            tag = "Closing"
        }

        interactionJankMonitor.begin(surface, context, shellMainHandler, CUJ_APP_UPDATE, tag)
    }

    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo,
    ): WindowContainerTransaction? {
        return null
    }

    private fun setUpInitialSurfaces(
        changes: UpdateChanges,
        startTransaction: SurfaceControl.Transaction,
    ) {
        startTransaction.apply {
            // If there is more than one task, the ancestor surface might be the TDA or the root
            // task.
            // In that case we should place the activity surface at the place where tasks are.
            // Otherwise, the parent is the task surface which means we can just put it at the
            // origin.
            val shouldAdjustSurfaces = changes.totalTasks > 1

            changes.openingChanges.forEach { c ->
                setAlpha(c.leash, 0f)
                if (shouldAdjustSurfaces) {
                    setWindowCrop(c.leash, c.endAbsBounds.width(), c.endAbsBounds.height())
                    setCornerRadius(c.leash, cornerRadius)
                    setPosition(
                        c.leash,
                        c.endAbsBounds.left.toFloat(),
                        c.endAbsBounds.top.toFloat(),
                    )
                } else {
                    setPosition(c.leash, 0f, 0f)
                }
            }

            changes.closingChanges.forEach { c ->
                setAlpha(c.leash, 1f)
                if (shouldAdjustSurfaces) {
                    setWindowCrop(c.leash, c.endAbsBounds.width(), c.endAbsBounds.height())
                    setCornerRadius(c.leash, cornerRadius)
                }
            }
            setFrameTimeline(Choreographer.getInstance().vsyncId)
            apply()
        }
    }

    private fun createFadeInAnimator(changes: UpdateChanges): ValueAnimator? {
        val fadeInTx = transactionProvider.get()
        return ValueAnimator.ofFloat(0f, 1f).apply {
            duration = FADE_IN_DURATION
            interpolator = Interpolators.LINEAR
            addUpdateListener { animation ->
                changes.openingChanges.forEach { change ->
                    fadeInTx.setAlpha(change.leash, animation.animatedValue as Float)
                }
                fadeInTx.setFrameTimeline(Choreographer.getInstance().vsyncId).apply()
            }
        }
    }

    private fun createFadeOutAnimator(changes: UpdateChanges): ValueAnimator? {
        val fadeOutTx = transactionProvider.get()
        return ValueAnimator.ofFloat(1f, 0f).apply {
            duration = FADE_OUT_DURATION
            interpolator = Interpolators.LINEAR
            addUpdateListener { animation ->
                changes.closingChanges.forEach { change ->
                    fadeOutTx.setAlpha(change.leash, animation.animatedValue as Float)
                }
                fadeOutTx.setFrameTimeline(Choreographer.getInstance().vsyncId).apply()
            }
        }
    }

    private fun getUpdateChanges(info: TransitionInfo): UpdateChanges {
        val openingChanges = info.changes.filter { c -> isOpeningType(c.mode) }
        val closingChanges = info.changes.filter { c -> isClosingType(c.mode) }
        val totalTasks = openingChanges.size
        return UpdateChanges(closingChanges, openingChanges, totalTasks)
    }

    companion object {
        const val FADE_IN_DURATION = 200L
        const val FADE_OUT_DURATION = 150L
    }
}

private data class UpdateChanges(
    val closingChanges: List<TransitionInfo.Change>,
    val openingChanges: List<TransitionInfo.Change>,
    val totalTasks: Int,
)
