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

package com.android.systemui.bouncer.ui.helper

//noinspection CleanArchitectureDependencyViolation: Data layer only referenced for this enum class

import com.android.keyguard.AuthInteractionProperties
import com.google.android.msdl.data.model.MSDLToken
import com.google.android.msdl.domain.MSDLPlayer
import javax.inject.Inject

/**
 * A helper class to deliver haptic feedback in bouncer interactions.
 *
 * @param[msdlPlayer] The [MSDLPlayer] used to deliver MSDL feedback.
 */
class BouncerHapticPlayer @Inject constructor(private val msdlPlayer: dagger.Lazy<MSDLPlayer>) {

    private val authInteractionProperties by
        lazy(LazyThreadSafetyMode.NONE) { AuthInteractionProperties() }

    /**
     * Deliver MSDL feedback as a result of authenticating through a bouncer.
     *
     * @param[authenticationSucceeded] Whether the authentication was successful or not.
     */
    fun playAuthenticationFeedback(authenticationSucceeded: Boolean) {
        val token =
            if (authenticationSucceeded) {
                MSDLToken.UNLOCK
            } else {
                MSDLToken.FAILURE
            }
        msdlPlayer.get().playToken(token, authInteractionProperties)
    }

    /**
     * Deliver feedback when dragging through cells in the pattern bouncer. This function can play
     * MSDL feedback using a [MSDLPlayer].
     */
    fun playPatternDotFeedback() {
        msdlPlayer.get().playToken(MSDLToken.DRAG_INDICATOR_DISCRETE)
    }

    /** Deliver MSDL feedback when the delete key of the pin bouncer is pressed */
    fun playDeleteKeyPressFeedback() = msdlPlayer.get().playToken(MSDLToken.KEYPRESS_DELETE)

    /** Deliver MSDL feedback when the delete key of the pin bouncer is long-pressed. */
    fun playDeleteKeyLongPressedFeedback() = msdlPlayer.get().playToken(MSDLToken.LONG_PRESS)

    /** Deliver MSDL feedback when a numpad key is pressed on the pin bouncer */
    fun playNumpadKeyFeedback() = msdlPlayer.get().playToken(MSDLToken.KEYPRESS_STANDARD)

    /** Deliver MSDL feedback when clicking on the emergency button */
    fun playEmergencyButtonClickFeedback() = msdlPlayer.get().playToken(MSDLToken.KEYPRESS_RETURN)
}
