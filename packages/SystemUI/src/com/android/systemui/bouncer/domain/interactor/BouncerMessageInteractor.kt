/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.bouncer.domain.interactor

import android.content.res.Resources
import android.hardware.biometrics.BiometricFaceConstants
import android.hardware.biometrics.BiometricSourceType
import android.os.CountDownTimer
import android.security.Flags.secureLockDevice
import com.android.internal.widget.LockPatternUtils.StrongAuthTracker.STRONG_AUTH_NOT_REQUIRED
import com.android.keyguard.KeyguardSecurityModel
import com.android.keyguard.KeyguardSecurityModel.SecurityMode
import com.android.keyguard.KeyguardUpdateMonitor
import com.android.keyguard.KeyguardUpdateMonitorCallback
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.biometrics.data.repository.FacePropertyRepository
import com.android.systemui.biometrics.shared.model.SensorStrength
import com.android.systemui.bouncer.data.repository.BouncerMessageRepository
import com.android.systemui.bouncer.shared.model.BouncerMessageModel
import com.android.systemui.bouncer.shared.model.BouncerMessageStrings
import com.android.systemui.bouncer.shared.model.LockoutMessageModel
import com.android.systemui.bouncer.shared.model.Message
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryBiometricsAllowedInteractor
import com.android.systemui.flags.SystemPropertiesHelper
import com.android.systemui.keyguard.data.repository.BiometricSettingsRepository
import com.android.systemui.keyguard.data.repository.TrustRepository
import com.android.systemui.keyguard.shared.model.AuthenticationFlags
import com.android.systemui.res.R
import com.android.systemui.securelockdevice.domain.interactor.SecureLockDeviceInteractor
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.user.data.repository.UserRepository
import com.android.systemui.util.kotlin.Nonuple
import com.android.systemui.util.kotlin.combine
import dagger.Lazy
import javax.inject.Inject
import kotlin.math.roundToLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val SYS_BOOT_REASON_PROP = "sys.boot.reason.last"
private const val REBOOT_MAINLINE_UPDATE = "reboot,mainline_update"
private const val TAG = "BouncerMessageInteractor"

/** Handles business logic for the primary bouncer message area. */
@SysUISingleton
class BouncerMessageInteractor
@Inject
constructor(
    private val repository: BouncerMessageRepository,
    private val userRepository: UserRepository,
    private val countDownTimerUtil: CountDownTimerUtil,
    updateMonitor: KeyguardUpdateMonitor,
    trustRepository: TrustRepository,
    private val biometricSettingsRepository: BiometricSettingsRepository,
    private val systemPropertiesHelper: SystemPropertiesHelper,
    primaryBouncerInteractor: PrimaryBouncerInteractor,
    @Application private val applicationScope: CoroutineScope,
    @Background val backgroundScope: CoroutineScope,
    private val facePropertyRepository: FacePropertyRepository,
    private val securityModel: KeyguardSecurityModel,
    deviceEntryBiometricsAllowedInteractor: DeviceEntryBiometricsAllowedInteractor,
    private val secureLockDeviceInteractor: Lazy<SecureLockDeviceInteractor>,
    @ShadeDisplayAware private val resources: Resources,
) {
    private val isFaceAuthCurrentlyAllowedOnBouncer =
        deviceEntryBiometricsAllowedInteractor.isFaceCurrentlyAllowedOnBouncer.stateIn(
            applicationScope,
            SharingStarted.Eagerly,
            false,
        )

    private val isFingerprintAuthCurrentlyAllowedOnBouncer =
        deviceEntryBiometricsAllowedInteractor.isFingerprintCurrentlyAllowedOnBouncer.stateIn(
            applicationScope,
            SharingStarted.Eagerly,
            false,
        )

    private val authenticationFlags: StateFlow<AuthenticationFlags> =
        biometricSettingsRepository.authenticationFlags.stateIn(
            applicationScope,
            SharingStarted.Eagerly,
            AuthenticationFlags(currentUserId, STRONG_AUTH_NOT_REQUIRED),
        )

    private val currentSecurityMode
        get() = securityModel.getSecurityMode(currentUserId)

    private val currentUserId
        get() = userRepository.getSelectedUserInfo().id

    private val kumCallback =
        object : KeyguardUpdateMonitorCallback() {
            override fun onBiometricAuthFailed(biometricSourceType: BiometricSourceType?) {
                // Only show the biometric failure messages if the biometric is NOT locked out.
                // If the biometric is locked out, rely on the lock out message to show
                // the lockout message & don't override it with the failure message.
                if (
                    (biometricSourceType == BiometricSourceType.FACE &&
                        deviceEntryBiometricsAllowedInteractor.isFaceLockedOut.value) ||
                        (biometricSourceType == BiometricSourceType.FINGERPRINT &&
                            deviceEntryBiometricsAllowedInteractor.isFingerprintLockedOut.value)
                ) {
                    return
                }
                setMessage(
                    when (biometricSourceType) {
                        BiometricSourceType.FINGERPRINT ->
                            BouncerMessageStrings.incorrectFingerprintInput(
                                    currentSecurityMode.toAuthModel()
                                )
                                .toMessage()
                        BiometricSourceType.FACE ->
                            BouncerMessageStrings.incorrectFaceInput(
                                    currentSecurityMode.toAuthModel(),
                                    isFingerprintAuthCurrentlyAllowedOnBouncer.value,
                                )
                                .toMessage()
                        else ->
                            BouncerMessageStrings.defaultMessage(
                                    currentSecurityMode.toAuthModel(),
                                    isFingerprintAuthCurrentlyAllowedOnBouncer.value,
                                    isFaceAuthCurrentlyAllowedOnBouncer.value,
                                )
                                .toMessage()
                    },
                    biometricSourceType,
                )
            }

            override fun onBiometricAcquired(
                biometricSourceType: BiometricSourceType?,
                acquireInfo: Int,
            ) {
                if (
                    repository.getMessageSource() == BiometricSourceType.FACE &&
                        acquireInfo == BiometricFaceConstants.FACE_ACQUIRED_START
                ) {
                    setMessage(defaultMessage)
                }
            }

            override fun onBiometricAuthenticated(
                userId: Int,
                biometricSourceType: BiometricSourceType?,
                isStrongBiometric: Boolean,
            ) {
                if (secureLockDeviceInteractor.get().isSecureLockDeviceEnabled.value) {
                    val message =
                        BouncerMessageStrings.authRequiredForSecureLockDeviceStrongBiometricAuth(
                                isClass3FingerprintAuthEnrolledAndEnabled,
                                isClass3FaceAuthEnrolledAndEnabled,
                            )
                            .toMessage()
                    repository.setMessage(message, biometricSourceType)
                } else {
                    setMessage(defaultMessage, biometricSourceType)
                }
            }
        }

    private val isAnyBiometricsEnabledAndEnrolled =
        biometricSettingsRepository.isFaceAuthEnrolledAndEnabled.or(
            biometricSettingsRepository.isFingerprintEnrolledAndEnabled
        )

    private val wasRebootedForMainlineUpdate
        get() = systemPropertiesHelper.get(SYS_BOOT_REASON_PROP) == REBOOT_MAINLINE_UPDATE

    private val isFaceAuthClass3
        get() = facePropertyRepository.sensorInfo.value?.strength == SensorStrength.STRONG

    private val initialBouncerMessage: Flow<BouncerMessageModel> =
        combine(
                primaryBouncerInteractor.lastShownSecurityMode, // required to update defaultMessage
                authenticationFlags,
                trustRepository.isCurrentUserTrustManaged,
                isAnyBiometricsEnabledAndEnrolled,
                deviceEntryBiometricsAllowedInteractor.isFingerprintLockedOut,
                deviceEntryBiometricsAllowedInteractor.isFaceLockedOut,
                isFingerprintAuthCurrentlyAllowedOnBouncer,
                isFaceAuthCurrentlyAllowedOnBouncer,
                secureLockDeviceInteractor.get().enrolledStrongBiometricModalities,
                ::Nonuple,
            )
            .map {
                (
                    _,
                    flags,
                    _,
                    biometricsEnrolledAndEnabled,
                    fpLockedOut,
                    faceLockedOut,
                    isFingerprintAuthCurrentlyAllowedOnBouncer,
                    isFaceAuthCurrentlyAllowedOnBouncer,
                    enrolledStrongBiometricModalities) ->
                val isTrustUsuallyManaged = trustRepository.isCurrentUserTrustUsuallyManaged.value
                val trustOrBiometricsAvailable =
                    (isTrustUsuallyManaged || biometricsEnrolledAndEnabled)
                return@map if (
                    (fpLockedOut || faceLockedOut) &&
                        (flags.isPrimaryAuthRequiredForSecureLockDevice ||
                            flags.isStrongBiometricAuthRequiredForSecureLockDevice)
                ) {
                    BouncerMessageStrings.class3AuthLockedOut(
                            securityMode = currentSecurityMode.toAuthModel(),
                            isSecureLockDeviceEnabled = isSecureLockDeviceEnabled(),
                        )
                        .toMessage()
                } else if (flags.isPrimaryAuthRequiredForSecureLockDevice) {
                    BouncerMessageStrings.authRequiredForSecureLockDevicePrimaryAuth(
                            currentSecurityMode.toAuthModel()
                        )
                        .toMessage()
                } else if (flags.isStrongBiometricAuthRequiredForSecureLockDevice) {
                    BouncerMessageStrings.authRequiredForSecureLockDeviceStrongBiometricAuth(
                            enrolledStrongBiometricModalities.hasFingerprint,
                            enrolledStrongBiometricModalities.hasFace,
                        )
                        .toMessage()
                } else if (trustOrBiometricsAvailable && flags.isPrimaryAuthRequiredAfterReboot) {
                    if (wasRebootedForMainlineUpdate) {
                        BouncerMessageStrings.authRequiredForMainlineUpdate(
                                currentSecurityMode.toAuthModel()
                            )
                            .toMessage()
                    } else {
                        BouncerMessageStrings.authRequiredAfterReboot(
                                currentSecurityMode.toAuthModel()
                            )
                            .toMessage()
                    }
                } else if (trustOrBiometricsAvailable && flags.isPrimaryAuthRequiredAfterTimeout) {
                    BouncerMessageStrings.authRequiredAfterPrimaryAuthTimeout(
                            currentSecurityMode.toAuthModel()
                        )
                        .toMessage()
                } else if (flags.isPrimaryAuthRequiredAfterDpmLockdown) {
                    BouncerMessageStrings.authRequiredAfterAdminLockdown(
                            currentSecurityMode.toAuthModel()
                        )
                        .toMessage()
                } else if (
                    trustOrBiometricsAvailable && flags.isPrimaryAuthRequiredForUnattendedUpdate
                ) {
                    BouncerMessageStrings.authRequiredForUnattendedUpdate(
                            currentSecurityMode.toAuthModel()
                        )
                        .toMessage()
                } else if (
                    biometricSettingsRepository.isFingerprintEnrolledAndEnabled.value && fpLockedOut
                ) {
                    BouncerMessageStrings.class3AuthLockedOut(currentSecurityMode.toAuthModel())
                        .toMessage()
                } else if (
                    biometricSettingsRepository.isFaceAuthEnrolledAndEnabled.value && faceLockedOut
                ) {
                    if (isFaceAuthClass3) {
                        BouncerMessageStrings.class3AuthLockedOut(currentSecurityMode.toAuthModel())
                            .toMessage()
                    } else {
                        BouncerMessageStrings.faceLockedOut(
                                currentSecurityMode.toAuthModel(),
                                isFingerprintAuthCurrentlyAllowedOnBouncer,
                            )
                            .toMessage()
                    }
                } else if (flags.isSomeAuthRequiredAfterAdaptiveAuthRequest) {
                    BouncerMessageStrings.authRequiredAfterAdaptiveAuthRequest(
                            currentSecurityMode.toAuthModel(),
                            isFingerprintAuthCurrentlyAllowedOnBouncer,
                        )
                        .toMessage()
                } else if (
                    trustOrBiometricsAvailable &&
                        flags.strongerAuthRequiredAfterNonStrongBiometricsTimeout
                ) {
                    BouncerMessageStrings.nonStrongAuthTimeout(
                            currentSecurityMode.toAuthModel(),
                            isFingerprintAuthCurrentlyAllowedOnBouncer,
                        )
                        .toMessage()
                } else if (isTrustUsuallyManaged && flags.someAuthRequiredAfterUserRequest) {
                    BouncerMessageStrings.trustAgentDisabled(
                            currentSecurityMode.toAuthModel(),
                            isFingerprintAuthCurrentlyAllowedOnBouncer,
                        )
                        .toMessage()
                } else if (isTrustUsuallyManaged && flags.someAuthRequiredAfterTrustAgentExpired) {
                    BouncerMessageStrings.trustAgentDisabled(
                            currentSecurityMode.toAuthModel(),
                            isFingerprintAuthCurrentlyAllowedOnBouncer,
                        )
                        .toMessage()
                } else if (trustOrBiometricsAvailable && flags.isInUserLockdown) {
                    BouncerMessageStrings.authRequiredAfterUserLockdown(
                            currentSecurityMode.toAuthModel()
                        )
                        .toMessage()
                } else {
                    defaultMessage
                }
            }

    fun onPrimaryAuthLockedOut(secondsBeforeLockoutReset: Long) {
        val callback =
            object : CountDownTimerCallback {
                override fun onFinish() {
                    setMessage(defaultMessage)
                }

                override fun onTick(millisUntilFinished: Long) {
                    val secondsRemaining = (millisUntilFinished / 1000.0).roundToLong()
                    setMessage(
                        BouncerMessageStrings.primaryAuthLockedOut(
                                currentSecurityMode.toAuthModel(),
                                secondsRemaining,
                            )
                            .toMessage()
                    )
                }
            }
        val msBeforeReset = secondsBeforeLockoutReset * 1000
        countDownTimerUtil.startTimer(msBeforeReset, 1000, callback)
        val countdownUserId = currentUserId

        // The timer should be canceled when the selected user is changed during the countdown, as
        // the keyguard state is not reloaded. Otherwise, the countdown will incorrectly show for
        // the other user.
        backgroundScope.launch {
            // The coroutine should always complete or cancel by the end of the timeout.
            withTimeoutOrNull(msBeforeReset) {
                userRepository.selectedUser.first { it.userInfo.id != countdownUserId }
                countDownTimerUtil.cancelTimer()
                setMessage(defaultMessage)
            }
        }
    }

    fun onPrimaryAuthIncorrectAttempt(isDuplicate: Boolean) {
        setMessage(
            BouncerMessageStrings.incorrectSecurityInput(
                    currentSecurityMode.toAuthModel(),
                    isFingerprintAuthCurrentlyAllowedOnBouncer.value,
                    isSecureLockDeviceEnabled(),
                    isDuplicate,
                )
                .toMessage()
        )
    }

    fun setFingerprintAcquisitionMessage(value: String?) {
        setMessage(
            defaultMessage(
                securityMode = currentSecurityMode,
                secondaryMessage = value,
                fpAuthIsAllowed = isFingerprintAuthCurrentlyAllowedOnBouncer.value,
                faceAuthIsAllowed = isFaceAuthCurrentlyAllowedOnBouncer.value,
                isSecureLockDeviceEnabled = isSecureLockDeviceEnabled(),
            ),
            BiometricSourceType.FINGERPRINT,
        )
    }

    fun setUnlockToContinueMessage(value: String) {
        setMessage(
            defaultMessage(
                securityMode = currentSecurityMode,
                secondaryMessage = value,
                fpAuthIsAllowed = isFingerprintAuthCurrentlyAllowedOnBouncer.value,
                faceAuthIsAllowed = isFaceAuthCurrentlyAllowedOnBouncer.value,
            )
        )
    }

    fun setFaceAcquisitionMessage(value: String?) {
        setMessage(
            defaultMessage(
                securityMode = currentSecurityMode,
                secondaryMessage = value,
                fpAuthIsAllowed = isFingerprintAuthCurrentlyAllowedOnBouncer.value,
                faceAuthIsAllowed = isFaceAuthCurrentlyAllowedOnBouncer.value,
                isSecureLockDeviceEnabled = isSecureLockDeviceEnabled(),
            ),
            BiometricSourceType.FACE,
        )
    }

    fun setCustomMessage(value: String?) {
        setMessage(
            defaultMessage(
                securityMode = currentSecurityMode,
                secondaryMessage = value,
                fpAuthIsAllowed = isFingerprintAuthCurrentlyAllowedOnBouncer.value,
                faceAuthIsAllowed = isFaceAuthCurrentlyAllowedOnBouncer.value,
            )
        )
    }

    private val defaultMessage: BouncerMessageModel
        get() =
            BouncerMessageStrings.defaultMessage(
                    currentSecurityMode.toAuthModel(),
                    isFingerprintAuthCurrentlyAllowedOnBouncer.value,
                    isFaceAuthCurrentlyAllowedOnBouncer.value,
                    isSecureLockDeviceEnabled(),
                )
                .toMessage()

    private val isClass3FaceAuthEnrolledAndEnabled: Boolean
        get() = secureLockDeviceInteractor.get().hasFace.value

    private val isClass3FingerprintAuthEnrolledAndEnabled: Boolean
        get() = secureLockDeviceInteractor.get().hasFingerprint.value

    private fun isSecureLockDeviceEnabled(): Boolean {
        return secureLockDevice() &&
            secureLockDeviceInteractor.get().isSecureLockDeviceEnabled.value
    }

    fun onPrimaryBouncerUserInput() {
        setMessage(defaultMessage)
    }

    fun onSecureLockDevicePendingConfirmation() {
        if (!isSecureLockDeviceEnabled()) return

        setMessage(
            BouncerMessageStrings.pendingFaceAuthConfirmationForSecureLockDevice().toMessage()
        )
    }

    fun onSecureLockDeviceRetryAuthentication(showingError: Boolean) {
        if (!isSecureLockDeviceEnabled()) return

        if (showingError) {
            setMessage(
                BouncerMessageStrings.incorrectFaceInput(
                        currentSecurityMode.toAuthModel(),
                        secureLockDeviceInteractor.get().hasFingerprint.value,
                    )
                    .toMessage()
            )
        } else {
            setMessage(
                BouncerMessageStrings.retryAuthenticationForSecureLockDevice(
                        secureLockDeviceInteractor.get().hasFingerprint.value,
                        secureLockDeviceInteractor.get().hasFace.value,
                    )
                    .toMessage()
            )
        }
    }

    val bouncerMessage = repository.bouncerMessage

    init {
        updateMonitor.registerCallback(kumCallback)
        combine(
                primaryBouncerInteractor.isShowing,
                initialBouncerMessage,
                secureLockDeviceInteractor.get().isSecureLockDeviceEnabled,
            ) { showing, bouncerMessage, isSecureLockDeviceEnabled ->
                if (showing) {
                    bouncerMessage
                } else {
                    null
                }
            }
            .filterNotNull()
            .onEach { setMessage(it) }
            .launchIn(applicationScope)
    }

    private fun setMessage(message: BouncerMessageModel, source: BiometricSourceType? = null) {
        if (
            secureLockDevice() &&
                secureLockDeviceInteractor.get().suppressBouncerMessageUpdates.value
        ) {
            return
        }

        repository.setMessage(message, source)
    }

    fun onSecureLockDeviceUnlock() {
        if (!secureLockDevice()) return

        setMessage(defaultMessage)
    }

    private fun LockoutMessageModel.toMessage(): BouncerMessageModel {
        return BouncerMessageModel(
            message =
                Message(
                    messageResId = this.primaryMessage,
                    formatterArgs = this.primaryFormatterArgs(),
                    animate = false,
                ),
            secondaryMessage =
                Message(
                    messageResId = this.secondaryMessage,
                    formatterArgs = this.secondaryFormatterArgs(resources),
                    animate = false,
                ),
        )
    }
}

interface CountDownTimerCallback {
    fun onFinish()

    fun onTick(millisUntilFinished: Long)
}

@SysUISingleton
open class CountDownTimerUtil @Inject constructor() {

    private var instance: CountDownTimer? = null

    /**
     * Start a count down timer that runs for [millisInFuture] with a tick every [millisInterval],
     * only if another isn't already active.
     */
    fun startTimer(
        millisInFuture: Long,
        millisInterval: Long,
        callback: CountDownTimerCallback,
    ): CountDownTimer {
        synchronized(this) {
            if (instance == null) {
                instance =
                    object : CountDownTimer(millisInFuture, millisInterval) {
                            override fun onFinish() {
                                instance = null
                                callback.onFinish()
                            }

                            override fun onTick(millisUntilFinished: Long) =
                                callback.onTick(millisUntilFinished)
                        }
                        .start()
            }
            return instance!!
        }
    }

    fun cancelTimer() {
        synchronized(this) {
            instance?.cancel()
            instance = null
        }
    }
}

private fun Flow<Boolean>.or(anotherFlow: Flow<Boolean>) =
    this.combine(anotherFlow) { a, b -> a || b }

private fun defaultMessage(
    securityMode: SecurityMode,
    secondaryMessage: String?,
    fpAuthIsAllowed: Boolean,
    faceAuthIsAllowed: Boolean,
    isSecureLockDeviceEnabled: Boolean = false,
): BouncerMessageModel {
    if (secureLockDevice() && isSecureLockDeviceEnabled && secondaryMessage != null) {
        return BouncerMessageModel(
            message =
                Message(
                    messageResId = R.string.kg_prompt_title_after_secure_lock_device,
                    animate = false,
                ),
            secondaryMessage = Message(message = secondaryMessage, animate = false),
        )
    } else {
        return BouncerMessageModel(
            message =
                Message(
                    messageResId =
                        BouncerMessageStrings.defaultMessage(
                                securityMode.toAuthModel(),
                                fpAuthIsAllowed,
                                faceAuthIsAllowed,
                                false,
                            )
                            .toMessage()
                            .message
                            ?.messageResId,
                    animate = false,
                ),
            secondaryMessage = Message(message = secondaryMessage, animate = false),
        )
    }
}

private fun Pair<Int, Int>.toMessage(): BouncerMessageModel {
    return BouncerMessageModel(
        message = Message(messageResId = this.first, animate = false),
        secondaryMessage = Message(messageResId = this.second, animate = false),
    )
}

private fun SecurityMode.toAuthModel(): AuthenticationMethodModel {
    return when (this) {
        SecurityMode.Invalid -> AuthenticationMethodModel.None
        SecurityMode.None -> AuthenticationMethodModel.None
        SecurityMode.Pattern -> AuthenticationMethodModel.Pattern
        SecurityMode.Password -> AuthenticationMethodModel.Password
        SecurityMode.PIN -> AuthenticationMethodModel.Pin
        SecurityMode.SimPin -> AuthenticationMethodModel.Sim
        SecurityMode.SimPuk -> AuthenticationMethodModel.Sim
        SecurityMode.SecureLockDeviceBiometricAuth -> AuthenticationMethodModel.Biometric
    }
}
