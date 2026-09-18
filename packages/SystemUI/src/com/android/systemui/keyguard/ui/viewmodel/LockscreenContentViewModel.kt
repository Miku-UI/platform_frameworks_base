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

import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.systemui.keyguard.shared.transition.KeyguardTransitionAnimationCallback
import com.android.systemui.keyguard.shared.transition.KeyguardTransitionAnimationCallbackDelegator
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.statusbar.notification.stack.domain.interactor.NotificationStackAppearanceInteractor
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.coroutineScope

class LockscreenContentViewModel
@AssistedInject
constructor(
    shadeModeInteractor: ShadeModeInteractor,
    private val keyguardTransitionAnimationCallbackDelegator:
        KeyguardTransitionAnimationCallbackDelegator,
    private val notificationStackAppearanceInteractor: NotificationStackAppearanceInteractor,
    private val lockscreenAlphaViewModelFactory: LockscreenAlphaViewModel.Factory,
    @Assisted private val keyguardTransitionAnimationCallback: KeyguardTransitionAnimationCallback,
    @Assisted private val viewStateAccessor: ViewStateAccessor,
) : HydratedActivatable() {
    /** @see ShadeModeInteractor.shadeMode */
    val shadeMode: ShadeMode by shadeModeInteractor.shadeMode.hydratedStateOf()

    /** Alpha value applied to all LockscreenElements. */
    val alpha: Float
        get() = lockscreenAlphaViewModel.alpha

    private val lockscreenAlphaViewModel: LockscreenAlphaViewModel by lazy {
        lockscreenAlphaViewModelFactory.create(viewStateAccessor)
    }

    /** Alpha value applied to all nonAuthUI LockscreenElements. */
    val nonAuthUIAlpha: Float
        get() = lockscreenAlphaViewModel.nonAuthUIAlpha

    override suspend fun onActivated() {
        coroutineScope {
            launch { lockscreenAlphaViewModel.activate() }

            keyguardTransitionAnimationCallbackDelegator.delegate =
                keyguardTransitionAnimationCallback
        }
    }

    override suspend fun onDeactivated() {
        keyguardTransitionAnimationCallbackDelegator.delegate = null
    }

    /** Sets the alpha to apply to the NSSL for fade-in on lockscreen */
    fun setContentAlphaForLockscreenFadeIn(alpha: Float) {
        notificationStackAppearanceInteractor.setAlphaForLockscreenFadeIn(alpha)
    }

    /** Should a content reveal animation run for the given transition */
    fun shouldContentFadeIn(currentTransition: TransitionState.Transition): Boolean {
        return shadeMode != ShadeMode.Dual &&
            currentTransition.isInitiatedByUserInput &&
            (currentTransition.isTransitioning(from = Scenes.Shade, to = Scenes.Lockscreen))
    }

    @AssistedFactory
    interface Factory {
        fun create(
            keyguardTransitionAnimationCallback: KeyguardTransitionAnimationCallback,
            viewState: ViewStateAccessor,
        ): LockscreenContentViewModel
    }
}
