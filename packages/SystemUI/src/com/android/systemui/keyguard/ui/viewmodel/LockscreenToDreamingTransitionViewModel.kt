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

import com.android.app.animation.Interpolators.EMPHASIZED_ACCELERATE
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.domain.interactor.FromLockscreenTransitionInteractor.Companion.TO_DREAMING_DURATION
import com.android.systemui.keyguard.shared.model.Edge
import com.android.systemui.keyguard.shared.model.KeyguardState.DREAMING
import com.android.systemui.keyguard.shared.model.KeyguardState.LOCKSCREEN
import com.android.systemui.keyguard.shared.model.ScrimAlpha
import com.android.systemui.keyguard.ui.KeyguardTransitionAnimationFlow
import com.android.systemui.keyguard.ui.transitions.DeviceEntryIconTransition
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.statusbar.phone.ScrimState
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Breaks down LOCKSCREEN->DREAMING transition into discrete steps for corresponding views to
 * consume.
 */
@SysUISingleton
class LockscreenToDreamingTransitionViewModel
@Inject
constructor(animationFlow: KeyguardTransitionAnimationFlow) : DeviceEntryIconTransition {
    private val transitionAnimation =
        animationFlow
            .setup(
                duration = TO_DREAMING_DURATION,
                edge = Edge.create(from = LOCKSCREEN, to = Scenes.Dream),
            )
            .setupWithoutSceneContainer(Edge.create(from = LOCKSCREEN, to = DREAMING))

    /** Lockscreen views y-translation */
    fun lockscreenTranslationY(translatePx: Int): Flow<Float> {
        return transitionAnimation.sharedFlow(
            duration = 500.milliseconds,
            onStep = { it * translatePx },
            // Reset on cancel or finish
            onFinish = { 0f },
            onCancel = { 0f },
            interpolator = EMPHASIZED_ACCELERATE,
        )
    }

    /** Lockscreen views alpha */
    val lockscreenAlpha: Flow<Float> =
        transitionAnimation.sharedFlowWithShade(
            duration = 250.milliseconds,
            onStep = { step, isShadeExpanded -> if (isShadeExpanded) 0f else 1f - step },
        )

    val shortcutsAlpha: Flow<Float> =
        transitionAnimation.sharedFlowWithShade(
            duration = 250.milliseconds,
            onStep = { step, isShadeExpanded -> if (isShadeExpanded) 0f else 1f - step },
            onFinish = { 0f },
            onCancel = { 1f },
        )

    override val deviceEntryParentViewAlpha: Flow<Float> =
        transitionAnimation.sharedFlowWithShade(
            duration = 250.milliseconds,
            onStep = { step, isShadeExpanded -> if (isShadeExpanded) 0f else 1f - step },
            onCancel = { 0f },
        )

    // Fade out behind scrim as it's on top of the dream.
    val scrimAlpha: Flow<ScrimAlpha> =
        transitionAnimation
            .sharedFlowWithShade(
                duration = 250.milliseconds,
                onStep = { step, isShadeExpanded ->
                    (1 - step) *
                        (if (isShadeExpanded) ScrimState.SHADE_LOCKED.behindAlpha
                        else ScrimState.KEYGUARD.behindAlpha)
                },
                onFinish = { 0f },
                // Shade is closed by ACTION_CLOSE_SYSTEM_DIALOGS when dream starts, so we'll be
                // returning to the keyguard with no shade open.
                onCancel = { ScrimState.KEYGUARD.behindAlpha },
            )
            .map { ScrimAlpha(behindAlpha = it) }

    val statusBarAlpha: Flow<Float> = lockscreenAlpha

    companion object {
        @JvmField val DREAMING_ANIMATION_DURATION_MS = TO_DREAMING_DURATION.inWholeMilliseconds
    }
}
