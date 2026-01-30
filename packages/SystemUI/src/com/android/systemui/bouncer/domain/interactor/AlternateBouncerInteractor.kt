/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.util.Log
import com.android.systemui.biometrics.data.repository.FingerprintPropertyRepository
import com.android.systemui.bouncer.data.repository.KeyguardBouncerRepository
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryBiometricsAllowedInteractor
import com.android.systemui.display.domain.interactor.DisplayStateInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.securelockdevice.domain.interactor.SecureLockDeviceInteractor
import com.android.systemui.util.kotlin.BooleanFlowOperators.anyOf
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/** Encapsulates business logic for interacting with the lock-screen alternate bouncer. */
@OptIn(ExperimentalCoroutinesApi::class)
@SysUISingleton
class AlternateBouncerInteractor
@Inject
constructor(
    private val bouncerRepository: KeyguardBouncerRepository,
    fingerprintPropertyRepository: FingerprintPropertyRepository,
    private val deviceEntryBiometricsAllowedInteractor:
        Lazy<DeviceEntryBiometricsAllowedInteractor>,
    private val keyguardInteractor: Lazy<KeyguardInteractor>,
    keyguardTransitionInteractor: Lazy<KeyguardTransitionInteractor>,
    displayStateInteractor: Lazy<DisplayStateInteractor>,
    sceneInteractor: Lazy<SceneInteractor>,
    secureLockDeviceInteractor: Lazy<SecureLockDeviceInteractor>,
    @Application scope: CoroutineScope,
) {
    private var receivedDownTouch = false

    val isVisible: StateFlow<Boolean> = bouncerRepository.alternateBouncerVisible

    val alternateBouncerSupported: StateFlow<Boolean> =
        fingerprintPropertyRepository.sensorType
            .map { sensorType -> sensorType.isUdfps() || sensorType.isPowerButton() }
            .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = false)

    private val isDozingOrAod: Flow<Boolean> =
        anyOf(
                keyguardTransitionInteractor.get().transitionValue(KeyguardState.DOZING).map {
                    it > 0f
                },
                keyguardTransitionInteractor.get().transitionValue(KeyguardState.AOD).map {
                    it > 0f
                },
            )
            .distinctUntilChanged()

    private val currentDisplayModeSupported: Flow<Boolean> =
        fingerprintPropertyRepository.sensorType.flatMapLatest {
            // SideFPS doesn't support AlternateBouncer in rear display mode
            if (it.isPowerButton()) {
                displayStateInteractor.get().isInRearDisplayMode.map { inRearDisplayMode ->
                    !inRearDisplayMode
                }
            } else {
                flowOf(true)
            }
        }

    /**
     * Whether the current biometric, bouncer, and keyguard states allow the alternate bouncer to
     * show.
     */
    val canShowAlternateBouncer: StateFlow<Boolean> =
        alternateBouncerSupported
            .flatMapLatest { alternateBouncerSupported ->
                if (alternateBouncerSupported) {
                    combine(
                            keyguardTransitionInteractor.get().currentKeyguardState,
                            sceneInteractor.get().currentScene,
                            secureLockDeviceInteractor.get().isSecureLockDeviceEnabled,
                            ::Triple,
                        )
                        .flatMapLatest {
                            (currentKeyguardState, transitionState, secureLockDeviceEnabled) ->
                            if (secureLockDeviceEnabled) {
                                flowOf(false)
                            } else if (currentKeyguardState == KeyguardState.GONE) {
                                flowOf(false)
                            } else if (
                                SceneContainerFlag.isEnabled && transitionState == Scenes.Gone
                            ) {
                                flowOf(false)
                            } else {
                                combine(
                                    deviceEntryBiometricsAllowedInteractor
                                        .get()
                                        .isFingerprintAuthCurrentlyAllowed,
                                    keyguardInteractor.get().isKeyguardDismissible,
                                    bouncerRepository.primaryBouncerShow,
                                    isDozingOrAod,
                                    currentDisplayModeSupported,
                                ) {
                                    fingerprintAllowed,
                                    keyguardDismissible,
                                    primaryBouncerShowing,
                                    dozing,
                                    currentDisplayModeSupported ->
                                    fingerprintAllowed &&
                                        !keyguardDismissible &&
                                        !primaryBouncerShowing &&
                                        !dozing &&
                                        currentDisplayModeSupported
                                }
                            }
                        }
                } else {
                    flowOf(false)
                }
            }
            .distinctUntilChanged()
            .onEach { Log.d(TAG, "canShowAlternateBouncer changed to $it") }
            .stateIn(scope = scope, started = WhileSubscribed(), initialValue = false)

    /**
     * Always shows the alternate bouncer. Requesters must check [canShowAlternateBouncer]` before
     * calling this.
     */
    fun forceShow() {
        bouncerRepository.setAlternateVisible(true)
    }

    /**
     * Sets the correct bouncer states to hide the bouncer. Should only be called through
     * StatusBarKeyguardViewManager until ScrimController is refactored to use
     * alternateBouncerInteractor.
     *
     * @return true if the alternate bouncer was newly hidden, else false.
     */
    fun hide(): Boolean {
        receivedDownTouch = false
        val wasAlternateBouncerVisible = isVisibleState()
        bouncerRepository.setAlternateVisible(false)
        return wasAlternateBouncerVisible && !isVisibleState()
    }

    fun isVisibleState(): Boolean {
        return bouncerRepository.alternateBouncerVisible.value
    }

    fun canShowAlternateBouncerForFingerprint(): Boolean {
        return canShowAlternateBouncer.value
    }

    /**
     * Should only be called through StatusBarKeyguardViewManager which propagates the source of
     * truth to other concerned controllers. Will hide the alternate bouncer if it's no longer
     * allowed to show.
     *
     * @return true if the alternate bouncer was newly hidden, else false.
     */
    fun maybeHide(): Boolean {
        if (isVisibleState() && !canShowAlternateBouncerForFingerprint()) {
            return hide()
        }
        return false
    }

    companion object {
        private const val TAG = "AlternateBouncerInteractor"
    }
}
