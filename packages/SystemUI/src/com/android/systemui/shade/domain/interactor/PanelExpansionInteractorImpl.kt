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

package com.android.systemui.shade.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.statusbar.SysuiStatusBarStateController
import javax.inject.Inject

@SysUISingleton
class PanelExpansionInteractorImpl
@Inject
constructor(
    private val sceneInteractor: SceneInteractor,
    private val shadeInteractor: ShadeInteractor,
    private val shadeAnimationInteractor: ShadeAnimationInteractor,
    private val statusBarStateController: SysuiStatusBarStateController,
) : PanelExpansionInteractor {

    @Deprecated(
        "depends on the state you check, use {@link #isShadeFullyExpanded()},\n" +
            "{@link #isOnAod()}, {@link #isOnKeyguard()} instead."
    )
    override val isFullyExpanded
        get() = shadeInteractor.isAnyFullyExpanded.value

    @Deprecated("Use !ShadeInteractor.isAnyExpanded instead")
    override val isFullyCollapsed
        get() = !shadeInteractor.isAnyExpanded.value

    @Deprecated("Use ShadeAnimationInteractor instead")
    override val isCollapsing
        get() =
            shadeAnimationInteractor.isAnyCloseAnimationRunning.value ||
                shadeAnimationInteractor.isLaunchingActivity.value

    @Deprecated("Use sceneInteractor.isTransitionUserInputOngoing instead")
    override val isTracking
        get() = sceneInteractor.isTransitionUserInputOngoing.value

    @Deprecated("Use ShadeInteractor.isAnyExpanded instead.")
    override val isPanelExpanded
        get() = shadeInteractor.isAnyExpanded.value

    @Deprecated("Use SceneInteractor or ShadeInteractor instead")
    override val barState
        get() = statusBarStateController.state

    @Deprecated("No longer supported. Do not add new calls to this.")
    override fun shouldHideStatusBarIconsWhenExpanded(): Boolean {
        if (shadeAnimationInteractor.isLaunchingActivity.value) {
            return false
        }
        // TODO(b/325936094) if a HUN is showing, return false
        return sceneInteractor.currentScene.value == Scenes.Lockscreen
    }
}
