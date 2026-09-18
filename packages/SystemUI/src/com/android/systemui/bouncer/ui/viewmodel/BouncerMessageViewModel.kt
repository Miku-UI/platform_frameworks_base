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
import android.security.Flags.lockscreenLargerTimeoutTimeUnits
import android.security.Flags.secureLockDevice
import android.util.PluralsMessageFormatter
import android.view.accessibility.AccessibilityManager.FLAG_CONTENT_TEXT
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.systemui.accessibility.domain.interactor.AccessibilityInteractor
import com.android.systemui.authentication.domain.interactor.AuthenticationInteractor
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.biometrics.shared.model.BiometricModalities
import com.android.systemui.bouncer.domain.interactor.BouncerInteractor
import com.android.systemui.bouncer.domain.interactor.SimBouncerInteractor
import com.android.systemui.bouncer.shared.model.BouncerMessagePair
import com.android.systemui.bouncer.shared.model.BouncerMessageStrings
import com.android.systemui.bouncer.shared.model.LockoutMessageModel
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
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
    private val a11yInteractor: AccessibilityInteractor,
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
    val message: MutableStateFlow<MessageViewModel?> = MutableStateFlow(defaultMessage())

    /**
     * The duration of the message shown on the bouncer. The [DEFAULT_MESSAGE_DURATION] is
     * overridden if the user has configured extra timeout in their accessibility controls.
     */
    private val messageDuration by lazy {
        a11yInteractor.getRecommendedTimeout(DEFAULT_MESSAGE_DURATION, FLAG_CONTENT_TEXT)
    }

    override suspend fun onActivated() {
        if (!SceneContainerFlag.isEnabled) {
            return
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

    private fun defaultMessage(): MessageViewModel {
        val authMethod = authenticationInteractor.authenticationMethod.value
        return if (authMethod == AuthenticationMethodModel.Sim) {
            MessageViewModel(simBouncerInteractor.getDefaultMessage())
        } else {
            val restrictionReason = deviceUnlockedInteractor.currentDeviceEntryRestrictionReason()
            restrictionReason.toMessage(
                authMethod,
                deviceEntryBiometricsAllowedInteractor.isFingerprintCurrentlyAllowedOnBouncer.value,
                secureLockDeviceInteractor.enrolledStrongBiometricModalities.value,
            )
        }
    }

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
                delay(messageDuration)
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
                delay(messageDuration)
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
                        authenticationInteractor.isDuplicateAttempt,
                    )
                    .collectLatest {
                        (
                            _,
                            authMethod,
                            isFingerprintAllowed,
                            isSecureLockDeviceEnabled,
                            isDuplicate) ->
                        authenticationInteractor.lockoutEndTime?.let {
                            if (
                                !lockscreenLargerTimeoutTimeUnits() ||
                                    it < clock.elapsedRealtime().milliseconds
                            ) {
                                // Skip setting the message only when there is an active lockout,
                                // since the countdown job should be handling it.
                                return@let
                            }
                            startLockoutCountdown()
                            return@collectLatest
                        }
                        message.emit(
                            BouncerMessageStrings.incorrectSecurityInput(
                                    authMethod,
                                    isFingerprintAllowed,
                                    isSecureLockDeviceEnabled,
                                    isDuplicate,
                                )
                                .toMessage()
                        )
                        delay(messageDuration)
                        resetToDefault.emit(Unit)
                    }
            }
        }
    }

    private fun DeviceEntryRestrictionReason?.toMessage(
        authMethod: AuthenticationMethodModel,
        isFingerprintAllowedOnBouncer: Boolean,
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
            DeviceEntryRestrictionReason.UserNotUnlockedSinceSignOut ->
                BouncerMessageStrings.authRequiredToSignIn(authMethod)
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

    private fun LockoutMessageModel.toMessage(): MessageViewModel {
        val resources = applicationContext.resources
        val secondaryFormatterArgs = secondaryFormatterArgs(resources)
        return MessageViewModel(
            text = primaryMessage.toPluralString(primaryFormatterArgs()),
            secondaryText =
                if (secondaryFormatterArgs != null) {
                    secondaryMessage.toPluralString(secondaryFormatterArgs)
                } else {
                    secondaryMessage.toResString()
                },
            isUpdateAnimated = false,
        )
    }

    /** Shows the countdown message and refreshes it every second. */
    private suspend fun startLockoutCountdown() {
        lockoutCountdownJob?.cancel()
        lockoutCountdownJob = coroutineScope {
            launch {
                authenticationInteractor.authenticationMethod.collectLatest { authMethod ->
                    do {
                        val remainingSeconds = remainingLockoutSeconds()
                        lockoutMessage.value =
                            if (remainingSeconds > 0) {
                                BouncerMessageStrings.primaryAuthLockedOut(
                                        authMethod,
                                        remainingSeconds,
                                    )
                                    .toMessage()
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

    private fun remainingLockoutSeconds(): Long {
        val endTime = authenticationInteractor.lockoutEndTime?.inWholeMilliseconds ?: 0
        val remainingMs = max(0, endTime - clock.elapsedRealtime())
        return if (lockscreenLargerTimeoutTimeUnits()) {
            (remainingMs + 999L) / 1000L
        } else {
            ceil(remainingMs / 1000f).toLong()
        }
    }

    private fun Int.toPluralString(formatterArgs: Map<String, Any>): String =
        PluralsMessageFormatter.format(applicationContext.resources, formatterArgs, this)

    private fun Int.toResString(): String =
        if (this == 0) "" else applicationContext.getString(this)

    @AssistedFactory
    interface Factory {
        fun create(): BouncerMessageViewModel
    }

    companion object {
        val DEFAULT_MESSAGE_DURATION = 2.seconds
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
