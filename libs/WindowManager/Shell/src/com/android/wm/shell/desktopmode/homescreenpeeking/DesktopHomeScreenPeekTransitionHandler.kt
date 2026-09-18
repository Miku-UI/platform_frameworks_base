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

package com.android.wm.shell.desktopmode.homescreenpeeking

import android.animation.ValueAnimator
import android.app.WindowConfiguration.ACTIVITY_TYPE_HOME
import android.os.IBinder
import android.view.Display.DEFAULT_DISPLAY
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_OPEN
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import androidx.core.animation.addListener
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.desktopwallpaperactivity.DesktopWallpaperActivityTokenProvider
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.transition.Transitions
import java.util.function.Supplier

/** Class to manage home screen peeking transitions and animations in desktop mode. */
class DesktopHomeScreenPeekTransitionHandler(
    private val mainExecutor: ShellExecutor,
    private val transitions: Transitions,
    private val transactionSupplier: Supplier<SurfaceControl.Transaction>,
    private val shellController: ShellController,
    private val userRepositories: DesktopUserRepositories,
    private val desktopWallpaperActivityTokenProvider: DesktopWallpaperActivityTokenProvider,
) : Transitions.TransitionHandler {
    private var onTransitionAnimationFinished: (() -> Unit)? = null

    /**
     * Starts a TRANSIT_CHANGE transition for the provided WindowContainerTransaction, with an
     * optional callback to run when the transition animation is finished.
     */
    fun startTransition(
        wct: WindowContainerTransaction,
        onTransitionAnimationFinishedCallback: (() -> Unit)? = null,
    ) {
        onTransitionAnimationFinished = onTransitionAnimationFinishedCallback
        transitions.startTransition(TRANSIT_CHANGE, wct, this)
    }

    // Setting this as active handler directly in startTransition.
    @Deprecated("Use handleRequestOnly instead.", ReplaceWith("handleRequestOnly"))
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
        ProtoLog.v(WM_SHELL_DESKTOP_MODE, "%s: startAnimation", TAG)
        val desktopChanges = getDesktopChanges(info)
        if (desktopChanges.isEmpty()) {
            ProtoLog.w(
                WM_SHELL_DESKTOP_MODE,
                "%s: Not animating transition, as no desktop TransitionInfo.Change exist.",
                TAG,
            )
            return false
        }
        val homeChange = getHomeChange(info)
        val isAnimatingToPeek = isChangePeekingHomeScreen(homeChange)
        val wallpaperChange = getDesktopWallpaperActivityChange(info)
        val animator =
            createPeekAnimator(
                finishTransaction,
                finishCallback,
                desktopChanges,
                homeChange,
                wallpaperChange,
                isAnimatingToPeek,
            )
        setStartAndFinishTransactionValues(
            startTransaction,
            finishTransaction,
            desktopChanges,
            homeChange,
            wallpaperChange,
            isAnimatingToPeek,
        )
        startTransaction.apply()
        mainExecutor.execute { animator.start() }
        return true
    }

    private fun getDesktopChanges(info: TransitionInfo): List<TransitionInfo.Change> =
        info.changes.filter { change ->
            val taskId = change.taskInfo?.taskId
            taskId != null &&
                userRepositories.getProfile(shellController.currentUserId).isActiveTask(taskId)
        }

    private fun getDesktopWallpaperActivityChange(info: TransitionInfo): TransitionInfo.Change? =
        info.changes.find {
            it.taskInfo?.token == desktopWallpaperActivityTokenProvider.getToken(getDisplayId())
        }

    private fun getHomeChange(info: TransitionInfo): TransitionInfo.Change? =
        info.changes.find { it.taskInfo?.activityType == ACTIVITY_TYPE_HOME }

    private fun isChangePeekingHomeScreen(homeChange: TransitionInfo.Change?): Boolean =
        homeChange != null &&
            (homeChange.mode == TRANSIT_OPEN || homeChange.mode == TRANSIT_TO_FRONT)

    /**
     * Animates the desktop tasks between peek and unpeek states.
     *
     * Also animates the DesktopWallpaperActivity and home screen if they exist, to reveal home.
     */
    private fun createPeekAnimator(
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
        desktopChanges: List<TransitionInfo.Change>,
        homeChange: TransitionInfo.Change?,
        wallpaperChange: TransitionInfo.Change?,
        isAnimatingToPeek: Boolean,
    ): ValueAnimator {
        val transaction = transactionSupplier.get()
        return ValueAnimator.ofFloat(0f, 1f).apply {
            addUpdateListener { animation ->
                val fraction = animation.animatedFraction
                // Translate desktop tasks.
                desktopChanges.forEach { change ->
                    val start = change.startAbsBounds
                    val end = change.endAbsBounds
                    val x = start.left + (end.left - start.left) * fraction
                    transaction.setPosition(change.leash, x, start.top.toFloat())
                }
                // Reveal or hide the home screen via alpha animation.
                homeChange?.leash?.let { leash ->
                    val alpha = if (isAnimatingToPeek) fraction else 1f - fraction
                    transaction.setAlpha(leash, alpha)
                }
                // Reveal or hide the DesktopWallpaperActivity via alpha animation.
                wallpaperChange?.leash?.let { leash ->
                    val alpha = if (isAnimatingToPeek) 1f - fraction else fraction
                    transaction.setAlpha(leash, alpha)
                }
                transaction.apply()
            }
            addListener(
                onEnd = {
                    onTransitionAnimationFinished?.invoke()
                    onTransitionAnimationFinished = null
                    finishTransaction.apply()
                    finishCallback.onTransitionFinished(/* wct= */ null)
                    ProtoLog.v(WM_SHELL_DESKTOP_MODE, "%s: startAnimation finished", TAG)
                }
            )
        }
    }

    /** Set start and finish transaction values for desktop, home, and wallpaper changes. */
    private fun setStartAndFinishTransactionValues(
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        desktopChanges: List<TransitionInfo.Change>,
        homeChange: TransitionInfo.Change?,
        wallpaperChange: TransitionInfo.Change?,
        isAnimatingToPeek: Boolean,
    ) {
        desktopChanges.forEach { change ->
            val start = change.startAbsBounds
            val end = change.endAbsBounds
            startTransaction.setPosition(change.leash, start.left.toFloat(), start.top.toFloat())
            finishTransaction.setPosition(change.leash, end.left.toFloat(), end.top.toFloat())
        }
        wallpaperChange?.leash?.let {
            val alpha = if (isAnimatingToPeek) 1f else 0f
            startTransaction.setAlpha(it, alpha)
            finishTransaction.setAlpha(it, 1f - alpha)
        }
        homeChange
            ?.leash
            ?.takeIf { isAnimatingToPeek }
            ?.let {
                startTransaction.setAlpha(it, 0f)
                finishTransaction.setAlpha(it, 1f)
            }
    }

    // Currently we only peek the default display, as Launcher is not present on other displays,
    // but define this as a function here for future expansion.
    fun getDisplayId() = DEFAULT_DISPLAY

    companion object {
        private const val TAG = "DesktopHomeScreenPeekTransitionHandler"
    }
}
