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

import com.android.systemui.deviceentry.shared.model.DeviceUnlockSource

/**
 * Model [BiometricUnlockMode] with [BiometricUnlockSource].
 *
 * @param source can be null as a starting state or if the unlock isn't coming from a biometric (the
 *   latter should be deprecated in the future, b/338578036)
 */
data class BiometricUnlockModel(val mode: BiometricUnlockMode, val source: BiometricUnlockSource?)

/**
 * Maps BiometricUnlockModel to an unlockSource.
 *
 * @return the DeviceUnlockSource corresponding with the BiometricUnlockModel. This can return null
 *   if the BiometricUnlockModel does not mean the device was unlocked.
 */
fun BiometricUnlockModel.toDeviceUnlockSource(): DeviceUnlockSource? {
    return when (mode) {
        BiometricUnlockMode.NONE,
        BiometricUnlockMode.SHOW_BOUNCER,
        BiometricUnlockMode.ONLY_WAKE -> null

        BiometricUnlockMode.DISMISS,
        BiometricUnlockMode.DISMISS_BOUNCER,
        BiometricUnlockMode.WAKE_AND_DISMISS,
        BiometricUnlockMode.WAKE_AND_DISMISS_FROM_DREAM,
        BiometricUnlockMode.WAKE_AND_DISMISS_PULSING ->
            when (source) {
                BiometricUnlockSource.FINGERPRINT_SENSOR -> DeviceUnlockSource.Fingerprint
                BiometricUnlockSource.FACE_SENSOR -> DeviceUnlockSource.FaceWithBypassOrUnlockIntent
                null -> null
            }

        // These states are only valid for passive auth (ie: face)
        BiometricUnlockMode.NONE_UNLOCKED,
        BiometricUnlockMode.ONLY_WAKE_UNLOCKED ->
            when (source) {
                BiometricUnlockSource.FACE_SENSOR -> DeviceUnlockSource.FaceWithoutBypass
                else -> null
            }
    }
}

/**
 * Model whether device is waking, unlocking, and/or dismissing keyguard. Dismissing implies the
 * device was unlocked.
 */
enum class BiometricUnlockMode {
    /**
     * Mode in which we don't need to wake up the device when we attempted authentication. No
     * authentication occurred.
     */
    NONE,
    /**
     * Mode in which we don't need to wake up the device when we authenticate. Authentication was
     * successful.
     */
    NONE_UNLOCKED,
    /**
     * Mode in which we wake up the device, and directly dismiss Keyguard. Active when we acquire a
     * fingerprint while the screen is off and the device was sleeping.
     */
    WAKE_AND_DISMISS,
    /**
     * Mode in which we wake the device up, and fade out the Keyguard contents because they were
     * already visible while pulsing in doze mode.
     */
    WAKE_AND_DISMISS_PULSING,
    /**
     * Mode in which we wake up the device, but play the normal dismiss animation. Active when we
     * acquire a fingerprint pulsing in doze mode.
     */
    SHOW_BOUNCER,
    /**
     * Mode in which we only wake up the device, and keyguard was not showing when we attempted
     * authentication. No authentication occurred.
     */
    ONLY_WAKE,
    /** Mode in which we only wake up the device. Authentication was successful. */
    ONLY_WAKE_UNLOCKED,
    /**
     * Mode in which fingerprint unlocks the device or passive auth (ie face auth) unlocks the
     * device while being requested when keyguard is occluded or showing.
     */
    DISMISS,
    /** When bouncer is visible and will be dismissed. */
    DISMISS_BOUNCER,
    /** Mode in which fingerprint wakes and unlocks the device from a dream. */
    WAKE_AND_DISMISS_FROM_DREAM;

    companion object {
        private val wakeAndDismissModes =
            setOf(WAKE_AND_DISMISS, WAKE_AND_DISMISS_FROM_DREAM, WAKE_AND_DISMISS_PULSING)
        private val dismissesKeyguardModes =
            setOf(
                WAKE_AND_DISMISS,
                WAKE_AND_DISMISS_PULSING,
                DISMISS,
                WAKE_AND_DISMISS_FROM_DREAM,
                DISMISS_BOUNCER,
            )

        fun isWakeAndDismiss(mode: BiometricUnlockMode): Boolean {
            return wakeAndDismissModes.contains(mode)
        }

        fun dismissesKeyguard(mode: BiometricUnlockMode): Boolean {
            return dismissesKeyguardModes.contains(mode)
        }
    }
}
