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

package com.android.systemui.keyguard.ui.viewmodel

import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.DozeStateModel
import com.android.systemui.keyguard.shared.model.KeyguardState
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class DozingTransitionFlows
@Inject
constructor(
    private val keyguardInteractor: KeyguardInteractor,
    private val keyguardTransitionInteractor: KeyguardTransitionInteractor,
) {
    /**
     * lockscreenAlpha on transitions from a given KeyguardState to DOZING
     *
     * @param from if null, returns lockscreenAlpha for ANY => DOZING
     */
    fun lockscreenAlpha(from: KeyguardState?): Flow<Float> {
        return keyguardTransitionInteractor.startedKeyguardTransitionStep
            .filter { from == null || it.from == from }
            .flatMapLatest {
                if (it.to == KeyguardState.DOZING) {
                    keyguardInteractor.dozeTransitionModel
                        .map { dozeModel -> dozeModel.to.lockscreenAlpha() }
                        .filterNotNull()
                } else {
                    emptyFlow()
                }
            }
    }

    /**
     * Alpha for the individual non-authentication related keyguard views when the device is
     * transitioning to DOZING or is currently DOZING based on the current [DozeStateModel]; in
     * particular due to [DozeStateModel.DOZE_PULSING_AUTH_UI].
     *
     * @param from if null, returns for ANY => DOZING
     */
    fun nonAuthUIAlpha(from: KeyguardState?): Flow<Float> {
        return keyguardTransitionInteractor.startedKeyguardTransitionStep
            .filter { from == null || it.from == from }
            .flatMapLatest {
                if (it.to == KeyguardState.DOZING) {
                    keyguardInteractor.dozeTransitionModel
                        .map { dozeModel -> dozeModel.to.nonAuthUIAlpha() }
                        .filterNotNull()
                        .distinctUntilChanged()
                } else {
                    emptyFlow()
                }
            }
            .distinctUntilChanged()
    }

    // only valid to check while dozing or transitioning from dozing
    val wasHiddenAuthUIShowingWhileDozing: Flow<Boolean> =
        combine(lockscreenAlpha(null).map { it > 0 }, nonAuthUIAlpha(null).map { it > 0 }) {
                lockscreenShowing,
                nonAuthUIShowing ->
                lockscreenShowing && nonAuthUIShowing
            }
            .onStart { emit(false) }

    private fun DozeStateModel.lockscreenAlpha(): Float? {
        return when (this) {
            DozeStateModel.DOZE_SUSPEND_TRIGGERS,
            DozeStateModel.DOZE_PULSING_WITHOUT_UI,
            DozeStateModel.DOZE -> 0f
            DozeStateModel.DOZE_PULSING_AUTH_UI,
            DozeStateModel.DOZE_PULSING_BRIGHT,
            DozeStateModel.DOZE_PULSING -> 1f
            // Unhandled states
            DozeStateModel.DOZE_AOD_MINMODE,
            DozeStateModel.DOZE_AOD,
            DozeStateModel.DOZE_AOD_DOCKED,
            DozeStateModel.DOZE_AOD_PAUSED,
            DozeStateModel.DOZE_PULSE_DONE,
            DozeStateModel.FINISH,
            DozeStateModel.DOZE_AOD_PAUSING,
            DozeStateModel.DOZE_REQUEST_PULSE,
            DozeStateModel.UNINITIALIZED,
            DozeStateModel.INITIALIZED -> null
        }
    }

    private fun DozeStateModel.nonAuthUIAlpha(): Float? {
        return when (this) {
            DozeStateModel.DOZE_PULSING_AUTH_UI -> 0f
            DozeStateModel.DOZE_SUSPEND_TRIGGERS,
            DozeStateModel.DOZE_PULSING_WITHOUT_UI,
            DozeStateModel.DOZE,
            DozeStateModel.DOZE_PULSING_BRIGHT,
            DozeStateModel.DOZE_PULSING -> 1f
            // Unhandled states
            DozeStateModel.DOZE_AOD_MINMODE,
            DozeStateModel.DOZE_AOD,
            DozeStateModel.DOZE_AOD_DOCKED,
            DozeStateModel.DOZE_AOD_PAUSED,
            DozeStateModel.DOZE_PULSE_DONE,
            DozeStateModel.FINISH,
            DozeStateModel.DOZE_AOD_PAUSING,
            DozeStateModel.DOZE_REQUEST_PULSE,
            DozeStateModel.UNINITIALIZED,
            DozeStateModel.INITIALIZED -> null
        }
    }
}
