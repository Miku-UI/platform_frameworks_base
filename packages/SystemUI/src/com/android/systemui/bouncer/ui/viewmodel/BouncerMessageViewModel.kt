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

package com.android.systemui.bouncer.ui.viewmodel

import android.content.Context
import android.security.Flags.secureLockDevice
import android.util.PluralsMessageFormatter
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.systemui.authentication.domain.interactor.AuthenticationInteractor
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.biometrics.shared.model.BiometricModalities
import com.android.systemui.bouncer.domain.interactor.BouncerInteractor
import com.android.systemui.bouncer.domain.interactor.SimBouncerInteractor
import com.android.systemui.bouncer.shared.model.BouncerMessagePair
import com.android.systemui.bouncer.shared.model.BouncerMessageStrings
import com.android.systemui.bouncer.shared.model.primaryMessage
import com.android.systemui.bouncer.shared.model.secondaryMessage
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.deviceentry.domain.interactor.BiometricMessageInteractor
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryBiometricsAllowedInteractor
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryFaceAuthInteractor
import com.android.systemui.deviceentry.domain.interactor.DeviceUnlockedInteractor
import com.android.systemui.deviceentry.shared.model.DeviceEntryRestrictionReason
import com.android.systemui.deviceentry.shared.model.FaceFailureMessage
import com.android.systemui.deviceentry.shared.model.FaceLockoutMessage
import com.android.systemui.deviceentry.shared.model.FaceTimeoutMessage
import com.android.systemui.deviceentry.shared.model.FingerprintFailureMessage
import com.android.systemui.deviceentry.shared.model.FingerprintLockoutMessage
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.res.R.string.kg_too_many_failed_attempts_countdown
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.securelockdevice.domain.interactor.SecureLockDeviceInteractor
import com.android.systemui.user.ui.viewmodel.UserSwitcherViewModel
import com.android.systemui.util.kotlin.Utils.Companion.sample
import com.android.systemui.util.kotlin.combine
import com.android.systemui.util.time.SystemClock
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlin.math.ceil
import kotlin.math.max
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** Holds UI state for the 2-line status message shown on the bouncer. */
class BouncerMessageViewModel
@AssistedInject
constructor(
    @Application private val applicationContext: Context,
    private val bouncerInteractor: BouncerInteractor,
    private val simBouncerInteractor: SimBouncerInteractor,
    private val authenticationInteractor: AuthenticationInteractor,
    private val userSwitcherViewModel: UserSwitcherViewModel,
    private val clock: SystemClock,
    private val biometricMessageInteractor: BiometricMessageInteractor,
    private val faceAuthInteractor: DeviceEntryFaceAuthInteractor,
    private val deviceUnlockedInteractor: DeviceUnlockedInteractor,
    private val deviceEntryBiometricsAllowedInteractor: DeviceEntryBiometricsAllowedInteractor,
    private val secureLockDeviceInteractor: SecureLockDeviceInteractor,
) : ExclusiveActivatable() {
    /**
     * A message shown when the user has attempted the wrong credential too many times and now must
     * wait a while before attempting to authenticate again.
     *
     * This is updated every second (countdown) during the lockout. When lockout is not active, this
     * is `null` and no lockout message should be shown.
     */
    private val lockoutMessage: MutableStateFlow<MessageViewModel?> = MutableStateFlow(null)

    /** Whether there is a lockout message that is available to be shown in the status message. */
    val isLockoutMessagePresent: Flow<Boolean> = lockoutMessage.map { it != null }

    /** The user-facing message to show in the bouncer. */
    val message: MutableStateFlow<MessageViewModel?> = MutableStateFlow(null)

    override suspend fun onActivated(): Nothing {
        if (!SceneContainerFlag.isEnabled) {
            return awaitCancellation()
        }

        coroutineScope {
            launch {
                // Update the lockout countdown whenever the selected user is switched.
                userSwitcherViewModel.selectedUser.collect { startLockoutCountdown() }
            }

            launch { defaultBouncerMessageInitializer() }
            launch { listenForSimBouncerEvents() }
            launch { listenForBouncerEvents() }
            launch { listenForFaceMessages() }
            launch { listenForFingerprintMessages() }
            awaitCancellation()
        }
    }

    /** Initializes the bouncer message to default whenever it is shown. */
    fun onShown() {
        showDefaultMessage()
    }

    /** Reset the message shown on the bouncer to the default message. */
    fun showDefaultMessage() {
        resetToDefault.tryEmit(Unit)
    }

    private val resetToDefault = MutableSharedFlow<Unit>(replay = 1)

    private var lockoutCountdownJob: Job? = null

    private suspend fun defaultBouncerMessageInitializer() {
        resetToDefault.emit(Unit)
        authenticationInteractor.authenticationMethod
            .flatMapLatest { authMethod ->
                if (authMethod == AuthenticationMethodModel.Sim) {
                    resetToDefault.map {
                        MessageViewModel(simBouncerInteractor.getDefaultMessage())
                    }
                } else if (authMethod.isSecure) {
                    combine(
                        deviceUnlockedInteractor.deviceEntryRestrictionReason,
                        lockoutMessage,
                        deviceEntryBiometricsAllowedInteractor
                            .isFingerprintCurrentlyAllowedOnBouncer,
                        deviceEntryBiometricsAllowedInteractor.isFaceCurrentlyAllowedOnBouncer,
                        secureLockDeviceInteractor.enrolledStrongBiometricModalities,
                        resetToDefault,
                    ) {
                        deviceEntryRestrictedReason,
                        lockoutMsg,
                        isFpAllowedOnBouncer,
                        isFaceAllowedOnBouncer,
                        enrolledStrongBiometricModalities,
                        _ ->
                        lockoutMsg
                            ?: deviceEntryRestrictedReason.toMessage(
                                authMethod,
                                isFpAllowedOnBouncer,
                                isFaceAllowedOnBouncer,
                                enrolledStrongBiometricModalities,
                            )
                    }
                } else {
                    emptyFlow()
                }
            }
            .collect { messageViewModel -> message.value = messageViewModel }
    }

    private suspend fun listenForSimBouncerEvents() {
        // Listen for any events from the SIM bouncer and update the message shown on the bouncer.
        authenticationInteractor.authenticationMethod
            .flatMapLatest { authMethod ->
                if (authMethod == AuthenticationMethodModel.Sim) {
                    simBouncerInteractor.bouncerMessageChanged.map { simMsg ->
                        simMsg?.let { MessageViewModel(it) }
                    }
                } else {
                    emptyFlow()
                }
            }
            .collect {
                if (it != null) {
                    message.value = it
                } else {
                    resetToDefault.emit(Unit)
                }
            }
    }

    private suspend fun listenForFaceMessages() {
        // Listen for any events from face authentication and update the message shown on the
        // bouncer.
        biometricMessageInteractor.faceMessage
            .sample(
                authenticationInteractor.authenticationMethod,
                deviceEntryBiometricsAllowedInteractor.isFingerprintCurrentlyAllowedOnBouncer,
                deviceEntryBiometricsAllowedInteractor.isFaceCurrentlyAllowedOnBouncer,
                secureLockDeviceInteractor.isSecureLockDeviceEnabled,
            )
            .collectLatest {
                (
                    faceMessage,
                    authMethod,
                    fingerprintAllowedOnBouncer,
                    faceAllowedOnBouncer,
                    isSecureLockDeviceEnabled) ->
                val isFaceAuthStrong = faceAuthInteractor.isFaceAuthStrong()
                val defaultMessage =
                    BouncerMessageStrings.defaultMessage(
                        securityMode = authMethod,
                        fpAuthIsAllowed = fingerprintAllowedOnBouncer,
                        faceAuthIsAllowed = faceAllowedOnBouncer,
                        secureLockDeviceEnabled = isSecureLockDeviceEnabled,
                    )
                val defaultPrimaryMessage = defaultMessage.primaryMessage.toResString()

                message.value =
                    when (faceMessage) {
                        is FaceTimeoutMessage ->
                            MessageViewModel(
                                text = defaultPrimaryMessage,
                                secondaryText = faceMessage.message,
                                isUpdateAnimated = true,
                            )
                        is FaceLockoutMessage ->
                            if (isFaceAuthStrong)
                                BouncerMessageStrings.class3AuthLockedOut(
                                        authMethod,
                                        isSecureLockDeviceEnabled,
                                    )
                                    .toMessage()
                            else
                                BouncerMessageStrings.faceLockedOut(
                                        authMethod,
                                        fingerprintAllowedOnBouncer,
                                    )
                                    .toMessage()
                        is FaceFailureMessage ->
                            BouncerMessageStrings.incorrectFaceInput(
                                    authMethod,
                                    fingerprintAllowedOnBouncer,
                                )
                                .toMessage()
                        else -> {
                            MessageViewModel(
                                text = defaultPrimaryMessage,
                                secondaryText = faceMessage.message,
                                isUpdateAnimated = false,
                            )
                        }
                    }

                // Prevents secure lock device face lockout message from being cleared in
                // defaultBouncerMessageInitializer by DeviceEntryRestrictionReason update until
                // resetToDefault emits
                if (
                    secureLockDevice() &&
                        isSecureLockDeviceEnabled &&
                        faceMessage is FaceLockoutMessage
                ) {
                    lockoutMessage.value = message.value
                }
                delay(MESSAGE_DURATION)
                // Prevents secure lock device face lockout message from being cleared in
                // defaultBouncerMessageInitializer by DeviceEntryRestrictionReason update until
                // resetToDefault emits
                if (
                    secureLockDevice() &&
                        isSecureLockDeviceEnabled &&
                        faceMessage is FaceLockoutMessage
                ) {
                    lockoutMessage.value = null
                }
                resetToDefault.emit(Unit)
            }
    }

    private suspend fun listenForFingerprintMessages() {
        // Listen for any events from fingerprint authentication and update the message shown
        // on the bouncer.
        biometricMessageInteractor.fingerprintMessage
            .sample(
                authenticationInteractor.authenticationMethod,
                deviceEntryBiometricsAllowedInteractor.isFingerprintCurrentlyAllowedOnBouncer,
                deviceEntryBiometricsAllowedInteractor.isFaceCurrentlyAllowedOnBouncer,
                secureLockDeviceInteractor.isSecureLockDeviceEnabled,
            )
            .collectLatest {
                (
                    fingerprintMessage,
                    authMethod,
                    fingerprintAllowedOnBouncer,
                    faceAllowedOnBouncer,
                    isSecureLockDeviceEnabled) ->
                val defaultMessage =
                    BouncerMessageStrings.defaultMessage(
                        securityMode = authMethod,
                        fpAuthIsAllowed = fingerprintAllowedOnBouncer,
                        faceAuthIsAllowed = faceAllowedOnBouncer,
                        secureLockDeviceEnabled = isSecureLockDeviceEnabled,
                    )
                val defaultPrimaryMessage = defaultMessage.primaryMessage.toResString()
                message.value =
                    when (fingerprintMessage) {
                        is FingerprintLockoutMessage ->
                            BouncerMessageStrings.class3AuthLockedOut(
                                    authMethod,
                                    isSecureLockDeviceEnabled,
                                )
                                .toMessage()
                        is FingerprintFailureMessage ->
                            BouncerMessageStrings.incorrectFingerprintInput(authMethod).toMessage()
                        else ->
                            MessageViewModel(
                                text = defaultPrimaryMessage,
                                secondaryText = fingerprintMessage.message,
                                isUpdateAnimated = false,
                            )
                    }
                // Prevents secure lock device fingerprint lockout message from being cleared in
                // defaultBouncerMessageInitializer by DeviceEntryRestrictionReason update until
                // resetToDefault emits
                if (
                    secureLockDevice() &&
                        isSecureLockDeviceEnabled &&
                        fingerprintMessage is FingerprintLockoutMessage
                ) {
                    lockoutMessage.value = message.value
                }
                delay(MESSAGE_DURATION)
                if (
                    secureLockDevice() &&
                        isSecureLockDeviceEnabled &&
                        fingerprintMessage is FingerprintLockoutMessage
                ) {
                    lockoutMessage.value = null
                }
                resetToDefault.emit(Unit)
            }
    }

    private suspend fun listenForBouncerEvents() {
        coroutineScope {
            // Keeps the lockout message up-to-date.
            launch { bouncerInteractor.onLockoutStarted.collect { startLockoutCountdown() } }

            // Start already active lockdown if it exists
            launch { startLockoutCountdown() }

            // Listens to relevant bouncer events
            launch {
                bouncerInteractor.onIncorrectBouncerInput
                    .sample(
                        authenticationInteractor.authenticationMethod,
                        deviceEntryBiometricsAllowedInteractor
                            .isFingerprintCurrentlyAllowedOnBouncer,
                        secureLockDeviceInteractor.isSecureLockDeviceEnabled,
                    )
                    .collectLatest {
                        (_, authMethod, isFingerprintAllowed, isSecureLockDeviceEnabled) ->
                        message.emit(
                            BouncerMessageStrings.incorrectSecurityInput(
                                    authMethod,
                                    isFingerprintAllowed,
                                    isSecureLockDeviceEnabled,
                                )
                                .toMessage()
                        )
                        delay(MESSAGE_DURATION)
                        resetToDefault.emit(Unit)
                    }
            }
        }
    }

    private fun DeviceEntryRestrictionReason?.toMessage(
        authMethod: AuthenticationMethodModel,
        isFingerprintAllowedOnBouncer: Boolean,
        isFaceAllowedOnBouncer: Boolean,
        enrolledStrongBiometricModalities: BiometricModalities,
    ): MessageViewModel {
        return when (this) {
            DeviceEntryRestrictionReason.SecureLockDevicePrimaryAuth ->
                BouncerMessageStrings.authRequiredForSecureLockDevicePrimaryAuth(authMethod)
            DeviceEntryRestrictionReason.SecureLockDeviceStrongBiometricOnlyAuth ->
                BouncerMessageStrings.authRequiredForSecureLockDeviceStrongBiometricAuth(
                    enrolledStrongBiometricModalities.hasFingerprint,
                    enrolledStrongBiometricModalities.hasFace,
                )
            DeviceEntryRestrictionReason.UserLockdown ->
                BouncerMessageStrings.authRequiredAfterUserLockdown(authMethod)
            DeviceEntryRestrictionReason.DeviceNotUnlockedSinceReboot ->
                BouncerMessageStrings.authRequiredAfterReboot(authMethod)
            DeviceEntryRestrictionReason.PolicyLockdown ->
                BouncerMessageStrings.authRequiredAfterAdminLockdown(authMethod)
            DeviceEntryRestrictionReason.UnattendedUpdate ->
                BouncerMessageStrings.authRequiredForUnattendedUpdate(authMethod)
            DeviceEntryRestrictionReason.DeviceNotUnlockedSinceMainlineUpdate ->
                BouncerMessageStrings.authRequiredForMainlineUpdate(authMethod)
            DeviceEntryRestrictionReason.SecurityTimeout ->
                BouncerMessageStrings.authRequiredAfterPrimaryAuthTimeout(authMethod)
            DeviceEntryRestrictionReason.StrongBiometricsLockedOut ->
                BouncerMessageStrings.class3AuthLockedOut(authMethod)
            DeviceEntryRestrictionReason.NonStrongFaceLockedOut ->
                BouncerMessageStrings.faceLockedOut(authMethod, isFingerprintAllowedOnBouncer)
            DeviceEntryRestrictionReason.NonStrongBiometricsSecurityTimeout ->
                BouncerMessageStrings.nonStrongAuthTimeout(
                    authMethod,
                    isFingerprintAllowedOnBouncer,
                )
            DeviceEntryRestrictionReason.TrustAgentDisabled ->
                BouncerMessageStrings.trustAgentDisabled(authMethod, isFingerprintAllowedOnBouncer)
            DeviceEntryRestrictionReason.AdaptiveAuthRequest ->
                BouncerMessageStrings.authRequiredAfterAdaptiveAuthRequest(
                    authMethod,
                    isFingerprintAllowedOnBouncer,
                )
            else -> BouncerMessageStrings.defaultMessage(authMethod, isFingerprintAllowedOnBouncer)
        }.toMessage()
    }

    private fun BouncerMessagePair.toMessage(): MessageViewModel {
        val primaryMsg = this.primaryMessage.toResString()
        val secondaryMsg = this.secondaryMessage.toResString()
        return MessageViewModel(primaryMsg, secondaryText = secondaryMsg, isUpdateAnimated = true)
    }

    /** Shows the countdown message and refreshes it every second. */
    private suspend fun startLockoutCountdown() {
        lockoutCountdownJob?.cancel()
        lockoutCountdownJob = coroutineScope {
            launch {
                authenticationInteractor.authenticationMethod.collectLatest { authMethod ->
                    do {
                        val remainingSeconds = remainingLockoutSeconds()
                        val authLockedOutMsg =
                            BouncerMessageStrings.primaryAuthLockedOut(authMethod)
                        lockoutMessage.value =
                            if (remainingSeconds > 0) {

                                MessageViewModel(
                                    text =
                                        kg_too_many_failed_attempts_countdown.toPluralString(
                                            mutableMapOf<String, Any>(
                                                Pair("count", remainingSeconds)
                                            )
                                        ),
                                    secondaryText = authLockedOutMsg.secondaryMessage.toResString(),
                                    isUpdateAnimated = false,
                                )
                            } else {
                                null
                            }
                        delay(1.seconds)
                    } while (remainingSeconds > 0)
                    lockoutCountdownJob = null
                }
            }
        }
    }

    private fun remainingLockoutSeconds(): Int {
        val endTimestampMs = authenticationInteractor.lockoutEndTimestamp ?: 0
        val remainingMs = max(0, endTimestampMs - clock.elapsedRealtime())
        return ceil(remainingMs / 1000f).toInt()
    }

    private fun Int.toPluralString(formatterArgs: Map<String, Any>): String =
        PluralsMessageFormatter.format(applicationContext.resources, formatterArgs, this)

    private fun Int.toResString(): String = if (this == 0) "" else applicationContext.getString(this)

    @AssistedFactory
    interface Factory {
        fun create(): BouncerMessageViewModel
    }

    companion object {
        private const val MESSAGE_DURATION = 2000L
    }
}

/** Data class that represents the status message show on the bouncer. */
data class MessageViewModel(
    val text: String,
    val secondaryText: String? = null,
    /**
     * Whether updates to the message should be cross-animated from one message to another.
     *
     * If `false`, no animation should be applied, the message text should just be replaced
     * instantly.
     */
    val isUpdateAnimated: Boolean = true,
)
