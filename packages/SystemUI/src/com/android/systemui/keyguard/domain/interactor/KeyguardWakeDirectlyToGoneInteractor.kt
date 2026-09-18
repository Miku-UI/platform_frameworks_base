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

package com.android.systemui.keyguard.domain.interactor

import android.util.Log
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.deviceentry.domain.interactor.DeviceUnlockedInteractor
import com.android.systemui.deviceentry.shared.model.DeviceUnlockStatus
import com.android.systemui.keyguard.data.repository.KeyguardRepository
import com.android.systemui.keyguard.shared.model.BiometricUnlockMode
import com.android.systemui.keyguard.shared.model.BiometricUnlockModel
import com.android.systemui.keyguard.shared.model.KeyguardState.Companion.deviceIsAsleepInState
import com.android.systemui.keyguard.shared.model.KeyguardState.Companion.deviceIsAwakeInState
import com.android.systemui.keyguard.shared.model.LockAfterDelayTimerState
import com.android.systemui.power.domain.interactor.PowerInteractor
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.statusbar.policy.domain.interactor.DeviceProvisioningInteractor
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn

/**
 * Logic related to the ability to wake directly to GONE from asleep (AOD/DOZING), without going
 * through LOCKSCREEN or a BOUNCER state.
 *
 * This is possible in the following scenarios:
 * - The keyguard is not enabled, either from an app request (SUW does this), or by the security
 *   "None" setting.
 * - The keyguard was suppressed via adb.
 * - A biometric authentication event occurred while we were asleep (fingerprint auth, etc). This
 *   specifically is referred to throughout the codebase as "wake and unlock".
 * - The screen timed out, but the "lock after screen timeout" duration has not elapsed.
 * - The power button was pressed, but "power button instantly locks" is disabled and the "lock
 *   after screen timeout" duration has not elapsed.
 *
 * In these cases, no (further) authentication is required, and we can transition directly from
 * AOD/DOZING -> GONE.
 */
@SysUISingleton
class KeyguardWakeDirectlyToGoneInteractor
@Inject
constructor(
    @Application private val scope: CoroutineScope,
    private val repository: KeyguardRepository,
    private val transitionInteractor: KeyguardTransitionInteractor,
    powerInteractor: PowerInteractor,
    keyguardEnabledInteractor: KeyguardEnabledInteractor,
    keyguardServiceShowLockscreenInteractor: KeyguardServiceShowLockscreenInteractor,
    private val sceneInteractor: Lazy<SceneInteractor>,
    private val deviceUnlockedInteractor: Lazy<DeviceUnlockedInteractor>,
    private val deviceProvisioningInteractor: DeviceProvisioningInteractor,
) {

    /**
     * Whether the keyguard was suppressed as of the most recent wakefulness event or lockNow
     * command. Keyguard suppression can only be queried (there is no callback available), and
     * legacy code only queried the value in onStartedGoingToSleep and doKeyguardTimeout. Tests now
     * depend on that behavior, so for now, we'll replicate it here.
     */
    val shouldSuppressKeyguard: StateFlow<Boolean> =
        merge(
                powerInteractor.isAwake,
                // Update only when doKeyguardTimeout is called, not on fold or other events, to
                // match pre-existing logic.
                keyguardServiceShowLockscreenInteractor.showNowEvents.filter {
                    it == ShowWhileAwakeReason.KEYGUARD_TIMEOUT_WHILE_SCREEN_ON
                },
            )
            .map { keyguardEnabledInteractor.isKeyguardSuppressed() }
            .stateIn(scope, SharingStarted.Eagerly, false)

    /**
     * Whether we can wake from AOD/DOZING or DREAMING directly to GONE, bypassing
     * LOCKSCREEN/BOUNCER states.
     *
     * This is possible in the following cases:
     * - Keyguard is disabled, either from an app request or from security being set to "None".
     * - Keyguard is suppressed, via adb locksettings.
     * - We're wake and unlocking (fingerprint auth occurred while asleep).
     * - We're allowed to ignore auth and return to GONE, due to timeouts not elapsing.
     * - We're DREAMING and dismissible.
     * - The device is not yet provisioned (still doing setup wizard stuff)
     */
    val canWakeDirectlyToGone by lazy {
        combine(
                repository.isKeyguardEnabled,
                shouldSuppressKeyguard,
                repository.biometricUnlockState,
                repository.canIgnoreAuthAndReturnToGone,
                deviceUnlockedInteractor.get().deviceUnlockStatus,
                sceneInteractor.get().currentScene,
                deviceProvisioningInteractor.isDeviceProvisioned,
            ) { values ->
                val keyguardEnabled = values[0] as Boolean
                val shouldSuppressKeyguard = values[1] as Boolean
                val biometricUnlockState = values[2] as BiometricUnlockModel
                val canIgnoreAuthAndReturnToGone = values[3] as Boolean
                val deviceUnlockStatus = values[4] as DeviceUnlockStatus
                val currentScene = values[5] as SceneKey
                val isDeviceProvisioned = values[6] as Boolean

                val isWakeAndDismiss =
                    BiometricUnlockMode.isWakeAndDismiss(biometricUnlockState.mode)

                val isDreamingAndDismissible =
                    currentScene == Scenes.Dream && deviceUnlockStatus.isUnlocked

                // make sure device is always unlocked. Even when keyguard is not enabled, SIM can
                // be locked and force keyguard/bouncer to be shown.
                deviceUnlockStatus.isUnlocked &&
                    (!isDeviceProvisioned ||
                        !keyguardEnabled ||
                        shouldSuppressKeyguard ||
                        isWakeAndDismiss ||
                        canIgnoreAuthAndReturnToGone ||
                        isDreamingAndDismissible)
            }
            .stateIn(scope, SharingStarted.Eagerly, false)
    }

    var isAwake = false

    init {
        listenForLockAfterScreenTimeoutState()
        listenForWakeToClearCanIgnoreAuth()
    }

    private fun listenForLockAfterScreenTimeoutState() {
        scope.launch {
            repository.lockAfterDelayState.collect { state ->
                if (state == LockAfterDelayTimerState.RUNNING) {
                    // Let the repository know that we can return to GONE until we notify
                    // it otherwise.
                    Log.d(
                        TAG,
                        "can ignore auth and return to gone - lock timeout timer not elapsed",
                    )
                    repository.setCanIgnoreAuthAndReturnToGone(true)
                } else if (state == LockAfterDelayTimerState.ELAPSED) {
                    Log.d(
                        TAG,
                        "can not ignore auth and return to gone - lock timeout timer elapsed",
                    )
                    repository.setCanIgnoreAuthAndReturnToGone(false)
                }
            }
        }
    }

    /** Clears the canIgnoreAuthAndReturnToGone value upon waking. */
    private fun listenForWakeToClearCanIgnoreAuth() {
        scope.launch {
            transitionInteractor
                .isInTransitionWhere(
                    fromStatePredicate = {
                        deviceIsAsleepInState(
                            it,
                            if (SceneContainerFlag.isEnabled) currentScene() else null,
                        )
                    },
                    toStatePredicate = {
                        deviceIsAwakeInState(
                            it,
                            if (SceneContainerFlag.isEnabled) currentScene() else null,
                        )
                    },
                )
                .collect {
                    // This value is reset when the timeout alarm fires, but if the device is woken
                    // back up before then, it needs to be reset here. The alarm is cancelled
                    // immediately upon waking up, but since this value is used by keyguard
                    // transition internals to decide whether we can transition to GONE, wait until
                    // that decision is made before resetting it.
                    Log.d(TAG, "can not ignore auth and return to gone - because of waking")
                    repository.setCanIgnoreAuthAndReturnToGone(false)
                }
        }
    }

    private fun currentScene() = sceneInteractor.get().currentScene.value

    companion object {
        private val TAG = "KeyguardWakeDirectlyToGoneInteractor"
    }
}
