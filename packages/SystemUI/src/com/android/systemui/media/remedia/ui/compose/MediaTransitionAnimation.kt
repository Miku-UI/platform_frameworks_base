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

package com.android.systemui.media.remedia.ui.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.android.compose.animation.scene.MutableSceneTransitionLayoutState
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.content.state.TransitionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest

internal suspend fun synchronizeMediaState(
    state: MutableSceneTransitionLayoutState,
    expansion: () -> Float,
    from: SceneKey = Media.Scenes.Compressed,
    to: SceneKey = Media.Scenes.Default,
) {
    coroutineScope {
        val animationScope = this

        var currentTransition: ExpansionTransition? = null

        fun snapTo(scene: SceneKey) {
            state.snapTo(scene)
            currentTransition = null
        }

        snapshotFlow { expansion() }
            .collectLatest { progress ->
                when (progress) {
                    0f -> snapTo(from)
                    1f -> snapTo(to)
                    else -> {
                        val transition = currentTransition
                        if (transition != null) {
                            transition.progress = progress
                            return@collectLatest
                        }

                        val newTransition =
                            ExpansionTransition(progress, from, to).also { currentTransition = it }
                        state.startTransitionImmediately(
                            animationScope = animationScope,
                            transition = newTransition,
                        )
                    }
                }
            }
    }
}

internal class ExpansionTransition(
    currentProgress: Float,
    private val from: SceneKey,
    to: SceneKey,
) : TransitionState.Transition.ChangeScene(fromScene = from, toScene = to) {
    override val currentScene: SceneKey
        get() = from

    override var progress: Float by mutableFloatStateOf(currentProgress)

    override val progressVelocity: Float
        get() = 0f

    override val isInitiatedByUserInput: Boolean
        get() = true

    override val isUserInputOngoing: Boolean
        get() = true

    private val finishCompletable = CompletableDeferred<Unit>()

    override suspend fun run() {
        // This transition runs until it is interrupted by another one.
        finishCompletable.await()
    }

    override fun freezeAndAnimateToCurrentState() {
        finishCompletable.complete(Unit)
    }
}
