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

package com.android.systemui.power.data.model

/**
 * Emitted after a double-tap power button launch gesture has been detected and we've determined
 * whether the device was entered or not entered when the gesture was originally initiated (the
 * first tap).
 *
 * The most common use case for this event is to launch the camera (either the secure, occluding
 * camera if not entered, or the insecure camera if entered).
 *
 * See [PowerInteractor.emitPowerButtonLaunchEvent] for a detailed explanation of how these events
 * are emitted.
 */
enum class PowerButtonLaunchEvent {
    /** Device was entered (awake, unlocked, and not on lockscreen) when the first tap occurred. */
    LAUNCH_FROM_ENTERED,

    /** Device was not entered (asleep, or on lockscreen) when the first tap occurred. */
    LAUNCH_FROM_NOT_ENTERED,
}
