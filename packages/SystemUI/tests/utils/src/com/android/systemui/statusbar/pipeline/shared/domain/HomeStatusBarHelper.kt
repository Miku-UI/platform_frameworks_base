/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.systemui.statusbar.pipeline.shared.domain

import android.app.StatusBarManager.CAMERA_LAUNCH_SOURCE_POWER_DOUBLE_TAP
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.data.repository.keyguardOcclusionRepository
import com.android.systemui.keyguard.domain.interactor.keyguardInteractor
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.testScope
import com.android.systemui.scene.SceneHelper.setDeviceEntered
import com.android.systemui.scene.data.repository.sceneContainerRepository
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.statusbar.window.data.repository.fakeStatusBarWindowStatePerDisplayRepository
import com.android.systemui.statusbar.window.shared.model.StatusBarWindowState

object HomeStatusBarHelper {
    fun Kosmos.setStatusBarWindowState(state: StatusBarWindowState) {
        fakeStatusBarWindowStatePerDisplayRepository.setWindowState(state)
    }

    suspend fun Kosmos.launchSecureCamera() {
        if (SceneContainerFlag.isEnabled) {
            sceneContainerRepository.instantlyTransitionTo(Scenes.Lockscreen)
            // Secure camera is an occluding activity
            keyguardOcclusionRepository.setOccludedFromRemoteAnimation(
                onTop = true,
                taskInfo = null,
            )
            keyguardInteractor.onCameraLaunchDetected(
                CAMERA_LAUNCH_SOURCE_POWER_DOUBLE_TAP,
                isSecureCamera = true,
            )
        } else {
            // Secure camera is an occluding activity
            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.AOD,
                to = KeyguardState.OCCLUDED,
                testScope = testScope,
            )
            keyguardOcclusionRepository.setOccludedFromRemoteAnimation(
                onTop = true,
                taskInfo = null,
            )
            keyguardInteractor.onCameraLaunchDetected(
                CAMERA_LAUNCH_SOURCE_POWER_DOUBLE_TAP,
                isSecureCamera = true,
            )
        }
    }

    suspend fun Kosmos.transitionKeyguardToGone() {
        if (SceneContainerFlag.isEnabled) {
            setDeviceEntered()
        }

        fakeKeyguardTransitionRepository.sendTransitionSteps(
            from = KeyguardState.LOCKSCREEN,
            to = KeyguardState.GONE,
            testScope = testScope,
        )
    }
}
