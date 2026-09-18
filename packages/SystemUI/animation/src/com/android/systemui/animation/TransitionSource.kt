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

package com.android.systemui.animation

import android.content.ComponentName
import android.view.View

/**
 * Represents a single, short-lived UI component (a View or a Composable) that can participate as an
 * origin or target in an animated transition within System UI.
 *
 * Implementations of this interface provide the specific animation controllers and layout
 * information needed for a smooth transition. [TransitionSource] instances are managed by an
 * [Expandable] coordinator object, which tracks their lifecycle and resolves the optimal active
 * source for animation.
 */
interface TransitionSource {

    // If source is visible.
    var isSourceVisible: Boolean

    var onSourceVisibilityChanged: (Boolean) -> Unit

    /**
     * Create a [DialogTransitionAnimator.Controller] that can be used to expand this
     * [TransitionSource] into a Dialog, or return `null` if this [TransitionSource] should not be
     * animated (e.g. if it is currently not attached or visible).
     */
    fun dialogTransitionController(cuj: DialogCuj? = null): DialogTransitionAnimator.Controller?

    /**
     * Create an [ActivityTransitionAnimator.Controller] that can be used to expand this
     * [TransitionSource] into an Activity, or return `null` if this [TransitionSource] should not
     * be animated (e.g. if it is currently not attached or visible).
     *
     * @param launchCujType The CUJ type from the [com.android.internal.jank.InteractionJankMonitor]
     *   associated to the launch that will use this controller.
     * @param cookie The unique cookie associated with the launch that will use this controller.
     *   This is required iff a return animation should be included.
     * @param component The name of the activity that will be launched by this controller. This is
     *   required for long-lived registrations only.
     * @param returnCujType The CUJ type from the [com.android.internal.jank.InteractionJankMonitor]
     *   associated to the return animation that will use this controller.
     */
    fun activityTransitionController(
        launchCujType: Int? = null,
        cookie: ActivityTransitionAnimator.TransitionCookie? = null,
        component: ComponentName? = null,
        returnCujType: Int? = null,
        isEphemeral: Boolean = true,
    ): ActivityTransitionAnimator.Controller?

    companion object {
        /**
         * Create an [TransitionSource] that will animate [view] when expanded/collapsed.
         *
         * Note: The background of [view] should be a (rounded) rectangle so that it can be properly
         * animated.
         */
        @JvmStatic
        fun fromView(view: View): TransitionSource {
            return object : TransitionSource {

                override var onSourceVisibilityChanged: (Boolean) -> Unit = {}

                override var isSourceVisible: Boolean = true

                override fun activityTransitionController(
                    launchCujType: Int?,
                    cookie: ActivityTransitionAnimator.TransitionCookie?,
                    component: ComponentName?,
                    returnCujType: Int?,
                    isEphemeral: Boolean,
                ): ActivityTransitionAnimator.Controller? {
                    return ActivityTransitionAnimator.Controller.fromView(
                        view,
                        launchCujType,
                        cookie,
                        component,
                        returnCujType,
                        isEphemeral,
                    )
                }

                override fun dialogTransitionController(
                    cuj: DialogCuj?
                ): DialogTransitionAnimator.Controller? {
                    return DialogTransitionAnimator.Controller.fromView(view, cuj)
                }
            }
        }
    }
}
