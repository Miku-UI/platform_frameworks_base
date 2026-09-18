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
package com.android.systemui.deviceentry.domain.interactor

import android.graphics.PointF
import android.hardware.biometrics.BiometricSourceType
import com.android.systemui.biometrics.data.repository.FacePropertyRepository
import com.android.systemui.biometrics.domain.interactor.PeripheralFpsInteractor
import com.android.systemui.biometrics.domain.interactor.SideFpsSensorInteractor
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.shared.model.BiometricUnlockSource
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn

/** Business logic for device entry auth ripple interactions. */
@SysUISingleton
class AuthRippleInteractor
@Inject
constructor(
    deviceEntrySourceInteractor: DeviceEntrySourceInteractor,
    deviceEntryUdfpsInteractor: DeviceEntryUdfpsInteractor,
    keyguardInteractor: KeyguardInteractor,
    sideFpsSensorInteractor: SideFpsSensorInteractor,
    facePropertyRepository: FacePropertyRepository,
    peripheralFpsInteractor: PeripheralFpsInteractor,
    @Background private val backgroundScope: CoroutineScope,
) {
    private val successfulEntryFromDeviceEntryIcon: Flow<Unit> =
        deviceEntrySourceInteractor.attemptEnterDeviceFromDeviceEntryIcon
            .map { keyguardInteractor.isKeyguardDismissible.value }
            .filter { it } // only emit events if the keyguard is dismissible
            // map to Unit
            .map {}

    @OptIn(ExperimentalCoroutinesApi::class)
    private val showUnlockRippleFromDeviceEntryIcon: Flow<BiometricUnlockSource> =
        deviceEntryUdfpsInteractor.isUdfpsSupported.flatMapLatest { isUdfpsSupported ->
            if (isUdfpsSupported) {
                successfulEntryFromDeviceEntryIcon.map { BiometricUnlockSource.FINGERPRINT_SENSOR }
            } else {
                emptyFlow()
            }
        }

    private val showUnlockRippleFromBiometricUnlock: Flow<BiometricUnlockSource> =
        deviceEntrySourceInteractor.deviceEntryFromBiometricSource

    private val sfpsSensorLocation =
        sideFpsSensorInteractor.sensorLocation.filterNotNull().map {
            PointF(it.left.toFloat(), it.top + it.length / 2f)
        }

    private val faceSensorOrigin =
        facePropertyRepository.sensorLocation.filterNotNull().map {
            PointF(it.x.toFloat(), it.y.toFloat())
        }

    private val rippleEvents = Channel<AuthRippleEvent>(Channel.CONFLATED)

    // For ADB debugging only. Configured via:
    // adb shell cmd statusbar auth-ripple custom <x> <y>
    private val _adbCommandCustomSensorLocation = MutableStateFlow<PointF?>(null)
    // For ADB debugging only. Set to null for "custom" command.
    private var _adbCommandRippleSource = MutableStateFlow<BiometricUnlockSource?>(null)
    // For ADB debugging only. Set to false to re-enable triggering.
    private var _showAdbCommandRipple = MutableStateFlow(false)

    val udfpsLocation: StateFlow<PointF> =
        deviceEntryUdfpsInteractor.udfpsLocation
            .filterNotNull()
            .map { PointF(it.centerX, it.centerY) }
            .stateIn(
                scope = backgroundScope,
                started = SharingStarted.Eagerly,
                initialValue = PointF(),
            )

    val udfpsRadius: StateFlow<Float> =
        deviceEntryUdfpsInteractor.udfpsLocation
            .filterNotNull()
            .map { it.radius }
            .stateIn(scope = backgroundScope, started = SharingStarted.Eagerly, initialValue = 0F)

    // For ADB debugging only. Map the ADB command source to BiometricUnlockSource.
    private val adbCommandRipple =
        combine(_showAdbCommandRipple.filter { it }, _adbCommandRippleSource, ::Pair).map {
            (_, source) ->
            source
        }

    val showUnlockRipple: Flow<BiometricUnlockSource?> =
        merge(
                showUnlockRippleFromDeviceEntryIcon,
                showUnlockRippleFromBiometricUnlock,
                adbCommandRipple,
            )
            .map {
                rippleEvents.trySend(AuthRippleEvent.PlayUnlockRipple)
                it
            }

    @OptIn(ExperimentalCoroutinesApi::class)
    val sensorOrigin: StateFlow<PointF> =
        showUnlockRipple
            .flatMapLatest { source ->
                when (source) {
                    BiometricUnlockSource.FINGERPRINT_SENSOR ->
                        combine(
                                deviceEntryUdfpsInteractor.isUdfpsSupported,
                                peripheralFpsInteractor.isSupported,
                                ::Pair,
                            )
                            .flatMapLatest { (isUdfpsSupported, isPeripheralLocationSupported) ->
                                if (isUdfpsSupported) {
                                    udfpsLocation
                                } else if (isPeripheralLocationSupported) {
                                    peripheralFpsInteractor.locationForRippleEffect
                                } else {
                                    sfpsSensorLocation
                                }
                            }
                    BiometricUnlockSource.FACE_SENSOR -> faceSensorOrigin
                    // For ADB debugging only: custom sensor location.
                    null -> _adbCommandCustomSensorLocation
                }
            }
            .filterNotNull()
            .stateIn(
                scope = backgroundScope,
                started = SharingStarted.Eagerly,
                initialValue = PointF(),
            )

    /**
     * The current phase of the "dwell" ripple (the animation playing while holding a finger on the
     * sensor).
     *
     * This StateFlow manages a state machine to ensure valid transitions:
     * - [DwellRipplePhase.IDLE]: No interaction.
     * - [DwellRipplePhase.PULSE_OUT]: User is touching the sensor.
     * - [DwellRipplePhase.RETRACT]: User lifted finger without authenticating (only valid from
     *   PULSE_OUT).
     * - [DwellRipplePhase.FADE_OUT]: User authenticated or UI reset, e.g. to bouncer (valid from
     *   PULSE/RETRACT). It automatically resets to [DwellRipplePhase.IDLE] when the Keyguard
     *   becomes visible.
     */
    val dwellRipplePhase: StateFlow<DwellRipplePhase> =
        merge(
                rippleEvents.receiveAsFlow(),
                keyguardInteractor.isKeyguardVisible.filter { it }.map { AuthRippleEvent.Reset },
            )
            .scan(DwellRipplePhase.IDLE) { currentPhase, event ->
                when (event) {
                    is AuthRippleEvent.PulseOut ->
                        if (currentPhase != DwellRipplePhase.FADE_OUT) {
                            DwellRipplePhase.PULSE_OUT
                        } else {
                            currentPhase
                        }

                    is AuthRippleEvent.Retract ->
                        if (currentPhase == DwellRipplePhase.PULSE_OUT) {
                            DwellRipplePhase.RETRACT
                        } else {
                            currentPhase
                        }

                    is AuthRippleEvent.Reset -> DwellRipplePhase.IDLE
                    is AuthRippleEvent.FadeOut,
                    is AuthRippleEvent.PlayUnlockRipple ->
                        if (currentPhase != DwellRipplePhase.IDLE) {
                            DwellRipplePhase.FADE_OUT
                        } else {
                            currentPhase
                        }
                }
            }
            .distinctUntilChanged()
            .stateIn(
                scope = backgroundScope,
                started = SharingStarted.Eagerly,
                initialValue = DwellRipplePhase.IDLE,
            )

    fun sendAuthRippleEvent(event: AuthRippleEvent) {
        rippleEvents.trySend(event)
    }

    // For ADB debugging only.
    fun sendAdbCommand(source: BiometricSourceType?) {
        _showAdbCommandRipple.value = true
        _adbCommandRippleSource.value = BiometricUnlockSource.fromBiometricSourceType(source)
    }

    fun setSensorLocation(pointF: PointF) {
        _adbCommandCustomSensorLocation.value = pointF
    }

    // For ADB debugging only. Set to false to re-enable triggering.
    fun resetAdbTriggeredRipple() {
        _showAdbCommandRipple.value = false
    }

    /** Represents the current state or phase of the UDFPS dwell ripple animation. */
    enum class DwellRipplePhase {
        /** The ripple is not visible and is in a quiescent state. */
        IDLE,

        /** The ripple is expanding, indicating the user is dwelling on the sensor. */
        PULSE_OUT,

        /** The ripple is fading out (e.g., due to successful authentication or UI reset). */
        FADE_OUT,

        /**
         * The ripple is actively retracting back towards the sensor center (e.g., due to failed
         * authentication).
         */
        RETRACT,
    }

    sealed interface AuthRippleEvent {
        data object PulseOut : AuthRippleEvent

        data object Retract : AuthRippleEvent

        data object PlayUnlockRipple : AuthRippleEvent

        data object FadeOut : AuthRippleEvent

        data object Reset : AuthRippleEvent
    }
}
