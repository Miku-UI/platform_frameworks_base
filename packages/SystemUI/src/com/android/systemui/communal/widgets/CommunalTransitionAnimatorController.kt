/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.communal.widgets

import com.android.systemui.animation.ActivityTransitionAnimator
import com.android.systemui.animation.DelegateTransitionAnimatorController
import com.android.systemui.communal.domain.interactor.CommunalSceneInteractor
import com.android.systemui.communal.shared.model.CommunalScenes
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.SceneFamilies
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.statusbar.phone.CentralSurfaces
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.Optional

/**
 * An [ActivityTransitionAnimator.Controller] that takes care of updating the state of the Communal
 * Hub at the right time.
 */
class CommunalTransitionAnimatorController
@AssistedInject
constructor(
    @Assisted delegate: ActivityTransitionAnimator.Controller,
    private val centralSurfaces: Optional<CentralSurfaces>,
    private val communalSceneInteractor: CommunalSceneInteractor,
) : DelegateTransitionAnimatorController(delegate) {

    @AssistedFactory
    interface Factory {
        fun create(
            delegate: ActivityTransitionAnimator.Controller
        ): CommunalTransitionAnimatorController
    }

    override fun onIntentStarted(willAnimate: Boolean) {
        if (!willAnimate) {
            // Other callbacks won't happen, so reset the state here.
            communalSceneInteractor.setIsLaunchingWidget(false)
        }
        delegate.onIntentStarted(willAnimate)
    }

    override fun onTransitionAnimationStart(isExpandingFullyAbove: Boolean) {
        delegate.onTransitionAnimationStart(isExpandingFullyAbove)
        // When launching an activity from the communal hub, we need to transition to the
        // correct scene after the animation. This is delayed by the animation duration to
        // ensure a smooth transition.
        centralSurfaces.ifPresent {
            val newScene =
                if (SceneContainerFlag.isEnabled) {
                    if (it.isLaunchingActivityOverLockscreen) {
                        Scenes.Occluded
                    } else {
                        SceneFamilies.Home
                    }
                } else {
                    CommunalScenes.Blank
                }
            communalSceneInteractor.snapToScene(
                newScene,
                "CommunalTransitionAnimatorController",
                delayMillis = ActivityTransitionAnimator.TIMINGS.totalDuration,
            )
        }
    }

    override fun onTransitionAnimationCancelled(newKeyguardOccludedState: Boolean?) {
        communalSceneInteractor.setIsLaunchingWidget(false)
        delegate.onTransitionAnimationCancelled(newKeyguardOccludedState)
    }

    override fun onTransitionAnimationEnd(isExpandingFullyAbove: Boolean) {
        communalSceneInteractor.setIsLaunchingWidget(false)
        delegate.onTransitionAnimationEnd(isExpandingFullyAbove)
    }
}
