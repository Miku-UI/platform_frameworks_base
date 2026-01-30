/*
 * Copyright (C) 2022 The Android Open Source Project
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
 * limitations under the License
 */
package com.android.systemui.keyguard.shared.model

import com.android.systemui.keyguard.shared.model.DozeStateModel.Companion.isDozeOff
import com.android.systemui.keyguard.shared.model.DozeStateModel.DOZE
import com.android.systemui.keyguard.shared.model.DozeStateModel.DOZE_AOD
import com.android.systemui.keyguard.shared.model.DozeStateModel.DOZE_AOD_DOCKED
import com.android.systemui.keyguard.shared.model.DozeStateModel.DOZE_AOD_MINMODE
import com.android.systemui.keyguard.shared.model.DozeStateModel.DOZE_AOD_PAUSED
import com.android.systemui.keyguard.shared.model.DozeStateModel.DOZE_AOD_PAUSING
import com.android.systemui.keyguard.shared.model.DozeStateModel.DOZE_PULSE_DONE
import com.android.systemui.keyguard.shared.model.DozeStateModel.DOZE_PULSING
import com.android.systemui.keyguard.shared.model.DozeStateModel.DOZE_PULSING_AUTH_UI
import com.android.systemui.keyguard.shared.model.DozeStateModel.DOZE_PULSING_BRIGHT
import com.android.systemui.keyguard.shared.model.DozeStateModel.DOZE_PULSING_WITHOUT_UI
import com.android.systemui.keyguard.shared.model.DozeStateModel.DOZE_REQUEST_PULSE
import com.android.systemui.keyguard.shared.model.DozeStateModel.DOZE_SUSPEND_TRIGGERS
import com.android.systemui.keyguard.shared.model.DozeStateModel.FINISH
import com.android.systemui.keyguard.shared.model.DozeStateModel.INITIALIZED
import com.android.systemui.keyguard.shared.model.DozeStateModel.UNINITIALIZED

/** Model device doze states. */
enum class DozeStateModel {
    /** Default state. Transition to INITIALIZED to get Doze going. */
    UNINITIALIZED,
    /** Doze components are set up. Followed by transition to DOZE or DOZE_AOD. */
    INITIALIZED,
    /** Regular doze. Device is asleep and listening for pulse triggers. */
    DOZE,
    /** Deep doze. Device is asleep and is not listening for pulse triggers. */
    DOZE_SUSPEND_TRIGGERS,
    /** Always-on doze. Device is asleep, showing UI and listening for pulse triggers. */
    DOZE_AOD,
    /** Pulse has been requested. Display is on and preparing UI */
    DOZE_REQUEST_PULSE,
    /** Pulse is showing. Display is on and showing UI. */
    DOZE_PULSING,
    /** Display is on and not showing any UI. */
    DOZE_PULSING_WITHOUT_UI,
    /**
     * Device is awake and showing authentication UI (any relevant biometric UI and auth messages.
     */
    DOZE_PULSING_AUTH_UI,
    /** Pulse is showing with bright wallpaper. Device is awake and showing UI. */
    DOZE_PULSING_BRIGHT,
    /** Pulse is done showing. Followed by transition to DOZE or DOZE_AOD. */
    DOZE_PULSE_DONE,
    /** Doze is done. DozeService is finished. */
    FINISH,
    /** AOD, but the display is temporarily off. */
    DOZE_AOD_PAUSED,
    /** AOD, prox is near, transitions to DOZE_AOD_PAUSED after a timeout. */
    DOZE_AOD_PAUSING,
    /** Always-on doze. Device is awake, showing docking UI and listening for pulse triggers. */
    DOZE_AOD_DOCKED,
    /** Always-on doze. Device is awake, showing min-mode UI and listening for pulse triggers. */
    DOZE_AOD_MINMODE;

    companion object {
        fun isDozeOff(model: DozeStateModel): Boolean {
            return model == UNINITIALIZED || model == FINISH
        }
    }
}

fun DozeStateModel.isPulsing(): Boolean {
    return when (this) {
        UNINITIALIZED,
        INITIALIZED,
        DOZE,
        DOZE_SUSPEND_TRIGGERS,
        DOZE_PULSE_DONE,
        FINISH,
        DOZE_AOD_PAUSED,
        DOZE_AOD_PAUSING,
        DOZE_AOD_DOCKED,
        DOZE_AOD_MINMODE,
        DOZE_AOD,
        DOZE_REQUEST_PULSE -> false
        DOZE_PULSING,
        DOZE_PULSING_WITHOUT_UI,
        DOZE_PULSING_AUTH_UI,
        DOZE_PULSING_BRIGHT -> true
    }
}

fun DozeStateModel.isDocked(): Boolean {
    return this == DOZE_AOD_DOCKED
}

fun DozeStateModel.isDozing(): Boolean {
    return !isDozeOff(this)
}
