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

package com.android.systemui.scene.ui.view

import android.util.Log
import com.android.compose.animation.scene.DelegatingTransition
import com.android.compose.animation.scene.HoistedSceneTransitionLayoutState
import com.android.compose.animation.scene.SceneTransitionLayout
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.systemui.bouncer.ui.composable.BouncerSceneContainer
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.ui.composable.SceneContainer
import com.android.systemui.scene.ui.viewmodel.SceneContainerViewModel
import kotlinx.coroutines.CoroutineScope

/**
 * Coordinates synchronization between the primary [SceneContainer] and the dedicated
 * [BouncerSceneContainer].
 *
 * The Bouncer is rendered in a separate [SceneTransitionLayout] (STL) to ensure it appears above
 * other UI elements (like notifications) while still participating in the main scene transition
 * system. This coordinator ensures that:
 * 1. Gestures started on the Bouncer container are propagated to the main container.
 * 2. Transitions started on the main container involving the Bouncer are mirrored in the Bouncer
 *    container.
 * 3. Snap events are synchronized to keep the "Bouncer showing" state consistent across both STLs.
 */
class BouncerSceneTransitionCoordinator(private val viewModel: SceneContainerViewModel) {
    /** The state for the dedicated Bouncer STL. */
    val bouncerSceneContainerState =
        HoistedSceneTransitionLayoutState(
            initialScene = Scenes.Gone,
            onTransitionStart = { transition ->
                // Bridge: Gesture started on the Bouncer container -> Propagate to Main container.
                // This allows the main container (and its interactor) to track the transition
                // state.
                if (
                    transition is TransitionState.Transition.ShowOrHideOverlay &&
                        transition.isTransitioning(from = Overlays.Bouncer)
                ) {
                    viewModel.startTransitionImmediately(
                        DelegatingTransition.ShowOrHideOverlay(
                            delegate = transition,
                            fromOrToScene = viewModel.currentScene,
                            overlay = Overlays.Bouncer,
                        )
                    )
                }
            },
            deferTransitionProgress = true,
        )

    /**
     * Bridges transition starts from the main container to the Bouncer container.
     *
     * This is called when the main STL starts a transition that involves the Bouncer overlay. We
     * mirror this transition in the Bouncer STL so its animations run in sync.
     */
    fun onMainContainerTransitionStart(
        transition: TransitionState.Transition,
        animationScope: CoroutineScope,
    ) {
        if (
            transition is TransitionState.Transition.ShowOrHideOverlay &&
                transition !is DelegatingTransition &&
                transition.isTransitioningFromOrTo(Overlays.Bouncer)
        ) {
            bouncerSceneContainerState.uiBoundState?.startTransitionImmediately(
                animationScope = animationScope,
                transition =
                    DelegatingTransition.ShowOrHideOverlay(
                        delegate = transition,
                        fromOrToScene = bouncerSceneContainerState.currentScene,
                        overlay = Overlays.Bouncer,
                    ),
            ) ?: Log.w(TAG, "onMainContainerTransitionStart: Bouncer uiBoundState is null")
        }
    }

    /**
     * Bridges snap events from the main container to the Bouncer container.
     *
     * Ensures that the Bouncer STL "snaps" to the same visibility state as the main STL when a
     * transition ends or is interrupted.
     */
    fun onMainContainerSnap(isBouncerShowing: Boolean) {
        val isBouncerCurrentlyShowing =
            bouncerSceneContainerState.currentOverlays.contains(Overlays.Bouncer)
        if (isBouncerShowing != isBouncerCurrentlyShowing) {
            bouncerSceneContainerState.uiBoundState?.snapTo(
                overlays =
                    if (isBouncerShowing) {
                        setOf(Overlays.Bouncer)
                    } else {
                        emptySet()
                    }
            ) ?: Log.w(TAG, "onMainContainerSnap: Bouncer uiBoundState is null")
        }
    }

    companion object {
        private const val TAG = "BouncerSceneTransitionCoordinator"
    }
}
