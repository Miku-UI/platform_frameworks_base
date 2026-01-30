/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apac he License, Version 2.0 (the "License");
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

package com.android.systemui.biometrics.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Pattern
import androidx.compose.material.icons.filled.Pin
import androidx.compose.ui.graphics.vector.ImageVector
import com.android.systemui.biometrics.domain.interactor.PromptSelectorInteractor
import com.android.systemui.biometrics.shared.model.FallbackOptionModel
import com.android.systemui.biometrics.shared.model.PromptKind
import com.android.systemui.biometrics.shared.model.WatchRangingState
import com.android.systemui.res.R
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** ViewModel for the fallback view in Biometric Prompt */
class PromptFallbackViewModel
@AssistedInject
constructor(val promptSelectorInteractor: PromptSelectorInteractor) {
    @AssistedFactory
    interface Factory {
        fun create(): PromptFallbackViewModel
    }

    private val watchRangingState: Flow<WatchRangingState> =
        promptSelectorInteractor.watchRangingState
    val fallbackOptions: Flow<List<FallbackOptionModel>> = promptSelectorInteractor.fallbackOptions

    private val identityCheckActive: Flow<Boolean> = promptSelectorInteractor.isIdentityCheckActive

    private val credentialAllowed: Flow<Boolean> = promptSelectorInteractor.isCredentialAllowed

    private val credentialKind: Flow<PromptKind> = promptSelectorInteractor.credentialKind

    /** Whether the credential button is enabled */
    val icCredentialButtonEnabled: Flow<Boolean> =
        watchRangingState.map { it == WatchRangingState.WATCH_RANGING_SUCCESSFUL }

    /** The subtitle to display for the credential button */
    val icCredentialSubtitle: Flow<Int?> =
        watchRangingState.map { status ->
            when (status) {
                WatchRangingState.WATCH_RANGING_STARTED -> {
                    R.string.biometric_dialog_identity_check_watch_ranging
                }
                WatchRangingState.WATCH_RANGING_STOPPED -> {
                    R.string.biometric_dialog_unavailable
                }
                else -> {
                    null
                }
            }
        }

    /** Whether to show the identity check footer text */
    val icShowFooter: Flow<Boolean> =
        watchRangingState.map { status ->
            status == WatchRangingState.WATCH_RANGING_STOPPED ||
                status == WatchRangingState.WATCH_RANGING_IDLE
        }

    /** Whether the credential fallback button should be shown */
    val showCredential: Flow<Boolean> =
        combine(credentialAllowed, identityCheckActive) { credentialAllowed, identityCheckActive ->
            credentialAllowed && !identityCheckActive
        }

    /** Whether to show the manage identity check button */
    val showManageIdentityCheck: Flow<Boolean> =
        combine(credentialAllowed, identityCheckActive) { credentialAllowed, identityCheckActive ->
            credentialAllowed && identityCheckActive
        }

    /** Icon to be used for the credential kind */
    val credentialKindIcon: Flow<ImageVector> =
        credentialKind.map { kind ->
            when (kind) {
                PromptKind.Pin -> Icons.Filled.Pin
                PromptKind.Password -> Icons.Filled.Password
                PromptKind.Pattern -> Icons.Filled.Pattern
                else -> {
                    Icons.Filled.Password
                }
            }
        }

    /** Matching string resource for the user credential kind */
    val credentialKindText: Flow<Int> =
        credentialKind.map { kind ->
            when (kind) {
                PromptKind.Pin -> R.string.biometric_dialog_use_pin
                PromptKind.Password -> R.string.biometric_dialog_use_password
                PromptKind.Pattern -> R.string.biometric_dialog_use_pattern
                else -> {
                    R.string.biometric_dialog_use_password
                }
            }
        }

    /** Launches intent for manage identity check settings page */
    fun manageIdentityCheck(context: Context) {
        val identityCheckSettingsAction: String =
            context.getString(com.android.internal.R.string.identity_check_settings_action)
        val intent =
            Intent(identityCheckSettingsAction.ifEmpty { Settings.ACTION_SETTINGS })
                .setPackage(
                    context.getString(
                        com.android.internal.R.string.identity_check_settings_package_name
                    )
                )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
