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

package com.android.systemui.scene.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryInteractor
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.statusbar.policy.domain.interactor.DeviceProvisioningInteractor
import javax.inject.Inject

@SysUISingleton
class OnBootTransitionInteractor
@Inject
constructor(
    val deviceEntryInteractor: DeviceEntryInteractor,
    val deviceProvisioningInteractor: DeviceProvisioningInteractor,
    val sceneInteractor: SceneInteractor,
) {

    /** Whether the lockscreen should be showing when the device starts up for the first time. */
    suspend fun showLockscreenOnBoot(): Boolean {
        return (deviceProvisioningInteractor.isDeviceProvisioned() ||
            deviceEntryInteractor.isAuthenticationRequired()) &&
            deviceEntryInteractor.isLockscreenEnabled()
    }

    /** Instantly snap to [Scenes.Gone] if the lockscreen should not be shown. */
    suspend fun maybeChangeInitialScene() {
        if (!showLockscreenOnBoot()) {
            sceneInteractor.snapToScene(Scenes.Gone, "No authentication on boot")
        }
    }
}
