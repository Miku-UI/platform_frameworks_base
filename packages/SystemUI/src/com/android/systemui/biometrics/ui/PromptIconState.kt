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

import android.annotation.RawRes
import com.android.systemui.biometrics.ui.viewmodel.BiometricAuthIconViewModel

/* Class for handling the state of the icon in Biometric prompt */
data class PromptIconState(
    @RawRes val asset: Int,
    val shouldAnimate: Boolean,
    val shouldLoop: Boolean,
    val contentDescriptionId: Int,
    val rotation: Float,
    val activeBiometricAuthType: BiometricAuthIconViewModel.BiometricAuthModalities,
    val showingError: Boolean,
)
