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
package com.android.systemui.securelockdevice.ui.composable

import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieClipSpec
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.android.compose.animation.Easings
import com.android.compose.modifiers.height
import com.android.compose.modifiers.width
import com.android.internal.jank.Cuj
import com.android.internal.jank.InteractionJankMonitor
import com.android.systemui.Flags.bpColors
import com.android.systemui.biometrics.BiometricAuthIconAssets
import com.android.systemui.bouncer.shared.model.SecureLockDeviceBouncerActionButtonModel
import com.android.systemui.deviceentry.ui.binder.UdfpsAccessibilityOverlayBinder
import com.android.systemui.deviceentry.ui.view.UdfpsAccessibilityOverlay
import com.android.systemui.deviceentry.ui.viewmodel.AlternateBouncerUdfpsAccessibilityOverlayViewModel
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.res.R
import com.android.systemui.securelockdevice.ui.viewmodel.SecureLockDeviceBiometricAuthContentViewModel
import com.android.systemui.util.ui.compose.LottieColorUtils
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

private val TO_BOUNCER_DURATION = 400.milliseconds
private val TO_GONE_DURATION = 500.milliseconds

@Composable
fun SecureLockDeviceContent(
    secureLockDeviceViewModelFactory: SecureLockDeviceBiometricAuthContentViewModel.Factory,
    modifier: Modifier = Modifier,
) {
    val secureLockDeviceViewModel =
        rememberViewModel(traceName = "SecureLockDeviceBiometricAuthContentViewModel") {
            secureLockDeviceViewModelFactory.create()
        }

    val view = LocalView.current

    val interactionJankMonitor: InteractionJankMonitor =
        secureLockDeviceViewModel.interactionJankMonitor

    val isVisible = secureLockDeviceViewModel.isVisible
    val isReadyToDismissBiometricAuth = secureLockDeviceViewModel.isReadyToDismissBiometricAuth
    val visibleState = remember { MutableTransitionState(isVisible) }

    /** This effect is run when the composable enters the composition */
    LaunchedEffect(Unit) { secureLockDeviceViewModel.startAppearAnimation() }

    /**
     * Updates the [visibleState] that drives the [AnimatedVisibility] animation.
     *
     * When [SecureLockDeviceBiometricAuthContentViewModel.isVisible] changes, this effect updates
     * the [MutableTransitionState.targetState] of the [visibleState], which triggers the animation.
     */
    LaunchedEffect(isVisible) { visibleState.targetState = isVisible }

    /**
     * Watches [visibleState] to track jank for the appear and disappear animations.
     *
     * When the disappear animation is complete, this calls
     * [SecureLockDeviceBiometricAuthContentViewModel.onDisappearAnimationFinished] to allow the
     * legacy keyguard to delay dismissal of the biometric auth composable until the animations on
     * the UI have finished playing on the UI.
     */
    LaunchedEffect(visibleState.currentState, visibleState.targetState, visibleState.isIdle) {
        handleJankMonitoring(
            currentState = visibleState.currentState,
            isCurrentStateIdle = visibleState.isIdle,
            targetState = visibleState.targetState,
            isReadyToDismissBiometricAuth = isReadyToDismissBiometricAuth,
            interactionJankMonitor = interactionJankMonitor,
            view = view,
            onDisappearAnimationFinished = {
                secureLockDeviceViewModel.onDisappearAnimationFinished()
            },
        )
    }

    /** Animates the biometric auth content in and out of view. */
    AnimatedVisibility(
        visibleState = visibleState,
        enter =
            fadeIn(tween(durationMillis = TO_BOUNCER_DURATION.toInt(DurationUnit.MILLISECONDS))),
        exit = fadeOut(tween(durationMillis = TO_GONE_DURATION.toInt(DurationUnit.MILLISECONDS))),
        modifier = modifier,
    ) {
        Box(modifier = modifier.background(color = Color.Transparent).fillMaxSize()) {
            ButtonArea(
                viewModel = secureLockDeviceViewModel,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            )
        }

        val hasUdfps: Boolean = secureLockDeviceViewModel.iconViewModel.hasUdfpsState
        val iconSize: Pair<Int, Int> = secureLockDeviceViewModel.iconViewModel.iconSizeState
        var globalCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

        Box(
            modifier =
                Modifier.fillMaxSize().onGloballyPositioned { coordinates ->
                    globalCoordinates = coordinates
                }
        ) {
            val udfpsLocation = secureLockDeviceViewModel.iconViewModel.udfpsLocation
            val iconModifier =
                if (hasUdfps && udfpsLocation != null && globalCoordinates != null) {
                    with(LocalDensity.current) {
                        val yOffset = globalCoordinates?.positionInWindow()?.y ?: 0f
                        Modifier.align(Alignment.TopStart)
                            .offset(
                                x = (udfpsLocation.centerX - udfpsLocation.radius).toDp(),
                                y = (udfpsLocation.centerY - udfpsLocation.radius - yOffset).toDp(),
                            )
                    }
                } else {
                    Modifier.align(Alignment.BottomCenter)
                        .padding(
                            bottom =
                                dimensionResource(
                                    R.dimen.biometric_prompt_portrait_medium_bottom_padding
                                )
                        )
                }

            BiometricIconLottie(
                viewModel = secureLockDeviceViewModel,
                modifier = iconModifier.width { iconSize.first }.height { iconSize.second },
            )
        }

        val shouldListenForBiometricAuth = secureLockDeviceViewModel.shouldListenForBiometricAuth
        if (hasUdfps && shouldListenForBiometricAuth) {
            UdfpsA11yOverlay(
                viewModel = secureLockDeviceViewModel.udfpsAccessibilityOverlayViewModel,
                modifier = Modifier.fillMaxHeight(),
            )
        }
    }
}

/** Handles InteractionJankMonitor tracking for the appear and disappear animations. */
@VisibleForTesting
fun handleJankMonitoring(
    currentState: Boolean,
    isCurrentStateIdle: Boolean,
    targetState: Boolean,
    isReadyToDismissBiometricAuth: Boolean,
    interactionJankMonitor: InteractionJankMonitor,
    view: View,
    onDisappearAnimationFinished: () -> Unit,
) {
    if (!currentState && targetState) { // Start appear animation
        // Start appear animation
        interactionJankMonitor.begin(
            /* v = */ view,
            /* cujType = */ Cuj.CUJ_BOUNCER_SECURE_LOCK_DEVICE_BIOMETRIC_AUTH_APPEAR,
        )
    } else if (currentState && isCurrentStateIdle) { // Appear animation complete
        interactionJankMonitor.end(
            /* cujType = */ Cuj.CUJ_BOUNCER_SECURE_LOCK_DEVICE_BIOMETRIC_AUTH_APPEAR
        )
    } else if (currentState && !targetState) { // Disappear animation started
        if (isReadyToDismissBiometricAuth) {
            interactionJankMonitor.begin(
                /* v = */ view,
                /* cujType = */ Cuj.CUJ_BOUNCER_SECURE_LOCK_DEVICE_BIOMETRIC_AUTH_DISAPPEAR,
            )
        }
    } else if (!currentState && isCurrentStateIdle) { // Disappear animation complete
        if (isReadyToDismissBiometricAuth) {
            interactionJankMonitor.end(
                /* cujType = */ Cuj.CUJ_BOUNCER_SECURE_LOCK_DEVICE_BIOMETRIC_AUTH_DISAPPEAR
            )
            onDisappearAnimationFinished()
        }
    }
}

@Composable
private fun BiometricIconLottie(
    viewModel: SecureLockDeviceBiometricAuthContentViewModel,
    modifier: Modifier = Modifier,
) {
    val iconViewModel = viewModel.iconViewModel
    val iconState = viewModel.iconViewModel.hydratedIconState
    if (iconState.asset == -1) {
        return
    }
    val iconContentDescription =
        if (iconState.contentDescriptionId != -1) stringResource(iconState.contentDescriptionId)
        else ""
    val showingError = iconViewModel.showingErrorState
    val isPendingConfirmation = iconViewModel.isPendingConfirmationState

    val lottie by
        rememberLottieComposition(
            spec = LottieCompositionSpec.RawRes(iconState.asset),
            cacheKey = iconState.asset.toString(),
        )

    val animatingFromSfpsAuthenticating =
        BiometricAuthIconAssets.animatingFromSfpsAuthenticating(iconState.asset)
    val minFrame: Int =
        if (animatingFromSfpsAuthenticating) {
            // Skipping to error / success / unlock segment of animation
            158
        } else {
            0
        }

    val numIterations =
        if (iconState.shouldLoop) {
            LottieConstants.IterateForever
        } else {
            1
        }

    val progress by
        animateLottieCompositionAsState(
            composition = lottie,
            isPlaying = iconState.shouldAnimate && lottie != null,
            iterations = numIterations,
            clipSpec = LottieClipSpec.Frame(min = minFrame),
        )
    if (progress == 1f) {
        viewModel.onIconAnimationFinished()
    }

    LottieAnimation(
        composition = lottie,
        dynamicProperties = LottieColorUtils.getDynamicProperties(bpColors()),
        modifier =
            modifier
                .graphicsLayer { rotationZ = iconState.rotation }
                .semantics { contentDescription = iconContentDescription }
                .then(
                    if (isPendingConfirmation) {
                        Modifier.clickable(
                            role = Role.Button,
                            onClick = { viewModel.onConfirmButtonClicked() },
                        )
                    } else {
                        Modifier
                    }
                ),
        progress = { progress },
        contentScale = ContentScale.FillBounds,
    )

    SideEffect { iconViewModel.setPreviousIconWasError(showingError) }
}

@Composable
fun ButtonArea(
    viewModel: SecureLockDeviceBiometricAuthContentViewModel,
    modifier: Modifier = Modifier,
) {
    val actionButton: SecureLockDeviceBouncerActionButtonModel? = viewModel.actionButton
    val appearFadeInAnimatable = remember { Animatable(0f) }
    val appearMoveAnimatable = remember { Animatable(0f) }
    val appearAnimationInitialOffset = with(LocalDensity.current) { 80.dp.roundToPx() }

    actionButton?.let { actionButtonModel ->
        LaunchedEffect(Unit) {
            appearFadeInAnimatable.animateTo(
                targetValue = 1f,
                animationSpec =
                    tween(
                        durationMillis = 450,
                        delayMillis = 133,
                        easing = Easings.LegacyDecelerate,
                    ),
            )
        }
        LaunchedEffect(Unit) {
            appearMoveAnimatable.animateTo(
                targetValue = 1f,
                animationSpec =
                    tween(
                        durationMillis = 450,
                        delayMillis = 133,
                        easing = Easings.StandardDecelerate,
                    ),
            )
        }

        val textColor =
            colorResource(id = com.android.internal.R.color.materialColorOnSecondaryContainer)
        val backgroundColor =
            colorResource(id = com.android.internal.R.color.materialColorSecondaryContainer)
        val contentDescription = actionButtonModel.contentDescriptionId?.let { stringResource(it) }

        Box(
            modifier =
                modifier
                    .graphicsLayer {
                        // Translate the button up from an initially pushed-down position:
                        translationY =
                            (1 - appearMoveAnimatable.value) * appearAnimationInitialOffset
                        // Fade the button in:
                        alpha = appearFadeInAnimatable.value
                    }
                    .height(56.dp)
                    .semantics {
                        if (contentDescription != null) {
                            this.contentDescription = contentDescription
                        }
                    }
                    .clip(ButtonDefaults.shape)
                    .background(color = backgroundColor)
                    .semantics { role = Role.Button }
                    .clickable(
                        onClick = { actionButton.let { viewModel.onActionButtonClicked(it) } }
                    )
        ) {
            Text(
                text = stringResource(id = actionButtonModel.labelResId),
                style = MaterialTheme.typography.titleMedium,
                color = textColor,
                modifier = Modifier.align(Alignment.Center).padding(ButtonDefaults.ContentPadding),
            )
        }
    }
}

@Composable
fun UdfpsA11yOverlay(
    viewModel: AlternateBouncerUdfpsAccessibilityOverlayViewModel,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context ->
            val view =
                UdfpsAccessibilityOverlay(context).apply { id = R.id.udfps_accessibility_overlay }
            UdfpsAccessibilityOverlayBinder.bind(view, viewModel)
            view
        },
        modifier = modifier,
    )
}
