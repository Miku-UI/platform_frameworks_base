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

package com.android.systemui.deviceentry.domain.interactor

import android.security.Flags.secureLockDevice
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.android.systemui.CoreStartable
import com.android.systemui.Flags
import com.android.systemui.Flags.strongAuthRequiredAfterSignOutMessageFix
import com.android.systemui.authentication.domain.interactor.AuthenticationInteractor
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.deviceentry.data.repository.DeviceEntryRepository
import com.android.systemui.deviceentry.shared.model.DeviceEntryRestrictionReason
import com.android.systemui.deviceentry.shared.model.DeviceUnlockSource
import com.android.systemui.deviceentry.shared.model.DeviceUnlockStatus
import com.android.systemui.flags.SystemPropertiesHelper
import com.android.systemui.keyguard.domain.interactor.BiometricUnlockInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardEnabledInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.domain.interactor.LockAfterDelayInteractor
import com.android.systemui.keyguard.domain.interactor.TrustInteractor
import com.android.systemui.keyguard.shared.model.AuthenticationFlags
import com.android.systemui.keyguard.shared.model.LockAfterDelayTimerState
import com.android.systemui.keyguard.shared.model.toDeviceUnlockSource
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.log.table.TableLogBuffer
import com.android.systemui.log.table.logDiffsForTable
import com.android.systemui.power.domain.interactor.PowerInteractor
import com.android.systemui.power.shared.model.WakeSleepReason
import com.android.systemui.scene.domain.SceneFrameworkTableLog
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.securelockdevice.domain.interactor.SecureLockDeviceInteractor
import com.android.systemui.shared.settings.data.repository.SecureSettingsRepository
import com.android.systemui.user.data.repository.UserSwitcherRepository
import com.android.systemui.utils.coroutines.flow.flatMapLatestConflated
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@SysUISingleton
class DeviceUnlockedInteractor
@Inject
constructor(
    private val authenticationInteractor: AuthenticationInteractor,
    private val repository: DeviceEntryRepository,
    private val trustInteractor: TrustInteractor,
    private val faceAuthInteractor: DeviceEntryFaceAuthInteractor,
    private val fingerprintAuthInteractor: DeviceEntryFingerprintAuthInteractor,
    private val powerInteractor: PowerInteractor,
    private val biometricSettingsInteractor: DeviceEntryBiometricSettingsInteractor,
    secureLockDeviceInteractor: Lazy<SecureLockDeviceInteractor>,
    private val systemPropertiesHelper: SystemPropertiesHelper,
    private val secureSettingsRepository: SecureSettingsRepository,
    private val keyguardInteractor: KeyguardInteractor,
    @SceneFrameworkTableLog private val tableLogBuffer: TableLogBuffer,
    biometricUnlockInteractor: BiometricUnlockInteractor,
    private val keyguardEnabledInteractor: KeyguardEnabledInteractor,
    private val lockAfterDelayInteractor: LockAfterDelayInteractor,
    private val userSwitcherRepository: UserSwitcherRepository,
) : ExclusiveActivatable() {
    private val faceEnrolledAndEnabled = biometricSettingsInteractor.isFaceAuthEnrolledAndEnabled
    private val fingerprintEnrolledAndEnabled =
        biometricSettingsInteractor.isFingerprintAuthEnrolledAndEnabled
    private val trustAgentEnabled = trustInteractor.isEnrolledAndEnabled

    private val faceOrFingerprintOrTrustEnabled: Flow<Triple<Boolean, Boolean, Boolean>> =
        combine(faceEnrolledAndEnabled, fingerprintEnrolledAndEnabled, trustAgentEnabled, ::Triple)

    private val currentFaceOrFingerprintOrTrustEnabled: Boolean
        get() =
            faceEnrolledAndEnabled.value ||
                fingerprintEnrolledAndEnabled.value ||
                trustAgentEnabled.value

    /**
     * Reason why device entry is restricted to certain authentication methods for the current user.
     *
     * Emits null when there are no device entry restrictions active.
     */
    val deviceEntryRestrictionReason: Flow<DeviceEntryRestrictionReason?> =
        faceOrFingerprintOrTrustEnabled.flatMapLatest {
            (faceEnabled, fingerprintEnabled, trustEnabled) ->
            if (faceEnabled || fingerprintEnabled || trustEnabled) {
                combine(
                    biometricSettingsInteractor.authenticationFlags,
                    faceAuthInteractor.isLockedOut,
                    fingerprintAuthInteractor.isLockedOut,
                    trustInteractor.isTrustAgentCurrentlyAllowed,
                ) { authFlags, isFaceLockedOut, isFingerprintLockedOut, trustManaged ->
                    authFlags.getDeviceEntryRestrictionReason(
                        isFaceLockedOut = isFaceLockedOut,
                        isFingerprintLockedOut = isFingerprintLockedOut,
                        trustEnabled = trustEnabled,
                        trustManaged = trustManaged,
                    )
                }
            } else {
                biometricSettingsInteractor.authenticationFlags.map { authFlags ->
                    when {
                        authFlags.isInUserLockdown -> DeviceEntryRestrictionReason.UserLockdown
                        authFlags.isPrimaryAuthRequiredAfterDpmLockdown ->
                            DeviceEntryRestrictionReason.PolicyLockdown
                        else -> null
                    }
                }
            }
        }

    fun currentDeviceEntryRestrictionReason(): DeviceEntryRestrictionReason? {
        val authFlags = biometricSettingsInteractor.authenticationFlags.value
        return if (currentFaceOrFingerprintOrTrustEnabled) {
            authFlags.getDeviceEntryRestrictionReason(
                isFaceLockedOut = faceAuthInteractor.isLockedOut.value,
                isFingerprintLockedOut = fingerprintAuthInteractor.isLockedOut.value,
                trustEnabled = trustAgentEnabled.value,
                trustManaged = trustInteractor.isTrustAgentCurrentlyAllowed.value,
            )
        } else {
            when {
                authFlags.isInUserLockdown -> DeviceEntryRestrictionReason.UserLockdown
                authFlags.isPrimaryAuthRequiredAfterDpmLockdown ->
                    DeviceEntryRestrictionReason.PolicyLockdown
                else -> null
            }
        }
    }

    /** Whether the device is in lockdown mode, where bouncer input is required to unlock. */
    val isInLockdown: Flow<Boolean> = deviceEntryRestrictionReason.map { it.isInLockdown() }

    /**
     * Whether secure lock device mode is enabled, meaning device entry requires two-factor
     * authentication: primary auth on the bouncer, followed by strong biometric-only auth on the
     * bouncer.
     *
     * @see SecureLockDeviceInteractor.isSecureLockDeviceEnabled
     *
     * Returns false when FLAG_SECURE_LOCK_DEVICE is disabled
     */
    val isSecureLockDeviceEnabled: Flow<Boolean> =
        if (secureLockDevice()) {
            secureLockDeviceInteractor.get().isSecureLockDeviceEnabled
        } else {
            flowOf(false)
        }

    /**
     * Whether the device is fully unlocked and ready to dismiss in secure lock device.
     *
     * @see SecureLockDeviceInteractor.isFullyUnlockedAndReadyToDismiss
     *
     * Returns false when FLAG_SECURE_LOCK_DEVICE is disabled
     */
    private val isFullyUnlockedAndReadyToDismissInSecureLockDevice: Flow<Boolean> =
        if (secureLockDevice()) {
            secureLockDeviceInteractor.get().isFullyUnlockedAndReadyToDismiss
        } else {
            flowOf(false)
        }

    /** Indicates when a device has been unlocked from successful authentication on the bouncer. */
    private val onUnlockFromBouncer = authenticationInteractor.onAuthenticationResult.filter { it }

    private val deviceUnlockSource =
        /**
         * When secure lock device is active, the device is not considered unlocked after successful
         * bouncer auth. Secure Lock Device requires two-factor authentication: primary auth on the
         * bouncer, followed by strong biometric authentication on the bouncer, in order to unlock
         * and enter the device.
         */
        isSecureLockDeviceEnabled.flatMapLatest { isSecureLockDeviceEnabled ->
            if (isSecureLockDeviceEnabled) {
                isFullyUnlockedAndReadyToDismissInSecureLockDevice
                    .filter { it }
                    .map { DeviceUnlockSource.SecureLockDeviceTwoFactorAuth }
            } else {
                merge(
                    biometricUnlockInteractor.unlockState
                        .map { biometricUnlockModel ->
                            return@map biometricUnlockModel.toDeviceUnlockSource()
                        }
                        .filterNotNull(),
                    trustInteractor.isTrusted.filter { it }.map { DeviceUnlockSource.TrustAgent },
                    onUnlockFromBouncer.map { DeviceUnlockSource.BouncerInput },
                    unlockForPowerButtonGestureRequests.receiveAsFlow().map {
                        DeviceUnlockSource.UnlockedPowerButtonGesture
                    },
                )
            }
        }

    /**
     * Whether the device is unlocked or not, along with the information about the authentication
     * method that was used to unlock the device.
     *
     * A device that is not yet unlocked requires unlocking by completing an authentication
     * challenge according to the current authentication method, unless in cases when the current
     * authentication method is not "secure" (for example, None and Swipe); in such cases, the value
     * of this flow will always be an instance of [DeviceUnlockStatus] with
     * [DeviceUnlockStatus.deviceUnlockSource] as null and [DeviceUnlockStatus.isUnlocked] set to
     * true, even if the lockscreen is showing and still needs to be dismissed by the user to
     * proceed.
     */
    val deviceUnlockStatus: StateFlow<DeviceUnlockStatus> =
        repository.deviceUnlockStatus.asStateFlow()

    /** Helper property to check if the device is unlocked. */
    val isUnlocked: Boolean
        get() = deviceUnlockStatus.value.isUnlocked

    /** A [Channel] of "lock now" requests where the values are the debugging reasons. */
    private val lockNowRequests = Channel<String>(Channel.CONFLATED)

    /**
     * A [Channel] of "unlock now" requests where the values are the debugging reasons. Currently
     * only used by the power button launch gesture.
     */
    private val unlockForPowerButtonGestureRequests = Channel<String>()

    override suspend fun onActivated() {
        coroutineScope {
            launch {
                combine(
                        authenticationInteractor.authenticationMethod,
                        keyguardEnabledInteractor.isKeyguardEnabled,
                        ::Pair,
                    )
                    .collectLatest { (authMethod, keyguardEnabled) ->
                        if (authMethod == AuthenticationMethodModel.Sim) {
                            // Device remains locked while SIM is locked.
                            Log.d(TAG, "remaining locked because SIM locked")
                            repository.deviceUnlockStatus.value = DeviceUnlockStatus(false, null)
                        }
                        // Keyguard can be disabled externally (by an app, app pinning, etc.)
                        // even if a secure auth method is set.
                        else if (!authMethod.isSecure || !keyguardEnabled) {
                            // Device remains unlocked as long as the authentication method is not
                            // secure or keyguard is disabled.
                            Log.d(
                                TAG,
                                "remaining unlocked because auth method is " +
                                    "${if (authMethod.isSecure) "secure" else "not secure"} or " +
                                    "keyguard is ${if (keyguardEnabled) "enabled" else "disabled"}",
                            )
                            repository.deviceUnlockStatus.value = DeviceUnlockStatus(true, null)
                        } else {
                            handleLockAndUnlockEvents()
                        }
                    }
            }

            launch {
                deviceUnlockStatus
                    .map { it.isUnlocked }
                    .logDiffsForTable(
                        tableLogBuffer = tableLogBuffer,
                        columnName = "isUnlocked",
                        initialValue = deviceUnlockStatus.value.isUnlocked,
                    )
                    .collect()
            }
        }
    }

    /** Locks the device instantly. */
    fun lockNow(debuggingReason: String) {
        lockNowRequests.trySend(debuggingReason)
    }

    /**
     * Unlocks the device instantly.
     *
     * Dangerous, obviously. The only current reason to do this is for the unlocked power button
     * launch gesture, which has the unilateral authority to cancel a lock and go back to being
     * unlocked.
     */
    fun unlockNowForPowerButtonGesture(debuggingReason: String) {
        unlockForPowerButtonGestureRequests.trySend(debuggingReason)
    }

    private suspend fun handleLockAndUnlockEvents() {
        try {
            Log.d(TAG, "started watching for lock and unlock events")
            coroutineScope {
                launch { handleUnlockEvents() }
                launch { handleLockEvents() }
            }
        } finally {
            Log.d(TAG, "stopped watching for lock and unlock events")
        }
    }

    private suspend fun handleUnlockEvents() {
        // Unlock the device when a new unlock source is detected.
        deviceUnlockSource.collect {
            Log.i(TAG, "unlocking due to \"$it\"")
            repository.deviceUnlockStatus.value = DeviceUnlockStatus(true, it)
        }
    }

    private suspend fun handleLockEvents() {
        merge(
                trustInteractor.isTrusted.flatMapLatestConflated { isTrusted ->
                    if (isTrusted) {
                        // When entering a trusted environment, power-related lock events are
                        // ignored.
                        Log.d(TAG, "In trusted environment, ignoring power-related lock events")
                        emptyFlow()
                    } else {
                        // When not in a trusted environment, power-related lock events are treated
                        // as normal.
                        Log.d(
                            TAG,
                            "Not in trusted environment, power-related lock events treated as normal",
                        )
                        merge(
                            // Device wakefulness events.
                            if (Flags.wakefulnessEventsSharedFlow()) {
                                    powerInteractor.detailedWakefulnessEvents
                                } else {
                                    powerInteractor.detailedWakefulness
                                }
                                .map {
                                    Triple(
                                        it.isAsleep(),
                                        it.lastSleepReason,
                                        it.powerButtonLaunchGestureTriggered,
                                    )
                                }
                                .distinctUntilChangedBy { it.first }
                                .flatMapLatestConflated {
                                    (isAsleep, lastSleepReason, launchGestureTriggered) ->
                                    if (isAsleep) {
                                        if (lastSleepReason == WakeSleepReason.POWER_BUTTON) {
                                            if (
                                                !launchGestureTriggered &&
                                                    authenticationInteractor
                                                        .getPowerButtonInstantlyLocks()
                                            ) {
                                                flowOf(
                                                    LockImmediately(
                                                        "locked instantly from power button"
                                                    )
                                                )
                                            } else {
                                                flowOf(
                                                    ExpectLockWithDelay(
                                                        "lock with delay from power button"
                                                    )
                                                )
                                            }
                                        } else if (
                                            lastSleepReason == WakeSleepReason.SLEEP_BUTTON
                                        ) {
                                            flowOf(
                                                LockImmediately(
                                                    "locked instantly from sleep button"
                                                )
                                            )
                                        } else if (lastSleepReason == WakeSleepReason.FOLD) {
                                            flowOf(LockImmediately("locked instantly from fold"))
                                        } else if (lastSleepReason == WakeSleepReason.LID_CLOSE) {
                                            flowOf(
                                                LockImmediately("locked instantly from lid close")
                                            )
                                        } else if (lastSleepReason == WakeSleepReason.TIMEOUT) {
                                            flowOf(ExpectLockWithDelay("screen timeout with delay"))
                                        } else {
                                            flowOf(ExpectLockWithDelay("unknown sleep reason"))
                                        }
                                    } else {
                                        emptyFlow<LockEvent>()
                                    }
                                },
                            lockAfterDelayInteractor.lockAfterDelayState.flatMapLatestConflated {
                                state ->
                                if (state == LockAfterDelayTimerState.ELAPSED) {
                                    flowOf(LockImmediately("lock screen timeout elapsed"))
                                } else {
                                    emptyFlow<LockEvent>()
                                }
                            },
                        )
                    }
                },
                // Device enters lockdown.
                isInLockdown
                    .distinctUntilChanged()
                    .filter { it }
                    .map { LockImmediately("lockdown") },
                lockNowRequests.receiveAsFlow().map { reason -> LockImmediately(reason) },
            )
            .collectLatest(::onLockEvent)
    }

    private suspend fun onLockEvent(event: LockEvent) {
        val debugReason = event.debugReason
        when (event) {
            is LockImmediately -> {
                Log.i(TAG, "locking without delay due to \"$debugReason\"")
                repository.deviceUnlockStatus.value = DeviceUnlockStatus(false, null)
            }

            is ExpectLockWithDelay -> {
                if (lockAfterDelayInteractor.delayLockIsImmediate()) {
                    Log.i(TAG, "lock timeout is 0, so locking immediately due to \"$debugReason\"")
                    repository.deviceUnlockStatus.value = DeviceUnlockStatus(false, null)
                } else {
                    Log.i(TAG, "waiting for lock timeout due to \"$debugReason\"")
                }
            }
        }
    }

    private fun DeviceEntryRestrictionReason?.isInLockdown(): Boolean {
        return when (this) {
            DeviceEntryRestrictionReason.UserLockdown -> true
            DeviceEntryRestrictionReason.PolicyLockdown -> true
            // Device locking is handled via the lockNow request from SecureLockDeviceService
            DeviceEntryRestrictionReason.SecureLockDevicePrimaryAuth -> false
            DeviceEntryRestrictionReason.SecureLockDeviceStrongBiometricOnlyAuth -> false

            // Add individual enum value instead of using "else" so new reasons are guaranteed
            // to be added here at compile-time.
            null -> false
            DeviceEntryRestrictionReason.DeviceNotUnlockedSinceReboot -> false
            DeviceEntryRestrictionReason.BouncerLockedOut -> false
            DeviceEntryRestrictionReason.AdaptiveAuthRequest -> false
            DeviceEntryRestrictionReason.NonStrongBiometricsSecurityTimeout -> false
            DeviceEntryRestrictionReason.TrustAgentDisabled -> false
            DeviceEntryRestrictionReason.StrongBiometricsLockedOut -> false
            DeviceEntryRestrictionReason.SecurityTimeout -> false
            DeviceEntryRestrictionReason.DeviceNotUnlockedSinceMainlineUpdate -> false
            DeviceEntryRestrictionReason.UnattendedUpdate -> false
            DeviceEntryRestrictionReason.NonStrongFaceLockedOut -> false
            DeviceEntryRestrictionReason.UserNotUnlockedSinceSignOut -> false
        }
    }

    private fun wasRebootedForMainlineUpdate(): Boolean {
        return systemPropertiesHelper.get(SYS_BOOT_REASON_PROP) == REBOOT_MAINLINE_UPDATE
    }

    private fun AuthenticationFlags.getDeviceEntryRestrictionReason(
        isFaceLockedOut: Boolean,
        isFingerprintLockedOut: Boolean,
        trustEnabled: Boolean,
        trustManaged: Boolean,
    ): DeviceEntryRestrictionReason? {
        return when {
            isPrimaryAuthRequiredForSecureLockDevice ->
                DeviceEntryRestrictionReason.SecureLockDevicePrimaryAuth
            isStrongBiometricAuthRequiredForSecureLockDevice ->
                DeviceEntryRestrictionReason.SecureLockDeviceStrongBiometricOnlyAuth
            strongAuthRequiredAfterSignOutMessageFix() &&
                isPrimaryAuthRequiredAfterReboot &&
                userSwitcherRepository.isUserSwitchingMustGoThroughLoginScreen ->
                DeviceEntryRestrictionReason.UserNotUnlockedSinceSignOut
            isPrimaryAuthRequiredAfterReboot && wasRebootedForMainlineUpdate() ->
                DeviceEntryRestrictionReason.DeviceNotUnlockedSinceMainlineUpdate
            isPrimaryAuthRequiredAfterReboot ->
                DeviceEntryRestrictionReason.DeviceNotUnlockedSinceReboot
            isPrimaryAuthRequiredAfterDpmLockdown -> DeviceEntryRestrictionReason.PolicyLockdown
            isInUserLockdown -> DeviceEntryRestrictionReason.UserLockdown
            isPrimaryAuthRequiredForUnattendedUpdate ->
                DeviceEntryRestrictionReason.UnattendedUpdate
            isPrimaryAuthRequiredAfterTimeout -> DeviceEntryRestrictionReason.SecurityTimeout
            isFingerprintLockedOut -> DeviceEntryRestrictionReason.StrongBiometricsLockedOut
            isFaceLockedOut && faceAuthInteractor.isFaceAuthStrong() ->
                DeviceEntryRestrictionReason.StrongBiometricsLockedOut
            isFaceLockedOut -> DeviceEntryRestrictionReason.NonStrongFaceLockedOut
            isSomeAuthRequiredAfterAdaptiveAuthRequest ->
                DeviceEntryRestrictionReason.AdaptiveAuthRequest
            (trustEnabled && !trustManaged) &&
                (someAuthRequiredAfterTrustAgentExpired || someAuthRequiredAfterUserRequest) ->
                DeviceEntryRestrictionReason.TrustAgentDisabled
            strongerAuthRequiredAfterNonStrongBiometricsTimeout ->
                DeviceEntryRestrictionReason.NonStrongBiometricsSecurityTimeout
            else -> null
        }
    }

    /** [CoreStartable] that activates the [DeviceUnlockedInteractor]. */
    class Activator
    @Inject
    constructor(
        @Application private val applicationScope: CoroutineScope,
        private val interactor: DeviceUnlockedInteractor,
    ) : CoreStartable {
        override fun start() {
            if (!SceneContainerFlag.isEnabled) return

            applicationScope.launch { interactor.activate() }
        }
    }

    private sealed interface LockEvent {
        val debugReason: String
    }

    private data class LockImmediately(override val debugReason: String) : LockEvent

    private data class ExpectLockWithDelay(override val debugReason: String) : LockEvent

    companion object {
        private val TAG = "DeviceUnlockedInteractor"
        @VisibleForTesting const val SYS_BOOT_REASON_PROP = "sys.boot.reason.last"
        @VisibleForTesting const val REBOOT_MAINLINE_UPDATE = "reboot,mainline_update"
    }
}
