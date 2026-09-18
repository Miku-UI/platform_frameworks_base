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
import android.util.TimeUtils
import android.view.Choreographer
import androidx.annotation.VisibleForTesting
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.core.animation.Animator
import androidx.core.animation.AnimatorListenerAdapter
import androidx.core.animation.Interpolator
import androidx.core.animation.PathInterpolator
import androidx.core.animation.ValueAnimator
import com.android.app.animation.InterpolatorsAndroidX
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.topui.TopUiController
import com.android.systemui.topwindoweffects.data.repository.SqueezeEffectRepository.GestureStatus.COMPLETED
import com.android.systemui.topwindoweffects.data.repository.SqueezeEffectRepository.GestureStatus.HIDDEN
import com.android.systemui.topwindoweffects.data.repository.SqueezeEffectRepository.GestureStatus.PARTIAL
import com.android.systemui.topwindoweffects.domain.interactor.PowerButtonSemantics
import com.android.systemui.topwindoweffects.domain.interactor.SqueezeEffectInteractor
import com.android.systemui.topwindoweffects.ui.viewmodel.SqueezeEffectHapticPlayer
import com.android.wm.shell.appzoomout.AppZoomOut
import java.io.PrintWriter
import java.util.Optional
import javax.inject.Inject
import kotlin.math.sqrt
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
        set(value) {
            field = value
            appZoomOutOptional.ifPresent {
                it.setTopLevelProgress(field, Choreographer.getInstance().vsyncId, mainHandler)
            }
        }

    private var isGestureOngoing: Boolean = false

    private var animator: ValueAnimator? = null

    private val velocityTracker = VelocityTracker1D(false)

    private val hapticPlayer: SqueezeEffectHapticPlayer by lazy {
        squeezeEffectHapticPlayerFactory.create()
    }

    override fun start() {
        applicationScope.launch {
            squeezeEffectInteractor.powerButtonSemantics.collectLatest { semantics ->
                when (semantics) {
                    PowerButtonSemantics.START_SQUEEZE_WITH_RUMBLE ->
                        startSqueeze(
                            useHapticRumble = true,
                            inwardsAnimationDuration =
                                squeezeEffectInteractor
                                    .getLppInvocationEffectInAnimationDurationMillis(),
                            delayMs =
                                squeezeEffectInteractor.getLppInvocationEffectInitialDelayMillis(),
                        )

                    PowerButtonSemantics.START_SQUEEZE_WITHOUT_RUMBLE ->
                        startSqueeze(
                            useHapticRumble = false,
                            inwardsAnimationDuration =
                                squeezeEffectInteractor
                                    .getLppInvocationEffectInAnimationDurationMillis(),
                            delayMs =
                                squeezeEffectInteractor.getLppInvocationEffectInitialDelayMillis(),
                        )

                    PowerButtonSemantics.CANCEL_SQUEEZE -> cancelSqueeze()
                    PowerButtonSemantics.PLAY_DEFAULT_ASSISTANT_HAPTICS ->
                        playDefaultAssistantHaptic()
                }
            }
        }

        applicationScope.launch {
            squeezeEffectInteractor.gestureProgress.collectLatest { gestureProgress ->
                when (gestureProgress.status) {
                    PARTIAL ->
                        // Ignore gesture updates if animation is running
                        if (animator == null) {
                            if (isGestureOngoing && gestureProgress.progress == 0f) {
                                isGestureOngoing = false
                                squeezeProgress = 0f
                                setRequestTopUi(false)
                            } else if (gestureProgress.progress > 0f) {
                                if (!isGestureOngoing) {
                                    velocityTracker.resetTracking()
                                    isGestureOngoing = true
                                    setRequestTopUi(true)
                                }
                                squeezeProgress = gestureProgress.progress * GESTURE_MAX_EFFECT
                                velocityTracker.addDataPoint(
                                    Choreographer.getInstance().lastFrameTimeNanos /
                                        TimeUtils.NANOS_PER_MS,
                                    squeezeProgress,
                                )
                            }
                        }
                    COMPLETED ->
                        // Ignore gesture updates if animation is running
                        if (animator == null) {
                            isGestureOngoing = false
                            startSqueeze(
                                useHapticRumble = false,
                                inwardsAnimationDuration =
                                    squeezeEffectInteractor
                                        .getGestureInvocationEffectInAnimationDurationMillis(),
                                velocityPerMs = velocityTracker.calculateVelocity() / 1000,
                            )
                        }
                    HIDDEN -> {
                        isGestureOngoing = false
                        squeezeProgress = 0f
                        finishAnimation()
                    }
                }
            }
        }
    }

    private suspend fun startSqueeze(
        useHapticRumble: Boolean,
        inwardsAnimationDuration: Long,
        delayMs: Long = 0L,
        velocityPerMs: Float = 0f,
    ) {
        delay(delayMs)
        setRequestTopUi(true)
        val outwardsAnimationDuration =
            squeezeEffectInteractor.getInvocationEffectOutAnimationDurationMillis()
        if (useHapticRumble) {
            hapticPlayer.playRumble(inwardsAnimationDuration.toInt())
        }
        animateSqueezeProgressTo(
            targetProgress = 1f,
            duration = inwardsAnimationDuration,
            interpolator = getInwardsInterpolator(velocityPerMs, inwardsAnimationDuration),
        ) {
            hapticPlayer.startZoomOutEffect(
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
                hapticPlayer.playLppIndicator()
            }
        }
    }

    // Creates a new interpolator based on the LEGACY interpolator but matching the initial velocity
    // provided
    private fun getInwardsInterpolator(velocityPerMs: Float, durationMs: Long) =
        if (velocityPerMs > 0f) {
            val slope = (velocityPerMs * durationMs) / (1f - squeezeProgress)
            val length = 0.4f
            val x1 = length / sqrt(1 + slope * slope)
            val y1 = slope * x1
            PathInterpolator(x1, y1, 0.2f, 1f)
        } else {
            InterpolatorsAndroidX.LEGACY
        }

    private fun cancelSqueeze() {
        if (isAnimationInterruptible && animator != null) {
            hapticPlayer.cancel()
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
                addUpdateListener { squeezeProgress = animatedValue as Float }
                setListenerForNaturalCompletion { doOnEnd() }
                start()
            }
    }

    private fun finishAnimation() {
        animator?.cancel()
        animator = null
        isAnimationInterruptible = true
        setRequestTopUi(false)
    }

    private fun setRequestTopUi(requestTopUi: Boolean) {
        topUiController.setRequestTopUi(requestTopUi, TAG)
    }

    private fun playDefaultAssistantHaptic() = hapticPlayer.playDefaultAssistantEffect()

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        pw.println("$TAG:")
        pw.println("  isAnimationInterruptible=$isAnimationInterruptible")
        pw.println("  isGestureOngoing=$isGestureOngoing")
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

        /**
         * Maximum of the invocation effect that is applied during corner gesture, i.e. before the
         * invocation has been committed.
         */
        @VisibleForTesting const val GESTURE_MAX_EFFECT = 0.25f
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
