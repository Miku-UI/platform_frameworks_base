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

package com.android.systemui.util.animation

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.View
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.android.app.tracing.traceSection
import com.android.systemui.Flags.screenOffAnimationGuardEnabled
import com.android.systemui.res.R
import com.android.systemui.util.weakReference
import java.lang.ref.WeakReference

private const val LOG_TAG = "AnimationGuard"
private const val LOTTIE_UPDATES_WHEN_SCREEN_OFF_LIMIT = 2

/**
 * This observes a given animation view and reports a WTF if the animations are running while the
 * screen is off.
 */
fun LottieAnimationView.enableScreenOffAnimationGuard() {
    if (!(Build.IS_ENG || Build.IS_USERDEBUG)) {
        return
    }

    if (!screenOffAnimationGuardEnabled()) {
        return
    }

    val lottieDrawable = drawable as? LottieDrawable ?: return
    if (getTag(R.id.screen_off_animation_guard_set) == System.identityHashCode(lottieDrawable)) {
        return
    }

    val animationView = WeakReference(this)
    val screenOffListenerGuard: ValueAnimator.AnimatorUpdateListener =
        ValueAnimator.AnimatorUpdateListener {
            animationView.get()?.let { view ->
                if (view.getTag(R.id.screen_off_animation_guard_reported_wtf) == true) {
                    return@AnimatorUpdateListener
                }

                // Retrieve ID of the view rendering
                val viewIdName =
                    try {
                        if (view.id != View.NO_ID) {
                            view.resources.getResourceEntryName(view.id)
                        } else {
                            "no-id"
                        }
                    } catch (e: Resources.NotFoundException) {
                        view.id.toString()
                    }

                val isScreenOff = view.display?.committedState == Display.STATE_OFF
                if (isScreenOff) {
                    if (reachedFrameThreshold()) {
                        // These logs create Binder calls, so throttle them. One is enough.
                        Log.wtf(
                            LOG_TAG,
                            "Lottie view $viewIdName is running while screen" +
                                " is off; more than $LOTTIE_UPDATES_WHEN_SCREEN_OFF_LIMIT updates",
                        )
                        view.setTag(R.id.screen_off_animation_guard_reported_wtf, true)
                    } else {
                        val updates =
                            (getTag(R.id.screen_off_animation_guard_screen_off_updates) as Int) + 1
                        if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
                            Log.d(
                                LOG_TAG,
                                "Lottie view $viewIdName is running while " +
                                    "screen is off # updates=$updates",
                            )
                        }
                        setTag(R.id.screen_off_animation_guard_screen_off_updates, updates)
                    }
                } else if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
                    Log.d(LOG_TAG, "Lottie view $viewIdName is running while screen is on")
                }
            }
        }

    setTag(R.id.screen_off_animation_guard_reported_wtf, false)
    setTag(R.id.screen_off_animation_guard_screen_off_updates, 0)
    lottieDrawable.addAnimatorUpdateListener(screenOffListenerGuard)
    setTag(R.id.screen_off_animation_guard_set, System.identityHashCode(lottieDrawable))
}

private fun LottieAnimationView.reachedFrameThreshold(): Boolean {
    val framesRenderedWhenOff = getTag(R.id.screen_off_animation_guard_screen_off_updates) as Int
    return framesRenderedWhenOff >= LOTTIE_UPDATES_WHEN_SCREEN_OFF_LIMIT
}

/**
 * Attaches a listener which will report a [Log.wtf] error if the animator is attempting to render
 * frames while the screen is off.
 */
fun ValueAnimator.enableScreenOffAnimationGuard(context: Context) {
    if (!(Build.IS_ENG || Build.IS_USERDEBUG)) {
        return
    }

    val weakContext by weakReference(context)
    enableScreenOffAnimationGuard({ weakContext?.display?.committedState == Display.STATE_OFF })
}

/** Attaches an animation guard listener to the given ValueAnimator. */
fun ValueAnimator.enableScreenOffAnimationGuard(isDisplayOffPredicate: () -> Boolean) {
    if (!screenOffAnimationGuardEnabled()) {
        return
    }

    val listener = ScreenOffAnimationGuardListener(isDisplayOffPredicate)
    this.addListener(listener)
    this.addUpdateListener(listener)
}

/**
 * Attaches a listener which will report a [Log.wtf] error if the animator is attempting to render
 * frames while the screen is off.
 */
fun androidx.core.animation.ValueAnimator.enableScreenOffAnimationGuard(context: Context) {
    if (!(Build.IS_ENG || Build.IS_USERDEBUG)) {
        return
    }

    val weakContext by weakReference(context)
    enableScreenOffAnimationGuard({ weakContext?.display?.committedState == Display.STATE_OFF })
}

/** Attaches an animation guard listener to the given ValueAnimator. */
fun androidx.core.animation.ValueAnimator.enableScreenOffAnimationGuard(
    isDisplayOffPredicate: () -> Boolean
) {
    if (!screenOffAnimationGuardEnabled()) {
        return
    }

    val listener = ScreenOffAnimationGuardListener(isDisplayOffPredicate)
    this.addListener(listener)
    this.addUpdateListener(listener)
}

/**
 * Remembers the stack trace of started animation and then reports an error if it runs when screen
 * is off.
 *
 * The implementation is a bit wonky because we account for both platform and AndroidX versions of
 * the listener.
 */
private class ScreenOffAnimationGuardListener(private val isDisplayOffPredicate: () -> Boolean) :
    Animator.AnimatorListener,
    ValueAnimator.AnimatorUpdateListener,
    androidx.core.animation.Animator.AnimatorListener,
    androidx.core.animation.Animator.AnimatorUpdateListener {

    val FRAMES_WHEN_SCREEN_OFF_LIMIT = 2

    // Holds the exception stack trace for the report.
    var animationStartedStackTrace: Exception? = null
    var framesRenderedWhileScreenIsOff = 0
    var animationDuringScreenOffReported = false

    /* Start animation, platform version. */
    override fun onAnimationStart(animation: Animator) {
        captureAnimationStackTrace()
    }

    /* Start animation, AndroidX version. */
    override fun onAnimationStart(animation: androidx.core.animation.Animator) {
        captureAnimationStackTrace()
    }

    /* End animation, platform version. */
    override fun onAnimationEnd(animation: Animator) {
        animationStartedStackTrace = null
    }

    /* End animation, AndroidX version. */
    override fun onAnimationEnd(animation: androidx.core.animation.Animator) {
        animationStartedStackTrace = null
    }

    /* Animation step, platform version. */
    override fun onAnimationUpdate(animation: ValueAnimator) {
        checkAndReportAnimationDuringScreenOff()
    }

    /* Animation step, AndroidX version. */
    override fun onAnimationUpdate(animation: androidx.core.animation.Animator) {
        checkAndReportAnimationDuringScreenOff()
    }

    override fun onAnimationCancel(animation: Animator) {}

    override fun onAnimationCancel(animation: androidx.core.animation.Animator) {}

    override fun onAnimationRepeat(animation: Animator) {}

    override fun onAnimationRepeat(animation: androidx.core.animation.Animator) {}

    /**
     * Stores the stack trace of "startAnimation" caller so we can determine which animator is
     * actually running.
     */
    fun captureAnimationStackTrace() =
        traceSection("captureAnimationStackTrace") {
            // This captures the stack trace of the starter of this animation.
            animationStartedStackTrace =
                AnimationDuringScreenOffException("Animation running during screen off.")
        }

    /** Reports WTF if we detect the animation running during screen off with saved stack trace. */
    fun checkAndReportAnimationDuringScreenOff() {
        if (!animationDuringScreenOffReported && isDisplayOffPredicate()) {
            // We want to give a bit of a leeway to make sure we don't report on animators
            // that race against screen off - that is, animators that _just_ complete as
            // the screen turned off.
            framesRenderedWhileScreenIsOff++
            if (framesRenderedWhileScreenIsOff < FRAMES_WHEN_SCREEN_OFF_LIMIT) {
                return
            }
            traceSection("reportAnimationDuringScreenOff") {
                Log.wtf(
                    LOG_TAG,
                    "View animator running during screen off.",
                    animationStartedStackTrace,
                )
                animationDuringScreenOffReported = true
            }
        }
    }
}

/** Used to record the stack trace of animation starter. */
private class AnimationDuringScreenOffException(message: String) : RuntimeException(message)
