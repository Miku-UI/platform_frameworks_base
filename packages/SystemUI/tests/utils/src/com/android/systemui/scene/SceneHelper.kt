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

package com.android.systemui.scene

import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.deviceentry.domain.interactor.deviceEntryInteractor
import com.android.systemui.keyguard.domain.interactor.biometricUnlockInteractor
import com.android.systemui.keyguard.shared.model.BiometricUnlockSource
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.currentValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.statusbar.phone.BiometricUnlockController
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf

/** Scene-related utility methods for tests. */
object SceneHelper {
    fun Kosmos.setDeviceEntered(isEntered: Boolean = true) {
        if (isEntered) {
            biometricUnlockInteractor.setBiometricUnlockState(
                unlockStateInt = BiometricUnlockController.MODE_DISMISS,
                biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
            )
            runCurrent()
        }

        val scene =
            if (isEntered) {
                Scenes.Gone
            } else {
                Scenes.Lockscreen
            }
        setScene(scene)

        assertThat(currentValue(deviceEntryInteractor.isDeviceEntered)).isEqualTo(isEntered)
    }

    fun Kosmos.setScene(scene: SceneKey) {
        sceneInteractor.changeScene(scene, "SceneHelper#setScene($scene)")
        sceneInteractor.setTransitionState(flowOf(ObservableTransitionState.Idle(scene)))
        runCurrent()
    }
}
