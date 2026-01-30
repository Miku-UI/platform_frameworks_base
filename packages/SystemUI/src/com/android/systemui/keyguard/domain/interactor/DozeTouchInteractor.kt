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

package com.android.systemui.keyguard.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryInteractor
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryUdfpsInteractor
import com.android.systemui.keyguard.shared.model.DozeStateModel
import com.android.systemui.keyguard.shared.model.isDocked
import com.android.systemui.keyguard.shared.model.isDozing
import com.android.systemui.keyguard.shared.model.isPulsing
import com.android.systemui.power.domain.interactor.PowerInteractor
import com.android.systemui.power.shared.model.DozeScreenStateModel
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Handles business logic for whether touches should be intercepted instead of going to child views
 * while entering/in doze.
 */
@SysUISingleton
class DozeTouchInteractor
@Inject
constructor(
    keyguardInteractor: KeyguardInteractor,
    powerInteractor: PowerInteractor,
    deviceEntryUdfpsInteractor: DeviceEntryUdfpsInteractor,
    deviceEntryInteractor: DeviceEntryInteractor,
) {
    private val toDozeTransitionModel = keyguardInteractor.dozeTransitionModel.map { it.to }

    private val isUnlocked =
        if (SceneContainerFlag.isEnabled) {
            deviceEntryInteractor.isUnlocked
        } else {
            keyguardInteractor.hasTrust
        }

    // Long-press is enabled in AOD only if UDFPS is enrolled & UDFPS can't be used while locked
    private val deviceEntryIconLongPressEnabledInAod =
        deviceEntryUdfpsInteractor.isUdfpsEnrolledAndEnabled.flatMapLatest { udfpsEnrolled ->
            if (udfpsEnrolled) {
                combine(isUnlocked, deviceEntryUdfpsInteractor.isListeningForUdfps) {
                    isUnlocked,
                    udfpsRunning ->
                    !isUnlocked && !udfpsRunning
                }
            } else {
                flowOf(false)
            }
        }

    private val inAodDeferment: Flow<Boolean> =
        combine(toDozeTransitionModel, powerInteractor.dozeScreenState) {
            dozeTransitionModel,
            dozeScreenState ->
            dozeTransitionModel == DozeStateModel.DOZE_AOD &&
                dozeScreenState == DozeScreenStateModel.ON
        }

    val shouldInterceptTouches: Flow<Boolean> =
        combine(inAodDeferment, toDozeTransitionModel, deviceEntryIconLongPressEnabledInAod) {
            aodDeferment,
            dozeTransitionModel,
            deviceEntryIconLongPressEnabledInAod ->
            dozeTransitionModel.isDozing() &&
                !dozeTransitionModel.isPulsing() &&
                !dozeTransitionModel.isDocked() &&
                (!aodDeferment || !deviceEntryIconLongPressEnabledInAod)
        }
}
