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
 *
 */

package com.android.systemui.biometrics.ui.viewmodel

import android.annotation.RawRes
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources.NotFoundException
import android.graphics.Rect
import android.hardware.biometrics.Flags
import android.security.Flags.secureLockDevice
import android.util.RotationUtils
import androidx.compose.runtime.getValue
import com.android.systemui.biometrics.domain.interactor.UdfpsOverlayInteractor
import com.android.systemui.biometrics.shared.model.UdfpsOverlayParams
import com.android.systemui.biometrics.ui.PromptIconState
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryUdfpsInteractor
import com.android.systemui.display.domain.interactor.DisplayStateInteractor
import com.android.systemui.display.shared.model.DisplayRotation
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.res.R
import com.android.systemui.securelockdevice.ui.viewmodel.SecureLockDeviceBiometricAuthContentViewModel
import com.android.systemui.util.kotlin.Quad
import com.android.systemui.util.kotlin.Quint
import com.android.systemui.util.kotlin.Sextuple
import com.android.systemui.util.kotlin.combine
import com.android.systemui.util.kotlin.sample
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Base ViewModel class for modeling UI of icon in biometric authentication dialogs. */
class BiometricAuthIconViewModel
@AssistedInject
constructor(
    @Assisted val promptViewModel: PromptViewModel? = null,
    @Assisted val secureLockDeviceViewModel: SecureLockDeviceBiometricAuthContentViewModel? = null,
    @Application private val applicationContext: Context,
    private val displayStateInteractor: DisplayStateInteractor,
    deviceEntryUdfpsInteractor: DeviceEntryUdfpsInteractor,
    udfpsOverlayInteractor: UdfpsOverlayInteractor,
) : HydratedActivatable() {

    /** Biometric auth modalities for the UI to display */
    enum class BiometricAuthModalities {
        Sfps,
        NonSfps, // UDFPS, REAR, HOME_BUTTON
        Face,
        SfpsCoex,
        NonSfpsCoex, // UDFPS, REAR, HOME_BUTTON
        None,
    }

    /** Indicates what biometric modalities are actively displayed on the UI for authentication. */
    val activeBiometricAuthType: Flow<BiometricAuthModalities> =
        if (promptViewModel != null) {
            combine(promptViewModel.modalities, promptViewModel.faceMode) { modalities, faceMode ->
                when {
                    modalities.hasFace && modalities.hasSfps && !faceMode ->
                        BiometricAuthModalities.SfpsCoex
                    modalities.hasFaceAndFingerprint && !faceMode ->
                        BiometricAuthModalities.NonSfpsCoex
                    modalities.hasFaceOnly || faceMode -> BiometricAuthModalities.Face
                    modalities.hasFingerprintOnly && modalities.hasSfps ->
                        BiometricAuthModalities.Sfps
                    modalities.hasFingerprintOnly -> BiometricAuthModalities.NonSfps
                    else -> BiometricAuthModalities.None
                }
            }
        } else if (secureLockDevice() && secureLockDeviceViewModel != null) {
            secureLockDeviceViewModel.enrolledStrongBiometrics.map { modalities ->
                when {
                    modalities.isSfpsStrong && modalities.isFaceStrong ->
                        BiometricAuthModalities.SfpsCoex
                    modalities.isUdfpsStrong && modalities.isFaceStrong ->
                        BiometricAuthModalities.NonSfpsCoex
                    modalities.isFaceStrong -> BiometricAuthModalities.Face
                    modalities.isSfpsStrong -> BiometricAuthModalities.Sfps
                    modalities.isUdfpsStrong -> BiometricAuthModalities.NonSfps
                    else -> BiometricAuthModalities.None
                }
            }
        } else {
            flowOf(BiometricAuthModalities.None)
        }

    private val hasSfps: Flow<Boolean> =
        promptViewModel?.modalities?.map { it.hasSfps }
            ?: if (secureLockDevice() && secureLockDeviceViewModel != null) {
                secureLockDeviceViewModel.enrolledStrongBiometrics.map { it.hasSfps }
            } else {
                flowOf(false)
            }

    /** Whether UDFPS is available on the biometric prompt. */
    val hasUdfps: Flow<Boolean> =
        promptViewModel?.modalities?.map { it.hasUdfps }
            ?: if (secureLockDevice() && secureLockDeviceViewModel != null) {
                secureLockDeviceViewModel.enrolledStrongBiometrics.map { it.hasUdfps }
            } else {
                flowOf(false)
            }
    /**
     * Hydrated state version of [hasUdfps] for use in composables. Should replace [hasUdfps] when
     * legacy icon view / view-binder are fully migrated to compose.
     */
    val hasUdfpsState: Boolean by
        hasUdfps.hydratedStateOf(traceName = "hasUdfps", initialValue = false)

    /** If the user is currently authenticating (i.e. at least one biometric is scanning). */
    val isAuthenticating: Flow<Boolean> =
        promptViewModel?.isAuthenticating
            ?: secureLockDeviceViewModel?.isAuthenticating
            ?: emptyFlow()

    /** Whether an error message is currently being shown. */
    val showingError: Flow<Boolean> =
        promptViewModel?.showingError ?: secureLockDeviceViewModel?.showingError ?: emptyFlow()
    /**
     * Hydrated state version of [showingError] for use in composables. Should replace
     * [showingError] when legacy icon view / view-binder are fully migrated to compose.
     */
    val showingErrorState: Boolean by
        showingError.hydratedStateOf(traceName = "showingError", initialValue = false)

    /** Whether the previous icon shown displayed an error. */
    internal val previousIconWasError: MutableStateFlow<Boolean> = MutableStateFlow(false)

    fun setPreviousIconWasError(wasError: Boolean) {
        previousIconWasError.value = wasError
    }

    /** If the user has successfully authenticated and confirmed (when explicitly required). */
    val isAuthenticated: Flow<PromptAuthState> =
        promptViewModel?.isAuthenticated
            ?: secureLockDeviceViewModel?.isAuthenticated
            ?: emptyFlow()

    private val isPendingConfirmation: Flow<Boolean> =
        isAuthenticated.map { authState ->
            authState.isAuthenticated && authState.needsUserConfirmation
        }
    /** If the auth is pending confirmation. */
    val isPendingConfirmationState: Boolean by
        isPendingConfirmation.hydratedStateOf(
            traceName = "isPendingConfirmation",
            initialValue = false,
        )

    /** Current biometric icon asset. */
    val iconAsset: Flow<Int> =
        activeBiometricAuthType.flatMapLatest { modalities ->
            when (modalities) {
                BiometricAuthModalities.Sfps ->
                    combine(
                            displayStateInteractor.currentRotation,
                            displayStateInteractor.isInRearDisplayMode,
                            isAuthenticated,
                            isAuthenticating,
                            showingError,
                            ::Quint,
                        )
                        .sample(previousIconWasError) {
                            (
                                rotation: DisplayRotation,
                                isInRearDisplayMode: Boolean,
                                authState: PromptAuthState,
                                isAuthenticating: Boolean,
                                showingError: Boolean),
                            previousIconWasError: Boolean ->
                            getSfpsIconViewAsset(
                                previousIconWasError,
                                rotation,
                                isInRearDisplayMode,
                                authState.isAuthenticated,
                                isAuthenticating,
                                showingError,
                            )
                        }
                BiometricAuthModalities.NonSfps ->
                    combine(isAuthenticated, isAuthenticating, showingError, ::Triple).sample(
                        previousIconWasError
                    ) {
                        (
                            authState: PromptAuthState,
                            isAuthenticating: Boolean,
                            showingError: Boolean),
                        previousIconWasError: Boolean ->
                        getFingerprintIconViewAsset(
                            previousIconWasError,
                            authState.isAuthenticated,
                            isAuthenticating,
                            showingError,
                        )
                    }
                BiometricAuthModalities.Face ->
                    combine(
                            isAuthenticated.distinctUntilChanged(),
                            isAuthenticating.distinctUntilChanged(),
                            isPendingConfirmation.distinctUntilChanged(),
                            showingError.distinctUntilChanged(),
                            ::Quad,
                        )
                        .sample(previousIconWasError) {
                            (
                                authState: PromptAuthState,
                                isAuthenticating: Boolean,
                                isPendingConfirmation: Boolean,
                                showingError: Boolean),
                            previousIconWasError: Boolean ->
                            getFaceIconViewAsset(
                                previousIconWasError,
                                authState,
                                isAuthenticating,
                                isPendingConfirmation,
                                showingError,
                            )
                        }
                BiometricAuthModalities.NonSfpsCoex ->
                    combine(
                            isAuthenticated,
                            isAuthenticating,
                            isPendingConfirmation,
                            showingError,
                            ::Quad,
                        )
                        .sample(previousIconWasError) {
                            (
                                authState: PromptAuthState,
                                isAuthenticating: Boolean,
                                isPendingConfirmation: Boolean,
                                showingError: Boolean),
                            previousIconWasError: Boolean ->
                            getCoexIconViewAsset(
                                previousIconWasError,
                                authState,
                                isAuthenticating,
                                isPendingConfirmation,
                                showingError,
                            )
                        }
                BiometricAuthModalities.SfpsCoex ->
                    combine(
                            displayStateInteractor.currentRotation,
                            displayStateInteractor.isInRearDisplayMode,
                            isAuthenticated,
                            isAuthenticating,
                            isPendingConfirmation,
                            showingError,
                            ::Sextuple,
                        )
                        .sample(previousIconWasError) {
                            (
                                rotation: DisplayRotation,
                                isInRearDisplayMode: Boolean,
                                authState: PromptAuthState,
                                isAuthenticating: Boolean,
                                isPendingConfirmation: Boolean,
                                showingError: Boolean),
                            previousIconWasError: Boolean ->
                            getCoexSfpsIconViewAsset(
                                previousIconWasError,
                                rotation,
                                isInRearDisplayMode,
                                authState,
                                isAuthenticating,
                                isPendingConfirmation,
                                showingError,
                            )
                        }
                else -> flowOf(-1)
            }
        }

    /** Layout params for fingerprint iconView */
    private val fingerprintIconWidth: Int =
        applicationContext.resources.getDimensionPixelSize(
            R.dimen.biometric_dialog_fingerprint_icon_width
        )
    /** Layout params for fingerprint iconView */
    private val fingerprintIconHeight: Int =
        applicationContext.resources.getDimensionPixelSize(
            R.dimen.biometric_dialog_fingerprint_icon_height
        )

    /** Layout params for face iconView */
    private val faceIconWidth: Int =
        applicationContext.resources.getDimensionPixelSize(R.dimen.biometric_dialog_face_icon_size)

    private val faceIconHeight: Int =
        applicationContext.resources.getDimensionPixelSize(R.dimen.biometric_dialog_face_icon_size)

    /** UDFPS sensor params */
    val udfpsOverlayParams: StateFlow<UdfpsOverlayParams> =
        udfpsOverlayInteractor.udfpsOverlayParams

    private val udfpsSensorWidth: Flow<Int> = udfpsOverlayParams.map { it.sensorBounds.width() }
    private val udfpsSensorHeight: Flow<Int> = udfpsOverlayParams.map { it.sensorBounds.height() }

    val udfpsSensorBounds: Flow<Rect> =
        combine(udfpsOverlayParams, displayStateInteractor.currentRotation) { params, rotation ->
                val rotatedBounds = Rect(params.sensorBounds)
                RotationUtils.rotateBounds(
                    rotatedBounds,
                    params.naturalDisplayWidth,
                    params.naturalDisplayHeight,
                    rotation.ordinal,
                )
                Rect(
                    rotatedBounds.left,
                    rotatedBounds.top,
                    params.logicalDisplayWidth - rotatedBounds.right,
                    params.logicalDisplayHeight - rotatedBounds.bottom,
                )
            }
            .distinctUntilChanged()

    val udfpsLocation by
        deviceEntryUdfpsInteractor.udfpsLocation.hydratedStateOf(
            traceName = "udfpsLocationState",
            initialValue = null,
        )

    /** The size of the biometric icon */
    val iconSize: Flow<Pair<Int, Int>> =
        combine(activeBiometricAuthType, hasUdfps, udfpsSensorWidth, udfpsSensorHeight) {
            modalities,
            hasUdfps,
            udfpsSensorWidth,
            udfpsSensorHeight ->
            if (modalities == BiometricAuthModalities.Face) {
                Pair(faceIconWidth, faceIconHeight)
            } else if (hasUdfps) {
                Pair(udfpsSensorWidth, udfpsSensorHeight)
            } else {
                Pair(fingerprintIconWidth, fingerprintIconHeight)
            }
        }

    /**
     * Hydrated state version of [iconSize] for use in composables. Should replace [iconSize] when
     * legacy icon view / view-binder are fully migrated to compose.
     */
    val iconSizeState: Pair<Int, Int> by
        iconSize.hydratedStateOf(traceName = "iconSize", initialValue = Pair(0, 0))

    /** Content description for iconView */
    val contentDescriptionId: Flow<Int> =
        activeBiometricAuthType.flatMapLatest { modalities ->
            when (modalities) {
                BiometricAuthModalities.Sfps,
                BiometricAuthModalities.SfpsCoex,
                BiometricAuthModalities.NonSfps,
                BiometricAuthModalities.NonSfpsCoex ->
                    combine(
                        hasSfps,
                        isAuthenticated,
                        isAuthenticating,
                        isPendingConfirmation,
                        showingError,
                    ) {
                        hasSfps: Boolean,
                        authState: PromptAuthState,
                        isAuthenticating: Boolean,
                        isPendingConfirmation: Boolean,
                        showingError: Boolean ->
                        getFingerprintIconContentDescriptionId(
                            hasSfps,
                            authState.isAuthenticated,
                            isAuthenticating,
                            isPendingConfirmation,
                            showingError,
                        )
                    }
                BiometricAuthModalities.Face ->
                    combine(isAuthenticated, isAuthenticating, showingError) {
                        authState: PromptAuthState,
                        isAuthenticating: Boolean,
                        showingError: Boolean ->
                        getFaceIconContentDescriptionId(authState, isAuthenticating, showingError)
                    }
                else -> emptyFlow()
            }
        }

    /** Content description for iconView */
    val contentDescription: Flow<String> =
        contentDescriptionId.filterNotNull().map {
            try {
                applicationContext.getString(it)
            } catch (e: NotFoundException) {
                ""
            }
        }

    /** Whether the current iconView asset animation should be playing. */
    val shouldAnimateIconView: Flow<Boolean> =
        activeBiometricAuthType.flatMapLatest { modalities ->
            when (modalities) {
                BiometricAuthModalities.Sfps,
                BiometricAuthModalities.SfpsCoex -> flowOf(true)
                BiometricAuthModalities.NonSfps ->
                    combine(isAuthenticated, isAuthenticating, showingError, ::Triple).sample(
                        previousIconWasError
                    ) {
                        (
                            authState: PromptAuthState,
                            isAuthenticating: Boolean,
                            showingError: Boolean),
                        previousIconWasError: Boolean ->
                        shouldAnimateFingerprintIconView(
                            previousIconWasError,
                            authState.isAuthenticated,
                            isAuthenticating,
                            showingError,
                        )
                    }
                BiometricAuthModalities.Face ->
                    combine(isAuthenticated, isAuthenticating, showingError, ::Triple).sample(
                        previousIconWasError
                    ) {
                        (
                            authState: PromptAuthState,
                            isAuthenticating: Boolean,
                            showingError: Boolean),
                        previousIconWasError: Boolean ->
                        shouldAnimateFaceIconView(
                            previousIconWasError,
                            authState.isAuthenticated,
                            isAuthenticating,
                            showingError,
                        )
                    }
                BiometricAuthModalities.NonSfpsCoex ->
                    combine(
                            isAuthenticated,
                            isAuthenticating,
                            isPendingConfirmation,
                            showingError,
                            ::Quad,
                        )
                        .sample(previousIconWasError) {
                            (
                                authState: PromptAuthState,
                                isAuthenticating: Boolean,
                                isPendingConfirmation: Boolean,
                                showingError: Boolean),
                            previousIconWasError: Boolean ->
                            shouldAnimateCoexIconView(
                                previousIconWasError,
                                authState.isAuthenticated,
                                isAuthenticating,
                                isPendingConfirmation,
                                showingError,
                            )
                        }
                else -> emptyFlow()
            }
        }

    /** Whether the current BiometricPromptLayout.iconView asset animation should be looping. */
    val shouldLoopIconView: Flow<Boolean> =
        activeBiometricAuthType.flatMapLatest { modalities ->
            when (modalities) {
                BiometricAuthModalities.Face -> isAuthenticating
                BiometricAuthModalities.Sfps,
                BiometricAuthModalities.NonSfps,
                BiometricAuthModalities.SfpsCoex,
                BiometricAuthModalities.NonSfpsCoex -> flowOf(false)
                else -> emptyFlow()
            }
        }

    /** Used to rotate the iconView for assets reused across rotations. */
    val iconViewRotation: Flow<Float> =
        combine(iconAsset, displayStateInteractor.currentRotation) {
            icon: Int,
            rotation: DisplayRotation ->
            if (assetReusedAcrossRotations(icon)) {
                when (rotation) {
                    DisplayRotation.ROTATION_0 -> 0f
                    DisplayRotation.ROTATION_90 -> 270f
                    DisplayRotation.ROTATION_180 -> 180f
                    DisplayRotation.ROTATION_270 -> 90f
                }
            } else {
                0f
            }
        }

    /** Current icon state */
    val iconState: Flow<PromptIconState> =
        combine(
                iconAsset,
                shouldAnimateIconView,
                shouldLoopIconView,
                contentDescriptionId,
                iconViewRotation,
                activeBiometricAuthType,
                showingError,
            ) { asset, shouldAnimate, shouldLoop, descId, rotation, activeBiometricAuthType, error
                ->
                PromptIconState(
                    asset,
                    shouldAnimate,
                    shouldLoop,
                    descId,
                    rotation,
                    activeBiometricAuthType,
                    error,
                )
            }
            .distinctUntilChanged()
    /**
     * Hydrated state version of [iconState] for use in composables. Should replace [iconState] when
     * legacy icon view / view-binder are fully migrated to compose.
     */
    val hydratedIconState: PromptIconState by
        iconState.hydratedStateOf(
            traceName = "iconState",
            initialValue =
                PromptIconState(
                    asset = -1,
                    shouldAnimate = false,
                    shouldLoop = false,
                    contentDescriptionId = -1,
                    rotation = 0f,
                    activeBiometricAuthType = BiometricAuthModalities.None,
                    showingError = false,
                ),
        )

    fun onConfigurationChanged(newConfig: Configuration) {
        displayStateInteractor.onConfigurationChanged(newConfig)
    }

    @RawRes
    fun getFingerprintIconViewAsset(
        previousIconWasError: Boolean,
        isAuthenticated: Boolean,
        isAuthenticating: Boolean,
        showingError: Boolean,
    ): Int {
        return when {
            isAuthenticated ->
                if (previousIconWasError) {
                    R.raw.fingerprint_dialogue_error_to_success_lottie
                } else {
                    R.raw.fingerprint_dialogue_fingerprint_to_success_lottie
                }
            isAuthenticating ->
                if (previousIconWasError) {
                    R.raw.fingerprint_dialogue_error_to_fingerprint_lottie
                } else {
                    R.raw.fingerprint_dialogue_fingerprint_to_error_lottie
                }
            showingError -> R.raw.fingerprint_dialogue_fingerprint_to_error_lottie
            Flags.bpFallbackOptions() -> R.raw.fingerprint_dialogue_fingerprint_to_error_lottie
            else -> -1
        }
    }

    @RawRes
    fun getSfpsIconViewAsset(
        previousIconWasError: Boolean,
        rotation: DisplayRotation,
        isInRearDisplayMode: Boolean,
        isAuthenticated: Boolean,
        isAuthenticating: Boolean,
        showingError: Boolean,
    ): Int {
        return if (isAuthenticated) {
            if (previousIconWasError) {
                R.raw.biometricprompt_sfps_error_to_success
            } else {
                getSfpsAsset_fingerprintToSuccess(rotation, isInRearDisplayMode)
            }
        } else if (isAuthenticating) {
            if (previousIconWasError) {
                getSfpsAsset_errorToFingerprint(rotation, isInRearDisplayMode)
            } else {
                getSfpsAsset_fingerprintAuthenticating(isInRearDisplayMode)
            }
        } else if (showingError) {
            getSfpsAsset_fingerprintToError(rotation, isInRearDisplayMode)
        } else if (Flags.bpFallbackOptions()) {
            getSfpsAsset_fingerprintAuthenticating(isInRearDisplayMode)
        } else {
            -1
        }
    }

    @RawRes
    fun getFaceIconViewAsset(
        previousIconWasError: Boolean,
        authState: PromptAuthState,
        isAuthenticating: Boolean,
        isPendingConfirmation: Boolean,
        showingError: Boolean,
    ): Int {
        return if (authState.isAuthenticated && isPendingConfirmation) {
            R.raw.face_dialog_wink_from_dark
        } else if (authState.isAuthenticated) {
            R.raw.face_dialog_dark_to_checkmark
        } else if (isAuthenticating) {
            R.raw.face_dialog_authenticating
        } else if (showingError) {
            R.raw.face_dialog_dark_to_error
        } else if (previousIconWasError) {
            R.raw.face_dialog_error_to_idle
        } else {
            R.raw.face_dialog_idle_static
        }
    }

    @RawRes
    fun getCoexIconViewAsset(
        previousIconWasError: Boolean,
        authState: PromptAuthState,
        isAuthenticating: Boolean,
        isPendingConfirmation: Boolean,
        showingError: Boolean,
    ): Int {
        return if (authState.isAuthenticatedAndExplicitlyConfirmed) {
            R.raw.fingerprint_dialogue_unlocked_to_checkmark_success_lottie
        } else if (isPendingConfirmation) {
            if (previousIconWasError) {
                R.raw.fingerprint_dialogue_error_to_unlock_lottie
            } else {
                R.raw.fingerprint_dialogue_fingerprint_to_unlock_lottie
            }
        } else if (authState.isAuthenticated) {
            if (previousIconWasError) {
                R.raw.fingerprint_dialogue_error_to_success_lottie
            } else {
                R.raw.fingerprint_dialogue_fingerprint_to_success_lottie
            }
        } else if (isAuthenticating) {
            if (previousIconWasError) {
                R.raw.fingerprint_dialogue_error_to_fingerprint_lottie
            } else {
                R.raw.fingerprint_dialogue_fingerprint_to_error_lottie
            }
        } else if (showingError) {
            R.raw.fingerprint_dialogue_fingerprint_to_error_lottie
        } else if (Flags.bpFallbackOptions()) {
            R.raw.fingerprint_dialogue_fingerprint_to_error_lottie
        } else {
            -1
        }
    }

    @RawRes
    fun getCoexSfpsIconViewAsset(
        previousIconWasError: Boolean,
        rotation: DisplayRotation,
        isInRearDisplayMode: Boolean,
        authState: PromptAuthState,
        isAuthenticating: Boolean,
        isPendingConfirmation: Boolean,
        showingError: Boolean,
    ): Int {
        return if (authState.isAuthenticatedAndExplicitlyConfirmed) {
            R.raw.biometricprompt_sfps_unlock_to_success
        } else if (isPendingConfirmation) {
            if (previousIconWasError) {
                R.raw.biometricprompt_sfps_error_to_unlock
            } else {
                getSfpsAsset_fingerprintToUnlock(rotation, isInRearDisplayMode)
            }
        } else if (authState.isAuthenticated) {
            if (previousIconWasError) {
                R.raw.biometricprompt_sfps_error_to_success
            } else {
                getSfpsAsset_fingerprintToSuccess(rotation, isInRearDisplayMode)
            }
        } else if (isAuthenticating) {
            if (previousIconWasError) {
                getSfpsAsset_errorToFingerprint(rotation, isInRearDisplayMode)
            } else {
                getSfpsAsset_fingerprintAuthenticating(isInRearDisplayMode)
            }
        } else if (showingError) {
            getSfpsAsset_fingerprintToError(rotation, isInRearDisplayMode)
        } else if (Flags.bpFallbackOptions()) {
            getSfpsAsset_fingerprintAuthenticating(isInRearDisplayMode)
        } else {
            -1
        }
    }

    fun getFingerprintIconContentDescriptionId(
        hasSfps: Boolean,
        isAuthenticated: Boolean,
        isAuthenticating: Boolean,
        isPendingConfirmation: Boolean,
        showingError: Boolean,
    ): Int {
        return when {
            isPendingConfirmation ->
                if (hasSfps) {
                    -1
                } else if (secureLockDevice() && secureLockDeviceViewModel != null) {
                    R.string.accessibility_confirm_biometric_auth_to_unlock
                } else {
                    R.string.biometric_dialog_confirm
                }
            isAuthenticating || isAuthenticated ->
                if (hasSfps) {
                    R.string.security_settings_sfps_enroll_find_sensor_message
                } else {
                    R.string.accessibility_fingerprint_label
                }
            showingError -> R.string.biometric_dialog_try_again
            else -> -1
        }
    }

    fun getFaceIconContentDescriptionId(
        authState: PromptAuthState,
        isAuthenticating: Boolean,
        showingError: Boolean,
    ): Int =
        when {
            authState.isAuthenticatedAndExplicitlyConfirmed ->
                R.string.biometric_dialog_face_icon_description_confirmed
            authState.isAuthenticated ->
                R.string.biometric_dialog_face_icon_description_authenticated
            isAuthenticating -> R.string.biometric_dialog_face_icon_description_authenticating
            showingError -> R.string.keyguard_face_failed
            else -> R.string.biometric_dialog_face_icon_description_idle
        }

    fun shouldAnimateFingerprintIconView(
        previousIconWasError: Boolean,
        isAuthenticated: Boolean,
        isAuthenticating: Boolean,
        showingError: Boolean,
    ) = (isAuthenticating && previousIconWasError) || isAuthenticated || showingError

    fun shouldAnimateFaceIconView(
        previousIconWasError: Boolean,
        isAuthenticated: Boolean,
        isAuthenticating: Boolean,
        showingError: Boolean,
    ) = isAuthenticating || isAuthenticated || showingError || previousIconWasError

    fun shouldAnimateCoexIconView(
        previousIconWasError: Boolean,
        isAuthenticated: Boolean,
        isAuthenticating: Boolean,
        isPendingConfirmation: Boolean,
        showingError: Boolean,
    ) =
        (isAuthenticating && previousIconWasError) ||
            isPendingConfirmation ||
            isAuthenticated ||
            showingError

    fun assetReusedAcrossRotations(asset: Int): Boolean {
        return asset in assetsReusedAcrossRotations
    }

    val assetsReusedAcrossRotations: List<Int> =
        listOf(
            R.raw.biometricprompt_sfps_fingerprint_authenticating,
            R.raw.biometricprompt_sfps_rear_display_fingerprint_authenticating,
            R.raw.biometricprompt_sfps_rear_display_fingerprint_authenticating,
        )

    fun getSfpsAsset_fingerprintAuthenticating(isInRearDisplayMode: Boolean): Int =
        if (isInRearDisplayMode) {
            R.raw.biometricprompt_sfps_rear_display_fingerprint_authenticating
        } else {
            R.raw.biometricprompt_sfps_fingerprint_authenticating
        }

    private fun getSfpsAsset_fingerprintToError(
        rotation: DisplayRotation,
        isInRearDisplayMode: Boolean,
    ): Int =
        if (isInRearDisplayMode) {
            when (rotation) {
                DisplayRotation.ROTATION_0 ->
                    R.raw.biometricprompt_sfps_rear_display_fingerprint_to_error
                DisplayRotation.ROTATION_90 ->
                    R.raw.biometricprompt_sfps_rear_display_fingerprint_to_error_90
                DisplayRotation.ROTATION_180 ->
                    R.raw.biometricprompt_sfps_rear_display_fingerprint_to_error_180
                DisplayRotation.ROTATION_270 ->
                    R.raw.biometricprompt_sfps_rear_display_fingerprint_to_error_270
            }
        } else {
            when (rotation) {
                DisplayRotation.ROTATION_0 -> R.raw.biometricprompt_sfps_fingerprint_to_error
                DisplayRotation.ROTATION_90 -> R.raw.biometricprompt_sfps_fingerprint_to_error_90
                DisplayRotation.ROTATION_180 -> R.raw.biometricprompt_sfps_fingerprint_to_error_180
                DisplayRotation.ROTATION_270 -> R.raw.biometricprompt_sfps_fingerprint_to_error_270
            }
        }

    private fun getSfpsAsset_errorToFingerprint(
        rotation: DisplayRotation,
        isInRearDisplayMode: Boolean,
    ): Int =
        if (isInRearDisplayMode) {
            when (rotation) {
                DisplayRotation.ROTATION_0 ->
                    R.raw.biometricprompt_sfps_rear_display_error_to_fingerprint
                DisplayRotation.ROTATION_90 ->
                    R.raw.biometricprompt_sfps_rear_display_error_to_fingerprint_90
                DisplayRotation.ROTATION_180 ->
                    R.raw.biometricprompt_sfps_rear_display_error_to_fingerprint_180
                DisplayRotation.ROTATION_270 ->
                    R.raw.biometricprompt_sfps_rear_display_error_to_fingerprint_270
            }
        } else {
            when (rotation) {
                DisplayRotation.ROTATION_0 -> R.raw.biometricprompt_sfps_error_to_fingerprint
                DisplayRotation.ROTATION_90 -> R.raw.biometricprompt_sfps_error_to_fingerprint_90
                DisplayRotation.ROTATION_180 -> R.raw.biometricprompt_sfps_error_to_fingerprint_180
                DisplayRotation.ROTATION_270 -> R.raw.biometricprompt_sfps_error_to_fingerprint_270
            }
        }

    private fun getSfpsAsset_fingerprintToUnlock(
        rotation: DisplayRotation,
        isInRearDisplayMode: Boolean,
    ): Int =
        if (isInRearDisplayMode) {
            when (rotation) {
                DisplayRotation.ROTATION_0 ->
                    R.raw.biometricprompt_sfps_rear_display_fingerprint_to_unlock
                DisplayRotation.ROTATION_90 ->
                    R.raw.biometricprompt_sfps_rear_display_fingerprint_to_unlock_90
                DisplayRotation.ROTATION_180 ->
                    R.raw.biometricprompt_sfps_rear_display_fingerprint_to_unlock_180
                DisplayRotation.ROTATION_270 ->
                    R.raw.biometricprompt_sfps_rear_display_fingerprint_to_unlock_270
            }
        } else {
            when (rotation) {
                DisplayRotation.ROTATION_0 -> R.raw.biometricprompt_sfps_fingerprint_to_unlock
                DisplayRotation.ROTATION_90 -> R.raw.biometricprompt_sfps_fingerprint_to_unlock_90
                DisplayRotation.ROTATION_180 -> R.raw.biometricprompt_sfps_fingerprint_to_unlock_180
                DisplayRotation.ROTATION_270 -> R.raw.biometricprompt_sfps_fingerprint_to_unlock_270
            }
        }

    private fun getSfpsAsset_fingerprintToSuccess(
        rotation: DisplayRotation,
        isInRearDisplayMode: Boolean,
    ): Int =
        if (isInRearDisplayMode) {
            when (rotation) {
                DisplayRotation.ROTATION_0 ->
                    R.raw.biometricprompt_sfps_rear_display_fingerprint_to_success
                DisplayRotation.ROTATION_90 ->
                    R.raw.biometricprompt_sfps_rear_display_fingerprint_to_success_90
                DisplayRotation.ROTATION_180 ->
                    R.raw.biometricprompt_sfps_rear_display_fingerprint_to_success_180
                DisplayRotation.ROTATION_270 ->
                    R.raw.biometricprompt_sfps_rear_display_fingerprint_to_success_270
            }
        } else {
            when (rotation) {
                DisplayRotation.ROTATION_0 -> R.raw.biometricprompt_sfps_fingerprint_to_success
                DisplayRotation.ROTATION_90 -> R.raw.biometricprompt_sfps_fingerprint_to_success_90
                DisplayRotation.ROTATION_180 ->
                    R.raw.biometricprompt_sfps_fingerprint_to_success_180
                DisplayRotation.ROTATION_270 ->
                    R.raw.biometricprompt_sfps_fingerprint_to_success_270
            }
        }

    @AssistedFactory
    interface Factory {
        fun create(
            promptViewModel: PromptViewModel? = null,
            secureLockDeviceViewModel: SecureLockDeviceBiometricAuthContentViewModel? = null,
        ): BiometricAuthIconViewModel
    }

    override suspend fun onActivated(): Nothing {
        coroutineScope { awaitCancellation() }
    }

    companion object {
        const val TAG = "BiometricAuthIconViewModel"
    }
}
