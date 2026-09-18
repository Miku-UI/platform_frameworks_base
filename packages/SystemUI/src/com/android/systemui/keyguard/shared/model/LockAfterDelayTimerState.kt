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
 *
 */

package com.android.systemui.keyguard.shared.model

/**
 * Models the state of the timer that runs starts at events such as
 * - Screen timeout
 * - Awake -> Power button press (with the "lock instantly" setting disabled)
 * - Awake -> Device starts dreaming and triggers the device to lock when it elapses. It is tied to
 *   the Settings.Secure.LOCK_SCREEN_LOCK_AFTER_TIMEOUT setting.
 */
enum class LockAfterDelayTimerState {
    // The "lock screen after timeout timer" is not running.
    // This could be because the device is awake and not dreaming,
    // It could also be because the device entered sleep in a way that is not relevant for the timer
    // (e.g. should lock immediately).
    INACTIVE,
    // The "lock screen after timeout timer" is running.
    // Note: The device may lock for other reasons already.
    RUNNING,
    // The "lock screen after timeout" timer has elapsed, the device should lock as a result of
    // this.
    ELAPSED,
}
