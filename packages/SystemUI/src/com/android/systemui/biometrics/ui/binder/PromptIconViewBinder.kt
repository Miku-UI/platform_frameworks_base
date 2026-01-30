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

package com.android.systemui.biometrics.ui.binder

import android.content.res.Resources
import android.hardware.biometrics.Flags.bpFallbackOptions
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.airbnb.lottie.LottieAnimationView
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.settingslib.widget.LottieColorUtils
import com.android.systemui.Flags.bpColors
import com.android.systemui.biometrics.BiometricAuthIconAssets
import com.android.systemui.biometrics.ui.viewmodel.BiometricAuthIconViewModel
import com.android.systemui.biometrics.ui.viewmodel.PromptIconViewModel
import com.android.systemui.biometrics.ui.viewmodel.PromptViewModel
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.util.kotlin.Quad
import com.android.systemui.util.kotlin.Utils.Companion.toQuint
import com.android.systemui.util.kotlin.sample
import kotlinx.coroutines.flow.combine

private const val TAG = "PromptIconViewBinder"

/** Sub-binder for [BiometricPromptLayout.iconView]. */
object PromptIconViewBinder {
    /** Binds [BiometricPromptLayout.iconView] to [PromptIconViewModel]. */
    @JvmStatic
    fun bind(iconView: LottieAnimationView, promptViewModel: PromptViewModel) {
        val viewModel = promptViewModel.iconViewModel
        iconView.repeatWhenAttached {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.onConfigurationChanged(iconView.context.resources.configuration)

                if (bpFallbackOptions()) {
                    launch {
                        viewModel.iconState.collect { state ->
                            if (state.asset != -1) {
                                iconView.updateAsset(
                                    "iconAsset",
                                    state.asset,
                                    state.shouldAnimate,
                                    state.shouldLoop,
                                    state.activeBiometricAuthType,
                                )
                            }

                            if (state.contentDescriptionId != -1) {
                                iconView.contentDescription =
                                    iconView.context.getString(state.contentDescriptionId)
                            }

                            iconView.rotation = state.rotation

                            viewModel.setPreviousIconWasError(state.showingError)
                        }
                    }
                } else {
                    launch {
                        viewModel.iconAsset
                            .sample(
                                combine(
                                    viewModel.activeBiometricAuthType,
                                    viewModel.shouldAnimateIconView,
                                    viewModel.shouldLoopIconView,
                                    viewModel.showingError,
                                    ::Quad,
                                ),
                                ::toQuint,
                            )
                            .collect {
                                (
                                    iconAsset,
                                    activeBiometricAuthType,
                                    shouldAnimateIconView,
                                    shouldLoopIconView,
                                    showingError) ->
                                if (iconAsset != -1) {
                                    iconView.updateAsset(
                                        "iconAsset",
                                        iconAsset,
                                        shouldAnimateIconView,
                                        shouldLoopIconView,
                                        activeBiometricAuthType,
                                    )
                                    viewModel.setPreviousIconWasError(showingError)
                                }
                            }
                    }

                    launch {
                        viewModel.iconViewRotation.collect { rotation ->
                            iconView.rotation = rotation
                        }
                    }

                    launch {
                        viewModel.contentDescriptionId.collect { id ->
                            if (id != -1) {
                                iconView.contentDescription = iconView.context.getString(id)
                            }
                        }
                    }
                }
            }
        }
    }
}

fun LottieAnimationView.updateAsset(
    type: String,
    asset: Int,
    shouldAnimateIconView: Boolean,
    shouldLoopIconView: Boolean,
    activeBiometricAuthType: BiometricAuthIconViewModel.BiometricAuthModalities,
) {
    setFailureListener(type, asset, activeBiometricAuthType)
    pauseAnimation()
    setAnimation(asset)
    val animatingFromSfpsAuthenticating =
        BiometricAuthIconAssets.animatingFromSfpsAuthenticating(asset)
    if (animatingFromSfpsAuthenticating) {
        // Skipping to error / success / unlock segment of animation
        setMinFrame(158)
    } else {
        frame = 0
    }
    if (shouldAnimateIconView) {
        loop(shouldLoopIconView)
        playAnimation()
    }
    LottieColorUtils.applyDynamicColors(context, this)
    if (bpColors()) {
        LottieColorUtils.applyMaterialColor(context, this)
    }
}

private fun LottieAnimationView.setFailureListener(
    type: String,
    asset: Int,
    activeBiometricAuthType: BiometricAuthIconViewModel.BiometricAuthModalities,
) {
    val assetName =
        try {
            context.resources.getResourceEntryName(asset)
        } catch (e: Resources.NotFoundException) {
            "Asset $asset not found"
        }

    setFailureListener { result: Throwable? ->
        Log.d(
            TAG,
            "Collecting $type: activeBiometricAuthType = $activeBiometricAuthType, invalid " +
                    "resource id: $asset, name $assetName",
            result,
        )
    }
}
