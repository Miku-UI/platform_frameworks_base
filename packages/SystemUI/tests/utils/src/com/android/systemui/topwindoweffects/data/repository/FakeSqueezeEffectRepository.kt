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

package com.android.systemui.topwindoweffects.data.repository

import com.android.systemui.topwindoweffects.data.repository.InvocationEffectPreferencesImpl.Companion.DEFAULT_OUTWARD_EFFECT_DURATION_MS
import com.android.systemui.topwindoweffects.data.repository.SqueezeEffectRepositoryImpl.Companion.DEFAULT_INWARD_EFFECT_DURATION_MILLIS
import com.android.systemui.topwindoweffects.data.repository.SqueezeEffectRepositoryImpl.Companion.DEFAULT_LONG_PRESS_POWER_DURATION_MILLIS
import java.io.PrintWriter
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSqueezeEffectRepository : SqueezeEffectRepository {
    var invocationEffectInitialDelayMs = DEFAULT_LONG_PRESS_POWER_DURATION_MILLIS
    var invocationEffectInwardsAnimationDurationMs = DEFAULT_INWARD_EFFECT_DURATION_MILLIS
    var invocationEffectOutwardsAnimationDurationMs = DEFAULT_OUTWARD_EFFECT_DURATION_MS
    var shouldUseHapticRumble = false

    override var isSqueezeEffectHapticEnabled = false

    override val isEffectEnabled = MutableStateFlow(true)

    override val isPowerButtonPressedAsSingleGesture = MutableStateFlow(false)

    override val isPowerButtonLongPressed = MutableStateFlow(false)

    override fun getInvocationEffectInitialDelayMillis() = invocationEffectInitialDelayMs

    override fun getInvocationEffectInAnimationDurationMillis() =
        invocationEffectInwardsAnimationDurationMs

    override fun getInvocationEffectOutAnimationDurationMillis() =
        invocationEffectOutwardsAnimationDurationMs

    override fun useHapticRumble(): Boolean = shouldUseHapticRumble

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        // empty
    }
}
