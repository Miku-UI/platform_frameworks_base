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

package com.android.systemui.scene.domain.interactor

import com.android.compose.animation.scene.SceneKey
import com.android.systemui.authentication.domain.interactor.authenticationInteractor
import com.android.systemui.deviceentry.domain.interactor.deviceUnlockedInteractor
import com.android.systemui.deviceentry.domain.interactor.restrictedModeInteractor
import com.android.systemui.keyguard.domain.interactor.keyguardEnabledInteractor
import com.android.systemui.keyguard.domain.interactor.scenetransition.lockscreenSceneTransitionInteractor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.scene.data.repository.sceneContainerRepository
import com.android.systemui.scene.domain.resolver.sceneFamilyResolvers
import com.android.systemui.scene.shared.logger.sceneLogger
import com.android.systemui.shade.domain.interactor.shadeModeInteractor
import platform.test.motion.compose.MotionControlScope

val Kosmos.sceneInteractor: SceneInteractor by
    Kosmos.Fixture {
        SceneInteractor(
            applicationScope = applicationCoroutineScope,
            repository = sceneContainerRepository,
            logger = sceneLogger,
            sceneFamilyResolvers = { sceneFamilyResolvers },
            deviceUnlockedInteractor = { deviceUnlockedInteractor },
            keyguardEnabledInteractor = { keyguardEnabledInteractor },
            disabledContentInteractor = disabledContentInteractor,
            shadeModeInteractor = shadeModeInteractor,
            authenticationInteractor = { authenticationInteractor },
            lockscreenSceneTransitionInteractor = { lockscreenSceneTransitionInteractor },
            restrictedModeInteractor = { restrictedModeInteractor },
        )
    }

/**
 * Waits until the SceneContainer is idle.
 *
 * When [onScene] is non-null, waits until the SceneContainer is idle on that specific scene.
 */
suspend fun MotionControlScope.awaitTransitionIdle(kosmos: Kosmos, onScene: SceneKey? = null) {
    awaitCondition {
        val transitionState = kosmos.sceneInteractor.transitionState
        transitionState.isIdle() && (onScene == null || onScene == transitionState.currentScene)
    }
}
