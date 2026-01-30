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

package com.android.systemui.securelockdevice.domain.interactor

import android.security.authenticationpolicy.AuthenticationPolicyManager
import android.security.authenticationpolicy.DisableSecureLockDeviceParams
import com.android.internal.widget.LockPatternUtils
import com.android.internal.widget.LockPatternUtils.StrongAuthTracker.PRIMARY_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE
import com.android.systemui.biometrics.domain.interactor.FacePropertyInteractor
import com.android.systemui.biometrics.domain.interactor.FingerprintPropertyInteractor
import com.android.systemui.biometrics.shared.model.BiometricModalities
import com.android.systemui.biometrics.shared.model.SensorStrength
import com.android.systemui.biometrics.ui.viewmodel.PromptAuthState
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryBiometricSettingsInteractor
import com.android.systemui.deviceentry.domain.interactor.SystemUIDeviceEntryFaceAuthInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.LogLevel
import com.android.systemui.log.dagger.SecureLockDeviceLog
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.securelockdevice.data.repository.SecureLockDeviceRepository
import com.android.systemui.user.domain.interactor.SelectedUserInteractor
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Handles business logic for secure lock device.
 *
 * Secure lock device is a feature called by a privileged component to remotely enable secure lock
 * on the device across all users. Secure lock is an enhanced security state that restricts access
 * to sensitive data (app notifications, widgets, quick settings, assistant, etc), and locks the
 * device under the calling user's credentials with multi-factor authentication for device entry,
 * such as two-factor primary authentication and strong biometric authentication.
 */
@SysUISingleton
class SecureLockDeviceInteractor
@Inject
constructor(
    @Application applicationScope: CoroutineScope,
    @SecureLockDeviceLog private val logBuffer: LogBuffer,
    private val secureLockDeviceRepository: SecureLockDeviceRepository,
    biometricSettingsInteractor: DeviceEntryBiometricSettingsInteractor,
    private val deviceEntryFaceAuthInteractor: SystemUIDeviceEntryFaceAuthInteractor,
    fingerprintPropertyInteractor: Lazy<FingerprintPropertyInteractor>,
    facePropertyInteractor: FacePropertyInteractor,
    private val lockPatternUtils: LockPatternUtils,
    private val authenticationPolicyManager: AuthenticationPolicyManager?,
    private val selectedUserInteractor: SelectedUserInteractor,
    private val keyguardTransitionInteractor: Lazy<KeyguardTransitionInteractor>,
) {
    /** @see SecureLockDeviceRepository.isSecureLockDeviceEnabled */
    val isSecureLockDeviceEnabled: StateFlow<Boolean> =
        secureLockDeviceRepository.isSecureLockDeviceEnabled.stateIn(
            applicationScope,
            SharingStarted.Eagerly,
            false,
        )

    /** @see SecureLockDeviceRepository.requiresPrimaryAuthForSecureLockDevice */
    val requiresPrimaryAuthForSecureLockDevice: StateFlow<Boolean> =
        secureLockDeviceRepository.requiresPrimaryAuthForSecureLockDevice.stateIn(
            applicationScope,
            SharingStarted.Eagerly,
            false,
        )

    /** @see SecureLockDeviceRepository.requiresStrongBiometricAuthForSecureLockDevice */
    val requiresStrongBiometricAuthForSecureLockDevice: StateFlow<Boolean> =
        secureLockDeviceRepository.requiresStrongBiometricAuthForSecureLockDevice.stateIn(
            applicationScope,
            SharingStarted.Eagerly,
            false,
        )

    /** @see SecureLockDeviceRepository.suppressBouncerMessageUpdates */
    val suppressBouncerMessageUpdates: StateFlow<Boolean> =
        secureLockDeviceRepository.suppressBouncerMessageUpdates.stateIn(
            applicationScope,
            SharingStarted.Eagerly,
            false,
        )

    private val _isBiometricAuthVisible = MutableStateFlow<Boolean>(false)
    /** Whether the secure lock device biometric auth UI should be shown. */
    val isBiometricAuthVisible: StateFlow<Boolean> = _isBiometricAuthVisible.asStateFlow()

    /**
     * The timestamp of the last strong face authentication success, or null otherwise. This is used
     * to ensure that a stale face authentication success will not re-authenticate the user if
     * secure lock device biometric auth is interrupted (e.g. power press, back gesture, etc) after
     * authenticating a user's face but before the user confirms the authentication on the UI.
     */
    var lastProcessedFaceAuthSuccessTime: Long? = null

    /**
     * If the user has successfully authenticated a strong biometric in the secure lock device UI
     * (and explicitly confirmed if required).
     */
    private val _strongBiometricAuthenticationComplete = MutableStateFlow(false)

    private val _isFullyUnlockedAndReadyToDismiss = MutableStateFlow<Boolean>(false)
    /**
     * Whether the user completed successful two-factor authentication (primary + strong biometric)
     * in secure lock device, and the device should be considered unlocked. This is true when the
     * strong biometric does not require confirmation (e.g. fingerprint) or when the strong
     * biometric does require confirmation (e.g. face) but the user has completed confirmation on
     * the UI, and the confirmation animation has played, so the UI is ready to be dismissed.
     */
    val isFullyUnlockedAndReadyToDismiss: StateFlow<Boolean> =
        _isFullyUnlockedAndReadyToDismiss.asStateFlow()

    init {
        if (!SceneContainerFlag.isEnabled) {
            // TODO (b/427071498): remove when SceneContainerFlag is removed
            applicationScope.launch {
                isFullyUnlockedAndReadyToDismiss
                    .filter { it }
                    .flatMapLatest {
                        keyguardTransitionInteractor.get().isFinishedIn(KeyguardState.GONE)
                    }
                    .filter { it }
                    .collect { onGoneTransitionFinished() }
            }
        }
    }

    /**
     * Whether the user has completed two-factor authentication (primary authentication and active
     * strong biometric authentication or confirmed passive strong biometric authentication)
     */
    val isAuthenticatedButPendingDismissal: StateFlow<Boolean> =
        combine(_strongBiometricAuthenticationComplete, isFullyUnlockedAndReadyToDismiss) {
                strongBiometricAuthenticationComplete,
                isFullyUnlockedAndReadyToDismiss ->
                strongBiometricAuthenticationComplete || isFullyUnlockedAndReadyToDismiss
            }
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = false,
            )

    /**
     * When !SceneContainerFlags.isEnabled, runnable for when the disappear animation has finished.
     */
    private var disappearAnimationFinishedRunnable: Runnable? = null

    /** Called upon updates to strong biometric authenticated status. */
    fun onBiometricAuthenticatedStateUpdated(authState: PromptAuthState) {
        logBuffer.log(
            TAG,
            LogLevel.DEBUG,
            { str1 = authState.toString() },
            { "onBiometricAuthenticatedStateUpdated: authState=$str1" },
        )
        val pendingConfirmation = authState.needsUserConfirmation
        _showConfirmBiometricAuthButton.value = pendingConfirmation
        deviceEntryFaceAuthInteractor.onSecureLockDeviceConfirmButtonShowingChanged(
            pendingConfirmation
        )
        _strongBiometricAuthenticationComplete.value = authState.isAuthenticatedAndConfirmed
    }

    /**
     * Called after the user completes successful two-factor authentication (primary + strong
     * biometric) in secure lock device and the authenticated animation has finished playing,
     * indicating the biometric auth UI is ready for dismissal
     */
    fun onReadyToDismissBiometricAuth() {
        logBuffer.log(TAG, LogLevel.DEBUG, "onReadyToDismissBiometricAuth")
        _isFullyUnlockedAndReadyToDismiss.value = true
    }

    /** @see SecureLockDeviceRepository.suppressBouncerMessageUpdates */
    fun suppressBouncerMessages() {
        logBuffer.log(TAG, LogLevel.DEBUG, "suppressBouncerMessages")
        secureLockDeviceRepository.suppressBouncerMessageUpdates.value = true
    }

    private val _showConfirmBiometricAuthButton = MutableStateFlow<Boolean>(false)

    /**
     * Indicates a confirm button should be displayed on the UI for the user to confirm a successful
     * strong biometric authentication during secure lock device.
     */
    val showConfirmBiometricAuthButton: StateFlow<Boolean> =
        _showConfirmBiometricAuthButton.asStateFlow()

    private val _showTryAgainButton = MutableStateFlow<Boolean>(false)
    /**
     * Indicates a try again button should be displayed on the UI for the user to retry
     * authentication during secure lock device.
     */
    val showTryAgainButton: StateFlow<Boolean> = _showTryAgainButton.asStateFlow()

    private val _showingError = MutableStateFlow<Boolean>(false)
    /**
     * Indicates an error is being displayed on the UI during secure lock device biometric
     * authentication.
     */
    val showingError: StateFlow<Boolean> = _showingError.asStateFlow()

    /**
     * Whether the device should listen for biometric auth while secure lock device is enabled. The
     * device should stop listening when pending authentication, when authenticated, or when the
     * biometric auth screen is exited without authenticating.
     */
    val shouldListenForBiometricAuth: Flow<Boolean> =
        combine(
            requiresStrongBiometricAuthForSecureLockDevice,
            showConfirmBiometricAuthButton,
            showTryAgainButton,
        ) { requiresBiometricAuth, confirmButtonShowing, showTryAgainButton ->
            requiresBiometricAuth && !confirmButtonShowing && !showTryAgainButton
        }

    /**
     * Called after a biometric authentication error during secure lock device when a try again
     * button should be displayed on the UI to allow the user to restart auth.
     */
    fun onRetryAvailableChanged(isAvailable: Boolean) {
        logBuffer.log(
            TAG,
            LogLevel.DEBUG,
            { bool1 = isAvailable },
            { "onRetryAvailableChanged=$bool1" },
        )
        _showTryAgainButton.value = isAvailable
        deviceEntryFaceAuthInteractor.onSecureLockDeviceTryAgainButtonShowingChanged(isAvailable)
    }

    fun onShowingError(showingError: Boolean) {
        logBuffer.log(TAG, LogLevel.DEBUG, { bool1 = showingError }, { "onShowingError=$bool1" })
        _showingError.value = showingError
    }

    /** Strong biometric modalities enrolled and enabled on the device. */
    val enrolledStrongBiometricModalities: Flow<BiometricModalities> by lazy {
        combine(
                biometricSettingsInteractor.isFingerprintAuthEnrolledAndEnabled,
                biometricSettingsInteractor.isFaceAuthEnrolledAndEnabled,
                fingerprintPropertyInteractor.get().sensorInfo,
                facePropertyInteractor.sensorInfo,
            ) { fingerprintEnrolledAndEnabled, faceEnrolledAndEnabled, fpSensorInfo, faceSensorInfo
                ->
                val hasStrongFingerprint =
                    fingerprintEnrolledAndEnabled && fpSensorInfo.strength == SensorStrength.STRONG
                val hasStrongFace =
                    faceEnrolledAndEnabled && faceSensorInfo?.strength == SensorStrength.STRONG

                if (hasStrongFingerprint && hasStrongFace) {
                    BiometricModalities(fpSensorInfo, faceSensorInfo)
                } else if (hasStrongFingerprint) {
                    BiometricModalities(fpSensorInfo, null)
                } else if (hasStrongFace) {
                    BiometricModalities(null, faceSensorInfo)
                } else {
                    BiometricModalities()
                }
            }
            .distinctUntilChanged()
    }

    /** Whether the device has a strong fingerprint enrollment on the device. */
    val hasFingerprint: StateFlow<Boolean> by lazy {
        enrolledStrongBiometricModalities
            .map { it.hasFingerprint }
            .stateIn(applicationScope, SharingStarted.Lazily, false)
    }

    /** Whether the device has a strong face enrollment on the device. */
    val hasFace: StateFlow<Boolean> by lazy {
        enrolledStrongBiometricModalities
            .map { it.hasFace }
            .stateIn(applicationScope, SharingStarted.Lazily, false)
    }

    /** Called when biometric authentication is requested for secure lock device. */
    fun onBiometricAuthRequested() {
        logBuffer.log(TAG, LogLevel.DEBUG, "onBiometricAuthRequested")
        resetBiometricAuthState(isBiometricAuthRequested = true)
        _isFullyUnlockedAndReadyToDismiss.value = false
        _isBiometricAuthVisible.value = true
        deviceEntryFaceAuthInteractor.onSecureLockDeviceBiometricAuthRequested()
    }

    /** Called when the user clicks the try again button to resume authentication. */
    fun onRetryBiometricAuth() {
        logBuffer.log(TAG, LogLevel.DEBUG, "onRetryBiometricAuth")
        secureLockDeviceRepository.suppressBouncerMessageUpdates.value = false
        _showTryAgainButton.value = false
        _showingError.value = false
        deviceEntryFaceAuthInteractor.onSecureLockDeviceBiometricAuthRequested()
    }

    /**
     * Resets UI state when leaving the biometric auth screen without authenticating, or when the
     * secure lock device UI state is reset upon the gone transition completing.
     */
    private fun resetBiometricAuthState(isBiometricAuthRequested: Boolean) {
        logBuffer.log(
            TAG,
            LogLevel.DEBUG,
            "resetBiometricAuthState(isBiometricAuthRequested $isBiometricAuthRequested)",
        )
        secureLockDeviceRepository.suppressBouncerMessageUpdates.value = false
        if (!isBiometricAuthRequested) {
            _isBiometricAuthVisible.value = false
        }
        _showConfirmBiometricAuthButton.value = false
        _showTryAgainButton.value = false
        _showingError.value = false
    }

    /** Called when the biometric auth view or overlay is hidden. */
    fun onBiometricAuthUiHidden() {
        logBuffer.log(
            TAG,
            LogLevel.DEBUG,
            { bool1 = _isFullyUnlockedAndReadyToDismiss.value },
            { "onBiometricAuthUiHidden: isFullyUnlockedAndReadyToDismiss=$bool1" },
        )
        deviceEntryFaceAuthInteractor.onSecureLockDeviceBiometricAuthHidden()

        // Determine if view is hidden for successful biometric auth / confirmation, or if the user
        // is unauthenticated (back gesture, lockout, screen off) and we should resecure the device.
        if (!_isFullyUnlockedAndReadyToDismiss.value) {
            /**
             * Biometric authentication for secure lock device has been interrupted (e.g. device
             * went to sleep, back gesture, biometric lockout, etc.)
             */
            logBuffer.log(
                TAG,
                LogLevel.DEBUG,
                "User exited secure lock device biometric auth screen without " +
                    "authenticating, set secure lock device strong auth flag.",
            )
            lockPatternUtils.requireStrongAuth(
                PRIMARY_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE,
                selectedUserInteractor.getSelectedUserId(),
            )
            resetBiometricAuthState(isBiometricAuthRequested = false)
        }
    }

    /**
     * Called when the secure lock device view has fully transitioned to gone, in order to disable
     * secure lock device and reset the biometric auth state.
     */
    fun onGoneTransitionFinished() {
        logBuffer.log(TAG, LogLevel.DEBUG, "onGoneTransitionFinished")
        authenticationPolicyManager?.disableSecureLockDevice(
            DisableSecureLockDeviceParams(
                "Disabling secure lock device on completed two-factor primary and strong " +
                    "biometric authentication"
            )
        )
        resetBiometricAuthState(isBiometricAuthRequested = false)
        _isFullyUnlockedAndReadyToDismiss.value = false
        _strongBiometricAuthenticationComplete.value = false
    }

    // TODO (b/427071498): remove when SceneContainerFlag is removed
    /**
     * Called from legacy keyguard controller to set runnable with actions to complete when the
     * disappear animation has finished.
     */
    fun setDisappearAnimationFinishedRunnable(finishRunnable: Runnable?) {
        SceneContainerFlag.assertInLegacyMode()
        disappearAnimationFinishedRunnable = finishRunnable
    }

    // TODO (b/427071498): remove when SceneContainerFlag is removed
    /**
     * Runs actions to complete when the disappear animation has finished in the legacy keyguard
     * implementation.
     */
    fun onDisappearAnimationFinished() {
        SceneContainerFlag.assertInLegacyMode()
        disappearAnimationFinishedRunnable?.run()
    }

    companion object {
        private const val TAG = "SecureLockDeviceInteractor"
    }
}
