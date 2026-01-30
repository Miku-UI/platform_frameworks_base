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

import android.util.MathUtils
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.domain.interactor.FromDozingTransitionInteractor
import com.android.systemui.keyguard.shared.model.Edge
import com.android.systemui.keyguard.shared.model.KeyguardState.DOZING
import com.android.systemui.keyguard.shared.model.KeyguardState.LOCKSCREEN
import com.android.systemui.keyguard.ui.KeyguardTransitionAnimationFlow
import com.android.systemui.keyguard.ui.transitions.DeviceEntryIconTransition
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Breaks down DOZING->LOCKSCREEN transition into discrete steps for corresponding views to consume.
 */
@SysUISingleton
class DozingToLockscreenTransitionViewModel
@Inject
constructor(
    animationFlow: KeyguardTransitionAnimationFlow,
    dozingTransitionFlows: DozingTransitionFlows,
) : DeviceEntryIconTransition {
    private val transitionAnimation =
        animationFlow.setup(
            duration = FromDozingTransitionInteractor.TO_LOCKSCREEN_DURATION,
            edge = Edge.create(from = DOZING, to = LOCKSCREEN),
        )

    val shortcutsAlpha: Flow<Float> =
        transitionAnimation.sharedFlow(
            duration = 150.milliseconds,
            onStep = { it },
            onCancel = { 0f },
        )

    // Show immediately to avoid what can appear to be a flicker on device wakeup
    @Deprecated("Use lockscreenAlpha(ViewStateAccessor) function instead")
    val lockscreenAlpha: Flow<Float> = transitionAnimation.immediatelyTransitionTo(1f)

    fun lockscreenAlpha(viewState: ViewStateAccessor): Flow<Float> {
        var startAlpha = 1f
        return transitionAnimation.sharedFlow(
            duration = FromDozingTransitionInteractor.TO_LOCKSCREEN_DURATION,
            onStart = { startAlpha = viewState.alpha() },
            onStep = { MathUtils.lerp(startAlpha, 1f, it) },
        )
    }

    val nonAuthUIAlpha: Flow<Float> =
        dozingTransitionFlows.wasHiddenAuthUIShowingWhileDozing.flatMapLatest { uiShowing ->
            if (uiShowing) { // already showing; no need to update alpha
                emptyFlow()
            } else { // fade in
                transitionAnimation.sharedFlow(
                    duration = 150.milliseconds,
                    onStep = { it },
                    onFinish = { 1f },
                    onCancel = { 1f },
                )
            }
        }

    val clockDozeAmount: Flow<Float> =
        dozingTransitionFlows.wasHiddenAuthUIShowingWhileDozing.flatMapLatest { uiShowing ->
            if (uiShowing) {
                transitionAnimation.sharedFlow(
                    onStep = { 1f - it },
                    onFinish = { 0f },
                    onCancel = { 0f },
                )
            } else {
                transitionAnimation.immediatelyTransitionTo(0f)
            }
        }

    val deviceEntryBackgroundViewAlpha: Flow<Float> =
        transitionAnimation.immediatelyTransitionTo(1f)

    override val deviceEntryParentViewAlpha: Flow<Float> =
        transitionAnimation.immediatelyTransitionTo(1f)
}
