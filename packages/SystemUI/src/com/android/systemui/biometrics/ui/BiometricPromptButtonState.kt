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

package com.android.systemui.biometrics.ui

/** The state of the positive (right) button in the biometric prompt. */
sealed class PositiveButtonState {
    // Do not show button
    data object Gone : PositiveButtonState()

    // Confirmation button after auth
    data object Confirm : PositiveButtonState()

    // Try again button after failure
    data object TryAgain : PositiveButtonState()
}

/** The state of the negative (left) button in the biometric prompt. */
sealed class NegativeButtonState(open val text: String?) {
    // Do not show button
    data object Gone : NegativeButtonState(null)

    // Cancel, default or after auth
    data class Cancel(override val text: String) : NegativeButtonState(text)

    // App provided setNegativeButton
    data class SetNegative(override val text: String) : NegativeButtonState(text)

    // Single fallback option
    data class SingleFallback(override val text: String) : NegativeButtonState(text)

    // Use credential button
    data class UseCredential(override val text: String) : NegativeButtonState(text)

    // Fallback option page button
    data class FallbackOptions(override val text: String) : NegativeButtonState(text)
}
