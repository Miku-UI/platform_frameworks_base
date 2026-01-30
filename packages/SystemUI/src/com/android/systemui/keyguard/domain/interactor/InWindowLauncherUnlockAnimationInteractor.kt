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

package com.android.systemui.keyguard.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.keyguard.data.repository.InWindowLauncherUnlockAnimationRepository
import com.android.systemui.keyguard.data.repository.KeyguardSurfaceBehindRepository
import com.android.systemui.keyguard.shared.model.Edge
import com.android.systemui.keyguard.shared.model.KeyguardState.GONE
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shared.system.ActivityManagerWrapper
import com.android.systemui.shared.system.smartspace.SmartspaceState
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@SysUISingleton
class InWindowLauncherUnlockAnimationInteractor
@Inject
constructor(
    private val repository: InWindowLauncherUnlockAnimationRepository,
    @Application scope: CoroutineScope,
    @Background val backgroundScope: CoroutineScope,
    transitionInteractor: KeyguardTransitionInteractor,
    surfaceBehindRepository: dagger.Lazy<KeyguardSurfaceBehindRepository>,
    private val activityManager: ActivityManagerWrapper,
) {
    val startedUnlockAnimation = repository.startedUnlockAnimation.asStateFlow()

    /**
     * Whether we've STARTED but not FINISHED a transition to GONE, and the preconditions are met to
     * play the in-window unlock animation.
     */
    val transitioningToGoneWithInWindowAnimation: StateFlow<Boolean> =
        transitionInteractor
            .isInTransition(
                edge = Edge.create(to = Scenes.Gone),
                edgeWithoutSceneContainer = Edge.create(to = GONE),
            )
            .map { transitioningToGone -> transitioningToGone && isLauncherUnderneath() }
            .stateIn(scope, SharingStarted.Eagerly, false)

    /**
     * Whether we should start the in-window unlock animation.
     *
     * This emits true once the Launcher surface becomes available while we're
     * [transitioningToGoneWithInWindowAnimation].
     */
    val shouldStartInWindowAnimation: StateFlow<Boolean> =
        combine(
                transitioningToGoneWithInWindowAnimation,
                surfaceBehindRepository.get().isSurfaceRemoteAnimationTargetAvailable,
            ) { transitioningWithInWindowAnimation, isSurfaceAvailable ->
                transitioningWithInWindowAnimation && isSurfaceAvailable
            }
            .stateIn(scope, SharingStarted.Eagerly, false)

    init {
        backgroundScope.launch {
            // Whenever we're not GONE, update whether Launcher was the app in front. We need to do
            // this before the next unlock, but it triggers a binder call, so this is the best time
            // to do it. In edge cases where this changes while we're locked (the foreground app
            // crashes, etc.) the worst case is that we fall back to the normal unlock animation (or
            // unnecessarily play the animation on Launcher when there's an app over it), which is
            // not a big deal.
            transitionInteractor.isCurrentlyIn(Scenes.Gone, GONE)
                .distinctUntilChanged()
                .collect { gone ->
                    if (!gone) {
                        updateIsLauncherUnderneath()
                    }
                }
        }
    }

    /** Sets whether we've started */
    fun setStartedUnlockAnimation(started: Boolean) {
        repository.setStartedUnlockAnimation(started)
    }

    fun setManualUnlockAmount(amount: Float) {
        repository.setManualUnlockAmount(amount)
    }

    fun setLauncherActivityClass(className: String) {
        repository.setLauncherActivityClass(className)
    }

    fun setLauncherSmartspaceState(state: SmartspaceState?) {
        repository.setLauncherSmartspaceState(state)
    }

    /**
     * Whether the Launcher was underneath the lockscreen as of the last time we locked (it's at the
     * top of the activity task stack).
     */
    fun isLauncherUnderneath(): Boolean {
        return repository.isLauncherUnderneath.value
    }

    /**
     * Updates whether Launcher is the app underneath the lock screen as of this moment. Triggers a
     * binder call, so this is run in the background.
     */
    private fun updateIsLauncherUnderneath() {
        backgroundScope.launch {
            repository.setIsLauncherUnderneath(
                repository.launcherActivityClass.value?.let {
                    activityManager.runningTask?.topActivity?.className?.equals(it)
                } ?: false
            )
        }
    }
}
