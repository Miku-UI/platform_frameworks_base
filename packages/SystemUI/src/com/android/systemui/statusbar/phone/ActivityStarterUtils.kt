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

package com.android.systemui.statusbar.phone

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.os.Bundle
import android.os.IBinder
import android.view.RemoteAnimationAdapter
import android.window.RemoteTransition
import android.window.SplashScreen
import com.android.systemui.animation.ActivityTransitionAnimator
import com.android.systemui.animation.DelegateTransitionAnimatorController
import com.android.systemui.animation.RemoteAnimationRunnerCompat

/**
 * Returns an [ActivityOptions] bundle created using the given parameters.
 *
 * @param displayId The ID of the display from which we are launching the activity. Typically this
 *   would be the display the status bar is on.
 * @param transition The animation driver used to start this activity, or null for the default
 *   animation.
 * @param cookie The launch cookie associated with this activity, or null. Only used if [transition]
 *   is also not null.
 */
fun createActivityOptions(displayId: Int, transition: RemoteTransition?, cookie: IBinder?): Bundle {
    return createDefaultActivityOptions(transition, cookie)
        .apply {
            callerDisplayId = displayId
            isPendingIntentBackgroundActivityLaunchAllowed = true
        }
        .toBundle()
}

/**
 * Returns an [ActivityOptions] bundle created using the given parameters.
 *
 * @param displayId The ID of the display from which we are launching the activity. Typically this
 *   would be the display the status bar is on.
 * @param transition The animation driver used to start this activity, or null for the default
 *   animation.
 * @param cookie The launch cookie associated with this activity, or null. Only used if [transition]
 *   is also not null.
 * @param isKeyguardShowing Whether keyguard is currently showing.
 * @param eventTime The event time in milliseconds since boot, not including sleep. See
 *   [ActivityOptions.setSourceInfo].
 */
fun createActivityOptions(
    displayId: Int,
    transition: RemoteTransition?,
    cookie: IBinder?,
    isKeyguardShowing: Boolean,
    eventTime: Long,
): Bundle {
    return createDefaultActivityOptions(transition, cookie)
        .apply {
            setSourceInfo(
                if (isKeyguardShowing) {
                    ActivityOptions.SourceInfo.TYPE_LOCKSCREEN
                } else {
                    ActivityOptions.SourceInfo.TYPE_NOTIFICATION
                },
                eventTime,
            )
            callerDisplayId = displayId
            isPendingIntentBackgroundActivityLaunchAllowed = true
        }
        .toBundle()
}

@SuppressLint("MissingPermission")
private fun createDefaultActivityOptions(
    transition: RemoteTransition?,
    cookie: IBinder?,
): ActivityOptions {
    val options =
        if (transition != null) {
            ActivityOptions.makeRemoteTransition(transition)
        } else {
            ActivityOptions.makeBasic()
        }
    options.launchCookie = cookie
    options.splashScreenStyle = SplashScreen.SPLASH_SCREEN_STYLE_SOLID_COLOR
    return options
}

/**
 * Returns an ActivityOptions bundle created using the given parameters.
 *
 * @param displayId The ID of the display from which we are launching the activity. Typically this
 *   would be the display the status bar is on.
 * @param animationAdapter The animation adapter used to start this activity, or {@code null} for
 *   the default animation.
 */
@Deprecated(
    "Launches requiring the creation of ActivityOptions must use the  RemoteTransition API, and " +
        "the createActivityOptions(Int, RemoteTransition?, IBinder?) overload accordingly."
)
fun createActivityOptions(displayId: Int, animationAdapter: RemoteAnimationAdapter?): Bundle {
    return createDefaultActivityOptions(animationAdapter)
        .apply {
            callerDisplayId = displayId
            isPendingIntentBackgroundActivityLaunchAllowed = true
        }
        .toBundle()
}

/**
 * Returns an ActivityOptions bundle created using the given parameters.
 *
 * @param displayId The ID of the display from which we are launching the activity. Typically this
 *   would be the display the status bar is on.
 * @param animationAdapter The animation adapter used to start this activity, or {@code null} for
 *   the default animation.
 * @param isKeyguardShowing Whether keyguard is currently showing.
 * @param eventTime The event time in milliseconds since boot, not including sleep. See {@link
 *   ActivityOptions#setSourceInfo}.
 */
@Deprecated(
    "Launches requiring the creation of ActivityOptions must use the  RemoteTransition API, and " +
        "the createActivityOptions(Int, RemoteTransition?, IBinder?, Boolean, Long) overload " +
        "accordingly."
)
fun createActivityOptions(
    displayId: Int,
    animationAdapter: RemoteAnimationAdapter?,
    isKeyguardShowing: Boolean,
    eventTime: Long,
): Bundle {
    return createDefaultActivityOptions(animationAdapter)
        .apply {
            setSourceInfo(
                if (isKeyguardShowing) {
                    ActivityOptions.SourceInfo.TYPE_LOCKSCREEN
                } else {
                    ActivityOptions.SourceInfo.TYPE_NOTIFICATION
                },
                eventTime,
            )
            callerDisplayId = displayId
            isPendingIntentBackgroundActivityLaunchAllowed = true
        }
        .toBundle()
}

@SuppressLint("MissingPermission")
private fun createDefaultActivityOptions(
    animationAdapter: RemoteAnimationAdapter?
): ActivityOptions {
    val options =
        if (animationAdapter != null) {
            ActivityOptions.makeRemoteTransition(
                RemoteTransition(
                    RemoteAnimationRunnerCompat.wrap(animationAdapter.runner),
                    animationAdapter.callingApplication,
                    "SysUILaunch",
                )
            )
        } else {
            ActivityOptions.makeBasic()
        }
    options.splashScreenStyle = SplashScreen.SPLASH_SCREEN_STYLE_SOLID_COLOR
    return options
}

/**
 * If [controller] is not null and does not already have a transition cookie, this function
 * generates a new unique cookie and returns a new [ActivityTransitionAnimator.Controller] with it
 * based on [identity] (if provided), or [controller]. Otherwise it just returns [controller]
 * unchanged.
 *
 * The [identity] object can be used to allow multiple calls from the same source but using
 * individually instantiated controllers to use equivalent cookies.
 */
@JvmOverloads
fun addCookieIfNeeded(
    controller: ActivityTransitionAnimator.Controller?,
    identity: Any? = null,
): ActivityTransitionAnimator.Controller? {
    return if (controller?.transitionCookie != null) {
        controller
    } else if (controller != null) {
        object : DelegateTransitionAnimatorController(controller) {
            override val transitionCookie =
                if (identity != null) {
                    ActivityTransitionAnimator.TransitionCookie("$identity")
                } else {
                    ActivityTransitionAnimator.TransitionCookie("$controller")
                }
        }
    } else {
        null
    }
}
