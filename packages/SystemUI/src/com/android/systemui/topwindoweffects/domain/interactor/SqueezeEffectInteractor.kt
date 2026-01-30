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

package com.android.systemui.topwindoweffects.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.topwindoweffects.data.repository.SqueezeEffectRepository
import java.io.PrintWriter
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

@SysUISingleton
class SqueezeEffectInteractor
@Inject
constructor(private val squeezeEffectRepository: SqueezeEffectRepository) {
    val isSqueezeEffectHapticEnabled = squeezeEffectRepository.isSqueezeEffectHapticEnabled

    val isPowerButtonLongPressed = squeezeEffectRepository.isPowerButtonLongPressed

    val powerButtonSemantics: Flow<PowerButtonSemantics> =
        combine(
                squeezeEffectRepository.isEffectEnabled,
                squeezeEffectRepository.isPowerButtonPressedAsSingleGesture,
                isPowerButtonLongPressed,
            ) { isEnabled, isPowerButtonPressedAsSingleGesture, isPowerButtonLongPressed ->
                val useInitialRumble = squeezeEffectRepository.useHapticRumble()
                when {
                    !isPowerButtonPressedAsSingleGesture -> PowerButtonSemantics.CANCEL_SQUEEZE
                    isEnabled && isPowerButtonPressedAsSingleGesture && useInitialRumble ->
                        PowerButtonSemantics.START_SQUEEZE_WITH_RUMBLE
                    isEnabled && isPowerButtonPressedAsSingleGesture && !useInitialRumble ->
                        PowerButtonSemantics.START_SQUEEZE_WITHOUT_RUMBLE
                    !isEnabled && isPowerButtonPressedAsSingleGesture && isPowerButtonLongPressed ->
                        PowerButtonSemantics.PLAY_DEFAULT_ASSISTANT_HAPTICS
                    else -> null
                }
            }
            .filterNotNull()
            .distinctUntilChanged()

    fun getInvocationEffectInitialDelayMillis(): Long {
        return squeezeEffectRepository.getInvocationEffectInitialDelayMillis()
    }

    fun getInvocationEffectInAnimationDurationMillis(): Long {
        return squeezeEffectRepository.getInvocationEffectInAnimationDurationMillis()
    }

    fun getInvocationEffectOutAnimationDurationMillis(): Long {
        return squeezeEffectRepository.getInvocationEffectOutAnimationDurationMillis()
    }

    fun dump(pw: PrintWriter, args: Array<out String>) {
        squeezeEffectRepository.dump(pw, args)
    }
}
