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

package com.android.compose.animation.scene

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.SpringSpec
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.withFrameNanos
import com.android.compose.animation.scene.content.state.TransitionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun CoroutineScope.animateContent(
    layoutState: MutableSceneTransitionLayoutStateImpl,
    transition: TransitionState.Transition,
    oneOffAnimation: OneOffAnimation,
    targetProgress: Float,
    chain: Boolean = true,
): Job {
    fun animationSpec(): AnimationSpec<Float> {
        return transition.transformationSpec.progressSpec
            ?: layoutState.motionScheme.defaultSpatialSpec()
    }

    oneOffAnimation.animatableFactory = {
        // Animate the progress to its target value.
        val visibilityThreshold =
            (animationSpec() as? SpringSpec)?.visibilityThreshold ?: ProgressVisibilityThreshold
        val replacedTransition = transition.replacedTransition
        val initialProgress = replacedTransition?.progress ?: 0f
        Animatable(initialProgress, visibilityThreshold = visibilityThreshold)
    }

    oneOffAnimation.onRun = {
        val replacedTransition = transition.replacedTransition
        val initialVelocity = replacedTransition?.progressVelocity ?: 0f

        if (layoutState.deferTransitionProgress) {
            // Defer the animation by one frame so that the transition progress is changed only when
            // the expensive first composition frame is done.
            withFrameNanos {}
        }
        oneOffAnimation.animatable.animateTo(targetProgress, animationSpec(), initialVelocity)
    }

    return layoutState.startTransitionImmediately(animationScope = this, transition, chain)
}

internal class OneOffAnimation {
    /**
     * The animatable used to animate this transition.
     *
     * Note: This is lateinit because we need to first create this object so that
     * [SceneTransitionLayoutState] can compute the transformations and animation spec associated to
     * the transition, which is needed to initialize this Animatable and happens right before
     * [onTransitionPrepared] is called.
     */
    lateinit var animatableFactory: () -> Animatable<Float, AnimationVector1D>
    lateinit var animatable: Animatable<Float, AnimationVector1D>
        private set

    /** The runnable to run for this animation. */
    lateinit var onRun: suspend () -> Unit

    val progress: Float
        get() = animatable.value

    val progressVelocity: Float
        get() = animatable.velocity

    fun onTransitionPrepared() {
        animatable = animatableFactory()
    }

    suspend fun run() {
        onRun()
    }

    fun freezeAndAnimateToCurrentState() {
        // Do nothing, the state of one-off animations never change and we directly animate to it.
    }
}

// TODO(b/290184746): Compute a good default visibility threshold that depends on the layout size
// and screen density.
internal const val ProgressVisibilityThreshold = 1e-3f
