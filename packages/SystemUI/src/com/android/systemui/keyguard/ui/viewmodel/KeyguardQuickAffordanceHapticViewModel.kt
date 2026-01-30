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

package com.android.systemui.keyguard.ui.viewmodel

//noinspection CleanArchitectureDependencyViolation
import com.android.systemui.Flags
import com.android.systemui.keyguard.ui.binder.KeyguardBottomAreaVibrations
import com.android.systemui.statusbar.VibratorHelper
import com.google.android.msdl.data.model.MSDLToken
import com.google.android.msdl.domain.MSDLPlayer
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

class KeyguardQuickAffordanceHapticViewModel
@AssistedInject
constructor(private val msdlPlayer: MSDLPlayer, private val vibratorHelper: VibratorHelper) {
    var longPressed = false
        private set

    private var activated = false

    fun updateActivatedHistory(isActivated: Boolean) {
        val toggleOn = !activated && isActivated
        val toggleOff = activated && !isActivated
        activated = isActivated

        if (Flags.msdlFeedback()) {
            playMSDLToggleHaptics(toggleOn, toggleOff)
        }
    }

    fun onQuickAffordanceLongPress(isActivated: Boolean) {
        longPressed = true
        if (!Flags.msdlFeedback()) {
            // Without MSDL, we need to play haptics on long-press instead of when the activated
            // history updates.
            val vibration =
                if (isActivated) {
                    KeyguardBottomAreaVibrations.Activated
                } else {
                    KeyguardBottomAreaVibrations.Deactivated
                }
            vibratorHelper.vibrate(vibration)
        }
    }

    fun onQuickAffordanceClick() {
        if (Flags.msdlFeedback()) {
            msdlPlayer.playToken(MSDLToken.FAILURE)
        } else {
            vibratorHelper.vibrate(KeyguardBottomAreaVibrations.Shake)
        }
    }

    private fun playMSDLToggleHaptics(toggleOn: Boolean, toggleOff: Boolean) {
        if (!longPressed) return
        if (toggleOn) {
            msdlPlayer.playToken(MSDLToken.SWITCH_ON)
        } else if (toggleOff) {
            msdlPlayer.playToken(MSDLToken.SWITCH_OFF)
        }
        longPressed = false
    }

    @AssistedFactory
    interface Factory {
        fun create(): KeyguardQuickAffordanceHapticViewModel
    }
}
