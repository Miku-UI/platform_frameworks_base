/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.haptics.msdl.qs

import android.content.ComponentName
import android.view.ViewGroup
import com.android.systemui.animation.ActivityTransitionAnimator
import com.android.systemui.animation.DialogCuj
import com.android.systemui.animation.DialogTransitionAnimator
import com.android.systemui.animation.Expandable
import com.android.systemui.animation.TransitionAnimator
import com.android.systemui.animation.TransitionSource

private fun ActivityTransitionAnimator.Controller.withStateAwareness(
    onActivityLaunchTransitionStart: () -> Unit,
    onActivityLaunchTransitionEnd: () -> Unit,
): ActivityTransitionAnimator.Controller {
    val delegate = this
    return object : ActivityTransitionAnimator.Controller by delegate {
        override fun onTransitionAnimationStart(isExpandingFullyAbove: Boolean) {
            onActivityLaunchTransitionStart()
            delegate.onTransitionAnimationStart(isExpandingFullyAbove)
        }

        override fun onTransitionAnimationCancelled(newKeyguardOccludedState: Boolean?) {
            onActivityLaunchTransitionEnd()
            delegate.onTransitionAnimationCancelled(newKeyguardOccludedState)
        }

        override fun onTransitionAnimationEnd(isExpandingFullyAbove: Boolean) {
            onActivityLaunchTransitionEnd()
            delegate.onTransitionAnimationEnd(isExpandingFullyAbove)
        }
    }
}

private fun DialogTransitionAnimator.Controller.withStateAwareness(
    onDialogDrawingStart: () -> Unit,
    onDialogDrawingEnd: () -> Unit,
): DialogTransitionAnimator.Controller {
    val delegate = this
    return object : DialogTransitionAnimator.Controller by delegate {

        override fun startDrawingInOverlayOf(viewGroup: ViewGroup) {
            onDialogDrawingStart()
            delegate.startDrawingInOverlayOf(viewGroup)
        }

        override fun stopDrawingInOverlay() {
            onDialogDrawingEnd()
            delegate.stopDrawingInOverlay()
        }
    }
}

// Helper function to contain the wrapping logic.
private fun TransitionSource.stateAwareTransitionSource(
    onDialogDrawingStart: () -> Unit,
    onDialogDrawingEnd: () -> Unit,
    onActivityLaunchTransitionStart: () -> Unit,
    onActivityLaunchTransitionEnd: () -> Unit,
): TransitionSource {

    val source = this
    return object : TransitionSource {

        override var isSourceVisible: Boolean = source.isSourceVisible

        override var onSourceVisibilityChanged: (Boolean) -> Unit = source.onSourceVisibilityChanged

        override fun dialogTransitionController(
            cuj: DialogCuj?
        ): DialogTransitionAnimator.Controller? {
            return source
                .dialogTransitionController(cuj)
                ?.withStateAwareness(onDialogDrawingStart, onDialogDrawingEnd)
        }

        override fun activityTransitionController(
            launchCujType: Int?,
            cookie: ActivityTransitionAnimator.TransitionCookie?,
            component: ComponentName?,
            returnCujType: Int?,
            isEphemeral: Boolean,
        ): ActivityTransitionAnimator.Controller? {
            return source
                .activityTransitionController(
                    launchCujType,
                    cookie,
                    component,
                    returnCujType,
                    isEphemeral,
                )
                ?.withStateAwareness(onActivityLaunchTransitionStart, onActivityLaunchTransitionEnd)
        }
    }
}

fun Expandable.withStateAwareness(
    onDialogDrawingStart: () -> Unit,
    onDialogDrawingEnd: () -> Unit,
    onActivityLaunchTransitionStart: () -> Unit,
    onActivityLaunchTransitionEnd: () -> Unit,
): Expandable {
    val delegate = this
    return if (TransitionAnimator.dynamicTargetResolutionEnabled()) {
        /*
         * Create an object that implements a live, wrapping of the delegate's set when it is accessed.
         *
         * Instead of creating a static copy, this uses a **Lazy Proxy pattern**. It wraps the
         * underlying transition sources dynamically as they are accessed, ensuring that
         * any sources added to the original [Expandable] later are automatically intercepted.
         */
        val liveWrappingSet =
            object : AbstractMutableSet<TransitionSource>() {
                // This is the original set that we will be proxying.
                private val originalSet = delegate.transitionSources

                override val size: Int
                    get() = originalSet.size

                override fun iterator(): MutableIterator<TransitionSource> {
                    val originalIterator = originalSet.iterator()

                    return object : MutableIterator<TransitionSource> {
                        override fun hasNext(): Boolean = originalIterator.hasNext()

                        override fun next(): TransitionSource {
                            // Get the original source and wrap it with state-aware logic.
                            val originalSource = originalIterator.next()
                            return originalSource.stateAwareTransitionSource(
                                onDialogDrawingStart,
                                onDialogDrawingEnd,
                                onActivityLaunchTransitionStart,
                                onActivityLaunchTransitionEnd,
                            )
                        }

                        override fun remove() {
                            // Delegate the remove operation directly to the original iterator.
                            originalIterator.remove()
                        }
                    }
                }

                // Mutations should happen on the source set directly.
                override fun add(element: TransitionSource): Boolean {
                    throw UnsupportedOperationException(
                        "Cannot add to a state-aware expandable view. Mutate the original."
                    )
                }
            }
        Expandable(liveWrappingSet)
    } else {
        val transitionSources =
            delegate.transitionSources.mapTo(mutableSetOf()) { source ->
                source.stateAwareTransitionSource(
                    onDialogDrawingStart,
                    onDialogDrawingEnd,
                    onActivityLaunchTransitionStart,
                    onActivityLaunchTransitionEnd,
                )
            }
        Expandable(transitionSources)
    }
}
