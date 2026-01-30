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

package com.android.systemui.keyguard.domain.interactor

import android.util.Log
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryInteractor
import com.android.systemui.keyguard.data.repository.KeyguardTransitionRepository
import com.android.systemui.keyguard.shared.model.KeyguardState.GONE
import com.android.systemui.keyguard.shared.model.KeyguardState.LOCKSCREEN
import com.android.systemui.keyguard.shared.model.KeyguardState.OFF
import com.android.systemui.scene.domain.interactor.OnBootTransitionInteractor
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.statusbar.policy.domain.interactor.DeviceProvisioningInteractor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/** Handles initialization of the KeyguardTransitionRepository on boot. */
@SysUISingleton
class KeyguardTransitionBootInteractor
@Inject
constructor(
    @Application val scope: CoroutineScope,
    val deviceEntryInteractor: DeviceEntryInteractor,
    val deviceProvisioningInteractor: DeviceProvisioningInteractor,
    val keyguardTransitionInteractor: KeyguardTransitionInteractor,
    val internalTransitionInteractor: InternalKeyguardTransitionInteractor,
    val onBootTransitionInteractor: OnBootTransitionInteractor,
    val repository: KeyguardTransitionRepository,
) {
    fun start() {
        scope.launch {
            if (internalTransitionInteractor.currentTransitionInfoInternal().from != OFF) {
                Log.e(
                    "KeyguardTransitionBootInteractor",
                    "We've already transitioned to a state other than OFF. We'll respect that " +
                        "transition, but this should not happen.",
                )
            } else {
                if (SceneContainerFlag.isEnabled) {
                    repository.emitInitialStepsFromOff(LOCKSCREEN)
                } else {
                    repository.emitInitialStepsFromOff(
                        if (onBootTransitionInteractor.showLockscreenOnBoot()) LOCKSCREEN else GONE
                    )
                }
            }
        }
    }
}
