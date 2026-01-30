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

package com.android.systemui.keyguard.ui.viewmodel

import android.util.Log
import com.android.systemui.Flags
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.domain.interactor.FromPrimaryBouncerTransitionInteractor
import com.android.systemui.keyguard.shared.model.Edge
import com.android.systemui.keyguard.shared.model.KeyguardState.OCCLUDED
import com.android.systemui.keyguard.shared.model.KeyguardState.PRIMARY_BOUNCER
import com.android.systemui.keyguard.ui.KeyguardTransitionAnimationFlow
import com.android.systemui.keyguard.ui.transitions.BlurConfig
import com.android.systemui.keyguard.ui.transitions.PrimaryBouncerTransition
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import com.android.systemui.statusbar.notification.headsup.HeadsUpManager
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.Flow

@SysUISingleton
class PrimaryBouncerToOccludedTransitionViewModel
@Inject
constructor(
    blurConfig: BlurConfig,
    animationFlow: KeyguardTransitionAnimationFlow,
    shadeInteractor: ShadeInteractor,
    shadeModeInteractor: ShadeModeInteractor,
    headsUpManager: HeadsUpManager,
) : PrimaryBouncerTransition {
    private val transitionAnimation =
        animationFlow
            .setup(
                duration = FromPrimaryBouncerTransitionInteractor.TO_OCCLUDED_DURATION,
                edge = Edge.INVALID,
            )
            .setupWithoutSceneContainer(edge = Edge.create(PRIMARY_BOUNCER, OCCLUDED))

    override val windowBlurRadius: Flow<Float> =
        transitionAnimation.sharedFlowWithShade(
            duration = 1.milliseconds,
            onStep = { step, isShadeExpanded ->
                val isOnlyHeadsUpNotificationShowing =
                    !SceneContainerFlag.isEnabled &&
                        shadeModeInteractor.isSplitShade &&
                        !shadeInteractor.isNotificationsExpanded.value &&
                        shadeInteractor.isQsExpanded.value &&
                        headsUpManager.hasPinnedHeadsUp() &&
                        headsUpManager.hasNotifications()
                if (isOnlyHeadsUpNotificationShowing) {
                    Log.w(
                        TAG,
                        "QsExpansion incorrect with splitShade + a pinned heads-up notification",
                    )
                }
                if (isShadeExpanded && !isOnlyHeadsUpNotificationShowing) {
                    if (Flags.notificationShadeBlur()) {
                        blurConfig.maxBlurRadiusPx
                    } else {
                        blurConfig.minBlurRadiusPx
                    }
                } else {
                    blurConfig.minBlurRadiusPx
                }
            },
        )

    override val notificationBlurRadius: Flow<Float> =
        transitionAnimation.immediatelyTransitionTo(0.0f)

    companion object {
        private const val TAG = "PrimaryBouncerToOccludedTransitionViewModel"
    }
}
