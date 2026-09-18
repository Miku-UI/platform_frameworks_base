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

package com.android.systemui.scene.data.repository

import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.OverlayKey
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.content.state.TransitionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

fun ObservableTransitionState.toTransitionState(): TransitionState {
    return when (this) {
        is ObservableTransitionState.Idle -> TransitionState.Idle(currentScene, currentOverlays)
        is ObservableTransitionState.Transition -> {
            when (this) {
                is ObservableTransitionState.Transition.ChangeScene ->
                    object :
                            TransitionState.Transition.ChangeScene(
                                fromScene = fromScene,
                                toScene = toScene,
                            ) {
                            override val currentScene: SceneKey
                                get() = runBlocking { this@toTransitionState.currentScene.first() }

                            override val progress: Float
                                get() = runBlocking { this@toTransitionState.progress.first() }

                            override val progressVelocity: Float = 0f

                            override val isInitiatedByUserInput: Boolean
                                get() = this@toTransitionState.isInitiatedByUserInput

                            override val isUserInputOngoing: Boolean
                                get() = runBlocking {
                                    this@toTransitionState.isUserInputOngoing.first()
                                }

                            override suspend fun run() {}

                            override fun freezeAndAnimateToCurrentState() {}
                        }
                        .also { it.currentOverlaysWhenTransitionStarted = currentOverlays }

                is ObservableTransitionState.Transition.ShowOrHideOverlay ->
                    object :
                            TransitionState.Transition.ShowOrHideOverlay(
                                fromContent = fromContent,
                                toContent = toContent,
                                overlay = overlay,
                                fromOrToScene =
                                    (if (fromContent is SceneKey) fromContent else toContent)
                                        as SceneKey,
                            ) {
                            override val progress: Float
                                get() = runBlocking { this@toTransitionState.progress.first() }

                            override val progressVelocity: Float = 0f

                            override val isInitiatedByUserInput: Boolean
                                get() = this@toTransitionState.isInitiatedByUserInput

                            override val isUserInputOngoing: Boolean
                                get() = runBlocking {
                                    this@toTransitionState.isUserInputOngoing.first()
                                }

                            override suspend fun run() {}

                            override fun freezeAndAnimateToCurrentState() {}

                            override val isEffectivelyShown: Boolean
                                get() = true
                        }
                        .also {
                            it.currentOverlaysWhenTransitionStarted = runBlocking {
                                currentOverlays.first()
                            }
                            it.currentSceneWhenTransitionStarted = runBlocking { currentScene }
                        }

                is ObservableTransitionState.Transition.ReplaceOverlay ->
                    object :
                            TransitionState.Transition.ReplaceOverlay(
                                fromOverlay = fromOverlay,
                                toOverlay = toOverlay,
                            ) {
                            override val progress: Float
                                get() = runBlocking { this@toTransitionState.progress.first() }

                            override val progressVelocity: Float = 0f

                            override val isInitiatedByUserInput: Boolean
                                get() = this@toTransitionState.isInitiatedByUserInput

                            override val isUserInputOngoing: Boolean
                                get() = runBlocking {
                                    this@toTransitionState.isUserInputOngoing.first()
                                }

                            override suspend fun run() {}

                            override fun freezeAndAnimateToCurrentState() {}

                            override val effectivelyShownOverlay: OverlayKey
                                get() = fromOverlay
                        }
                        .also {
                            it.currentOverlaysWhenTransitionStarted = runBlocking {
                                currentOverlays.first()
                            }
                            it.currentSceneWhenTransitionStarted = runBlocking { currentScene }
                        }
            }
        }
    }
}
