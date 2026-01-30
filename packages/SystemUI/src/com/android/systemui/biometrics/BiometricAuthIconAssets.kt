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
 *
 */

package com.android.systemui.biometrics

import com.android.systemui.res.R

object BiometricAuthIconAssets {
    /** Coex iconView assets for caching */
    fun getCoexAssetsList(hasSfps: Boolean): List<Int> =
        if (hasSfps) {
            listOf(
                R.raw.biometricprompt_sfps_fingerprint_authenticating,
                R.raw.biometricprompt_sfps_rear_display_fingerprint_authenticating,
                R.raw.biometricprompt_sfps_error_to_unlock,
                R.raw.biometricprompt_sfps_error_to_success,
                R.raw.biometricprompt_sfps_fingerprint_to_error,
                R.raw.biometricprompt_sfps_fingerprint_to_error_90,
                R.raw.biometricprompt_sfps_fingerprint_to_error_180,
                R.raw.biometricprompt_sfps_fingerprint_to_error_270,
                R.raw.biometricprompt_sfps_rear_display_fingerprint_to_error,
                R.raw.biometricprompt_sfps_rear_display_fingerprint_to_error_90,
                R.raw.biometricprompt_sfps_rear_display_fingerprint_to_error_180,
                R.raw.biometricprompt_sfps_rear_display_fingerprint_to_error_270,
                R.raw.biometricprompt_sfps_error_to_fingerprint,
                R.raw.biometricprompt_sfps_error_to_fingerprint_90,
                R.raw.biometricprompt_sfps_error_to_fingerprint_180,
                R.raw.biometricprompt_sfps_error_to_fingerprint_270,
                R.raw.biometricprompt_sfps_rear_display_error_to_fingerprint,
                R.raw.biometricprompt_sfps_rear_display_error_to_fingerprint_90,
                R.raw.biometricprompt_sfps_rear_display_error_to_fingerprint_180,
                R.raw.biometricprompt_sfps_rear_display_error_to_fingerprint_270,
                R.raw.biometricprompt_sfps_fingerprint_to_unlock,
                R.raw.biometricprompt_sfps_fingerprint_to_unlock_90,
                R.raw.biometricprompt_sfps_fingerprint_to_unlock_180,
                R.raw.biometricprompt_sfps_fingerprint_to_unlock_270,
                R.raw.biometricprompt_sfps_rear_display_fingerprint_to_unlock,
                R.raw.biometricprompt_sfps_rear_display_fingerprint_to_unlock_90,
                R.raw.biometricprompt_sfps_rear_display_fingerprint_to_unlock_180,
                R.raw.biometricprompt_sfps_rear_display_fingerprint_to_unlock_270,
                R.raw.biometricprompt_sfps_fingerprint_to_success,
                R.raw.biometricprompt_sfps_fingerprint_to_success_90,
                R.raw.biometricprompt_sfps_fingerprint_to_success_180,
                R.raw.biometricprompt_sfps_fingerprint_to_success_270,
                R.raw.biometricprompt_sfps_rear_display_fingerprint_to_success,
                R.raw.biometricprompt_sfps_rear_display_fingerprint_to_success_90,
                R.raw.biometricprompt_sfps_rear_display_fingerprint_to_success_180,
                R.raw.biometricprompt_sfps_rear_display_fingerprint_to_success_270,
            )
        } else {
            listOf(
                R.raw.fingerprint_dialogue_unlocked_to_checkmark_success_lottie,
                R.raw.fingerprint_dialogue_error_to_unlock_lottie,
                R.raw.fingerprint_dialogue_fingerprint_to_unlock_lottie,
                R.raw.fingerprint_dialogue_error_to_success_lottie,
                R.raw.fingerprint_dialogue_fingerprint_to_success_lottie,
                R.raw.fingerprint_dialogue_error_to_fingerprint_lottie,
                R.raw.fingerprint_dialogue_fingerprint_to_error_lottie,
            )
        }

    /** Fingerprint iconView assets for caching */
    fun getFingerprintAssetsList(hasSfps: Boolean): List<Int> =
        if (hasSfps) {
            listOf(
                R.raw.biometricprompt_sfps_fingerprint_authenticating,
                R.raw.biometricprompt_sfps_rear_display_fingerprint_authenticating,
                R.raw.biometricprompt_sfps_error_to_success,
                R.raw.biometricprompt_sfps_fingerprint_to_error,
                R.raw.biometricprompt_sfps_fingerprint_to_error_90,
                R.raw.biometricprompt_sfps_fingerprint_to_error_180,
                R.raw.biometricprompt_sfps_fingerprint_to_error_270,
                R.raw.biometricprompt_sfps_rear_display_fingerprint_to_error,
                R.raw.biometricprompt_sfps_rear_display_fingerprint_to_error_90,
                R.raw.biometricprompt_sfps_rear_display_fingerprint_to_error_180,
                R.raw.biometricprompt_sfps_rear_display_fingerprint_to_error_270,
                R.raw.biometricprompt_sfps_error_to_fingerprint,
                R.raw.biometricprompt_sfps_error_to_fingerprint_90,
                R.raw.biometricprompt_sfps_error_to_fingerprint_180,
                R.raw.biometricprompt_sfps_error_to_fingerprint_270,
                R.raw.biometricprompt_sfps_rear_display_error_to_fingerprint,
                R.raw.biometricprompt_sfps_rear_display_error_to_fingerprint_90,
                R.raw.biometricprompt_sfps_rear_display_error_to_fingerprint_180,
                R.raw.biometricprompt_sfps_rear_display_error_to_fingerprint_270,
                R.raw.biometricprompt_sfps_fingerprint_to_success,
                R.raw.biometricprompt_sfps_fingerprint_to_success_90,
                R.raw.biometricprompt_sfps_fingerprint_to_success_180,
                R.raw.biometricprompt_sfps_fingerprint_to_success_270,
                R.raw.biometricprompt_sfps_rear_display_fingerprint_to_success,
                R.raw.biometricprompt_sfps_rear_display_fingerprint_to_success_90,
                R.raw.biometricprompt_sfps_rear_display_fingerprint_to_success_180,
                R.raw.biometricprompt_sfps_rear_display_fingerprint_to_success_270,
            )
        } else {
            listOf(
                R.raw.fingerprint_dialogue_error_to_fingerprint_lottie,
                R.raw.fingerprint_dialogue_error_to_success_lottie,
                R.raw.fingerprint_dialogue_fingerprint_to_error_lottie,
                R.raw.fingerprint_dialogue_fingerprint_to_success_lottie,
            )
        }

    /** Face iconView assets for caching */
    fun getFaceAssetsList(): List<Int> =
        listOf(
            R.raw.face_dialog_wink_from_dark,
            R.raw.face_dialog_dark_to_checkmark,
            R.raw.face_dialog_dark_to_error,
            R.raw.face_dialog_error_to_idle,
            R.raw.face_dialog_idle_static,
            R.raw.face_dialog_authenticating,
        )

    fun animatingFromSfpsAuthenticating(asset: Int): Boolean =
        asset in sfpsFpToErrorAssets ||
            asset in sfpsFpToUnlockAssets ||
            asset in sfpsFpToSuccessAssets

    private val sfpsFpToErrorAssets: List<Int> =
        listOf(
            R.raw.biometricprompt_sfps_fingerprint_to_error,
            R.raw.biometricprompt_sfps_fingerprint_to_error_90,
            R.raw.biometricprompt_sfps_fingerprint_to_error_180,
            R.raw.biometricprompt_sfps_fingerprint_to_error_270,
            R.raw.biometricprompt_sfps_rear_display_fingerprint_to_error,
            R.raw.biometricprompt_sfps_rear_display_fingerprint_to_error_90,
            R.raw.biometricprompt_sfps_rear_display_fingerprint_to_error_180,
            R.raw.biometricprompt_sfps_rear_display_fingerprint_to_error_270,
        )

    private val sfpsFpToUnlockAssets: List<Int> =
        listOf(
            R.raw.biometricprompt_sfps_fingerprint_to_unlock,
            R.raw.biometricprompt_sfps_fingerprint_to_unlock_90,
            R.raw.biometricprompt_sfps_fingerprint_to_unlock_180,
            R.raw.biometricprompt_sfps_fingerprint_to_unlock_270,
            R.raw.biometricprompt_sfps_rear_display_fingerprint_to_unlock,
            R.raw.biometricprompt_sfps_rear_display_fingerprint_to_unlock_90,
            R.raw.biometricprompt_sfps_rear_display_fingerprint_to_unlock_180,
            R.raw.biometricprompt_sfps_rear_display_fingerprint_to_unlock_270,
        )

    private val sfpsFpToSuccessAssets: List<Int> =
        listOf(
            R.raw.biometricprompt_sfps_fingerprint_to_success,
            R.raw.biometricprompt_sfps_fingerprint_to_success_90,
            R.raw.biometricprompt_sfps_fingerprint_to_success_180,
            R.raw.biometricprompt_sfps_fingerprint_to_success_270,
            R.raw.biometricprompt_sfps_rear_display_fingerprint_to_success,
            R.raw.biometricprompt_sfps_rear_display_fingerprint_to_success_90,
            R.raw.biometricprompt_sfps_rear_display_fingerprint_to_success_180,
            R.raw.biometricprompt_sfps_rear_display_fingerprint_to_success_270,
        )
}
