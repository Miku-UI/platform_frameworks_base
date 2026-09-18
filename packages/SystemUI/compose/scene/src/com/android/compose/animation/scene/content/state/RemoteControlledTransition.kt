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

package com.android.compose.animation.scene.content.state

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.compose.animation.scene.SceneKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Signature for coroutine controlling the progress of a transition.
 *
 * While active, it's the coroutines responsibility to continuously update the
 * [RemoteControlScope.progress]. The transition will end when the coroutine ends; the returned
 * boolean indicates whether to commit the scene transition, or - when `false` - to revert back to
 * start scene.
 *
 * For convenience, the progress is animated to 1f or 0f respectively, if needed.
 *
 * The coroutine will be cancelled if the transition is interrupted before completion.
 *
 * @return true if the transition should be committed, false otherwise.
 */
typealias RemoteControlFn = suspend RemoteControlScope.() -> Boolean

/** Scope for [RemoteControlFn]. */
interface RemoteControlScope {
    /**
     * The progress of the transition. This is usually in the `[0; 1]` range, but can set to values
     * outside that range, typically for overshots. *
     */
    var progress: Float
}

/**
 * Creates a [TransitionState.Transition.ChangeScene] transition, which can be controlled manually.
 *
 * The [controlFn] defines the duration and progress of the transition: While active, the coroutine
 * updates the [RemoteControlScope.progress] continuously,
 *
 * The returned transition can be used *once* in connection with [MutableSceneTransitionLayoutState]
 * [startTransition] or [startTransitionImmediately].
 *
 * @param isInitiatedByUserInput Whether this transition is controlled by a users gesture.
 * @param initialProgress The initial progress of the transition.
 * @param controlFn The coroutine to provide the transition progress.
 */
fun TransitionState.Companion.createRemoteControlledSceneTransition(
    fromScene: SceneKey,
    toScene: SceneKey,
    isInitiatedByUserInput: Boolean = true,
    initialProgress: Float = 0f,
    controlFn: RemoteControlFn,
): TransitionState.Transition.ChangeScene {
    return RemoteControllableTransition(
        fromScene,
        toScene,
        initialProgress,
        isInitiatedByUserInput,
        controlFn,
    )
}

private class RemoteControllableTransition(
    fromScene: SceneKey,
    toScene: SceneKey,
    initialProgress: Float,
    override val isInitiatedByUserInput: Boolean = true,
    private val controlFn: RemoteControlFn,
) : TransitionState.Transition.ChangeScene(fromScene, toScene), RemoteControlScope {

    private var controlJob: Job? = null

    override var currentScene by mutableStateOf(fromScene)
        private set

    override var progress: Float by mutableFloatStateOf(initialProgress)

    override val progressVelocity: Float
        // TODO: b/462697242 - keep track of the progress velocity
        get() = 0f

    override var isUserInputOngoing by mutableStateOf(isInitiatedByUserInput)

    override suspend fun run() = coroutineScope {
        check(controlJob == null) { "Transition cannot be started twice" }
        val deferredCommit = async { controlFn() }.also { controlJob = it }

        var targetProgress = 0f
        try {
            if (deferredCommit.await()) {
                targetProgress = 1f
                currentScene = toScene
            }
        } finally {
            isUserInputOngoing = false
            // TODO: b/417444347 - use the correct spring spec.
            Animatable(progress).animateTo(targetProgress, initialVelocity = progressVelocity) {
                progress = value
            }
        }
    }

    override fun freezeAndAnimateToCurrentState() {
        controlJob?.cancel()
    }
}
