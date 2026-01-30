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

package com.android.systemui.lowlight.shared.model

enum class LowLightDisplayBehavior {
    NONE,
    UNKNOWN,
    LOW_LIGHT_DREAM,
    NO_DREAM,
    SCREEN_OFF,
}

enum class ScreenState {
    ON,
    DOZE,
    OFF,
}

fun LowLightDisplayBehavior.allowedInScreenState(screenState: ScreenState): Boolean {
    return when (screenState) {
        ScreenState.ON -> true
        ScreenState.DOZE -> {
            this == LowLightDisplayBehavior.NO_DREAM || this == LowLightDisplayBehavior.SCREEN_OFF
        }
        ScreenState.OFF -> {
            this == LowLightDisplayBehavior.SCREEN_OFF
        }
    }
}
