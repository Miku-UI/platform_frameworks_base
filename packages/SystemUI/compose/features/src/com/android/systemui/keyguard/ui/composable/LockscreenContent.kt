/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.keyguard.ui.composable

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import com.android.compose.animation.scene.ContentScope
import com.android.internal.jank.Cuj
import com.android.internal.jank.Cuj.CujType
import com.android.internal.jank.InteractionJankMonitor
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.keyguard.domain.interactor.KeyguardClockInteractor
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.transition.KeyguardTransitionAnimationCallback
import com.android.systemui.keyguard.ui.composable.blueprint.ComposableLockscreenSceneBlueprint
import com.android.systemui.keyguard.ui.viewmodel.LockscreenBehindScrimViewModel
import com.android.systemui.keyguard.ui.viewmodel.LockscreenContentViewModel
import com.android.systemui.keyguard.ui.viewmodel.LockscreenFrontScrimViewModel
import com.android.systemui.keyguard.ui.viewmodel.ViewStateAccessor
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys
import kotlin.math.min

/**
 * Renders the content of the lockscreen.
 *
 * This is separate from the [LockscreenScene] because it's meant to support usage of this UI from
 * outside the scene container framework.
 */
class LockscreenContent(
    private val viewModelFactory: LockscreenContentViewModel.Factory,
    private val lockscreenFrontScrimViewModelFactory: LockscreenFrontScrimViewModel.Factory,
    private val lockscreenBehindScrimViewModelFactory: LockscreenBehindScrimViewModel.Factory,
    private val blueprints: Set<@JvmSuppressWildcards ComposableLockscreenSceneBlueprint>,
    private val clockInteractor: KeyguardClockInteractor,
    private val interactionJankMonitor: InteractionJankMonitor,
) {
    private val blueprintByBlueprintId: Map<String, ComposableLockscreenSceneBlueprint> by lazy {
        blueprints.associateBy { it.id }
    }

    @Composable
    fun ContentScope.Content(modifier: Modifier = Modifier) {
        val view = LocalView.current
        var lockscreenAlpha by remember { mutableFloatStateOf(0f) }
        val viewModel =
            rememberViewModel("LockscreenContent-viewModel") {
                viewModelFactory.create(
                    keyguardTransitionAnimationCallback =
                        KeyguardTransitionAnimationCallbackImpl(view, interactionJankMonitor),
                    viewState = ViewStateAccessor(alpha = { lockscreenAlpha }),
                )
            }

        LaunchedEffect(viewModel.alpha) { lockscreenAlpha = viewModel.alpha }
        val lockscreenFrontScrimViewModel =
            rememberViewModel("LockscreenContent-frontScrimViewModel") {
                lockscreenFrontScrimViewModelFactory.create()
            }
        val lockscreenBehindScrimViewModel =
            rememberViewModel("LockscreenContent-behindScrimViewModel") {
                lockscreenBehindScrimViewModelFactory.create()
            }

        /**
         * Important: Make sure that [LockscreenContentViewModel.shouldContentFadeIn] is checked the
         * first time the Lockscreen scene is composed.
         */
        val useFadeInOnComposition = remember {
            layoutState.currentTransition?.let { currentTransition ->
                viewModel.shouldContentFadeIn(currentTransition)
            } ?: false
        }

        // Alpha for the animation when transitioning from Shade scene to Lockscreen Scene and
        // ending user input, at which point the content fades in, visually completing the
        // transition.
        val contentAlphaAnimatable = remember { Animatable(0f) }
        LaunchedEffect(contentAlphaAnimatable) {
            snapshotFlow { contentAlphaAnimatable.value }
                .collect {
                    // Pipe the content alpha animation progress to the view model, so NSSL can
                    // fade-in the stack in tandem.
                    viewModel.setContentAlphaForLockscreenFadeIn(it)
                }
        }

        LaunchedEffect(
            contentAlphaAnimatable,
            layoutState.currentTransition,
            useFadeInOnComposition,
        ) {
            val currentTransition = layoutState.currentTransition
            when {
                useFadeInOnComposition &&
                    currentTransition != null &&
                    viewModel.shouldContentFadeIn(currentTransition) &&
                    currentTransition.isUserInputOngoing -> {

                    // Keep the content invisible until user lifts their finger.
                    contentAlphaAnimatable.snapTo(0f)
                }

                useFadeInOnComposition &&
                    (currentTransition == null ||
                        (viewModel.shouldContentFadeIn(currentTransition) &&
                            !currentTransition.isUserInputOngoing)) -> {
                    // Animate the content fade in.
                    contentAlphaAnimatable.animateTo(1f, tween())
                }

                else -> {
                    // Disable the content fade in logic.
                    contentAlphaAnimatable.snapTo(1f)
                }
            }
        }

        // Ensure clock events are connected. This is a no-op if they are already registered.
        clockInteractor.clockEventController.registerListeners()

        DisposableEffect(view) {
            val handle = clockInteractor.clockEventController.bind(view)
            onDispose { handle.dispose() }
        }

        val blueprint = blueprintByBlueprintId[viewModel.blueprintId] ?: return
        with(blueprint) {
            LockscreenBehindScrim(
                lockscreenBehindScrimViewModel,
                Modifier.element(LockscreenElementKeys.BehindScrim),
            )
            Content(
                viewModel,
                modifier
                    .sysuiResTag("keyguard_root_view")
                    .element(LockscreenElementKeys.Root)
                    .graphicsLayer { alpha = min(viewModel.alpha, contentAlphaAnimatable.value) },
            )
            LockscreenFrontScrim(lockscreenFrontScrimViewModel)
        }
    }
}

private class KeyguardTransitionAnimationCallbackImpl(
    private val view: View,
    private val interactionJankMonitor: InteractionJankMonitor,
) : KeyguardTransitionAnimationCallback {

    override fun onAnimationStarted(from: KeyguardState, to: KeyguardState) {
        cujOrNull(from, to)?.let { cuj -> interactionJankMonitor.begin(view, cuj) }
    }

    override fun onAnimationEnded(from: KeyguardState, to: KeyguardState) {
        cujOrNull(from, to)?.let { cuj -> interactionJankMonitor.end(cuj) }
    }

    override fun onAnimationCanceled(from: KeyguardState, to: KeyguardState) {
        cujOrNull(from, to)?.let { cuj -> interactionJankMonitor.cancel(cuj) }
    }

    @CujType
    private fun cujOrNull(from: KeyguardState, to: KeyguardState): Int? {
        return when {
            from == KeyguardState.AOD -> Cuj.CUJ_LOCKSCREEN_TRANSITION_FROM_AOD
            to == KeyguardState.AOD -> Cuj.CUJ_LOCKSCREEN_TRANSITION_TO_AOD
            else -> null
        }
    }
}
