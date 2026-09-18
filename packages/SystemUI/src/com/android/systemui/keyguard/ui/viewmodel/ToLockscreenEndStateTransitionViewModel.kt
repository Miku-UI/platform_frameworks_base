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

package com.android.systemui.keyguard.ui.viewmodel

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.shared.model.Edge
import com.android.systemui.keyguard.shared.model.KeyguardState.LOCKSCREEN
import com.android.systemui.keyguard.ui.KeyguardTransitionAnimationFlow
import com.android.systemui.keyguard.ui.transitions.DeviceEntryIconTransition
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf

@SysUISingleton
/**
 * This transition is not tied to a specific "from" state. It's used to ensure that all views are in
 * the correct final state when the [LOCKSCREEN] state is settled, acting as a fallback to
 * explicitly set all lockscreen-related view properties to their end values.
 */
class ToLockscreenEndStateTransitionViewModel
@Inject
constructor(animationFlow: KeyguardTransitionAnimationFlow) : DeviceEntryIconTransition {
    private val transitionAnimation =
        animationFlow
            .setup(duration = DEFAULT_DURATION, edge = Edge.create(to = LOCKSCREEN))
            .setupWithoutSceneContainer(edge = Edge.create(to = LOCKSCREEN))

    private fun transitionToFinalValue(onFinishValue: Float): Flow<Float> =
        if (SceneContainerFlag.isEnabled) {
                transitionAnimation.sharedFlow(
                    duration = DEFAULT_DURATION,
                    onStep = { null },
                    onFinish = { onFinishValue },
                    onCancel = { onFinishValue },
                )
            } else {
                flowOf(null)
            }
            .filterNotNull()

    val lockscreenAlpha = transitionToFinalValue(1f)
    val shortcutsAlpha = transitionToFinalValue(1f)
    val deviceEntryBackgroundViewAlpha = transitionToFinalValue(1f)
    override val deviceEntryParentViewAlpha = transitionToFinalValue(1f)
    val statusBarAlpha = lockscreenAlpha
    val zoomOut = transitionToFinalValue(0f)
    val dreamOverlayAlpha = transitionToFinalValue(0f)

    companion object {
        const val TAG = "ToLockscreenEndStateTransitionViewModel"

        // This is an "end state" transition, which is not tied to a specific "from" state.
        // It's used to ensure that all views are in the correct final state when the LOCKSCREEN
        // state is settled.
        //
        // As such, its duration MUST be longer than any other *ToLockscreen transition to avoid
        // cutting off their animations. Please update this value if a longer transition is added.
        //
        // Known relevant transition durations:
        // - DreamingToLockscreen: 1133ms
        // - OffToLockscreen: 933ms
        // - AodToLockscreen: 833ms
        // - GlanceableHubToLockscreen: 800ms
        // - Others: <= 500ms
        private val DEFAULT_DURATION = 1200.milliseconds
    }
}
