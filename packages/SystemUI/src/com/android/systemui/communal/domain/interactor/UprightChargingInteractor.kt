/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.communal.domain.interactor

import com.android.systemui.communal.posturing.domain.interactor.PosturingInteractor
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.pipeline.battery.data.repository.BatteryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/** An interactor that provides a signal for the "upright and charging" trigger state. */
interface UprightChargingInteractor {
    /** Emits `true` when the device is upright and charging. */
    val isTriggered: Flow<Boolean>
}

@SysUISingleton
class UprightChargingInteractorImpl
@Inject
constructor(
    batteryRepository: BatteryRepository,
    posturingInteractor: PosturingInteractor,
) : UprightChargingInteractor {
    override val isTriggered: Flow<Boolean> =
        batteryRepository.isPluggedIn
            .distinctUntilChanged()
            .flatMapLatest { isPluggedIn ->
                if (isPluggedIn) {
                    posturingInteractor.postured
                } else {
                    flowOf(false)
                }
            }
}
