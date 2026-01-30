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

package com.android.systemui.keyguard.ui.viewmodel

import android.annotation.ColorInt
import android.content.Context
import android.util.Log
import androidx.core.graphics.alpha
import com.android.app.animation.Interpolators.EMPHASIZED_ACCELERATE
import com.android.systemui.Flags
import com.android.systemui.bouncer.domain.interactor.PrimaryBouncerInteractor
import com.android.systemui.bouncer.ui.BouncerColors.surfaceColor
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.keyguard.domain.interactor.KeyguardDismissActionInteractor
import com.android.systemui.keyguard.shared.model.Edge
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.KeyguardState.GONE
import com.android.systemui.keyguard.shared.model.KeyguardState.PRIMARY_BOUNCER
import com.android.systemui.keyguard.shared.model.ScrimAlpha
import com.android.systemui.keyguard.ui.KeyguardTransitionAnimationFlow
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.shade.ui.ShadeColors.notificationScrim
import com.android.systemui.shade.ui.ShadeColors.shadePanel
import com.android.systemui.statusbar.SysuiStatusBarStateController
import com.android.systemui.window.domain.interactor.WindowRootViewBlurInteractor
import dagger.Lazy
import javax.inject.Inject
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** ALTERNATE and PRIMARY bouncers common animations */
class BouncerToGoneFlows
@Inject
constructor(
    @Application private val context: Context,
    private val statusBarStateController: SysuiStatusBarStateController,
    private val primaryBouncerInteractor: PrimaryBouncerInteractor,
    private val keyguardDismissActionInteractor: Lazy<KeyguardDismissActionInteractor>,
    private val shadeInteractor: ShadeInteractor,
    private val animationFlow: KeyguardTransitionAnimationFlow,
    private val windowRootViewBlurInteractor: WindowRootViewBlurInteractor,
) {
    /** Common fade for scrim alpha values during *BOUNCER->GONE */
    fun scrimAlpha(duration: Duration, fromState: KeyguardState): Flow<ScrimAlpha> {
        return if (SceneContainerFlag.isEnabled) {
            keyguardDismissActionInteractor
                .get()
                .willAnimateDismissActionOnLockscreen
                .flatMapLatest { createScrimAlphaFlow(duration, fromState) { it } }
        } else {
            createScrimAlphaFlow(
                duration,
                fromState,
                primaryBouncerInteractor::willRunDismissFromKeyguard,
            )
        }
    }

    /**
     * When the shade is expanded, make sure that all notifications can be seen immediately during a
     * transition to GONE. This matters especially when the user has chosen to not show
     * notifications on the lockscreen and then pulls down the shade, which presents them with an
     * immediate auth prompt, followed by a notification animation.
     */
    fun showAllNotifications(duration: Duration, from: KeyguardState): Flow<Boolean> {
        var leaveShadeOpen = false
        return animationFlow
            .setup(
                duration = duration,
                edge =
                    if (from == PRIMARY_BOUNCER) {
                        Edge.INVALID
                    } else {
                        Edge.create(from = from, to = Scenes.Gone)
                    },
            )
            .setupWithoutSceneContainer(edge = Edge.create(from = from, to = GONE))
            .sharedFlow(
                duration = duration,
                onStart = { leaveShadeOpen = statusBarStateController.leaveOpenOnKeyguardHide() },
                onStep = { if (leaveShadeOpen) 1f else 0f },
                onFinish = { 0f },
                onCancel = { 0f },
            )
            .map { it == 1f }
            .distinctUntilChanged()
    }

    private fun createScrimAlphaFlow(
        duration: Duration,
        fromState: KeyguardState,
        willRunAnimationOnKeyguard: () -> Boolean,
    ): Flow<ScrimAlpha> {
        var startState = StartState()
        val transitionAnimation =
            animationFlow
                .setup(
                    duration = duration,
                    edge =
                        if (fromState == PRIMARY_BOUNCER) {
                            Edge.INVALID
                        } else {
                            Edge.create(from = fromState, to = Scenes.Gone)
                        },
                )
                .setupWithoutSceneContainer(edge = Edge.create(from = fromState, to = GONE))

        return shadeInteractor.anyExpansion
            .map { it > 0f }
            .distinctUntilChanged()
            .flatMapLatest { isAnyExpanded ->
                transitionAnimation
                    .sharedFlow(
                        duration = duration,
                        interpolator = EMPHASIZED_ACCELERATE,
                        onStart = {
                            startState =
                                toStartState(
                                    leaveShadeOpen =
                                        statusBarStateController.leaveOpenOnKeyguardHide(),
                                    willRunDismissFromKeyguard = willRunAnimationOnKeyguard(),
                                    isShadeExpanded = isAnyExpanded,
                                )
                            Log.d(TAG, "onStart BouncerToGone with $startState")
                        },
                        onStep = { it },
                    )
                    .map {
                        if (Flags.bouncerUiRevamp() || Flags.notificationShadeBlur()) {
                            mapToScrimAlphasWithCustomMaxAlphas(
                                transitionProgress = it,
                                startState = startState,
                            )
                        } else {
                            mapToScrimAlphas(transitionProgress = it, startState = startState)
                        }
                    }
            }
    }

    private fun mapToScrimAlphasWithCustomMaxAlphas(
        transitionProgress: Float,
        startState: StartState,
    ): ScrimAlpha {
        // convert to a value that goes from 1 -> 0 and scale down the max allowed alpha with it.
        val invertedTransitionProgress = 1 - transitionProgress
        return with(startState) {
            when {
                willRunDismissFromKeyguard && isShadeExpanded ->
                    ScrimAlpha(
                        behindAlpha = shadeBehindAlpha * invertedTransitionProgress,
                        notificationsAlpha = shadeNotifAlpha * invertedTransitionProgress,
                    )
                willRunDismissFromKeyguard && !isShadeExpanded -> ScrimAlpha()
                leaveShadeOpen ->
                    ScrimAlpha(behindAlpha = shadeBehindAlpha, notificationsAlpha = shadeNotifAlpha)
                else -> ScrimAlpha(behindAlpha = bouncerBehindAlpha * invertedTransitionProgress)
            }
        }
    }

    private fun toStartState(
        willRunDismissFromKeyguard: Boolean,
        isShadeExpanded: Boolean,
        leaveShadeOpen: Boolean,
    ): StartState {
        val isBlurCurrentlySupported = windowRootViewBlurInteractor.isBlurCurrentlySupported.value
        val defaultValue =
            StartState(
                willRunDismissFromKeyguard = willRunDismissFromKeyguard,
                isShadeExpanded = isShadeExpanded,
                leaveShadeOpen = leaveShadeOpen,
                shadeNotifAlpha = 1.0f,
                shadeBehindAlpha = 1.0f,
                bouncerBehindAlpha = 1.0f,
            )
        val shadeNotifAlpha = colorAlpha(notificationScrim(context, isBlurCurrentlySupported))
        val shadeBehindAlpha =
            colorAlpha(
                shadePanel(
                    context = context,
                    blurSupported = isBlurCurrentlySupported,
                    withScrim = true,
                )
            )
        val bouncerBehindAlpha = colorAlpha(context.surfaceColor(isBlurCurrentlySupported))
        return when {
            Flags.bouncerUiRevamp() && Flags.notificationShadeBlur() -> {
                defaultValue.copy(
                    shadeBehindAlpha = shadeBehindAlpha,
                    shadeNotifAlpha = shadeNotifAlpha,
                    bouncerBehindAlpha = bouncerBehindAlpha,
                )
            }
            Flags.bouncerUiRevamp() && !Flags.notificationShadeBlur() -> {
                defaultValue.copy(bouncerBehindAlpha = bouncerBehindAlpha)
            }
            !Flags.bouncerUiRevamp() && Flags.notificationShadeBlur() -> {
                defaultValue.copy(
                    shadeBehindAlpha = shadeBehindAlpha,
                    shadeNotifAlpha = shadeNotifAlpha,
                )
            }
            else -> defaultValue
        }
    }

    private fun colorAlpha(@ColorInt colorInt: Int): Float = colorInt.alpha / 255.0f

    private fun mapToScrimAlphas(transitionProgress: Float, startState: StartState): ScrimAlpha {
        val willRunDismissFromKeyguard = startState.willRunDismissFromKeyguard
        val isShadeExpanded: Boolean = startState.isShadeExpanded
        val leaveShadeOpen: Boolean = startState.leaveShadeOpen
        val invertedTransitionProgress = 1 - transitionProgress
        return if (willRunDismissFromKeyguard) {
            if (isShadeExpanded) {
                ScrimAlpha(
                    behindAlpha = invertedTransitionProgress,
                    notificationsAlpha = invertedTransitionProgress,
                )
            } else {
                ScrimAlpha()
            }
        } else if (leaveShadeOpen) {
            ScrimAlpha(behindAlpha = 1f, notificationsAlpha = 1f)
        } else {
            ScrimAlpha(behindAlpha = invertedTransitionProgress)
        }
    }

    private data class StartState(
        val isShadeExpanded: Boolean = false,
        val leaveShadeOpen: Boolean = false,
        val willRunDismissFromKeyguard: Boolean = false,
        val isBlurCurrentlySupported: Boolean = false,
        val shadeNotifAlpha: Float = 1.0f,
        val shadeBehindAlpha: Float = 1.0f,
        val bouncerBehindAlpha: Float = 1.0f,
    )

    private companion object {
        const val TAG = "BouncerToGoneFlows"
    }
}
