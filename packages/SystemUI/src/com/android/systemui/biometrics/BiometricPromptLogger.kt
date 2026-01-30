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

package com.android.systemui.biometrics

import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.hardware.biometrics.BiometricPrompt.DISMISSED_REASON_BIOMETRIC_CONFIRMED
import android.hardware.biometrics.BiometricPrompt.DISMISSED_REASON_BIOMETRIC_CONFIRM_NOT_REQUIRED
import android.hardware.biometrics.BiometricPrompt.DISMISSED_REASON_CONTENT_VIEW_MORE_OPTIONS
import android.hardware.biometrics.BiometricPrompt.DISMISSED_REASON_CREDENTIAL_CONFIRMED
import android.hardware.biometrics.BiometricPrompt.DISMISSED_REASON_ERROR
import android.hardware.biometrics.BiometricPrompt.DISMISSED_REASON_ERROR_NO_WM
import android.hardware.biometrics.BiometricPrompt.DISMISSED_REASON_FALLBACK_OPTION_BASE
import android.hardware.biometrics.BiometricPrompt.DISMISSED_REASON_FALLBACK_OPTION_MAX
import android.hardware.biometrics.BiometricPrompt.DISMISSED_REASON_NEGATIVE
import android.hardware.biometrics.BiometricPrompt.DISMISSED_REASON_SERVER_REQUESTED
import android.hardware.biometrics.BiometricPrompt.DISMISSED_REASON_USER_CANCEL
import android.hardware.biometrics.PromptInfo
import android.util.Log
import com.android.internal.logging.InstanceId
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.log.SessionTracker
import com.android.systemui.shared.system.SysUiStatsLog
import com.android.systemui.shared.system.SysUiStatsLog.BIOMETRIC_PROMPT_ENDED__REASON__PROMPT_ENDED_REASON_BIOMETRIC_CONFIRMED
import com.android.systemui.shared.system.SysUiStatsLog.BIOMETRIC_PROMPT_ENDED__REASON__PROMPT_ENDED_REASON_BIOMETRIC_CONFIRM_NOT_REQUIRED
import com.android.systemui.shared.system.SysUiStatsLog.BIOMETRIC_PROMPT_ENDED__REASON__PROMPT_ENDED_REASON_CONTENT_VIEW_MORE_OPTIONS
import com.android.systemui.shared.system.SysUiStatsLog.BIOMETRIC_PROMPT_ENDED__REASON__PROMPT_ENDED_REASON_CREDENTIAL_CONFIRMED
import com.android.systemui.shared.system.SysUiStatsLog.BIOMETRIC_PROMPT_ENDED__REASON__PROMPT_ENDED_REASON_ERROR
import com.android.systemui.shared.system.SysUiStatsLog.BIOMETRIC_PROMPT_ENDED__REASON__PROMPT_ENDED_REASON_ERROR_NO_WM
import com.android.systemui.shared.system.SysUiStatsLog.BIOMETRIC_PROMPT_ENDED__REASON__PROMPT_ENDED_REASON_FALLBACK_OPTION
import com.android.systemui.shared.system.SysUiStatsLog.BIOMETRIC_PROMPT_ENDED__REASON__PROMPT_ENDED_REASON_NEGATIVE
import com.android.systemui.shared.system.SysUiStatsLog.BIOMETRIC_PROMPT_ENDED__REASON__PROMPT_ENDED_REASON_SERVER_REQUESTED
import com.android.systemui.shared.system.SysUiStatsLog.BIOMETRIC_PROMPT_ENDED__REASON__PROMPT_ENDED_REASON_UNKNOWN
import com.android.systemui.shared.system.SysUiStatsLog.BIOMETRIC_PROMPT_ENDED__REASON__PROMPT_ENDED_REASON_USER_CANCEL
import javax.inject.Inject

/**
 * Listens to BiometricPrompt events from [AuthController] and logs them to SysUI stats. This class
 * coordinates with [SessionTracker] to ensure logs are associated with the correct session ID.
 */
@SysUISingleton
class BiometricPromptLogger @Inject constructor() {
    /** Logs the start of the Biometric Prompt along with the provided [PromptInfo]. */
    fun logPromptStart(sessionId: InstanceId?, promptInfo: PromptInfo) {
        if (sessionId == null) {
            Log.d(TAG, "Failed to log PromptStart - SessionId null")
            return
        }

        val authenticators = promptInfo.authenticators
        val authenticatorDeviceCredential =
            (authenticators and BiometricManager.Authenticators.DEVICE_CREDENTIAL) != 0
        val authenticatorWeak =
            (authenticators and BiometricManager.Authenticators.BIOMETRIC_WEAK) != 0
        val authenticatorStrong =
            (authenticators and BiometricManager.Authenticators.BIOMETRIC_STRONG) != 0
        val authenticatorIdentityCheck =
            (authenticators and BiometricManager.Authenticators.IDENTITY_CHECK) != 0

        val hasCustomTitle = !promptInfo.title.isNullOrEmpty()
        val hasCustomSubtitle = !promptInfo.subtitle.isNullOrEmpty()
        val hasCustomDescription = !promptInfo.description.isNullOrEmpty()
        val hasCustomNegativeButtonText = !promptInfo.negativeButtonText.isNullOrEmpty()

        SysUiStatsLog.write(
            SysUiStatsLog.BIOMETRIC_PROMPT_STARTED,
            sessionId.id,
            promptInfo.isDeviceCredentialAllowed,
            authenticatorDeviceCredential,
            authenticatorWeak,
            authenticatorStrong,
            authenticatorIdentityCheck,
            promptInfo.isConfirmationRequested,
            hasCustomTitle,
            hasCustomSubtitle,
            hasCustomDescription,
            promptInfo.contentView != null,
            hasCustomNegativeButtonText,
            promptInfo.fallbackOptions.size,
            promptInfo.isIdentityCheckActive,
        )
    }

    /** Logs Biometric Prompt end and reason */
    fun logPromptEnd(sessionId: InstanceId?, @BiometricPrompt.DismissedReason reason: Int) {
        if (sessionId == null) {
            Log.d(TAG, "Failed to log PromptEnd - SessionId null")
            return
        }

        SysUiStatsLog.write(
            SysUiStatsLog.BIOMETRIC_PROMPT_ENDED,
            sessionId.id,
            toBiometricPromptEndedReason(reason),
        )
    }

    private fun toBiometricPromptEndedReason(@BiometricPrompt.DismissedReason reason: Int): Int {
        return when (reason) {
            // Check if the reason is within the fallback option range
            in DISMISSED_REASON_FALLBACK_OPTION_BASE until DISMISSED_REASON_FALLBACK_OPTION_MAX ->
                BIOMETRIC_PROMPT_ENDED__REASON__PROMPT_ENDED_REASON_FALLBACK_OPTION

            DISMISSED_REASON_BIOMETRIC_CONFIRMED ->
                BIOMETRIC_PROMPT_ENDED__REASON__PROMPT_ENDED_REASON_BIOMETRIC_CONFIRMED
            DISMISSED_REASON_NEGATIVE ->
                BIOMETRIC_PROMPT_ENDED__REASON__PROMPT_ENDED_REASON_NEGATIVE
            DISMISSED_REASON_USER_CANCEL ->
                BIOMETRIC_PROMPT_ENDED__REASON__PROMPT_ENDED_REASON_USER_CANCEL
            DISMISSED_REASON_BIOMETRIC_CONFIRM_NOT_REQUIRED ->
                BIOMETRIC_PROMPT_ENDED__REASON__PROMPT_ENDED_REASON_BIOMETRIC_CONFIRM_NOT_REQUIRED
            DISMISSED_REASON_ERROR -> BIOMETRIC_PROMPT_ENDED__REASON__PROMPT_ENDED_REASON_ERROR
            DISMISSED_REASON_SERVER_REQUESTED ->
                BIOMETRIC_PROMPT_ENDED__REASON__PROMPT_ENDED_REASON_SERVER_REQUESTED
            DISMISSED_REASON_CREDENTIAL_CONFIRMED ->
                BIOMETRIC_PROMPT_ENDED__REASON__PROMPT_ENDED_REASON_CREDENTIAL_CONFIRMED
            DISMISSED_REASON_CONTENT_VIEW_MORE_OPTIONS ->
                BIOMETRIC_PROMPT_ENDED__REASON__PROMPT_ENDED_REASON_CONTENT_VIEW_MORE_OPTIONS
            DISMISSED_REASON_ERROR_NO_WM ->
                BIOMETRIC_PROMPT_ENDED__REASON__PROMPT_ENDED_REASON_ERROR_NO_WM
            else -> BIOMETRIC_PROMPT_ENDED__REASON__PROMPT_ENDED_REASON_UNKNOWN
        }
    }

    /** Logs an event in Biometric Prompt */
    fun logPromptEvent(sessionId: InstanceId?, event: Int) {
        if (sessionId == null) {
            Log.d(TAG, "Failed to log PromptEvent - SessionId null")
            return
        }

        SysUiStatsLog.write(SysUiStatsLog.BIOMETRIC_PROMPT_EVENT, sessionId.id, event)
    }

    companion object {
        private const val TAG = "BiometricPromptLogger"
    }
}
