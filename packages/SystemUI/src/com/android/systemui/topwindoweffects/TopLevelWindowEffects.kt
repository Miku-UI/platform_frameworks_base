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

package com.android.systemui.topwindoweffects

import android.os.Handler
import android.view.Choreographer
import androidx.annotation.VisibleForTesting
import androidx.core.animation.Animator
import androidx.core.animation.AnimatorListenerAdapter
import androidx.core.animation.Interpolator
import androidx.core.animation.ValueAnimator
import com.android.app.animation.InterpolatorsAndroidX
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.topui.TopUiController
import com.android.systemui.topwindoweffects.domain.interactor.PowerButtonSemantics
import com.android.systemui.topwindoweffects.domain.interactor.SqueezeEffectInteractor
import com.android.systemui.topwindoweffects.ui.viewmodel.SqueezeEffectHapticPlayer
import com.android.wm.shell.appzoomout.AppZoomOut
import java.io.PrintWriter
import java.util.Optional
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@SysUISingleton
class TopLevelWindowEffects
@Inject
constructor(
    @Application private val applicationScope: CoroutineScope,
    private val squeezeEffectInteractor: SqueezeEffectInteractor,
    // TODO(b/409930584): make AppZoomOut non-optional
    private val appZoomOutOptional: Optional<AppZoomOut>,
    squeezeEffectHapticPlayerFactory: SqueezeEffectHapticPlayer.Factory,
    private val topUiController: TopUiController,
    @Main private val mainHandler: Handler,
) : CoreStartable {

    // The main animation is interruptible until power button long press has been detected. At this
    // point the default assistant is invoked, and since this invocation cannot be interrupted by
    // lifting the power button the animation shouldn't be interruptible either.
    private var isAnimationInterruptible = true

    private var squeezeProgress: Float = 0f

    private var animator: ValueAnimator? = null

    private val hapticPlayer: SqueezeEffectHapticPlayer? by lazy {
        if (squeezeEffectInteractor.isSqueezeEffectHapticEnabled) {
            squeezeEffectHapticPlayerFactory.create()
        } else {
            null
        }
    }

    override fun start() {
        applicationScope.launch {
            squeezeEffectInteractor.powerButtonSemantics.collectLatest { semantics ->
                when (semantics) {
                    PowerButtonSemantics.START_SQUEEZE_WITH_RUMBLE ->
                        startSqueeze(useHapticRumble = true)
                    PowerButtonSemantics.START_SQUEEZE_WITHOUT_RUMBLE ->
                        startSqueeze(useHapticRumble = false)
                    PowerButtonSemantics.CANCEL_SQUEEZE -> cancelSqueeze()
                    PowerButtonSemantics.PLAY_DEFAULT_ASSISTANT_HAPTICS ->
                        playDefaultAssistantHaptic()
                }
            }
        }
    }

    private suspend fun startSqueeze(useHapticRumble: Boolean) {
        delay(squeezeEffectInteractor.getInvocationEffectInitialDelayMillis())
        setRequestTopUi(true)
        val inwardsAnimationDuration =
            squeezeEffectInteractor.getInvocationEffectInAnimationDurationMillis()
        val outwardsAnimationDuration =
            squeezeEffectInteractor.getInvocationEffectOutAnimationDurationMillis()
        if (useHapticRumble) {
            hapticPlayer?.playRumble(inwardsAnimationDuration.toInt())
        }
        animateSqueezeProgressTo(
            targetProgress = 1f,
            duration = inwardsAnimationDuration,
            interpolator = InterpolatorsAndroidX.LEGACY,
        ) {
            hapticPlayer?.startZoomOutEffect(
                durationMillis =
                    (HAPTIC_OUTWARD_EFFECT_DURATION_SCALE * outwardsAnimationDuration).toInt()
            )
            animateSqueezeProgressTo(
                targetProgress = 0f,
                duration = outwardsAnimationDuration,
                interpolator = InterpolatorsAndroidX.EMPHASIZED,
            ) {
                finishAnimation()
            }
        }
        squeezeEffectInteractor.isPowerButtonLongPressed.collectLatest { isLongPressed ->
            if (isLongPressed) {
                isAnimationInterruptible = false
                hapticPlayer?.playLppIndicator()
            }
        }
    }

    private fun cancelSqueeze() {
        if (isAnimationInterruptible && animator != null) {
            hapticPlayer?.cancel()
            animateSqueezeProgressTo(
                targetProgress = 0f,
                duration = squeezeEffectInteractor.getInvocationEffectOutAnimationDurationMillis(),
                interpolator = InterpolatorsAndroidX.EMPHASIZED,
            ) {
                finishAnimation()
            }
        }
    }

    private fun animateSqueezeProgressTo(
        targetProgress: Float,
        duration: Long,
        interpolator: Interpolator,
        doOnEnd: () -> Unit,
    ) {
        animator?.cancel()
        animator =
            ValueAnimator.ofFloat(squeezeProgress, targetProgress).apply {
                this.duration = duration
                this.interpolator = interpolator
                addUpdateListener {
                    squeezeProgress = animatedValue as Float
                    appZoomOutOptional.ifPresent {
                        it.setTopLevelProgress(
                            squeezeProgress,
                            Choreographer.getInstance().vsyncId,
                            mainHandler,
                        )
                    }
                }
                setListenerForNaturalCompletion { doOnEnd() }
                start()
            }
    }

    private fun finishAnimation() {
        animator = null
        isAnimationInterruptible = true
        setRequestTopUi(false)
    }

    private fun setRequestTopUi(requestTopUi: Boolean) {
        topUiController.setRequestTopUi(requestTopUi, TAG)
    }

    private fun playDefaultAssistantHaptic() = hapticPlayer?.playDefaultAssistantEffect()

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        pw.println("$TAG:")
        pw.println("  isAnimationInterruptible=$isAnimationInterruptible")
        pw.println("  squeezeProgress=$squeezeProgress")
        squeezeEffectInteractor.dump(pw, args)
    }

    companion object {
        @VisibleForTesting const val TAG = "TopLevelWindowEffects"

        /**
         * A scale applied to the outward animation duration to derive the duration of the haptic
         * effect. This number is fine tuned to produce a haptic effect that suits the outward
         * animator interpolator well.
         */
        @VisibleForTesting const val HAPTIC_OUTWARD_EFFECT_DURATION_SCALE = 0.53
    }
}

/**
 * Adds an [Animator.AnimatorListener] to this [ValueAnimator] that triggers the
 * [onNaturallyCompletedAction] only when the animation finishes normally (i.e., not cancelled).
 *
 * This works because [AnimatorListenerAdapter.onAnimationCancel] is guaranteed to be called before
 * [AnimatorListenerAdapter.onAnimationEnd] (if the animation is cancelled). See
 * https://developer.android.com/reference/android/animation/Animator#cancel()
 *
 * @param onNaturallyCompletedAction The lambda to execute when the animation ends without being
 *   cancelled.
 */
private fun ValueAnimator.setListenerForNaturalCompletion(onNaturallyCompletedAction: () -> Unit) {
    addListener(
        object : AnimatorListenerAdapter() {
            private var wasCancelled = false

            override fun onAnimationCancel(animation: Animator) {
                wasCancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (!wasCancelled) {
                    onNaturallyCompletedAction()
                }
            }
        }
    )
}
