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

package com.android.internal.policy;

/**
 * The Keyguard state that has a source of truth in system server and is pushed to KeyguardService.
 *
 * <p>When KeyguardService dies, this data is preserved and pused to the new KeyguardService
 * instance, in order to keep the keyguard state consistent.
 */
parcelable KeyguardState {

    /** The device screen has finished turning off. */
    const int SCREEN_STATE_OFF = 0;
    /** The device screen has started turning on */
    const int SCREEN_STATE_TURNING_ON = 1;
    /** The device screen has finished turning on. */
    const int SCREEN_STATE_ON = 2;
    /** The device screen has started turning off */
    const int SCREEN_STATE_TURNING_OFF = 3;

    /** The device has finished going to sleep. */
    const int INTERACTIVE_STATE_SLEEP = 0;
    /** The device has started waking up. */
    const int INTERACTIVE_STATE_WAKING = 1;
    /** The device has finished waking up. */
    const int INTERACTIVE_STATE_AWAKE = 2;
    /** The device has started going to sleep. */
    const int INTERACTIVE_STATE_GOING_TO_SLEEP = 3;

    /** Whether Keyguard is occluded by another window. */
    boolean occluded;

    /** Whether the display is dreaming. */
    boolean dreaming;

    /** Whether Keyguard is enabled. */
    boolean enabled = true;

    /** Whether the system is mostly done booting. */
    boolean systemReady;

    /** Whether the system is fully done booting. */
    boolean bootCompleted;

    /** The current screen state. */
    int screenState;

    /** The current interactive state. */
    int interactiveState;

    /** The ID of the current user (or user profile). */
    int userId;
}
