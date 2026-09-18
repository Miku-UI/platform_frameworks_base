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

package com.android.systemui.bouncer.shared.model

import android.content.res.Resources
import android.security.Flags.lockscreenLargerTimeoutTimeUnits
import android.security.Flags.lockscreenTimeoutShortlink
import android.security.Flags.secureLockDevice
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.Biometric
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.Password
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.Pattern
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.Pin
import com.android.systemui.res.R

data class LockoutMessageModel(
    val primaryMessage: Int,
    val count: Long,
    val secondaryMessage: Int,
) {
    fun primaryFormatterArgs(): Map<String, Any> = mapOf("count" to count)

    fun secondaryFormatterArgs(resources: Resources): Map<String, Any>? =
        if (lockscreenTimeoutShortlink()) {
            mapOf(
                "shortlink" to
                    resources.getString(
                        com.android.internal.R.string.config_lockscreenLockoutShortlink
                    )
            )
        } else {
            null
        }
}

typealias BouncerMessagePair = Pair<Int, Int>

val BouncerMessagePair.primaryMessage: Int
    get() = this.first

val BouncerMessagePair.secondaryMessage: Int
    get() = this.second

object BouncerMessageStrings {
    private val EmptyMessage = Pair(0, 0)
    private const val SECONDS_IN_MINUTE = 60L
    private const val SECONDS_IN_HOUR = SECONDS_IN_MINUTE * 60L
    private const val SECONDS_IN_DAY = SECONDS_IN_HOUR * 24L
    private const val SECONDS_IN_YEAR = SECONDS_IN_DAY * 365L

    fun defaultMessage(
        securityMode: AuthenticationMethodModel,
        fpAuthIsAllowed: Boolean,
        faceAuthIsAllowed: Boolean = false,
        secureLockDeviceEnabled: Boolean = false,
    ): BouncerMessagePair {
        if (secureLockDevice() && secureLockDeviceEnabled) {
            return when (securityMode) {
                Biometric ->
                    authRequiredForSecureLockDeviceStrongBiometricAuth(
                        fpAuthIsAllowed,
                        faceAuthIsAllowed,
                    )
                else -> authRequiredForSecureLockDevicePrimaryAuth(securityMode)
            }
        }

        return Pair(defaultPrimaryMessage(securityMode, fpAuthIsAllowed), 0)
    }

    private fun defaultPrimaryMessage(
        securityMode: AuthenticationMethodModel,
        fpAuthIsAllowed: Boolean,
    ): Int =
        when (securityMode) {
            Pattern -> patternDefaultMessage(fpAuthIsAllowed)
            Password -> passwordDefaultMessage(fpAuthIsAllowed)
            Pin -> pinDefaultMessage(fpAuthIsAllowed)
            else -> 0
        }

    fun incorrectSecurityInput(
        securityMode: AuthenticationMethodModel,
        fpAuthIsAllowed: Boolean,
        secureLockDeviceEnabled: Boolean = false,
        isDuplicate: Boolean = false,
    ): BouncerMessagePair {
        if (isDuplicate) {
            val defaultPrimaryMessage = defaultPrimaryMessage(securityMode, fpAuthIsAllowed)
            val duplicateMessage = duplicateGuessMessage(securityMode)
            return if (secureLockDevice() && secureLockDeviceEnabled) {
                Pair(R.string.kg_prompt_title_after_secure_lock_device, duplicateMessage)
            } else {
                Pair(defaultPrimaryMessage, duplicateMessage)
            }
        }
        val wrongInputMessage = wrongInputMessage(securityMode)
        return when {
            secureLockDevice() && secureLockDeviceEnabled ->
                Pair(R.string.kg_prompt_title_after_secure_lock_device, wrongInputMessage)
            wrongInputMessage != 0 ->
                Pair(wrongInputMessage, incorrectSecurityInputSecondaryMessage(fpAuthIsAllowed))
            else -> EmptyMessage
        }
    }

    private fun wrongInputMessage(securityMode: AuthenticationMethodModel): Int =
        when (securityMode) {
            Pattern -> R.string.kg_wrong_pattern_try_again
            Password -> R.string.kg_wrong_password_try_again
            Pin -> R.string.kg_wrong_pin_try_again
            else -> 0
        }

    private fun duplicateGuessMessage(securityMode: AuthenticationMethodModel): Int =
        when (securityMode) {
            Pattern -> R.string.kg_primary_auth_duplicate_guess_pattern
            Password -> R.string.kg_primary_auth_duplicate_guess_password
            Pin -> R.string.kg_primary_auth_duplicate_guess_pin
            else -> 0
        }

    private fun incorrectSecurityInputSecondaryMessage(fpAuthIsAllowed: Boolean): Int {
        return if (fpAuthIsAllowed) R.string.kg_wrong_input_try_fp_suggestion else 0
    }

    fun incorrectFingerprintInput(securityMode: AuthenticationMethodModel): BouncerMessagePair {
        val primaryMessage = R.string.kg_fp_not_recognized
        return when (securityMode) {
            Biometric -> Pair(R.string.kg_prompt_title_after_secure_lock_device, primaryMessage)
            Pattern -> Pair(primaryMessage, R.string.kg_bio_try_again_or_pattern)
            Password -> Pair(primaryMessage, R.string.kg_bio_try_again_or_password)
            Pin -> Pair(primaryMessage, R.string.kg_bio_try_again_or_pin)
            else -> EmptyMessage
        }
    }

    fun incorrectFaceInput(
        securityMode: AuthenticationMethodModel,
        fpAuthIsAllowed: Boolean,
    ): BouncerMessagePair {
        return if (fpAuthIsAllowed) incorrectFaceInputWithFingerprintAllowed(securityMode)
        else {
            val primaryMessage = R.string.bouncer_face_not_recognized
            when (securityMode) {
                Biometric -> Pair(R.string.kg_prompt_title_after_secure_lock_device, primaryMessage)
                Pattern -> Pair(primaryMessage, R.string.kg_bio_try_again_or_pattern)
                Password -> Pair(primaryMessage, R.string.kg_bio_try_again_or_password)
                Pin -> Pair(primaryMessage, R.string.kg_bio_try_again_or_pin)
                else -> EmptyMessage
            }
        }
    }

    private fun incorrectFaceInputWithFingerprintAllowed(
        securityMode: AuthenticationMethodModel
    ): BouncerMessagePair {
        val secondaryMsg = R.string.bouncer_face_not_recognized
        return when (securityMode) {
            Biometric -> Pair(R.string.kg_prompt_title_after_secure_lock_device, secondaryMsg)
            Pattern -> Pair(patternDefaultMessage(true), secondaryMsg)
            Password -> Pair(passwordDefaultMessage(true), secondaryMsg)
            Pin -> Pair(pinDefaultMessage(true), secondaryMsg)
            else -> EmptyMessage
        }
    }

    fun authRequiredAfterReboot(securityMode: AuthenticationMethodModel): BouncerMessagePair {
        return when (securityMode) {
            Pattern -> Pair(patternDefaultMessage(false), R.string.kg_prompt_reason_restart_pattern)
            Password ->
                Pair(passwordDefaultMessage(false), R.string.kg_prompt_reason_restart_password)
            Pin -> Pair(pinDefaultMessage(false), R.string.kg_prompt_reason_restart_pin)
            else -> EmptyMessage
        }
    }

    fun authRequiredToSignIn(securityMode: AuthenticationMethodModel): BouncerMessagePair {
        return when (securityMode) {
            Pattern -> Pair(patternDefaultMessage(false), R.string.kg_prompt_reason_signin_pattern)
            Password ->
                Pair(passwordDefaultMessage(false), R.string.kg_prompt_reason_signin_password)
            Pin -> Pair(pinDefaultMessage(false), R.string.kg_prompt_reason_signin_pin)
            else -> EmptyMessage
        }
    }

    fun authRequiredAfterAdminLockdown(
        securityMode: AuthenticationMethodModel
    ): BouncerMessagePair {
        val secondaryMsg = R.string.kg_prompt_after_dpm_lock
        return when (securityMode) {
            Pattern -> Pair(patternDefaultMessage(false), secondaryMsg)
            Password -> Pair(passwordDefaultMessage(false), secondaryMsg)
            Pin -> Pair(pinDefaultMessage(false), secondaryMsg)
            else -> EmptyMessage
        }
    }

    fun authRequiredAfterAdaptiveAuthRequest(
        securityMode: AuthenticationMethodModel,
        fpAuthIsAllowed: Boolean,
    ): BouncerMessagePair {
        val secondaryMsg = R.string.kg_prompt_after_adaptive_auth_lock
        return when (securityMode) {
            Pattern -> Pair(patternDefaultMessage(fpAuthIsAllowed), secondaryMsg)
            Password -> Pair(passwordDefaultMessage(fpAuthIsAllowed), secondaryMsg)
            Pin -> Pair(pinDefaultMessage(fpAuthIsAllowed), secondaryMsg)
            else -> EmptyMessage
        }
    }

    fun authRequiredAfterUserLockdown(securityMode: AuthenticationMethodModel): BouncerMessagePair {
        return when (securityMode) {
            Pattern ->
                Pair(patternDefaultMessage(false), R.string.kg_prompt_after_user_lockdown_pattern)
            Password ->
                Pair(passwordDefaultMessage(false), R.string.kg_prompt_after_user_lockdown_password)
            Pin -> Pair(pinDefaultMessage(false), R.string.kg_prompt_after_user_lockdown_pin)
            else -> EmptyMessage
        }
    }

    fun authRequiredForSecureLockDevicePrimaryAuth(
        securityMode: AuthenticationMethodModel
    ): BouncerMessagePair {
        return when (securityMode) {
            Pattern ->
                Pair(
                    R.string.kg_prompt_title_after_secure_lock_device,
                    patternDefaultMessage(false),
                )
            Password ->
                Pair(
                    R.string.kg_prompt_title_after_secure_lock_device,
                    passwordDefaultMessage(false),
                )
            Pin -> Pair(R.string.kg_prompt_title_after_secure_lock_device, pinDefaultMessage(false))
            else -> EmptyMessage
        }
    }

    fun authRequiredForSecureLockDeviceStrongBiometricAuth(
        isFingerprintAllowedOnBouncer: Boolean,
        isFaceAllowedOnBouncer: Boolean,
    ): BouncerMessagePair {
        return if (isFingerprintAllowedOnBouncer && isFaceAllowedOnBouncer) {
            Pair(
                R.string.kg_prompt_title_after_secure_lock_device,
                R.string.kg_prompt_subtitle_for_secure_lock_device_biometric_auth_coex,
            )
        } else if (isFingerprintAllowedOnBouncer) {
            Pair(
                R.string.kg_prompt_title_after_secure_lock_device,
                R.string.kg_prompt_subtitle_for_secure_lock_device_biometric_auth_fingerprint,
            )
        } else if (isFaceAllowedOnBouncer) {
            Pair(
                R.string.kg_prompt_title_after_secure_lock_device,
                R.string.kg_prompt_subtitle_for_secure_lock_device_biometric_auth_face,
            )
        } else EmptyMessage
    }

    // TODO(b/405120698): on adding confirm button, update to this bouncer message from
    //  BouncerMessageInteractor (pre-flexiglass) or BouncerMessageViewModel (post-flexiglass)
    fun pendingFaceAuthConfirmationForSecureLockDevice(): BouncerMessagePair {
        return Pair(
            R.string.kg_prompt_title_after_secure_lock_device,
            R.string.keyguard_face_successful_unlock_confirm_button,
        )
    }

    fun retryAuthenticationForSecureLockDevice(
        fpAuthIsAllowed: Boolean,
        faceAuthIsAllowed: Boolean,
    ): BouncerMessagePair {
        return authRequiredForSecureLockDeviceStrongBiometricAuth(
            fpAuthIsAllowed,
            faceAuthIsAllowed,
        )
    }

    fun authRequiredForUnattendedUpdate(
        securityMode: AuthenticationMethodModel
    ): BouncerMessagePair {
        return when (securityMode) {
            Pattern -> Pair(patternDefaultMessage(false), R.string.kg_prompt_added_security_pattern)
            Password ->
                Pair(passwordDefaultMessage(false), R.string.kg_prompt_added_security_password)
            Pin -> Pair(pinDefaultMessage(false), R.string.kg_prompt_added_security_pin)
            else -> EmptyMessage
        }
    }

    fun authRequiredForMainlineUpdate(securityMode: AuthenticationMethodModel): BouncerMessagePair {
        return when (securityMode) {
            Pattern -> Pair(patternDefaultMessage(false), R.string.kg_prompt_after_update_pattern)
            Password ->
                Pair(passwordDefaultMessage(false), R.string.kg_prompt_after_update_password)
            Pin -> Pair(pinDefaultMessage(false), R.string.kg_prompt_after_update_pin)
            else -> EmptyMessage
        }
    }

    fun authRequiredAfterPrimaryAuthTimeout(
        securityMode: AuthenticationMethodModel
    ): BouncerMessagePair {
        return when (securityMode) {
            Pattern -> Pair(patternDefaultMessage(false), R.string.kg_prompt_pattern_auth_timeout)
            Password ->
                Pair(passwordDefaultMessage(false), R.string.kg_prompt_password_auth_timeout)
            Pin -> Pair(pinDefaultMessage(false), R.string.kg_prompt_pin_auth_timeout)
            else -> EmptyMessage
        }
    }

    fun nonStrongAuthTimeout(
        securityMode: AuthenticationMethodModel,
        fpAuthIsAllowed: Boolean,
    ): BouncerMessagePair {
        val secondaryMsg = R.string.kg_prompt_auth_timeout
        return when (securityMode) {
            Pattern -> Pair(patternDefaultMessage(fpAuthIsAllowed), secondaryMsg)
            Password -> Pair(passwordDefaultMessage(fpAuthIsAllowed), secondaryMsg)
            Pin -> Pair(pinDefaultMessage(fpAuthIsAllowed), secondaryMsg)
            else -> EmptyMessage
        }
    }

    fun faceLockedOut(
        securityMode: AuthenticationMethodModel,
        fpAuthIsAllowed: Boolean,
    ): BouncerMessagePair {
        val secondaryMsg = R.string.kg_face_locked_out
        return when (securityMode) {
            Pattern -> Pair(patternDefaultMessage(fpAuthIsAllowed), secondaryMsg)
            Password -> Pair(passwordDefaultMessage(fpAuthIsAllowed), secondaryMsg)
            Pin -> Pair(pinDefaultMessage(fpAuthIsAllowed), secondaryMsg)
            else -> EmptyMessage
        }
    }

    fun class3AuthLockedOut(
        securityMode: AuthenticationMethodModel,
        isSecureLockDeviceEnabled: Boolean = false,
    ): BouncerMessagePair {
        if (isSecureLockDeviceEnabled) {
            val primaryResId = R.string.kg_prompt_title_after_secure_lock_device
            return when (securityMode) {
                Pattern -> Pair(primaryResId, R.string.kg_bio_too_many_attempts_pattern)
                Password -> Pair(primaryResId, R.string.kg_bio_too_many_attempts_password)
                Pin -> Pair(primaryResId, R.string.kg_bio_too_many_attempts_pin)
                else -> EmptyMessage
            }
        }
        return when (securityMode) {
            Pattern -> Pair(patternDefaultMessage(false), R.string.kg_bio_too_many_attempts_pattern)
            Password ->
                Pair(passwordDefaultMessage(false), R.string.kg_bio_too_many_attempts_password)
            Pin -> Pair(pinDefaultMessage(false), R.string.kg_bio_too_many_attempts_pin)
            else -> EmptyMessage
        }
    }

    fun trustAgentDisabled(
        securityMode: AuthenticationMethodModel,
        fpAuthIsAllowed: Boolean,
    ): BouncerMessagePair {
        val secondaryMsg = R.string.kg_trust_agent_disabled
        return when (securityMode) {
            Pattern -> Pair(patternDefaultMessage(fpAuthIsAllowed), secondaryMsg)
            Password -> Pair(passwordDefaultMessage(fpAuthIsAllowed), secondaryMsg)
            Pin -> Pair(pinDefaultMessage(fpAuthIsAllowed), secondaryMsg)
            else -> EmptyMessage
        }
    }

    fun primaryAuthLockedOut(
        securityMode: AuthenticationMethodModel,
        timeoutSeconds: Long,
    ): LockoutMessageModel {
        val secondaryId =
            when (securityMode) {
                Pattern ->
                    if (lockscreenTimeoutShortlink()) {
                        R.string.kg_primary_auth_locked_out_pattern_shortlink
                    } else {
                        R.string.kg_primary_auth_locked_out_pattern
                    }
                Password ->
                    if (lockscreenTimeoutShortlink()) {
                        R.string.kg_primary_auth_locked_out_password_shortlink
                    } else {
                        R.string.kg_primary_auth_locked_out_password
                    }
                Pin ->
                    if (lockscreenTimeoutShortlink()) {
                        R.string.kg_primary_auth_locked_out_pin_shortlink
                    } else {
                        R.string.kg_primary_auth_locked_out_pin
                    }
                else -> 0
            }
        val (primaryId, count) = determineTimeoutStringAndCount(timeoutSeconds)
        return LockoutMessageModel(primaryId, count, secondaryId)
    }

    private fun determineTimeoutStringAndCount(totalSeconds: Long): Pair<Int, Long> {
        return if (lockscreenLargerTimeoutTimeUnits()) {
            when {
                totalSeconds <= 59 ->
                    Pair(R.string.kg_too_many_failed_attempts_countdown_seconds, totalSeconds)

                totalSeconds <= 90 * SECONDS_IN_MINUTE ->
                    Pair(
                        R.string.kg_too_many_failed_attempts_countdown_minutes,
                        totalSeconds ceilDivide SECONDS_IN_MINUTE,
                    )

                totalSeconds <= 36 * SECONDS_IN_HOUR ->
                    Pair(
                        R.string.kg_too_many_failed_attempts_countdown_hours,
                        totalSeconds ceilDivide SECONDS_IN_HOUR,
                    )

                totalSeconds <= 364 * SECONDS_IN_DAY ->
                    Pair(
                        R.string.kg_too_many_failed_attempts_countdown_days,
                        totalSeconds ceilDivide SECONDS_IN_DAY,
                    )

                else ->
                    Pair(
                        R.string.kg_too_many_failed_attempts_countdown_years,
                        totalSeconds ceilDivide SECONDS_IN_YEAR,
                    )
            }
        } else {
            R.string.kg_too_many_failed_attempts_countdown to totalSeconds
        }
    }

    private infix fun Long.ceilDivide(divisor: Long): Long = (this + divisor - 1) / divisor

    private fun patternDefaultMessage(fingerprintAllowed: Boolean): Int {
        return if (fingerprintAllowed) R.string.kg_unlock_with_pattern_or_fp
        else R.string.keyguard_enter_pattern
    }

    private fun pinDefaultMessage(fingerprintAllowed: Boolean): Int {
        return if (fingerprintAllowed) R.string.kg_unlock_with_pin_or_fp
        else R.string.keyguard_enter_pin
    }

    private fun passwordDefaultMessage(fingerprintAllowed: Boolean): Int {
        return if (fingerprintAllowed) R.string.kg_unlock_with_password_or_fp
        else R.string.keyguard_enter_password
    }
}
