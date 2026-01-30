/*
 * Copyright 2025 The Android Open Source Project
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

import androidx.annotation.StringRes

/** Models the action button on the secure lock device biometric auth bouncer. */
sealed class SecureLockDeviceBouncerActionButtonModel(
    /** The resource Id of the text to be shown on the button. */
    @StringRes val labelResId: Int,
    @StringRes val contentDescriptionId: Int? = null,
) {
    data class ConfirmStrongBiometricAuthButtonModel(
        @StringRes private val labelResourceId: Int,
        @StringRes private val contentDescId: Int,
    ) : SecureLockDeviceBouncerActionButtonModel(labelResourceId, contentDescId)

    data class TryAgainButtonModel(
        @StringRes private val labelResourceId: Int,
        @StringRes private val contentDescId: Int,
    ) : SecureLockDeviceBouncerActionButtonModel(labelResourceId, contentDescId)
}
