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

package com.android.systemui.scene.shared.model

import com.android.compose.animation.scene.OverlayKey
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.TransitionKey
import com.android.compose.animation.scene.content.state.TransitionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NoOpSceneDataSource(initialSceneKey: SceneKey) : SceneDataSource {
    override val currentScene: StateFlow<SceneKey> = MutableStateFlow(initialSceneKey).asStateFlow()

    override val transitionState: TransitionState = TransitionState.Idle(initialSceneKey)

    override val currentOverlays: StateFlow<Set<OverlayKey>> =
        MutableStateFlow(emptySet<OverlayKey>()).asStateFlow()

    override fun changeScene(toScene: SceneKey, transitionKey: TransitionKey?) = Unit

    override fun showOverlay(overlay: OverlayKey, transitionKey: TransitionKey?) = Unit

    override fun hideOverlay(overlay: OverlayKey, transitionKey: TransitionKey?) = Unit

    override fun replaceOverlay(from: OverlayKey, to: OverlayKey, transitionKey: TransitionKey?) =
        Unit

    override fun freezeAndAnimateToCurrentState() = Unit

    override fun instantlyTransitionTo(scene: SceneKey?, overlays: Set<OverlayKey>?) = Unit

    override fun startTransitionImmediately(transition: TransitionState.Transition) = Unit
}
