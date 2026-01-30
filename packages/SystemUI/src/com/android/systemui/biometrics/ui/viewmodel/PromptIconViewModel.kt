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
 *
 */

package com.android.systemui.biometrics.ui.viewmodel

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import com.android.systemui.biometrics.ui.PromptIconState
import com.android.systemui.biometrics.ui.PromptPosition
import com.android.systemui.biometrics.ui.isMedium
import com.android.systemui.biometrics.ui.isSmall
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.res.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** Models UI of [BiometricPromptLayout.iconView] */
class PromptIconViewModel
@AssistedInject
constructor(
    @Assisted private val promptViewModel: PromptViewModel,
    @Assisted private val biometricAuthIconViewModelFactory: BiometricAuthIconViewModel.Factory,
    @Application private val context: Context,
) : ExclusiveActivatable() {
    val internal: BiometricAuthIconViewModel =
        biometricAuthIconViewModelFactory.create(
            promptViewModel = promptViewModel,
            secureLockDeviceViewModel = null,
        )

    /** Padding for placing icons */
    val portraitSmallBottomPadding =
        context.resources.getDimensionPixelSize(
            R.dimen.biometric_prompt_portrait_small_bottom_padding
        )
    val portraitMediumBottomPadding =
        context.resources.getDimensionPixelSize(
            R.dimen.biometric_prompt_portrait_medium_bottom_padding
        )
    val portraitLargeScreenBottomPadding =
        context.resources.getDimensionPixelSize(
            R.dimen.biometric_prompt_portrait_large_screen_bottom_padding
        )
    val landscapeSmallBottomPadding =
        context.resources.getDimensionPixelSize(
            R.dimen.biometric_prompt_landscape_small_bottom_padding
        )
    val landscapeSmallHorizontalPadding =
        context.resources.getDimensionPixelSize(
            R.dimen.biometric_prompt_landscape_small_horizontal_padding
        )
    val landscapeMediumBottomPadding =
        context.resources.getDimensionPixelSize(
            R.dimen.biometric_prompt_landscape_medium_bottom_padding
        )
    val landscapeMediumHorizontalPadding =
        context.resources.getDimensionPixelSize(
            R.dimen.biometric_prompt_landscape_medium_horizontal_padding
        )

    /** Rect for positioning biometric icon */
    val iconPosition: Flow<Rect> =
        combine(
                internal.udfpsSensorBounds,
                promptViewModel.size,
                promptViewModel.position,
                promptViewModel.modalities,
            ) { sensorBounds, size, position, modalities ->
                when (position) {
                    PromptPosition.Bottom ->
                        if (size.isSmall) {
                            Rect(0, 0, 0, portraitSmallBottomPadding)
                        } else if (size.isMedium && modalities.hasUdfps) {
                            Rect(0, 0, 0, sensorBounds.bottom)
                        } else if (size.isMedium) {
                            Rect(0, 0, 0, portraitMediumBottomPadding)
                        } else {
                            // Large screen
                            Rect(0, 0, 0, portraitLargeScreenBottomPadding)
                        }
                    PromptPosition.Right ->
                        if (size.isSmall || modalities.hasFaceOnly) {
                            Rect(0, 0, landscapeSmallHorizontalPadding, landscapeSmallBottomPadding)
                        } else if (size.isMedium && modalities.hasUdfps) {
                            Rect(0, 0, sensorBounds.right, sensorBounds.bottom)
                        } else {
                            // SFPS
                            Rect(
                                0,
                                0,
                                landscapeMediumHorizontalPadding,
                                landscapeMediumBottomPadding,
                            )
                        }
                    PromptPosition.Left ->
                        if (size.isSmall || modalities.hasFaceOnly) {
                            Rect(landscapeSmallHorizontalPadding, 0, 0, landscapeSmallBottomPadding)
                        } else if (size.isMedium && modalities.hasUdfps) {
                            Rect(sensorBounds.left, 0, 0, sensorBounds.bottom)
                        } else {
                            // SFPS
                            Rect(
                                landscapeMediumHorizontalPadding,
                                0,
                                0,
                                landscapeMediumBottomPadding,
                            )
                        }
                    PromptPosition.Top ->
                        if (size.isSmall) {
                            Rect(0, 0, 0, portraitSmallBottomPadding)
                        } else if (size.isMedium && modalities.hasUdfps) {
                            Rect(0, 0, 0, sensorBounds.bottom)
                        } else {
                            Rect(0, 0, 0, portraitMediumBottomPadding)
                        }
                }
            }
            .distinctUntilChanged()

    val iconSize: Flow<Pair<Int, Int>> = internal.iconSize

    /** Current icon state */
    val iconState: Flow<PromptIconState> = internal.iconState

    fun setPreviousIconWasError(wasError: Boolean) {
        internal.setPreviousIconWasError(wasError)
    }

    // Remove after bpFallback()
    val activeBiometricAuthType: Flow<BiometricAuthIconViewModel.BiometricAuthModalities> =
        internal.activeBiometricAuthType
    val contentDescriptionId: Flow<Int> = internal.contentDescriptionId
    val iconAsset: Flow<Int> = internal.iconAsset

    /** Used to rotate the iconView for assets reused across rotations. */
    val iconViewRotation: Flow<Float> = internal.iconViewRotation
    val shouldAnimateIconView: Flow<Boolean> = internal.shouldAnimateIconView
    val shouldLoopIconView: Flow<Boolean> = internal.shouldLoopIconView
    val showingError: Flow<Boolean> = internal.showingError

    /** Called on configuration changes */
    fun onConfigurationChanged(newConfig: Configuration) {
        internal.onConfigurationChanged(newConfig)
    }

    @AssistedFactory
    interface Factory {
        fun create(
            promptViewModel: PromptViewModel,
            biometricAuthIconViewModelFactory: BiometricAuthIconViewModel.Factory,
        ): PromptIconViewModel
    }

    override suspend fun onActivated(): Nothing {
        coroutineScope { awaitCancellation() }
    }
}
