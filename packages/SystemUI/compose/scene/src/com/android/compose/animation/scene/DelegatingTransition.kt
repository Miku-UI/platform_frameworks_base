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

package com.android.compose.animation.scene

import androidx.compose.runtime.snapshotFlow
import com.android.compose.animation.scene.content.state.TransitionState
import kotlinx.coroutines.flow.first

/**
 * Defines interface for classes that can be a transition that is, itself, delegating to another
 * transition. The effect is that the given transition "tracks" the other transition which means
 * both transitions run in sync with each other.
 */
sealed interface DelegatingTransition {

    /**
     * A [TransitionState.Transition.ShowOrHideOverlay] that tracks another
     * [TransitionState.Transition.ShowOrHideOverlay]. The [fromOrToScene] is the scene that is
     * present below the [overlay] that is being shown (if [isShowing] is `true`) or hidden (if it's
     * `false`).
     */
    class ShowOrHideOverlay(
        private val delegate: ShowOrHideOverlay,
        fromOrToScene: SceneKey = delegate.fromOrToScene,
        overlay: OverlayKey = delegate.overlay,
    ) :
        DelegatingTransition,
        TransitionState.Transition.ShowOrHideOverlay(
            overlay = overlay,
            fromOrToScene = fromOrToScene,
            fromContent = if (delegate.overlay == delegate.fromContent) overlay else fromOrToScene,
            toContent = if (delegate.overlay == delegate.toContent) overlay else fromOrToScene,
        ) {

        override val isEffectivelyShown: Boolean
            get() = delegate.isEffectivelyShown

        override val progress: Float
            get() = delegate.progress

        override val progressVelocity: Float
            get() = delegate.progressVelocity

        override val isInitiatedByUserInput: Boolean
            get() = delegate.isInitiatedByUserInput

        override val isUserInputOngoing: Boolean
            get() = delegate.isUserInputOngoing

        override suspend fun run() {
            // TODO(b/466079250): replace this snapshotFlow with something like delegate.await().
            snapshotFlow { delegate.isProgressStable }.first { it }
        }

        override fun freezeAndAnimateToCurrentState() {
            delegate.freezeAndAnimateToCurrentState()
        }

        override fun toString(): String {
            return "Delegating${super.toString()}"
        }
    }
}
